package dev.uwu.dimension;

import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;

@EventBusSubscriber(modid = "uwudimension")
public final class NativeChickens {
    public static final byte VANILLA = 0, PETAL = 1, DUSK = 2;
    private static final Map<ResourceLocation, Byte> PASTURES = Map.of(
            UwUDimension.id("lantern_fens"), PETAL, UwUDimension.id("ribbon_bluffs"), PETAL,
            UwUDimension.id("starlace_frostlands"), PETAL, UwUDimension.id("dusk_frostlands"), DUSK,
            UwUDimension.id("velvet_highlands"), DUSK);

    public interface BirthCoat {
        byte uwu$getCoat();
        void uwu$setCoat(byte coat);
    }

    static byte pasture(Holder<Biome> biome) {
        return biome.unwrapKey().map(key -> PASTURES.getOrDefault(key.location(), VANILLA)).orElse(VANILLA);
    }

    @SubscribeEvent
    public static void onSpawn(FinalizeSpawnEvent event) {
        if (event.getEntity().getType() != EntityType.CHICKEN) return;
        if (event.getSpawnType() != MobSpawnType.NATURAL && event.getSpawnType() != MobSpawnType.CHUNK_GENERATION) return;
        var region = event.getLevel();
        if (region.getLevel().dimension() != ReefLife.REACH) return;
        var chicken = (Chicken) event.getEntity();
        ((BirthCoat) chicken).uwu$setCoat(pasture(region.getBiome(chicken.blockPosition())));
    }

    public static byte visibleCoat(Chicken chicken) {
        if (chicken.getType() != EntityType.CHICKEN || chicken.level().dimension() != ReefLife.REACH) return VANILLA;
        byte coat = ((BirthCoat) chicken).uwu$getCoat();
        return coat != VANILLA && coat == pasture(chicken.level().getBiome(chicken.blockPosition())) ? coat : VANILLA;
    }
}
