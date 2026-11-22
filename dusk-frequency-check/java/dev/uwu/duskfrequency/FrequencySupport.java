package dev.uwu.duskfrequency;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.uwu.dimension.AcquisitionPolicy;
import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.CupidShrine;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.People;
import dev.uwu.dimension.PetalShrinePlacement;
import dev.uwu.dimension.SettlementLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.config.ModConfigs;

/** Shared native checks, with no production writes beyond ordinary fixture generation. */
final class FrequencySupport {
    static final Path OUTPUT=Path.of(System.getProperty("uwu.check.evidence"));
    static final int RADIUS=10;
    static final int EXPANDED_DUSK_ATTEMPTS=1024;
    static void require(boolean condition,String message){if(!condition)throw new AssertionError(message);}
    static String signature(BlossomVillage.Plan plan){
        return "grid="+plan.site().gx()+","+plan.site().gz()+" site="+plan.site().x()+","+plan.site().z()+","+plan.site().turns()
                +" plaza="+plan.plaza().toShortString()+" dusk="+plan.dusk()+" road="+Arrays.toString(plan.road())
                +" bends="+Arrays.toString(plan.bends())+" houses="+plan.houses();
    }
    static String signature(Optional<BlossomVillage.Plan> plan){return plan.map(FrequencySupport::signature).orElse("");}
    @SuppressWarnings("unchecked") static Optional<BlossomVillage.Plan> original(ServerLevel level,GroveSites.Site site,boolean dusk){
        try {
            var method=BlossomVillage.class.getDeclaredMethod("survey",ServerLevel.class,GroveSites.Site.class,boolean.class);
            method.setAccessible(true);
            return (Optional<BlossomVillage.Plan>)method.invoke(null,level,site,dusk);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    static boolean assigned(ServerLevel level,GroveSites.Site site,boolean dusk){
        try {
            var method=SettlementLayout.class.getDeclaredMethod("admits",ServerLevel.class,GroveSites.Site.class,boolean.class);
            method.setAccessible(true);
            return (boolean)method.invoke(null,level,site,dusk);
        }catch(ReflectiveOperationException failure){throw new IllegalStateException(failure);}
    }
    static void percent(int value,boolean save){
        AcquisitionPolicy.DUSK_SITE_CHANCE.set(value);
        if(save)AcquisitionPolicy.DUSK_SITE_CHANCE.save();
        require(AcquisitionPolicy.duskVillageChancePercent()==value,"Config getter did not consume "+value);
    }
    static Path configPath()throws IOException{
        // FML uses the game config directory unless an existing world override
        // is present. Ask the registered live config rather than assuming either.
        var configs=ModConfigs.getModConfigs("uwudimension").stream()
                .filter(c->c.getType()==ModConfig.Type.SERVER&&c.getSpec()==AcquisitionPolicy.SPEC).toList();
        require(configs.size()==1,"Expected one registered UwU acquisition policy config");
        var config=configs.getFirst();require(config.getFileName().equals("uwudimension-server.toml"),"Unexpected UwU policy filename");
        Path path=config.getFullPath().toRealPath();require(Files.isRegularFile(path),"Registered UwU policy has no persisted file");return path;
    }
    static JsonObject read(String name)throws Exception{return JsonParser.parseString(Files.readString(OUTPUT.resolve(name))).getAsJsonObject();}
    static void write(String name,JsonObject value)throws Exception{
        Files.createDirectories(OUTPUT);
        Files.writeString(OUTPUT.resolve(name),new GsonBuilder().setPrettyPrinting().create().toJson(value)+"\n");
    }
    static JsonObject metadata(String stage,ServerLevel level){
        var value=new JsonObject();
        value.addProperty("stage",stage);value.addProperty("seed",level.getSeed());
        value.addProperty("sourceSha",System.getenv("GITHUB_SHA"));
        value.addProperty("loadedUwuJarSha256",System.getProperty("uwu.check.candidateSha256"));
        String fixture=System.getProperty("uwu.check.fixtureSha256");
        if(fixture!=null)value.addProperty("loadedFixtureJarSha256",fixture);
        value.addProperty("dimension",level.dimension().location().toString());
        return value;
    }
    static JsonObject cell(GroveSites.Site site){
        var row=new JsonObject();row.addProperty("gx",site.gx());row.addProperty("gz",site.gz());
        row.addProperty("x",site.x());row.addProperty("z",site.z());row.addProperty("turns",site.turns());
        row.addProperty("usable",site.usable());return row;
    }
    static String biome(ServerLevel level,BlockPos pos){
        var source=level.getChunkSource();
        return level.getBiomeManager().withDifferentSource((qx,qy,qz)->source.getGenerator().getBiomeSource()
                .getNoiseBiome(qx,qy,qz,source.randomState().sampler())).getBiome(pos).unwrapKey().orElseThrow().location().toString();
    }
    static String nativeBiome(ServerLevel level,BlossomVillage.Plan plan){
        return level.getBiome(plan.plaza().below()).unwrapKey().orElseThrow().location().toString();
    }
    static GroveSites.Site site(ServerLevel level,JsonObject row){return GroveSites.site(level,row.get("gx").getAsInt(),row.get("gz").getAsInt());}
    static JsonObject choose(JsonArray rows,java.util.function.Predicate<JsonObject> predicate,String reason){
        for(var entry:rows){var row=entry.getAsJsonObject();if(predicate.test(row))return row;}
        throw new AssertionError("Fixed sample has no "+reason+"; do not weaken the required evidence");
    }
    static boolean has(JsonObject row,String name){return row.has(name)&&!row.get(name).getAsString().isEmpty();}
    static Set<ChunkPos> chunks(BlossomVillage.Plan plan){
        var result=new LinkedHashSet<ChunkPos>();
        var a=plan.site().pos(plan.x()-60,0,plan.z()-60);var b=plan.site().pos(plan.x()+60,0,plan.z()+60);
        for(int x=Math.min(a.getX(),b.getX())>>4;x<=(Math.max(a.getX(),b.getX())>>4);x++)
            for(int z=Math.min(a.getZ(),b.getZ())>>4;z<=(Math.max(a.getZ(),b.getZ())>>4);z++)result.add(new ChunkPos(x,z));
        return result;
    }
    static ChunkPos edge(BlossomVillage.Plan plan){
        var house=plan.houses().getFirst();return new ChunkPos(plan.resident(house));
    }
    static void request(ServerLevel level,Set<ChunkPos> tickets,ChunkPos pos){
        if(tickets.add(pos))level.getChunkSource().addRegionTicket(TicketType.FORCED,pos,0,pos,true);
    }
    static boolean loaded(ServerLevel level,ChunkPos pos){return level.getChunkSource().getChunkNow(pos.x,pos.z)!=null;}
    static void entityTicket(ServerLevel level,BlossomVillage.Plan plan){
        var chunk=new ChunkPos(plan.plaza());level.getChunkSource().addRegionTicket(TicketType.FORCED,chunk,2,chunk,true);
    }
    static boolean residentsReady(ServerLevel level,BlossomVillage.Plan plan){
        return !level.getEntitiesOfClass(Keeper.class,new AABB(plan.plaza()).inflate(100)).isEmpty();
    }
    static int nativeVillage(ServerLevel level,BlossomVillage.Plan plan){
        String plaza=BuiltInRegistries.BLOCK.getKey(level.getBlockState(plan.plaza().below()).getBlock()).getPath();
        require(plaza.equals(plan.dusk()?"midnight_lace":"petal_quilt"),"Native plaza missing/wrong faction: "+plaza);
        int authored=0;
        for(var cp:chunks(plan)){
            var chunk=level.getChunkSource().getChunkNow(cp.x,cp.z);require(chunk!=null,"Native footprint incomplete "+cp);
            for(var pos:BlockPos.betweenClosed(cp.getMinBlockX(),plan.y()-10,cp.getMinBlockZ(),cp.getMaxBlockX(),plan.y()+45,cp.getMaxBlockZ())){
                var block=chunk.getBlockState(pos).getBlock();var id=BuiltInRegistries.BLOCK.getKey(block);
                if(plan.dusk())require(block!=CupidShrine.BLOCK.get(),"Cupid shrine generated in Dusk site");
                if(id.getNamespace().equals("uwudimension")&&id.getPath().equals(plan.dusk()?"velvetwood_planks":"petalwood_planks"))authored++;
            }
        }
        // Petal architecture varies; its exact native plaza and residents are the gate.
        if(plan.dusk()){
            require(authored>20,"No native Dusk house architecture");
            require(PetalShrinePlacement.select(level,plan).isEmpty(),"Dusk acquired Cupid shrine placement");
        }
        var residents=level.getEntitiesOfClass(Keeper.class,new AABB(plan.plaza()).inflate(100));
        require(!residents.isEmpty(),"No generated residents");
        require(residents.stream().allMatch(k->k.getType()==(plan.dusk()?People.DUSK_KEEPER.get():People.PETAL_KEEPER.get())),"Native residents have wrong faction");
        return residents.size();
    }
    static CompoundTag blocks(ServerLevel level,Set<ChunkPos> chunks,int y){
        var result=new CompoundTag();
        for(var cp:chunks){
            var chunk=level.getChunkSource().getChunkNow(cp.x,cp.z);require(chunk!=null,"Snapshot chunk missing");
            for(var pos:BlockPos.betweenClosed(cp.getMinBlockX(),Math.max(level.getMinBuildHeight(),y-12),cp.getMinBlockZ(),
                    cp.getMaxBlockX(),Math.min(level.getMaxBuildHeight()-1,y+45),cp.getMaxBlockZ())){
                var state=chunk.getBlockState(pos);var id=BuiltInRegistries.BLOCK.getKey(state.getBlock());String path=id.getPath();
                if(id.getNamespace().equals("uwudimension")&&(path.contains("ashlar")||path.contains("planks")||path.contains("beam")
                        ||path.contains("roof_tile")||path.contains("shingle")||path.contains("frieze")||path.contains("quilt")||path.equals("midnight_lace")))
                    result.putString(Long.toString(pos.asLong()),state.toString());
            }
        }
        return result;
    }
    static void verifyBlocks(ServerLevel level,CompoundTag states){
        for(String key:states.getAllKeys())require(level.getBlockState(BlockPos.of(Long.parseLong(key))).toString().equals(states.getString(key)),"Saved structural block changed at "+key);
    }
    static CompoundTag resident(Mob mob){
        var row=new CompoundTag();row.putUUID("UUID",mob.getUUID());row.putString("Type",BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType()).toString());
        row.putString("FixtureIdentity",mob.getPersistentData().getString("DuskFrequencyFixture"));
        var nbt=mob.saveWithoutId(new CompoundTag());
        for(String key:List.of("CustomName","Offers","VillagerData","Inventory","NoAI","Invulnerable"))if(nbt.contains(key))row.put(key,nbt.get(key).copy());
        return row;
    }
    static ListTag residents(ServerLevel level,BlossomVillage.Plan plan){
        var result=new ListTag();
        for(var keeper:level.getEntitiesOfClass(Keeper.class,new AABB(plan.plaza()).inflate(100))){
            keeper.setNoAi(true);keeper.setNoGravity(true);keeper.setInvulnerable(true);keeper.setPersistenceRequired();result.add(resident(keeper));
        }
        require(!result.isEmpty(),"No real native residents to snapshot");return result;
    }
    static boolean residentsLoaded(ServerLevel level,ListTag rows){for(var tag:rows)if(level.getEntity(((CompoundTag)tag).getUUID("UUID"))==null)return false;return true;}
    static void verifyResidents(ServerLevel level,ListTag rows){
        for(var tag:rows){var row=(CompoundTag)tag;var actor=level.getEntity(row.getUUID("UUID"));
            require(actor instanceof Mob&&resident((Mob)actor).equals(row),"Saved resident identity/type/trades/inventory changed");}
    }
    static CompoundTag planTag(BlossomVillage.Plan plan,int chance){
        var tag=new CompoundTag();tag.putInt("Gx",plan.site().gx());tag.putInt("Gz",plan.site().gz());
        tag.putBoolean("Dusk",plan.dusk());tag.putString("Plan",signature(plan));tag.putInt("Chance",chance);return tag;
    }
    static BlossomVillage.Plan plan(ServerLevel level,CompoundTag tag){
        var site=GroveSites.site(level,tag.getInt("Gx"),tag.getInt("Gz"));
        var plan=BlossomVillage.plan(level,site,tag.getBoolean("Dusk")).orElseThrow(()->new AssertionError("Saved plan vanished"));
        require(signature(plan).equals(tag.getString("Plan")),"Saved plan geometry/faction changed");return plan;
    }
    private FrequencySupport(){}
}
