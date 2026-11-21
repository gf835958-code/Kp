"""Auto MMSub v1.2: inspect imported PDF/video locally before upload."""
from pathlib import Path
r=Path('android/app/src/main/java/com/kp/autommsub')
def change(s,old,new,label):
    assert s.count(old)==1, (label,s.count(old))
    return s.replace(old,new,1)
p=r/'Network.kt';s=p.read_text(encoding='utf-8')
s=change(s,'import android.provider.OpenableColumns','''import android.provider.OpenableColumns
import android.media.MediaMetadataRetriever
import android.graphics.pdf.PdfRenderer''','imports')
s=change(s,'/** Reject example, local-only, credential-bearing, and malformed server URLs. */','''/** Check real user-selected media locally, without uploading any content. */
data class InputInspection(val uri: Uri, val kind: String, val filename: String,
                           val sizeBytes: Long, val summary: String)
fun inspectInput(context: Context, uri: Uri, kind: String): InputInspection {
    require(kind == "video" || kind == "pdf")
    var name = "input.${if (kind == "video") "mp4" else "pdf"}"
    var bytes = -1L
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE), null,null,null)?.use { cursor ->
        if(cursor.moveToFirst()) {
            val ni=cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val si=cursor.getColumnIndex(OpenableColumns.SIZE)
            if(ni>=0 && !cursor.isNull(ni)) name=cursor.getString(ni)
            if(si>=0 && !cursor.isNull(si)) bytes=cursor.getLong(si)
        }
    }
    val pfd=context.contentResolver.openFileDescriptor(uri,"r") ?: error("Selected file cannot be opened")
    val detail=if(kind=="pdf") {
        pfd.use { file ->
            if(bytes<=0) bytes=file.statSize
            PdfRenderer(file).use { pdf ->
                if(pdf.pageCount==0) error("PDF has no pages")
                "PDF · ${pdf.pageCount} pages · scanned pages may need OCR"
            }
        }
    } else {
        pfd.use { file -> if(bytes<=0) bytes=file.statSize }
        val retriever=MediaMetadataRetriever()
        try {
            retriever.setDataSource(context,uri)
            if(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)!="yes")
                error("Selected media has no video track")
            val durationMs=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
                ?: error("Video duration is not readable")
            if(durationMs<1) error("Video duration must be positive")
            val width=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH) ?: "?"
            val height=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT) ?: "?"
            "Video · ${durationMs / 1000}s · ${width}×${height}"
        } finally { retriever.release() }
    }
    if(bytes<=0) error("File size is unknown; select a locally available file")
    val mib=String.format(java.util.Locale.US,"%.1f",bytes/1048576.0)
    return InputInspection(uri,kind,name,bytes,"$detail · $mib MiB")
}

/** Reject example, local-only, credential-bearing, and malformed server URLs. */''','inspection')
p.write_text(s,encoding='utf-8')
p=r/'MainActivity.kt';s=p.read_text(encoding='utf-8')
s=change(s,'    var secret by remember { mutableStateOf("") }','''    var secret by remember { mutableStateOf("") }
    var localInspection by remember { mutableStateOf<InputInspection?>(null) }
    var inspecting by remember { mutableStateOf(false) }''','state')
s=change(s,'''    val videoPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) { enqueueUpload(context,uri,"video") { alert=it }; route="queue";refresh() }
    }
    val pdfPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) { enqueueUpload(context,uri,"pdf") { alert=it }; route="queue";refresh() }
    }''','''    fun inspectSelectedFile(uri: android.net.Uri, kind: String) {
        inspecting=true
        scope.launch {
            try {
                localInspection=withContext(Dispatchers.IO) { inspectInput(context,uri,kind) }
                alert=""
                route=kind
            } catch (e: Exception) {
                localInspection=null
                alert="ဖိုင်စစ်ဆေးမှု မအောင်မြင်ပါ: ${e.message ?: "Unsupported format"}"
            } finally { inspecting=false }
        }
    }
    val videoPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri!=null) inspectSelectedFile(uri,"video")
    }
    val pdfPicker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri!=null) inspectSelectedFile(uri,"pdf")
    }
    fun uploadInspected(kind: String) {
        val info=localInspection ?: return
        if (info.kind != kind) return
        if (!settings.configured()) { route="settings";return }
        enqueueUpload(context,info.uri,kind) { alert=it }
        localInspection=null
        route="queue"
        refresh()
    }''','picker')
s=change(s,'''item {Button(onClick={if(settings.configured()) videoPicker.launch(arrayOf("video/*","application/octet-stream")) else route="settings"},modifier=Modifier.fillMaxWidth()){
                            Icon(Icons.Rounded.VideoFile,contentDescription=null);Spacer(Modifier.width(9.dp));Text(if(settings.configured()) "Video ရွေးပြီး စတင်ရန်" else "AI Server ချိတ်ဆက်ရန်")
                        }}''','''item {Button(onClick={videoPicker.launch(arrayOf("video/*","application/octet-stream"))},enabled=!inspecting,modifier=Modifier.fillMaxWidth()){
                            Icon(Icons.Rounded.VideoFile,contentDescription=null);Spacer(Modifier.width(9.dp));Text(if(inspecting) "ဖိုင်စစ်ဆေးနေသည်" else "Video ဖိုင် ရွေးချယ်ပြီး စစ်ဆေးရန်")
                        }}
                        if(localInspection?.kind=="video") item {
                            Card(colors=CardDefaults.cardColors(containerColor=CardBase)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(localInspection!!.filename,fontWeight=FontWeight.Bold)
                                    Text(localInspection!!.summary,color=Muted)
                                    Spacer(Modifier.height(10.dp))
                                    Text("ဖိုင်ကို ဖုန်းပေါ်မှာပဲ စစ်ဆေးထားတယ်။ AI ဘာသာပြန်ရန် Server လိုအပ်နေဆဲဖြစ်တယ်။",color=Muted)
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick={uploadInspected("video")},modifier=Modifier.fillMaxWidth()) {
                                        Text(if(settings.configured()) "Server သို့တင်ပြီး ဘာသာပြန်စတင်ရန်" else "Server Settings သို့သွားရန်")
                                    }
                                }
                            }
                        }''','video view')
s=change(s,'''item {Button(onClick={if(settings.configured()) pdfPicker.launch(arrayOf("application/pdf")) else route="settings"},modifier=Modifier.fillMaxWidth()){
                            Icon(Icons.Rounded.UploadFile,contentDescription=null);Spacer(Modifier.width(9.dp));Text(if(settings.configured()) "PDF ရွေးပြီး စတင်ရန်" else "AI Server ချိတ်ဆက်ရန်")
                        }}''','''item {Button(onClick={pdfPicker.launch(arrayOf("application/pdf"))},enabled=!inspecting,modifier=Modifier.fillMaxWidth()){
                            Icon(Icons.Rounded.UploadFile,contentDescription=null);Spacer(Modifier.width(9.dp));Text(if(inspecting) "ဖိုင်စစ်ဆေးနေသည်" else "PDF ဖိုင် ရွေးချယ်ပြီး စစ်ဆေးရန်")
                        }}
                        if(localInspection?.kind=="pdf") item {
                            Card(colors=CardDefaults.cardColors(containerColor=CardBase)) {
                                Column(Modifier.padding(16.dp)) {
                                    Text(localInspection!!.filename,fontWeight=FontWeight.Bold)
                                    Text(localInspection!!.summary,color=Muted)
                                    Spacer(Modifier.height(10.dp))
                                    Text("စာမျက်နှာအရေအတွက်ကို ဖုန်းပေါ်မှာ စစ်ဆေးထားတယ်။ OCR/AI ဘာသာပြန်ရန် Server လိုအပ်နေဆဲဖြစ်တယ်။",color=Muted)
                                    Spacer(Modifier.height(12.dp))
                                    Button(onClick={uploadInspected("pdf")},modifier=Modifier.fillMaxWidth()) {
                                        Text(if(settings.configured()) "Server သို့တင်ပြီး ဘာသာပြန်စတင်ရန်" else "Server Settings သို့သွားရန်")
                                    }
                                }
                            }
                        }''','pdf view')
p.write_text(s,encoding='utf-8')
p=Path('android/app/build.gradle.kts');s=p.read_text(encoding='utf-8')
s=change(s,'versionCode = 11','versionCode = 12','versionCode')
s=change(s,'versionName = "1.1.0"','versionName = "1.2.0"','versionName')
p.write_text(s,encoding='utf-8')
print("v1.2 local file inspection installed")
