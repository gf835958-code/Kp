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
