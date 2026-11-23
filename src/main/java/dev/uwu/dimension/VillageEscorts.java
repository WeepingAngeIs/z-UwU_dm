package dev.uwu.dimension;

import java.util.*;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.*;
import net.minecraft.server.level.*;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** One departure edge per visit, one travelling Guardian per player, including her journey home. */
@EventBusSubscriber(modid="uwudimension")
public final class VillageEscorts extends SavedData {
    static final int MINUTES_MIN=5, MINUTES_MAX=20;
    private static final Factory<VillageEscorts> FACTORY=new Factory<>(VillageEscorts::new,VillageEscorts::load,null);
    private static final TicketType<UUID> TRAVEL=TicketType.create("uwu_escort_travel",UUID::compareTo,100);
    private static final Map<ServerPlayer,Visit> VISITS=new WeakHashMap<>();
    private static final Map<ServerPlayer,BlockPos> LAST_OUTSIDE=new WeakHashMap<>();
    private record Visit(ServerLevel level, BlockPos court, BlockPos previous) {}
    static final class Trip {
        final UUID player,guardian;
        final BlockPos court;
        final long until;
        ChunkPos last;
        int missing;
        Trip(UUID player,UUID guardian,BlockPos court,long until,ChunkPos last) {
            this.player=player;this.guardian=guardian;this.court=court;this.until=until;this.last=last;
        }
    }
    private final Map<UUID,Trip> trips=new HashMap<>();
    static VillageEscorts data(ServerLevel level) { return level.getDataStorage().computeIfAbsent(FACTORY,"uwudimension_village_escorts"); }
    @Nullable Trip trip(UUID owner) {return trips.get(owner);}

    @SubscribeEvent public static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !ReefLife.isSettlementRealm(level) || level.getGameTime()%10!=0) return;
        VillageEscorts book=data(level);
        for (ServerPlayer player:level.players()) book.observe(level,player);
        if(level.getGameTime()%40==0) for (Trip trip:List.copyOf(book.trips.values())) {
            level.getChunkSource().addRegionTicket(TRAVEL,trip.last,2,trip.guardian);
            if(level.getGameTime()>=trip.until) level.getChunkSource().addRegionTicket(TRAVEL,new ChunkPos(trip.court),2,trip.guardian);
            if(level.getEntity(trip.guardian) instanceof BlossomWarden) trip.missing=0;
            else if(level.isPositionEntityTicking(trip.last.getMiddleBlockPosition(trip.court.getY())) && ++trip.missing>=15)
                book.finish(level,trip);
        }
    }
    @SubscribeEvent public static void left(PlayerEvent.PlayerLoggedOutEvent event) { VISITS.remove(event.getEntity());LAST_OUTSIDE.remove(event.getEntity()); }
    @SubscribeEvent public static void changed(PlayerEvent.PlayerChangedDimensionEvent event) { VISITS.remove(event.getEntity());LAST_OUTSIDE.remove(event.getEntity()); }

    static boolean inVillage(ServerLevel level,BlockPos home,BlockPos at,int extra) {
        int dx=at.getX()-home.getX(),dz=at.getZ()-home.getZ();
        int r=GuardianVillageHomes.PRECINCT_RADIUS+extra;
        if(Math.abs(dx)<=r && Math.abs(dz)<=r)return true;
        if(Math.max(Math.abs(dx),Math.abs(dz))>ProfessionQuarter.REACH+extra+8)return false;
        var site=GroveSites.forChunk(level,new ChunkPos(home));
        if(!site.usable())return false;
        for(boolean dusk:new boolean[]{false,true}) {
            var plan=BlossomVillage.plan(level,site,dusk);
            if(plan.isEmpty()||!plan.get().plaza().equals(home))continue;
            var p=plan.get();
            int x=p.x()+switch(site.turns()){case 1->dz;case 2->-dx;case 3->-dz;default->dx;};
            int z=p.z()+switch(site.turns()){case 1->-dx;case 2->-dz;case 3->dx;default->dz;};
            return ProfessionQuarter.nearWork(ProfessionQuarter.plan(level,p),x,z,Math.max(2,4+extra));
        }
        return false;
    }
    void observe(ServerLevel level,ServerPlayer player) {
        if(!player.isAlive()||player.isSpectator()){VISITS.remove(player);LAST_OUTSIDE.remove(player);return;}
        Visit visit=VISITS.get(player); BlockPos at=player.blockPosition();
        if(visit!=null && (visit.level!=level || visit.previous.distSqr(at)>64*64)){VISITS.remove(player);visit=null;}
        if(visit!=null) {
            if(inVillage(level,visit.court,at,8)){VISITS.put(player,new Visit(level,visit.court,at));return;}
            VISITS.remove(player);
            if(!trips.containsKey(player.getUUID()) && player.getRandom().nextInt(4)==0) depart(level,player,visit.court);
            return;
        }
        if(at.equals(LAST_OUTSIDE.put(player,at)) && level.getGameTime()%200!=0)return;
        var home=GuardianVillageHomes.nearest(level,at);
        if(home!=null && inVillage(level,home,at,-4)) {
            LAST_OUTSIDE.remove(player);VISITS.put(player,new Visit(level,home,at));
        }
    }
    private void depart(ServerLevel level,ServerPlayer player,BlockPos home) {
        Boolean dusk=GuardianVillageHomes.duskAt(level,home);
        if(dusk==null)return;
        BlossomWarden guard=People.BLOSSOM_WARDEN.get().create(level);
        if(guard==null)return;
        guard.setFemale(true);guard.setDuskGuardian(dusk);
        Vec3 inward=Vec3.atBottomCenterOf(home).subtract(player.position()).multiply(1,0,1).normalize();
        BlockPos gate=BlockPos.containing(player.position().add(inward.scale(8)));
        BlockPos feet=EscortRoute.nearGround(level,guard,gate,5);
        if(feet==null)feet=EscortRoute.nearGround(level,guard,home,4);
        if(feet==null)return;
        guard.moveTo(feet.getX()+.5,feet.getY(),feet.getZ()+.5,player.getYRot(),0);
        guard.escortOwner=player.getUUID();guard.bindVillage(home);guard.setPersistenceRequired();
        long duration=(MINUTES_MIN+player.getRandom().nextInt(MINUTES_MAX-MINUTES_MIN+1))*1200L;
        Trip trip=new Trip(player.getUUID(),guard.getUUID(),home,level.getGameTime()+duration,guard.chunkPosition());
        if(!level.addFreshEntity(guard))return;
        trips.put(trip.player,trip);setDirty();
    }
    void moved(ServerLevel level,Trip trip,ChunkPos where) {
        if(!where.equals(trip.last)){
            level.getChunkSource().removeRegionTicket(TRAVEL,trip.last,2,trip.guardian);
            trip.last=where;setDirty();
        }
    }
    void finish(ServerLevel level,Trip trip) {
        if(!trips.remove(trip.player,trip))return;
        level.getChunkSource().removeRegionTicket(TRAVEL,trip.last,2,trip.guardian);
        level.getChunkSource().removeRegionTicket(TRAVEL,new ChunkPos(trip.court),2,trip.guardian);
        setDirty();
    }
    private static VillageEscorts load(CompoundTag tag,HolderLookup.Provider lookup) {
        VillageEscorts book=new VillageEscorts();
        for(Tag entry:tag.getList("Trips",Tag.TAG_COMPOUND)){
            var n=(CompoundTag)entry;
            var t=new Trip(n.getUUID("Player"),n.getUUID("Guardian"),BlockPos.of(n.getLong("Court")),n.getLong("Until"),new ChunkPos(n.getLong("Chunk")));
            book.trips.put(t.player,t);
        }
        return book;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        ListTag entries=new ListTag();
        for(Trip t:trips.values()){
            CompoundTag n=new CompoundTag();n.putUUID("Player",t.player);n.putUUID("Guardian",t.guardian);
            n.putLong("Court",t.court.asLong());n.putLong("Until",t.until);n.putLong("Chunk",t.last.toLong());entries.add(n);
        }
        tag.put("Trips",entries);return tag;
    }
}
