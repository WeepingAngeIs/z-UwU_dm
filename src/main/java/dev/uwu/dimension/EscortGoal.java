package dev.uwu.dimension;

import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;

final class EscortGoal extends Goal {
    private final BlossomWarden guard;
    private long planAt,blockedSince;
    private int strike;
    private @Nullable BlockPos mining,firstColumn,exit,ledge,passageTarget;
    private @Nullable Direction wall;
    private Vec3 progress=Vec3.ZERO;
    EscortGoal(BlossomWarden guard){this.guard=guard;setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
    @Override public boolean canUse(){return guard.escortOwner!=null && !guard.hasOffenders();}
    @Override public boolean canContinueToUse(){return canUse();}
    @Override public boolean requiresUpdateEveryTick(){return true;}
    @Override public void start(){blockedSince=0;planAt=0;progress=guard.position();}
    @Override public void stop(){
        resetTools();guard.getNavigation().stop();guard.setSprinting(false);
    }
    private void resetTools(){mining=firstColumn=exit=ledge=passageTarget=null;wall=null;strike=0;guard.showEscortPick(false);}
    @Override public void tick(){
        var level=(ServerLevel)guard.level();var book=VillageEscorts.data(level);var trip=book.trip(guard.escortOwner);
        if(trip==null || !trip.guardian.equals(guard.getUUID())){guard.discard();return;}
        if(trip.until==0){guard.getNavigation().stop();return;}
        long now=level.getGameTime();book.moved(level,trip,guard.chunkPosition());
        ServerPlayer player=level.getServer().getPlayerList().getPlayer(trip.player);
        boolean returning=now>=trip.until;
        if(!returning && (player==null || !player.isAlive() || player.serverLevel()!=level || player.isSpectator())) {
            resetTools();guard.getNavigation().stop();return;
        }
        if(returning){
            resetTools();
            if(guard.distanceToSqr(Vec3.atBottomCenterOf(trip.court))<=16){book.finish(level,trip);guard.discard();return;}
        }else {
            if(mining!=null){strike(level,player);return;}
            if(ledge!=null){climb(level);return;}
        }
        if(now<planAt)return;planAt=now+20;
        BlockPos target=returning?trip.court:player.blockPosition();
        if(!returning && guard.distanceToSqr(player)<16 && Math.abs(guard.getY()-player.getY())<=1.5
                && guard.hasLineOfSight(player)){
            guard.getNavigation().stop();blockedSince=0;guard.setSprinting(false);return;
        }
        var goal=target;
        if(guard.blockPosition().distSqr(goal)>38*38) {
            Vec3 dir=Vec3.atBottomCenterOf(goal).subtract(guard.position()).normalize();
            goal=BlockPos.containing(guard.position().add(dir.scale(27)));
        }
        var nav=guard.getNavigation();var route=nav.createPath(goal,returning?2:1);
        boolean moved=progress.distanceToSqr(guard.position())>1;
        progress=guard.position();
        if(route!=null && route.getNodeCount()>1 && (moved || blockedSince==0 || now-blockedSince<65)){
            nav.moveTo(route,returning?1.25:1.4);guard.setSprinting(!returning);
            if(route.canReach() && moved){blockedSince=0;return;}
        }
        if(blockedSince==0){blockedSince=now;return;}
        if(moved){blockedSince=now;return;}
        if(now-blockedSince<60)return;
        nav.stop();guard.setSprinting(false); planAt=now+40;
        if(returning){
            // Travel resumes on foot whenever either endpoint can be observed.
            if(EscortRoute.concealed(level,guard,guard.blockPosition())) {
                BlockPos home=EscortRoute.nearGround(level,guard,trip.court,3);
                if(home!=null && EscortRoute.concealed(level,guard,home))guard.teleportTo(home.getX()+.5,home.getY(),home.getZ()+.5);
            }
            return;
        }
        if(EscortRoute.hiddenCatchUp(level,guard,player)){blockedSince=0;return;}
        findPassage(level,player);
    }
    private void findPassage(ServerLevel level,ServerPlayer player) {
        BlockPos from=guard.blockPosition();
        var probe=People.BLOSSOM_WARDEN.get().create(level);if(probe==null)return;probe.setFemale(true);
        for(Direction dir:EscortRoute.toward(from,player.blockPosition())){
            BlockPos front=from.relative(dir);
            if(!level.hasChunkAt(front)||level.getBlockState(front).getCollisionShape(level,front).isEmpty())continue;
            for(int rise=2;rise<=4;rise++) {
                BlockPos top=front.above(rise);
                if(!EscortRoute.safe(level,guard,top) || !level.noCollision(guard,guard.getBoundingBox().expandTowards(0,rise+.2,0))
                        || !EscortRoute.walkFrom(level,probe,top,player.blockPosition()))continue;
                ledge=top;wall=dir;return;
            }
        }
        for(Direction dir:EscortRoute.toward(from,player.blockPosition())){
            BlockPos first=null,front=from.relative(dir);
            for(int depth=1;depth<=3;depth++){
                BlockPos column=from.relative(dir,depth);
                if(!level.hasChunkAt(column) || !level.getBlockState(column.below()).isFaceSturdy(level,column.below(),Direction.UP))break;
                boolean permitted=true;
                for(int dy=1;dy>=0;dy--){
                    BlockPos p=column.above(dy);var state=level.getBlockState(p);
                    if(!state.getFluidState().isEmpty()){permitted=false;break;}
                    if(state.getCollisionShape(level,p).isEmpty())continue;
                    if(!EscortStone.allowed(level,guard,player,p)){permitted=false;break;}
                    if(depth==1 && first==null)first=p;
                }
                if(!permitted || first==null)break;
                BlockPos end=column.relative(dir);
                if(!EscortRoute.safe(level,guard,end) || !EscortRoute.walkFrom(level,probe,end,player.blockPosition()))continue;
                // Complete at most the first two-block doorway from this stance, then walk and reassess.
                mining=first;firstColumn=front;exit=end;wall=dir;passageTarget=player.blockPosition();
                strike=0;guard.showEscortPick(true);return;
            }
        }
    }
    private void climb(ServerLevel level){
        if(!EscortRoute.safe(level,guard,ledge) || guard.blockPosition().distSqr(ledge)>27){resetTools();return;}
        Vec3 target=Vec3.atBottomCenterOf(ledge);Vec3 difference=target.subtract(guard.position());
        if(difference.y<=.08){
            guard.getMoveControl().setWantedPosition(target.x,target.y,target.z,1.1);
            guard.setDeltaMovement(difference.multiply(.28,0,.28).add(0,.06,0));
            if(difference.horizontalDistanceSqr()<.2){resetTools();blockedSince=0;planAt=0;}
        }else {
            var contact=guard.blockPosition().relative(wall);
            if(level.getBlockState(contact).getCollisionShape(level,contact).isEmpty()
                    && level.getBlockState(contact.below()).getCollisionShape(level,contact.below()).isEmpty()){resetTools();return;}
            guard.setDeltaMovement(wall.getStepX()*.09,.24,wall.getStepZ()*.09);guard.fallDistance=0;
        }
    }
    private void strike(ServerLevel level,ServerPlayer player){
        if(player.blockPosition().distSqr(passageTarget)>9 || guard.blockPosition().distSqr(mining)>8 || !EscortRoute.safe(level,guard,exit)
                || !EscortStone.allowed(level,guard,player,mining)){resetTools();return;}
        guard.getLookControl().setLookAt(Vec3.atCenterOf(mining));
        if(++strike%8!=0)return;
        var open=guard.getNavigation().createPath(player,1);
        if(open!=null && open.canReach()){resetTools();planAt=0;return;}
        var face=Vec3.atCenterOf(mining).add(-.52*wall.getStepX(),.15,-.52*wall.getStepZ());
        level.sendParticles(ParticleTypes.END_ROD,face.x,face.y,face.z,2,.04,.04,.04,.005);
        level.playSound(null,mining,SoundType.STONE.getHitSound(),net.minecraft.sounds.SoundSource.NEUTRAL,.38F,1.25F);
        guard.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        if(strike<24)return;
        if(!EscortStone.cut(level,guard,player,mining)){resetTools();return;}
        var foot=firstColumn;
        if(!foot.equals(mining) && !level.getBlockState(foot).getCollisionShape(level,foot).isEmpty()){
            mining=foot;strike=0;return;
        }
        resetTools();blockedSince=0;planAt=0;
    }
}
