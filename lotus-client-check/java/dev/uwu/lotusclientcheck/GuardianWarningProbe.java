package dev.uwu.lotusclientcheck;

import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.GuardianFlight;
import dev.uwu.dimension.People;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.List;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoUpdatePacket;
import net.minecraft.network.protocol.game.ClientboundPlayerInfoRemovePacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Native Survival attack packets and movement from the hosted client, observed on both sides. */
final class GuardianWarningProbe {
    private enum Phase { SETUP, READY, WARNING, PATROL, FALL_CONTROL, QUIET, REPEAT }
    private record Swing(int number, int guardian, boolean duplicate) {}
    private record View(int swing, boolean grip, double peak, float left, float right, float pitch) {}
    private static volatile Swing request;
    private static volatile View view;
    private static volatile boolean joined, done;
    private static Swing played;

    private static Phase phase = Phase.SETUP;
    private static BlossomWarden guard;
    private static Witness other;
    private static BlockPos start;
    private static int round, gripTicks, secondGripTicks, grounded, serial;
    private static long since;
    private static double peak, patrolStartX;
    private static float health;
    private static boolean clientWasWarned;

    static void damage(LivingIncomingDamageEvent event) {
        if (joined && !done && event.getEntity() instanceof ServerPlayer player) {
            System.out.println("GUARDIAN DAMAGE " + event.getSource().getMsgId() + " amount=" + event.getAmount()
                    + " phase=" + phase + " feet=" + player.getY() + " fallDistance=" + player.fallDistance);
        }
    }

    static void clientTick(Minecraft client) {
        if (done) { client.stop(); return; }
        if (client.player == null || client.level == null || client.gameMode == null || client.screen != null) return;
        joined = true;
        Swing swing = request;
        if (swing == null || !(client.level.getEntity(swing.guardian) instanceof BlossomWarden mob)) return;
        if (played != swing) {
            if (!client.player.onGround() || client.player.distanceToSqr(mob) > 9) return;
            played = swing;
            view = new View(swing.number, false, client.player.getY(), 0, 0, 0);
            client.gameMode.attack(client.player, mob);
            if (swing.duplicate) client.gameMode.attack(client.player, mob);
            return;
        }
        View prev = view;
        boolean gripped = prev.grip || mob.gripping();
        float yaw = gripped && !mob.gripping() && client.player.getY() > start.getY() + 2.5
                ? Mth.wrapDegrees(mob.yHeadRot - mob.yBodyRot) : 0;
        float pitch = yaw == 0 ? 0 : mob.getXRot();
        view = new View(swing.number, gripped, Math.max(prev.peak, client.player.getY()),
                Math.min(prev.left, yaw), Math.max(prev.right, yaw), Math.min(prev.pitch, pitch));
    }

    static void serverTick(ServerTickEvent.Post event) {
        if (!joined || done || event.getServer().getPlayerList().getPlayers().isEmpty()) return;
        ServerPlayer player = event.getServer().getPlayerList().getPlayers().getFirst();
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        try {
            if (phase != Phase.SETUP && phase != Phase.QUIET && now - since > 200)
                throw new AssertionError("Timed out: " + details(player));
            switch (phase) {
                case SETUP -> {
                    start = new BlockPos(player.getBlockX(), 260, player.getBlockZ());
                    for (BlockPos pos : BlockPos.betweenClosed(start.offset(-17,-1,-8), start.offset(17,-1,8)))
                        level.setBlockAndUpdate(pos, Blocks.STONE.defaultBlockState());
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, level.getServer());
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, level.getServer());
                    level.setDayTime(15_000);
                    player.getInventory().clearContent();
                    player.setHealth(player.getMaxHealth());
                    level.getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION).set(false, level.getServer());
                    spawn(level, false);
                    position(player);
                    next(Phase.READY, now);
                }
                case READY -> {
                    if (now - since < 20 || !player.onGround()) return;
                    peak = player.getY();
                    health = player.getHealth();
                    gripTicks = secondGripTicks = grounded = 0;
                    clientWasWarned = false;
                    request = new Swing(++serial, guard.getId(), round == 0);
                    next(round == 3 ? Phase.REPEAT : Phase.WARNING, now);
                }
                case WARNING -> {
                    require(!player.getPersistentData().contains(GuardianFlight.STATE), "First hit entered long flight");
                    peak = Math.max(peak, player.getY());
                    if (player.getUUID().equals(guard.offender()) && guard.gripping()) {
                        if (++gripTicks == 1) guard.bindVillage(start.east(15));
                        if (round == 2 && gripTicks == 13) {
                            other = new Witness(level, guard);
                            other.player.connection.handleInteract(ServerboundInteractPacket.createAttackPacket(guard, false));
                            require(player.getUUID().equals(guard.offender()), "Second player displaced the first");
                        }
                    }
                    if (other != null) {
                        require(!other.player.getPersistentData().contains(GuardianFlight.STATE), "Second player inherited a repeat hit");
                        if (other.player.getUUID().equals(guard.offender()) && guard.gripping()) secondGripTicks++;
                    }
                    if (gripTicks < 1 || guard.offender() != null) return;
                    grounded = player.onGround() && Math.abs(player.getY()-start.getY()) < .05 ? grounded + 1 : 0;
                    if (grounded < 8) return;
                    View v = view;
                    if (v == null || v.swing != serial || !v.grip) return;
                    double rise = peak - start.getY(), clientRise = v.peak - start.getY();
                    require(gripTicks >= 8 && rise >= 8 && rise <= 12 && Math.abs(rise-clientRise) < 1,
                            "Grip or toss height: " + details(player));
                    require(v.left < -7 && v.right > 7 && v.pitch < -15, "Missing visible head shake or upward gaze: " + v);
                    require(guard.getTarget() == null && player.getHealth() == health && player.isAlive(),
                            "Combat did not clear or landing hurt the player: " + details(player));
                    if (round == 2) require(other != null && secondGripTicks >= 8 && other.shortImpulse,
                            "The other player never received their own short toss: " + details(player));
                    String label = round == 0 ? "MALE WARNING" : round == 1 ? "QUIET RESET" : "FEMALE WARNING";
                    System.out.printf(java.util.Locale.ROOT,
                            "GUARDIAN %s PASS serverRise=%.2f clientRise=%.2f headYaw=%.1f..%.1f lookPitch=%.1f health=%.1f%n",
                            label, rise, clientRise, v.left, v.right, v.pitch, player.getHealth());
                    clientWasWarned = true;
                    request = null;
                    if (round == 2) other.close(level);
                    patrolStartX = guard.getX();
                    next(Phase.PATROL, now);
                }
                case PATROL -> {
                    require(clientWasWarned && guard.offender() == null && guard.getTarget() == null, "Player was reacquired");
                    if (guard.getX() < patrolStartX + 1.0) return;
                    System.out.printf(java.util.Locale.ROOT, "GUARDIAN PATROL RESUMED round=%d walked=%.2f%n", round, guard.getX()-patrolStartX);
                    if (round == 0) {
                        player.connection.teleport(player.getX(), start.getY()+7.5, player.getZ(), 0, 0);
                        next(Phase.FALL_CONTROL, now);
                        return;
                    }
                    round++;
                    if (round == 2) { guard.discard(); spawn(level, true); }
                    position(player);
                    next(Phase.READY, now);
                }
                case FALL_CONTROL -> {
                    if (now - since < 20 || !player.onGround() || player.getY() > start.getY()+.05) return;
                    require(player.getHealth() < health && player.isAlive(), "An unrelated ordinary fall was protected");
                    player.setHealth(health);
                    System.out.println("GUARDIAN ORDINARY FALL CONTROL PASS");
                    next(Phase.QUIET, now);
                }
                case QUIET -> {
                    if (now - since < 605) return;
                    round++;
                    // Keep this exact Guardian and player, with the existing warning memory.
                    position(player);
                    next(Phase.READY, now);
                }
                case REPEAT -> {
                    if (!player.getPersistentData().contains(GuardianFlight.STATE)) return;
                    require(guard.female() && other.shortImpulse && player.getPersistentData().getCompound(GuardianFlight.STATE)
                            .getDouble("Y") > start.getY() + 7_000, "Repeat did not take the original Guardian path");
                    System.out.println("GUARDIAN INDEPENDENT PLAYERS AND REPEAT PASS");
                    done = true;
                }
            }
        } catch (Throwable failure) {
            System.err.println("GUARDIAN WARNING CHECK FAILED " + details(player));
            failure.printStackTrace();
            done = true;
        }
    }

    private static void spawn(ServerLevel level, boolean female) {
        guard = People.BLOSSOM_WARDEN.get().create(level);
        require(guard != null, "Missing Guardian");
        CompoundTag saved = guard.saveWithoutId(new CompoundTag());
        saved.putBoolean("FemaleGuardian", female);
        saved.putBoolean("KeeperSummonedGuardian", true);
        guard.load(saved);
        guard.moveTo(start.getX()+.5, start.getY(), start.getZ()+.5, 0, 0);
        guard.bindVillage(start);
        require(guard.female() == female && level.addFreshEntity(guard), "Could not place the requested Guardian variant");
    }

    private static void position(ServerPlayer player) {
        guard.bindVillage(start);
        guard.teleportTo(start.getX()+.5, start.getY(), start.getZ()+.5);
        player.connection.teleport(start.getX()+.5, start.getY(), start.getZ()+2.4, 180, 0);
        player.fallDistance = 0;
    }

    private static void next(Phase to, long now) { phase = to; since = now; }
    private static String details(ServerPlayer p) {
        return phase + " round=" + round + " grip=" + gripTicks + "/" + secondGripTicks + " peak=" + peak
                + " feet=" + p.getY() + " ground=" + p.onGround() + " health=" + p.getHealth() + "/" + health
                + " otherImpulse=" + (other != null && other.shortImpulse) + " view=" + view + " guardian="
                + (guard == null ? "missing" : guard.position() + " target=" + guard.offender());
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }

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
