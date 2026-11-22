package dev.uwu.dimension;

import java.util.LinkedHashMap;
import java.util.Map;
import net.neoforged.neoforge.common.ModConfigSpec;

/** The config editor and operator commands share these registered SERVER values. */
public final class WeaponBalance {
    static final Map<String, Map<String, Setting>> GROUPS = new LinkedHashMap<>();
    static final Map<String, Setting> BASE = new LinkedHashMap<>();
    static final String DIRK = "returnbloom_dirk", AXE = "dawnpetal_axe";
    static final String BASE_HIT = "baseHitDamage", ABILITY = "abilityDamage", CAST = "castCooldownSeconds";
    static final String THROW = "throwCooldownSeconds", THROW_DAMAGE = "throwDamage", WAVE = "waveCooldownSeconds";
    static final String BACKSTAB = "backstabMultiplier", SHARED = "blossom_revival";

    record Setting(String label, double original, double min, double max, ModConfigSpec.DoubleValue value) {
        double current() { return AcquisitionPolicy.SPEC.isLoaded() ? value.get() : original; }
    }

    static void define(ModConfigSpec.Builder builder) {
        builder.push("weapons");
        Group dirk = new Group(builder, DIRK, "Returnbloom Dirk");
        dirk.base(15);
        dirk.time("turnBackCooldownSeconds", "Turn Back melee trigger cooldown", 120);
        dirk.time(THROW, "throw cooldown", 100);
        dirk.damage(THROW_DAMAGE, "thrown blade damage", 10);
        dirk.add(BACKSTAB, "melee backstab multiplier", 2, 1, 8);
        Group axe = new Group(builder, AXE, "Dawnpetal Axe");
        axe.base(22);
        axe.time(THROW, "throw cooldown", 100);
        axe.damage(THROW_DAMAGE, "thrown axe damage", 10);
        axe.time("spinCooldownSeconds", "Spin cooldown", 70);
        axe.time("heartSpinCooldownSeconds", "Heart Spin cooldown", 120);
        axe.damage("spinDamage", "Spin damage per struck target", 8);
        axe.damage("heartSpinDamage", "Heart Spin damage per struck target", 8);
        new Group(builder, SHARED, "Returnbloom Dirk and Dawnpetal Axe")
                .time("rechargeSeconds", "shared fatal-hit revival recharge", 1200);
        Group launcher = new Group(builder, "heart_launcher", "Heart Launcher");
        launcher.time(CAST, "firing cooldown", 12);
        launcher.damage(ABILITY, "heart blast damage", 8);
        new Group(builder, "petalfold_shield", "Petalfold Shield").time("blockCharmSeconds", "successful block charm", 120);
        for (var relic : Arsenal.Relic.values()) {
            Group group = new Group(builder, relic.id(), relic.title);
            group.base(relic.damage);
            group.damage(ABILITY, relic.bow() ? "full-draw arrow damage when damage is dealt" : "direct ability hit damage", relic.damage);
            switch (relic) {
                case RIBBONHARE_SCEPTER -> group.time("hareLifetimeSeconds", "summoned hare lifetime", 160);
                case AXOLOTL_WHIP -> {
                    group.time("companionLifetimeSeconds", "axolotl companion lifetime", 600);
                    group.damage("companionHitDamage", "axolotl companion attack damage", 4);
                }
                case RIFT_WHIP -> group.time("markDurationSeconds", "teleport mark lifetime", 160);
                case FROST_WHIP -> group.time("slowSeconds", "Snowkiss movement slow", 60);
                case RIPPLE_BOW, BLUE_HOUR_WAND -> {
                    group.time("slowSeconds", "movement slow", relic.bow() ? 75 : 80);
                    group.time("glowSeconds", "reveal duration", 140);
                }
                default -> {}
            }
            if (relic == Arsenal.Relic.VESPER_SCYTHE) {
                group.time(THROW, "physical throw cooldown", 60);
                group.time(WAVE, "crescent attack cooldown", 14);
            } else if (relic == Arsenal.Relic.CUPID_BOW) {
                group.add(CAST, "shot cooldown in seconds", 0, 0, 3600);
            } else {
                group.time(CAST, "cast cooldown", relic.cooldown);
            }
        }
        for (var entry : CounterArsenal.ROSTER) {
            if (!CounterArsenal.selectable(entry)) continue;
            Group group = new Group(builder, entry.id(), entry.name());
            group.base(entry.damage());
            group.damage(ABILITY, "direct or delayed echo damage before any rear bonus", entry.damage());
            group.time(CAST, "cast or charged throw cooldown", 60);
            if (entry.shape().equals("scythe")) group.time(WAVE, "crescent attack cooldown", 14);
        }
        builder.pop();
    }

    private record Group(ModConfigSpec.Builder builder, String id, String title) {
        void base(int original) {
            BASE.put(id, add(BASE_HIT, "fully charged main-hand base hit damage", original, 1, 256));
        }
        void time(String key, String label, int ticks) {
            add(key, label + " in seconds", ticks / 20.0, .05, 3600);
        }
        void damage(String key, String label, double original) {
            add(key, label + " in health points", original, 1, 256);
        }
        Setting add(String key, String label, double original, double min, double max) {
            String description = title + " " + label;
            var value = builder.comment(description + ".").defineInRange(java.util.List.of(id, key), original, min, max);
            var setting = new Setting(description, original, min, max, value);
            GROUPS.computeIfAbsent(id, ignored -> new LinkedHashMap<>()).put(key, setting);
            return setting;
        }
    }

    static int ticks(String weapon, String key) {
        return (int) Math.round(GROUPS.get(weapon).get(key).current() * 20);
    }
    static float amount(String weapon, String key) {
        return (float) GROUPS.get(weapon).get(key).current();
    }
    static int cast(Arsenal.Relic relic) { return ticks(relic.id(), CAST); }
    static float damage(Arsenal.Relic relic) { return amount(relic.id(), ABILITY); }
    static int counterCast(CounterArsenal.Entry entry) {
        return CounterArsenal.selectable(entry) ? ticks(entry.id(), CAST) : 60;
    }
    static float counterDamage(CounterArsenal.Entry entry) {
        return CounterArsenal.selectable(entry) ? amount(entry.id(), ABILITY) : entry.damage();
    }
    static int scytheThrow(CounterArsenal.Entry counter) {
        return counter == null ? ticks(Arsenal.Relic.VESPER_SCYTHE.id(), THROW) : counterCast(counter);
    }
    static int wave(CounterArsenal.Entry counter) {
        return ticks(counter == null ? Arsenal.Relic.VESPER_SCYTHE.id() : counter.id(), WAVE);
    }

    private WeaponBalance() {}
}
