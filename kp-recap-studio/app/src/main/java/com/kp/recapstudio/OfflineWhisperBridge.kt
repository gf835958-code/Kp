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
 * Offline multilingual Whisper tiny speech transcription.
 *
 * Speech from LONG videos is split into <=27s 16kHz PCM WAV chunks
 * and processed sequentially with a SINGLE loaded Whisper model.
 * This limits per-inference input size and lets the UI show progress.
 *
 * Accuracy for Burmese, music, overlapping voices and chunk boundaries
 * is not guaranteed; this is not video visual understanding.
 */
object OfflineWhisperBridge {
    interface Callback {
        fun onText(text: String)
        fun onProgress(completedChunks: Int, totalChunks: Int)
        fun onError(message: String)
    }

    @JvmStatic
    fun transcribeAsync(context: Context, wav: File, callback: Callback) {
        val app = context.applicationContext
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            var scratchFile: File? = null
            try {
                val working = File.createTempFile(
                    "kp-whisper-segment-", ".wav", app.cacheDir
                )
                scratchFile = working
                if (!wav.isFile || wav.length() < 100L) {
                    throw IllegalArgumentException("Extracted video audio WAV does not exist")
                }
                val total = OfflineWavChunker.count(
                    wav, OfflineWavChunker.DEFAULT_SECONDS
                )
                if (total < 1) throw IllegalArgumentException("Empty video speech WAV")
                // Exact audio-content SHA-256 keeps resume checkpoints from
                // accidentally applying to a different movie or soundtrack.
                val checkpointDir = OfflineTranscriptCheckpoints.jobDirectory(app, wav)
                val cached = (0 until total).map {
                    OfflineTranscriptCheckpoints.read(checkpointDir, it)
                }
                val transcription = StringBuilder()
                if (cached.all { it != null }) {
                    // Fully processed earlier: avoid loading the native AI model.
                    for (part in cached) {
                        val recognized = part.orEmpty().trim()
                        if (recognized.isNotEmpty()) {
                            if (transcription.isNotEmpty()) transcription.append('\n')
                            transcription.append(recognized)
                        }
                    }
                    withContext(Dispatchers.Main) {
                        callback.onProgress(total, total)
                    }
                } else {
                    val model = Whisper.loadModelFromAsset(
                        app, "models/ggml-tiny.bin"
                    )
                    try {
                        for (index in 0 until total) {
                            var recognized = cached[index]
                            if (recognized == null) {
                                OfflineWavChunker.writeChunk(
                                    wav, working, index,
                                    OfflineWavChunker.DEFAULT_SECONDS
                                )
                                val result = Whisper.transcribe(
                                    model, working.absolutePath,
                                    WhisperConfig(language = "my")
                                )
                                recognized = result.text.trim()
                                // Flush atomically AFTER successful inference.
                                // Empty recognized text also counts as completed.
                                OfflineTranscriptCheckpoints.write(
                                    checkpointDir, index, recognized
                                )
                            }
                            if (recognized.isNotEmpty()) {
                                if (transcription.isNotEmpty()) transcription.append('\n')
                                transcription.append(recognized)
                            }
                            withContext(Dispatchers.Main) {
                                callback.onProgress(index + 1, total)
                            }
                        }
                    } finally {
                        Whisper.releaseModel(model)
                    }
                }
                withContext(Dispatchers.Main) {
                    callback.onText(transcription.toString().trim())
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    callback.onError(e.message ?: "Offline Whisper model inference failed")
                }
            } catch (e: LinkageError) {
                withContext(Dispatchers.Main) {
                    callback.onError("Offline native Whisper runtime unavailable: " + e.message)
                }
            } finally {
                scratchFile?.delete()
            }
        }
    }
}
