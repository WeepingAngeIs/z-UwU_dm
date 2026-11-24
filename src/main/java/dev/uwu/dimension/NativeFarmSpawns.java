package dev.uwu.dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.Holder;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings.SpawnerData;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

@EventBusSubscriber(modid = "uwudimension")
public final class NativeFarmSpawns {
    // NaturalSpawner checks that its selected entry is still in the potential list by identity.
    private static final SpawnerData CHICKENS = new SpawnerData(EntityType.CHICKEN, 1, 2, 3);

    private static boolean meadow(Holder<Biome> biome) {
        return NativeChickens.pasture(biome) != NativeChickens.VANILLA;
    }

    private static void addMissing(List<SpawnerData> original, Consumer<SpawnerData> add) {
        // Preserve empty or structure-specific lists that replace the meadow's livestock.
        if (original.stream().noneMatch(s -> s.type == EntityType.PIG)
                || original.stream().noneMatch(s -> s.type == EntityType.SHEEP)) return;
        if (original.stream().noneMatch(s -> s.type == EntityType.CHICKEN)) add.accept(CHICKENS);
    }

    @SubscribeEvent
    public static void livingMeadow(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.CREATURE
                || !(event.getLevel() instanceof ServerLevelAccessor region)
                || region.getLevel().dimension() != ReefLife.REACH
                || !meadow(region.getBiome(event.getPos()))) return;
        addMissing(event.getSpawnerDataList(), event::addSpawnerData);
    }

    public static WeightedRandomList<SpawnerData> generationPool(ServerLevelAccessor region, Holder<Biome> biome,
            WeightedRandomList<SpawnerData> original) {
        if (region.getLevel().dimension() != ReefLife.REACH || !meadow(biome)) return original;
        var entries = new ArrayList<>(original.unwrap());
        addMissing(original.unwrap(), entries::add);
        return entries.size() == original.unwrap().size() ? original : WeightedRandomList.create(entries);
    }
}
