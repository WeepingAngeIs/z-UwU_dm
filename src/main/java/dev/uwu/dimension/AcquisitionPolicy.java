package dev.uwu.dimension;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.ModConfigSpec;
/** Stable server-policy API for companion mods. Registered items and owned inventory never change. */
public final class AcquisitionPolicy {
 public static final ModConfigSpec SPEC;
 public static final ModConfigSpec.IntValue SETTLEMENT_DISTANCE;
 public static final ModConfigSpec.IntValue DUSK_SITE_CHANCE;
 static final int MAX_TIMER_SECONDS = Integer.MAX_VALUE / 20;
 public static final int MAX_CUPID_BOW_CHARM_SECONDS = MAX_TIMER_SECONDS;
 public static final ModConfigSpec.IntValue CUPID_BOW_CHARM_SECONDS;
 private static final int DEFAULT_UWUZI_FREEZE_SECONDS = 4;
 static final ModConfigSpec.IntValue UWUZI_FREEZE_SECONDS;
 public static final ModConfigSpec.BooleanValue GUARDIAN_FAMILY;
 public static final ModConfigSpec.IntValue GUARDIAN_COOLDOWN,GUARDIAN_GROWTH,GUARDIAN_READY,GUARDIAN_PAIR_RADIUS,
         GUARDIAN_POPULATION_RADIUS,GUARDIAN_POPULATION_CAP,GUARDIAN_PERMISSION;
 public static final ModConfigSpec.BooleanValue HEART_LOCKET_CRAFTING;
 public static final ModConfigSpec.BooleanValue WEAPON_ACQUISITION;
 public static final ModConfigSpec.BooleanValue LOOTR_FOR_NEW_PERSONAL_CHESTS;
 public static final ModConfigSpec.BooleanValue AREA_MINING;
 public static final ModConfigSpec.BooleanValue CUPID_WISHES, CUPID_PLAYER_WITNESSES;
 public static final ModConfigSpec.IntValue CUPID_WISH_RADIUS, CUPID_WISH_PERMISSION;
 static {
  var b=new ModConfigSpec.Builder();
  SETTLEMENT_DISTANCE=b.comment("Minimum separation between native new Petal/Dusk village footprints, in blocks. Chosen when the world first opens with this version and then saved per world; old villages keep their locations.").worldRestart().defineInRange("nativeVillageSpacingBlocks",384,192,2048);
  DUSK_SITE_CHANCE=b.comment("Chance, in percent, for each newly considered habitable Dusk settlement site in Sakura Reach (not per chunk or biome). Existing and already-touched sites retain their decision; edits apply to untouched future sites. Petal placement and saved village spacing are unchanged.").defineInRange("duskVillageChancePercent",75,0,100);
  HEART_LOCKET_CRAFTING=b.comment("Allow crafting heartwater_locket. Restart the world/server after changing; clients receive the server policy.").worldRestart().define("heartLocketCraftingEnabled",true);
  WEAPON_ACQUISITION=b.comment("Allow new survival acquisition of all UwU weapons through crafting, trades and generated loot (including unopened Lootr rolls). Existing owned/opened inventories and operator/creative access remain. Restart after changing.").worldRestart().define("weaponAcquisitionEnabled",true);
  AREA_MINING=b.comment("Allow deliberate 9 by 9 Bell Hammer mining. Block permissions and harvest rules still apply.").define("bellHammerAreaMiningEnabled",true);
  LOOTR_FOR_NEW_PERSONAL_CHESTS=b.comment("Opt in to compatible Lootr rewards at eligible new personal chest sites in Sakura Reach. Native rewards are the default. The Mirror Court seal uses the native menu. Existing rewards retain their assigned backend.")
          .worldRestart().define("useLootrForNewPersonalChests",false);
  CUPID_BOW_CHARM_SECONDS=b.comment("Cupid Bow enemy charm duration in whole seconds. Each eligible arrow impact starts a new timer with the current value. Active charms keep their deadline.").defineInRange("cupidBowCharmSeconds",40,1,MAX_CUPID_BOW_CHARM_SECONDS);
  UWUZI_FREEZE_SECONDS = b.comment("UwUzi time-freeze duration in whole seconds. Changes apply the next time a player activates UwUzi.")
          .translation("uwudimension.configuration.uwuziFreezeSeconds")
          .defineInRange("uwuziFreezeSeconds", DEFAULT_UWUZI_FREEZE_SECONDS, 1, MAX_TIMER_SECONDS);
  WeaponBalance.define(b);
  b.push("cupidWishes");
  CUPID_WISHES=b.comment("Allow two selected charmed targets to summon a young creature.").define("enabled",true);
  CUPID_PLAYER_WITNESSES=b.comment("Allow a player who opts in to witness a creature wish.").define("playerWitnesses",true);
  CUPID_WISH_RADIUS=b.comment("Maximum distance between the two targets in blocks.").defineInRange("pairRadius",8,2,24);
  CUPID_WISH_PERMISSION=b.comment("Required command permission level of the archer.").defineInRange("permissionLevel",0,0,4);
  b.pop();
  b.push("guardianFamily");
  GUARDIAN_FAMILY=b.comment("Allow adult Owl Guardians to raise young when each is fed a native crop.").define("enabled",true);
  GUARDIAN_COOLDOWN=b.comment("Ticks each parent rests after a successful birth.").defineInRange("cooldownTicks",6000,1,240000);
  GUARDIAN_GROWTH=b.comment("Loaded ticks required for a newborn to grow into its adult sex variant.").defineInRange("growthTicks",24000,1,240000);
  GUARDIAN_READY=b.comment("Server ticks an individually fed guardian remains receptive.").defineInRange("readyTicks",600,20,24000);
  GUARDIAN_PAIR_RADIUS=b.comment("Maximum 3D distance between two receptive adults.").defineInRange("pairRadius",8,2,16);
  GUARDIAN_POPULATION_RADIUS=b.comment("Radius around the proposed birth for counting living Owl Guardians.").defineInRange("populationRadius",32,8,48);
  GUARDIAN_POPULATION_CAP=b.comment("Refuse a birth when this many guardians, including adults and young, are already in range.").defineInRange("populationCap",6,2,64);
  GUARDIAN_PERMISSION=b.comment("Minimum command permission level for feeding guardian parents.").defineInRange("permissionLevel",0,0,4);
  b.pop();
  SPEC=b.build();
 }
 /** Stable server-policy API used by the optional Unyielding configuration adapter. */
 public static int cupidBowCharmSeconds(){return SPEC.isLoaded()?CUPID_BOW_CHARM_SECONDS.get():40;}
 public static int uwuziFreezeSeconds() {
  return SPEC.isLoaded() ? UWUZI_FREEZE_SECONDS.get() : DEFAULT_UWUZI_FREEZE_SECONDS;
 }
 public static int duskVillageChancePercent(){return SPEC.isLoaded()?DUSK_SITE_CHANCE.get():75;}
 public static boolean heartLocketCraftingEnabled(){return !SPEC.isLoaded()||HEART_LOCKET_CRAFTING.get();}
 public static boolean weaponAcquisitionEnabled(){return !SPEC.isLoaded()||WEAPON_ACQUISITION.get();}
 /** Called only when choosing a backend for a previously unassigned new reward site. */
 public static boolean useLootrForNewPersonalChest(java.util.function.BooleanSupplier compatibleForThisTable) {
  return SPEC.isLoaded() && LOOTR_FOR_NEW_PERSONAL_CHESTS.get()
          && net.neoforged.fml.ModList.get().isLoaded("lootr") && compatibleForThisTable.getAsBoolean();
 }
 public static boolean isWeapon(ItemStack stack){return stack.getItem() instanceof Arsenal.RelicItem||stack.getItem() instanceof CounterArsenal.CounterItem||stack.getItem() instanceof GlitchBow||stack.is(HeartLauncher.LAUNCHER.get())||stack.is(BellHammer.ITEM.get())||RevivalBlossoms.isWeapon(stack);}
 public static boolean allowsWeapon(ItemStack stack){return weaponAcquisitionEnabled()||!isWeapon(stack);}
 public static boolean allowsCraft(ItemStack stack){return allowsWeapon(stack)&&(heartLocketCraftingEnabled()||!stack.is(UwUDimension.LOCKET.get()));}
}
