package dev.uwu.dimension.mixin;

import dev.uwu.dimension.NativeChickens;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Chicken.class)
abstract class ChickenBirthCoatMixin extends Animal implements NativeChickens.BirthCoat {
    @Unique private static final EntityDataAccessor<Byte> uwu$coat =
            SynchedEntityData.defineId(Chicken.class, EntityDataSerializers.BYTE);
    @Unique private static final String uwu$saveKey = "UwUDimensionNativeCoat";

    protected ChickenBirthCoatMixin(EntityType<? extends Animal> type, Level level) {
        super(type, level);
    }

    // Chicken inherits this method from AgeableMob in 1.21.1.
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(uwu$coat, NativeChickens.VANILLA);
    }

    public byte uwu$getCoat() { return getEntityData().get(uwu$coat); }
    public void uwu$setCoat(byte coat) { getEntityData().set(uwu$coat, coat); }

    @Inject(method = "addAdditionalSaveData", at = @At("TAIL"))
    private void uwu$saveCoat(CompoundTag tag, CallbackInfo ci) {
        byte coat = uwu$getCoat();
        if (coat != NativeChickens.VANILLA) tag.putByte(uwu$saveKey, coat);
    }

    @Inject(method = "readAdditionalSaveData", at = @At("TAIL"))
    private void uwu$loadCoat(CompoundTag tag, CallbackInfo ci) {
        byte coat = tag.getByte(uwu$saveKey);
        uwu$setCoat(coat == NativeChickens.PETAL || coat == NativeChickens.DUSK ? coat : NativeChickens.VANILLA);
    }
}
