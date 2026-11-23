package dev.uwu.dimension.mixin;

import dev.uwu.dimension.EscortStone;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class EscortTerrainWriteMixin {
    @Inject(method="setBlockState", at=@At("RETURN"))
    private void uwu$recordBuiltGround(BlockPos pos,BlockState state,boolean moving,CallbackInfoReturnable<BlockState> result) {
        EscortStone.changed((LevelChunk)(Object)this,pos,state,result.getReturnValue());
    }
}
