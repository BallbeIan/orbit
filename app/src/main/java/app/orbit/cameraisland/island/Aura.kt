package app.orbit.cameraisland.island

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.TextView

/**
 * The island's surface treatment. Sits behind all content: a soft glow of the activity's accent colour that spills in
 * from the leading icon, a faint top sheen, and a hairline edge. Because every control is tinted from the same accent,
 * the album art, the progress bar, the buttons and the glow read as one object.
 */
class AuraView(ctx: Context) : View(ctx) {
    private val glow = Paint(Paint.ANTI_ALIAS_FLAG)
    private val sheen = Paint(Paint.ANTI_ALIAS_FLAG)
    private val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dp(1f); color = Color.argb(34, 255, 255, 255) }
    private val clip = Path()
    private val rect = RectF()
    private var shownColor = Color.WHITE
    private var anim: ValueAnimator? = null

    /** Corner radius of the pill this sits in (px), kept in step with the morphing shape. */
    var radius = 0f
        set(v) { field = v; invalidate() }

    /** 0 = plain black. */
    var intensity = 0.3f
        set(v) { if (v != field) { field = v; invalidate() } }
    private var intensityAnim: ValueAnimator? = null

    /** Eases the glow to [target] (e.g. when the pill expands) instead of popping. */
    fun fadeIntensity(target: Float, animate: Boolean) {
        intensityAnim?.cancel()
        if (!animate || !isAttachedToWindow || target == intensity) { intensity = target; return }
        intensityAnim = ValueAnimator.ofFloat(intensity, target).apply {
            duration = 300
            addUpdateListener { intensity = it.animatedValue as Float }
            start()
        }
    }

    fun setAccent(color: Int, animate: Boolean = true) {
        if (color == shownColor) return
        anim?.cancel()
        if (!animate || !isAttachedToWindow) { shownColor = color; invalidate(); return }
        val from = shownColor
        anim = ValueAnimator.ofObject(ArgbEvaluator(), from, color).apply {
            duration = 450
            addUpdateListener { shownColor = it.animatedValue as Int; invalidate() }
            start()
        }
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        if (w <= 0f || h <= 0f) return
        rect.set(0f, 0f, w, h)
        clip.reset(); clip.addRoundRect(rect, radius, radius, Path.Direction.CW)
        c.save(); c.clipPath(clip)
        if (intensity > 0f) {
            // grey / white accents would only muddy the black, so the glow scales with how colourful the accent is
            val hsv = FloatArray(3); Color.colorToHSV(shownColor, hsv)
            val a = (255 * intensity * (0.2f + 0.8f * hsv[1].coerceIn(0f, 1f))).toInt().coerceIn(0, 255)
            val col = Color.argb(a, Color.red(shownColor), Color.green(shownColor), Color.blue(shownColor))
            glow.shader = RadialGradient(w * 0.14f, h * 0.3f, maxOf(w * 0.55f, h * 1.0f), col, Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(rect, glow)
            sheen.shader = LinearGradient(0f, 0f, 0f, h * 0.5f, Color.argb(18, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
            c.drawRect(rect, sheen)
        }
        c.restore()
        val inset = edge.strokeWidth / 2f
        rect.set(inset, inset, w - inset, h - inset)
        c.drawRoundRect(rect, maxOf(0f, radius - inset), maxOf(0f, radius - inset), edge)
    }
}

// ---------------- shared look: colours, type scale, shapes ----------------

val WHITE_70 = Color.argb(178, 255, 255, 255)
val WHITE_50 = Color.argb(128, 255, 255, 255)

fun tint(color: Int, alpha: Int) = Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))

/** Activities whose icon is one of our line glyphs (alerts, charging, volume) rather than an app icon or album art. */
val IslandActivity.isGlyph get() = type in setOf(
    ActivityType.ALERT, ActivityType.BATTERY_ALERT, ActivityType.VOLUME, ActivityType.CHARGING, ActivityType.INDICATOR)

/** The type scale used everywhere inside the island. */
object Type {
    private val medium = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    fun title(v: TextView, sp: Float = 17f) { styleText(v, sp, Color.WHITE); v.typeface = medium; v.letterSpacing = -0.015f }
    fun body(v: TextView, sp: Float = 14f) { styleText(v, sp, WHITE_70); v.letterSpacing = -0.005f }
    fun caption(v: TextView) { styleText(v, 12f, WHITE_50); v.fontFeatureSettings = "tnum" }
    fun number(v: TextView, sp: Float) { styleText(v, sp, Color.WHITE); v.typeface = medium; v.fontFeatureSettings = "tnum"; v.letterSpacing = -0.02f }
}

/**
 * Icon presentation shared by the pill, banner and expanded views: line glyphs sit in a disc tinted with the accent,
 * app icons and album art are rounded squares.
 */
fun styleIconTile(v: android.widget.ImageView, a: IslandActivity, accent: Int, sizeDp: Int) {
    val ctx = v.context
    if (a.isGlyph) {
        v.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(tint(accent, 78)) }
        v.scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
        val p = ctx.dpi((sizeDp * 0.2f).toInt()); v.setPadding(p, p, p, p)
        v.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
    } else {
        roundClip(v, ctx.dp(sizeDp * 0.28f))
        v.scaleType = android.widget.ImageView.ScaleType.CENTER_CROP
        v.setPadding(0, 0, 0, 0)
    }
    v.clipToOutline = true
}

/** Translucent capsule or circle with a hairline edge. */
fun glassShape(ctx: Context, fill: Int, radiusDp: Float = 100f, edge: Boolean = true) = GradientDrawable().apply {
    setColor(fill); cornerRadius = ctx.dp(radiusDp)
    if (edge) setStroke(ctx.dpi(1), Color.argb(26, 255, 255, 255))
}
