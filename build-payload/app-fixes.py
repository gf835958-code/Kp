from pathlib import Path

root = Path("android/app/src/main/java/com/kp/autommsub")
activity = root / "MainActivity.kt"
s = activity.read_text(encoding="utf-8")
fixes = {
    'OutlinedButton(onClick={refresh},': 'OutlinedButton(onClick={refresh()},',
    'upload.inputData.getString("name")': 'upload.progress.getString("name")',
}
for old, new in fixes.items():
    assert old in s, f"Unexpected source: {old}"
    s = s.replace(old, new, 1)
activity.write_text(s, encoding="utf-8")

network = root / "Network.kt"
s = network.read_text(encoding="utf-8")
old = 'setProgress(workDataOf("percent" to ((chunk+1)*100/total), "remoteId" to remoteId))'
new = 'setProgress(workDataOf("percent" to ((chunk+1)*100/total), "remoteId" to remoteId, "name" to name))'
assert old in s, "Unexpected upload progress source"
network.write_text(s.replace(old, new, 1), encoding="utf-8")
print("Kotlin compilation fixes applied to verified source")


# v1.1 video-guided UI and endpoint validation fixes.
from pathlib import Path
p=Path("android/app/src/main/java/com/kp/autommsub/MainActivity.kt")
s=p.read_text(encoding="utf-8")
def change(old,new):
    global s
    assert s.count(old)==1,(old,s.count(old))
    s=s.replace(old,new,1)
change('if(!settings.configured() && route!="settings")','if(!settings.configured() && route=="home")')
change('onClick={videoPicker.launch(arrayOf("video/*","application/octet-stream"))},enabled=settings.configured()', 'onClick={if(settings.configured()) videoPicker.launch(arrayOf("video/*","application/octet-stream")) else route="settings"}')
change('onClick={pdfPicker.launch(arrayOf("application/pdf"))},enabled=settings.configured()', 'onClick={if(settings.configured()) pdfPicker.launch(arrayOf("application/pdf")) else route="settings"}')
change('Text("Video ရွေးပြီး စတင်ရန်")','Text(if(settings.configured()) "Video ရွေးပြီး စတင်ရန်" else "AI Server ချိတ်ဆက်ရန်")')
change('Text("PDF ရွေးပြီး စတင်ရန်")','Text(if(settings.configured()) "PDF ရွေးပြီး စတင်ရန်" else "AI Server ချိတ်ဆက်ရန်")')
change('Text("Real AI translation အတွက် HTTPS backend နဲ့ access token ကို Settings မှာ ထည့်ပါ။",color=Color.White)', 'Text("AI ဘာသာပြန်ရန် တကယ့် HTTPS Server လိုအပ်ပါတယ်။ နမူနာ URL က အလုပ်မလုပ်ပါ။",color=Color.White)')
change('Text("Auto MMSub ကို အသုံးပြုဖို့ ကိုယ်ပိုင် HTTPS processing backend လိုပါတယ်။ API token ကို Android Keystore နဲ့ encrypted သိမ်းမယ်။",color=Muted)', 'Text("AI ဘာသာပြန်ရန် တကယ့် HTTPS Server နဲ့ access token လိုပါတယ်။ Example URL တစ်ခုတည်းနဲ့ ဘာသာမပြန်နိုင်ပါ။",color=Muted)')
change('''if(!base.trim().startsWith("https://")) alert="Only HTTPS backend endpoints are accepted"
                            else {settings.baseUrl=base;if(secret.isNotBlank()) settings.saveToken(secret);secret="";alert="Settings saved";refresh()}''','''if(!isValidBackendUrl(base)) alert="HTTPS Server URL အစစ် ထည့်ပါ။ နမူနာ URL မရပါ။"
                            else if(secret.isBlank() && settings.token().isBlank()) alert="Server API Token လိုအပ်ပါတယ်။"
                            else {
                                settings.baseUrl=base
                                if(secret.isNotBlank()) settings.saveToken(secret)
                                secret=""
                                scope.launch {
                                    try {
                                        withContext(Dispatchers.IO) {
                                            Api(settings).json("GET","/v1/preflight")
                                            Api(settings).allJobs()
                                        }
                                        alert="Server API ချိတ်ဆက်မှု အောင်မြင်သည်။ AI models ကို ထပ်စစ်ပါ။"
                                        refresh()
                                    } catch(e:Exception) {
                                        alert="Server မချိတ်နိုင်ပါ: " + (e.message ?: "Network error")
                                    }
                                }
                            }''')
p.write_text(s,encoding="utf-8")
p=Path("android/app/src/main/java/com/kp/autommsub/Network.kt")
s=p.read_text(encoding="utf-8")
a='/** Token is AES-GCM encrypted using a non-exportable key in AndroidKeyStore. */'
assert s.count(a)==1
s=s.replace(a,'''/** Reject example, local-only, credential-bearing, and malformed server URLs. */
fun isValidBackendUrl(value: String): Boolean = try {
    val uri=java.net.URI(value.trim())
    val host=uri.host?.lowercase() ?: ""
    uri.scheme.equals("https",ignoreCase=true) && host.contains('.') &&
        uri.rawUserInfo==null && uri.rawQuery==null && uri.rawFragment==null &&
        host!="localhost" && host!="127.0.0.1" && host!="0.0.0.0" &&
        !host.endsWith(".example") && !host.endsWith(".example.com") &&
        !value.any { it.isWhitespace() }
} catch (_: Exception) { false }

/** Token is AES-GCM encrypted using a non-exportable key in AndroidKeyStore. */''',1)
a='fun configured(): Boolean = baseUrl.startsWith("https://") && token().isNotBlank()'
assert s.count(a)==1
s=s.replace(a,'fun configured(): Boolean = isValidBackendUrl(baseUrl) && token().isNotBlank()',1)
p.write_text(s,encoding="utf-8")
p=Path("android/app/build.gradle.kts")
s=p.read_text(encoding="utf-8")
assert 'versionCode = 10' in s and 'versionName = "1.0.0"' in s
p.write_text(s.replace('versionCode = 10','versionCode = 11').replace('versionName = "1.0.0"','versionName = "1.1.0"'),encoding="utf-8")
print("v1.1 video-guided fixes installed")
