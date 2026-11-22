package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import net.minecraft.server.level.ServerLevel;

/** Size an already habitable village without moving its plaza or rerolling its faction. */
final class VillageHouseLots {
    static final int EXTENT_X = 40, EXTENT_Z = 52;
    private static final long SIZE_SALT = 0x56494C4C41474553L;
    private record Lot(int offset, int side, boolean rear) {}

    static BlossomVillage.Plan layout(ServerLevel level, BlossomVillage.Plan base) {
        var site = base.site();
        int hash = HeartwaterFeature.Valley.mix(site.gx(), site.gz(), level.getSeed() ^ SIZE_SALT);
        int target = 6 + Math.floorMod(hash, 7);
        if (base.houses().size() == target) return base;
        var houses = new ArrayList<>(base.houses().subList(0, Math.min(target, base.houses().size())));
        if (houses.size() < target) {
            var lots = new ArrayList<Lot>();
            for (int side : new int[]{-1, 1}) {
                for (int dx = -24; dx <= 24; dx += 16) {
                    int x = base.x() + dx;
                    if (houses.stream().noneMatch(h -> h.x() == x && h.side() == side))
                        lots.add(new Lot(dx, side, false));
                }
                // Back-lane entrances fit between the eaves of the houses on the main street.
                for (int dx = -32; dx <= 32; dx += 16) lots.add(new Lot(dx, side, true));
            }
            var random = new Random(Integer.toUnsignedLong(hash));
            Collections.shuffle(lots, random);
            var tree = MoonveilTreehouse.plan(level, base).orElse(null);
            for (Lot lot : lots) {
                var house = fit(level, base, houses, lot, random.nextInt(VillageHouse.STYLES.size()), tree);
                if (house != null) houses.add(house);
                if (houses.size() == target) break;
            }
        }
        if (houses.equals(base.houses())) return base;
        return new BlossomVillage.Plan(site, base.x(), base.z(), base.y(), base.road(), base.bends(),
                List.copyOf(houses), base.dusk(), base);
    }

    private static BlossomVillage.House fit(ServerLevel level, BlossomVillage.Plan p, List<BlossomVillage.House> houses,
            Lot lot, int firstStyle, MoonveilTreehouse.Plot tree) {
        int x = p.x() + lot.offset, index = p.road().length / 2 + lot.offset;
        int street = p.z() + p.bends()[index], start = street + 2 * lot.side;
        var site = p.site();
        for (int setback = lot.rear ? 18 : 5; setback <= (lot.rear ? 30 : 11); setback += 3) {
            int door = street + lot.side * setback;
            int floor = GroveSites.height(level, site.wx(x,door), site.wz(x,door));
            if (floor <= level.getSeaLevel() || !laneFits(level, p, x, start, door, p.road()[index], floor)) continue;
            for (int n = 0; n < VillageHouse.STYLES.size(); n++) {
                int style = (firstStyle + n) % VillageHouse.STYLES.size();
                var shell = VillageHouse.STYLES.get(style);
                int z = door + lot.side * shell.depth;
                int left = x-shell.radiusX, right = x+shell.radiusX, near = z-shell.radiusZ, far = z+shell.radiusZ;
                if (Math.abs(z-p.z()) + shell.radiusZ > EXTENT_Z
                        || !clearPlot(p, houses, x, start, door, left, near, right, far)) continue;
                int laneMin = Math.min(start, door), laneMax = Math.max(start, door);
                if (reserved(level,p,tree,left,near,right,far) || reserved(level,p,tree,x,laneMin,x,laneMax)) continue;
                if (!level.getWorldBorder().isWithinBounds(site.pos(left,floor,near))
                        || !level.getWorldBorder().isWithinBounds(site.pos(right,floor,far))) continue;
                if (!dryLot(level,p,x,z,floor,shell)) continue;
                return new BlossomVillage.House(x,z,floor,shell.width,shell.depth,lot.side,style,
                        BlossomVillage.nextJob(houses.size()), SettlementLayout.useWallBeds(level,site,x,z,shell.radiusX,shell.radiusZ));
            }
        }
        return null;
    }

    private static boolean clearPlot(BlossomVillage.Plan p, List<BlossomVillage.House> houses,
            int x,int start,int door,int left,int near,int right,int far) {
        for (var h : houses) {
            var roof = VillageHouse.STYLES.get(h.style());
            int x1=h.x()-roof.radiusX, x2=h.x()+roof.radiusX, z1=h.z()-roof.radiusZ, z2=h.z()+roof.radiusZ;
            if (overlaps(left-1,near-1,right+1,far+1,x1,z1,x2,z2)
                    || overlaps(x,Math.min(start,door),x,Math.max(start,door),x1,z1,x2,z2)) return false;
            int otherStart = p.z()+p.bends()[h.x()-p.x()+p.road().length/2]+2*h.side();
            int otherDoor = h.z()-h.side()*h.depth();
            int width = p.houses().contains(h) ? 1 : 0;
            if (overlaps(left,near,right,far,h.x()-width,Math.min(otherStart,otherDoor),
                    h.x()+width,Math.max(otherStart,otherDoor))) return false;
        }
        return true;
    }

    private static boolean reserved(ServerLevel level, BlossomVillage.Plan p, MoonveilTreehouse.Plot tree,
            int x1,int z1,int x2,int z2) {
        if (tree != null && overlaps(x1,z1,x2,z2,tree.x()-MoonveilTreehouse.FOOTPRINT,tree.z()-MoonveilTreehouse.FOOTPRINT,
                tree.x()+MoonveilTreehouse.FOOTPRINT,tree.z()+MoonveilTreehouse.FOOTPRINT)) return true;
        if (VillageSakura.reservedForTree(level,p,x1,z1,x2,z2)) return true;
        if (overlaps(x1,z1,x2,z2,-14,p.site().lip()-15,14,57)) return true;
        // The Petal entrance shoulder can hold its Cupid shrine; the gate itself spans its street.
        int gate = p.x() + (p.dusk()?30:-30);
        int gateZ = p.z() + p.bends()[p.dusk()?p.road().length-3:2];
        if (overlaps(x1,z1,x2,z2,gate,gateZ-5,gate,gateZ+5)) return true;
        return !p.dusk() && overlaps(x1,z1,x2,z2,p.x()-50,gateZ-17,p.x()-33,gateZ+17);
    }

    private static boolean overlaps(int ax,int az,int bx,int bz,int cx,int cz,int dx,int dz) {
        return ax<=dx && bx>=cx && az<=dz && bz>=cz;
    }

    private static boolean laneFits(ServerLevel level,BlossomVillage.Plan p,int x,int start,int door,int from,int to) {
        int length = Math.abs(door-start);
        if (Math.abs(to-from)>length) return false;
        var s = p.site();
        for (int i=0;i<=length;i++) {
            int z = start + Integer.signum(door-start)*i;
            int wx=s.wx(x,z), wz=s.wz(x,z);
            int ground=GroveSites.height(level,wx,wz);
            int grade=from+(int)Math.round((to-from)*i/(double)length);
            if (ground<=level.getSeaLevel() || Math.abs(ground-grade)>2 || !GroveSites.cherry(level,wx,ground+1,wz)
                    || !p.dusk() && GroveSites.duskLand(level,wx,ground+1,wz)) return false;
        }
        return true;
    }

    private static boolean dryLot(ServerLevel level,BlossomVillage.Plan p,int x,int z,int floor,VillageHouse shell) {
        var s=p.site();
        for (int dx=-shell.radiusX;dx<=shell.radiusX;dx++) for (int dz=-shell.radiusZ;dz<=shell.radiusZ;dz++) {
            int wx=s.wx(x+dx,z+dz), wz=s.wz(x+dx,z+dz);
            if (dx % shell.radiusX == 0 && dz % shell.radiusZ == 0) {
                int earth=GroveSites.height(level,wx,wz);
                if (earth<=level.getSeaLevel() || Math.abs(earth-floor)>3 || !GroveSites.cherry(level,wx,earth+1,wz)) return false;
            }
            if (!p.dusk() && GroveSites.duskLand(level,wx,floor,wz)) return false;
        }
        return true;
    }

    private VillageHouseLots() {}
}
