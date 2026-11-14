'use strict';
const fs=require('node:fs');
const vm=require('node:vm');
const assert=require('node:assert/strict');
const html=fs.readFileSync('app/src/main/assets/index.html','utf8');
const activity=fs.readFileSync('app/src/main/java/com/kp/movierecap/MainActivity.java','utf8');
const scanner=fs.readFileSync('app/src/main/java/com/kp/movierecap/NativeSceneScanner.java','utf8');
const script=[...html.matchAll(/<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g)].map(x=>x[1]).join('\n');
assert.ok(script.length>100, 'app script missing');
new vm.Script(script,{filename:'index.html'});
function functionSource(name) {
  const found=new RegExp('function\\s+'+name+'\\s*\\(').exec(script);
  assert.ok(found,name+' missing');
  const start=found.index,open=script.indexOf('{',start);
  let depth=0;
  for(let i=open;i<script.length;i++) {
    if(script[i]==='{')depth++;
    else if(script[i]==='}'&&--depth===0)return script.slice(start,i+1);
  }
  throw Error('Unclosed function '+name);
}
const fields={maxScenes:{value:'10'},target:{value:'4'},preview:{duration:NaN}};
const ctx={shotList:[],lastNativeDuration:1802,$:id=>fields[id],Number,Math,Array,Error};
vm.createContext(ctx);
vm.runInContext(functionSource('chooseNativeScenes')+'\n'+functionSource('buildAutoClips'),ctx);
const frames=Array.from({length:20},(_,i)=>({time:40+i*87,diff:i===0?1:i/22,thumb:'data:image/jpeg;base64,ABCD'}));
const picks=ctx.chooseNativeScenes(frames,1802,10);
assert.ok(picks.length>=2&&picks.length<=10,'Scene count');
for(let i=1;i<picks.length;i++)assert.ok(picks[i].time>picks[i-1].time,'Unsorted scenes');
ctx.shotList=picks;
const clips=ctx.buildAutoClips();
assert.ok(clips.length>=2&&clips.length<=10,'No clips');
for(let i=0;i<clips.length;i++){
  assert.ok(clips[i][0]>=0&&clips[i][1]<=1802,'Clip outside video duration');
  assert.ok(clips[i][1]-clips[i][0]>.5,'Empty clip');
  if(i)assert.ok(clips[i][0]>=clips[i-1][1],'Overlapping clips');
}
assert.ok(script.includes("lastNativeDuration || Number($('preview').duration)"),'Native duration not used');
assert.ok(script.includes('scanVideoNative')&&script.includes('exportHighlightsAuto'),'One-click bridge missing');
assert.equal(activity.split('void scanVideoNative(').length-1,1,'Duplicate or missing scanner bridge');
assert.ok(activity.includes('NativeSceneScanner.sample'),'Native scan not wired');
assert.ok(scanner.includes('MediaMetadataRetriever'),'Native frame reader missing');
assert.ok(activity.includes('Environment.DIRECTORY_MOVIES'),'Automatic file save missing');
assert.ok(activity.includes('exportCaptionedAuto('), 'Captions bridge missing');
assert.ok(activity.includes('saveCaptionedToMovies('), 'Caption MP4 auto-save missing');
assert.ok(script.includes('AndroidRecap.exportCaptionedAuto('), 'UI not connected to caption exporter');
assert.ok(script.includes('buildAutoCaptionPlan()'), 'Caption/clip alignment missing');
const captioned=fs.readFileSync('app/src/main/java/com/kp/movierecap/CaptionedVideoExporter.java','utf8');
assert.ok(captioned.includes('new OverlayEffect('), 'Media3 text overlay missing');
assert.ok(captioned.includes('EditedMediaItemSequence.withAudioFrom(') &&
  captioned.includes('EditedMediaItemSequence.withVideoFrom('),
  'Separate Media3 voice and video tracks missing');
assert.ok(activity.includes('BurmeseNarration.generate('), 'Offline Burmese narration not wired');
const voice = fs.readFileSync('app/src/main/java/com/kp/movierecap/BurmeseNarration.java','utf8');
assert.ok(voice.includes('OfflineTts('), 'Offline voice engine missing');
assert.ok(voice.includes('mya/model.onnx'), 'Burmese model path missing');
assert.ok(voice.includes('samples.length > frames'), 'Missing speech clipping guard');
assert.ok(script.includes('<option value="12" selected>12 Seconds</option>'), 'Narrated clip duration is too short');
assert.ok(script.includes("function buildAutoCaptionPlan()"), 'Caption clip planning missing');


console.log('PASS: JS syntax, 30min scene/clip planning, native bridge and auto-export wiring');
