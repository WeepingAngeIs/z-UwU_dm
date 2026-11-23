package dev.uwu.dimension;

import com.mojang.serialization.Codec;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.*;
import net.neoforged.neoforge.registries.*;

/** Positive provenance. Old chunks have no certificate. Any subsequent block replacement retires it. */
public final class EscortStone {
    private static final DeferredRegister<AttachmentType<?>> TYPES=DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES,"uwudimension");
    private static final Supplier<AttachmentType<Boolean>> PRISTINE=TYPES.register("escort_untouched_terrain",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL,Boolean::booleanValue).build());
    private static final Set<Block> ROCK=Set.of(Blocks.STONE,Blocks.DEEPSLATE,Blocks.ANDESITE,Blocks.DIORITE,Blocks.GRANITE,Blocks.TUFF);
    private static final com.mojang.authlib.GameProfile MINER=new com.mojang.authlib.GameProfile(
            java.util.UUID.fromString("0474afe7-c3f7-4714-97fb-7812c11b6d5e"),"[Leafsprig]");
    private record Cut(ServerLevel level,BlockPos pos) {}
    private static final ThreadLocal<Cut> CUT=new ThreadLocal<>();
    static void register(IEventBus bus) {
        TYPES.register(bus);
        NeoForge.EVENT_BUS.addListener(EscortStone::newChunk);
        NeoForge.EVENT_BUS.addListener(EscortStone::placed);
    }
    private static void newChunk(ChunkEvent.Load event) {
        if(!(event.getChunk() instanceof LevelChunk chunk) || !(event.getLevel() instanceof ServerLevel level)
                || !ReefLife.isSettlementRealm(level))return;
        if(event.isNewChunk()){chunk.setData(PRISTINE,true);chunk.setUnsaved(true);}
        if(chunk.hasData(PRISTINE) && chunk.getData(PRISTINE))track(chunk,true);
    }
    private static void placed(BlockEvent.EntityPlaceEvent event) {
        if(event.getLevel() instanceof ServerLevel level && ReefLife.isSettlementRealm(level)) {
            var at=event.getPos();var chunk=level.getChunkSource().getChunkNow(at.getX()>>4,at.getZ()>>4);
            if(chunk!=null)retire(chunk);
        }
    }
    private static void track(LevelChunk chunk,boolean enabled) {
        var sections=chunk.getSections();
        for(int i=0;i<sections.length;i++)
            ((EscortTerrainSection)(Object)sections[i]).uwu$trackEscortTerrain(enabled?chunk:null,chunk.getSectionYFromSectionIndex(i)<<4);
    }
    private static void retire(LevelChunk chunk) {
        if(chunk.hasData(PRISTINE)){chunk.removeData(PRISTINE);track(chunk,false);chunk.setUnsaved(true);}
    }
    public static void changed(LevelChunk chunk,BlockPos pos,BlockState state,BlockState previous) {
        if(previous==null||state==previous||previous.getBlock()==state.getBlock()||!chunk.hasData(PRISTINE))return;
        Cut exact=CUT.get();
        if(exact!=null && exact.level==chunk.getLevel() && exact.pos.equals(pos) && state.isAir()){CUT.remove();return;}
        retire(chunk);
    }
    static boolean allowed(ServerLevel level,BlossomWarden guard,ServerPlayer owner,BlockPos pos) {
        if(guard.escortOwner==null || !guard.escortOwner.equals(owner.getUUID()) || !guard.female()
                || !ReefLife.isSettlementRealm(level) || !level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)
                || !level.getWorldBorder().isWithinBounds(pos) || !level.hasChunkAt(pos))return false;
        var trip=VillageEscorts.data(level).trip(owner.getUUID());
        if(trip==null || !trip.guardian.equals(guard.getUUID()) || level.getGameTime()>=trip.until || owner.serverLevel()!=level)return false;
        var state=level.getBlockState(pos);
        if(!ROCK.contains(state.getBlock()) || state.hasBlockEntity() || !state.getFluidState().isEmpty())return false;
        // A roof or a wall built just across a chunk border protects the adjacent rock too.
        for(int x=(pos.getX()-4)>>4;x<=(pos.getX()+4)>>4;x++)for(int z=(pos.getZ()-4)>>4;z<=(pos.getZ()+4)>>4;z++){
            var chunk=level.getChunkSource().getChunkNow(x,z);
            if(chunk==null || !chunk.hasData(PRISTINE) || !chunk.getData(PRISTINE) || !chunk.getAllReferences().isEmpty())return false;
        }
        if(AuthoredFootprints.check(level,new BoundingBox(pos).inflatedBy(3))!=AuthoredFootprints.Decision.CLEAR)return false;
        var source=level.getChunkSource();
        if(source.getGenerator().getBaseColumn(pos.getX(),pos.getZ(),level,source.randomState()).getBlock(pos.getY())!=state)return false;
        return LauncherExcavationRules.unclaimed(level,pos) && SakuraAccess.mayInteract(owner,pos) &&
                EventHooks.canEntityGrief(level,guard) && state.canEntityDestroy(level,pos,guard);
    }
    static boolean cut(ServerLevel level,BlossomWarden guard,ServerPlayer owner,BlockPos pos) {
        if(!allowed(level,guard,owner,pos))return false;
        var before=level.getBlockState(pos);
        // Give claim/protection listeners a separate, unprivileged actor. Player tools, area-mining
        // effects, XP and loot must never fire on behalf of the visitor holding them.
        var worker=net.neoforged.neoforge.common.util.FakePlayerFactory.get(level,MINER);
        worker.setPos(guard.position());
        worker.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,net.minecraft.world.item.ItemStack.EMPTY);
        if(!SakuraAccess.mayInteract(worker,pos) || !EventHooks.onEntityDestroyBlock(guard,pos,before)
                || NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level,pos,before,worker)).isCanceled()
                || !allowed(level,guard,owner,pos) || level.getBlockState(pos)!=before)return false;
        CUT.set(new Cut(level,pos.immutable()));
        try{return level.destroyBlock(pos,false,guard);}finally{CUT.remove();}
    }
    private EscortStone() {}
}
