package dev.uwu.dimension;

import javax.annotation.Nullable;
import net.minecraft.core.*;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.block.*;
import net.neoforged.neoforge.common.SpecialPlantable;

final class NativeFieldSeed extends BlockItem implements SpecialPlantable {
    NativeFieldSeed(Block crop){super(crop,PantryPlants.ingredientProperties());}
    @Override public boolean villagerCanPlantItem(Villager villager){
        return villager instanceof Keeper && ReefLife.isProduceRealm(villager.level());
    }
    @Override public boolean canPlacePlantAtPosition(ItemStack seed,LevelReader world,BlockPos pos,@Nullable Direction side){
        return world instanceof Level level && ReefLife.isProduceRealm(level) && level.isEmptyBlock(pos)
                && level.getBlockState(pos.below()).is(Blocks.FARMLAND) && getBlock().defaultBlockState().canSurvive(level,pos);
    }
    @Override public void spawnPlantAtPosition(ItemStack seed,LevelAccessor world,BlockPos pos,@Nullable Direction side){
        if(canPlacePlantAtPosition(seed,world,pos,side))world.setBlock(pos,getBlock().defaultBlockState(),Block.UPDATE_ALL);
    }
}
