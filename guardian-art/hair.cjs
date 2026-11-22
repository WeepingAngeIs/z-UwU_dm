// Assemble the female Guardian's actual cutout locks on the continuous male hair shell.
const fs = require('node:fs');
const path = require('node:path');
const {PNG} = require('pngjs');

function paintHair(atlas, female, sourceRoot, charts) {
    const rig=JSON.parse(fs.readFileSync(path.join(sourceRoot,'geometry/guardian_bones.json'),'utf8'));
    const head=rig.bones.find(b=>b.name==='head');
    const tiles=new Map(head.cubes.map(box=>[box.name,box.tile]));
    const unit=rig.width/16, shade=.86;
    function stamp(panel,name,x,y,w,h,slant=0) {
        const tile=tiles.get(name);
        if(tile===undefined)throw Error('Missing female Guardian hair material '+name);
        const sx=(tile%4)*unit*4+unit, sy=Math.floor(tile/4)*unit*2+unit;
        for(let row=0;row<h;row++)for(let col=0;col<w;col++) {
            const dx=x+col+Math.round(slant*row/h), dy=y+row;
            if(dx<0||dy<0||dx>=panel.width||dy>=panel.height)continue;
            const i=((sy+Math.floor(row*unit/h))*female.width+sx+Math.floor(col*unit/w))*4;
            const j=(dy*panel.width+dx)*4;
            if(!female.data[i+3])continue;
            for(let c=0;c<3;c++)panel.data[j+c]=Math.round(female.data[i+c]*shade);
            panel.data[j+3]=female.data[i+3];
        }
    }
    for(const [name,[ox,oy,w,h]] of Object.entries(charts)) {
        const panel=new PNG({width:w,height:h});panel.data.fill(0);
        if(name==='front'){
            stamp(panel,'under_canopy',0,0,w,7);
            // A short, side-swept fringe leaves both brows clear. Only the outer locks reach the temples.
            for(const [part,x,width,height,slant] of [
                [1,-6,18,27,-2],[2,5,23,19,-3],[3,22,23,16,-5],[4,40,25,14,-6],
                [5,59,22,12,-3],[4,76,21,15,3],[6,89,21,25,4]
            ])stamp(panel,'fringe_leaf_'+part,x,0,width,height,slant);
        }else if(name==='corner'){
            stamp(panel,'under_canopy',0,0,w,25);
            stamp(panel,'left_temple_slip',-4,8,21,36,2);
            stamp(panel,'right_temple_slip',12,5,22,34,2);
            stamp(panel,'fringe_leaf_5',4,-2,23,29,2);
        }else{
            const back=name==='back', step=back?24:20;
            stamp(panel,'under_canopy',0,0,w,back?38:28);
            // Her canopy and nape locks are authored in left/right pairs with a shared central vein.
            for(let k=-1;k*step<w;k++){
                const x=k*step, variation=(k+4)%3;
                const prefix=variation===1?'right_nape_leaf':'middle_canopy_'+(variation===2?1:0);
                const base=back?33:22, height=(back?32:27)+(variation===1?5:0);
                stamp(panel,prefix+'_a',x,base,step/2+2,height,2);
                stamp(panel,prefix+'_b',x+step/2,base,step/2+2,height,2);
            }
            for(let k=-1;k*step<w;k++){
                const x=k*step+8, prefix=k%2===0?'middle_canopy_1':'middle_canopy_0';
                const height=(back?39:30)+(k%2===0?4:0);
                stamp(panel,prefix+'_a',x,-2,step/2+2,height,-3);
                stamp(panel,prefix+'_b',x+step/2,-2,step/2+2,height,-3);
            }
        }
        PNG.bitblt(panel,atlas,0,0,w,h,ox,oy);
    }
}

module.exports={paintHair};
