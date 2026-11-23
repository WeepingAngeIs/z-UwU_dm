package dev.uwu.dimension;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.*;

/** Checks the whole female hitbox and an actual exit route before appearing behind opaque cover. */
final class EscortRoute {
    static boolean safe(ServerLevel level,BlossomWarden guard,BlockPos pos) {
        if(!level.hasChunkAt(pos) || !level.isInWorldBounds(pos.above()) || !level.getWorldBorder().isWithinBounds(pos))return false;
        var floor=level.getBlockState(pos.below());
        if(!floor.isFaceSturdy(level,pos.below(),Direction.UP) || hazardous(floor))return false;
        var box=guard.getDimensions(guard.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(pos));
        if(!level.noCollision(guard,box) || level.containsAnyLiquid(box))return false;
        for(int y=0;y<2;y++)if(hazardous(level.getBlockState(pos.above(y))))return false;
        return true;
    }
    private static boolean hazardous(net.minecraft.world.level.block.state.BlockState s) {
        return s.is(BlockTags.FIRE)||s.is(Blocks.MAGMA_BLOCK)||s.is(Blocks.CACTUS)||s.is(Blocks.POWDER_SNOW)
                ||s.is(Blocks.SWEET_BERRY_BUSH)||s.is(Blocks.WITHER_ROSE)||s.is(Blocks.CAMPFIRE)||s.is(Blocks.SOUL_CAMPFIRE)
                ||s.is(Blocks.POINTED_DRIPSTONE)||s.is(Blocks.NETHER_PORTAL)||s.is(Blocks.END_PORTAL);
    }
    @Nullable static BlockPos nearGround(ServerLevel level,BlossomWarden guard,BlockPos origin,int radius) {
        for(BlockPos p:BlockPos.withinManhattan(origin,radius,3,radius))
            if(safe(level,guard,p))return p.immutable();
        return null;
    }
    static boolean concealed(ServerLevel level,BlossomWarden guard,BlockPos at) {
        Vec3 base=Vec3.atBottomCenterOf(at);
        for(ServerPlayer watcher:level.players()) {
            if(watcher.distanceToSqr(base)>128*128)continue;
            Vec3 eye=watcher.getEyePosition();
            // Leaves, glass, open doors and merely facing away do not establish opaque cover.
            for(double dx:new double[]{-.33,0,.33})for(double dy:new double[]{.04,1.0,1.99})
                for(double dz:new double[]{-.33,0,.33}) {
                    Vec3 point=base.add(dx,dy,dz);
                    boolean blocked=BlockGetter.traverseBlocks(eye,point,level,(world,p)->{
                        if(!world.hasChunkAt(p))return Boolean.FALSE;
                        var s=world.getBlockState(p);
                        return s.isSolidRender(world,p) && s.canOcclude() ? Boolean.TRUE : null;
                    },ignored->Boolean.FALSE);
                    if(!blocked)return false;
                }
        }
        return true;
    }
    static boolean walkFrom(ServerLevel level,BlossomWarden probe,BlockPos from,BlockPos target) {
        probe.moveTo(from.getX()+.5,from.getY(),from.getZ()+.5,0,0);
        var path=probe.getNavigation().createPath(target,2);
        return path!=null && path.canReach();
    }
    static boolean hiddenCatchUp(ServerLevel level,BlossomWarden guard,ServerPlayer player) {
        var dst=player.blockPosition();
        var probe=People.BLOSSOM_WARDEN.get().create(level);
        if(probe==null)return false;
        probe.setFemale(true);
        int turns=guard.getRandom().nextInt(16), paths=0;
        for(int r:new int[]{7,11,15})for(int n=0;n<16;n++){
            double a=(turns+n)*Math.PI/8;
            BlockPos around=dst.offset((int)Math.round(r*Math.cos(a)),0,(int)Math.round(r*Math.sin(a)));
            for(int y:new int[]{0,1,-1,2,-2,3,-3}){
                BlockPos p=around.above(y);
                if(!safe(level,guard,p)||!concealed(level,guard,p))continue;
                if(++paths>8)return false;
                if(!walkFrom(level,probe,p,dst))continue;
                guard.getNavigation().stop();guard.teleportTo(p.getX()+.5,p.getY(),p.getZ()+.5);
                guard.setDeltaMovement(Vec3.ZERO);
                guard.getNavigation().moveTo(player,1.15);
                return true;
            }
        }
        return false;
    }
    static List<Direction> toward(BlockPos from,BlockPos to) {
        int x=to.getX()-from.getX(),z=to.getZ()-from.getZ();
        var a=x>=0?Direction.EAST:Direction.WEST;var b=z>=0?Direction.SOUTH:Direction.NORTH;
        if(x==0 && z==0)return List.of();
        if(x==0)return List.of(b);if(z==0)return List.of(a);
        return Math.abs(x)>=Math.abs(z)?List.of(a,b):List.of(b,a);
    }
    private EscortRoute(){}
}
