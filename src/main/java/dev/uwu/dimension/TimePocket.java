package dev.uwu.dimension;

import com.mojang.brigadier.arguments.BoolArgumentType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.commands.Commands;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TimePocket {
    public static final int COOLDOWN = 240;
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("uwudimension");
    private static final DeferredRegister<EntityType<?>> TYPES = DeferredRegister.create(Registries.ENTITY_TYPE,"uwudimension");
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT,"uwudimension");
    public static final Supplier<Item> CLOCK = ITEMS.register("pocket_clock_prototype", Clock::new);
    public static final Supplier<Item> STAR = ITEMS.register("clock_star", () -> new Item(new Item.Properties()));
    public static final Supplier<EntityType<Star>> SHOT = TYPES.register("clock_star", () ->
            EntityType.Builder.<Star>of(Star::new,MobCategory.MISC).sized(.52F,.52F)
                    .clientTrackingRange(8).updateInterval(1).build("uwudimension:clock_star"));
    public static final Supplier<SoundEvent> TICK = sound("pocket_tick"), TOCK = sound("pocket_tock");
    private static Supplier<SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(UwUDimension.id(name)));
    }
    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.BooleanValue NEGATIVE;
    static {
        var settings = new ModConfigSpec.Builder();
        NEGATIVE = settings.comment("Invert colors on visible surfaces inside active time pockets. Audio and gameplay are independent.")
                .define("negativeColors",true);
        SPEC=settings.build();
    }

    public record Field(UUID caster, int x, int z, long until, boolean negative) {
        public boolean contains(int chunkX,int chunkZ) {
            return Math.abs((long)chunkX-x)+Math.abs((long)chunkZ-z)<=1;
        }
        public boolean at(double worldX,double worldZ) {
            return contains(net.minecraft.util.Mth.floor(worldX) >> 4,net.minecraft.util.Mth.floor(worldZ) >> 4);
        }
    }

    // An integrated server and its client tick on different threads. Never share a field map between sides.
    private static final Map<ServerLevel,Field> SERVER = new WeakHashMap<>();
    private static final Map<ServerPlayer,Long> LAST_SHOT = new WeakHashMap<>();
    private static final long NO_FIELD = -1;
    private static final UUID EMPTY = new UUID(0,0);

    public record State(ResourceLocation dimension,Field field) implements CustomPacketPayload {
        public static final Type<State> TYPE=new Type<>(UwUDimension.id("time_pocket"));
        static final StreamCodec<RegistryFriendlyByteBuf,State> CODEC=StreamCodec.of((b,s)->{
            b.writeResourceLocation(s.dimension);b.writeUUID(s.field.caster);b.writeInt(s.field.x);b.writeInt(s.field.z);
            b.writeLong(s.field.until);b.writeBoolean(s.field.negative);
        },b->new State(b.readResourceLocation(),new Field(b.readUUID(),b.readInt(),b.readInt(),b.readLong(),b.readBoolean())));
        @Override public Type<State> type(){return TYPE;}
    }
    public record Fire() implements CustomPacketPayload {
        public static final Type<Fire> TYPE=new Type<>(UwUDimension.id("send_clock_star"));
        static final StreamCodec<RegistryFriendlyByteBuf,Fire> CODEC=StreamCodec.unit(new Fire());
        @Override public Type<Fire> type(){return TYPE;}
    }

    static void register(IEventBus bus, ModContainer container) {
        ITEMS.register(bus); TYPES.register(bus); SOUNDS.register(bus);
        container.registerConfig(ModConfig.Type.SERVER,SPEC,"uwudimension-timestop.toml");
        bus.addListener((BuildCreativeModeTabContentsEvent e)->{if(e.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES)e.accept(CLOCK.get());});
        bus.addListener((RegisterPayloadHandlersEvent e)->{
            var r=e.registrar("1");
            r.playToServer(Fire.TYPE,Fire.CODEC,(msg,ctx)->{if(ctx.player() instanceof ServerPlayer p)fire(p);});
            r.playToClient(State.TYPE,State.CODEC,(msg,ctx)->dev.uwu.dimension.client.TimePocketClient.receive(msg));
        });
        NeoForge.EVENT_BUS.addListener(TimePocket::entityTick);
        NeoForge.EVENT_BUS.addListener(TimePocket::levelTick);
        NeoForge.EVENT_BUS.addListener(TimePocket::registerCommands);
        NeoForge.EVENT_BUS.addListener((LevelEvent.Unload e)->{if(e.getLevel() instanceof ServerLevel s)SERVER.remove(s);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedInEvent e)->{if(e.getEntity() instanceof ServerPlayer p)syncVisitor(p);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerChangedDimensionEvent e)->{if(e.getEntity() instanceof ServerPlayer p)syncVisitor(p);});
        NeoForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent e)->{
            if(e.getEntity() instanceof ServerPlayer p) { LAST_SHOT.remove(p); cancelIfOwner(p.serverLevel(),p.getUUID()); }
        });
    }

    private static void registerCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("uwuclock")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("negativeColors")
                        .then(Commands.argument("enabled", BoolArgumentType.bool()).executes(context -> {
                            boolean enabled = BoolArgumentType.getBool(context, "enabled");
                            NEGATIVE.set(enabled);
                            NEGATIVE.save();
                            context.getSource().sendSuccess(() -> Component.literal(
                                    "Time pocket color inversion " + (enabled ? "enabled" : "disabled")), true);
                            return 1;
                        }))));
    }

    public static Field field(Level level) {
        if(level instanceof ServerLevel server)return SERVER.get(server);
        return dev.uwu.dimension.client.TimePocketClient.field(level);
    }

    private static void syncVisitor(ServerPlayer p) {
        Field field=SERVER.get(p.serverLevel());
        PacketDistributor.sendToPlayer(p, state(p.serverLevel(), field));
    }

    private static State state(ServerLevel level, Field field) {
        return new State(level.dimension().location(), field == null ? new Field(EMPTY,0,0,NO_FIELD,false) : field);
    }

    private static void publish(ServerLevel s,Field field) {
        if(field==null) SERVER.remove(s); else SERVER.put(s,field);
        var message=state(s,field);
        for(var p:s.players()) PacketDistributor.sendToPlayer(p,message);
    }

    private static void cancelIfOwner(ServerLevel s,UUID id) {
        Field f=SERVER.get(s);
        if(f!=null && f.caster.equals(id))publish(s,null);
    }

    private static void levelTick(LevelTickEvent.Pre e) {
        if(!(e.getLevel() instanceof ServerLevel s))return;
        Field f=SERVER.get(s);
        if(f==null)return;
        var owner=s.getServer().getPlayerList().getPlayer(f.caster);
        if(s.getGameTime()>=f.until || owner==null || !owner.isAlive() || owner.serverLevel()!=s || owner.isSpectator()) {
            publish(s,null);
        } else if(f.negative!=NEGATIVE.get()) {
            publish(s,new Field(f.caster,f.x,f.z,f.until,NEGATIVE.get()));
        }
    }

    private static boolean stopped(Entity entity,Field field) {
        if(field==null || !field.at(entity.getX(),entity.getZ()) || entity instanceof Player
                || entity.isPassenger() || entity.getControllingPassenger() instanceof Player)return false;
        if(entity instanceof Star star && star.getOwner()!=null && field.caster.equals(star.getOwner().getUUID()))return false;
        return entity instanceof Mob || entity instanceof FallingBlockEntity || entity instanceof ItemEntity
                || entity instanceof PrimedTnt || entity instanceof Projectile || entity instanceof ExperienceOrb;
    }

    public static boolean suspended(Entity entity) {
        return stopped(entity, field(entity.level()));
    }

    private static void entityTick(EntityTickEvent.Pre e) {
        var entity=e.getEntity();
        if(!suspended(entity))return;
        // 1.21.1 has advanced the counter immediately before firing this event on both sides.
        entity.tickCount--;
        e.setCanceled(true);
    }

    public static final class Clock extends Item {
        Clock(){super(new Item.Properties().stacksTo(1));}
        @Override public InteractionResultHolder<ItemStack> use(Level world, Player p, InteractionHand hand) {
            ItemStack held=p.getItemInHand(hand);
            if(hand!=InteractionHand.MAIN_HAND || p.isSpectator())return InteractionResultHolder.pass(held);
            if(world instanceof ServerLevel s && p instanceof ServerPlayer owner) {
                if(SERVER.containsKey(s) || p.getCooldowns().isOnCooldown(this))return InteractionResultHolder.fail(held);
                ChunkPos c=p.chunkPosition();
                long deadline = s.getGameTime() + 20L * AcquisitionPolicy.uwuziFreezeSeconds();
                publish(s,new Field(owner.getUUID(),c.x,c.z,deadline,NEGATIVE.get()));
                p.getCooldowns().addCooldown(this,COOLDOWN);
            }
            return InteractionResultHolder.sidedSuccess(held,world.isClientSide);
        }
        @Override public boolean canAttackBlock(BlockState s,Level level,BlockPos pos,Player p){return false;}
        @Override public boolean onLeftClickEntity(ItemStack stack,Player player,Entity entity){return true;}
        @Override public void appendHoverText(ItemStack stack,TooltipContext c,List<Component> lines,TooltipFlag flags) {
            lines.add(Component.translatable("item.uwudimension.pocket_clock_prototype.hint"));
        }
    }

    private static void fire(ServerPlayer p) {
        if(!p.isAlive() || p.isSpectator() || !p.getMainHandItem().is(CLOCK.get()))return;
        var level=p.serverLevel();
        long now=level.getServer().overworld().getGameTime();
        if(now-LAST_SHOT.getOrDefault(p,-100L)<8)return;
        LAST_SHOT.put(p,now);
        Star star=SHOT.get().create(level);
        if(star==null)return;
        star.setOwner(p);star.setPos(p.getEyePosition().add(p.getLookAngle().scale(.75)));
        star.setDeltaMovement(p.getLookAngle().normalize().scale(Star.NORMAL_SPEED));
        level.addFreshEntity(star);
    }

    public static final class Star extends ThrowableItemProjectile {
        static final double NORMAL_SPEED=1.25, SLOW_SPEED=.12;
        Star(EntityType<? extends Star> t,Level level){super(t,level);setNoGravity(true);}
        @Override protected Item getDefaultItem(){return STAR.get();}
        @Override public void tick() {
            var f=field(level()); var owner=getOwner();
            boolean slow=f!=null && owner!=null && f.caster.equals(owner.getUUID()) && f.at(getX(),getZ());
            var velocity=getDeltaMovement().normalize().scale(slow?SLOW_SPEED:NORMAL_SPEED);
            if(level() instanceof ServerLevel s && (tickCount>100 || !s.hasChunkAt(BlockPos.containing(position().add(velocity))))) {discard();return;}
            setDeltaMovement(velocity); super.tick();
        }
        @Override protected void onHitEntity(EntityHitResult hit){if(!level().isClientSide)discard();}
        @Override protected void onHitBlock(BlockHitResult hit) {
            if(level() instanceof ServerLevel s) {
                s.sendParticles(ParticleTypes.END_ROD,hit.getLocation().x,hit.getLocation().y,hit.getLocation().z,5,.12,.12,.12,.015);
                // Disposable practice targets observe the standard projectile-impact event in the test mod.
                discard();
            }
        }
    }
    private TimePocket() {}
}
