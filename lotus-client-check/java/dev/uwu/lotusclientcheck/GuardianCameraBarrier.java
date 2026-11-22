package dev.uwu.lotusclientcheck;

import net.minecraft.client.Camera;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Exercise the native first-person setup a second time with one solid cell on the carried eye's path. */
final class GuardianCameraBarrier {
    private record Wall(BlockPos pos, int getMinBuildHeight, int getHeight) implements BlockGetter {
        public BlockEntity getBlockEntity(BlockPos at) { return null; }
        public BlockState getBlockState(BlockPos at) { return (pos.equals(at) ? Blocks.STONE : Blocks.AIR).defaultBlockState(); }
        public FluidState getFluidState(BlockPos at) { return Fluids.EMPTY.defaultFluidState(); }
    }

    static boolean checked(LocalPlayer player, float partial, Vec3 openEye) {
        Vec3 start = player.getEyePosition(partial);
        if (start.distanceTo(openEye) < .9) return false;
        BlockPos obstacle = BlockPos.containing(start.lerp(openEye, .8));
        if (obstacle.equals(BlockPos.containing(start))) return false;
        var wall = new Wall(obstacle, player.level().getMinBuildHeight(), player.level().getHeight());
        var hit = wall.clip(new ClipContext(start, openEye, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        if (hit.getType() == HitResult.Type.MISS || start.distanceTo(hit.getLocation()) < .2) return false;

        Camera camera = new Camera();
        camera.setup(player.level(), player, false, false, partial);
        for (int i = 0; i < 40; i++) camera.tick();
        camera.setup(wall, player, false, false, partial);
        Vec3 limited = camera.getPosition();
        if (limited.distanceTo(start) > hit.getLocation().distanceTo(start) - .015
                || obstacle.equals(BlockPos.containing(limited)) || limited.distanceTo(openEye) < .1)
            throw new AssertionError("First-person carried camera crossed the solid test block");
        return true;
    }
    private GuardianCameraBarrier() {}
}
