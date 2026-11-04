package com.oscar.linker

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import java.util.Random
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sin

/**
 * أصوات التطبيق تُولَّد بالكود مباشرة (بدون ملفات صوت):
 *  - cash : صوت مال قصير عند تنفيذ صفقة (كاش)
 *  - win  : رنين عملات عند انتهاء الجلسة بربح
 *  - loss : نغمة حزينة هابطة عند انتهاء الجلسة بخسارة
 */
class SoundFx {
    private val rate = 22050
    private val main = Handler(Looper.getMainLooper())

    private fun mix(buf: ShortArray, freq: Double, startMs: Int, lenMs: Int, vol: Double, decay: Double) {
        val off = rate * startMs / 1000
        val n = rate * lenMs / 1000
        for (i in 0 until n) {
            val idx = off + i
            if (idx >= buf.size) break
            val t = i.toDouble() / rate
            val env = exp(-decay * t) * min(1.0, i / (rate * 0.004))
            val s = sin(2 * PI * freq * t) + 0.35 * sin(2 * PI * freq * 2.01 * t) + 0.12 * sin(2 * PI * freq * 3.03 * t)
            val v = (s * env * vol * 8000).toInt()
            buf[idx] = (buf[idx] + v).coerceIn(-30000, 30000).toShort()
        }
    }

    private fun click(buf: ShortArray, startMs: Int, lenMs: Int, vol: Double) {
        val r = Random(7)
        val off = rate * startMs / 1000
        val n = rate * lenMs / 1000
        for (i in 0 until n) {
            val idx = off + i
            if (idx >= buf.size) break
            val env = exp(-90.0 * i / rate)
            val v = ((r.nextDouble() * 2 - 1) * env * vol * 9000).toInt()
            buf[idx] = (buf[idx] + v).coerceIn(-30000, 30000).toShort()
        }
    }

    private val cashData: ShortArray by lazy {
        val b = ShortArray(rate * 900 / 1000)
        click(b, 0, 30, 0.8)
        mix(b, 1760.0, 0, 160, 0.55, 12.0)
        mix(b, 2349.0, 85, 700, 0.60, 5.0)
        mix(b, 3136.0, 85, 500, 0.22, 7.0)
        b
    }

    private val winData: ShortArray by lazy {
        val b = ShortArray(rate * 1500 / 1000)
        val notes = doubleArrayOf(1047.0, 1319.0, 1568.0, 2093.0)
        for (i in notes.indices) {
            mix(b, notes[i], i * 110, 420, 0.5, 5.5)
            click(b, i * 110, 18, 0.35)
        }
        for (f in notes) mix(b, f, 470, 900, 0.30, 3.2)
        mix(b, 4186.0, 470, 700, 0.12, 4.0)
        b
    }

    private val lossData: ShortArray by lazy {
        val b = ShortArray(rate * 1700 / 1000)
        mix(b, 311.0, 0, 380, 0.75, 4.0)
        mix(b, 294.0, 380, 380, 0.75, 4.0)
        mix(b, 277.0, 760, 380, 0.75, 4.0)
        mix(b, 220.0, 1140, 560, 0.85, 2.6)
        mix(b, 110.0, 1140, 560, 0.45, 2.6)
        b
    }

    private fun play(data: ShortArray) {
        try {
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .build()
                )
                .setBufferSizeInBytes(data.size * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(data, 0, data.size)
            track.play()
            val ms = data.size * 1000L / rate + 300
            main.postDelayed({ try { track.release() } catch (_: Exception) {} }, ms)
        } catch (_: Exception) {
        }
    }

    fun cash() = play(cashData)
    fun win() = play(winData)
    fun loss() = play(lossData)
}
