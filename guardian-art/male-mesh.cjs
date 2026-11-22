// Authoring coordinates use the original male rig's model pixels (16 per block, Y down).
// Keep this file with the baked mesh. node guardian-art/paint.cjs regenerates both palettes and the model.
const fs = require('node:fs');
const path = require('node:path');
const bones = ['basket', 'head', 'left', 'right', 'left_root', 'right_root'];
const parts = [];
const mats = ['moss','bark','sapwood','skin','petal','pollen','shadow','pearl','vine','leaf','root','ribbon','lightPetal','stone','brass','deepLeaf'];

function form(name, bone, material, center, rows, options = {}) {
    parts.push({name,bone,material,center,rows,power:options.power || 2.45, sides:options.sides || 16,
        roll:options.roll||0,pitch:options.pitch||0,yaw:options.yaw||0,face:!!options.face});
}
function oval(name,bone,material,center,size,options={}) {
    const [x,y,z]=size;
    const rows=[[-1,.17],[-.86,.56],[-.54,.88],[0,1],[.56,.86],[.88,.52],[1,.14]]
        .map(([h,r])=>[h*y,r*x,r*z]);
    form(name,bone,material,center,rows,options);
}
function flower(name,bone,cx,cy,cz,size) {
    // Each petal narrows to a real thickness. Slightly different tilt on adjacent petals.
    for(let i=0;i<5;i++) {
        let a=2*Math.PI*i/5;
        oval(name+'_petal_'+i,bone,'petal',[cx+Math.sin(a)*size*.55,cy-Math.cos(a)*size*.55,cz+.09*Math.cos(a)],
            [.37*size,.63*size,.23*size],{roll:a*180/Math.PI,pitch:7*Math.cos(a),sides:10});
    }
    oval(name+'_pollen',bone,'pollen',[cx,cy,cz-.22*size],[.29*size,.29*size,.18*size],{sides:10});
}

// One continuous face extends from the brow ridge to the tip of the second chin.
// The inward fold and forward second lobe are rings in the same surface, so the chins have no overlapping faces.
form('face_and_double_chin','head','skin',[0,0,-.72],[
    [-11.6,3.6,3.1,.24],[-10.7,5.30,3.92,.19],[-9.10,5.91,4.40,.10],[-7.58,6.12,4.54],
    [-5.8,6.01,4.68,-.12],[-4.37,5.91,4.73,-.32],[-3.20,5.62,4.91,-.64],[-2.27,4.99,4.57,-.98],
    [-1.77,4.39,3.82,-.75],[-1.35,4.46,4.03,-.62],[-.76,4.06,4.14,-.78],[-.17,3.40,3.74,-.70],
    [.23,2.34,2.67,-.50]], {power:3.5,sides:24,face:true});
oval('nose_bridge','head','skin',[0,-5.64,-5.61],[.88,1.12,.88],{power:2.6,sides:14});
oval('nose_tip','head','skin',[.06,-5.18,-5.89],[1.00,.49,.77],{sides:14});
// A separate rear canopy never lies on the facial front. Pads still use their original cut-out crown layer.
form('moss_cap','head','moss',[0,0,.39],[[-13.00,2.6,2.55],[-12.53,5.33,4.49],[-11.62,6.54,5.23],
    [-10.61,6.76,5.33],[-9.85,6.12,5.04],[-9.64,5.7,4.61]],{sides:22,power:3.0});
form('neck','basket','sapwood',[0,0,.28],[[-7.29,2.94,2.61],[-6.75,3.18,2.82],[-5.03,3.12,2.73],[-4.08,4.0,3.16]],{sides:16});
// Broad clavicles, a narrow waist, an apron of overlapping petals and carved chest plates.
form('living_bark_torso','basket','bark',[0,0,.45],[
    [-5.32,4.12,2.92],[-4.76,7.67,3.91],[-3.65,9.16,4.46],[-1.72,9.26,4.71],[.23,8.66,4.36],
    [2.33,7.48,3.82],[4.67,5.82,3.14],[6.69,4.99,3.04],[8.36,5.37,3.26],[10.24,5.96,3.61],
    [11.8,5.82,3.46],[12.50,4.59,3.09]],{sides:22,power:2.9});
for(const sign of [-1,1]) {
    const side=sign>0?'left':'right', arm=side, leg=side+'_root';
    oval(side+'_temple_leaf','head','leaf',[sign*5.77,-7.72,-.69],[1.56,3.27,2.97],{roll:-sign*13,pitch:-4});
    oval(side+'_crown_fold','head','deepLeaf',[sign*4.65,-10.55,-3.32],[2.45,.83,1.78],{roll:-sign*15,yaw:sign*10,sides:14});
    oval(side+'_pectoral','basket','sapwood',[sign*4.25,-1.79,-3.15],[4.37,2.75,2.03],
        {power:2.6,roll:-sign*10,sides:18});
    oval(side+'_collar_leaf','basket','leaf',[sign*4.74,-4.22,-2.95],[3.27,.75,1.51],{roll:-sign*12,sides:14});
    oval(side+'_waist_oblique','basket','bark',[sign*5.10,3.11,-1.79],[1.16,2.97,1.48],{roll:sign*22,sides:12});
    for(let i=0;i<3;i++)oval(side+'_rib_'+i,'basket','sapwood',[sign*(1.68-i*.05),1.89+i*1.67,-3.31+i*.15],
        [1.65-i*.08,.92-i*.11,.83],{roll:-sign*(8-i),sides:12});
    oval(side+'_hip_petal','basket','deepLeaf',[sign*4.15,10.22,-.90],[1.79,3.10,2.8],{roll:sign*12,sides:14});
    // The upper arm swells below its joint. All cuffs and pads have rounded, recessed edges.
    form(side+'_arm',arm,'sapwood',[0,0,0],[
        [-3.41,1.65,1.83],[-2.80,2.82,2.94],[-1.29,3.76,3.70],[.62,3.84,3.79],
        [2.08,3.34,3.05],[3.62,2.98,2.83],[5.26,3.06,2.96,-.18],[6.68,2.55,2.56,-.06],
        [7.58,2.30,2.22],[8.62,2.85,2.70,-.12],[10.64,2.91,2.65,-.29],
        [12.88,2.23,2.32,-.35],[13.89,2.05,2.13,-.37]], {sides:18});
    oval(side+'_leaf_pauldron',arm,'moss',[sign*.26,-.97,.30],[3.80,2.32,3.80],{roll:sign*9,sides:18});
    oval(side+'_biceps',arm,'sapwood',[sign*.08,4.08,-1.43],[2.44,2.88,2.09],{roll:-sign*8,sides:16});
    oval(side+'_inner_bracer',arm,'vine',[0,10.3,-.08],[3.08,2.29,2.96],{sides:16});
    oval(side+'_hand',arm,'bark',[sign*.08,15.01,-.38],[2.53,2.30,2.48],{power:3.4});
    oval(side+'_thumb',arm,'sapwood',[-sign*1.99,14.54,-1.14],[.96,1.29,1.12],{roll:-sign*16,sides:12});
    form(side+'_leg',leg,'root',[0,0,.20],[
        [-1.14,2.31,2.72],[.10,3.08,3.16],[2.03,3.19,3.25],[4.11,2.72,2.59],
        [5.55,2.68,2.63],[6.28,3.00,3.18],[8.17,2.68,2.86],[9.76,2.35,2.68]],{sides:16});
    oval(side+'_shin_leaf',leg,'leaf',[0,6.45,-1.64],[2.33,2.12,1.50],{sides:14});
    form(side+'_boot',leg,'bark',[0,0,-.59],[[8.01,2.42,2.85],[8.50,3.02,3.55,-.18],
        [9.48,3.54,4.53,-.50],[10.39,3.73,4.71,-.55],[11.02,3.38,4.15,-.47]],{power:3.7,sides:18});
}
oval('leaf_belt','basket','moss',[0,7.73,-.03],[5.58,1.18,3.64],{power:3.0,sides:20});
flower('heartwood_brooch','basket',-4.49,-2.94,-5.23,1.35);
flower('belt_blossom','basket',0,7.85,-3.83,1.49);
flower('shoulder_blossom','left',1.92,-1.48,-2.71,1.21);
flower('crown_blossom','head',-3.35,-11.43,-4.44,1.42);

function rotate(v,r) {
    let [x,y,z]=v, [rx,ry,rz]=r.map(a=>a*Math.PI/180);
    [y,z]=[y*Math.cos(rx)-z*Math.sin(rx),y*Math.sin(rx)+z*Math.cos(rx)];
    [x,z]=[x*Math.cos(ry)+z*Math.sin(ry),-x*Math.sin(ry)+z*Math.cos(ry)];
    return [x*Math.cos(rz)-y*Math.sin(rz),x*Math.sin(rz)+y*Math.cos(rz),z];
}
const sub=(a,b)=>a.map((x,i)=>x-b[i]);
const cross=([a,b,c],[d,e,f])=>[b*f-c*e,c*d-a*f,a*e-b*d];
const unit=v=>{const n=Math.hypot(...v);return v.map(x=>x/n);};
const fraction=(v,p)=>Math.sign(v)*Math.pow(Math.abs(v),p);
const sourceRoot=path.join(__dirname,'../src/main/resources/assets/uwudimension');
function bake(write = true) {
    let batches=bones.map(()=>[]), ranges=[];
    for(const shape of parts){
        const {rows,sides:n,power,center,roll,pitch,yaw}=shape, last=rows.length-1;
        function point(row,i){
            const [y,rx,rz,cz=0,cx=0]=rows[row], a=2*Math.PI*i/n-Math.PI;
            return [cx+rx*fraction(Math.sin(a),2/power),y,cz-rz*fraction(Math.cos(a),2/power)];
        }
        const rotatePart=v=>rotate(v,[pitch,yaw,roll]);
        const batch=batches[bones.indexOf(shape.bone)];
        const first=batch.length;
        function vertex(j,i,face,cap){
            let v=point(j,i), ang=2*Math.PI*i/n-Math.PI;
            let normal=cap?[0,cap,0]:unit(cross(sub(point(Math.min(last,j+1),i),point(Math.max(0,j-1),i)),sub(point(j,i+1),point(j,i-1))));
            let pos=rotatePart(v).map((value,k)=>(value+center[k])/16), norm=rotatePart(normal);
            let height=(j===0?0:j===last?1:(rows[j][0]-rows[0][0])/(rows[last][0]-rows[0][0]));
            let uv;
            if(face)uv=[(1.0+(ang/Math.PI+.5)*61)/128,(65+height*57)/128];
            else{
                const tile=mats.indexOf(shape.material);
                uv=[((tile%8)*16+1+i/n*14)/128,(Math.floor(tile/8)*16+1+height*14)/128];
            }
            return [...pos,...norm,...uv];
        }
        for(let j=0;j<last;j++)for(let i=0;i<n;i++){
            const front=shape.face && Math.cos((i+.5)*2*Math.PI/n-Math.PI)>0;
            batch.push(vertex(j,i,front),vertex(j+1,i,front),vertex(j+1,i+1,front),vertex(j,i+1,front));
        }
        for(const [j,sgn] of [[0,-1],[last,1]]){
            for(let i=0;i<n;i++){
                const a=vertex(j,i,false,sgn), b=vertex(j,i+1,false,sgn);
                const r=rows[j], c=rotatePart([r[4]||0,r[0],r[3]||0]).map((x,k)=>(x+center[k])/16);
                const mid=[...c,...a.slice(3)];
                if(sgn===-1)batch.push(mid,a,b,mid);else batch.push(mid,b,a,mid);
            }
        }
        ranges.push({name:shape.name,bone:shape.bone,first,count:batch.length-first});
    }
    const blocks=[Buffer.from('GDM1')];
    for(const batch of batches){
        const bytes=Buffer.alloc(4+batch.length*32);bytes.writeUInt32BE(batch.length,0);
        let offset=4;for(const v of batch)for(const f of v){bytes.writeFloatBE(f,offset);offset+=4;}
        blocks.push(bytes);
    }
    if (write) fs.writeFileSync(path.join(sourceRoot,'geometry/petal_guardian_male.mesh'),Buffer.concat(blocks));
    return ranges;
}
module.exports={sourceRoot,bones,parts,mats,bake,rotate};
