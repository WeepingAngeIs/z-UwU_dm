package dev.uwu.weaponcheck;

import dev.uwu.weaponcheck.mixin.PairedMouse;
import java.nio.file.Files;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.lwjgl.glfw.GLFW;

/** One ordinary mouse attack per marked stack. Both clients stamp their native rendered frames. */
public final class PairedTrial {
    record Trial(int number, String id) {
        String token() { return "paired-" + number + "-" + id; }
    }
    private static final List<String> IDS = new ArrayList<>();
    static {
        for (String material : List.of("diamond","iron","gold","netherite","runic"))
            for (String shape : List.of("katana","sai","rapier","twinblade"))
                IDS.add("counter_" + material + "_" + shape);
    }
    private static Trial trial;
    private static int armedAt, firedAt = -1, index;
    private static long lastFrame = -1, onset = -1;
    private static String clip = "";
    private static boolean ready, pre, recovery;
    private static UUID owner;

    static void serverTick(ServerTickEvent.Post event) {
        if (Evidence.OBSERVER || owner == null) return;
        try {
            Evidence.line("server-clock.tsv", System.currentTimeMillis()+"\t"+event.getServer().overworld().getGameTime());
        } catch (Exception e) { PairedFixture.fail(e); }
    }
    private static void begin(Trial next) {
        trial = next; lastFrame = onset = -1; clip = ""; ready = pre = recovery = false;
    }
    public static void animation(AbstractClientPlayer actor, String name) {
        if (trial != null && actor.getUUID().equals(owner) && PairedFixture.marker(actor.getMainHandItem()).equals(trial.token())) {
            clip = name; onset = actor.level().getGameTime();
        }
    }
    static boolean host(Minecraft c) throws Exception {
        owner = c.player.getUUID();
        int tick = c.player.tickCount;
        if (trial == null) {
            if (index == IDS.size()) return true;
            begin(new Trial(index, IDS.get(index)));
            var id = ResourceLocation.fromNamespaceAndPath("uwudimension", trial.id);
            Evidence.require(BuiltInRegistries.ITEM.containsKey(id), "Missing item " + id);
            PairedFixture.equip(c,new ItemStack(BuiltInRegistries.ITEM.get(id)),trial.token(),InteractionHand.MAIN_HAND);
            Evidence.signal("trial.json",trial);
            armedAt=tick; firedAt=-1;
        }
        Evidence.require(tick-armedAt<260,"Trial timeout "+trial.id);
        if (firedAt<0 && pre && tick-armedAt>=35 && Files.exists(Evidence.CONTROL.resolve("ready-"+index+".json"))) {
            Evidence.signal("input-"+index+".json",Map.of("gameTick",c.level.getGameTime()));
            var mouse=(PairedMouse)(Object)c.mouseHandler;
            mouse.hotbarCheck$press(c.getWindow().getWindow(),0,GLFW.GLFW_PRESS,0);
            mouse.hotbarCheck$press(c.getWindow().getWindow(),0,GLFW.GLFW_RELEASE,0);
            firedAt=tick;
        }
        if (firedAt>=0 && tick-firedAt>=52 && recovery && Files.exists(Evidence.CONTROL.resolve("recovered-"+index+".json"))) {
            index++; trial=null;
        }
        return false;
    }
    static void observer(Minecraft c, UUID id) throws Exception {
        owner=id;
        var next=Evidence.read("trial.json",Trial.class);
        if(next==null)return;
        if(trial==null || next.number!=trial.number)begin(next);
        if(!ready && pre) {
            Evidence.signal("ready-"+trial.number+".json",Map.of("observer",c.player.getUUID().toString()));
            ready=true;
        }
    }
    static void frame(Minecraft c, UUID actorId) throws Exception {
        if(trial==null || actorId==null)return;
        var actor=c.level.getPlayerByUUID(actorId);
        if(actor==null || !PairedFixture.marker(actor.getMainHandItem()).equals(trial.token()))return;
        if(Evidence.OBSERVER && !(actor instanceof RemotePlayer))throw new AssertionError("Expected TCP RemotePlayer");
        long tick=c.level.getGameTime();
        if(tick==lastFrame)return;
        float partial=c.gameRenderer.getMainCamera().getPartialTickTime();
        float vanilla=actor.getAttackAnim(partial);
        boolean sai=trial.id.endsWith("_sai");
        if(sai && onset<0 && vanilla>0){onset=tick;clip="vanilla:swing";}
        long age=onset<0?-1:tick-onset;
        boolean active=false;
        float progress=vanilla;
        if(!sai && !Evidence.OBSERVER) {
            var api=Class.forName("net.bettercombat.api.MinecraftClient_BetterCombat");
            active=api.getMethod("getCurrentAttackHand").invoke(c)!=null;
            progress=((Number)api.getMethod("getSwingProgress").invoke(c)).floatValue();
        } else if(sai)active=vanilla>0;
        else active=age>=0 && age<=24;
        String phase;
        if(onset<0 && !pre){phase="rest";pre=true;}
        else if(age>=0 && age<=26)phase="swing";
        else if(age>=36 && !recovery){phase="recovered";recovery=true;}
        else return;
        lastFrame=tick;
        String name=trial.token()+"-"+phase+"-"+tick;
        long millis=System.currentTimeMillis();
        Evidence.screenshot(name);
        String row=String.join("\t",trial.id,trial.token(),actorId.toString(),c.player.getUUID().toString(),name,phase,
                Long.toString(tick),Long.toString(age),Long.toString(millis),clip,Boolean.toString(active),
                Float.toString(progress),Float.toString(partial));
        Evidence.line("samples.tsv",row);
        if(recovery && Evidence.OBSERVER)Evidence.signal("recovered-"+trial.number+".json",Map.of("tick",tick));
    }
}
