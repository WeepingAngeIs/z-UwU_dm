// Bakes Minecraft UVs and color variants in place. Only authoring requires pngjs.
const fs = require('node:fs');
const path = require('node:path');
const {PNG} = require('pngjs');
const {sourceRoot,bake,mats} = require('./male-mesh.cjs');
const root=path.join(sourceRoot,'textures');
const read=f=>PNG.sync.read(fs.readFileSync(path.join(root,f+'.png')));
function save(f,data){fs.writeFileSync(path.join(root,f+'.png'),PNG.sync.write(data));}
const rgba=h=>[parseInt(h.slice(0,2),16),parseInt(h.slice(2,4),16),parseInt(h.slice(4,6),16),255];
const palette=xs=>xs.split(' ').map(rgba);
const colorSets=[
 // Original Guardian greens, living wood and pink pollen accents.
 ['35493a 57704a 779355 abc27c','403b33 674e42 85624b a78b60','604a3c 896847 b38d63 d6ae78',
 'ba8265 e6b88d f4d7aa ffe8be','a85876 dc8299 f5adbd ffdce0','a97240 d19c4f ffcc65 ffe7a2',
 '27292d 393936 494a35 595c3a','d5b299 f0d8b4 fff1d7 fffced','394f3b 4e674b 67885b 90a66a',
 '4a6742 709255 93b56e c5d898','373e32 4e4a3b 645a42 867354','8a6073 ac7389 d394aa f1bed0',
 'b7758a df9baa f6c0cc ffe7e8','4f5040 6f7454 949678 b7baa0','a57945 c89459 dfb66e ffe09c',
 '304e43 476348 5f7d51 80925c'],
 // Dusk villagers' charcoal-violet shadows, orchid blossoms, moonlit edging and muted teal foliage.
 ['2a2945 4f446e 786899 ada1d4','332b40 4d3a56 71566f 9b7892','49394d 70526c 96728b ba92a4',
 'a57886 d9b2b6 ebd2d0 fce8dd','754d82 ab75b8 d69adb f2c6f2','8c78a6 b9a9ca e5d9f2 fbf1ed',
 '222035 302b45 423652 563f63','bbaacc dfcee1 f6e9eb fff9f3','302e48 4e456a 73638e 9a88b3',
 '504366 756393 9a88b2 c5b3d6','302b41 43364d 625065 8a7184','603c62 8b5986 ba82a5 d9a8c5',
 '97729f bf92c5 e0b4df f6def0','474052 67596e 8c7f94 b4a6b9','81718a a89ab1 d5c4d6 f3e4ef',
 '2a374c 3e5063 5c7580 839a9a']
].map(set=>set.map(palette));
function put(img,x,y,c){
    if(x<0||y<0||x>=img.width||y>=img.height)return;
    img.data.set(c,(img.width*y+x)*4);
}
function poly(img,points,c){
    let xs=points.map(p=>p[0]),ys=points.map(p=>p[1]);
    for(let y=Math.floor(Math.min(...ys));y<=Math.ceil(Math.max(...ys));y++)for(let x=Math.floor(Math.min(...xs));x<=Math.ceil(Math.max(...xs));x++){
        let inShape=false;
        for(let i=0,j=points.length-1;i<points.length;j=i++){
            let [xi,yi]=points[i],[xj,yj]=points[j];
            if((yi>y+.5)!=(yj>y+.5)&&x+.5<(xj-xi)*(y+.5-yi)/(yj-yi)+xi)inShape=!inShape;
        }
        if(inShape)put(img,x,y,c);
    }
}
function line(img,points,ink,w=1){
    for(let i=1;i<points.length;i++){
        let [ax,ay]=points[i-1],[bx,by]=points[i],n=Math.max(Math.abs(ax-bx),Math.abs(ay-by),1);
        for(let k=0;k<=n;k++)for(let dx=0;dx<w;dx++)for(let dy=0;dy<w;dy++)
            put(img,Math.round(ax+(bx-ax)*k/n)+dx,Math.round(ay+(by-ay)*k/n)+dy,ink);
    }
}
function paintMale(dusk,wink){
    const tones=colorSets[+dusk], img=new PNG({width:128,height:128});
    for(let t=0;t<mats.length;t++){
        let tx=t%8*16,ty=Math.floor(t/8)*16;
        for(let y=0;y<16;y++)for(let x=0;x<16;x++)put(img,tx+x,ty+y,tones[t][1+(t===3?1:0)]);
        if(t===3)continue;
        // Tiny grain marks and paired leaf veins occupy separate spaces on each UV swatch.
        for(let j=0;j<5;j++){
            let x=(j*7+t*3)%14+1,y=(j*5+t)%12+1;
            let vertical=[1,2,5,10,14].includes(t);
            line(img,[[tx+x,ty+y],[tx+x+(vertical?0:2),ty+y+(vertical?2:0)]],tones[t][j%2===0?2:0]);
            if(j===0)put(img,tx+x+1,ty+y,tones[t][3]);
        }
    }
    let face=new PNG({width:64,height:60});
    const col=s=>rgba(s);
    const shades=dusk?['fadfd9','ebcdcb','dcb6b8','c49da4','a87e92','8e637c','503e54','362c42','ede4e8','b2a4d1','755d9a','dca5ba']:
        ['ffe4bc','f3d2a5','e6be90','d4a381','bd8a70','a77162','504338','302c31','fff4da','9bab79','577754','e2a797'];
    const [high,skin,shade,cheek,fold,lip,brow,ink,white,iris,deep,blush]=shades.map(col);
    for(let y=0;y<60;y++)for(let x=0;x<64;x++)put(face,x,y,skin);
    poly(face,[[0,0],[9,0],[12,13],[8,29],[10,41],[20,51],[18,57],[0,59]],shade);
    poly(face,[[63,0],[55,0],[52,13],[56,29],[53,40],[46,50],[48,58],[63,59]],shade);
    poly(face,[[12,24],[20,27],[19,30],[11,30]],high);
    poly(face,[[49,23],[52,22],[53,27],[45,29],[42,27]],high);
    poly(face,[[6,31],[13,30],[15,32],[11,34],[7,33]],blush);
    poly(face,[[49,31],[54,29],[58,30],[57,33],[52,34]],blush);
    // Distinct arched brows, asymmetrical smirk and a split lower chin read even with the cap on.
    poly(face,[[8,17],[10,13],[16,11],[23,12],[25,15],[22,15],[17,14],[12,15]],brow);
    poly(face,[[36,13],[39,10],[47,8],[51,9],[54,13],[50,12],[43,12],[38,15]],brow);
    line(face,[[13,14],[17,13],[21,14]],cheek);line(face,[[40,12],[46,10],[49,10]],cheek);
    function eye(points,left){
        if(wink&&left){
            line(face,[[9,23],[12,21],[16,20],[20,21],[22,22]],ink,2);
            line(face,[[12,24],[16,23],[19,24]],high);return;
        }
        poly(face,points,ink);
        const a=left?10:38, y=left?18:17;
        poly(face,[[a,y+3],[a+4,y+1],[a+9,y+1],[a+12,y+3],[a+8,y+5],[a+3,y+5]],white);
        poly(face,[[a+4,y+1],[a+8,y+1],[a+9,y+3],[a+7,y+5],[a+4,y+4]],deep);
        put(face,a+6,y+2,ink);put(face,a+5,y+1,white);put(face,a+7,y+4,iris);
    }
    eye([[8,21],[12,18],[16,17],[21,18],[24,21],[20,23],[15,24],[11,23]],true);
    eye([[36,19],[40,16],[45,16],[50,17],[53,20],[49,22],[44,23],[39,22]],false);
    line(face,[[26,30],[28,31],[31,30]],cheek);line(face,[[34,30],[37,30]],cheek);
    line(face,[[20,37],[24,38],[30,38],[36,37],[40,35],[42,35]],lip,2);
    line(face,[[24,38],[30,39],[35,38]],high);
    line(face,[[41,34],[43,33],[45,34]],lip);
    line(face,[[19,45],[22,46],[27,47]],cheek);line(face,[[34,47],[40,45]],cheek);
    line(face,[[18,49],[23,50],[30,51],[36,50],[44,48]],fold);
    line(face,[[25,54],[29,55],[34,54]],high);
    for(let y=0;y<60;y++)for(let x=0;x<64;x++){
        const i=(y*64+x)*4;put(img,x,y+64,[...face.data.subarray(i,i+4)]);
    }
    return img;
}
const lum=(r,g,b)=>(.299*r+.587*g+.114*b)/255;
function ramp(colors,t){
    t=Math.max(0,Math.min(2.9999,t*3));let i=Math.floor(t), f=t-i;
    return colors[i].map((v,k)=>k===3?255:Math.round(v+(colors[i+1][k]-v)*f));
}
function femaleDusk(src){
    const out=new PNG({width:src.width,height:src.height});
    const faceRamp=palette('483347 a47d87 e6c4c2 ffe7dd'),eyeRamp=palette('382a4a 6b5489 b49bc6 f2e3e8');
    const colors=colorSets[1];
    for(let y=0;y<src.height;y++)for(let x=0;x<src.width;x++){
        let i=(y*src.width+x)*4,[r,g,b,a]=src.data.subarray(i,i+4);
        if(!a)continue;
        const tile=Math.floor(y/64)*4+Math.floor(x/128), light=lum(r,g,b);
        let c;
        if(tile===0||tile===1){
            c=(g>r*1.025&&g>b*1.06)?ramp(eyeRamp,light):ramp(faceRamp,light);
            if(r>228&&Math.abs(r-g)<14&&Math.abs(g-b)<14)c=[r,g,b,255];
        }else{
            let mat;
            if([4,12,32,40,41,42,50,51,55].includes(tile))mat=4;
            else if([13,43,52,53].includes(tile))mat=5;
            else if([6,7,14,44,45,46].includes(tile))mat=1;
            else if(tile===11)mat=11;
            else if([8,9,10,15,26,27,28,29,37,39].includes(tile))mat=15;
            else mat=9;
            c=ramp(colors[mat],light);
        }
        put(out,x,y,[...c.slice(0,3),a]);
    }
    return out;
}
function femaleWink(){
    const open=read('entity/female_uwu_guardian'), shut=read('entity/female_uwu_guardian_blink');
    // The author placed the 32-square warm_face front at (160,32); copy only one eyelid.
    for(let y=32;y<64;y++)for(let x=160;x<176;x++){
        let i=(y*open.width+x)*4;shut.data.copy(open.data,i,i,i+4);
    }
    return open;
}
function remapSmall(name,destination,swatches){
    const base=read(name), mapping=Object.fromEntries(Object.entries(swatches).map(([a,b])=>[a,rgba(b)]));
    for(let i=0;i<base.data.length;i+=4){
        if(!base.data[i+3])continue;
        let key=base.data.subarray(i,i+3).toString('hex');
        if(!mapping[key])throw Error('Missing exact source color '+key+' in '+name);
        base.data.set(mapping[key],i);
    }
    save(destination,base);
}
bake();
save('entity/petal_guardian_male',paintMale(false,false));
save('entity/petal_guardian_male_wink',paintMale(false,true));
save('entity/dusk_guardian_male',paintMale(true,false));
save('entity/petal_guardian_female_wink',femaleWink());
for(const suffix of ['','_half','_blink','_half_lips','_blink_lips'])
    save('entity/dusk_guardian_female'+suffix,femaleDusk(read('entity/female_uwu_guardian'+suffix)));
remapSmall('block/drift_blossom_pad','entity/dusk_guardian_lily_pad',{'62a77a':'81709d','356f58':'514669','a9d99a':'c3abd3'});
remapSmall('item/owl_guardian_male_egg','item/dusk_guardian_male_egg',{'ecaab6':'b49ac7','799566':'6c6388','734e60':'4a3d59','ffdae1':'ead8ef','c27f8e':'8e779f','4e5547':'413e54','adc587':'a69cb9','424d44':'342e46','f6dca5':'e2d6af','fff1dc':'fff2db'});
remapSmall('item/owl_guardian_girl_egg','item/dusk_guardian_girl_egg',{'75aa80':'81729c','835774':'574365','fff1e7':'f2e3ec','ffd1e2':'e2c3e7','f39cbe':'b48cc8','aa5384':'77518f','466659':'48415d','b6d6a1':'b7a3c9','fff3f0':'f9eff7','fff1dc':'fff2db'});
