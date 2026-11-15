package app.orbit.cameraisland.settings

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.Window
import android.widget.ScrollView
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.orbit.cameraisland.island.Icon
import app.orbit.cameraisland.island.IconDrawable
import app.orbit.cameraisland.island.dp
import app.orbit.cameraisland.island.dpi

/** Design tokens and small view helpers shared by every settings screen (iOS-style grouped look). */
object Ui {
    /** Follows the system light / dark setting; call [init] before building any screen. */
    var dark = true
        private set

    fun init(c: Context) {
        dark = when (Prefs.get(c).getString(Prefs.THEME, null)) {
            "light" -> false
            "dark" -> true
            else -> (c.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) !=
                android.content.res.Configuration.UI_MODE_NIGHT_NO
        }
    }

    /** The sun / moon button for the top right corner: switches between light and dark and redraws the screen. */
    fun themeButton(act: Activity) = FrameLayout(act).apply {
        background = ripple(round(act, Color.TRANSPARENT, 24f))
        contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode"
        setOnClickListener {
            Prefs.get(act).edit().putString(Prefs.THEME, if (dark) "light" else "dark").apply()
            act.recreate()
        }
        addView(ImageView(act).apply { setImageDrawable(ThemeIcon(dark, TEXT2)) },
            FrameLayout.LayoutParams(act.dpi(22), act.dpi(22), Gravity.CENTER))
    }

    private fun pick(d: Int, l: Int) = if (dark) d else l
    val BG get() = pick(Color.BLACK, Color.rgb(242, 242, 247))
    val SURFACE get() = pick(Color.rgb(28, 28, 30), Color.WHITE)
    val SURFACE2 get() = pick(Color.rgb(44, 44, 46), Color.rgb(229, 229, 234))
    val DIVIDER get() = pick(Color.rgb(56, 56, 58), Color.rgb(229, 229, 234))
    val TEXT get() = pick(Color.WHITE, Color.BLACK)
    val TEXT2 get() = pick(Color.rgb(142, 142, 147), Color.rgb(138, 138, 142))
    val ACCENT get() = pick(Color.rgb(10, 132, 255), Color.rgb(0, 122, 255))
    val OK get() = pick(Color.rgb(48, 209, 88), Color.rgb(52, 199, 89))
    val WARN get() = pick(Color.rgb(255, 159, 10), Color.rgb(255, 149, 0))
    val BAD get() = pick(Color.rgb(255, 69, 58), Color.rgb(255, 59, 48))
    val SWITCH_OFF_TRACK get() = pick(Color.rgb(57, 57, 61), Color.rgb(233, 233, 234))
    val SLIDER_TRACK get() = pick(Color.rgb(72, 72, 74), Color.rgb(209, 209, 214))

    /** Dark icons on the system bars when the screen is light. */
    @Suppress("DEPRECATION")
    fun systemBars(act: Activity) {
        val w = act.window
        w.setBackgroundDrawable(ColorDrawable(BG))
        w.statusBarColor = Color.TRANSPARENT; w.navigationBarColor = Color.TRANSPARENT
        var f = w.decorView.systemUiVisibility
        val light = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or (if (Build.VERSION.SDK_INT >= 26) View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR else 0)
        f = if (dark) f and light.inv() else f or light
        w.decorView.systemUiVisibility = f
    }

    fun round(c: Context, color: Int, radiusDp: Float) = GradientDrawable().apply {
        setColor(color); cornerRadius = c.dp(radiusDp)
    }

    fun ripple(content: Drawable?): Drawable =
        RippleDrawable(ColorStateList.valueOf(if (dark) Color.argb(30, 255, 255, 255) else Color.argb(22, 0, 0, 0)), content, ColorDrawable(Color.WHITE))

    fun text(c: Context, sp: Float, color: Int, medium: Boolean = false) = TextView(c).apply {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        includeFontPadding = false
        if (medium) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }

    fun chevron(c: Context, left: Boolean = false, color: Int = TEXT2) = ImageView(c).apply {
        setImageDrawable(ChevronDrawable(left, color))
        layoutParams = LinearLayout.LayoutParams(c.dpi(18), c.dpi(18))
    }

    fun lighten(color: Int, by: Float) = Color.rgb(
        (Color.red(color) + (255 - Color.red(color)) * by).toInt(),
        (Color.green(color) + (255 - Color.green(color)) * by).toInt(),
        (Color.blue(color) + (255 - Color.blue(color)) * by).toInt())

    /** App-icon style squircle with a soft top-lit gradient and a white glyph. */
    fun badge(c: Context, icon: Icon, color: Int, sizeDp: Int) = ImageView(c).apply {
        background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(lighten(color, 0.3f), color))
            .apply { cornerRadius = c.dp(sizeDp * 0.29f) }
        setImageDrawable(IconDrawable(icon, Color.WHITE))
        val p = c.dpi((sizeDp * 0.2f).toInt())
        setPadding(p, p, p, p)
    }

    /** Edge-to-edge: content scrolls under the bars, so pad the screen by the system-bar insets. */
    @Suppress("DEPRECATION")
    fun padForInsets(act: Activity, root: View) {
        if (Build.VERSION.SDK_INT >= 30) act.window.setDecorFitsSystemWindows(false)
        systemBars(act)
        root.setOnApplyWindowInsetsListener { v, ins ->
            v.setPadding(0, ins.systemWindowInsetTop, 0, ins.systemWindowInsetBottom)
            ins
        }
        root.requestApplyInsets()
    }

    /** Compact bar: accent back chevron on the left, theme switch on the right. The title is a large heading in the page. */
    fun topBar(act: Activity, backLabel: String = "Orbit", onBack: (() -> Unit)? = null, showTheme: Boolean = true): View {
        val bar = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(act.dpi(8), 0, act.dpi(10), 0)
        }
        val back = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = ripple(round(act, Color.TRANSPARENT, 20f))
            setPadding(act.dpi(4), 0, act.dpi(12), 0)
            setOnClickListener { if (onBack != null) onBack() else act.finish() }
            addView(chevron(act, left = true, color = ACCENT).apply {
                layoutParams = LinearLayout.LayoutParams(act.dpi(24), act.dpi(24))
            })
            addView(text(act, 17f, ACCENT).apply { text = backLabel })
        }
        bar.addView(back, LinearLayout.LayoutParams(-2, act.dpi(40)))
        bar.addView(View(act), LinearLayout.LayoutParams(0, 1, 1f))
        if (showTheme) bar.addView(themeButton(act), LinearLayout.LayoutParams(act.dpi(44), act.dpi(44)))
        return bar.also { it.minimumHeight = act.dpi(48) }
    }

    fun largeTitle(c: Context, title: String) = text(c, 32f, TEXT).apply {
        text = title
        typeface = Typeface.create("sans-serif", Typeface.BOLD)
        letterSpacing = -0.02f
        setPadding(c.dpi(6), c.dpi(4), c.dpi(6), c.dpi(8))
    }

    // ---------------- dialogs ----------------

    private fun baseDialog(act: Activity, bottom: Boolean, content: View): Dialog {
        val d = Dialog(act)
        d.requestWindowFeature(Window.FEATURE_NO_TITLE)
        d.setContentView(content)
        d.window?.apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            val m = act.resources.displayMetrics
            if (bottom) {
                setLayout(-1, -2); setGravity(Gravity.BOTTOM); setWindowAnimations(android.R.style.Animation_InputMethod)
            } else {
                setLayout(minOf(m.widthPixels - act.dpi(48), act.dpi(320)), -2); setGravity(Gravity.CENTER)
            }
            attributes = attributes.apply { dimAmount = 0.5f }
        }
        return d
    }

    /** Bottom sheet with a check mark next to the current option. */
    fun sheet(act: Activity, title: String, labels: List<String>, selected: Int, onPick: (Int) -> Unit) {
        lateinit var dialog: Dialog
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            val r = act.dp(28f)
            background = GradientDrawable().apply { setColor(SURFACE); cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f) }
            setPadding(0, act.dpi(10), 0, act.dpi(20))
        }
        box.addView(View(act).apply { background = round(act, SLIDER_TRACK, 3f) },
            LinearLayout.LayoutParams(act.dpi(36), act.dpi(5)).apply { gravity = Gravity.CENTER_HORIZONTAL; bottomMargin = act.dpi(14) })
        box.addView(text(act, 15f, TEXT2, medium = true).apply { text = title; gravity = Gravity.CENTER; setPadding(act.dpi(20), 0, act.dpi(20), act.dpi(10)) })
        val list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        labels.forEachIndexed { i, label ->
            if (i > 0) list.addView(View(act).apply { setBackgroundColor(DIVIDER) },
                LinearLayout.LayoutParams(-1, 1).apply { marginStart = act.dpi(20) })
            val row = LinearLayout(act).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = act.dpi(52)
                setPadding(act.dpi(20), 0, act.dpi(20), 0)
                background = ripple(null)
                setOnClickListener { dialog.dismiss(); onPick(i) }
                addView(text(act, 17f, TEXT).apply { text = label }, LinearLayout.LayoutParams(0, -2, 1f))
                if (i == selected) addView(ImageView(act).apply { setImageDrawable(IconDrawable(Icon.CHECK, ACCENT)) },
                    LinearLayout.LayoutParams(act.dpi(22), act.dpi(22)))
            }
            list.addView(row)
        }
        val scroll = object : ScrollView(act) {
            override fun onMeasure(w: Int, h: Int) = super.onMeasure(w,
                View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * 0.6f).toInt(), View.MeasureSpec.AT_MOST))
        }.apply { isVerticalScrollBarEnabled = false; addView(list) }
        box.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        dialog = baseDialog(act, true, box)
        dialog.show()
    }

    /** Centred confirmation card with two stacked actions. */
    fun confirm(act: Activity, title: String, message: String, yes: String, no: String, onYes: () -> Unit) {
        lateinit var dialog: Dialog
        val box = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            background = round(act, SURFACE, 26f)
        }
        val body = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setPadding(act.dpi(22), act.dpi(22), act.dpi(22), act.dpi(18)) }
        body.addView(text(act, 18f, TEXT).apply { text = title; typeface = Typeface.create("sans-serif", Typeface.BOLD); gravity = Gravity.CENTER })
        body.addView(text(act, 14f, TEXT2).apply { text = message; setLineSpacing(0f, 1.15f); setPadding(0, act.dpi(10), 0, 0) })
        val scroll = object : ScrollView(act) {
            override fun onMeasure(w: Int, h: Int) = super.onMeasure(w,
                View.MeasureSpec.makeMeasureSpec((resources.displayMetrics.heightPixels * 0.55f).toInt(), View.MeasureSpec.AT_MOST))
        }.apply { isVerticalScrollBarEnabled = false; addView(body) }
        box.addView(scroll, LinearLayout.LayoutParams(-1, -2))
        fun action(label: String, bold: Boolean, run: () -> Unit) {
            box.addView(View(act).apply { setBackgroundColor(DIVIDER) }, LinearLayout.LayoutParams(-1, 1))
            box.addView(text(act, 17f, ACCENT, medium = bold).apply {
                text = label; gravity = Gravity.CENTER; background = ripple(null)
                setOnClickListener { dialog.dismiss(); run() }
            }, LinearLayout.LayoutParams(-1, act.dpi(52)))
        }
        action(yes, true, onYes)
        action(no, false) {}
        dialog = baseDialog(act, false, box)
        dialog.show()
    }
}

/** iOS-style switch: 51 x 31 dp, green when on, white thumb that slides. */
class IosSwitch(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private var f = 0f
    private var anim: ValueAnimator? = null
    var onChange: ((Boolean) -> Unit)? = null

    private var checked = false
    var isChecked: Boolean
        get() = checked
        set(v) { checked = v; anim?.cancel(); f = if (v) 1f else 0f; invalidate() }

    init { isClickable = true; setOnClickListener { toggle() } }

    fun toggle() {
        checked = !checked
        anim?.cancel()
        anim = ValueAnimator.ofFloat(f, if (checked) 1f else 0f).apply {
            duration = 200; addUpdateListener { f = it.animatedValue as Float; invalidate() }; start()
        }
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        onChange?.invoke(checked)
    }

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(context.dpi(51), context.dpi(31))

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        paint.color = ArgbEvaluator().evaluate(f, Ui.SWITCH_OFF_TRACK, Ui.OK) as Int
        rect.set(0f, 0f, w, h); c.drawRoundRect(rect, h / 2, h / 2, paint)
        val pad = context.dp(2f); val r = h / 2 - pad
        val cx = pad + r + f * (w - 2 * (pad + r))
        paint.color = Color.argb(48, 0, 0, 0); c.drawCircle(cx, h / 2 + context.dp(1.2f), r + context.dp(0.4f), paint)
        paint.color = Color.WHITE; c.drawCircle(cx, h / 2, r, paint)
    }
}

class ChevronDrawable(private val left: Boolean, color: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; this.color = color; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        p.strokeWidth = w * 0.12f
        path.reset()
        if (left) { path.moveTo(w * 0.62f, h * 0.2f); path.lineTo(w * 0.34f, h * 0.5f); path.lineTo(w * 0.62f, h * 0.8f) }
        else { path.moveTo(w * 0.38f, h * 0.2f); path.lineTo(w * 0.66f, h * 0.5f); path.lineTo(w * 0.38f, h * 0.8f) }
        c.save(); c.translate(bounds.left.toFloat(), bounds.top.toFloat()); c.drawPath(path, p); c.restore()
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Sun (shown in dark mode: tap for light) or moon (shown in light mode: tap for dark). */
class ThemeIcon(private val dark: Boolean, color: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; strokeCap = Paint.Cap.ROUND }
    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat(); val cx = bounds.exactCenterX(); val cy = bounds.exactCenterY()
        if (dark) {
            p.style = Paint.Style.FILL
            c.drawCircle(cx, cy, w * 0.2f, p)
            p.style = Paint.Style.STROKE; p.strokeWidth = w * 0.08f
            for (i in 0 until 8) {
                val a = Math.toRadians(i * 45.0)
                val (x, y) = Math.cos(a).toFloat() to Math.sin(a).toFloat()
                c.drawLine(cx + x * w * 0.32f, cy + y * w * 0.32f, cx + x * w * 0.45f, cy + y * w * 0.45f, p)
            }
        } else {
            p.style = Paint.Style.FILL
            val moon = Path().apply { addCircle(cx, cy, w * 0.4f, Path.Direction.CW) }
            val bite = Path().apply { addCircle(cx + w * 0.22f, cy - w * 0.16f, w * 0.34f, Path.Direction.CW) }
            moon.op(bite, Path.Op.DIFFERENCE)
            c.drawPath(moon, p)
        }
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}

/** Small stacked up / down chevrons that mark a row which opens a picker. */
class UpDownDrawable(color: Int) : Drawable() {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; this.color = color; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    override fun draw(c: Canvas) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        p.strokeWidth = w * 0.11f
        path.reset()
        path.moveTo(w * 0.32f, h * 0.38f); path.lineTo(w * 0.5f, h * 0.2f); path.lineTo(w * 0.68f, h * 0.38f)
        path.moveTo(w * 0.32f, h * 0.62f); path.lineTo(w * 0.5f, h * 0.8f); path.lineTo(w * 0.68f, h * 0.62f)
        c.save(); c.translate(bounds.left.toFloat(), bounds.top.toFloat()); c.drawPath(path, p); c.restore()
    }
    override fun setAlpha(a: Int) { p.alpha = a }
    override fun setColorFilter(cf: ColorFilter?) { p.colorFilter = cf }
    @Deprecated("Deprecated in Java")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
