package dev.uwu.duskfrequency;

import static dev.uwu.duskfrequency.FrequencySupport.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.uwu.dimension.AcquisitionPolicy;
import dev.uwu.dimension.AuthoredFootprints;
import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.SettlementLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.IOUtilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Native seeded surveys and disposable disk worlds. All entry points run on the server thread. */
final class FrequencyServer {
    private final String stage=System.getProperty("uwu.check.frequencyStage");
    private final Set<ChunkPos> tickets=new LinkedHashSet<>();
    private final Map<ChunkPos,Boolean> fresh=new ConcurrentHashMap<>();
    private final List<BlossomVillage.Plan> plans=new ArrayList<>();
    private final JsonArray rows=new JsonArray();
    private ServerLevel level;
    private GroveSites.Site origin;
    private BlossomVillage.Plan accepted,rejected,high,petal;
    private CompoundTag saved;
    private int phase,index;
    private long started;
    private int usable,assignedDusk,viable32,viable256,accepted75,petalCount;

    FrequencyServer(){NeoForge.EVENT_BUS.addListener(this::onChunkLoad);}
    private void onChunkLoad(ChunkEvent.Load event){
        if(event.getLevel() instanceof ServerLevel world&&world.dimension().equals(ReefLife.REACH))fresh.putIfAbsent(event.getChunk().getPos(),event.isNewChunk());
    }
    boolean tick(MinecraftServer server)throws Exception{
        if(level==null){
            level=server.getLevel(ReefLife.REACH);require(level!=null,"Sakura Reach absent");
            require(level.getSeed()==Long.parseLong(System.getProperty("uwu.check.seed")),"Native world seed differs from fixture");
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,server);level.setDayTime(6000);
            started=System.nanoTime();origin=GroveSites.first(level);
        }
        require(System.nanoTime()-started<900_000_000_000L,"Stage exceeded15minutes: "+stage+" phase="+phase+" cell="+index);
        if(stage.startsWith("survey-"))return survey();
        return switch(stage){
            case "native-sample"->nativeSample();
            case "fresh"->freshWorld(server);
            case "restart"->restart(server);
            case "legacy-seed"->legacySeed(server);
            case "legacy-upgrade","legacy-restart"->legacy(server);
            default->throw new AssertionError("Unknown stage "+stage);
        };
    }
    private boolean nativeSample()throws Exception{
        if(phase==0){
            var source=read("survey-candidate-"+level.getSeed()+".json").getAsJsonArray("cells");
            var row=choose(source,r->has(r,"expanded256")&&r.get("accepted75").getAsBoolean(),"native accepted75 sample on seed"+level.getSeed());
            percent(75,true);accepted=BlossomVillage.plan(level,site(level,row),true).orElseThrow();
            require(!SettlementLayout.duskChanceFrozen(level,accepted.site()),"Fresh sample was already frozen");
            chunks(accepted).forEach(c->request(level,tickets,c));entityTicket(level,accepted);phase=1;return false;
        }
        if(!allLoaded()||!residentsReady(level,accepted))return false;
        int residents=nativeVillage(level,accepted);frozen(accepted,75);
        var report=metadata(stage,level);report.addProperty("plan",signature(accepted));report.addProperty("nativeBiome",nativeBiome(level,accepted));
        report.addProperty("nativeDuskVillages",1);report.addProperty("nativeDuskResidents",residents);report.addProperty("duskCupidShrines",0);
        write("native-sample-"+level.getSeed()+".json",report);
        System.out.println("DUSK FREQUENCY NATIVE SEED PASS seed="+level.getSeed()+" villages=1 residents="+residents);return true;
    }
    private boolean survey()throws Exception{
        boolean candidate=stage.equals("survey-candidate");
        int side=RADIUS*2+1,total=side*side;
        if(index<total){
            int dx=index/side-RADIUS,dz=index%side-RADIUS;++index;
            int stride=Math.ceilDiv(SettlementLayout.minimum(level)+600,GroveSites.SPACING);
            var site=GroveSites.site(level,origin.gx()+dx*stride,origin.gz()+dz*stride);
            var row=cell(site);row.addProperty("lattice",true);
            row.addProperty("petal","");row.addProperty("original32","");row.addProperty("expanded256","");
            row.addProperty("assignedDusk",false);row.addProperty("accepted75",false);row.addProperty("roll",-1);
            if(site.usable()){
                usable++;
                var p=BlossomVillage.plan(level,site,false);row.addProperty("petal",signature(p));if(p.isPresent())petalCount++;
                boolean dusk=assigned(level,site,true);row.addProperty("assignedDusk",dusk);
                if(dusk){
                    assignedDusk++;
                    var old=original(level,site,true);row.addProperty("original32",signature(old));if(old.isPresent())viable32++;
                    old.ifPresent(v->row.addProperty("originalPlazaBiome",biome(level,v.plaza().below())));
                    if(candidate){
                        require(!SettlementLayout.duskChanceFrozen(level,site),"Survey unexpectedly touched/froze site");
                        require(SettlementLayout.duskSurveyAttempts(level,site)==256,"Fresh Dusk survey budget not256");
                        var expanded=BlossomVillage.potentialPlan(level,site,true);
                        if(old.isPresent())require(signature(old).equals(signature(expanded)),"Expanded survey changed an existing first32 success");
                        row.addProperty("expanded256",signature(expanded));if(expanded.isPresent())viable256++;
                        int roll=SettlementLayout.duskSiteRoll(level,site);row.addProperty("roll",roll);
                        require(roll>=0&&roll<100,"Roll out of range");
                        percent(0,false);require(BlossomVillage.plan(level,site,true).isEmpty(),"Zero admitted new Dusk site");
                        percent(75,false);var current=BlossomVillage.plan(level,site,true);
                        require(current.isPresent()==(expanded.isPresent()&&roll<75),"75% threshold differs from fixed roll");
                        row.addProperty("accepted75",current.isPresent());if(current.isPresent())accepted75++;
                        percent(100,false);require(signature(BlossomVillage.plan(level,site,true)).equals(signature(expanded)),"100 lost eligible Dusk site");
                        require(signature(BlossomVillage.plan(level,site,false)).equals(signature(p)),"Dusk percentage changed Petal plan");
                        percent(75,false);require(!SettlementLayout.duskChanceFrozen(level,site),"Pure percentage preview froze a site");
                        expanded.ifPresent(v->{row.addProperty("plazaX",v.plaza().getX());row.addProperty("plazaY",v.plaza().getY());row.addProperty("plazaZ",v.plaza().getZ());row.addProperty("plazaBiome",biome(level,v.plaza().below()));});
                    }else{
                        require(signature(BlossomVillage.plan(level,site,true)).equals(signature(old)),"Baseline plan differs from original32");
                    }
                }
            }
            rows.add(row);
            if(index%50==0)System.out.println("DUSK SURVEY PROGRESS stage="+stage+" seed="+level.getSeed()+" cells="+index+"/"+total);
            return false;
        }
        var report=metadata(stage,level);report.addProperty("originGx",origin.gx());report.addProperty("originGz",origin.gz());
        report.addProperty("windowRadiusInLatticeCells",RADIUS);report.addProperty("latticeCells",rows.size());
        report.addProperty("minimumSpacingBlocks",SettlementLayout.minimum(level));
        report.addProperty("latticeSpacingBlocks",GroveSites.SPACING*Math.ceilDiv(SettlementLayout.minimum(level)+600,GroveSites.SPACING));
        report.addProperty("usableGroves",usable);report.addProperty("assignedDusk",assignedDusk);
        report.addProperty("viableOriginal32",viable32);report.addProperty("viableExpanded256",candidate?viable256:viable32);
        report.addProperty("accepted75",candidate?accepted75:0);report.addProperty("petalPlans",petalCount);
        report.addProperty("previewGeneratedChunks",fresh.size());report.addProperty("actualNativePlacementsInThisSurvey",0);
        int velvetOriginal=0,velvetExpanded=0,velvetAccepted=0;
        for(var entry:rows){var row=entry.getAsJsonObject();
            if(row.has("originalPlazaBiome")&&row.get("originalPlazaBiome").getAsString().equals("uwudimension:velvet_highlands"))velvetOriginal++;
            if(row.has("plazaBiome")&&row.get("plazaBiome").getAsString().equals("uwudimension:velvet_highlands")){velvetExpanded++;if(row.get("accepted75").getAsBoolean())velvetAccepted++;}
        }
        report.addProperty("velvetOriginal32",velvetOriginal);report.addProperty("velvetExpanded256",velvetExpanded);report.addProperty("velvetAccepted75",velvetAccepted);
        require(fresh.isEmpty(),"Terrain survey unexpectedly generated chunks");
        report.add("cells",rows);write(stage+"-"+level.getSeed()+".json",report);
        System.out.println("DUSK FREQUENCY SURVEY PASS stage="+stage+" seed="+level.getSeed()+" cells="+rows.size()+" usable="+usable
                +" assigned="+assignedDusk+" original32="+viable32+" expanded256="+viable256+" accepted75="+accepted75+" petal="+petalCount);
        return true;
    }
    private boolean freshWorld(MinecraftServer server)throws Exception{
        if(phase==0){
            var source=read("survey-candidate-"+level.getSeed()+".json").getAsJsonArray("cells");
            var a=choose(source,r->has(r,"expanded256")&&!has(r,"original32")&&r.get("roll").getAsInt()<75
                    &&r.get("plazaBiome").getAsString().equals("uwudimension:velvet_highlands"),"expanded-only accepted75 Velvet Highlands site");
            var r=choose(source,v->has(v,"expanded256")&&v.get("roll").getAsInt()>=75,"rejected75/accepted100 site");
            var h=choose(source,v->has(v,"expanded256")&&v.get("roll").getAsInt()>=75&&!v.equals(r),"second high-roll site");
            var p=choose(source,v->has(v,"petal"),"Petal control");
            accepted=BlossomVillage.potentialPlan(level,site(level,a),true).orElseThrow();
            rejected=BlossomVillage.potentialPlan(level,site(level,r),true).orElseThrow();
            high=BlossomVillage.potentialPlan(level,site(level,h),true).orElseThrow();
            petal=BlossomVillage.plan(level,site(level,p),false).orElseThrow();
            require(original(level,accepted.site(),true).isEmpty(),"Recovery fixture was already viable at32");
            reservationInvariant(accepted);
            percent(75,true);request(level,tickets,edge(accepted));phase=1;return false;
        }
        if(phase==1){
            if(!loaded(level,edge(accepted)))return false;
            require(Boolean.TRUE.equals(fresh.get(edge(accepted))),"Accepted footprint was not freshly generated");
            frozen(accepted,75);percent(0,true);
            chunks(accepted).forEach(c->request(level,tickets,c));entityTicket(level,accepted);
            request(level,tickets,edge(rejected));
            chunks(petal).forEach(c->request(level,tickets,c));entityTicket(level,petal);
            phase=2;return false;
        }
        if(phase==2){
            if(!allLoaded()||!residentsReady(level,accepted)||!residentsReady(level,petal))return false;
            nativeVillage(level,accepted);nativeVillage(level,petal);frozen(rejected,0);
            require(nativeBiome(level,accepted).equals("uwudimension:velvet_highlands"),"Native recovered placement is not actually in Velvet Highlands");
            require(Boolean.TRUE.equals(fresh.get(edge(rejected))),"Zero-rejected footprint was not freshly touched");
            require(BlossomVillage.plan(level,rejected.site(),true).isEmpty(),"Rejected zero site acquired plan");
            require(!SettlementLayout.duskChanceFrozen(level,high.site()),"Untouched high-roll site was prematurely frozen");
            require(BlossomVillage.plan(level,high.site(),true).isEmpty(),"Current0 admitted untouched site");
            percent(100,true);
            require(BlossomVillage.plan(level,high.site(),true).isPresent(),"Untouched site did not respond live to100");
            require(BlossomVillage.plan(level,rejected.site(),true).isEmpty(),"Touched zero-rejected site changed after100 edit");
            chunks(rejected).forEach(c->request(level,tickets,c));
            chunks(high).forEach(c->request(level,tickets,c));entityTicket(level,high);
            phase=3;return false;
        }
        if(phase==3){
            if(!allLoaded()||!residentsReady(level,high))return false;
            nativeVillage(level,high);noVillage(rejected);frozen(high,100);percent(0,true);
            frozen(accepted,75);frozen(rejected,0);frozen(high,100);
            saved=new CompoundTag();var list=new ListTag();
            for(var plan:List.of(accepted,high,petal)){
                int chance=plan==accepted?75:plan==high?100:-1;
                var tag=planTag(plan,chance);tag.put("States",blocks(level,chunks(plan),plan.y()));tag.put("Residents",residents(level,plan));list.add(tag);
            }
            saved.put("Plans",list);saved.putInt("RejectedGx",rejected.site().gx());saved.putInt("RejectedGz",rejected.site().gz());
            saved.putString("RejectedPlan",signature(rejected));saved.putInt("GlobalChance",0);
            IOUtilities.writeNbtCompressed(saved,packet(server));
            var report=metadata(stage,level);report.addProperty("expandedOnlyAccepted75",signature(accepted));
            report.addProperty("frozenRejected0",signature(rejected));report.addProperty("untouchedThenAccepted100",signature(high));
            report.addProperty("nativeDuskVillages",2);report.addProperty("nativePetalControls",1);
            report.addProperty("expandedOnlyNativeBiome",nativeBiome(level,accepted));report.addProperty("accepted100NativeBiome",nativeBiome(level,high));
            report.addProperty("preservedAcrossLiveEdit",true);report.addProperty("zeroBlankFootprintTouched",true);
            report.addProperty("duskCupidShrines",0);write("fresh.json",report);
            write("native-sample-"+level.getSeed()+".json",report);
            System.out.println("DUSK FREQUENCY NATIVE SEED PASS seed="+level.getSeed()+" villages=2 expandedOnlyVelvet=1");
            System.out.println("DUSK FREQUENCY PLACEMENT PASS nativeDusk=2 nativePetal=1 expandedOnly=1 frozenZeroBlank=1");return true;
        }
        return false;
    }
    private boolean restart(MinecraftServer server)throws Exception{
        if(phase==0){
            saved=NbtIo.readCompressed(packet(server),NbtAccounter.unlimitedHeap());
            require(AcquisitionPolicy.duskVillageChancePercent()==saved.getInt("GlobalChance"),"Server TOML did not survive restart");
            for(var tag:saved.getList("Plans",Tag.TAG_COMPOUND)){
                var row=(CompoundTag)tag;var plan=plan(level,row);plans.add(plan);
                if(plan.dusk()){frozen(plan,row.getInt("Chance"));require(SettlementLayout.duskSurveyAttempts(level,plan.site())==256,"New saved site was misclassified as pre-upgrade");}
                chunks(plan).forEach(c->request(level,tickets,c));entityTicket(level,plan);
            }
            var site=GroveSites.site(level,saved.getInt("RejectedGx"),saved.getInt("RejectedGz"));
            rejected=BlossomVillage.potentialPlan(level,site,true).orElseThrow();
            require(signature(rejected).equals(saved.getString("RejectedPlan")),"Rejected site geometry changed on restart");
            frozen(rejected,0);chunks(rejected).forEach(c->request(level,tickets,c));phase=1;return false;
        }
        if(!allLoaded())return false;
        for(var tag:saved.getList("Plans",Tag.TAG_COMPOUND))if(!residentsLoaded(level,((CompoundTag)tag).getList("Residents",Tag.TAG_COMPOUND)))return false;
        for(var tag:saved.getList("Plans",Tag.TAG_COMPOUND)){
            var row=(CompoundTag)tag;verifyBlocks(level,row.getCompound("States"));verifyResidents(level,row.getList("Residents",Tag.TAG_COMPOUND));nativeVillage(level,plan(level,row));
        }
        noVillage(rejected);percent(100,false);require(BlossomVillage.plan(level,rejected.site(),true).isEmpty(),"Restart lost frozen zero decision");percent(0,false);
        var report=metadata(stage,level);report.addProperty("acceptedAndRejectedPolicySurvivedRestart",true);
        report.addProperty("structureAndResidentSnapshotsEqual",true);write("restart.json",report);
        System.out.println("DUSK FREQUENCY RESTART PASS accepted75 accepted100 rejected0 structures residents");return true;
    }
    private boolean legacySeed(MinecraftServer server)throws Exception{
        if(phase==0){
            var baseline=read("survey-baseline-"+level.getSeed()+".json").getAsJsonArray("cells");
            var candidate=read("survey-candidate-"+level.getSeed()+".json").getAsJsonArray("cells");
            var d=choose(baseline,r->has(r,"original32"),"baseline Dusk site");
            var p=choose(baseline,r->has(r,"petal"),"baseline Petal site");
            plans.add(BlossomVillage.plan(level,site(level,d),true).orElseThrow());plans.add(BlossomVillage.plan(level,site(level,p),false).orElseThrow());
            var blank=choose(candidate,r->has(r,"expanded256")&&!has(r,"original32"),"old empty expanded-only footprint");
            saved=new CompoundTag();saved.putInt("BlankGx",blank.get("gx").getAsInt());saved.putInt("BlankGz",blank.get("gz").getAsInt());
            var blankPos=new BlockPos(blank.get("plazaX").getAsInt(),blank.get("plazaY").getAsInt(),blank.get("plazaZ").getAsInt());
            saved.putLong("BlankPos",blankPos.asLong());saved.putLong("BlankChunk",new ChunkPos(blankPos).toLong());
            require(BlossomVillage.plan(level,site(level,blank),true).isEmpty(),"Baseline blank control unexpectedly viable");
            for(var plan:plans)request(level,tickets,edge(plan));request(level,tickets,new ChunkPos(blankPos));phase=1;return false;
        }
        if(!allLoaded()||plans.stream().anyMatch(p->!residentsReady(level,p)))return false;
        var list=new ListTag();
        for(var plan:plans){
            var first=edge(plan);require(!loaded(level,new ChunkPos(plan.plaza())),"Baseline partial fixture accidentally loaded whole center");
            var tag=planTag(plan,100);tag.putLong("OldChunk",first.toLong());
            var states=blocks(level,Set.of(first),plan.y());require(!states.isEmpty(),"No native historical structures in partial chunk");
            var resident=level.getEntitiesOfClass(Keeper.class,new AABB(plan.plaza()).inflate(100)).getFirst();
            resident.setCustomName(Component.literal(plan.dusk()?"Historical Dusk fixture":"Historical Petal fixture"));
            resident.getPersistentData().putString("DuskFrequencyFixture",plan.dusk()?"dusk-existing":"petal-existing");
            resident.getOffers().clear();resident.getOffers().add(new MerchantOffer(new ItemCost(Items.EMERALD,3),new ItemStack(Items.BREAD,2),7,1,0));
            resident.getInventory().setItem(0,new ItemStack(Items.WHEAT,5));
            tag.put("States",states);tag.put("Residents",residents(level,plan));list.add(tag);
        }
        saved.put("Plans",list);
        var blank=BlockPos.of(saved.getLong("BlankPos"));saved.putString("BlankState",level.getBlockState(blank.below()).toString());
        IOUtilities.writeNbtCompressed(saved,packet(server));
        var report=metadata(stage,level);report.addProperty("partialDuskAndPetal",true);report.addProperty("preExistingBlankFootprint",true);write("legacy-seed.json",report);
        System.out.println("DUSK FREQUENCY LEGACY SEED PASS baseline partial Dusk/Petal residents and empty footprint");return true;
    }
    private boolean legacy(MinecraftServer server)throws Exception{
        if(phase==0){
            saved=NbtIo.readCompressed(packet(server),NbtAccounter.unlimitedHeap());percent(0,true);
            for(var tag:saved.getList("Plans",Tag.TAG_COMPOUND)){
                var row=(CompoundTag)tag;var plan=plan(level,row);plans.add(plan);
                if(plan.dusk()){frozen(plan,100);require(SettlementLayout.duskSurveyAttempts(level,plan.site())==32,"Historical Dusk received expanded budget");}
                request(level,tickets,new ChunkPos(row.getLong("OldChunk")));
            }
            request(level,tickets,new ChunkPos(saved.getLong("BlankChunk")));phase=1;return false;
        }
        if(phase==1){
            if(!allLoaded())return false;
            for(var tag:saved.getList("Plans",Tag.TAG_COMPOUND))if(!residentsLoaded(level,((CompoundTag)tag).getList("Residents",Tag.TAG_COMPOUND)))return false;
            for(var tag:saved.getList("Plans",Tag.TAG_COMPOUND)){
                var row=(CompoundTag)tag;verifyBlocks(level,row.getCompound("States"));verifyResidents(level,row.getList("Residents",Tag.TAG_COMPOUND));
                require(Boolean.FALSE.equals(fresh.get(new ChunkPos(row.getLong("OldChunk")))),"Historical chunk regenerated");
            }
            var site=GroveSites.site(level,saved.getInt("BlankGx"),saved.getInt("BlankGz"));
            require(SettlementLayout.duskChanceFrozen(level,site)&&SettlementLayout.duskSurveyAttempts(level,site)==32,"Old blank footprint not historically protected");
            percent(100,false);require(BlossomVillage.plan(level,site,true).isEmpty(),"Old empty footprint gained recovered Dusk village");percent(0,false);
            var blank=BlockPos.of(saved.getLong("BlankPos"));require(level.getBlockState(blank.below()).toString().equals(saved.getString("BlankState")),"Old empty terrain was rewritten");
            for(var plan:plans){chunks(plan).forEach(c->request(level,tickets,c));entityTicket(level,plan);}phase=2;return false;
        }
        if(!allLoaded()||plans.stream().anyMatch(p->!residentsReady(level,p)))return false;
        for(var plan:plans)nativeVillage(level,plan);
        for(var tag:saved.getList("Plans",Tag.TAG_COMPOUND)){var row=(CompoundTag)tag;verifyBlocks(level,row.getCompound("States"));verifyResidents(level,row.getList("Residents",Tag.TAG_COMPOUND));}
        var report=metadata(stage,level);report.addProperty("partialPlansCompletedWithoutMoving",true);report.addProperty("savedBlocksAndResidentsEqual",true);
        report.addProperty("oldEmptyFootprintRemainsEmpty",true);report.addProperty("historicalDuskBudget",32);report.addProperty("historicalDuskChance",100);write(stage+".json",report);
        System.out.println("DUSK FREQUENCY LEGACY PASS stage="+stage+" partial Dusk/Petal plans structures residents blank terrain");return true;
    }
    private void reservationInvariant(BlossomVillage.Plan plan){
        percent(0,false);AuthoredFootprints.clear(level);String zero=AuthoredFootprints.cell(level,plan.site().gx(),plan.site().gz()).toString();
        percent(100,false);AuthoredFootprints.clear(level);String all=AuthoredFootprints.cell(level,plan.site().gx(),plan.site().gz()).toString();
        require(zero.equals(all),"Config edit changed structure reservations");
        require(zero.contains("dusk_village"),"Rejected future Dusk village lost its reserved envelope");
        percent(75,false);require(!SettlementLayout.duskChanceFrozen(level,plan.site()),"Reservation preview froze a site");
    }
    private void frozen(BlossomVillage.Plan plan,int chance){
        require(SettlementLayout.duskChanceFrozen(level,plan.site()),"Actual first-touch did not freeze site");
        require(SettlementLayout.duskChanceForSite(level,plan.site())==chance,"Frozen percentage changed from "+chance);
        require(BlossomVillage.plan(level,plan.site(),true).isPresent()==(SettlementLayout.duskSiteRoll(level,plan.site())<chance),"Plan does not use saved decision");
    }
    private void noVillage(BlossomVillage.Plan plan){
        require(BlossomVillage.plan(level,plan.site(),true).isEmpty(),"Rejected site has live plan");
        require(!BuiltInRegistries.BLOCK.getKey(level.getBlockState(plan.plaza().below()).getBlock()).getPath().equals("midnight_lace"),"Rejected site generated native plaza");
        require(level.getEntitiesOfClass(Keeper.class,new AABB(plan.plaza()).inflate(100)).isEmpty(),"Rejected site generated villagers");
    }
    private boolean allLoaded(){return tickets.stream().allMatch(c->loaded(level,c));}
    private Path packet(MinecraftServer server){return server.getWorldPath(LevelResource.ROOT).resolve("dusk-frequency-check.nbt");}
}
