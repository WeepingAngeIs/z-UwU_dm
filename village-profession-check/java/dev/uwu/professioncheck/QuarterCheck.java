package dev.uwu.professioncheck;

import java.lang.reflect.*;
import java.util.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import dev.uwu.dimension.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.*;
import net.minecraft.world.item.crafting.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayerFactory;

final class QuarterCheck {
    static void check(boolean pass,String why){if(!pass)throw new AssertionError(why);}
    static Class<?> type(String name)throws Exception{return Class.forName("dev.uwu.dimension."+name);}
    static Object call(Object self,String name,Object... args)throws Exception {
        Class<?> t=self instanceof Class<?> c?c:self.getClass();
        for(Method m:t.getDeclaredMethods())if(m.getName().equals(name)&&m.getParameterCount()==args.length){
            m.setAccessible(true);return m.invoke(self instanceof Class<?>?null:self,args);
        }
        throw new NoSuchMethodException(t+"."+name);
    }
    static Object field(Object self,String name)throws Exception{
        Class<?> t=self instanceof Class<?> c?c:self.getClass();
        for(;t!=null;t=t.getSuperclass()){
            try{var f=t.getDeclaredField(name);f.setAccessible(true);return f.get(self instanceof Class<?>?null:self);}
            catch(NoSuchFieldException next){}
        }
        throw new NoSuchFieldException(name);
    }
    static List<?> plots(Object layout)throws Exception{return (List<?>)call(layout,"plots");}
    static BlockPos at(BlossomVillage.Plan p,Object tile,int y)throws Exception{
        return p.site().pos((int)call(tile,"x"),y,(int)call(tile,"z"));
    }
    static Item item(String id){return BuiltInRegistries.ITEM.get(ResourceLocation.parse(id.contains(":")?id:"uwudimension:"+id));}
    static void recipes(ServerLevel l)throws Exception {
        var actor=FakePlayerFactory.getMinecraft(l);
        Class<?> api=Class.forName("team.creative.solonion.common.SOLOnionConfig");
        Object config=null;
        for(String name:List.of("team.creative.solonion.SOLOnion","team.creative.solonion.common.SOLOnion")){
            try{for(Field f:Class.forName(name).getDeclaredFields())if(Modifier.isStatic(f.getModifiers())&&api.isAssignableFrom(f.getType())){f.setAccessible(true);config=f.get(null);}}
            catch(ClassNotFoundException unused){}
        }
        check(config!=null,"SOL Onion active configuration");
        var dishes=new LinkedHashMap<String,List<String>>();
        dishes.put("petal_harvest_galette",List.of("uwu_wheat","uwu_potato","glowginger"));
        dishes.put("moonlit_potato_chowder",List.of("uwu_potato","uwu_lettuce","minecraft:brown_mushroom","minecraft:milk_bucket","minecraft:bowl"));
        dishes.put("dewleaf_picnic_wrap",List.of("uwu_lettuce","uwu_wheat","sweetbean_pods"));
        dishes.put("sakura_flour",List.of("uwu_wheat","uwu_wheat"));
        for(var e:dishes.entrySet()){
            var input=new ArrayList<ItemStack>();e.getValue().forEach(k->input.add(new ItemStack(item(k))));
            while(input.size()<9)input.add(ItemStack.EMPTY);
            var grid=CraftingInput.of(3,3,input);var recipe=l.getRecipeManager().getRecipeFor(RecipeType.CRAFTING,grid,l).orElseThrow();
            var prepared=recipe.value().assemble(grid,l.registryAccess());
            check(prepared.is(item(e.getKey())),"Normal crafting returned "+prepared);
            if(!e.getKey().equals("sakura_flour")){
                double score=(double)api.getMethod("getDiversity",net.minecraft.world.entity.LivingEntity.class,ItemStack.class).invoke(config,actor,prepared);
                check(score>=8,"Low diversity "+prepared+" "+score);System.out.println("DISH "+e.getKey()+" diversity="+score);
            }
        }
    }
    static List<BlossomVillage.Plan> villages(ServerLevel level)throws Exception {
        var plans=new ArrayList<BlossomVillage.Plan>();Set<String> seen=new HashSet<>();boolean petal=false,dusk=false;
        var cls=type("ProfessionQuarter");int samples=0;
        try(var r=new BufferedReader(new InputStreamReader(QuarterCheck.class.getResourceAsStream("/village-sites.csv"),StandardCharsets.UTF_8))){
            for(String line:r.lines().skip(1).toList()){
                var v=line.split(",");if(!v[0].equals(Long.toString(level.getSeed())))continue;
                boolean dark=v[3].equals("dusk");
                var opt=BlossomVillage.plan(level,GroveSites.site(level,Integer.parseInt(v[1]),Integer.parseInt(v[2])),dark);
                if(opt.isEmpty())continue;
                var p=opt.get();int mask=(int)call(cls,"rolledJobs",level.getSeed(),p);
                Set<String> jobs=new HashSet<>();var layout=call(cls,"plan",level,p);
                for(var plot:plots(layout))jobs.add(call(plot,"job").toString());
                check(jobs.size()==Integer.bitCount(mask),"Selected plots dropped");
                boolean additional=jobs.stream().anyMatch(j->!seen.contains(j));
                if(!jobs.isEmpty() && ((dark?!dusk:!petal) || additional && samples<7)){
                    verifyBlocks(level,p,layout);seen.addAll(jobs);plans.add(p);dusk|=dark;petal|=!dark;samples++;
                }
                if(dusk&&petal&&seen.size()==13)break;
            }
        }
        check(petal&&dusk&&samples>=2,"No representative villages");
        var p=plans.getFirst();var all=call(cls,"design",level,p,8191);check(plots(all).size()==13,"All-selected village");
        check(plots(call(cls,"design",level,p,0)).isEmpty(),"None-selected village");
        var single=new int[13];int joint=0;
        for(int n=0;n<8192;n++){int mask=(int)call(cls,"rolledJobs",(long)n,p);
            for(int j=0;j<13;j++)single[j]+=(mask>>j)&1;if((mask&3)==3)joint++;}
        for(int count:single)check(count>1810&&count<2290,"Marginal distribution "+Arrays.toString(single));
        check(joint>400&&joint<630,"Dependent rolls "+joint);
        System.out.println("VILLAGES count="+samples+" professions="+seen+" marginal="+Arrays.toString(single)+" joint="+joint+" forced=0,13");
        return plans;
    }
    static void verifyBlocks(ServerLevel l,BlossomVillage.Plan p,Object layout)throws Exception {
        check(p.houses().size()>=6&&p.houses().size()<=12,"Homes changed");
        for(var h:p.houses()){check(l.getBlockState(p.doorway(h)).getBlock() instanceof DoorBlock,"House door "+p.doorway(h));
            check(l.getBlockState(p.bedHead(h)).getBlock() instanceof BedBlock,"House bed");}
        var jobs=new ArrayList<String>();Set<BlockPos> occupied=new HashSet<>();
        Set<BlockPos> walking=new HashSet<>();
        for(int i=0;i<p.road().length;i++)for(int a=-1;a<=1;a++)walking.add(p.site().pos(p.x()+i-32,p.road()[i]+1,p.z()+p.bends()[i]+a));
        for(Object tile:(Set<?>)call(layout,"street")){
            var at=at(p,tile,(int)call(type("ProfessionQuarter"),"streetHeight",p,tile)+1);
            if(l.getBlockState(at).getCollisionShape(l,at).isEmpty() && l.getBlockState(at.above()).getCollisionShape(l,at.above()).isEmpty()
                    && !l.getBlockState(at.below()).getCollisionShape(l,at.below()).isEmpty())walking.add(at);
        }
        var reached=new HashSet<BlockPos>();var queue=new ArrayDeque<BlockPos>();queue.add(p.plaza());reached.add(p.plaza());
        while(!queue.isEmpty()){var a=queue.remove();for(Direction d:Direction.Plane.HORIZONTAL)for(int dy=-1;dy<=1;dy++){
            var next=a.relative(d).above(dy);if(walking.contains(next)&&reached.add(next))queue.add(next);}}
        for(Object plot:plots(layout)){
            Object job=call(plot,"job"),c=call(plot,"center");Direction front=(Direction)call(plot,"front");
            int floor=(int)call(plot,"floor");String name=job.toString();jobs.add(name);
            for(int x=-3;x<=3;x++)for(int z=-3;z<=3;z++)check(occupied.add(at(p,c,floor).offset(x,0,z)),"Overlapping plots");
            var landing=at(p,call(c,"step",front,4),floor+1);check(reached.contains(landing),"Disconnected landing "+name+" "+landing+" at "+p.plaza());
            var station=at(p,call(c,"step",front,name.equals("FARMER")?-3:-2),floor+1);
            check(l.getBlockState(station).is((Block)field(job,"station")),"Missing "+name+" "+station);
            check(l.getPoiManager().getType(station).isPresent(),"Workstation has no actual POI "+station);
            if(name.equals("TOOLSMITH"))check(l.getBlockState(at(p,call(call(c,"step",front,-1),"step",front.getClockWise(),-2),floor+1)).is(Blocks.ANVIL),"Welder anvil");
            if(name.equals("FARMER"))for(int j=-2;j<=2;j+=2){
                var tile=call(call(c,"step",front,-2),"step",front.getClockWise(),j);var at=at(p,tile,floor+1);
                var state=l.getBlockState(at);var crop=(CropBlock)state.getBlock();
                String id=BuiltInRegistries.BLOCK.getKey(crop).toString();
                String plant=new String[]{"uwu_wheat","uwu_potato","uwu_lettuce"}[(j+2)/2];
                check(id.equals("uwudimension:"+plant+"_crop") && l.getBlockState(at.below()).is(Blocks.FARMLAND),"Wrong crop "+id);
                var drops=Block.getDrops(crop.getStateForAge(7),l,at,null);
                check(drops.stream().anyMatch(s->s.is(item(plant)))&&drops.stream().anyMatch(s->s.is(item(plant.equals("uwu_potato")?plant:plant+"_seeds"))),"Harvest "+id+" "+drops);
            }
        }
        System.out.println("GENERATED "+(p.dusk()?"Dusk":"Petal")+" "+p.plaza()+" homes="+p.houses().size()+" jobs="+jobs);
    }
    private QuarterCheck(){}
}
