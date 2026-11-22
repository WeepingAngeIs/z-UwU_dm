package dev.uwu.villagesize;

import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.BlossomWarden;
import dev.uwu.dimension.People;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.AABB;

/** Native generation and normal entity loading must agree on faction without spawning a test Guardian. */
final class GuardianVillageWitness {
    static final boolean ENABLED = Boolean.getBoolean("uwu.check.guardianFactions");

    static List<BlossomWarden> residents(ServerLevel level, BlossomVillage.Plan plan) {
        return level.getEntitiesOfClass(BlossomWarden.class,new AABB(plan.plaza()).inflate(52),guard -> {
            CompoundTag saved = new CompoundTag();
            guard.addAdditionalSaveData(saved);
            return guard.isAlive() && saved.contains("VillageCourt") && saved.getLong("VillageCourt") == plan.plaza().asLong();
        });
    }

    static String verify(ServerLevel level, BlossomVillage.Plan plan) {
        var residents = residents(level, plan);
        VillageSizeProbe.require(residents.size() == 1,"Expected one naturally generated plaza Guardian at "+plan.plaza()+", got "+residents.size());
        BlossomWarden guard = residents.getFirst();
        String expected = plan.dusk() ? "entity.uwudimension.dusk_guardian" : "entity.uwudimension.blossom_warden";
        VillageSizeProbe.require(guard.getType() == People.BLOSSOM_WARDEN.get() && guard.duskGuardian() == plan.dusk(),
                "Entity ID or Guardian faction differs from the built village");
        VillageSizeProbe.require(guard.getName().getContents() instanceof TranslatableContents name && expected.equals(name.getKey()),
                "Wrong displayed Guardian name at "+plan.plaza());
        System.out.println("NATIVE VILLAGE GUARDIAN "+plan.plaza()+" "+guard.getUUID()+" "+expected);
        return expected;
    }

    private GuardianVillageWitness() {}
}
