package dev.uwu.villagesize;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.uwu.dimension.AcquisitionPolicy;
import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.SettlementLayout;
import dev.uwu.weaponcheck.CandidateJarProbe;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

@Mod(value="uwu_village_size_check",dist=Dist.DEDICATED_SERVER)
public final class VillageSizeProbe {
    private static final Path OUT=Path.of(System.getProperty("uwu.village.out"));
    private final String stage=System.getProperty("uwu.village.stage");
    private final long seed=Long.parseLong(System.getProperty("uwu.village.seed"));
    private record Row(int x,int z,boolean dusk,int old,String signature){}
    private record Want(int count,boolean dusk){}
    private record Pick(Row row,BlossomVillage.Plan plan){}
    private final List<Row> sites=new ArrayList<>();
    private final List<Pick> picks=new ArrayList<>();
    private final List<ChunkPos> tickets=new ArrayList<>();
    private final Set<String> examined=new HashSet<>();
    private final JsonArray layouts=new JsonArray();
    private ServerLevel level;
    private List<Want> goals;
    private int goal,index,phase,physical;
    private long started=System.nanoTime(),nextSurvey;
    private boolean done;
    private JsonObject oldState;

    public VillageSizeProbe(){
        CandidateJarProbe.verify();
        NeoForge.EVENT_BUS.addListener(this::tick);
    }
    private void tick(ServerTickEvent.Post event) {
        if(done)return;
        try{
            if(level==null){
                level=event.getServer().getLevel(ReefLife.REACH);
                require(level!=null&&level.getSeed()==seed,"Wrong native dimension or seed");
                require(AcquisitionPolicy.duskVillageChancePercent()==75,"Shipped Dusk frequency changed");
                require(AcquisitionPolicy.cupidBowCharmSeconds()==40,"Weapon settings base changed");
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());level.setDayTime(6000);
                try(var input=new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/village-sites.csv"),StandardCharsets.UTF_8))){
                    input.lines().skip(1).forEach(line->{String[] v=line.split(",");if(Long.parseLong(v[0])==seed)
                        sites.add(new Row(Integer.parseInt(v[1]),Integer.parseInt(v[2]),v[3].equals("dusk"),Integer.parseInt(v[4]),v[5]));});
                }
                sites.sort(Comparator.comparingInt(Row::old).reversed());
                goals=switch((int)seed){
                    case 152045->List.of(new Want(6,false),new Want(9,false),new Want(12,true));
                    case 20261121->List.of(new Want(7,true),new Want(10,false),new Want(12,false));
                    case 8675309->List.of(new Want(6,true),new Want(8,false),new Want(11,true));
                    default->throw new AssertionError("Unexpected seed");};
            }
            require(System.nanoTime()-started<1_080_000_000_000L,"Stage timed out "+stage+" "+phase+" "+goal+" "+physical);
            done=switch(stage){
                case "fresh"->fresh();
                case "restart"->restart();
                case "oldpartial","upgrade"->migration();
                default->throw new AssertionError("Unknown stage "+stage);
            };
            if(done)System.out.println("UWU VILLAGE SIZES PASS "+stage+" "+seed);
        }catch(Throwable failure){
            done=true;System.err.println("UWU VILLAGE SIZES FAILED "+stage+" "+seed+" phase="+phase+" search="+goal+" physical="+physical);failure.printStackTrace();
        }
        if(done)event.getServer().halt(false);
    }
    private static int target(Row row,long seed){
        long h=seed ^ 0x56494C4C41474553L ^ row.x*0x632BE59BD9B4E019L ^ row.z*0x9E3779B97F4A7C15L;
        h=(h^(h>>>27))*0x94D049BB133111EBL;return 6+Math.floorMod((int)(h^(h>>>31)),7);
    }
    private BlossomVillage.Plan plan(Row row){
        var site=GroveSites.site(level,row.x,row.z);
        require(BlossomVillage.plan(level,site,!row.dusk).isEmpty(),"Unrelated faction admitted");
        return BlossomVillage.plan(level,site,row.dusk).orElseThrow(()->new AssertionError("Previously approved site vanished "+row));
    }
    private static String signature(BlossomVillage.Plan p)throws Exception{
        String input="grid="+p.site().gx()+","+p.site().gz()+" site="+p.site().x()+","+p.site().z()+","+p.site().turns()
                +" plaza="+p.plaza().toShortString()+" dusk="+p.dusk()+" road="+Arrays.toString(p.road())
                +" bends="+Arrays.toString(p.bends())+" houses="+p.houses();
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8)));
    }
    private boolean fresh()throws Exception{
        if(phase==0){
            if(goal==goals.size()){phase=1;return false;}
            if(System.nanoTime()<nextSurvey)return false;
            Want want=goals.get(goal);
            while(index<sites.size()){
                Row row=sites.get(index++);
                if(row.dusk!=want.dusk || target(row,seed)<want.count || want.count==6 && target(row,seed)!=6)continue;
                BlossomVillage.Plan p=plan(row);
                var base=p.original()==null?p:p.original();
                require(signature(base).equals(row.signature),"Baseline roads, core houses, or faction diverged "+row);
                require(p.houses().size()>=6 && p.houses().size()<=12,"Village size outside range");
                if(examined.add(row.x+","+row.z))System.out.println("NATIVE HOUSE SURVEY "+row+" observed="+p.houses().size()+" target="+target(row,seed));
                if(p.houses().size()==want.count){
                    picks.add(new Pick(row,p));goal++;index=0;
                }
                nextSurvey=System.nanoTime()+120_000_000;return false;
            }
            throw new AssertionError("No eligible actual "+want+" in declared prior sample");
        }
        if(physical==picks.size()){
            save("layouts-"+seed,layouts);
            return true;
        }
        Pick pick=picks.get(physical);
        if(phase==1){load(pick.plan);phase=2;return false;}
        if(!ready(pick.plan))return false;
        JsonObject result=PlacedVillage.inspect(level,pick.plan,true);
        result.addProperty("coreHouseCount",pick.row.old);
        result.addProperty("coreSignature",pick.row.signature);
        result.addProperty("signature",signature(pick.plan));layouts.add(result);
        System.out.println("NATIVE HOUSES VERIFIED "+seed+" "+pick.row.dusk+" "+pick.plan.houses().size()+" "+pick.plan.plaza());
        release(pick.plan);physical++;phase=1;return false;
    }
    private boolean restart()throws Exception{
        if(phase==0){
            JsonArray saved=JsonParser.parseString(Files.readString(OUT.resolve("layouts-"+seed+".json"))).getAsJsonArray();
            for(var item:saved){var r=item.getAsJsonObject();Row row=row(r);var p=plan(row);
                require(signature(p).equals(r.get("signature").getAsString()),"New village reclassified by second start "+row);
                require(SettlementLayout.minimum(level)==384,"Spacing changed on restart");
                if(row.dusk)require(SettlementLayout.duskChanceForSite(level,p.site())==75&&SettlementLayout.duskChanceFrozen(level,p.site()),"Frozen Dusk chance changed");
            }
            save("restart-"+seed,saved);return true;
        }
        return true;
    }
    private boolean migration()throws Exception{
        if(phase==0){
            var samples=JsonParser.parseString(Files.readString(OUT.resolve("layouts-"+seed+".json"))).getAsJsonArray();
            var expanded=samples.asList().stream().map(e->e.getAsJsonObject()).filter(r->r.get("actualHouses").getAsInt()==12).findFirst().orElseThrow();
            var untouched=samples.asList().stream().map(e->e.getAsJsonObject()).filter(r->r.get("actualHouses").getAsInt()!=r.get("coreHouseCount").getAsInt() && r!=expanded && r.get("actualHouses").getAsInt()!=12).findFirst().orElseThrow();
            Row row=row(expanded);var p=plan(row);picks.add(new Pick(row,p));
            require(p.houses().size()==row.old && signature(p).equals(row.signature),"Previously visited site received a different layout");
            if(stage.equals("oldpartial")){
                request(new ChunkPos(p.doorway(p.houses().getFirst())));
            }else{
                oldState=JsonParser.parseString(Files.readString(OUT.resolve("oldpartial-"+seed+".json"))).getAsJsonObject();
                require(signature(plan(row(untouched))).equals(untouched.get("signature").getAsString()),"Untouched sister site lost its new layout after upgrade");
                load(p);
            }
            phase=1;return false;
        }
        var p=picks.getFirst().plan;
        if(stage.equals("oldpartial")){
            if(!tickets.stream().allMatch(this::loaded))return false;
            long missing=p.houses().stream().map(p::doorway).map(ChunkPos::new).filter(c->!loaded(c)).distinct().count();
            require(missing>0,"Baseline fixture fully loaded all houses");
            var door=p.doorway(p.houses().getFirst());
            require(level.getBlockState(door).getBlock() instanceof net.minecraft.world.level.block.DoorBlock,"Baseline house missing");
            var marker=door.below(3);level.setBlockAndUpdate(marker,Blocks.DIAMOND_BLOCK.defaultBlockState());
            var state=new JsonObject();state.addProperty("marker",marker.asLong());state.addProperty("door",level.getBlockState(door).toString());
            state.addProperty("oldHouseCount",p.houses().size());state.addProperty("unloadedHouseChunks",missing);
            save("oldpartial-"+seed,state);return true;
        }
        if(!ready(p))return false;
        require(level.getBlockState(BlockPos.of(oldState.get("marker").getAsLong())).is(Blocks.DIAMOND_BLOCK),"Old player edit lost");
        require(level.getBlockState(p.doorway(p.houses().getFirst())).toString().equals(oldState.get("door").getAsString()),"Existing doorway changed");
        var full=PlacedVillage.inspect(level,p,false);require(full.get("actualHouses").getAsInt()==oldState.get("oldHouseCount").getAsInt(),"Old footprint gained homes");
        save("upgrade-"+seed,full);return true;
    }
    private Row row(JsonObject evidence){return sites.stream().filter(r->r.x==evidence.get("gx").getAsInt()&&r.z==evidence.get("gz").getAsInt()).findFirst().orElseThrow();}
    private void load(BlossomVillage.Plan p){
        var a=new ChunkPos(p.site().pos(p.x()-40,0,p.z()-52));var b=new ChunkPos(p.site().pos(p.x()+40,0,p.z()+52));
        for(int x=Math.min(a.x,b.x);x<=Math.max(a.x,b.x);x++)for(int z=Math.min(a.z,b.z);z<=Math.max(a.z,b.z);z++)request(new ChunkPos(x,z));
        var cp=new ChunkPos(p.plaza());level.getChunkSource().addRegionTicket(TicketType.FORCED,cp,2,cp,true);
    }
    private void request(ChunkPos cp){tickets.add(cp);level.getChunkSource().addRegionTicket(TicketType.FORCED,cp,0,cp,true);}
    private boolean loaded(ChunkPos cp){return level.getChunkSource().getChunkNow(cp.x,cp.z)!=null;}
    private boolean ready(BlossomVillage.Plan p){return tickets.stream().allMatch(this::loaded)
            &&!level.getEntitiesOfClass(Keeper.class,new AABB(p.plaza()).inflate(78)).isEmpty();}
    private void release(BlossomVillage.Plan p){
        for(var cp:tickets)level.getChunkSource().removeRegionTicket(TicketType.FORCED,cp,0,cp,true);tickets.clear();
        var cp=new ChunkPos(p.plaza());level.getChunkSource().removeRegionTicket(TicketType.FORCED,cp,2,cp,true);
    }
    private static void save(String name,com.google.gson.JsonElement value)throws Exception{
        Files.createDirectories(OUT);Files.writeString(OUT.resolve(name+".json"),new GsonBuilder().setPrettyPrinting().create().toJson(value)+"\n");
    }
    static void require(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
