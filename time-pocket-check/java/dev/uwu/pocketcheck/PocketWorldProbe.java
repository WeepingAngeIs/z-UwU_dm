package dev.uwu.pocketcheck;

import dev.uwu.dimension.AcquisitionPolicy;
import dev.uwu.dimension.TimePocket;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** Runs only in the fresh client fixture's integrated server. All captures come from the separate client. */
final class PocketWorldProbe {
    static final double ORIGIN_X = -.5, ORIGIN_Z = 8.5;
    static final int FLOOR = 209;
    private static final AABB ARENA = new AABB(-55, 190, -40, 65, 270, 70);
    private static final int[][] CROSS = {{-1,0},{0,0},{-2,0},{-1,-1},{-1,1}};
    private static final int[][] OUTSIDE = {{0,1},{-2,-1},{0,-1},{-2,1}};
    private static final List<Pig> stopped = new ArrayList<>(), moving = new ArrayList<>();
    private static final List<LevelChunk> retained = new ArrayList<>();
    private static final List<Integer> frozenAges = new ArrayList<>();
    private static final int[] LANES = {32, 36, 40};
    private static ServerLevel level;
    private static UUID caster;
    private static FallingBlockEntity sand, outsideSand, baselineSand;
    private static Pig baselinePig;
    private static Vec3 sandOrigin, outsideOrigin, firstPlayer, previousStar;
    private static TimePocket.Star tracked;
    private static long deadline, fieldStart, lastStarTick;
    private static int outsideAge, slowSteps, normalSteps, loadedSamples, claimEvents, impacts;
    private static boolean fieldEnded;
    private static UUID activeId;
    private static int casts, walkSamples, wallSamples, walkPigAge;
    private static long lastUse = -1, longThawTick;
    private static double walked;
    static volatile boolean ready, frozenChecked, resumeChecked, shotFinished, casterMoved, walkEnded, shortEnded, earlyEnded;
    static volatile int targetDone = -1;
    static volatile Throwable failure;
    private static int target = -1;
    private static boolean switchedTool;

    static void setup(MinecraftServer server, UUID id) {
        try {
            check(server.getWorldData().getLevelName().equals("Disposable Time Pocket"), "Unexpected world for fixture");
            level = server.overworld();
            caster = id;
            ServerPlayer player = owner();
            check(player != null && player.serverLevel() == level, "Missing fixture player");
            level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_WEATHER_CYCLE).set(false, server);
            level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, server);
            level.setDayTime(6000);
            level.setWeatherParameters(60000,0,false,false);
            for (var p : BlockPos.betweenClosed(new BlockPos(-12,FLOOR,4),new BlockPos(10,FLOOR,24)))
                level.setBlockAndUpdate(p, Blocks.SMOOTH_STONE.defaultBlockState());
            for (var p : BlockPos.betweenClosed(new BlockPos(-12,FLOOR+1,20),new BlockPos(10,FLOOR+12,20)))
                level.setBlockAndUpdate(p, Blocks.LIME_CONCRETE.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(-1,FLOOR+2,20), Blocks.AIR.defaultBlockState());
            level.setBlockAndUpdate(new BlockPos(-1,FLOOR+2,36), Blocks.CYAN_CONCRETE.defaultBlockState());
            player.setGameMode(GameType.SURVIVAL);
            home();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(TimePocket.CLOCK.get()));
            player.getInventory().setItem(1, new ItemStack(TimePocket.NO_COOLDOWN.get()));
            player.containerMenu.broadcastChanges();
            for (int i=0;i<LANES.length;i++) {
                level.setBlockAndUpdate(new BlockPos(LANES[i],FLOOR,8),Blocks.SMOOTH_STONE.defaultBlockState());
                level.setBlockAndUpdate(targetPos(i), i==2 ? Blocks.CHEST.defaultBlockState() : Blocks.YELLOW_CONCRETE.defaultBlockState());
            }
            ((ChestBlockEntity)level.getBlockEntity(targetPos(2))).setItem(0,new ItemStack(Items.EMERALD,3));
            check(TimePocket.field(level)==null,"Unexpected field before input");
            baselinePig=pig(CROSS[0]);
            baselineSand=fall(-6.5,244,6.5);
            ready=true;
        } catch(Throwable ex) { failure=ex; }
    }

    static void home() { owner().connection.teleport(ORIGIN_X,FLOOR+1,ORIGIN_Z,0,0); }
    private static ServerPlayer owner() { return level.getServer().getPlayerList().getPlayer(caster); }
    private static BlockPos targetPos(int index) { return new BlockPos(LANES[index],FLOOR+2,24); }

    static void used(PlayerInteractEvent.RightClickItem event) {
        if (ready && event.getLevel() == level && event.getEntity() == owner()
                && event.getHand() == InteractionHand.MAIN_HAND && event.getItemStack().getItem() instanceof TimePocket.Clock
                && !owner().getCooldowns().isOnCooldown(event.getItemStack().getItem())) {
            var field = TimePocket.field(level);
            if (field == null || level.getGameTime() >= field.until()) lastUse = level.getGameTime();
        }
    }

    static void tick(LevelTickEvent.Post event) {
        if (!ready || event.getLevel()!=level || failure!=null || owner()==null) return;
        try {
            var f=TimePocket.field(level);
            if (activeId != null && (f == null || !activeId.equals(f.id()))) {
                endCast();
                activeId = null;
            }
            if (f != null) {
                if (activeId == null) {
                    beginCast(f);
                    activeId = f.id();
                }
                check(f.until() == deadline, "An active freeze changed its deadline");
                if (casts == 1) examine(f);
                if (casts == 2) examineWalk(f);
            }
            if (casts == 1 && target < 0) observeShot(f);
            if (fieldEnded && sand!=null && !resumeChecked && sand.getY()<sandOrigin.y-.1) {
                check(stopped.get(0).tickCount>frozenAges.get(0),"Mob failed to resume");
                for(int i=0;i<CROSS.length;i++)
                    check(level.getChunkSource().getChunkNow(CROSS[i][0],CROSS[i][1])==retained.get(i),"Chunk replaced");
                resumeChecked=true;
                System.out.println("POCKET RESUME PASS loadedSamples="+loadedSamples+" slowSteps="+slowSteps);
            }
            if (target>=0 && targetDone<target && !switchedTool && !level.getEntitiesOfClass(TimePocket.Star.class,ARENA).isEmpty()) {
                // The authorized practice break goes through the player's normal pickaxe and all break listeners.
                owner().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(Items.IRON_PICKAXE));
                switchedTool=true;
            }
        } catch(Throwable ex) { failure=ex; }
    }

    private static void beginCast(TimePocket.Field field) {
        casts++;
        int seconds = switch (casts) {
            case 1 -> 4;
            case 2 -> 20;
            case 3 -> 2;
            case 4 -> 1;
            default -> throw new AssertionError("Unexpected activation " + casts);
        };
        deadline = field.until();
        check(lastUse >= 0 && deadline - lastUse == seconds * 20L, "Wrong duration from the actual item use");
        if (casts == 1) check(owner().getCooldowns().isOnCooldown(TimePocket.CLOCK.get()), "Standard variant missed its cooldown");
        else check(owner().getMainHandItem().is(TimePocket.NO_COOLDOWN.get())
                && !owner().getCooldowns().isOnCooldown(TimePocket.NO_COOLDOWN.get()), "No-cooldown variant was blocked");
        if (casts == 3) check(lastUse >= longThawTick - 1 && lastUse - longThawTick < 10, "Immediate no-cooldown recast was delayed");
        System.out.println("UWUZI ITEM USE PASS cast=" + casts + " seconds=" + seconds + " ticks=" + (deadline - lastUse));
        lastUse = -1;
        if (casts == 1) start(field);
        if (casts == 2) {
            firstPlayer = owner().position();
            walkPigAge = stopped.get(0).tickCount;
        }
    }

    private static void endCast() {
        if (casts == 4) {
            check(level.getGameTime() < deadline, "Spectator cancellation missed the short freeze");
            earlyEnded = true;
            return;
        }
        check(level.getGameTime() >= deadline && level.getGameTime() <= deadline + 1, "Wrong expiry tick");
        if (casts == 1) {
            check(frozenChecked && casterMoved && slowSteps >= 4, "Missing first-cast evidence");
            check(owner().getCooldowns().isOnCooldown(TimePocket.CLOCK.get())
                    && !owner().getCooldowns().isOnCooldown(TimePocket.NO_COOLDOWN.get()), "Variant cooldowns leaked across items");
            float remaining = owner().getCooldowns().getCooldownPercent(TimePocket.CLOCK.get(), 0F);
            check(Math.abs(remaining - 2F / 3F) < .015F, "Standard 12-second cooldown was shortened or restarted");
            fieldEnded = true;
        } else if (casts == 2) {
            check(walkSamples >= 399 && wallSamples >= 12 && walked > 10, "Caster failed the walk to the solid wall");
            check(AcquisitionPolicy.uwuziFreezeSeconds() == 2, "Mid-freeze setting edit did not take effect for the next cast");
            walkEnded = true;
            longThawTick = level.getGameTime();
            System.out.println("UWUZI WALK PASS frozenSamples=" + walkSamples + " distance=" + walked + " wallTicks=" + wallSamples);
        } else {
            shortEnded = true;
        }
    }

    private static void examineWalk(TimePocket.Field field) {
        var player = owner();
        double z = player.getZ();
        check(player.gameMode.getGameModeForPlayer() == GameType.SURVIVAL && field.at(player.getX(), z), "Walk left the freeze");
        check(Math.abs(player.getY() - (FLOOR + 1)) < .02
                        && level.getBlockState(player.blockPosition().below()).is(Blocks.SMOOTH_STONE), "Caster fell through the floor");
        check(level.getBlockState(new BlockPos(-1,FLOOR+1,20)).is(Blocks.LIME_CONCRETE) && z < 19.72, "Caster passed through the wall");
        check(stopped.get(0).tickCount == walkPigAge, "Frozen mob advanced during the walk");
        for (int i = 0; i < CROSS.length; i++) {
            check(level.getChunkSource().getChunkNow(CROSS[i][0], CROSS[i][1]) == retained.get(i), "A frozen chunk was unloaded");
        }
        walkSamples++;
        walked = Math.max(walked, z - firstPlayer.z);
        if (z > 19.65 && player.onGround()) wallSamples++;
    }

    private static void start(TimePocket.Field f) {
        check(baselinePig.tickCount>=6 && baselineSand.getY()<243,"Ordinary simulation did not advance before casting");
        check(f.x()==-1 && f.z()==0 && f.caster().equals(caster),"Wrong frozen chunk or caster");
        int cardinality=0;
        for(int x=-4;x<=2;x++) for(int z=-3;z<=3;z++) if(f.contains(x,z)) cardinality++;
        check(cardinality==5 && f.at(-.001,31.999) && !f.at(0,16) && !f.at(-16.001,-.001),"Wrong five-chunk boundary");
        fieldStart=level.getGameTime();firstPlayer=owner().position();
        for(int[] c:CROSS) {
            LevelChunk chunk=level.getChunkSource().getChunkNow(c[0],c[1]);
            check(chunk!=null,"Fixture chunk wasn't already loaded");
            retained.add(chunk);
            var p=pig(c);stopped.add(p);frozenAges.add(p.tickCount);
        }
        for(int[] c:OUTSIDE) moving.add(pig(c));
        pigAt(-3.5,FLOOR+1,15);
        pigAt(3.5,FLOOR+1,18);
        outsideAge=moving.get(0).tickCount;
        sand=fall(-6.5,214.25,15.5);outsideSand=fall(3.5,244,22.5);
        sandOrigin=sand.position();outsideOrigin=outsideSand.position();
    }

    private static Pig pig(int[] c) {
        return pigAt(c[0]*16+8.5,FLOOR+2,c[1]*16+8.5);
    }

    private static Pig pigAt(double x,double y,double z) {
        Pig p=EntityType.PIG.create(level);
        check(p!=null,"Failed to create pig");
        p.setPos(x,y,z);
        p.setNoGravity(true);p.setNoAi(true);p.setSilent(true);p.setPersistenceRequired();
        check(level.addFreshEntity(p),"Failed to add pig");
        return p;
    }

    private static FallingBlockEntity fall(double x,double y,double z) {
        var e=EntityType.FALLING_BLOCK.create(level);
        check(e!=null,"Failed to create falling sand");
        e.setPos(x,y,z);e.setDeltaMovement(0,-.18,0);
        check(level.addFreshEntity(e),"Failed to add sand");return e;
    }

    private static void examine(TimePocket.Field f) {
        check(f.until()==deadline,"Toggle or repeat use restarted the duration");
        for(int i=0;i<CROSS.length;i++) {
            check(stopped.get(i).tickCount==frozenAges.get(i),"Inside mob advanced in "+stopped.get(i).chunkPosition());
            check(level.getChunkSource().getChunkNow(CROSS[i][0],CROSS[i][1])==retained.get(i),"Chunk unloaded");
        }
        loadedSamples++;
        check(sand.position().distanceToSqr(sandOrigin)<1e-12 && sand.time==0,"Falling block moved or aged inside");
        long elapsed=level.getGameTime()-fieldStart;
        if(elapsed>=10 && !frozenChecked) {
            check(moving.stream().allMatch(p->p.tickCount>=outsideAge+7),"An outside mob stopped");
            check(outsideSand.getY()<outsideOrigin.y-1,"Outside sand stopped");
            frozenChecked=true;
        }
        if(owner().position().subtract(firstPlayer).horizontalDistance()>.16) casterMoved=true;
    }

    private static void observeShot(TimePocket.Field f) {
        if (shotFinished) return;
        if (tracked==null) tracked=level.getEntitiesOfClass(TimePocket.Star.class,ARENA).stream().findFirst().orElse(null);
        if (tracked==null) return;
        if (!tracked.isAlive()) {
            check(normalSteps>=1 && slowSteps>=4,"Star never resumed normal speed "+normalSteps+"/"+slowSteps);
            check(level.getBlockState(new BlockPos(-1,FLOOR+2,36)).is(Blocks.CYAN_CONCRETE),"Ordinary impact damaged terrain");
            shotFinished=true;return;
        }
        Vec3 now=tracked.position();
        if(previousStar!=null && lastStarTick+1==level.getGameTime()) {
            double travel=now.distanceTo(previousStar);
            if(f!=null && travel>.10 && travel<.15) slowSteps++;
            if(f==null && travel>1.10 && travel<1.35) normalSteps++;
        }
        previousStar=now;lastStarTick=level.getGameTime();
    }

    static void setting(boolean on) {
        try {
            int result=level.getServer().getCommands().getDispatcher().execute("uwuclock negativeColors "+on,
                    level.getServer().createCommandSourceStack());
            check(result==1,"Operator color toggle failed");
        } catch(Throwable ex) { failure=ex; }
    }

    static void duration(int seconds) {
        var server = level.getServer();
        try {
            int result = server.getCommands().getDispatcher().execute("uwu uwuzi freezeseconds " + seconds, server.createCommandSourceStack());
            check(result == 1 && AcquisitionPolicy.uwuziFreezeSeconds() == seconds, "Duration command failed");
        } catch (Throwable ex) { failure = ex; }
    }

    static void prepareTarget(int index) {
        try {
            target=index;switchedTool=false;
            owner().connection.teleport(LANES[index]+.5,FLOOR+1,ORIGIN_Z,0,0);
            owner().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(index == 2 ? TimePocket.NO_COOLDOWN.get() : TimePocket.CLOCK.get()));
        } catch(Throwable ex) { failure=ex; }
    }

    static void practiceImpact(ProjectileImpactEvent event) {
        if(target<0 || targetDone>=target || !(event.getProjectile() instanceof TimePocket.Star star) ||
                star.level()!=level || star.getOwner()!=owner() ||
                !(event.getRayTraceResult() instanceof BlockHitResult hit) || !hit.getBlockPos().equals(targetPos(target)))return;
        try {
            BlockPos pos=hit.getBlockPos();
            if (target<2) {
                ServerPlayer player=owner();
                check(switchedTool && player.getMainHandItem().is(Items.IRON_PICKAXE),"Practice tool missing");
                boolean success=level.mayInteract(player,pos) && player.gameMode.destroyBlock(pos);
                check(success==(target==0) && level.getBlockState(pos).isAir()==(target==0),"Practice protection failed");
                if (target==1) check(claimEvents==1,"Claim veto was bypassed");
            } else {
                var chest=(ChestBlockEntity) level.getBlockEntity(pos);
                check(chest!=null && chest.getItem(0).is(Items.EMERALD) && chest.getItem(0).getCount()==3,"Player chest changed");
            }
            impacts++;targetDone=target;
            System.out.println("POCKET PRACTICE IMPACT "+target+" at "+pos);
        } catch(Throwable ex) { failure=ex; }
    }

    static void claim(BlockEvent.BreakEvent event) {
        if (level!=null && event.getLevel()==level && target==1 && event.getPos().equals(targetPos(1))) {
            claimEvents++;
            event.setCanceled(true);
        }
    }

    static void secondSetup() {
        try {
            check(impacts==3 && claimEvents==1 && level.getBlockState(targetPos(0)).isAir()
                    && level.getBlockState(targetPos(1)).is(Blocks.YELLOW_CONCRETE)
                    && level.getBlockEntity(targetPos(2)) instanceof ChestBlockEntity chest
                    && chest.getItem(0).is(Items.EMERALD) && chest.getItem(0).getCount()==3,
                    "Disposable target or protected terrain was not preserved correctly");
            home();
            owner().setItemInHand(InteractionHand.MAIN_HAND,new ItemStack(TimePocket.NO_COOLDOWN.get()));
        } catch(Throwable ex){failure=ex;}
    }
    static void cancelEarly() { owner().setGameMode(GameType.SPECTATOR); }
    static void moveOutside(int centerX, int centerZ) {
        BlockPos floor = new BlockPos((centerX+1)*16, FLOOR, (centerZ+1)*16);
        level.setBlockAndUpdate(floor, Blocks.SMOOTH_STONE.defaultBlockState());
        owner().connection.teleport(floor.getX()+.5, FLOOR+1, floor.getZ()+.5, 0, -75);
    }
    static void survival() { owner().setGameMode(GameType.SURVIVAL); }

    static void check(boolean ok,String reason) { if(!ok)throw new AssertionError(reason); }
    private PocketWorldProbe() {}
}

