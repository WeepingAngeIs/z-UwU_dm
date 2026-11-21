package dev.uwu.dialoguecheck;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.KeeperSpeech;
import dev.uwu.dimension.People;
import dev.uwu.dimension.ReefLife;
import dev.uwu.dimension.UwUDimension;
import io.netty.channel.embedded.EmbeddedChannel;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundBundlePacket;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/** Runs native keeper interactions and records real outbound packets to positioned peers. */
final class ServerSpeechProbe {
    private final List<Peer> peers = new ArrayList<>();
    private final List<Map<String, Object>> receipts = new ArrayList<>();
    private ServerLevel reach;
    private Keeper petal, dusk;
    int petalId, duskId;
    UUID petalUuid, duskUuid;
    Component petalGreeting, duskGreeting, opening, reply, steps;
    Vec3 targetPosition;

    void setup(ServerPlayer owner) throws Exception {
        reach = owner.server.getLevel(ReefLife.REACH);
        require(reach != null, "Missing Sakura Reach");
        require(KeeperSpeech.RANGE == 80.0 && KeeperSpeech.DURATION_TICKS == 240, "Unexpected speech range/lifetime");
        reach.setDayTime(6000);
        reach.setWeatherParameters(6000, 0, false, false);
        reach.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, owner.server);
        reach.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, owner.server);
        for (int x = -5; x <= 18; x++) for (int z = -10; z <= 4; z++) {
            reach.setBlockAndUpdate(new BlockPos(x, 220, z), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        }
        petal = spawn(false, .5, .5);
        dusk = spawn(true, 12.5, .5);
        petalId = petal.getId(); duskId = dusk.getId();
        petalUuid = petal.getUUID(); duskUuid = dusk.getUUID();
        petalGreeting = greeting(petal); duskGreeting = greeting(dusk);
        opening = line("dusk_village_open"); reply = line("dusk_village_lamps"); steps = line("dusk_village_steps");
        peer(owner, reach, "Near", new Vec3(16, 0, 0), true);
        peer(owner, reach, "Inside", new Vec3(79.875, 0, 0), true);
        peer(owner, reach, "Edge", new Vec3(80, 0, 0), true);
        peer(owner, reach, "Far", new Vec3(80.125, 0, 0), false);
        peer(owner, reach, "VerticalFar", new Vec3(0, 80.125, 0), false);
        peer(owner, owner.server.overworld(), "OtherDimension", Vec3.ZERO, false);
        owner.setGameMode(GameType.CREATIVE);
        owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        owner.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
        position(owner, petal, 2.5);
    }

    private Keeper spawn(boolean dark, double x, double z) {
        Keeper keeper = (dark ? People.DUSK_KEEPER : People.PETAL_KEEPER).get().create(reach);
        require(keeper != null, "Missing keeper type");
        keeper.moveTo(x, 221, z, 180, 0);
        keeper.setYHeadRot(180);
        keeper.setYBodyRot(180);
        keeper.setNoAi(true);
        keeper.setNoGravity(true);
        keeper.setInvulnerable(true);
        keeper.setPersistenceRequired();
        keeper.bindVillage(keeper.blockPosition());
        require(reach.addFreshEntity(keeper), "Could not spawn keeper fixture");
        return keeper;
    }

    void positionDusk(ServerPlayer owner) { position(owner, dusk, 2.5); }

    void camera(ServerPlayer owner, boolean dark) { position(owner, dark ? dusk : petal, 6.5); }

    void positionForConversation(ServerPlayer owner) {
        require(!dusk.isTrading(), "Native close-container did not end trading");
        position(owner, dusk, 2.5);
        reset();
    }

    private void position(ServerPlayer owner, Keeper keeper, double distance) {
        targetPosition = keeper.position().add(0, 0, -distance);
        require(owner.teleportTo(reach, targetPosition.x, targetPosition.y, targetPosition.z, Set.of(), 0, 0),
                "Native owner teleport failed");
        owner.fallDistance = 0;
        movePeers(keeper);
        reset();
    }

    void interact(ServerPlayer owner, boolean dark) {
        Keeper keeper = dark ? dusk : petal;
        require(owner.serverLevel() == reach && owner.distanceToSqr(keeper) <= 9, "Interaction owner is not near the NPC");
        movePeers(keeper);
        reset();
        keeper.mobInteract(owner, InteractionHand.MAIN_HAND);
        require(keeper.getTradingPlayer() == owner, "Ordinary interaction no longer opens native trading");
        // Same native interaction repeated in the same server tick must not echo speech twice.
        keeper.mobInteract(owner, InteractionHand.MAIN_HAND);
        verify(keeper, dark ? duskGreeting : petalGreeting, dark ? "dusk-greeting" : "petal-greeting");
    }

    void conversation(ServerPlayer owner, String command, boolean response) {
        require(!dusk.isTrading(), "Conversation target is still trading");
        movePeers(dusk);
        reset();
        owner.server.getCommands().performPrefixedCommand(owner.createCommandSourceStack(), command);
        verify(dusk, response ? reply : opening, response ? "dusk-reply" : "dusk-opening");
    }

    private void verify(Keeper speaker, Component expected, String phase) {
        for (Peer peer : peers) {
            int count = peer.expected ? 1 : 0;
            require(peer.wire.chat.size() == count && peer.wire.cues.size() == count,
                    phase + " wrong recipient " + peer.name + ": chat=" + peer.wire.chat.size() + ", cue=" + peer.wire.cues.size());
            if (peer.expected) {
                var chat = peer.wire.chat.getFirst();
                var cue = peer.wire.cues.getFirst();
                require(!chat.overlay() && chat.content().equals(expected), "Speech was not exact regular chat for " + peer.name);
                require(cue.line().equals(chat.content()) && cue.entityId() == speaker.getId()
                        && cue.speaker().equals(speaker.getUUID()) && cue.dusk() == (speaker.getType() == People.DUSK_KEEPER.get())
                        && cue.dimension().equals(reach.dimension().location()), "Chat and bubble identity/content differ");
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("phase", phase); row.put("recipient", peer.name);
            row.put("dimension", peer.player.level().dimension().location().toString());
            row.put("distance", Math.sqrt(peer.player.distanceToSqr(speaker)));
            row.put("chat_packets", peer.wire.chat.size()); row.put("bubble_packets", peer.wire.cues.size());
            row.put("line", expected.getString()); receipts.add(row);
        }
    }

    void vanillaAndRefresh(ServerPlayer owner) {
        Peer outside = peers.stream().filter(p -> p.name.equals("OtherDimension")).findFirst().orElseThrow();
        ServerLevel level = outside.player.serverLevel();
        level.getChunkAt(outside.player.blockPosition());
        Villager vanilla = EntityType.VILLAGER.create(level);
        require(vanilla != null && !(vanilla instanceof Keeper), "Vanilla villager factory changed");
        Vec3 position = outside.player.position().add(0, 0, 2);
        vanilla.moveTo(position.x, position.y, position.z, 0, 0);
        vanilla.setNoAi(true); vanilla.setNoGravity(true); vanilla.setInvulnerable(true);
        require(level.addFreshEntity(vanilla), "Could not add vanilla control");
        reset();
        vanilla.interact(outside.player, InteractionHand.MAIN_HAND);
        for (Peer peer : peers) require(peer.wire.cues.isEmpty() && peer.wire.chat.isEmpty(), "Vanilla interaction emitted UwU speech");
        require(vanilla.getType() == EntityType.VILLAGER, "Vanilla control changed type");
        vanilla.discard();
        require(KeeperSpeech.say(dusk, steps), "Could not refresh speaker before dimension switch");
    }

    void leaveDimension(ServerPlayer owner) {
        var world = owner.server.overworld();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++)
            world.setBlockAndUpdate(new BlockPos(x, 220, z), Blocks.SMOOTH_QUARTZ.defaultBlockState());
        require(owner.teleportTo(world, .5, 221, .5, Set.of(), 0, 0), "Dimension switch failed");
    }

    List<Map<String, Object>> receipts() { return List.copyOf(receipts); }

    void cleanup(ServerPlayer owner) throws Exception {
        for (Peer peer : peers) {
            online(owner.server.getPlayerList()).remove(peer.player);
            byUuid(owner.server.getPlayerList()).remove(peer.player.getUUID());
            peer.player.serverLevel().removePlayerImmediately(peer.player, Entity.RemovalReason.DISCARDED);
            peer.wire.channel.finishAndReleaseAll();
        }
        peers.clear();
        if (petal != null) petal.discard();
        if (dusk != null) dusk.discard();
    }

    private void reset() { for (Peer peer : peers) { peer.wire.chat.clear(); peer.wire.cues.clear(); } }

    private void movePeers(Keeper speaker) {
        // Native player collisions between the very close boundary controls must
        // not move them off their documented test coordinates before delivery.
        for (Peer peer : peers) peer.player.setPos(speaker.position().add(peer.offset));
    }

    private void peer(ServerPlayer owner, ServerLevel level, String name, Vec3 offset, boolean expected) throws Exception {
        var player = new ServerPlayer(owner.server, level, new GameProfile(UUID.randomUUID(), "Sp" + name), ClientInformation.createDefault());
        var wire = new RecordingListener(player);
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.setInvulnerable(true);
        player.setPos(petal.position().add(offset));
        online(owner.server.getPlayerList()).add(player);
        byUuid(owner.server.getPlayerList()).put(player.getUUID(), player);
        level.addNewPlayer(player);
        require(level.players().contains(player), "Recorder is not in the real level player list");
        peers.add(new Peer(name, player, wire, offset, expected));
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> online(PlayerList list) throws Exception {
        var field = PlayerList.class.getDeclaredField("players"); field.setAccessible(true);
        return (List<ServerPlayer>) field.get(list);
    }

    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> byUuid(PlayerList list) throws Exception {
        var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        return (Map<UUID, ServerPlayer>) field.get(list);
    }

    private static Component greeting(Keeper keeper) throws Exception {
        var craft = Class.forName("dev.uwu.dimension.KeeperCraft");
        var of = craft.getDeclaredMethod("of", Keeper.class); of.setAccessible(true);
        var greeting = craft.getDeclaredField("greeting"); greeting.setAccessible(true);
        return keeper.getDisplayName().copy().append(": ").append((String) greeting.get(of.invoke(null, keeper)));
    }

    private static Component line(String id) throws Exception {
        try (var in = UwUDimension.class.getResourceAsStream("/data/uwudimension/dialogue/sakura_ambient.json")) {
            require(in != null, "Missing packaged dialogue resource");
            var root = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var entry : root.getAsJsonArray("dialogues")) {
                var line = entry.getAsJsonObject();
                if (line.get("id").getAsString().equals(id))
                    return Component.literal(line.get("speaker").getAsString() + ": " + line.get("text").getAsString());
            }
        }
        throw new AssertionError("Missing existing line " + id);
    }

    private record Peer(String name, ServerPlayer player, RecordingListener wire, Vec3 offset, boolean expected) {}

    private static final class RecordingListener extends ServerGamePacketListenerImpl {
        final List<ClientboundSystemChatPacket> chat = new ArrayList<>();
        final List<KeeperSpeech.Cue> cues = new ArrayList<>();
        final EmbeddedChannel channel;
        RecordingListener(ServerPlayer player) { this(player, new Connection(PacketFlow.SERVERBOUND)); }
        private RecordingListener(ServerPlayer player, Connection connection) {
            super(player.server, connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
            channel = new EmbeddedChannel(connection);
        }
        @Override public void send(Packet<?> packet, PacketSendListener listener) {
            if (packet instanceof ClientboundBundlePacket bundle) bundle.subPackets().forEach(part -> send(part, null));
            else if (packet instanceof ClientboundSystemChatPacket message) chat.add(message);
            else if (packet instanceof ClientboundCustomPayloadPacket payload && payload.payload() instanceof KeeperSpeech.Cue cue) cues.add(cue);
        }
    }

    private static void require(boolean condition, String problem) { if (!condition) throw new AssertionError(problem); }
}
