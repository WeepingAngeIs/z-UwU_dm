package dev.uwu.pocketcheck.mixin;
import dev.uwu.pocketcheck.PocketGallery;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
abstract class GalleryCamera {
    @Shadow protected abstract void setPosition(double x,double y,double z);
    @Shadow protected abstract void setRotation(float yaw,float pitch);
    @Inject(method="setup",at=@At("TAIL"))
    private void pocketCheck$camera(BlockGetter level,Entity entity,boolean detached,boolean mirrored,float partial,CallbackInfo ci){
        var v=PocketGallery.view();
        if(v!=null){setPosition(v.eye().x,v.eye().y,v.eye().z);setRotation(v.yaw(),v.pitch());}
    }
}
