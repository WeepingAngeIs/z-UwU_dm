package dev.uwu.duskfrequency;

import static dev.uwu.duskfrequency.FrequencySupport.*;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.lwjgl.glfw.GLFW;

/** Actual native Unyielding menu and optional patched companion, in a disposable hosted client. */
@Mod(value="uwu_dusk_frequency_check",dist=Dist.CLIENT)
public final class FrequencyClient {
    private enum Stage { LOAD, SETUP, GUI, CAPTURE, NATIVE, GUARDS, FINISH, DONE }
    private static final int[] TARGETS={0,75,100};
    private static final UiWorldProbe SERVER=new UiWorldProbe();
    private static final JsonObject FRAMES=new JsonObject();
    private static Stage stage=Stage.LOAD;
    private static ConfigGuiDriver driver;
    private static CompletableFuture<Boolean> work;
    private static boolean result,opening,failed;
    private static int edit;
    private static String capture;
    private static JsonArray guards;
    private static Throwable renderFailure;
    private static long started=System.nanoTime();
    private static String companionHash;

    public FrequencyClient()throws Exception{
        CandidateJarProbe.verify();
        require(ModList.get().isLoaded("unyieldingquests"),"Patched optional configuration module is not loaded");
        Path expected=Path.of(System.getProperty("uwu.check.companionJar")).toRealPath();
        Path actual=ModList.get().getModFileById("unyieldingquests").getFile().getFilePath().toRealPath();
        require(actual.equals(expected),"Loaded configuration module differs from hosted companion build");
        companionHash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(actual)));
        NeoForge.EVENT_BUS.addListener(FrequencyClient::tick);NeoForge.EVENT_BUS.addListener(FrequencyClient::frame);
    }
    private static void tick(ClientTickEvent.Post event){
        var client=Minecraft.getInstance();
        try{
            if(failed||stage==Stage.DONE){client.stop();return;}
            if(!opening&&client.screen instanceof TitleScreen title){
                opening=true;GLFW.glfwSetWindowSize(client.getWindow().getWindow(),1280,720);
                client.createWorldOpenFlows().createFreshLevel("UwUDuskFrequencyUI",
                        new LevelSettings("Disposable Dusk settings check",GameType.CREATIVE,false,Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(Long.parseLong(System.getProperty("uwu.check.seed")),false,false),WorldPresets::createNormalWorldDimensions,title);
            }else if(opening&&client.player==null&&client.screen!=null){
                for(var child:List.copyOf(client.screen.children()))if(child instanceof Button button&&(button.getMessage().getString().equals("Proceed")
                        ||button.getMessage().getString().equals("I know what I'm doing!"))){button.onPress();break;}
            }
            if(client.player==null||client.level==null)return;
            require(System.nanoTime()-started<300_000_000_000L,"UI stage timed out: "+stage+" "+(driver==null?"":driver.status()));
            if(renderFailure!=null)throw new AssertionError("Native config frame capture failed",renderFailure);
            if(work!=null){if(!work.isDone())return;result=work.join();work=null;}
            switch(stage){
                case LOAD->{
                    if(client.player.tickCount<70||client.screen!=null)return;
                    action(client,p->{SERVER.setup(p);return true;});next(Stage.SETUP);
                }
                case SETUP->{driver=new ConfigGuiDriver(75,0);next(Stage.GUI);}
                case GUI->{
                    String edited="ui-edited-"+TARGETS[edit];
                    if(driver.status().startsWith("EDITED")&&!FRAMES.has(edited)){capture=edited;return;}
                    driver.tick(client);
                    if(driver.complete()){capture="ui-saved-"+TARGETS[edit];next(Stage.CAPTURE);}
                }
                case CAPTURE->{
                    if(!FRAMES.has("ui-saved-"+TARGETS[edit]))return;
                    client.setScreen(null);
                    action(client,p->{SERVER.begin(p,TARGETS[edit]);return false;});next(Stage.NATIVE);
                }
                case NATIVE->{
                    if(!result){action(client,SERVER::tick);return;}
                    System.out.println("DUSK FREQUENCY UI EDIT PASS percent="+TARGETS[edit]+" nativeMenuPacketAndGeneration=true");
                    if(++edit<TARGETS.length){driver=new ConfigGuiDriver(TARGETS[edit-1],TARGETS[edit]);next(Stage.GUI);}
                    else {action(client,p->{guards=ConfigGuardProbe.run(p);return true;});next(Stage.GUARDS);}
                }
                case GUARDS->{next(Stage.FINISH);}
                case FINISH->{
                    require(FRAMES.size()==6,"Missing native edited/saved menu screenshots");
                    var report=new JsonObject();report.addProperty("sourceSha",System.getenv("GITHUB_SHA"));
                    report.addProperty("candidateSha256",System.getProperty("uwu.check.candidateSha256"));
                    report.addProperty("loadedUwuJarSha256",System.getProperty("uwu.check.candidateSha256"));
                    report.addProperty("companionJarSha256",companionHash);report.addProperty("seed",Long.parseLong(System.getProperty("uwu.check.seed")));
                    report.addProperty("nativePositivePath",true);report.addProperty("positiveEditsUseDirectHandler",false);
                    report.addProperty("guardPath","Actual registered server handler after Action CODEC round-trip; separate from graphical positive edits");
                    report.add("edits",SERVER.edits);report.add("guardChecks",guards);report.add("captures",FRAMES);write("ui.json",report);
                    System.out.println("DUSK FREQUENCY CONFIG GUARDS PASS checks="+guards.size());
                    System.out.println("DUSK FREQUENCY UI PASS native GUI0/75/100 -> packet -> persisted config -> new generation");
                    next(Stage.DONE);client.stop();
                }
                case DONE->client.stop();
            }
        }catch(Throwable failure){failed=true;System.err.println("DUSK FREQUENCY UI CHECK FAILED stage="+stage);failure.printStackTrace();client.stop();}
    }
    private static void frame(RenderFrameEvent.Post event){
        if(capture==null||failed)return;
        try{
            var client=Minecraft.getInstance();require(client.screen!=null,"Config screenshot requested without native screen");
            Files.createDirectories(OUTPUT);
            try(var pixels=Screenshot.takeScreenshot(client.getMainRenderTarget())){
                require(pixels.getWidth()>=800&&pixels.getHeight()>=450,"Native menu screenshot unexpectedly small");
                pixels.writeToFile(OUTPUT.resolve(capture+".png"));
                var row=new JsonObject();row.addProperty("file",capture+".png");row.addProperty("width",pixels.getWidth());row.addProperty("height",pixels.getHeight());
                row.addProperty("screen",client.screen.getClass().getName());row.addProperty("title",client.screen.getTitle().getString());row.addProperty("driver",driver.status());
                FRAMES.add(capture,row);capture=null;
            }
        }catch(Throwable failure){renderFailure=failure;capture=null;}
    }
    private static void action(Minecraft client,ServerAction action){
        var server=client.getSingleplayerServer();UUID uuid=client.player.getUUID();work=new CompletableFuture<>();var completion=work;
        server.execute(()->{try{completion.complete(action.run(server.getPlayerList().getPlayer(uuid)));}catch(Throwable failure){completion.completeExceptionally(failure);}});
    }
    private static void next(Stage value){stage=value;started=System.nanoTime();}
    @FunctionalInterface private interface ServerAction{boolean run(ServerPlayer owner)throws Exception;}
}
