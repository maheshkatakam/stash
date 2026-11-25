package com.maheshkatakam.stash.audio

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.os.SystemClock
import java.io.File

/** Records AAC voice notes (.m4a), the same format phones use for voice memos. */
class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    val isRecording: Boolean get() = recorder != null

    fun start(output: File): Boolean {
        var r: MediaRecorder? = null
        return try {
            r = if (Build.VERSION.SDK_INT >= 31) MediaRecorder(context) else MediaRecorder()
            r.setAudioSource(MediaRecorder.AudioSource.MIC)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioEncodingBitRate(128_000)
            r.setAudioSamplingRate(44_100)
            r.setOutputFile(output.absolutePath)
            r.prepare()
            r.start()
            recorder = r
            file = output
            startedAt = SystemClock.elapsedRealtime()
            true
        } catch (e: Exception) {
            runCatching { r?.release() }
            output.delete()
            false
        }
    }

    /** 0..1 loudness for the level meter. */
    fun level(): Float = try {
        ((recorder?.maxAmplitude ?: 0) / 32767f).coerceIn(0f, 1f)
    } catch (e: Exception) {
        0f
    }

    fun elapsedMs(): Long = if (recorder != null) SystemClock.elapsedRealtime() - startedAt else 0L

    /** Stops and keeps the file. Returns its length in ms, or null if nothing usable was recorded. */
    fun stop(): Long? {
        val r = recorder ?: return null
        recorder = null
        val duration = SystemClock.elapsedRealtime() - startedAt
        return try {
            r.stop()
            duration
        } catch (e: RuntimeException) {
            file?.delete()
            null
        } finally {
            runCatching { r.release() }
        }
    }

    /** Stops and throws the recording away. */
    fun cancel() {
        val r = recorder ?: return
        recorder = null
        runCatching { r.stop() }
        runCatching { r.release() }
        file?.delete()
    }
}
