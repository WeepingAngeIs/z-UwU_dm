package dev.uwu.lotusclientcheck;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.GuardianFlight;
import dev.uwu.dimension.People;
import dev.uwu.dimension.ReefLife;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Actual Survival attack, real tracked client poses, physical throws, and a separate native observer pass. */
final class GuardianWarningProbe {
    enum Stage { SETUP, READY, ACTIVE, PATROL, FALL, QUIET, END }
    record Trial(int number,int id,boolean dusk,boolean girl,boolean second,boolean arrow,double floor) {}
    static volatile Trial trial;
    static volatile boolean failed,done;
    static int clientStart;
    private static boolean joined;
    private static Trial filming,fired,armed;
    private static int draw=-1;
    private static Stage stage=Stage.SETUP;
    private static BlossomWarden guard,oldDusk,oldPetal;
    private static Witness witness;
    private static BlockPos arena;
    private static int round,shake,winks,grips,flips,ground,peerShake;
    private static long since;
    private static double peak,initialDistance,minHand,maxHand,maxHandHeight,approach,clientPeak;
    private static Vec3 initialGuard,from,release,walkFrom;
    private static float health;
    private static boolean hadGrip,sawAttack;
    private static final JsonArray outcomes=new JsonArray();
    private static StringBuilder track;

    static boolean filming(Trial t){return t==filming;}
    static void fail(Throwable e){
        if(!failed){failed=true;System.err.println("GUARDIAN WARNING CHECK FAILED round="+round+" stage="+stage);e.printStackTrace();}
        done=true;
    }
    static void damage(LivingIncomingDamageEvent event){
        if(event.getEntity()==guard && event.getSource().getEntity() instanceof ServerPlayer player){
            guard.setNoAi(false);
            if(witness==null||player!=witness.player)sawAttack=true;
        }
    }
    static void clientTick(Minecraft mc){
        if(done){
            try {GuardianCamera.finish();} catch(Exception e){fail(e);}
            mc.stop();return;
        }
        if(mc.player==null||mc.level==null||mc.gameMode==null||mc.screen!=null)return;
        joined=true;
        mc.options.setCameraType(GuardianCamera.observer()?CameraType.THIRD_PERSON_BACK:CameraType.FIRST_PERSON);
        var t=trial;
        if(t==null || !(mc.level.getEntity(t.id()) instanceof BlossomWarden mob))return;
        try{
            if(GuardianGallery.pending(t))return;
            var player=mc.player;
            if(t!=filming){filming=t;clientStart=player.tickCount;clientPeak=player.getY();draw=-1;armed=null;}
            clientPeak=Math.max(clientPeak,player.getY());
            if(t!=fired || mob.warningShake() || mob.warningWink()){
                Vec3 aim=mob.position().add(0,t.arrow()&&t!=fired?1.70:2.14,0).subtract(player.getEyePosition());
                player.setYRot((float)Math.toDegrees(Math.atan2(-aim.x,aim.z)));
                player.setXRot((float)-Math.toDegrees(Math.atan2(aim.y,aim.horizontalDistance())));
            }
            if(t==fired||player.tickCount-clientStart<13||!player.onGround())return;
            if(t.arrow()){
                if(!player.getMainHandItem().is(Items.BOW))return;
                if(armed!=t){
                    mc.options.keyUse.setDown(true);
                    require(mc.gameMode.useItem(player,InteractionHand.MAIN_HAND).consumesAction(),"Native bow did not draw");
                    armed=t;draw=player.tickCount;
                }
                if(player.tickCount-draw<22)return;
                mc.options.keyUse.setDown(false);mc.gameMode.releaseUsingItem(player);
                player.getInventory().selected=1;
            }else{
                if(player.distanceToSqr(mob)>3.65*3.65 || !player.getMainHandItem().isEmpty())return;
                mc.gameMode.attack(player,mob);player.swing(InteractionHand.MAIN_HAND);
                if(t.number()==2)mc.gameMode.attack(player,mob);
            }
            fired=t;
        }catch(Throwable e){fail(e);}
    }

    static void serverTick(ServerTickEvent.Post event){
        if(done||!joined||event.getServer().getPlayerList().getPlayers().isEmpty())return;
        var player=event.getServer().getPlayerList().getPlayers().getFirst();
        var level=player.serverLevel();long now=level.getGameTime();
        try{
            require(now-since<900 || stage==Stage.SETUP,"Timed out. "+details(player));
            switch(stage){
                case SETUP -> {
                    arena=new BlockPos(player.getBlockX(),208,player.getBlockZ());
                    courtyard(level);
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
                    level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false,event.getServer());
                    level.setDayTime(6000);level.setWeatherParameters(6000,0,false,false);
                    player.setHealth(player.getMaxHealth());oldVillageControls(level);
                    prepare(player,now);
                }
                case READY -> {
                    if(now-since<36 || !player.onGround())return;
                    if(round==0){
                        require(oldDusk.duskGuardian()&&!oldPetal.duskGuardian(),"Old natural village guardians failed to adopt the original court faction");
                        require(oldDusk.getType()==oldPetal.getType()&&oldDusk.getType()==People.BLOSSOM_WARDEN.get(),"Changed registry identity");
                        oldDusk.discard();oldPetal.discard();
                        System.out.println("GUARDIAN LEGACY PETAL AND DUSK PLAZA PASS");
                    }
                    trial=new Trial(round,guard.getId(),round>=4,round>=2&&round<=5,round==1||round==3,round==0||round==6,arena.getY());
                    next(Stage.ACTIVE,now);
                }
                case ACTIVE -> active(player,now);
                case PATROL -> {
                    require(guard.offender()==null&&guard.getTarget()==null,"Patrol reacquired the warned player");
                    if(guard.position().distanceTo(walkFrom)<1.15)return;
                    System.out.printf(Locale.ROOT,"GUARDIAN WALK AWAY round=%d walked=%.2f%n",round,guard.position().distanceTo(walkFrom));
                    if(round==4){
                        trial=null;player.connection.teleport(player.getX(),arena.getY()+7.5,player.getZ(),0,0);
                        next(Stage.FALL,now);
                    }else{trial=null;round++;prepare(player,now);}
                }
                case FALL -> {
                    if(now-since<25||!player.onGround())return;
                    require(player.getHealth()<health&&player.isAlive(),"Ordinary fall protection leaked beyond the warning");
                    player.setHealth(health);System.out.println("GUARDIAN ORDINARY FALL CONTROL PASS");
                    next(Stage.QUIET,now);
                }
                case QUIET -> {
                    if(now-since<606)return;
                    round++;prepare(player,now);
                }
                case END -> {
                    if(now-since<4)return;
                    Files.createDirectories(GuardianCamera.OUT);
                    Files.writeString(GuardianCamera.OUT.resolve("outcomes.json"),new GsonBuilder().setPrettyPrinting().create().toJson(outcomes));
                    System.out.println("GUARDIAN NATIVE ENCOUNTERS PASS view="+(GuardianCamera.observer()?"observer":"player"));done=true;
                }
            }
        }catch(Throwable e){System.err.println(details(player));fail(e);}
    }

    private static void active(ServerPlayer player,long now)throws Exception{
        var level=player.serverLevel();var t=trial;
        if(!sawAttack)return;
        if(track.length()<230000)track.append(String.format(Locale.ROOT,"%d,%s,%.5f,%.5f,%.5f,%.5f,%.5f,%.5f,%s,%.1f%n",
                now,guard.warningShake()?"shake":guard.warningWink()?"wink":guard.warningFlip()?"flip":guard.gripping()?"grip":guard.chasing()?"chase":"free",
                player.getX(),player.getY(),player.getZ(),guard.getX(),guard.getY(),guard.getZ(),player.onGround(),player.getHealth()));
        if(round==7){
            if(!player.getPersistentData().contains(GuardianFlight.STATE))return;
            require(guard.duskGuardian()&&player.getPersistentData().getCompound(GuardianFlight.STATE).getDouble("Y")>arena.getY()+7000,
                    "Dusk's existing follow-up changed");
            System.out.println("GUARDIAN DUSK PRIOR REPEAT PASS");trial=null;next(Stage.END,now);return;
        }
        require(!player.getPersistentData().contains(GuardianFlight.STATE),"A warning entered the long defensive flight");
        peak=Math.max(peak,player.getY());
        if(player.getUUID().equals(guard.offender())){
            if(guard.chasing())approach=Math.max(approach,guard.position().distanceTo(initialGuard));
            if(guard.warningShake()||guard.warningWink()){
                require(!hadGrip,"Player was held before the visible warning ended");
                require(Math.abs(player.getY()-arena.getY())<.08&&player.onGround(),"Warning occurred after lift-off");
                if(guard.warningShake())shake++;
                if(guard.warningWink())winks++;
                if(shake+winks>=6){
                    Vec3 forward=Vec3.directionFromRotation(0,guard.yBodyRot);
                    Vec3 toward=player.position().subtract(guard.position()).multiply(1,0,1).normalize();
                    require(forward.dot(toward)>.72,"Warning Guardian faced away from the player");
                }
            }
            if(guard.gripping()||guard.warningFlip()){
                if(!hadGrip){require(t.second()?winks>=8:shake>=32,"Animation too short or missing before grab");from=player.position();hadGrip=true;}
                grips++;
                if(grips==1)guard.bindVillage(arena.east(15));
                if(guard.warningFlip()){
                    flips++;
                    Vec3 relative=player.position().subtract(guard.position());
                    require(relative.length()<6,"Held player separated from the backflipping Guardian");
                    double forward=relative.dot(Vec3.directionFromRotation(0,guard.yBodyRot));
                    minHand=Math.min(minHand,forward);maxHand=Math.max(maxHand,forward);maxHandHeight=Math.max(maxHandHeight,relative.y);
                }
                if(round==4&&grips==9&&witness==null){
                    witness=new Witness(level,guard);
                    witness.player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(guard,false));
                    require(player.getUUID().equals(guard.offender()),"Another player displaced the current warning");
                }
            }
        }
        if(witness!=null&&witness.player.getUUID().equals(guard.offender())&&guard.warningShake())peerShake++;
        if(hadGrip&&release==null&&!(player.getUUID().equals(guard.offender())&&(guard.gripping()||guard.warningFlip())))
            release=player.position();
        if(guard.offender()!=null||release==null||!hadGrip)return;
        ground=player.onGround()&&Math.abs(player.getY()-arena.getY())<.07?ground+1:0;
        if(ground<10)return;
        double rise=peak-arena.getY(),distance=player.position().subtract(release).horizontalDistance();
        require(player.isAlive()&&player.getHealth()==health&&guard.getTarget()==null,"First or second warning hurt/reacquired the player");
        if(t.second()){
            require(winks>=8&&shake==0&&flips>=18&&minHand<-.25&&maxHand>.25
                    &&maxHand-minHand>(t.girl()?.70:1.40)&&maxHandHeight>1.80,
                    "Missing Petal wink or held rotation "+details(player));
            require(rise>3&&distance>=16&&distance<=24,"Wrong diagonal warning arc "+details(player)+" travel="+distance);
        }else{
            require(shake>=32&&grips>=8&&winks==0&&flips==0&&rise>=8&&rise<=12.5&&distance<2.2,
                    "Wrong first warning or height "+details(player)+" distance="+distance);
        }
        require(Math.abs(clientPeak-peak)<1.5,"Client and server observed different apexes");
        if(t.arrow())require(initialDistance>=6&&approach>2,"No visible run-up from a real ranged hit");
        if(round==4){
            require(witness!=null&&witness.shortImpulse&&peerShake>=32,"Independent player did not receive its own first warning");
            witness.close(level);witness=null;
        }
        var record=new JsonObject();record.addProperty("trial",round);record.addProperty("variant",t.dusk()?"Dusk":"Petal");
        record.addProperty("sex",t.girl()?"female":"male");record.addProperty("warning",t.second()?2:1);
        record.addProperty("serverRise",rise);record.addProperty("clientRise",clientPeak-arena.getY());
        record.addProperty("horizontal",distance);record.addProperty("groundedShakeTicks",shake);record.addProperty("groundedWinkTicks",winks);
        record.addProperty("gripTicks",grips);record.addProperty("heldFlipTicks",flips);record.addProperty("runUp",approach);
        record.addProperty("health",health);outcomes.add(record);
        Files.createDirectories(GuardianCamera.OUT);Files.writeString(GuardianCamera.OUT.resolve("track-"+round+".csv"),track);
        System.out.println("GUARDIAN VERIFIED "+record);
        walkFrom=guard.position();next(Stage.PATROL,now);
    }
    private static void prepare(ServerPlayer p,long now){
        var level=p.serverLevel();boolean dusk=round>=4,girl=round>=2&&round<=5;
        if(round==0||round==2||round==4||round==6){
            if(guard!=null)guard.discard();
            guard=People.BLOSSOM_WARDEN.get().create(level);require(guard!=null,"Missing registered Guardian");
            var nbt=guard.saveWithoutId(new CompoundTag());nbt.putBoolean("FemaleGuardian",girl);nbt.putBoolean("KeeperSummonedGuardian",true);
            guard.load(nbt);guard.setDuskGuardian(dusk);
            require(BuiltInRegistries.ENTITY_TYPE.getKey(guard.getType()).toString().equals("uwudimension:blossom_warden"),"Changed Guardian registry ID");
            String expected=dusk?"Dusk Guardian":"Petal Guardian";
            require(guard.getName().getString().contains(expected),expected+" display name missing: "+guard.getName().getString());
            var clone=People.BLOSSOM_WARDEN.get().create(level);clone.load(guard.saveWithoutId(new CompoundTag()));
            require(clone.duskGuardian()==dusk&&clone.female()==girl,"Guardian form/sex did not survive NBT");
            guard.moveTo(arena.getX()+.5,arena.getY(),arena.getZ()+.5,0,0);guard.bindVillage(arena);
            require(level.addFreshEntity(guard),"Could not spawn Guardian");
        }
        boolean ranged=round==0||round==6;
        guard.setNoAi(true);guard.setDeltaMovement(Vec3.ZERO);guard.bindVillage(arena);
        guard.teleportTo(arena.getX()+.5,arena.getY(),arena.getZ()+.5);guard.setYRot(0);guard.setYBodyRot(0);guard.setYHeadRot(0);
        p.getInventory().clearContent();p.getInventory().selected=0;
        if(ranged){p.getInventory().setItem(0,new ItemStack(Items.BOW));p.getInventory().setItem(9,new ItemStack(Items.ARROW,4));}
        p.inventoryMenu.broadcastChanges();
        p.connection.send(new net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket(0));
        p.connection.teleport(arena.getX()+.5,arena.getY(),arena.getZ()+(ranged?8.5:3.3),180,0);
        p.fallDistance=0;p.setDeltaMovement(Vec3.ZERO);
        from=release=walkFrom=null;initialGuard=guard.position();initialDistance=p.distanceTo(guard);
        health=p.getHealth();peak=arena.getY();approach=0;minHand=Double.POSITIVE_INFINITY;maxHand=Double.NEGATIVE_INFINITY;maxHandHeight=0;
        shake=winks=flips=grips=ground=peerShake=0;hadGrip=sawAttack=false;
        track=new StringBuilder("tick,phase,playerX,playerY,playerZ,guardianX,guardianY,guardianZ,grounded,health\n");
        next(Stage.READY,now);
    }
    private static void courtyard(ServerLevel level){
        for(int x=-33;x<=33;x++)for(int z=-33;z<=33;z++){
            boolean line=Math.floorMod(x,5)==0||Math.floorMod(z,5)==0;
            var floor=line?Blocks.POLISHED_ANDESITE:(Math.floorMod(x+z,4)==0?Blocks.SMOOTH_STONE:Blocks.POLISHED_DIORITE);
            level.setBlock(arena.offset(x,-1,z),floor.defaultBlockState(),2);
            if(Math.abs(x)==33&&z%4==0||Math.abs(z)==33&&x%4==0){
                level.setBlock(arena.offset(x,0,z),Blocks.CHERRY_LEAVES.defaultBlockState(),2);
                level.setBlock(arena.offset(x,1,z),Blocks.LANTERN.defaultBlockState(),2);
            }
        }
    }
    private static void oldVillageControls(ServerLevel original){
        var world=original.getServer().getLevel(ReefLife.REACH);require(world!=null,"Sakura Reach missing");
        oldDusk=legacy(world,new BlockPos(13,215,13),"midnight_lace");
        oldPetal=legacy(world,new BlockPos(51,215,13),"petal_quilt");
    }
    private static BlossomWarden legacy(ServerLevel level,BlockPos feet,String marker){
        var cp=new net.minecraft.world.level.ChunkPos(feet);
        level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,cp,2,cp,true);
        level.setBlockAndUpdate(feet.below(),BuiltInRegistries.BLOCK.get(ResourceLocation.fromNamespaceAndPath("uwudimension",marker)).defaultBlockState());
        level.setBlockAndUpdate(feet.offset(2,-1,-2),Blocks.STONE.defaultBlockState());
        level.setBlockAndUpdate(feet.offset(2,0,-2),Blocks.BELL.defaultBlockState());
        var entity=People.BLOSSOM_WARDEN.get().create(level);
        // Recreate only actual old fields, with no future faction tag.
        var tag=entity.saveWithoutId(new CompoundTag());
        tag.remove("DuskGuardian");tag.putLong("VillageCourt",feet.asLong());tag.putString("VillageHomeDimension",level.dimension().location().toString());
        entity.readAdditionalSaveData(tag);entity.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5,0,0);
        require(level.addFreshEntity(entity),"Old village Guardian failed to load");return entity;
    }
    private static void next(Stage to,long now){stage=to;since=now;}
    private static String details(ServerPlayer p){
        return "round="+round+" stage="+stage+" shake="+shake+" wink="+winks+" grip="+grips+" flip="+flips+" apex="+peak
                +" feet="+p.position()+" health="+p.getHealth()+" held="+minHand+".."+maxHand+" high="+maxHandHeight
                +" attacker="+sawAttack+" peerShake="+peerShake+" guardian="+(guard==null?"none":guard.position()+" target="+guard.offender());
    }
    private static void require(boolean correct,String text){if(!correct)throw new AssertionError(text);}

    /** Second independent server connection. The first player runs the normal movement client. */
    private static final class Witness extends ServerGamePacketListenerImpl {
        private final EmbeddedChannel channel;
        boolean shortImpulse;

        Witness(ServerLevel level, BlossomWarden guardian) {
            this(level, new Connection(PacketFlow.SERVERBOUND), new ServerPlayer(level.getServer(), level,
                    new GameProfile(UUID.randomUUID(), "SecondGardener"), ClientInformation.createDefault()));
            player.moveTo(guardian.getX()-1.8, guardian.getY(), guardian.getZ(), -90, 0);
            level.getServer().getPlayerList().broadcastAll(ClientboundPlayerInfoUpdatePacket.createPlayerInitializing(List.of(player)));
            level.addNewPlayer(player);
        }
        private Witness(ServerLevel level, Connection connection, ServerPlayer player) {
            super(level.getServer(), connection, player, CommonListenerCookie.createInitial(player.getGameProfile(),false));
            channel = new EmbeddedChannel(connection);
        }
        @Override public void send(Packet<?> packet, PacketSendListener listener) {
            if (packet instanceof ClientboundSetEntityMotionPacket && player.getDeltaMovement().y > 1
                    && player.getDeltaMovement().y < 1.4) shortImpulse = true;
        }
        void close(ServerLevel level) {
            level.removePlayerImmediately(player, Entity.RemovalReason.DISCARDED);
            level.getServer().getPlayerList().broadcastAll(new ClientboundPlayerInfoRemovePacket(List.of(player.getUUID())));
            channel.finishAndReleaseAll();
        }
    }
}
