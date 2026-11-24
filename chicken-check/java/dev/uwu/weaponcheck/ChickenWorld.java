package dev.uwu.weaponcheck;

import dev.uwu.dimension.NativeChickens;
import dev.uwu.dimension.ReefLife;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.EventHooks;

final class ChickenWorld {
    static final int Y = 301;
    static final String[] STYLES = {"blossoms","crest","fringe"};
    static final int[] HOME = {8,40,48};
    static final Map<String, UUID> birds = new LinkedHashMap<>();
    private static final Set<String> origins = new HashSet<>();
    private static final Set<UUID> observed = new HashSet<>();
    private static int attempts;

    static ServerLevel reach(MinecraftServer server) {
        return Objects.requireNonNull(server.getLevel(ReefLife.REACH));
    }

    static void prepare(MinecraftServer server, boolean restart) throws Exception {
        ServerLevel l = reach(server);
        l.setDayTime(6000);
        l.setWeatherParameters(24000, 0, false, false);
        l.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
        l.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
        for (int x = -1; x <= 4; x++) for (int z = -1; z <= 1; z++) l.getChunk(x,z);
        if (restart) {
            ChickenGallery.Saved saved = Evidence.read("saved.json", ChickenGallery.Saved.class);
            for (int i=0; i<3; i++) birds.put(STYLES[i], UUID.fromString(saved.ids()[i]));
        } else {
            for (var p : BlockPos.betweenClosed(-8,Y-1,-8,79,Y-1,31)) l.setBlock(p, Blocks.GRASS_BLOCK.defaultBlockState(),2);
            biome(l, -8, 23, "uwudimension:lantern_fens");
            biome(l, 24, 55, "uwudimension:velvet_highlands");
            // Probe the remaining keyed pastures through NeoForge's real weighted event.
            for (String name : List.of("ribbon_bluffs","starlace_frostlands","dusk_frostlands","lantern_grotto")) {
                biome(l,56,75,"uwudimension:"+name);
                pool(l,65,!name.equals("lantern_grotto"));
            }
            biome(l,56,75,"minecraft:plains");
            pool(l,8,true);
            pool(l,40,true);
            Chicken egg = EntityType.CHICKEN.spawn(l,new BlockPos(8,Y,15),MobSpawnType.SPAWN_EGG);
            Evidence.require(egg != null && NativeChickens.visibleCoat(egg) == 0 && raw(egg)==0,"spawn egg got a coat");
            egg.discard();
            ServerLevel over = server.overworld();
            over.getChunk(0,0);
            Chicken ordinary = EntityType.CHICKEN.spawn(over,new BlockPos(8,Y,8),MobSpawnType.NATURAL);
            Evidence.require(ordinary != null && raw(ordinary)==0,"Overworld chicken got a birth coat");
            ordinary.discard();
        }
        for (var p:server.getPlayerList().getPlayers()) {
            p.setGameMode(GameType.CREATIVE);
            p.getAbilities().flying=true; p.onUpdateAbilities();
            p.teleportTo(l,28,Y+1,-39,0,0);
        }
    }

    private static void biome(ServerLevel l,int x1,int x2,String biome) {
        // The entire disposable study strip is in loaded chunks; split to respect the vanilla command limit.
        for (int z=-8;z<32;z+=16) {
            String cmd="fillbiome "+x1+" "+(Y-9)+" "+z+" "+x2+" "+(Y+10)+" "+(z+15)+" "+biome;
            l.getServer().getCommands().performPrefixedCommand(l.getServer().createCommandSourceStack().withLevel(l).withSuppressedOutput(),cmd);
        }
        var actual = l.getBiome(new BlockPos((x1+x2)/2,Y,9)).unwrapKey().orElseThrow().location().toString();
        Evidence.require(actual.equals(biome),"biome fixture: wanted "+biome+" got "+actual);
    }

    private static void pool(ServerLevel l, int x, boolean eligible) {
        var pos=new BlockPos(x,Y,8);
        var biome=l.getBiome(pos);
        var original=biome.value().getMobSettings().getMobs(MobCategory.CREATURE);
        var pool=EventHooks.getPotentialSpawns(l,MobCategory.CREATURE,pos,original).unwrap();
        var chickens=pool.stream().filter(s->s.type==EntityType.CHICKEN).toList();
        if (eligible) {
            Evidence.require(chickens.size()==1 && chickens.get(0).getWeight().asInt()==1
                && chickens.get(0).minCount==2 && chickens.get(0).maxCount==3,"wrong chicken weight or group");
            Evidence.require(pool.size()==original.unwrap().size()+1 && pool.containsAll(original.unwrap()),"another species' draw changed");
        } else Evidence.require(pool.equals(original.unwrap()),"unexpected pool change outside pasture");
    }

    static byte raw(Chicken c) { return ((NativeChickens.BirthCoat)c).uwu$getCoat(); }

    static boolean findWild(ServerLevel l) {
        // Both methods run vanilla's actual weighted selection, terrain/light checks and finalize event.
        for(int x : new int[]{8,40}) {
            var pos=new BlockPos(x,Y,8);
            NaturalSpawner.spawnCategoryForPosition(MobCategory.CREATURE,l,pos);
            for(int n=0;n<8;n++)
                NaturalSpawner.spawnMobsForChunkGeneration(l,l.getBiome(pos),new ChunkPos(pos),
                        RandomSource.create(5711L + attempts*127L+x*11L+n*499L));
        }
        for (var entity : l.getEntitiesOfClass(Chicken.class,new AABB(-8,Y-1,-8,80,Y+4,32))) {
            if (!observed.add(entity.getUUID())) continue;
            var reason=entity.getSpawnType();
            byte coat=raw(entity);
            if (coat==0 || reason==null) continue;
            String faction=coat==1?"petal":"dusk";
            Evidence.require(NativeChickens.visibleCoat(entity)==coat,"wild chicken has wrong local coat");
            origins.add(faction+"/"+reason.name());
            String style=coat==1?"blossoms":(entity.getUUID().getLeastSignificantBits()&1L)==0?"crest":"fringe";
            birds.putIfAbsent(style,entity.getUUID());
            entity.setPersistenceRequired(); entity.setNoAi(true);
        }
        if (birds.size()==3 && origins.containsAll(List.of("petal/NATURAL","dusk/NATURAL","petal/CHUNK_GENERATION","dusk/CHUNK_GENERATION"))) {
            for(var entity:l.getEntitiesOfClass(Mob.class,new AABB(-8,Y-1,-8,80,Y+5,32)))
                if(!birds.containsValue(entity.getUUID())) entity.discard();
            for(int i=0;i<3;i++) {
                var c=bird(l,i); c.setAge(0); move(c,HOME[i],8); c.setPersistenceRequired();
            }
            System.out.println("NATIVE WILD CHICKEN PASS "+origins+" birds="+birds+" batches="+(attempts+1));
            return true;
        }
        Evidence.require(++attempts<=150,"missing real wild origins "+origins+" birds="+birds);
        return false;
    }

    static Chicken bird(ServerLevel l,int i) { return (Chicken) l.getEntity(birds.get(STYLES[i])); }
    static void move(Chicken c, double x, double z) {
        c.teleportTo(x+.5,Y,z+.5); c.setYRot(0); c.setYHeadRot(0); c.setYBodyRot(0); c.setXRot(0);
    }

    static ChickenGallery.Scene scene(ServerLevel l,int index,boolean restart) {
        int style=restart?index:index/6;
        int slot=restart?0:index%6;
        // After the angle series, move the SAME Dusk bird outside the five pastures and back.
        boolean crossing=!restart && index>=18;
        if(crossing) { style=1; slot=0; }
        boolean hidden=crossing && index==18;
        var chicken=bird(l,style);
        Evidence.require(chicken!=null,"saved bird unavailable "+STYLES[style]);
        double x=hidden?64:HOME[style];
        move(chicken,x,8);
        byte visible=NativeChickens.visibleCoat(chicken);
        Evidence.require(visible==(hidden?0:style==0?1:2),"wrong coat after boundary/reload");
        CompoundTag nbt=new CompoundTag(); chicken.saveWithoutId(nbt);
        Evidence.require(nbt.getByte("UwUDimensionNativeCoat")==raw(chicken),"birth data missing");
        double a=(slot%3)*Math.PI/2, distance=slot<3?2.1:4.6;
        double px=x+.5+Math.sin(a)*distance,pz=8.5+Math.cos(a)*distance;
        String label=(restart?"reload-":hidden?"crossed-":crossing?"returned-":"")+STYLES[style]+"-"+slot;
        for (ServerPlayer p:l.getServer().getPlayerList().getPlayers()) {
            p.setGameMode(GameType.SPECTATOR);
            p.teleportTo(l,px,Y-.46,pz,0,0);
        }
        return new ChickenGallery.Scene(index,label,chicken.getId(),chicken.getUUID().toString(),hidden?"vanilla":STYLES[style],
                px,Y-.46,pz,x+.5,Y+.48,8.5);
    }
    static void animate(ServerLevel l,ChickenGallery.Scene scene,int n) {
        if (n>90 || scene.expected().equals("vanilla")) return;
        var c=(Chicken)l.getEntity(UUID.fromString(scene.uuid()));
        // Real native movement/leg interpolation over a small visible step.
        c.setYHeadRot((float)(Math.sin(n*.11)*32));
        c.setXRot((float)(Math.cos(n*.08)*9));
        if(n>=15 && n<30) c.setDeltaMovement(0,c.getDeltaMovement().y,0.035);
    }
}
