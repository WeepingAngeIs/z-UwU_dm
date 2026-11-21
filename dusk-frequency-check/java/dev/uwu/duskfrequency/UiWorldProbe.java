package dev.uwu.duskfrequency;

import static dev.uwu.duskfrequency.FrequencySupport.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.uwu.dimension.AcquisitionPolicy;
import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.GroveSites;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.SettlementLayout;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.storage.LevelResource;

/** Consumes only changes already made through the real client GUI and native network path. */
final class UiWorldProbe {
    final JsonArray edits=new JsonArray();
    private final Set<ChunkPos> tickets=new LinkedHashSet<>();
    private ServerLevel level;
    private BlossomVillage.Plan accepted,zero,all,petal;
    private int target=-1;

    void setup(ServerPlayer owner)throws Exception{
        require(owner.hasPermissions(2),"Integrated fixture operator lacks real config permissions");
        level=owner.getServer().getLevel(ReefLife.REACH);require(level!=null,"Sakura Reach missing");
        require(AcquisitionPolicy.duskVillageChancePercent()==75,"GUI fixture did not start at default75");
        var source=read("survey-candidate-"+level.getSeed()+".json").getAsJsonArray("cells");
        var a=choose(source,r->has(r,"expanded256")&&!has(r,"original32")&&r.get("accepted75").getAsBoolean()
                &&r.get("plazaBiome").getAsString().equals("uwudimension:velvet_highlands"),"GUI recovered Velvet75 site");
        var z=choose(source,r->has(r,"expanded256")&&r.get("roll").getAsInt()>=75,"GUI zero-denial site");
        var h=choose(source,r->has(r,"expanded256")&&r.get("roll").getAsInt()>=75&&!r.equals(z),"GUI untouched100 site");
        var p=choose(source,r->has(r,"petal"),"GUI Petal control");
        accepted=BlossomVillage.potentialPlan(level,site(level,a),true).orElseThrow();
        zero=BlossomVillage.potentialPlan(level,site(level,z),true).orElseThrow();
        all=BlossomVillage.potentialPlan(level,site(level,h),true).orElseThrow();
        petal=BlossomVillage.plan(level,site(level,p),false).orElseThrow();
        for(var plan:List.of(accepted,zero,all))require(!SettlementLayout.duskChanceFrozen(level,plan.site()),"GUI test preview froze a site");
        level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,owner.getServer());
        level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,owner.getServer());level.setDayTime(6000);
    }
    void begin(ServerPlayer owner,int value)throws Exception{
        target=value;
        require(AcquisitionPolicy.duskVillageChancePercent()==value,"Native GUI Save did not reach actual UwU getter");
        var path=owner.getServer().getWorldPath(LevelResource.ROOT).resolve("serverconfig/uwudimension-server.toml");
        String toml=Files.readString(path);
        require(Pattern.compile("(?m)^duskVillageChancePercent\\s*=\\s*"+value+"\\s*$").matcher(toml).find(),"Native GUI Save did not persist server TOML");
        require(signature(BlossomVillage.plan(level,petal.site(),false)).equals(signature(petal)),"GUI edit changed Petal plan");
        require(BlossomVillage.plan(owner.getServer().overworld(),accepted.site(),true).isEmpty(),"GUI setting enabled UwU worldgen outside Sakura Reach");
        if(value==0){
            for(var plan:List.of(accepted,zero,all))require(BlossomVillage.plan(level,plan.site(),true).isEmpty(),"GUI0 admitted new site");
            request(level,tickets,edge(zero));
        }else if(value==75){
            require(BlossomVillage.plan(level,accepted.site(),true).isPresent(),"GUI75 failed recovered site");
            require(BlossomVillage.plan(level,all.site(),true).isEmpty(),"GUI75 admitted high roll");
            require(BlossomVillage.plan(level,zero.site(),true).isEmpty(),"GUI75 changed touched0 decision");
            chunks(accepted).forEach(c->request(level,tickets,c));entityTicket(level,accepted);
        }else if(value==100){
            require(BlossomVillage.plan(level,all.site(),true).isPresent(),"GUI100 failed untouched high-roll site");
            require(BlossomVillage.plan(level,zero.site(),true).isEmpty(),"GUI100 changed touched0 decision");
            require(BlossomVillage.plan(level,accepted.site(),true).isPresent(),"GUI100 changed accepted75 decision");
            chunks(all).forEach(c->request(level,tickets,c));entityTicket(level,all);
        }else throw new AssertionError("Unexpected GUI fixture percentage");
    }
    boolean tick(ServerPlayer owner){
        if(!tickets.stream().allMatch(c->loaded(level,c)))return false;
        var plan=target==0?zero:target==75?accepted:all;
        require(SettlementLayout.duskChanceFrozen(level,plan.site()),"Actual GUI-driven native generation did not freeze policy");
        require(SettlementLayout.duskChanceForSite(level,plan.site())==target,"Native generator did not consume GUI percentage");
        int residents=0;
        if(target!=0){
            if(!residentsReady(level,plan))return false;
            residents=nativeVillage(level,plan);
            if(target==75)require(nativeBiome(level,plan).equals("uwudimension:velvet_highlands"),"GUI-driven recovered village is not in Velvet Highlands");
        }
        var row=new JsonObject();row.addProperty("percentage",target);row.addProperty("serverGetter",AcquisitionPolicy.duskVillageChancePercent());
        row.addProperty("tomlSaved",true);row.addProperty("site",signature(plan));row.addProperty("roll",SettlementLayout.duskSiteRoll(level,plan.site()));
        row.addProperty("frozenPercentage",SettlementLayout.duskChanceForSite(level,plan.site()));row.addProperty("nativeResidents",residents);
        row.addProperty("nativeVillageGenerated",target!=0);row.addProperty("path","Native ConfigValuesScreen keyboard/click Save -> ConfigEditor.Action network packet -> server handler -> UwU getter -> native generation");
        if(target!=0)row.addProperty("nativeBiome",nativeBiome(level,plan));edits.add(row);
        return true;
    }
}
