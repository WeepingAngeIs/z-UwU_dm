package dev.uwu.escortpictures.mixin;

import dev.uwu.escortpictures.EscortGallery;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.uwu.dimension.BlossomWarden;
import net.minecraft.client.renderer.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
abstract class PickDraw {
    @Inject(method="renderItem",at=@At("HEAD"))
    private void verifyDraw(LivingEntity entity, ItemStack item, ItemDisplayContext context,boolean left,PoseStack pose,
            MultiBufferSource buffers,int light,CallbackInfo ci){
        if(entity instanceof BlossomWarden guardian && guardian.getId()==EscortGallery.entityId && guardian.female()
                && BuiltInRegistries.ITEM.getKey(item.getItem()).toString().equals("uwudimension:escort_leaf_pick"))
            EscortGallery.rendered=true;
    }
}
