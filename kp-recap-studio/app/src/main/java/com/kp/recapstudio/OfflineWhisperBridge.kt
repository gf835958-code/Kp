package com.kp.recapstudio

import android.content.Context
import dev.ffmpegkit.whisper.Whisper
import dev.ffmpegkit.whisper.WhisperConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Official free-tier Whisper Android AAR, bundled with a pinned multilingual
 * tiny model. Native inference runs on-device. The bundled tiny model can
 * transcribe Burmese, but language accuracy requires real device testing.
 */
object OfflineWhisperBridge {
    interface Callback {
        fun onText(text: String)
        fun onError(message: String)
    }

    @JvmStatic
    fun transcribeAsync(context: Context, wav: File, callback: Callback) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                if (!wav.isFile || wav.length() < 100L) {
                    throw IllegalArgumentException("Extracted video audio WAV does not exist")
                }
                val model = Whisper.loadModelFromAsset(app, "models/ggml-tiny.bin")
                try {
                    val result = Whisper.transcribe(
                        model,
                        wav.absolutePath,
                        WhisperConfig(language = "my")
                    )
                    withContext(Dispatchers.Main) {
                        callback.onText(result.text.trim())
                    }
                } finally {
                    Whisper.releaseModel(model)
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e.message ?: "Offline Whisper model inference failed")
                }
            } catch (e: LinkageError) {
                withContext(Dispatchers.Main) {
                    callback.onError("Offline native Whisper runtime unavailable: " + e.message)
                }
            }
        }
    }
}
