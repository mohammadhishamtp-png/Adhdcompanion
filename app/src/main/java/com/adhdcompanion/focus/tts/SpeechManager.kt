package com.adhdcompanion.focus.tts

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.speech.tts.TextToSpeech
import android.util.Base64
import android.util.Log
import com.adhdcompanion.focus.data.prefs.SecurePrefsManager
import com.adhdcompanion.focus.data.remote.GeminiRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class SpeechManager(
    private val context: Context,
    private val geminiRepository: GeminiRepository,
    private val prefs: SecurePrefsManager
) {
    private var androidTts: TextToSpeech? = null
    private var androidTtsReady = false
    private var mediaPlayer: MediaPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main)

    init {
        androidTts = TextToSpeech(context.applicationContext) { status ->
            androidTtsReady = status == TextToSpeech.SUCCESS
            if (androidTtsReady) {
                androidTts?.language = Locale.getDefault()
                androidTts?.setSpeechRate(0.98f)
            }
        }
    }

    fun speak(text: String, onDone: (() -> Unit)? = null) {
        if (text.isBlank()) {
            onDone?.invoke()
            return
        }
        val wantsGemini = prefs.voicePreference == SecurePrefsManager.VOICE_GEMINI_WITH_FALLBACK &&
            prefs.hasApiKey()

        if (!wantsGemini) {
            speakWithAndroidTts(text, onDone)
            return
        }

        scope.launch {
            val audio = runCatching { geminiRepository.generateSpeechAudio(text) }.getOrNull()
            if (audio?.data != null) {
                val played = playBase64Audio(audio.data, audio.mimeType ?: "audio/l16;rate=24000", onDone)
                if (!played) speakWithAndroidTts(text, onDone)
            } else {
                speakWithAndroidTts(text, onDone)
            }
        }
    }

    private fun speakWithAndroidTts(text: String, onDone: (() -> Unit)?) {
        val tts = androidTts
        if (tts == null || !androidTtsReady) {
            onDone?.invoke()
            return
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "briefing_utterance")
        if (onDone != null) {
            val estimatedMs = (text.length * 55L).coerceIn(800L, 15000L)
            android.os.Handler(context.mainLooper).postDelayed({ onDone() }, estimatedMs)
        }
    }

    private fun playBase64Audio(base64Data: String, mimeType: String, onDone: (() -> Unit)?): Boolean {
        return try {
            val rawBytes = Base64.decode(base64Data, Base64.DEFAULT)
            val lowerMime = mimeType.lowercase(Locale.ROOT)
            val isRawPcm = lowerMime.contains("l16") || lowerMime.contains("pcm")
            val sampleRate = Regex("rate=(\\d+)").find(lowerMime)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 24000

            val fileBytes = if (isRawPcm) {
                pcm16MonoToWav(rawBytes, sampleRate)
            } else {
                rawBytes
            }
            val extension = if (isRawPcm || lowerMime.contains("wav")) "wav" else "mp3"
            val tempFile = File.createTempFile("gemini_tts_", ".$extension", context.cacheDir)
            FileOutputStream(tempFile).use { it.write(fileBytes) }

            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ASSISTANT)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(tempFile.absolutePath)
                setOnCompletionListener {
                    tempFile.delete()
                    onDone?.invoke()
                }
                setOnErrorListener { _, _, _ ->
                    tempFile.delete()
                    false
                }
                prepare()
                start()
            }
            true
        } catch (e: Exception) {
            Log.w("SpeechManager", "Gemini audio playback failed, falling back", e)
            false
        }
    }

    private fun pcm16MonoToWav(pcmData: ByteArray, sampleRate: Int): ByteArray {
        val channels = 1
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val dataSize = pcmData.size
        val chunkSize = 36 + dataSize

        val out = ByteArrayOutputStream(44 + dataSize)
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray(Charsets.US_ASCII))
        header.putInt(chunkSize)
        header.put("WAVE".toByteArray(Charsets.US_ASCII))
        header.put("fmt ".toByteArray(Charsets.US_ASCII))
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort(blockAlign.toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray(Charsets.US_ASCII))
        header.putInt(dataSize)
        out.write(header.array())
        out.write(pcmData)
        return out.toByteArray()
    }

    fun stop() {
        androidTts?.stop()
        mediaPlayer?.let {
            runCatching { it.stop() }
            it.release()
        }
        mediaPlayer = null
    }

    fun release() {
        stop()
        androidTts?.shutdown()
        androidTts = null
    }
}
