package dev.uwu.weaponcheck;

import dev.uwu.dimension.NativeChickens;
import dev.uwu.dimension.ReefLife;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.*;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.common.NeoForge;

@Mod("uwu_chicken_check")
public final class ChickenGallery {
    record Saved(String[] ids) {}
    record Scene(int index,String label,int entityId,String uuid,String expected,double px,double py,double pz,
                 double targetX,double targetY,double targetZ) {}
    private static final String WORLD="ChickenPasturesQA";
    private static final boolean RESTART=System.getenv("UWU_QA_PASS").equals("reload");
    private static final String PASS=RESTART?"reload":"fresh";
    private static final boolean SHADER_SPOT=Boolean.parseBoolean(System.getenv("UWU_QA_SHADER_SPOT"));
    private static final Set<String> parts=new HashSet<>();
    private static Chicken rendering;
    private static Scene scene;
    private static int index, cameraTicks, shots, ticks, animationTicks;
    private static boolean started,joined,prepared,found,closed,startedFinish,rendered;
    private static CompletableFuture<?> serverWork;

    public ChickenGallery() throws Exception {
        CandidateJarProbe.verify();
        Evidence.initialize();
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::frame);
        NeoForge.EVENT_BUS.addListener((RenderFrameEvent.Pre ev)->{ parts.clear(); rendered=false; rendering=null; });
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Pre<?,?> ev)->{
            rendering=ev.getEntity() instanceof Chicken c?c:null;
            if(target()) rendered=true;
        });
        NeoForge.EVENT_BUS.addListener((RenderLivingEvent.Post<?,?> ev)->rendering=null);
    }

    static boolean target() {return rendering!=null && scene!=null && rendering.getUUID().toString().equals(scene.uuid);}
    public static void drawn(ModelResourceLocation id) {
        if (!target()) return;
        var manager=Minecraft.getInstance().getModelManager();
        Evidence.require(manager.getModel(id)!=manager.getMissingModel(),"unbaked chicken part "+id);
        parts.add(id.id().getPath().substring(id.id().getPath().lastIndexOf('/')+1));
    }
    private static Path control(String name) {return Evidence.CONTROL.resolve(PASS+"-"+name+".json");}
    private static void signal(String name,Object data) throws Exception { Evidence.signal(PASS+"-"+name+".json",data); }

    @FunctionalInterface private interface ServerTask { void run() throws Exception; }
    private static CompletableFuture<Void> submit(MinecraftServer server, ServerTask task) {
        return server.submit(() -> {
            try { task.run(); }
            catch (Exception e) { throw new java.util.concurrent.CompletionException(e); }
        });
    }

    private void tick(ClientTickEvent.Post event) {
        var mc=Minecraft.getInstance();
        try {
            if (closed) { mc.stop(); return; }
            if (++ticks%600==0) {
                System.out.println("Chicken camera " + PASS + " role=" + System.getProperty("uwu.check.role")
                        + " index=" + index + " shots=" + shots + " frames=" + cameraTicks
                        + " joined=" + joined + " found=" + found + " screen=" + mc.screen
                        + " position=" + (mc.player==null ? "no player" : mc.player.position()));
            }
            Evidence.require(ticks<14000,"pasture fixture timed out "+PASS+" phase="+index);
            Evidence.require(!Files.exists(control("failed")),"peer failed");
            if (serverWork!=null) { if(!serverWork.isDone()) return; serverWork.join(); serverWork=null; }
            if (!started && mc.screen instanceof TitleScreen title) {
                if (Evidence.OBSERVER) {
                    if (!Files.exists(control("lan"))) return;
                    String endpoint="127.0.0.1:25591";
                    ConnectScreen.startConnecting(title,mc,ServerAddress.parseString(endpoint),
                        new ServerData("Hosted chicken pasture",endpoint,ServerData.Type.LAN),false,null);
                } else if (RESTART) mc.createWorldOpenFlows().openWorld(WORLD,()->mc.setScreen(title));
                else mc.createWorldOpenFlows().createFreshLevel(WORLD,new LevelSettings(WORLD,GameType.CREATIVE,false,
                        Difficulty.PEACEFUL,true,new GameRules(),WorldDataConfiguration.DEFAULT),
                        new WorldOptions(152045L,false,false),WorldPresets::createNormalWorldDimensions,title);
                started=true;return;
            }
            if (mc.player==null) {
                if (started && mc.screen!=null) for(var widget:List.copyOf(mc.screen.children()))
                    if(widget instanceof Button b && Set.of("Proceed","I know what I'm doing!").contains(b.getMessage().getString())){b.onPress();break;}
                return;
            }
            if(mc.level==null || mc.screen!=null || mc.player.tickCount<15)return;
            mc.setWindowActive(true);mc.options.hideGui=true;mc.options.setCameraType(CameraType.FIRST_PERSON);
            var server=mc.getSingleplayerServer();
            if(!joined) {
                if(Evidence.OBSERVER) {
                    Evidence.require(server==null && mc.getConnection()!=null && !mc.getConnection().getConnection().isMemoryConnection(),"observer needs independent TCP client");
                    signal("guest",mc.player.getUUID().toString());
                } else {
                    server.setUsesAuthentication(false);
                    Evidence.require(server.publishServer(GameType.CREATIVE,false,25591),"LAN setup");
                    signal("lan",true);
                }
                joined=true;
            }
            if(!Files.exists(control("guest")))return;
            if(!Evidence.OBSERVER && !prepared) {
                if(server.getPlayerList().getPlayerCount()!=2)return;
                serverWork=submit(server,()->ChickenWorld.prepare(server,RESTART));
                prepared=true; return;
            }
            if(!Evidence.OBSERVER && !found) {
                serverWork=server.submit(()->{
                    if(RESTART) {
                        found=ChickenWorld.bird(ChickenWorld.reach(server),0)!=null && ChickenWorld.bird(ChickenWorld.reach(server),1)!=null
                                && ChickenWorld.bird(ChickenWorld.reach(server),2)!=null;
                    } else found=ChickenWorld.findWild(ChickenWorld.reach(server));
                }); return;
            }
            int count=SHADER_SPOT?6:RESTART?3:20;
            if(index==count) {
                if(!Evidence.OBSERVER && !startedFinish) {
                    startedFinish=true;
                    serverWork=submit(server,()->{
                        if(!RESTART && !SHADER_SPOT) {
                            var ids=Arrays.stream(ChickenWorld.STYLES).map(s->ChickenWorld.birds.get(s).toString()).toArray(String[]::new);
                            server.saveEverything(true,true,false);
                            Evidence.signal("saved.json",new Saved(ids));
                        }
                        signal("finished",true);
                    }); return;
                }
                if(Files.exists(control("finished"))) {
                    if (Evidence.OBSERVER) signal("guest-done",true);
                    if (Evidence.OBSERVER || Files.exists(control("guest-done"))) {
                        System.out.println("NATIVE CHICKEN GALLERY PASS "+PASS+" "+System.getProperty("uwu.check.role"));closed=true;
                    }
                }
                return;
            }
            if(scene==null) {
                if(!Evidence.OBSERVER && !Files.exists(control("scene"+index))) {
                    int i=index;
                    // Two angles for each look suffice when only the shader ZIP changed.
                    int camera=SHADER_SPOT?(i/2)*6+i%2:i;
                    serverWork=submit(server,()->signal("scene"+i,ChickenWorld.scene(ChickenWorld.reach(server),camera,RESTART)));
                    return;
                }
                scene=Evidence.read(PASS+"-scene"+index+".json",Scene.class);
                if(scene==null)return;
                cameraTicks=0;shots=0;animationTicks=0;
            }
            // Either client can see both receipts first. Advance even if the server
            // has already moved the observer away from the preceding camera.
            if(shots==3 && Files.exists(control("host"+index)) && Files.exists(control("observer"+index))) {
                scene=null; index++; return;
            }
            if(!Evidence.OBSERVER && animationTicks++%2==0) {
                var s=scene;int n=animationTicks;
                server.execute(()->ChickenWorld.animate(ChickenWorld.reach(server),s,n));
            }
            if(mc.level.dimension()!=ReefLife.REACH || !(mc.level.getEntity(scene.entityId) instanceof Chicken chicken)
                    || !chicken.getUUID().toString().equals(scene.uuid) || mc.player.distanceToSqr(scene.px,scene.py,scene.pz)>.15)return;
            double dx=scene.targetX-scene.px, dz=scene.targetZ-scene.pz;
            float yaw=(float)Math.toDegrees(Math.atan2(-dx,dz)) + (float)(Math.sin(cameraTicks*.12)*1.5);
            float pitch=(float)-Math.toDegrees(Math.atan2(scene.targetY-mc.player.getEyeY(),Math.hypot(dx,dz)));
            mc.player.setYRot(yaw); mc.player.yRotO=yaw; mc.player.setXRot(pitch); mc.player.xRotO=pitch;
            cameraTicks++;
        } catch(Throwable t){fail(t);}
    }

    private void frame(RenderFrameEvent.Post event) {
        var mc=Minecraft.getInstance();
        if(closed || scene==null || shots>=3 || cameraTicks<12+shots*8 || !rendered || mc.screen!=null)return;
        try {
            var chicken=(Chicken)mc.level.getEntity(scene.entityId);
            boolean vanilla=scene.expected.equals("vanilla"),petal=scene.expected.equals("blossoms");
            byte expected=(byte)(vanilla?0:petal?1:2);
            if(NativeChickens.visibleCoat(chicken)!=expected && cameraTicks<90) return;
            Evidence.require(NativeChickens.visibleCoat(chicken)==expected,"wrong synchronized coat "+scene.label);
            String skin=mc.getEntityRenderDispatcher().getRenderer(chicken).getTextureLocation(chicken).toString();
            String expectedSkin=vanilla?"minecraft:textures/entity/chicken.png":"uwudimension:textures/entity/native_coats/"+(petal?"petal":"dusk")+"_chicken.png";
            Evidence.require(skin.equals(expectedSkin),"body skin "+skin+" for "+scene.label);
            Set<String> expectedParts=vanilla?Set.of():Set.of("chicken_"+scene.expected,petal?"petal_wing":"dusk_wing");
            Evidence.require(parts.equals(expectedParts),"actual accessories "+parts+" expected "+expectedParts+" in "+scene.label);
            if (System.getenv("UWU_SHADER_ZIP")!=null && shots==0) {
                var iris=Class.forName("net.irisshaders.iris.Iris");
                var manager=iris.getMethod("getPipelineManager").invoke(null);
                var pipe=manager.getClass().getMethod("getPipelineNullable").invoke(manager);
                String name=pipe==null?"none":pipe.getClass().getName();
                boolean enabled=(boolean)iris.getMethod("isPackInUseQuick").invoke(null);
                Evidence.require(enabled==(Evidence.OBSERVER!=RESTART) && (!enabled || name.contains("IrisRenderingPipeline")),
                        "wrong shader pipeline: "+name+" enabled="+enabled);
                Evidence.line("shader.tsv",PASS+"\t"+scene.label+"\t"+enabled+"\t"+name);
            }
            String key=PASS+"-"+scene.label+"-"+shots;
            Evidence.screenshot(key);
            Evidence.line("chickens.tsv",key+"\t"+scene.uuid+"\t"+skin+"\t"+parts+"\t"+chicken.walkAnimation.speed());
            if(++shots==3)signal(System.getProperty("uwu.check.role")+index,true);
        } catch(Throwable t){fail(t);}
    }
    private static void fail(Throwable t) {
        System.err.println("NATIVE CHICKEN CHECK FAILED "+PASS+" index="+index+" scene="+scene);
        t.printStackTrace();
        try { signal("failed",t.toString()); } catch(Exception ignored) {}
        closed=true;
    }
}
