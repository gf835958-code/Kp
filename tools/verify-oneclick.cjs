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
vm.runInContext(functionSource('chooseDialogueTimes'),ctx);
const dialogueWindows=ctx.chooseDialogueTimes(1802);
assert.equal(dialogueWindows.length,6,'30-minute movie should sample six audio regions');
assert.ok(dialogueWindows[0]>0 && dialogueWindows.at(-1)<1802,'Audio windows within movie');
for(let i=1;i<dialogueWindows.length;i++)
  assert.ok(dialogueWindows[i]-dialogueWindows[i-1]>100,'Audio evidence regions must cover film');
assert.equal(ctx.chooseDialogueTimes(40).length,1,'Short movie should not waste Whisper inference');
assert.equal(ctx.chooseDialogueTimes(NaN).length,0,'Invalid duration must not launch ASR');
assert.ok(script.includes('best.dialogueEvidenceList.push('),'Must preserve verified scene speech quotes');
assert.ok(script.includes('Original soundtrack excerpts'),'Keep the original soundtrack evidence separate from Burmese recap');
assert.ok(script.includes('fullFilmDialogueEvidence.map('),'Full-film dialogue must remain timestamped in script');
vm.runInContext(functionSource('formatTime')+'\n'+functionSource('addSourceDialogue'),ctx);
const sourceScenes=[{time:25,labels:[]}],sourceEvidence=[];
assert.equal(ctx.addSourceDialogue({time:325,lang:'en',text:'Take the map to the house.'},sourceScenes,sourceEvidence),true);
assert.equal(sourceEvidence.length,1,'Preserve distant original speech in global timeline');
assert.ok(!sourceScenes[0].dialogueEvidenceList,'Distant dialogue must not be falsely placed over unrelated scenes');
assert.equal(ctx.addSourceDialogue({time:26,lang:'my',text:'မင်္ဂလာပါ။'},sourceScenes,sourceEvidence),true);
assert.equal(sourceScenes[0].dialogueEvidenceList.length,1,'Nearby Burmese dialogue must attach to matching scene');
assert.equal(ctx.addSourceDialogue({time:26,lang:'my',text:'မင်္ဂလာပါ။'},sourceScenes,sourceEvidence),false,'Deduplicate source utterance at same timestamp');

ctx.visualDict={person:'လူ',house:'အိမ်'};
vm.runInContext(functionSource('briefGroundedNarration'),ctx);
const emptyNarration=ctx.briefGroundedNarration({labels:[],dialogueEvidenceList:[]});
assert.ok(emptyNarration.length<60 && /[\u1000-\u109f]/.test(emptyNarration),
  'Unknown image must not invent a movie plot or create a long caption');
const grounded=ctx.briefGroundedNarration({
  labels:[{label:'person',confidence:.9}],dialogueEvidenceList:[
    {lang:'en',text:'I found a map, said John.'}
  ]});
assert.ok(grounded.includes('လူ') && !grounded.includes('John'),
  'Non-Burmese speech must not be falsely narrated as a Burmese translation');
const matchingVoice=ctx.briefGroundedNarration(sourceScenes[0],24,36);
assert.ok(matchingVoice.includes('မင်္ဂလာပါ'),'Burmese quote in source clip should be narrated');
const unrelatedVoice=ctx.briefGroundedNarration(sourceScenes[0],90,102);
assert.ok(!unrelatedVoice.includes('မင်္ဂလာပါ'),'Never reuse an unrelated Burmese quote in a different highlight clip');
assert.ok(script.includes('if(end-start>=5)'),'Tiny segments must not force truncated speech');
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
const preflight=fs.readFileSync('app/src/main/java/com/kp/movierecap/MovieExportPreflight.java','utf8');
assert.ok(activity.includes('MovieExportPreflight.verify('),
  'One-click export must validate source video and storage before offline TTS');
assert.ok(activity.includes('MovieExportPreflight.verifyOutput(encoded)'),
  'App must verify decoded video and audio tracks before declaring MP4 saved');
assert.ok(preflight.includes('video = true') && preflight.includes('audio = true'),
  'Completed MP4 must contain valid video and speech tracks');
assert.ok(preflight.includes('METADATA_KEY_VIDEO_WIDTH') &&
  preflight.includes('getAvailableBytes()'),
  'Preflight must validate the selected video and available storage');
assert.ok(!activity.includes('ranges.sort(Comparator.comparingLong'),
  'Sorting video ranges without matching captions would cause wrong narration');
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
assert.ok(html.includes('<option value="12" selected>12 Seconds</option>'), 'Narrated clip duration is too short');
assert.ok(script.includes("function buildAutoCaptionPlan()"), 'Caption clip planning missing');


assert.ok(activity.includes('transcribeVideoNative('), 'Android dialogue bridge missing');
assert.ok(script.includes('scanDialogueContext()'), 'Offline dialogue not connected to one-click scan');
assert.ok(fs.readFileSync('app/src/main/java/com/kp/movierecap/DialogueTranscriber.java','utf8')
  .includes('new OfflineRecognizer('), 'On-device Whisper decoder missing');
const decoder=fs.readFileSync('app/src/main/java/com/kp/movierecap/DialogueTranscriber.java','utf8');
assert.ok(decoder.includes('double rms = Math.sqrt('),'Silence gate missing');
assert.ok(decoder.includes('seenText.add('),'ASR duplicate suppression missing');
assert.ok(decoder.includes('ProgressListener progress'),'ASR progress callback missing');
assert.ok(activity.includes('reportNativeDialogueProgress('),'Android progress bridge missing');
assert.ok(script.includes('window.onNativeDialogueProgress='),'UI progress receiver missing');

assert.ok(fs.readFileSync('app/src/main/java/com/kp/movierecap/MovieAudioSampler.java','utf8')
  .includes('MediaCodec.createDecoderByType'), 'Movie audio decoder missing');
// Mobile-first UI acceptance: one main action and an accessible result state.
assert.ok(html.includes('class="flow-steps"') && html.includes('Video ရွေး') &&
  html.includes('MP4 ကြည့်'), 'Three-step Burmese onboarding missing');
assert.ok(html.includes('id="exportResult" hidden') &&
  html.includes('id="openVideo"'), 'Saved output card missing');
assert.ok(html.includes('id="detailsToggle"') &&
  html.includes('aria-expanded="false"'), 'Accessible advanced-settings toggle missing');
assert.ok(script.includes("$('autoBtn').disabled=!currentFile"),
  'Generate action should be disabled until a video is selected');
assert.ok(script.includes("$('exportResult').hidden=!success"),
  'MP4 completion must reveal result, not only show transient toast');
assert.ok(script.includes('AndroidRecap.openLastExportedVideo()') &&
  activity.includes('void openLastExportedVideo()'),
  'View saved MP4 native bridge missing');
assert.ok(html.includes('ဇာတ်လမ်းတစ်ကားလုံး') &&
  html.includes('AI မပြီးသေးပါ'), 'Experimental story limitation not disclosed');
assert.ok(!html.includes('Burmese TTS Model မထည့်ရသေးပါ'),
  'Outdated unsupported voice-model copy still visible');
assert.ok(script.includes('window.onNativeExportProgress='),'Real render progress listener missing');
assert.ok(activity.includes('void reportNativeExportProgress('),'Native render progress bridge missing');
assert.ok(captioned.includes('current.getProgress(progress)'),'Media3 actual progress polling missing');
assert.ok(script.includes("setProgress($('autoBtn').disabled?82:100"),
  'Scan must not report 100% for whole one-click MP4 pipeline');
console.log('PASS: JavaScript syntax, native 30min scene planning, distributed dialogue coverage, silent-audio guard, narrated MP4 wiring');
