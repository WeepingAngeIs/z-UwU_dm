package dev.uwu.dimension;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;

/** Seven-block-wide, open-front workshops. Coordinates inside each workshop are relative to its front step. */
final class ProfessionPavilion {
    private final HeartwaterFeature.Valley valley;
    private final ProfessionQuarter.Plot plot;
    private final boolean dusk;
    private final ReachArchitecture.Wood wood;
    private ProfessionPavilion(HeartwaterFeature.Valley v, BlossomVillage.Plan village, ProfessionQuarter.Plot plot) {
        this.valley=v; this.plot=plot; dusk=village.dusk(); wood=ReachArchitecture.wood(dusk);
    }
    static void build(HeartwaterFeature.Valley v, BlossomVillage.Plan village, ProfessionQuarter.Plot plot) {
        new ProfessionPavilion(v,village,plot).build();
    }
    private ProfessionQuarter.Tile tile(int across,int front) {
        return plot.center().step(plot.front(),front).step(plot.front().getClockWise(),across);
    }
    private void put(int across,int y,int front,BlockState state) {
        var at=tile(across,front); valley.put(at.x(),plot.floor()+y,at.z(),state);
    }
    private void set(int across,int y,int front,Block block) { put(across,y,front,block.defaultBlockState()); }
    private void build() {
        for(int a=-3;a<=3;a++) for(int b=-3;b<=3;b++) {
            var t=tile(a,b);
            if (!valley.inside(t.x(),t.z())) continue;
            BlossomVillage.clear(valley,t.x(),plot.floor()+1,t.z());
            Block floor=Math.abs(a)==3 || Math.abs(b)==3 ? ReachArchitecture.paving(dusk) : wood.boards().get();
            BlossomVillage.floor(valley,t.x(),plot.floor(),t.z(),floor);
        }
        if(plot.job()==ProfessionQuarter.Job.FARMER) { field(); return; }
        boolean forge=switch(plot.job()){case ARMORER, TOOLSMITH, WEAPONSMITH, MASON -> true; default -> false;};
        for(int a:new int[]{-3,3}) for(int b:new int[]{-2,2}) {
            for(int y=1;y<=3;y++) set(a,y,b,wood.beam().get());
        }
        for(int a=-3;a<=3;a++) for(int b=-3;b<=3;b++) {
            int y=4+(Math.abs(a)<2?1:0);
            Block roof=forge ? (dusk?ReachArchitecture.ROOF_SLAB.get():PinkRoofTiles.slab(false))
                    : (Math.abs(a)%2==1 ? wood.slab().get() : PinkRoofTiles.slab(dusk));
            set(a,y,b,roof);
        }
        set(0,1,-2,plot.job().station);
        // Workstations can be reached from the footpath through the three-wide open front.
        switch(plot.job()) {
            case LIBRARIAN -> {
                for(int b=-2;b<=1;b++) for(int y=1;y<=2;y++) set(-2,y,b,Blocks.BOOKSHELF);
                set(2,1,-2,Blocks.BOOKSHELF); set(2,2,-2,Blocks.LANTERN);
            }
            case TOOLSMITH -> { set(-2,1,-1,Blocks.ANVIL); set(2,1,-2,Blocks.COPPER_BLOCK); set(2,2,-2,Blocks.LIGHTNING_ROD); }
            case ARMORER -> { set(-2,1,-2,Blocks.POLISHED_BLACKSTONE); set(-2,2,-2,Blocks.CHAIN); set(2,1,-2,Blocks.IRON_BARS); }
            case WEAPONSMITH -> { set(-2,1,-1,Blocks.SMOOTH_STONE); set(-2,2,-1,Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE); set(2,1,-2,Blocks.POLISHED_BASALT); }
            case BUTCHER -> { set(-2,1,-2,Blocks.BRICKS); set(-2,2,-2,Blocks.FLOWER_POT); set(2,1,-2,Blocks.HAY_BLOCK); }
            case FISHERMAN -> { put(-2,0,0,Blocks.WATER.defaultBlockState()); put(-2,0,-1,Blocks.WATER.defaultBlockState()); set(2,1,-2,Blocks.DRIED_KELP_BLOCK); }
            case CARTOGRAPHER -> { set(-2,1,-2,wood.boards().get()); set(-2,2,-2,Blocks.RED_CARPET); set(2,1,-2,Blocks.SEA_LANTERN); }
            case CLERIC -> { set(-2,1,-2,Blocks.CALCITE); set(-2,2,-2,Blocks.SMALL_AMETHYST_BUD); set(2,1,-2,Blocks.POTTED_AZURE_BLUET); }
            case FLETCHER -> { set(2,1,-2,Blocks.TARGET); set(-2,1,-2,Blocks.STRIPPED_BIRCH_WOOD); set(-2,2,-2,Blocks.BIRCH_PRESSURE_PLATE); }
            case LEATHERWORKER -> { set(-2,1,-2,Blocks.BROWN_WOOL); set(2,1,-2,Blocks.ORANGE_TERRACOTTA); set(2,2,-2,Blocks.BROWN_CARPET); }
            case MASON -> { set(-2,1,-2,Blocks.POLISHED_DIORITE); set(-2,2,-2,Blocks.QUARTZ_SLAB); set(2,1,-2,ReachArchitecture.RIB.get()); }
            case SHEPHERD -> { for(int a=-2;a<=2;a++) set(a,3,-2,a%2==0?Blocks.PINK_WOOL:(dusk?Blocks.PURPLE_WOOL:Blocks.WHITE_WOOL)); set(-2,1,-2,Blocks.MAGENTA_WOOL); }
            default -> throw new IllegalStateException("Unfinished workshop "+plot.job());
        }
    }
    private void field() {
        String[] crops={"uwu_wheat_crop","uwu_potato_crop","uwu_lettuce_crop"};
        for(int a=-2;a<=2;a+=2) for(int b=-2;b<=1;b++) {
            put(a,0,b,Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE,7));
            var crop=PantryPlants.fieldCrop(crops[(a+2)/2]);
            put(a,1,b,crop.getStateForAge(3+Math.floorMod(b+a+2,5)));
        }
        put(-1,0,-1,Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED,true));
        set(0,1,-3,Blocks.COMPOSTER);
        for(int a:new int[]{-3,3}){set(a,1,-3,wood.fence().get());set(a,2,-3,dusk?Blocks.SOUL_LANTERN:Blocks.LANTERN);}
    }
}
