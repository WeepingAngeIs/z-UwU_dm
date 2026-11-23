package dev.uwu.dimension;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

/** Lay out working plots and connect their front steps to the existing street in grove-local coordinates. */
final class ProfessionQuarter {
    static final int REACH = 110, WING = 65;
    enum Job {
        ARMORER(Blocks.BLAST_FURNACE), BUTCHER(Blocks.SMOKER), CARTOGRAPHER(Blocks.CARTOGRAPHY_TABLE),
        CLERIC(Blocks.BREWING_STAND), FARMER(Blocks.COMPOSTER), FISHERMAN(Blocks.BARREL),
        FLETCHER(Blocks.FLETCHING_TABLE), LEATHERWORKER(Blocks.CAULDRON), LIBRARIAN(Blocks.LECTERN),
        MASON(Blocks.STONECUTTER), SHEPHERD(Blocks.LOOM), TOOLSMITH(Blocks.SMITHING_TABLE),
        WEAPONSMITH(Blocks.GRINDSTONE);
        final Block station;
        Job(Block station) { this.station = station; }
    }
    record Tile(int x, int z) {
        Tile step(Direction dir, int n) { return new Tile(x + dir.getStepX()*n, z + dir.getStepZ()*n); }
    }
    record Plot(Job job, Tile center, Direction front, int floor) {}
    record Layout(List<Plot> plots, Set<Tile> street) {}
    private record Candidate(Tile center, Direction front, int entrance, int score) {}
    private static final Layout EMPTY = new Layout(List.of(), Set.of());
    private static final Map<ServerLevel,Map<BlockPos,Layout>> CACHE = new WeakHashMap<>();

    static int rolledJobs(long worldSeed, BlossomVillage.Plan village) {
        long seed = worldSeed ^ village.plaza().asLong() ^ (village.dusk() ? 0x6698acb41dfeL : 0x41cb315b929fL);
        int mask = 0;
        for (Job job : Job.values()) {
            // A private stream for every profession keeps the existing rolls stable if more jobs are added.
            long salt = net.minecraft.util.Mth.getSeed(job.name().hashCode(), 34871, 977);
            if (new SplittableRandom(seed ^ salt).nextInt(4) == 0) mask |= 1 << job.ordinal();
        }
        return mask;
    }

    static Layout plan(ServerLevel level, BlossomVillage.Plan village) {
        Map<BlockPos,Layout> plans;
        synchronized (CACHE) { plans = CACHE.computeIfAbsent(level, unused -> new ConcurrentHashMap<>()); }
        if (plans.size() > 1024) plans.clear();
        return plans.computeIfAbsent(village.plaza(), ignored -> SettlementLayout.newProfessionSites(level,village)
                ? design(level, village, rolledJobs(level.getSeed(), village)) : EMPTY);
    }

    /** All selected jobs receive separate plots. The mask is also useful for exercising the rare all-jobs layout. */
    static Layout design(ServerLevel level, BlossomVillage.Plan village, int mask) {
        if (mask == 0) return EMPTY;
        Grid grid = new Grid(village);
        for (var h : village.houses()) {
            var style = VillageHouse.STYLES.get(h.style());
            grid.reserve(h.x(),h.z(),style.radiusX + 1,style.radiusZ + 1);
            int streetZ = village.z() + village.bends()[h.x()-village.x()+32];
            int doorZ = h.z()-h.side()*h.depth();
            grid.rect(h.x()-2,Math.min(streetZ,doorZ)+1,h.x()+2,Math.max(streetZ,doorZ)-1);
        }
        MoonveilTreehouse.plan(level,village).ifPresent(t -> grid.reserve(t.x(),t.z(),MoonveilTreehouse.FOOTPRINT+1,MoonveilTreehouse.FOOTPRINT+1));
        VillageSakura.center(level,village).ifPresent(t -> grid.reserve(t.x(),t.z(),18,18));
        PetalShrinePlacement.select(level,village).ifPresent(s -> grid.reserve(s.localX(),s.localZ(),6,6));
        int gate = village.dusk() ? 62 : 2;
        int gateX = village.x()+gate-32, gateZ = village.z()+village.bends()[gate];
        grid.reserve(gateX,gateZ-4,1,2);
        grid.reserve(gateX,gateZ+4,1,2);
        // Keep both halves of the grove away from the spring, its bank and the other faction.
        if (village.dusk()) grid.rect(grid.minX,grid.minZ,35,grid.maxZ);
        else grid.rect(-35,grid.minZ,grid.maxX,grid.maxZ);

        List<Tile> cells = new ArrayList<>();
        int outward = village.dusk() ? 1 : -1;
        for (int offset = -28; offset <= 104; offset += 12) for (int across=-60; across<=60; across+=12) {
            if (Math.abs(offset) <= 39 && Math.abs(across) <= 52) continue;
            Tile c = new Tile(village.x()+outward*offset, village.z()+across);
            if (!grid.openSquare(c,5)) continue;
            cells.add(c);
            grid.reserve(c.x,c.z,4,4);
        }
        grid.walkFromStreet();
        List<Candidate> available = new ArrayList<>();
        for (Tile c : cells) {
            Candidate best = null;
            for (Direction face : Direction.Plane.HORIZONTAL) {
                int index = grid.index(c.step(face,5));
                if (grid.parent[index] < 0) continue;
                int score = grid.distance[index];
                if (best == null || score < best.score) best = new Candidate(c,face,index,score);
            }
            if (best != null) available.add(best);
        }
        available.sort(Comparator.comparingInt(Candidate::score));
        // Survey the closest viable plots first. Prefer natural terraces over large cuts or fills.
        for (int i=0; i<Math.min(available.size(),24); i++) {
            Candidate c = available.get(i);
            var s = village.site();
            int ground = GroveSites.height(level,s.wx(c.center.x,c.center.z),s.wz(c.center.x,c.center.z));
            available.set(i,new Candidate(c.center,c.front,c.entrance,c.score + Math.min(45,Math.abs(ground-village.y())*2)));
        }
        available.sort(Comparator.comparingInt(Candidate::score));
        List<Plot> plots = new ArrayList<>();
        Set<Tile> street = new LinkedHashSet<>();
        for (Job job : Job.values()) {
            if ((mask & (1 << job.ordinal())) == 0) continue;
            Candidate c = available.get(plots.size());
            plots.add(new Plot(job,c.center,c.front,village.y()));
            for (int i = c.entrance;; i = grid.parent[i]) {
                Tile step = grid.tile(i);
                for (int dx=-1; dx<=1; dx++) for (int dz=-1; dz<=1; dz++) {
                    Tile t = new Tile(step.x+dx,step.z+dz);
                    if (!onOldStreet(village,t) && !grid.reserved(t)) street.add(t);
                }
                if (grid.parent[i] == i) break;
            }
            street.add(c.center.step(c.front,4));
        }
        return new Layout(List.copyOf(plots), Collections.unmodifiableSet(street));
    }

    private static boolean onOldStreet(BlossomVillage.Plan v, Tile t) {
        int i=t.x-v.x()+32;
        return i>=0 && i<v.road().length && Math.abs(t.z-v.z()-v.bends()[i])<=1;
    }

    static int streetHeight(BlossomVillage.Plan v, Tile tile) {
        int i=Math.clamp(tile.x-v.x()+32,0,64);
        int distance=Math.abs(tile.x-(v.x()+i-32))+Math.max(0,Math.abs(tile.z-v.z()-v.bends()[i])-1);
        return Math.clamp(v.y(),v.road()[i]-distance,v.road()[i]+distance);
    }

    static void place(HeartwaterFeature.Valley v, BlossomVillage.Plan village) {
        if (!v.overlaps(village.x()-REACH,village.z()-WING,village.x()+REACH,village.z()+WING)) return;
        Layout layout = plan(v.level.getLevel(),village);
        if (layout.plots.isEmpty()) return;
        v.part("profession_street", () -> {
            for (Tile t : layout.street) if (v.inside(t.x,t.z)) BlossomVillage.road(v,t.x,streetHeight(village,t),t.z,village.dusk());
        });
        for (Plot plot : layout.plots) if (v.overlaps(plot.center.x-3,plot.center.z-3,plot.center.x+3,plot.center.z+3))
            v.part("profession_"+plot.job.name().toLowerCase(Locale.ROOT), () -> ProfessionPavilion.build(v,village,plot));
    }

    static void indexStations(HeartwaterFeature.Valley valley, BlossomVillage.Plan village) {
        if(!valley.overlaps(village.x()-REACH,village.z()-WING,village.x()+REACH,village.z()+WING))return;
        var level=valley.level.getLevel();
        var pois=level.getPoiManager();
        for(Plot p:plan(level,village).plots) {
            Tile point=p.center.step(p.front,p.job==Job.FARMER?-3:-2);
            if(!valley.inside(point.x,point.z))continue;
            BlockPos at=village.site().pos(point.x,p.floor+1,point.z);
            var state=valley.chunk.getBlockState(at);
            if(state.is(p.job.station) && pois.getType(at).isEmpty())
                net.minecraft.world.entity.ai.village.poi.PoiTypes.forState(state).ifPresent(type->pois.add(at,type));
        }
    }

    static boolean nearWork(Layout layout, int x, int z, int margin) {
        for (Plot p : layout.plots) if (Math.abs(x-p.center.x)<=3+margin && Math.abs(z-p.center.z)<=3+margin) return true;
        for (Tile t : layout.street) if (Math.abs(x-t.x)<=margin && Math.abs(z-t.z)<=margin) return true;
        return false;
    }

    private static final class Grid {
        final BlossomVillage.Plan village;
        final int minX,minZ,maxX,maxZ,width;
        final boolean[] used;
        final int[] parent,distance;
        Grid(BlossomVillage.Plan v) {
            village=v; minX=v.x()-(v.dusk()?40:REACH); maxX=v.x()+(v.dusk()?REACH:40);
            minZ=v.z()-WING; maxZ=v.z()+WING; width=maxX-minX+1;
            used=new boolean[width*(maxZ-minZ+1)]; parent=new int[used.length]; distance=new int[used.length]; Arrays.fill(parent,-1);
        }
        Tile tile(int n) { return new Tile(minX+n%width,minZ+n/width); }
        int index(Tile t){return (t.z-minZ)*width+t.x-minX;}
        boolean inside(int x,int z){return x>=minX&&x<=maxX&&z>=minZ&&z<=maxZ;}
        boolean reserved(Tile t){return !inside(t.x,t.z)||used[index(t)];}
        void reserve(int x,int z,int rx,int rz){rect(x-rx,z-rz,x+rx,z+rz);}
        void rect(int x1,int z1,int x2,int z2) {
            int a=Math.max(x1,minX),b=Math.min(x2,maxX);
            if(a>b)return;
            for(int z=Math.max(z1,minZ);z<=Math.min(z2,maxZ);z++) Arrays.fill(used,(z-minZ)*width+a-minX,(z-minZ)*width+b-minX+1,true);
        }
        boolean openSquare(Tile c,int r) {
            if (!inside(c.x-r,c.z-r)||!inside(c.x+r,c.z+r)) return false;
            for(int z=c.z-r;z<=c.z+r;z++) for(int x=c.x-r;x<=c.x+r;x++) if(used[(z-minZ)*width+x-minX])return false;
            return true;
        }
        void walkFromStreet() {
            int[] queue=new int[parent.length]; int head=0,tail=0;
            for(int i=0;i<65;i++){
                Tile t=new Tile(village.x()+i-32,village.z()+village.bends()[i]);
                if(reserved(t))continue;
                int start=index(t);parent[start]=start;queue[tail++]=start;
            }
            while(head<tail) {
                int at=queue[head++];
                for(Direction dir:Direction.Plane.HORIZONTAL) {
                    Tile t=tile(at).step(dir,1);
                    if(reserved(t))continue;
                    int next=index(t);
                    if(parent[next]>=0)continue;
                    parent[next]=at; distance[next]=distance[at]+1; queue[tail++]=next;
                }
            }
        }
    }
    private ProfessionQuarter() {}
}
