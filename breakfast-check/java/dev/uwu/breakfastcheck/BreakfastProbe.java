package dev.uwu.breakfastcheck;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.authlib.GameProfile;
import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.ReefLife;
import dev.uwu.weaponcheck.CandidateJarProbe;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.PlayerList;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.CraftingMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Hosted dedicated-server fixtures, not GUI clients. The two independent ServerPlayers have
 * memory network transports and normal inventory/advancement listeners. Acceptance uses native
 * scheduled toaster ticks, ItemEntity.playerTouch, ServerPlayerGameMode.useItemOn/useItem,
 * CraftingMenu result extraction and ordinary item-use ticks. No award/criterion trigger calls.
 */
@Mod(value = "uwu_breakfast_check", dist = Dist.DEDICATED_SERVER)
public final class BreakfastProbe {
    private static final ResourceLocation TOASTER = id("baker_toaster"), BREAD = id("uwu_bread"), DING = id("toaster_ding");
    private static final List<String> MILESTONES = List.of("toaster", "loaf", "galette", "chowder", "wrap");
    private static final String TOKEN = "UwUGeneratedToast";
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Gson LINE = new Gson();
    private static final Path OUT = Path.of(System.getProperty("uwu.breakfast.out", "breakfast-evidence"));
    private enum Phase { PLAN, LOAD, ORDINARY, REWARD, PICKUP_A, INSPECT_B, TRANSFER_B, REPEAT,
        STALE_RELOAD, FOOD_PICKUP, CRAFT, EAT_CHOWDER, EAT_WRAP, RELOADED, FINAL_CHECK, DONE }

    private final Set<ChunkPos> generated = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Map<String, Integer>> earns = new LinkedHashMap<>();
    private final List<Map<String, Object>> passes = new ArrayList<>();
    private final List<Map<String, Object>> kitchens = new ArrayList<>();
    private ServerLevel level;
    private List<BlossomVillage.Plan> plans = List.of();
    private Phase phase = Phase.PLAN;
    private int tick, began, kitchenIndex, attempts, rejectedAt, acceptedPops, nativeInspections;
    private boolean done;
    private Throwable asynchronousFailure;
    private BlockPos position, smoker, farA, farB, standA, standB;
    private ChunkPos kitchenChunk, smokerChunk;
    private CompoundTag readySnapshot;
    private UUID token, originalEntityId;
    private ItemEntity published;
    private ItemStack originalLoaf;
    private RecordingListener a, b;
    private Map<String, Boolean> savedA, savedB;

    public BreakfastProbe() {
        CandidateJarProbe.verify();
        NeoForge.EVENT_BUS.addListener(this::tick);
        NeoForge.EVENT_BUS.addListener(this::chunkLoaded);
        NeoForge.EVENT_BUS.addListener(this::joined);
        NeoForge.EVENT_BUS.addListener(this::earned);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, true, this::inspected);
    }

    private void tick(ServerTickEvent.Post event) {
        if (done) return;
        try {
            ++tick;
            require(tick < 7000, "Overall breakfast tick timeout");
            require(elapsed() < (phase == Phase.LOAD ? 1200 : 500), "Phase timeout " + phase);
            if (asynchronousFailure != null) throw new AssertionError("Observer callback failed", asynchronousFailure);
            // There is no remote client connection ticking these actors. Drive exactly the normal
            // player game tick while a native held food use is active, without finishing it directly.
            if (a != null && a.player.isUsingItem()) a.player.doTick();
            if (b != null && b.player.isUsingItem()) b.player.doTick();
            sync(a); sync(b);
            switch (phase) {
                case PLAN -> {
                    level = event.getServer().getLevel(ReefLife.REACH);
                    require(level != null, "Sakura Reach did not load");
                    level.getGameRules().getRule(GameRules.RULE_DOMOBSPAWNING).set(false, event.getServer());
                    level.getGameRules().getRule(GameRules.RULE_DAYLIGHT).set(false, event.getServer());
                    for (String name : MILESTONES) require(level.getServer().getAdvancements().get(adv(name)) != null,
                            "Candidate did not load advancement " + adv(name));
                    evidence("fixture", Map.of("actors", "Two independent Survival ServerPlayers with memory transports",
                            "clientRendering", false, "directAdvancementGrants", false,
                            "sourceBase", "c1c0802be13d0cd5a01149ffa357018f7a863c5c",
                            "breakfastSource", "5960f6f62ae929b718e56291698ccc301fd175b6"));
                    plans = List.of(BlossomVillage.first(level, false), BlossomVillage.first(level, true));
                    loadKitchen();
                }
                case LOAD -> {
                    if (!level.isPositionEntityTicking(position) || !level.isPositionEntityTicking(smoker)) return;
                    verifyKitchen();
                    List<BlockPos> stands = standingSpots(level.getBlockState(position).getValue(HorizontalDirectionalBlock.FACING));
                    standA = stands.get(0); standB = stands.get(1);
                    farA = position.offset(0, 5, 12); farB = position.offset(3, 5, 12);
                    force(farA); force(farB);
                    a = spawn("BreakfastA" + kitchenIndex, farA);
                    b = spawn("BreakfastB" + kitchenIndex, farB);
                    require(!a.player.getUUID().equals(b.player.getUUID()), "Players are not independent");
                    require(a.player.distanceToSqr(Vec3.atCenterOf(position)) > 16
                            && b.player.distanceToSqr(Vec3.atCenterOf(position)) > 16, "Fresh fixtures started in pop range");
                    pickup(a.player, new ItemStack(Items.BREAD));
                    next(Phase.ORDINARY);
                }
                case ORDINARY -> {
                    if (elapsed() < 5) return;
                    breakfastState(a.player, false, false); breakfastState(b.player, false, false);
                    require(count(a.player, Items.BREAD) == 1 && attempts == 0, "Ordinary bread fixture did not remain independent");
                    pass("ordinary-bread-no-award", Map.of("A", state(a.player), "B", state(b.player)));
                    move(a.player, standA);
                    next(Phase.REWARD);
                }
                case REWARD -> {
                    require(b.player.distanceToSqr(Vec3.atCenterOf(position)) > 16, "B entered the pop radius early");
                    if (published == null) {
                        breakfastState(a.player, false, false); breakfastState(b.player, false, false);
                        if (rejectedAt != 0 && tick - rejectedAt < 200) {
                            require(attempts == 1 && Files.isRegularFile(receipt("ready")) && !Files.exists(receipt("spent")),
                                    "Rejected spawn consumed or awarded a breakfast");
                        }
                        return;
                    }
                    require(level.getEntity(published.getUUID()) == published && acceptedPops == 1
                                    && attempts == expectedAttempts(), "Expected one accepted native toaster loaf");
                    breakfastState(a.player, true, false); breakfastState(b.player, false, false);
                    verifySpent();
                    if (published.hasPickUpDelay()) return;
                    originalEntityId = published.getUUID(); originalLoaf = published.getItem().copy();
                    pass("pop-awards-toast-only", Map.of("A", state(a.player), "B", state(b.player),
                            "entity", originalEntityId.toString(), "acceptedPops", acceptedPops, "spawnAttempts", attempts));
                    move(a.player, published.position());
                    published.playerTouch(a.player);
                    sync(a);
                    next(Phase.PICKUP_A);
                }
                case PICKUP_A -> {
                    if (elapsed() < 4) return;
                    breakfastState(a.player, true, true); breakfastState(b.player, false, false);
                    require(published.isRemoved() && count(a.player, item("uwu_bread")) == 1,
                            "A did not natively pick up the original loaf");
                    pass("A-original-loaf-pickup", Map.of("sourceEntity", originalEntityId.toString(), "A", state(a.player)));
                    move(a.player, farA); move(b.player, standB);
                    inspect(b.player);
                    next(Phase.INSPECT_B);
                }
                case INSPECT_B -> {
                    if (elapsed() < 5) return;
                    breakfastState(b.player, true, false);
                    require(nativeInspections == 1 && acceptedPops == 1 && count(b.player, item("uwu_bread")) == 0,
                            "Empty inspection did not remain separate from loaf ownership");
                    require(count(a.player, item("uwu_bread")) == 1 && !snapshot().hasUUID(TOKEN),
                            "A lost the original loaf or spent toaster became armed");
                    pass("B-empty-toaster-toast-only", Map.of("B", state(b.player), "acceptedPops", acceptedPops,
                            "nativeRightClickEvents", nativeInspections));
                    move(b.player, farB);
                    transferLoaf(a.player, b.player);
                    next(Phase.TRANSFER_B);
                }
                case TRANSFER_B -> {
                    if (elapsed() < 4) return;
                    breakfastState(a.player, true, true); breakfastState(b.player, true, true);
                    require(count(a.player, item("uwu_bread")) == 0 && count(b.player, item("uwu_bread")) == 1,
                            "Original loaf transfer lost or duplicated its stack");
                    pass("B-original-loaf-transfer", Map.of("B", state(b.player), "toasterOutputCount", acceptedPops));
                    move(b.player, standB); inspect(b.player); move(b.player, farB);
                    transferLoaf(b.player, a.player); transferLoaf(a.player, b.player);
                    next(Phase.REPEAT);
                }
                case REPEAT -> {
                    if (elapsed() < 5) return;
                    exactCounts(a, List.of("toaster", "loaf")); exactCounts(b, List.of("toaster", "loaf"));
                    require(acceptedPops == 1 && attempts == expectedAttempts(), "Repeated interaction produced another toaster loaf");
                    pass("repeat-no-completion-or-loaf-duplicates", Map.of("Aearned", earnedCounts(a), "Bearned", earnedCounts(b),
                            "AtoastPackets", a.toastPackets, "BtoastPackets", b.toastPackets));
                    restoreStaleSnapshot();
                    next(Phase.STALE_RELOAD);
                }
                case STALE_RELOAD -> {
                    if (elapsed() < 30) return;
                    require(!snapshot().hasUUID(TOKEN) && acceptedPops == 1 && attempts == expectedAttempts(),
                            "Stale block-entity reload respawned the spent reward");
                    verifySpent();
                    exactCounts(a, List.of("toaster", "loaf")); exactCounts(b, List.of("toaster", "loaf"));
                    pass("spent-receipt-survives-stale-reload", Map.of("token", token.toString(), "acceptedPops", acceptedPops));
                    pickup(a.player, new ItemStack(item("moonlit_potato_chowder")));
                    pickup(a.player, new ItemStack(item("dewleaf_picnic_wrap")));
                    next(Phase.FOOD_PICKUP);
                }
                case FOOD_PICKUP -> {
                    if (elapsed() < 4) return;
                    require(!completed(a.player, "chowder") && !completed(a.player, "wrap") && !completed(a.player, "galette"),
                            "Food pickup completed a consume or crafting advancement");
                    require(count(a.player, item("moonlit_potato_chowder")) == 1
                            && count(a.player, item("dewleaf_picnic_wrap")) == 1, "Native food pickups missing");
                    pass("food-pickup-does-not-count-as-eating", Map.of("A", state(a.player)));
                    craftGalette(a.player);
                    next(Phase.CRAFT);
                }
                case CRAFT -> {
                    if (elapsed() < 4) return;
                    require(completed(a.player, "galette") && count(a.player, item("petal_harvest_galette")) == 1,
                            "Native crafting-result extraction did not grant Galette advancement");
                    require(!completed(a.player, "chowder") && !completed(a.player, "wrap"), "Crafting incorrectly completed eating milestones");
                    pass("actual-galette-crafting", Map.of("A", state(a.player), "pathway", "CraftingMenu result slot pickup"));
                    eat(a.player, item("moonlit_potato_chowder"));
                    next(Phase.EAT_CHOWDER);
                }
                case EAT_CHOWDER -> {
                    if (a.player.isUsingItem()) return;
                    require(completed(a.player, "chowder") && !completed(a.player, "wrap"), "Native Chowder use did not grant only Chowder");
                    require(count(a.player, item("moonlit_potato_chowder")) == 0, "Chowder was not consumed");
                    pass("native-chowder-consumption", Map.of("A", state(a.player), "elapsedServerTicks", elapsed()));
                    eat(a.player, item("dewleaf_picnic_wrap"));
                    next(Phase.EAT_WRAP);
                }
                case EAT_WRAP -> {
                    if (a.player.isUsingItem()) return;
                    require(completed(a.player, "wrap") && count(a.player, item("dewleaf_picnic_wrap")) == 0,
                            "Native Wrap use did not consume and grant Wrap");
                    pass("native-wrap-consumption", Map.of("A", state(a.player), "elapsedServerTicks", elapsed()));
                    savedA = state(a.player); savedB = state(b.player);
                    saveAndReload(a); saveAndReload(b);
                    next(Phase.RELOADED);
                }
                case RELOADED -> {
                    if (elapsed() < 5) return;
                    require(savedA.equals(state(a.player)) && savedB.equals(state(b.player)), "Minecraft advancement reload changed player progress");
                    exactCounts(a, MILESTONES); exactCounts(b, List.of("toaster", "loaf"));
                    pass("player-progress-native-save-reload", Map.of("A", state(a.player), "B", state(b.player)));
                    move(b.player, standB); inspect(b.player); move(b.player, farB);
                    transferLoaf(b.player, a.player); transferLoaf(a.player, b.player);
                    next(Phase.FINAL_CHECK);
                }
                case FINAL_CHECK -> {
                    if (elapsed() < 5) return;
                    exactCounts(a, MILESTONES); exactCounts(b, List.of("toaster", "loaf"));
                    verifySpent();
                    require(acceptedPops == 1 && attempts == expectedAttempts(), "Post-reload action respawned a loaf");
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("faction", faction()); result.put("toaster", position.toShortString());
                    result.put("A", state(a.player)); result.put("B", state(b.player));
                    result.put("Aearned", new LinkedHashMap<>(earnedCounts(a))); result.put("Bearned", new LinkedHashMap<>(earnedCounts(b)));
                    result.put("AtoastPackets", new LinkedHashMap<>(a.toastPackets)); result.put("BtoastPackets", new LinkedHashMap<>(b.toastPackets));
                    result.put("acceptedPops", acceptedPops); result.put("spawnAttempts", attempts);
                    result.put("nativeInspections", nativeInspections); result.put("originalLoafEntity", originalEntityId.toString());
                    kitchens.add(result); pass("kitchen-complete", result);
                    cleanKitchen();
                    if (++kitchenIndex < plans.size()) loadKitchen();
                    else finish(event.getServer());
                }
                case DONE -> { }
            }
        } catch (Throwable failure) {
            done = true;
            System.err.println("UWU BREAKFAST FAILED phase=" + phase + " faction=" + kitchenIndex + " tick=" + tick);
            failure.printStackTrace();
            try { evidence("failure", Map.of("error", failure.toString(), "acceptedPops", acceptedPops, "attempts", attempts)); }
            catch (Throwable ignored) { }
            event.getServer().halt(false);
        }
    }

    private void loadKitchen() {
        BlossomVillage.Plan plan = plans.get(kitchenIndex);
        BlossomVillage.House kitchen = plan.houses().stream().filter(home -> home.job() == Blocks.SMOKER).findFirst().orElseThrow();
        position = plan.site().pos(kitchen.x() + kitchen.width() - 2, kitchen.floor() + 2,
                kitchen.z() + kitchen.side() * (kitchen.depth() - 1));
        smoker = plan.workstation(kitchen);
        kitchenChunk = new ChunkPos(position); smokerChunk = new ChunkPos(smoker);
        force(position); force(smoker);
        next(Phase.LOAD);
    }

    private void verifyKitchen() throws Exception {
        var plan = plans.get(kitchenIndex);
        var kitchen = plan.houses().stream().filter(home -> home.job() == Blocks.SMOKER).findFirst().orElseThrow();
        var state = level.getBlockState(position);
        require(generated.contains(kitchenChunk), "No native fresh chunk generation witness");
        require(level.getBlockState(smoker).is(Blocks.SMOKER), "Authentic Baker smoker missing");
        require(BuiltInRegistries.BLOCK.getKey(state.getBlock()).equals(TOASTER), "Authentic generated toaster missing");
        Direction localFacing = kitchen.side() > 0 ? Direction.NORTH : Direction.SOUTH;
        var normal = localFacing.getNormal();
        Direction facing = Direction.fromDelta(plan.site().wx(normal.getX(), normal.getZ()) - plan.site().x(), 0,
                plan.site().wz(normal.getX(), normal.getZ()) - plan.site().z());
        require(state.getValue(HorizontalDirectionalBlock.FACING) == facing, "Toaster orientation differs from authentic kitchen plan");
        var worktop = level.getBlockState(position.below());
        require(plan.dusk() ? worktop.is(Blocks.DARK_OAK_PLANKS)
                        : worktop.is(Blocks.CHERRY_PLANKS) || worktop.is(BuiltInRegistries.BLOCK.get(id("silkwood_planks"))),
                "Toaster worktop differs from kitchen plan");
        readySnapshot = snapshot();
        require(readySnapshot.hasUUID(TOKEN), "Generated toaster has no authentic token");
        token = readySnapshot.getUUID(TOKEN);
        require(Files.isRegularFile(receipt("ready")) && !Files.exists(receipt("spent")), "Fresh toaster receipt missing");
        pass("authentic-generated-kitchen", Map.of("position", position.toShortString(), "token", token.toString()));
    }

    private void chunkLoaded(ChunkEvent.Load event) {
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel world && world.dimension().equals(ReefLife.REACH))
            generated.add(event.getChunk().getPos());
    }

    private void joined(EntityJoinLevelEvent event) {
        if (level == null || position == null || event.getLevel() != level || done) return;
        if (!(event.getEntity() instanceof ItemEntity loaf) || !loaf.getItem().is(item("uwu_bread"))
                || loaf.position().distanceToSqr(Vec3.atCenterOf(position)) > 1) return;
        try {
            ++attempts;
            require(!event.loadedFromDisk() && loaf.getItem().getCount() == 1 && loaf.hasPickUpDelay()
                            && loaf.getDeltaMovement().y > .2, "Unexpected toaster loaf spawn pathway");
            if (phase == Phase.REWARD && kitchenIndex == 0 && attempts == 1) {
                rejectedAt = tick; event.setCanceled(true);
                evidence("rejectedSpawnFixture", Map.of("retryDelayTicks", 200));
            } else { published = loaf; acceptedPops++; }
        } catch (Throwable failure) { asynchronousFailure = failure; }
    }

    private void earned(AdvancementEvent.AdvancementEarnEvent event) {
        ResourceLocation id = event.getAdvancement().id();
        String milestone = milestone(id);
        if (milestone == null) return;
        earns.computeIfAbsent(event.getEntity().getUUID(), ignored -> new LinkedHashMap<>()).merge(milestone, 1, Integer::sum);
        try { evidence("earned", Map.of("player", event.getEntity().getUUID().toString(), "advancement", id.toString())); }
        catch (Throwable failure) { asynchronousFailure = failure; }
    }

    private void inspected(PlayerInteractEvent.RightClickBlock event) {
        if (level != null && event.getLevel() == level && event.getPos().equals(position) && b != null && event.getEntity() == b.player)
            nativeInspections++;
    }

    private void inspect(ServerPlayer player) {
        require(player.distanceToSqr(Vec3.atCenterOf(position)) <= 16, "Inspector outside native reach fixture");
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(position), Direction.UP, position, false);
        player.gameMode.useItemOn(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND, hit);
        player.inventoryMenu.broadcastChanges();
        player.getAdvancements().flushDirty(player);
    }

    private void pickup(ServerPlayer player, ItemStack stack) {
        ItemEntity entity = new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack);
        entity.setNoPickUpDelay();
        require(level.addFreshEntity(entity), "Fixture item spawn was rejected");
        entity.playerTouch(player);
        require(entity.isRemoved(), "Native pickup did not remove its source entity");
        player.inventoryMenu.broadcastChanges();
        player.getAdvancements().flushDirty(player);
    }

    private void transferLoaf(ServerPlayer from, ServerPlayer to) {
        require(from.distanceToSqr(Vec3.atCenterOf(position)) > 16 && to.distanceToSqr(Vec3.atCenterOf(position)) > 16,
                "Transfer fixture could be mistaken for a toaster pop");
        int slot = find(from, item("uwu_bread"));
        ItemStack actual = from.getInventory().removeItem(slot, 1);
        require(ItemStack.matches(actual, originalLoaf), "Transfer did not use the original loaf's complete stack data");
        from.inventoryMenu.broadcastChanges();
        pickup(to, actual);
    }

    private void craftGalette(ServerPlayer player) {
        BlockPos table = farA.below();
        level.setBlockAndUpdate(table, Blocks.CRAFTING_TABLE.defaultBlockState());
        require(player.openMenu(new SimpleMenuProvider((containerId, inventory, actor) ->
                new CraftingMenu(containerId, inventory, ContainerLevelAccess.create(level, table)),
                Component.literal("Breakfast fixture crafting"))).isPresent(), "Native crafting menu did not open");
        require(player.containerMenu instanceof CraftingMenu, "Wrong native menu");
        CraftingMenu menu = (CraftingMenu) player.containerMenu;
        menu.getSlot(1).set(new ItemStack(item("uwu_wheat")));
        menu.getSlot(2).set(new ItemStack(item("uwu_potato")));
        menu.getSlot(3).set(new ItemStack(item("glowginger")));
        require(menu.getSlot(0).getItem().is(item("petal_harvest_galette")), "Real crafting grid did not resolve the Galette recipe");
        require(!completed(player, "galette"), "Populating a crafting grid awarded completion before taking the result");
        menu.clicked(0, 0, ClickType.PICKUP, player);
        require(menu.getCarried().is(item("petal_harvest_galette")), "Native result-slot click did not take the crafted Galette");
        require(menu.getSlot(1).getItem().isEmpty() && menu.getSlot(2).getItem().isEmpty() && menu.getSlot(3).getItem().isEmpty(),
                "Native craft did not consume its three ingredients");
        player.closeContainer();
        player.inventoryMenu.broadcastChanges();
        player.getAdvancements().flushDirty(player);
    }

    private void eat(ServerPlayer player, Item food) {
        int slot = find(player, food);
        require(slot < 9, "Food fixture must be in the normal hotbar");
        player.getInventory().selected = slot;
        player.getFoodData().setFoodLevel(1);
        player.gameMode.useItem(player, level, player.getMainHandItem(), InteractionHand.MAIN_HAND);
        require(player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND && player.getUseItem().is(food),
                "Native right-click item use did not begin food consumption");
    }

    private void saveAndReload(RecordingListener actor) throws Exception {
        sync(actor);
        actor.player.getAdvancements().save();
        Path file = level.getServer().getWorldPath(LevelResource.ROOT).resolve("advancements").resolve(actor.player.getUUID() + ".json");
        require(Files.isRegularFile(file), "Minecraft did not save player advancement progress");
        String persisted = Files.readString(file);
        for (String name : MILESTONES) if (completed(actor.player, name))
            require(persisted.contains(adv(name).toString()), "Saved advancement file omitted completed " + name);
        Files.createDirectories(OUT.resolve("progress-before-reload"));
        Files.copy(file, OUT.resolve("progress-before-reload").resolve(actor.player.getUUID() + ".json"),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        actor.player.getAdvancements().reload(level.getServer().getAdvancements());
        actor.player.getAdvancements().flushDirty(actor.player);
    }

    private void verifySpent() throws Exception {
        require(!Files.exists(receipt("ready")) && Files.isRegularFile(receipt("spent")), "Ready receipt was not durably consumed");
        CompoundTag saved = NbtIo.readCompressed(receipt("spent"), NbtAccounter.unlimitedHeap());
        require(saved.getInt("Version") == 1 && saved.getLong("Position") == position.asLong()
                        && saved.getString("Dimension").equals(ReefLife.REACH.location().toString()) && saved.getUUID("Token").equals(token),
                "Spent receipt did not preserve the original native toaster identity");
    }

    private void restoreStaleSnapshot() throws Exception {
        var field = Class.forName("dev.uwu.dimension.ToastClaims").getDeclaredField("WORLDS");
        field.setAccessible(true);
        require(((Map<?, ?>)field.get(null)).remove(level.getServer()) != null, "Could not drop receipt cache for native reload fixture");
        BlockEntity replacement = BlockEntity.loadStatic(position, level.getBlockState(position), readySnapshot.copy(), level.registryAccess());
        require(replacement != null, "Could not deserialize old ready-state block entity");
        level.setBlockEntity(replacement);
        require(level.getBlockEntity(position) == replacement && snapshot().getUUID(TOKEN).equals(token), "Stale native block entity not installed");
    }

    private CompoundTag snapshot() {
        BlockEntity toaster = level.getBlockEntity(position);
        require(toaster != null && BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(toaster.getType()).equals(TOASTER), "Native Baker block entity missing");
        return toaster.saveWithFullMetadata(level.registryAccess());
    }
    private Path receipt(String suffix) {
        return level.getServer().getWorldPath(LevelResource.ROOT).resolve("data/uwudimension_baker_toasters/sakura_reach")
                .resolve(position.getX() + "_" + position.getY() + "_" + position.getZ() + "." + suffix);
    }

    private List<BlockPos> standingSpots(Direction intoRoom) {
        List<BlockPos> spots = new ArrayList<>();
        BlockPos center = position.below(), preferred = center.relative(intoRoom, 2);
        for (BlockPos feet : BlockPos.betweenClosed(center.offset(-3, 0, -3), center.offset(3, 0, 3))) {
            Vec3 point = Vec3.atBottomCenterOf(feet);
            double distance = point.distanceToSqr(Vec3.atCenterOf(position));
            if (distance > 16 || distance < 2.5 || !level.isPositionEntityTicking(feet)) continue;
            AABB body = new AABB(point.x - .3, point.y, point.z - .3, point.x + .3, point.y + 1.8, point.z + .3);
            if (level.getBlockState(feet.below()).isFaceSturdy(level, feet.below(), Direction.UP) && level.noCollision(body)) spots.add(feet.immutable());
        }
        spots.sort(Comparator.comparingDouble(feet -> feet.distSqr(preferred)));
        require(spots.size() >= 2, "Kitchen lacks two valid standing positions outside automatic pickup distance");
        return spots;
    }

    private RecordingListener spawn(String name, BlockPos feet) throws Exception {
        MinecraftServer server = level.getServer();
        ServerPlayer player = new ServerPlayer(server, level, new GameProfile(UUID.randomUUID(), name), ClientInformation.createDefault());
        RecordingListener listener = new RecordingListener(this, server, player);
        player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); move(player, feet);
        onlinePlayers(server).add(player); playersById(server).put(player.getUUID(), player);
        level.addNewPlayer(player);
        player.initInventoryMenu();
        player.getAdvancements().flushDirty(player);
        earns.putIfAbsent(player.getUUID(), new LinkedHashMap<>());
        require(level.getEntity(player.getId()) == player && !player.hasDisconnected() && !player.isFakePlayer(), "Fixture actor is not a live independent ServerPlayer");
        for (String milestone : MILESTONES) require(!completed(player, milestone), "Fixture player has prior breakfast progress");
        return listener;
    }

    private void cleanKitchen() throws Exception {
        for (RecordingListener listener : List.of(a, b)) {
            listener.player.getAdvancements().save();
            onlinePlayers(level.getServer()).remove(listener.player);
            playersById(level.getServer()).remove(listener.player.getUUID());
            level.removePlayerImmediately(listener.player, Entity.RemovalReason.DISCARDED);
            listener.transport.finishAndReleaseAll();
        }
        a = null; b = null;
        if (published != null && !published.isRemoved()) published.discard();
        for (BlockPos at : List.of(position, smoker, farA, farB)) {
            ChunkPos chunk = new ChunkPos(at); level.setChunkForced(chunk.x, chunk.z, false);
        }
        published = null; attempts = 0; rejectedAt = 0; acceptedPops = 0; nativeInspections = 0;
    }

    @SuppressWarnings("unchecked")
    private static List<ServerPlayer> onlinePlayers(MinecraftServer server) throws Exception {
        var field = PlayerList.class.getDeclaredField("players"); field.setAccessible(true);
        return (List<ServerPlayer>)field.get(server.getPlayerList());
    }
    @SuppressWarnings("unchecked")
    private static Map<UUID, ServerPlayer> playersById(MinecraftServer server) throws Exception {
        var field = PlayerList.class.getDeclaredField("playersByUUID"); field.setAccessible(true);
        return (Map<UUID, ServerPlayer>)field.get(server.getPlayerList());
    }
    private void force(BlockPos at) { ChunkPos chunk = new ChunkPos(at); level.setChunkForced(chunk.x, chunk.z, true); }
    private static void move(ServerPlayer player, BlockPos at) { move(player, Vec3.atBottomCenterOf(at)); }
    private static void move(ServerPlayer player, Vec3 at) { player.moveTo(at.x, at.y, at.z, 0, 0); player.setDeltaMovement(Vec3.ZERO); player.fallDistance = 0; }
    private static void sync(RecordingListener actor) {
        if (actor != null) { actor.player.inventoryMenu.broadcastChanges(); actor.player.getAdvancements().flushDirty(actor.player); }
    }
    private static int find(ServerPlayer player, Item item) {
        for (int index = 0; index < player.getInventory().getContainerSize(); index++)
            if (player.getInventory().getItem(index).is(item)) return index;
        throw new AssertionError("Missing fixture item " + BuiltInRegistries.ITEM.getKey(item));
    }
    private static int count(ServerPlayer player, Item item) {
        int count = 0;
        for (int index = 0; index < player.getInventory().getContainerSize(); index++)
            if (player.getInventory().getItem(index).is(item)) count += player.getInventory().getItem(index).getCount();
        return count;
    }
    private static Item item(String name) {
        Item item = BuiltInRegistries.ITEM.get(id(name)); require(item != Items.AIR, "Missing registered item " + name); return item;
    }
    private static ResourceLocation id(String path) { return ResourceLocation.fromNamespaceAndPath("uwudimension", path); }
    private static ResourceLocation adv(String name) { return id("activities/pantry/" + name); }
    private static String milestone(ResourceLocation id) {
        for (String name : MILESTONES) if (adv(name).equals(id)) return name;
        return null;
    }
    private static boolean completed(ServerPlayer player, String name) {
        var holder = player.server.getAdvancements().get(adv(name));
        require(holder != null, "Missing loaded advancement " + adv(name));
        return player.getAdvancements().getOrStartProgress(holder).isDone();
    }
    private static Map<String, Boolean> state(ServerPlayer player) {
        Map<String, Boolean> state = new LinkedHashMap<>();
        for (String name : MILESTONES) state.put(name, completed(player, name));
        return state;
    }
    private static void breakfastState(ServerPlayer player, boolean toast, boolean loaf) {
        require(completed(player, "toaster") == toast && completed(player, "loaf") == loaf,
                "Wrong breakfast state for " + player.getGameProfile().getName() + ": " + state(player));
    }
    private Map<String, Integer> earnedCounts(RecordingListener actor) { return earns.getOrDefault(actor.player.getUUID(), Map.of()); }
    private void exactCounts(RecordingListener actor, List<String> expected) {
        for (String name : MILESTONES) {
            int count = expected.contains(name) ? 1 : 0;
            require(earnedCounts(actor).getOrDefault(name, 0) == count, "Wrong native earned-event count for " + name + ": " + earnedCounts(actor));
            require(actor.toastPackets.getOrDefault(name, 0) == count, "Wrong outgoing non-reset completed-advancement delta count for " + name + ": " + actor.toastPackets);
        }
    }
    private int expectedAttempts() { return kitchenIndex == 0 ? 2 : 1; }
    private int elapsed() { return tick - began; }
    private String faction() { return kitchenIndex < plans.size() ? (plans.get(kitchenIndex).dusk() ? "Dusk" : "Petal") : "complete"; }
    private void next(Phase next) { phase = next; began = tick; System.out.println("BREAKFAST PHASE " + faction() + " " + phase); }
    private static void require(boolean good, String message) { if (!good) throw new AssertionError(message); }

    private void evidence(String kind, Map<String, ?> values) throws Exception {
        Files.createDirectories(OUT);
        Files.writeString(OUT.resolve("breakfast.jsonl"), LINE.toJson(Map.of("kind", kind, "phase", phase.toString(),
                "faction", faction(), "tick", tick, "values", values)) + "\n", StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
    private void pass(String name, Map<String, ?> observed) throws Exception {
        Map<String, Object> record = Map.of("check", name, "faction", faction(), "observed", observed);
        passes.add(record); evidence("pass", record); System.out.println("BREAKFAST CHECK PASS " + name + " " + LINE.toJson(observed));
    }
    private void finish(MinecraftServer server) throws Exception {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("status", "pass"); summary.put("candidateSha256", System.getProperty("uwu.check.candidateSha256"));
        summary.put("actors", "Independent Survival ServerPlayer fixtures; memory connections; no GUI clients");
        summary.put("kitchens", kitchens); summary.put("passCount", passes.size());
        summary.put("limits", List.of("No rendered client advancement-toast screenshot", "No operating-system mouse/keyboard injection",
                "Save/reload uses Minecraft advancement reload and stale block-entity receipt recovery, not a process restart",
                "No claim-provider, Creative inspection, or all-modpack matrix"));
        Files.writeString(OUT.resolve("summary.json"), JSON.toJson(summary) + "\n");
        evidence("complete", summary);
        done = true; phase = Phase.DONE;
        System.out.println("UWU BREAKFAST PASS"); server.halt(false);
    }

    private static final class RecordingListener extends ServerGamePacketListenerImpl {
        final EmbeddedChannel transport;
        final Map<String, Integer> toastPackets = new LinkedHashMap<>();
        private BreakfastProbe probe;
        int dings;
        RecordingListener(BreakfastProbe probe, MinecraftServer server, ServerPlayer player) {
            this(probe, server, new Connection(PacketFlow.SERVERBOUND), player);
        }
        private RecordingListener(BreakfastProbe probe, MinecraftServer server, Connection connection, ServerPlayer player) {
            super(server, connection, player, CommonListenerCookie.createInitial(player.getGameProfile(), false));
            this.probe = probe;
            transport = new EmbeddedChannel(connection);
            require(connection.channel() == transport, "Fixture memory channel failed");
        }
        @Override public void send(Packet<?> packet, PacketSendListener listener) {
            if (probe == null) return;
            try {
                if (packet instanceof ClientboundSoundPacket sound && sound.getSound().value().getLocation().equals(DING)) {
                    require(sound.getSource() == SoundSource.BLOCKS && Math.abs(sound.getVolume() - .55F) < .001F
                            && Math.abs(sound.getPitch() - 1F) < .001F, "Native toaster ding parameters changed");
                    dings++;
                }
                if (packet instanceof ClientboundUpdateAdvancementsPacket update) {
                    // Mojang has used both bean accessors and record accessors for packet classes;
                    // observation supports either without changing or synthesizing any packet.
                    boolean reset = (boolean)read(update, "shouldReset", "reset");
                    @SuppressWarnings("unchecked") Map<ResourceLocation, AdvancementProgress> progress =
                            (Map<ResourceLocation, AdvancementProgress>)read(update, "getProgress", "progress");
                    if (!reset) for (var entry : progress.entrySet()) {
                        String name = milestone(entry.getKey());
                        if (name != null && entry.getValue().isDone()) toastPackets.merge(name, 1, Integer::sum);
                    }
                }
            } catch (Throwable failure) { probe.asynchronousFailure = failure; }
        }
        private static Object read(Object packet, String... names) throws Exception {
            for (String name : names) {
                try { Method method = packet.getClass().getMethod(name); return method.invoke(packet); }
                catch (NoSuchMethodException ignored) { }
            }
            throw new NoSuchMethodException("Advancement packet accessor missing: " + String.join("/", names));
        }
    }
}
