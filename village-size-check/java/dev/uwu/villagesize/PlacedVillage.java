package dev.uwu.villagesize;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import dev.uwu.dimension.BlossomVillage;
import dev.uwu.dimension.Keeper;
import dev.uwu.dimension.People;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.AABB;

import static dev.uwu.villagesize.VillageSizeProbe.require;

final class PlacedVillage {
    static JsonObject inspect(ServerLevel level, BlossomVillage.Plan plan, boolean fresh) {
        var site=plan.site();
        var firstDoor=level.getBlockState(plan.doorway(plan.houses().getFirst())).getBlock();
        String expectedDoor=plan.dusk()?"uwudimension:velvetwood_door":"uwudimension:silkwood_door";
        String crest=plan.dusk()?"uwudimension:midnight_lace":"uwudimension:rosette_mosaic";
        require(BuiltInRegistries.BLOCK.getKey(firstDoor).toString().equals(expectedDoor),"Wrong faction door "+firstDoor);
        int low=plan.houses().stream().mapToInt(BlossomVillage.House::floor).min().orElseThrow()-5;
        int high=plan.houses().stream().mapToInt(BlossomVillage.House::floor).max().orElseThrow()+6;
        var corners=new BlockPos[]{site.pos(plan.x()-40,low,plan.z()-52),site.pos(plan.x()+40,high,plan.z()+52)};
        var found=new HashSet<BlockPos>();
        // Scan actual chunk blocks for complete faction entrances. No planned house coordinates are used in this count.
        for (BlockPos pos : BlockPos.betweenClosed(corners[0],corners[1])) {
            var state=level.getBlockState(pos);
            if(state.is(firstDoor) && state.getValue(DoorBlock.HALF)==DoubleBlockHalf.LOWER
                    && level.getBlockState(pos.above()).is(firstDoor)
                    && BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.above(2)).getBlock()).toString().equals(crest))
                found.add(pos.immutable());
        }
        var planned=new HashSet<BlockPos>();
        var houses=new JsonArray();
        int paths=0,beds=0;
        for(var house:plan.houses()){
            BlockPos door=plan.doorway(house);planned.add(door);
            require(found.contains(door),"Missing built entrance "+door+" on "+site+" house "+house);
            require(level.getBlockState(plan.workstation(house)).is(house.job()),"Missing workstation at "+plan.workstation(house));
            int bedCount=house.wallBeds()||house==plan.houses().getFirst()?2:1;
            for(int slot=0;slot<bedCount;slot++){
                var state=level.getBlockState(plan.bedHead(house,slot));
                require(state.getBlock() instanceof BedBlock && state.getValue(BedBlock.PART)==BedPart.HEAD,
                        "Missing bed head at "+plan.bedHead(house,slot)); beds++;
            }
            int dx=house.x()-plan.x(),index=dx+plan.road().length/2;
            int start=plan.z()+plan.bends()[index]+2*house.side(),end=house.z()-house.side()*house.depth();
            int distance=Math.abs(end-start);
            int previous=plan.road()[index];
            for(int i=0;i<distance;i++){
                var column=site.pos(house.x(),previous,start+i*house.side());
                // Find the actual paved tread independently of the generator's interpolation. Adjacent
                // treads must connect and leave room for a player. Legacy porches can expose native soil.
                boolean foundTread=false;
                for(int dy:new int[]{0,1,-1}){
                    var ground=column.above(dy);
                    var state=level.getBlockState(ground);
                    boolean authored=BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("uwudimension");
                    if ((!authored && (fresh || distance-i>2)) || state.getCollisionShape(level,ground).isEmpty())continue;
                    if (!level.getBlockState(ground.above()).getCollisionShape(level,ground.above()).isEmpty()
                            || !level.getBlockState(ground.above(2)).getCollisionShape(level,ground.above(2)).isEmpty())continue;
                    previous=ground.getY();foundTread=true;break;
                }
                require(foundTread,"Missing walkable tread at "+column+" house "+house+" remaining="+(distance-i)
                        +" column="+level.getBlockState(column.below())+","+level.getBlockState(column)+","+level.getBlockState(column.above()));
                paths++;
            }
            require(Math.abs(previous-house.floor())<=1,"Disconnected last tread at "+door);
            var h=new JsonObject();h.addProperty("x",dx);h.addProperty("z",house.z()-plan.z());
            h.addProperty("width",house.width());h.addProperty("depth",house.depth());h.addProperty("side",house.side());
            h.addProperty("floor",house.floor());h.addProperty("style",house.style());
            h.addProperty("job",BuiltInRegistries.BLOCK.getKey(house.job()).toString());
            h.addProperty("verifiedDoor",door.toShortString()); houses.add(h);
        }
        require(found.equals(planned),"Independent completed dwelling count differs, found "+found+" planned "+planned);
        require(level.getBlockState(site.pos(plan.x()+2,plan.y()+1,plan.z()-2)).is(Blocks.BELL),"Missing meeting bell");
        var keepers=level.getEntitiesOfClass(Keeper.class,new AABB(plan.plaza()).inflate(78));
        require(!keepers.isEmpty()&&keepers.stream().allMatch(k->k.getType()==(plan.dusk()?People.DUSK_KEEPER.get():People.PETAL_KEEPER.get())),
                "Missing or wrong faction residents at "+plan.plaza());
        int gate=plan.dusk()?plan.road().length-3:2;
        var post=site.pos(plan.x()+gate-plan.road().length/2,plan.road()[gate]+2,plan.z()+plan.bends()[gate]+3);
        require(!level.getBlockState(post).isAir(),"Missing street gate at "+post);
        var row=new JsonObject(); row.addProperty("seed",level.getSeed());row.addProperty("gx",site.gx());row.addProperty("gz",site.gz());
        if (GuardianVillageWitness.ENABLED) row.addProperty("guardianTranslationKey",GuardianVillageWitness.verify(level,plan));
        row.addProperty("faction",plan.dusk()?"Dusk":"Petal");row.addProperty("actualHouses",found.size());row.addProperty("residentsSeen",keepers.size());
        row.addProperty("beds",beds);row.addProperty("walkablePathColumns",paths);row.addProperty("plaza",plan.plaza().toShortString());
        var bends=new JsonArray();for(int z:plan.bends())bends.add(z);row.add("bends",bends);row.add("houses",houses);return row;
    }
    private PlacedVillage(){}
}
