package com.oscar.linker

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.view.View
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * خلفية شموع تداول متحركة بسلاسة، مرسومة بالكود بالكامل (بدون صور وبدون أصوات).
 * الحركة تعتمد على الزمن الفعلي بين الإطارات، فتبقى ناعمة على أي جهاز.
 */
class CandleBackgroundView(context: Context) : View(context) {

    private class Candle(var o: Float, var h: Float, var l: Float, var c: Float)

    private val rnd = Random()
    private val candles = ArrayList<Candle>()

    private val density = resources.displayMetrics.density
    private val spacing = 16f * density          // المسافة بين الشموع
    private val bodyW = 9f * density             // عرض جسم الشمعة
    private val speed = 14f * density            // سرعة الحركة (بكسل/ثانية)

    private var offset = 0f
    private var lastNanos = 0L
    private var timeSec = 0f

    // الشمعة الحية: تتحرك إغلاقها بنعومة نحو هدف يتغير كل لحظات
    private var liveTarget = 0f
    private var liveRetarget = 0f

    // نطاق العرض (يتكيف بنعومة مع الأسعار الظاهرة)
    private var viewMin = 0f
    private var viewMax = 1f

    private val green = Color.parseColor("#26d0a0")
    private val red = Color.parseColor("#ff5c6c")
    private val gold = Color.parseColor("#d4af37")

    private val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val wickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.6f * density
        strokeCap = Paint.Cap.ROUND
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
        color = Color.parseColor("#12ffffff")
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        color = Color.parseColor("#66d4af37")
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val scrimPaint = Paint()
    private val rect = RectF()
    private val path = Path()

    private var scrimShader: LinearGradient? = null

    private var imageMode = false

    /** عند وجود صورة خلفية من المدير نخفّف الطبقة المعتمة حتى تظهر الصورة. */
    fun setImageMode(on: Boolean) {
        imageMode = on
        buildScrim(height)
        invalidate()
    }

    private fun buildScrim(h: Int) {
        if (h <= 0) return
        val cols = if (imageMode)
            intArrayOf(Color.parseColor("#9E070b14"), Color.parseColor("#66070b14"), Color.parseColor("#B8070b14"))
        else
            intArrayOf(Color.parseColor("#E6070b14"), Color.parseColor("#99070b14"), Color.parseColor("#F2070b14"))
        scrimShader = LinearGradient(0f, 0f, 0f, h.toFloat(), cols, floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP)
        scrimPaint.shader = scrimShader
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        buildScrim(h)
        seed(w)
    }

    private fun seed(w: Int) {
        candles.clear()
        val count = (w / spacing).toInt() + 6
        var price = 0.5f
        for (i in 0 until count) {
            val c = nextCandle(price)
            candles.add(c)
            price = c.c
        }
        liveTarget = price
        viewMin = candles.minOf { it.l } - 0.05f
        viewMax = candles.maxOf { it.h } + 0.05f
    }

    private fun nextCandle(prev: Float): Candle {
        // مشي عشوائي مع ميل للعودة نحو الوسط حتى لا يهرب السعر خارج الشاشة
        val pull = (0.5f - prev) * 0.12f
        val step = (rnd.nextFloat() - 0.5f) * 0.16f + pull
        val o = prev
        val c = o + step
        val top = max(o, c) + rnd.nextFloat() * 0.05f
        val bot = min(o, c) - rnd.nextFloat() * 0.05f
        return Candle(o, top, bot, c)
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f || candles.isEmpty()) return

        val now = System.nanoTime()
        var dt = if (lastNanos == 0L) 0.016f else (now - lastNanos) / 1_000_000_000f
        lastNanos = now
        if (dt > 0.05f) dt = 0.05f     // لا نقفز إذا توقف التطبيق قليلاً
        timeSec += dt

        advance(dt)
        drawGlow(canvas, w, h)
        drawGrid(canvas, w, h)
        drawCandles(canvas, w, h)
        canvas.drawRect(0f, 0f, w, h, scrimPaint)

        postInvalidateOnAnimation()
    }

    private fun advance(dt: Float) {
        offset += speed * dt

        // الشمعة الحية تتحرك بسلاسة
        val live = candles[candles.size - 1]
        liveRetarget -= dt
        if (liveRetarget <= 0f) {
            liveRetarget = 0.35f + rnd.nextFloat() * 0.6f
            val pull = (0.5f - live.c) * 0.05f
            liveTarget = live.c + (rnd.nextFloat() - 0.5f) * 0.12f + pull
        }
        live.c += (liveTarget - live.c) * min(1f, dt * 2.4f)
        live.h = max(live.h, max(live.o, live.c))
        live.l = min(live.l, min(live.o, live.c))

        // عندما تتحرك مسافة شمعة كاملة نغلق الشمعة الحية ونبدأ جديدة
        while (offset >= spacing) {
            offset -= spacing
            candles.removeAt(0)
            val last = candles[candles.size - 1]
            candles.add(nextCandle(last.c))
        }

        // تكيّف النطاق بنعومة
        var lo = Float.MAX_VALUE
        var hi = -Float.MAX_VALUE
        for (c in candles) {
            lo = min(lo, c.l)
            hi = max(hi, c.h)
        }
        val k = min(1f, dt * 1.2f)
        viewMin += ((lo - 0.06f) - viewMin) * k
        viewMax += ((hi + 0.06f) - viewMax) * k
    }

    private fun yOf(v: Float, h: Float): Float {
        val top = h * 0.14f
        val bottom = h * 0.86f
        val span = max(0.0001f, viewMax - viewMin)
        return bottom - (v - viewMin) / span * (bottom - top)
    }

    private fun drawGlow(canvas: Canvas, w: Float, h: Float) {
        val cx1 = w * (0.25f + 0.1f * sin(timeSec * 0.21f))
        val cy1 = h * (0.30f + 0.06f * sin(timeSec * 0.17f))
        glowPaint.shader = RadialGradient(
            cx1, cy1, max(w, h) * 0.55f,
            Color.parseColor("#26d4af37"), Color.parseColor("#00d4af37"), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, glowPaint)

        val cx2 = w * (0.80f + 0.08f * sin(timeSec * 0.15f + 1.7f))
        val cy2 = h * (0.75f + 0.07f * sin(timeSec * 0.19f + 0.6f))
        glowPaint.shader = RadialGradient(
            cx2, cy2, max(w, h) * 0.5f,
            Color.parseColor("#2226d0a0"), Color.parseColor("#0026d0a0"), Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, w, h, glowPaint)
        glowPaint.shader = null
    }

    private fun drawGrid(canvas: Canvas, w: Float, h: Float) {
        for (i in 1..6) {
            val y = h * i / 7f
            canvas.drawLine(0f, y, w, y, gridPaint)
        }
        // خطوط عمودية تتحرك مع الشموع
        var x = w - (offset % (spacing * 4f))
        while (x > -spacing * 4f) {
            canvas.drawLine(x, 0f, x, h, gridPaint)
            x -= spacing * 4f
        }
    }

    private fun drawCandles(canvas: Canvas, w: Float, h: Float) {
        val n = candles.size
        path.reset()
        var first = true
        for (i in 0 until n) {
            val c = candles[i]
            val x = w - (n - 1 - i) * spacing - offset - spacing * 1.5f
            if (x < -spacing || x > w + spacing) continue

            val up = c.c >= c.o
            val col = if (up) green else red
            val alpha = if (i == n - 1) 235 else 170

            wickPaint.color = col
            wickPaint.alpha = alpha
            bodyPaint.color = col
            bodyPaint.alpha = alpha

            val yo = yOf(c.o, h)
            val yc = yOf(c.c, h)
            val yh = yOf(c.h, h)
            val yl = yOf(c.l, h)
            canvas.drawLine(x, yh, x, yl, wickPaint)

            val top = min(yo, yc)
            val bot = max(yo, yc)
            val bh = max(bot - top, 2f * density)
            rect.set(x - bodyW / 2f, top, x + bodyW / 2f, top + bh)
            canvas.drawRoundRect(rect, 2f * density, 2f * density, bodyPaint)

            if (first) { path.moveTo(x, yc); first = false } else path.lineTo(x, yc)
        }
        canvas.drawPath(path, linePaint)
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        lastNanos = 0L
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        lastNanos = 0L
        postInvalidateOnAnimation()
    }

    @Suppress("unused")
    private fun spread() = abs(viewMax - viewMin)
}
