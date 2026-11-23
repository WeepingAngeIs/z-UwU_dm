package dev.uwu.dimension.mixin;

import dev.uwu.dimension.EscortStone;
import dev.uwu.dimension.EscortTerrainSection;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.*;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunkSection.class)
abstract class EscortTerrainWriteMixin implements EscortTerrainSection {
    @Unique @Nullable private volatile LevelChunk uwu$owner;
    @Unique private int uwu$sectionY;
    public void uwu$trackEscortTerrain(@Nullable LevelChunk owner,int baseY){uwu$sectionY=baseY;uwu$owner=owner;}
    @Inject(method="setBlockState(IIILnet/minecraft/world/level/block/state/BlockState;Z)Lnet/minecraft/world/level/block/state/BlockState;",at=@At("RETURN"))
    private void uwu$recordBuiltGround(int x,int y,int z,BlockState state,boolean lock,CallbackInfoReturnable<BlockState> result){
        var chunk=uwu$owner;
        if(chunk==null || result.getReturnValue().getBlock()==state.getBlock())return;
        BlockPos pos=new BlockPos(chunk.getPos().getMinBlockX()+x,uwu$sectionY+y,chunk.getPos().getMinBlockZ()+z);
        EscortStone.changed(chunk,pos,state,result.getReturnValue());
    }
}
