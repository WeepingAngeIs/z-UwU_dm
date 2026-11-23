package dev.uwu.professioncheck;

import dev.uwu.dimension.*;
import dev.uwu.weaponcheck.CandidateJarProbe;
import com.mojang.authlib.GameProfile;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.server.players.PlayerList;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.chunk.*;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import static dev.uwu.professioncheck.QuarterCheck.*;

@Mod(value="uwu_profession_check",dist=Dist.DEDICATED_SERVER)
public final class ProfessionProbe {
    private ServerLevel level;
    private BlossomVillage.Plan village;
    private BlockPos home,room,arena,barrier,covered,previous;
    private ServerPlayer player;
    private BlossomWarden escort,ordinary;
    private Object book,trip;
    private long until,start;
    private int stage,ticks,stageAt;
    private boolean done,sawPick,sawHigher,followed,veto;
    private int denied;
    private final Map<BlockPos,Integer> strikeFlashes=new HashMap<>();
    public ProfessionProbe(){CandidateJarProbe.verify();NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.event.entity.living.LivingDestroyBlockEvent e)->{
            if(veto && e.getEntity()==escort){denied++;e.setCanceled(true);}
        });}
    private void tick(ServerTickEvent.Post event){
        if(done)return;
        try{
            ticks++;
            if(level==null){
                level=event.getServer().getLevel(ReefLife.REACH);check(level!=null,"Reach absent");
                level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false,event.getServer());
                level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false,event.getServer());
                recipes(level);
                village=QuarterCheck.villages(level).stream().filter(v->v.dusk()==(level.getSeed()==20261121L)).findFirst().orElseThrow();home=village.plaza();
                chamber();arena=home.above(85);platform();
                player=new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"EscortVisitor"),ClientInformation.createDefault());
                var wire=new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
                new net.minecraft.server.network.ServerGamePacketListenerImpl(level.getServer(),wire,player,
                        net.minecraft.server.network.CommonListenerCookie.createInitial(player.getGameProfile(),false)){
                    @Override public void send(net.minecraft.network.protocol.Packet<?> packet,net.minecraft.network.PacketSendListener listener){
                        if(stage==5 && packet instanceof net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket flash
                                && flash.getParticle().getType()==net.minecraft.core.particles.ParticleTypes.END_ROD && flash.getCount()==2) {
                            // In this corridor the chips leave the west face of the eastward cut.
                            BlockPos struck=BlockPos.containing(flash.getX()+.5,flash.getY(),flash.getZ());
                            if(struck.distSqr(room)<=2)strikeFlashes.merge(struck,1,Integer::sum);
                        }
                    }
                };
                new io.netty.channel.embedded.EmbeddedChannel(wire);
                player.setInvulnerable(true);player.setNoGravity(true);player.setPos(Vec3.atBottomCenterOf(home));
                ((List<ServerPlayer>)field(level.getServer().getPlayerList(),"players")).add(player);
                ((Map<UUID,ServerPlayer>)field(level.getServer().getPlayerList(),"playersByUUID")).put(player.getUUID(),player);
                level.addNewPlayer(player);
                call(type("GuardianVillageHomes"),"remember",level,home);
                book=call(type("VillageEscorts"),"data",level);
                level.getServer().tickRateManager().requestGameToSprint(30000);
                stage=1;return;
            }
            check(ticks<26800,"Timeout stage="+stage+" actor="+(escort==null?null:escort.position()));
            switch(stage) {
                case 1 -> departure();
                case 2 -> follow();
                case 3 -> hidden();
                case 4 -> climb();
                case 5 -> mining();
                case 6 -> expiry();
                case 7 -> returned();
                case 8 -> ordinaryGuard();
                case 9 -> walkIntoView();
                case 10 -> spawned();
            }
        }catch(Throwable e){done=true;System.err.println("UWU PROFESSION AND ESCORT FAILED stage="+stage+" tick="+ticks);e.printStackTrace();event.getServer().halt(false);}
    }
    private void go(int next){stage=next;stageAt=ticks;}
    private void hold(BlockPos pos) {
        var cp=new ChunkPos(pos);level.getChunkSource().addRegionTicket(TicketType.FORCED,cp,3,cp);level.getChunk(cp.x,cp.z);
    }
    private void chamber()throws Exception {
        // Carve only the verifier's corridors before full promotion. Leave both obstruction blocks as generated stone.
        // The real freshness attachment must be issued by Minecraft's normal new-chunk event.
        for(int attempt=0;attempt<24;attempt++){
            int cx=60035+attempt*61,cz=-60040;
            var cp=new ChunkPos(cx,cz);var c=level.getChunkSource().getChunk(cx,cz,ChunkStatus.FEATURES,true);
            if(c instanceof LevelChunk || c instanceof ImposterProtoChunk || !c.getAllReferences().isEmpty())continue;
            for(int y=10;y<43;y+=2){
                var mid=new BlockPos(cp.getMinBlockX()+8,y,cp.getMinBlockZ()+8);
                var col=level.getChunkSource().getGenerator().getBaseColumn(mid.getX(),mid.getZ(),level,level.getChunkSource().randomState());
                if(!c.getBlockState(mid).is(Blocks.STONE)||!c.getBlockState(mid.above()).is(Blocks.STONE)
                        ||!col.getBlock(y).is(Blocks.STONE)||!col.getBlock(y+1).is(Blocks.STONE)
                        ||!c.getBlockState(mid.east()).is(Blocks.STONE)||!c.getBlockState(mid.east().above()).is(Blocks.STONE)
                        ||AuthoredFootprints.check(level,new net.minecraft.world.level.levelgen.structure.BoundingBox(mid).inflatedBy(4))!=AuthoredFootprints.Decision.CLEAR
                        ||!dryQuarry(c,mid))continue;
                for(int x=-6;x<=6;x++)for(int z=-3;z<=3;z++)for(int dy=-1;dy<=2;dy++){
                    var p=mid.offset(x,dy,z);if((x==0||x==1) && z==0 && (dy==0||dy==1))continue;
                    var state=z==0 && x!=0 && dy>=0 && dy<=1 ? Blocks.AIR:Blocks.STONE;
                    c.setBlockState(p,state.defaultBlockState(),false);
                }
                room=mid;hold(mid);
                System.out.println("ESCORT selected native rock at "+room+" untouched="+call(type("EscortStone"),"untouched",level,room));
                for(var target:List.of(mid,mid.above(),mid.east(),mid.east().above()))
                    check(level.getBlockState(target).is(SakuraMaterials.STONE.get()),"Post-generation obstruction "+target+" was "+level.getBlockState(target));
                return;
            }
        }
        throw new AssertionError("No pristine natural stone for the chamber");
    }
    // Flowing cave water and falling gravel are real ground changes. Select a dry, stable volume so this
    // positive control stays untouched throughout the test. The protection in the actual mod still sees every write.
    private static boolean dryQuarry(ChunkAccess chunk,BlockPos mid) {
        int low=((mid.getY()-4)&~7)-1,high=((mid.getY()+5)|7)+1;
        BlockPos.MutableBlockPos pos=new BlockPos.MutableBlockPos();var cp=chunk.getPos();
        for(int y=low;y<=high;y++)for(int x=cp.getMinBlockX();x<=cp.getMaxBlockX();x++)
            for(int z=cp.getMinBlockZ();z<=cp.getMaxBlockZ();z++){
                pos.set(x,y,z);var state=chunk.getBlockState(pos);
                if(!state.isSolidRender(chunk,pos)||!state.getFluidState().isEmpty() || state.isRandomlyTicking()
                        ||state.getBlock() instanceof FallingBlock)return false;
            }
        return true;
    }
    private void platform() {
        hold(arena);for(int x=-23;x<=23;x++)for(int z=-14;z<=14;z++){
            var p=arena.offset(x,-1,z);level.setBlock(p,Blocks.SMOOTH_QUARTZ.defaultBlockState(),2);
        }
    }
    private void actor(BlockPos p){player.setPos(Vec3.atBottomCenterOf(p));}
    private void guard(BlockPos p){hold(p);escort.getNavigation().stop();escort.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);escort.setDeltaMovement(Vec3.ZERO);}
    private void observe(BlockPos p)throws Exception{actor(p);call(book,"observe",level,player);}
    private Object current()throws Exception{return call(book,"trip",player.getUUID());}
    private int expectedMinutes(){return level.getSeed()==20261121L?20:5;}
    private void departure()throws Exception {
        if(!level.isPositionEntityTicking(home)||!level.isPositionEntityTicking(room))return;
        BlockPos out=null;
        for(Direction d:Direction.Plane.HORIZONTAL){var p=home.relative(d,61);
            if(!(boolean)call(type("VillageEscorts"),"inVillage",level,home,p,8)){out=p;break;}}
        check(out!=null,"No genuine exit");
        int lose=0,win=0;while(RandomSource.create(lose).nextInt(4)==0)lose++;
        for(;;win++){
            var random=RandomSource.create(win);
            if(random.nextInt(4)==0 && 5+random.nextInt(16)==expectedMinutes())break;
        }
        observe(home);player.getRandom().setSeed(lose);observe(out);check(current()==null,"Failed roll created an escort");
        for(int i=0;i<24;i++){player.getRandom().setSeed(win);observe(out);check(current()==null,"Rerolled outside");}
        observe(home);player.getRandom().setSeed(win);observe(out);trip=current();check(trip!=null,"Winning departure failed");
        observe(home);player.getRandom().setSeed(win);observe(out);check(trip==current(),"Duplicate on another departure");
        go(10);
    }
    private void spawned()throws Exception {
        until=(long)field(trip,"until");
        escort=(BlossomWarden)level.getEntity((UUID)field(trip,"guardian"));
        if(until==0 || escort==null){
            check(ticks-stageAt<240,"Reserved escort not tracked: until="+until+" chunk="+field(trip,"last")
                    +" owner="+player.position()+" home="+home+" sameTrip="+(current()==trip));return;
        }
        check(escort.female(),"Male selected for escort");
        check(escort.duskGuardian()==village.dusk() && home.equals(field(trip,"court")),"Origin village or faction changed");
        start=until-(long)field(trip,"duration");
        check(until-start==expectedMinutes()*1200L,"Wrong inclusive follow duration "+(until-start));
        var nbt=escort.saveWithoutId(new CompoundTag());check(nbt.getBoolean("FemaleGuardian")&&nbt.getUUID("VillageEscortOwner").equals(player.getUUID()),"Lost identity on save");
        var restored=call(type("VillageEscorts"),"load",((net.minecraft.world.level.saveddata.SavedData)book).save(new CompoundTag(),level.registryAccess()),level.registryAccess());
        check(home.equals(field(call(restored,"trip",player.getUUID()),"court")),"Return address not persisted");
        level.setDayTime(14500);
        actor(arena.east(10));guard(arena.west(9));go(2);
    }
    private void follow()throws Exception {
        if(escort.distanceToSqr(player)<=22){followed=true;
            System.out.println("ESCORT real night follow female="+escort.female()+" durationTicks="+(until-start));
            // A sealed pen has no walking route. A separate opaque screen near the player conceals the landing.
            var pen=arena.west(17);for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++)if(Math.abs(x)==2 || Math.abs(z)==2)
                for(int y=0;y<5;y++)level.setBlock(pen.offset(x,y,z),Blocks.DEEPSLATE_BRICKS.defaultBlockState(),2);
            covered=arena.east(9);
            for(int z=-3;z<=3;z++)for(int y=0;y<5;y++)level.setBlock(covered.offset(0,y,z),Blocks.DEEPSLATE_BRICKS.defaultBlockState(),2);
            actor(arena.east(15));guard(pen);previous=escort.blockPosition();go(3);
        }else check(ticks-stageAt<500,"Did not follow at night "+escort.position());
    }
    private void hidden()throws Exception {
        if(previous.distSqr(escort.blockPosition())>64){
            check((boolean)call(type("EscortRoute"),"concealed",level,escort,escort.blockPosition()),"Visible teleport");
            System.out.println("ESCORT catch-up hidden by solid cover "+escort.blockPosition());
            previous=escort.blockPosition();go(9);
        }else{previous=escort.blockPosition();check(ticks-stageAt<420,"No hidden catch-up");}
    }
    private void walkIntoView()throws Exception {
        check(previous.distSqr(escort.blockPosition())<=5,"Visible appearance after hidden landing");
        previous=escort.blockPosition();
        if(escort.hasLineOfSight(player) && escort.distanceToSqr(player)<22){
            System.out.println("ESCORT walked naturally into sight after concealed landing");
            // Narrow raised walkway with no concealed landing anywhere on it. The end is visible over the low wall.
            barrier=arena.south(9);for(int x=0;x<=10;x++)for(int z=-1;z<=1;z++)for(int y=0;y<3;y++)
                level.setBlock(barrier.offset(x,y,z),Blocks.SMOOTH_STONE.defaultBlockState(),2);
            actor(barrier.east(5).above(3));guard(barrier.west(1));sawHigher=false;go(4);
        }else check(ticks-stageAt<360,"Did not walk into sight after the hidden catch-up");
    }
    private void climb()throws Exception {
        if(escort.getY()>barrier.getY()+.4 && escort.getY()<barrier.getY()+2.8)sawHigher=true;
        if(escort.getY()>barrier.getY()+2.9 && escort.distanceToSqr(player)<23){
            check(sawHigher,"Appeared on wall instead of climbing");
            System.out.println("ESCORT climbed the unobscured three-block wall on foot");
            if(!(boolean)call(type("EscortStone"),"untouched",level,room))chamber();
            actor(room.east(6));escort.setNoAi(true);
            ordinary=People.BLOSSOM_WARDEN.get().create(level);
            call(ordinary,"setFemale",true);var p=room.west(1);ordinary.moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,0,0);
            level.addFreshEntity(ordinary);go(8);
        }else check(ticks-stageAt<600,"Climb failed "+escort.position());
    }
    private void ordinaryGuard()throws Exception {
        check(!(boolean)call(ordinary,"carryingEscortPick"),"Ordinary female Guardian drew an escort pick");
        for(var target:List.of(room,room.above(),room.east(),room.east().above()))
            check(level.getBlockState(target).is(SakuraMaterials.STONE.get()),"Ordinary encounter changed "+target+" to "+level.getBlockState(target));
        if(ticks-stageAt<135)return;
        System.out.println("ESCORT ordinary female Guardian left all four natural obstruction blocks intact");
        ordinary.discard();escort.setNoAi(false);guard(room.west(1));
        level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(false,level.getServer());go(5);
    }
    private void claim(boolean add)throws Exception {
        var api=Class.forName("xaero.pac.common.server.api.OpenPACServerAPI");
        Object server=api.getMethod("get",net.minecraft.server.MinecraftServer.class).invoke(null,level.getServer());
        var manager=api.getMethod("getServerClaimsManager");
        Object claims=manager.invoke(server);Class<?> contract=manager.getReturnType();
        var dimension=level.dimension().location();var chunk=new ChunkPos(room);
        if(add) {
            var record=contract.getMethod("claim",net.minecraft.resources.ResourceLocation.class,UUID.class,int.class,int.class,int.class,boolean.class)
                    .invoke(claims,dimension,player.getUUID(),0,chunk.x,chunk.z,false);
            check(record!=null,"Installed OpenPAC did not accept the disposable claim");
        }else contract.getMethod("unclaim",net.minecraft.resources.ResourceLocation.class,int.class,int.class).invoke(claims,dimension,chunk.x,chunk.z);
    }
    private String terrain()throws Exception {
        var c=level.getChunkAt(room);var attachment=(java.util.function.Supplier<?>)field(type("EscortStone"),"PRISTINE");
        var registered=(net.neoforged.neoforge.attachment.AttachmentType<?>)attachment.get();
        var raw=level.getChunkSource().getGenerator().getBaseColumn(room.getX(),room.getZ(),level,level.getChunkSource().randomState()).getBlock(room.getY());
        var changes=(java.util.function.Supplier<?>)field(type("EscortStone"),"CHANGED");
        var changed=(net.neoforged.neoforge.attachment.AttachmentType<?>)changes.get();
        return " changedCells="+(c.hasData(changed)?c.getData(changed):"none")+" chunkFresh="+c.hasData(registered)+" references="+c.getAllReferences().size()+" raw="+raw+" wall="+level.getBlockState(room)+"/"+level.getBlockState(room.above())
                +" next="+level.getBlockState(room.east())+"/"+level.getBlockState(room.east().above())+" below="+level.getBlockState(room.below())
                +" untouched="+call(type("EscortStone"),"untouched",level,room)+" owner="+player.blockPosition()+" guard="+escort.position()+" since="+(ticks-stageAt);
    }
    private void mining()throws Exception{
        int age=ticks-stageAt;
        check(escort.blockPosition().distSqr(room)<300,"Appeared outside the sealed chamber");
        if(age<120){
            check(!level.getBlockState(room).isAir()&&!level.getBlockState(room.above()).isAir(),"Mined while mobGriefing off");
            check(escort.getX()<room.getX(),"Popped into open sight");return;
        }
        if(age==120){
            level.getGameRules().getRule(GameRules.RULE_MOBGRIEFING).set(true,level.getServer());
            if(level.getSeed()==20261121L){
                claim(true);
                check(!(boolean)call(type("EscortStone"),"allowed",level,escort,player,room),"Even owner-claimed stone must be protected");
            }else veto=true;
            var normal=People.BLOSSOM_WARDEN.get().create(level);
            check(!(boolean)call(type("EscortStone"),"allowed",level,normal,player,room),"Ordinary Guardian permitted to mine");
            if(level.getSeed()!=20261121L && !(boolean)call(type("EscortStone"),"allowed",level,escort,player,room))
                throw new AssertionError("Natural Sakura Stone not approved"+terrain());
        }
        if(age==240){
            if(level.getSeed()==20261121L)claim(false);
            check((level.getSeed()==20261121L || denied>0) && level.getBlockState(room).is(SakuraMaterials.STONE.get()) && level.getBlockState(room.above()).is(SakuraMaterials.STONE.get()),
                    "A protection veto was bypassed or never tested");veto=false;
        }
        sawPick|=(boolean)call(escort,"carryingEscortPick");
        if(level.getBlockState(room).isAir()&&level.getBlockState(room.above()).isAir()
                && level.getBlockState(room.east()).isAir() && level.getBlockState(room.east().above()).isAir()
                && escort.getX()>room.getX()+2){
            check(sawPick && level.getBlockState(room.north()).is(SakuraMaterials.STONE.get()),"Mined outside two-block opening");
            for(var cut:List.of(room,room.above(),room.east(),room.east().above()))
                check(strikeFlashes.getOrDefault(cut,0)>=3,"Missing player-visible strike flashes at "+cut+" "+strikeFlashes);
            level.setBlockAndUpdate(room,SakuraMaterials.STONE.get().defaultBlockState());
            check(!(boolean)call(type("EscortStone"),"allowed",level,escort,player,room),"Might break player-built stone");
            System.out.println("ESCORT native climb, wait in sight, two-column natural passage mined, leafy pick and per-strike flashes shown, placed replacement protected");
            var p=village.site().pos(village.x()+20,village.road()[52]+1,village.z()+village.bends()[52]);
            hold(home);actor(p);guard(p.west(2));go(6);
        }else if(age>=880)throw new AssertionError("No mining or walk-through; pick="+sawPick+terrain());
    }
    private void expiry()throws Exception {
        if(level.getGameTime()<until){check(current()==trip && escort.isAlive(),"Left before chosen time");return;}
        check(followed,"Follow never observed");go(7);
    }
    private void returned()throws Exception {
        if(current()==null){
            check(escort.isRemoved()&&escort.distanceToSqr(Vec3.atBottomCenterOf(home))<=16,"Did not reach original court");
            done=true;System.out.println("UWU PROFESSION AND ESCORT PASS "+level.getSeed());level.getServer().halt(false);
        }else check(ticks-stageAt<700,"Failed to return to actual court "+home+" last="+escort.position());
    }
}
