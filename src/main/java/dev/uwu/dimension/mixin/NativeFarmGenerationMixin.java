package dev.uwu.dimension.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.uwu.dimension.NativeFarmSpawns;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(NaturalSpawner.class)
abstract class NativeFarmGenerationMixin {
    @ModifyExpressionValue(method = "spawnMobsForChunkGeneration", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/biome/MobSpawnSettings;getMobs(Lnet/minecraft/world/entity/MobCategory;)Lnet/minecraft/util/random/WeightedRandomList;"))
    private static WeightedRandomList<MobSpawnSettings.SpawnerData> uwu$meadowHerds(WeightedRandomList<MobSpawnSettings.SpawnerData> original,
            ServerLevelAccessor region, Holder<Biome> biome, ChunkPos chunk, RandomSource random) {
        return NativeFarmSpawns.generationPool(region, biome, original);
    }
}
