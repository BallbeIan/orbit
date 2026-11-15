package app.orbit.cameraisland.settings

import android.animation.ValueAnimator
import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import app.orbit.cameraisland.island.ActionButton
import app.orbit.cameraisland.island.ActivityType
import app.orbit.cameraisland.island.AuraView
import app.orbit.cameraisland.island.BannerView
import app.orbit.cameraisland.island.BatteryDrawable
import app.orbit.cameraisland.island.CollapsedView
import app.orbit.cameraisland.island.GenericExpandedView
import app.orbit.cameraisland.island.Icon
import app.orbit.cameraisland.island.IconDrawable
import app.orbit.cameraisland.island.IslandActivity
import app.orbit.cameraisland.island.Kind
import app.orbit.cameraisland.island.MediaExpandedView
import app.orbit.cameraisland.island.MediaInfo
import app.orbit.cameraisland.island.RingView
import app.orbit.cameraisland.island.Trailing
import app.orbit.cameraisland.island.VolumeInfo
import app.orbit.cameraisland.island.WHITE_70
import app.orbit.cameraisland.island.dp
import app.orbit.cameraisland.island.dpi
import app.orbit.cameraisland.island.vivid
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The settings app's centrepiece: a live island on a wallpaper, built from the island's *real* views, so what you see
 * here is what the island looks like. It follows every look / position setting instantly and morphs between the
 * situations the island handles (tap it to try the next one).
 */
class IslandPreviewCard(private val act: Activity) : FrameLayout(act), SharedPreferences.OnSharedPreferenceChangeListener {

    enum class Demo(val label: String) {
        MUSIC_PILL("Music"), MESSAGE("Message"), CHARGING("Charging"), VOLUME("Volume"),
        TIMER("Timer"), MUSIC_OPEN("Music, expanded"),
    }

    private val prefs = Prefs.get(act)
    private val scopeKey: String? by lazy { ScreenProfile.scopeOrNull(act) }
    private val sp = ScreenPrefs(prefs) { scopeKey }
    private val handler = Handler(Looper.getMainLooper())

    // ---- the pill ----
    private val pillBg = GradientDrawable()
    private val aura = AuraView(act)
    private val collapsed = CollapsedView(act)
    private val banner = BannerView(act)
    private val mediaExp = MediaExpandedView(act)
    private val genericExp = GenericExpandedView(act, {}, {})
    private val contentViews = listOf<View>(collapsed, banner, mediaExp, genericExp)
    private val pill = object : FrameLayout(act) {
        override fun onInterceptTouchEvent(e: MotionEvent) = true   // the real views must not react in a preview
    }
    private val secondary = FrameLayout(act)
    private val secondaryBg = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.BLACK) }
    private val secondaryRing = RingView(act)

    // ---- chrome ----
    private val footerLabel = TextView(act)
    private val master = IosSwitch(act)

    private var demo = Demo.MUSIC_PILL
    private var started = false
    private var curW = 0
    private var curH = 0
    private var curR = 0f
    private var anim: ValueAnimator? = null
    private var shown: View? = null
    private var topInset = 0

    private val wall = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val ease = PathInterpolator(0.22f, 1f, 0.36f, 1f)

    private val ticker = object : Runnable {
        override fun run() {
            collapsed.tick(); mediaExp.tick(); genericExp.tick()
            handler.postDelayed(this, 500)
        }
    }

    init {
        setWillNotDraw(false)
        background = GradientDrawable().apply {
            val r = act.dp(28f)
            cornerRadii = floatArrayOf(0f, 0f, 0f, 0f, r, r, r, r)
        }
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = true

        pill.background = pillBg
        pill.outlineProvider = ViewOutlineProvider.BACKGROUND
        pill.clipToOutline = true
        pill.isClickable = true
        pill.setOnClickListener { next() }
        pill.addView(aura, FrameLayout.LayoutParams(-1, -1))
        collapsed.setPadding(act.dpi(6), 0, act.dpi(12), 0)
        for (v in contentViews) { v.visibility = GONE; pill.addView(v, FrameLayout.LayoutParams(-1, -1)) }
        addView(pill, LayoutParams(act.dpi(100), act.dpi(36), Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        secondary.background = secondaryBg
        secondary.addView(secondaryRing, LayoutParams(-1, -1).apply { val m = act.dpi(5); setMargins(m, m, m, m) })
        secondaryRing.color = Color.rgb(255, 159, 10); secondaryRing.fraction = 0.7f
        addView(secondary, LayoutParams(act.dpi(36), act.dpi(36), Gravity.TOP or Gravity.CENTER_HORIZONTAL))

        // footer: what is being shown, and the master switch
        footerLabel.setTextColor(WHITE_70); footerLabel.textSize = 13f
        addView(footerLabel, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.START).apply { setMargins(act.dpi(20), 0, 0, act.dpi(15)) })
        val chip = LinearLayout(act).apply {
            gravity = Gravity.CENTER_VERTICAL
            background = GradientDrawable().apply { setColor(Color.argb(46, 255, 255, 255)); cornerRadius = act.dp(20f) }
            setPadding(act.dpi(14), act.dpi(4), act.dpi(6), act.dpi(4))
            addView(TextView(act).apply { text = "Island"; setTextColor(Color.WHITE); textSize = 14f }, LinearLayout.LayoutParams(-2, -2).apply { marginEnd = act.dpi(10) })
            addView(master, LinearLayout.LayoutParams(act.dpi(51), act.dpi(31)))
        }
        master.isChecked = prefs.getBoolean(Prefs.ENABLED, true)
        master.onChange = { prefs.edit().putBoolean(Prefs.ENABLED, it).apply() }
        addView(chip, LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.END).apply { setMargins(0, 0, act.dpi(14), act.dpi(8)) })
        setOnClickListener { next() }
        applyLook()
    }

    // ---------------- public ----------------

    /** Room above the card for the real status bar (the wallpaper runs underneath it, like the real screen). */
    fun setTopInset(px: Int) { topInset = px; requestLayout() }

    fun setDemo(d: Demo, animate: Boolean = true) {
        if (started && d == demo) return
        demo = d
        val a = build(d)
        val target = viewFor(d)
        bind(d, a)
        footerLabel.text = "Preview · ${d.label}  ·  tap to change"
        if (width == 0) { started = true; return }     // sized on first layout
        morphTo(target, animate && started)
        started = true
    }

    private fun next() {
        val all = Demo.values()
        setDemo(all[(demo.ordinal + 1) % all.size])
    }

    // ---------------- demo data (all fake, nothing touches the real island) ----------------

    private fun art(): Bitmap {
        val b = Bitmap.createBitmap(220, 220, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 220f, 220f, Color.rgb(120, 60, 220), Color.rgb(255, 110, 100), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 220f, 220f, p)
        p.shader = RadialGradient(70f, 60f, 120f, Color.argb(120, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 220f, 220f, p)
        return b
    }

    private fun letterIcon(letter: String, color: Int): android.graphics.drawable.Drawable {
        val b = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        p.shader = LinearGradient(0f, 0f, 0f, 120f, Ui.lighten(color, 0.25f), color, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, 120f, 120f, p)
        p.shader = null; p.color = Color.WHITE; p.textSize = 64f; p.textAlign = Paint.Align.CENTER
        p.typeface = android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL)
        c.drawText(letter, 60f, 84f, p)
        return BitmapDrawable(act.resources, b)
    }

    private lateinit var current: IslandActivity

    private fun build(d: Demo): IslandActivity {
        val type = when (d) {
            Demo.CHARGING -> ActivityType.ALERT
            Demo.VOLUME -> ActivityType.VOLUME
            Demo.MUSIC_PILL, Demo.MUSIC_OPEN -> ActivityType.MEDIA
            Demo.TIMER -> ActivityType.TIMER
            Demo.MESSAGE -> ActivityType.NOTIFICATION
        }
        val a = IslandActivity("preview", type)
        when (d) {
            Demo.MUSIC_PILL, Demo.MUSIC_OPEN -> {
                val art = art()
                a.kind = Kind.MEDIA
                a.accent = vivid(Color.rgb(255, 92, 120))
                a.title = "Midnight City"; a.subtitle = "M83"
                a.trailing = Trailing.Wave
                a.icon = { BitmapDrawable(act.resources, art) }
                a.media = MediaInfo("demo", "Midnight City", "M83", art, true, 243_000, 96_000, SystemClock.elapsedRealtime(), 1f, true)
            }
            Demo.MESSAGE -> {
                a.accent = Color.rgb(10, 132, 255)
                a.title = "Alex"; a.subtitle = "Are we still on for dinner tonight?"
                a.icon = { letterIcon("A", Color.rgb(10, 132, 255)) }
            }
            Demo.CHARGING -> {
                a.accent = Color.rgb(48, 209, 88)
                a.title = "78%"; a.subtitle = "42 min to full"
                a.icon = { BatteryDrawable(78, true, Color.rgb(48, 209, 88)) }
            }
            Demo.VOLUME -> {
                a.kind = Kind.VOLUME
                a.accent = Color.rgb(10, 132, 255)
                a.title = "Media"
                a.icon = { IconDrawable(Icon.VOLUME, Color.WHITE) }
                a.volume = VolumeInfo(false, 55) {}
            }
            Demo.TIMER -> {
                a.accent = Color.rgb(255, 159, 10)
                a.title = "Timer"; a.subtitle = "Pasta"
                a.icon = { IconDrawable(Icon.ALARM, Color.WHITE) }
                a.trailing = Trailing.Timer(System.currentTimeMillis() + 4 * 60_000L + 12_000L, true)
                a.buttons = listOf(ActionButton("Pause") {}, ActionButton("Stop", Color.rgb(255, 69, 58)) {})
            }
        }
        current = a
        return a
    }

    private fun viewFor(d: Demo): View = when (d) {
        Demo.MUSIC_PILL -> collapsed
        Demo.MESSAGE, Demo.CHARGING, Demo.VOLUME -> banner
        Demo.MUSIC_OPEN -> mediaExp
        Demo.TIMER -> genericExp
    }

    private fun bind(d: Demo, a: IslandActivity) {
        val inset = act.dpi(12)       // the preview pill is below the camera, so only a little top clearance
        when (d) {
            Demo.MUSIC_PILL -> collapsed.bind(a, a.accent, emptyList())
            Demo.MESSAGE, Demo.CHARGING, Demo.VOLUME -> { banner.setCamera(0, inset); banner.bind(a, a.accent) }
            Demo.MUSIC_OPEN -> { mediaExp.setTopInset(0); mediaExp.bind(a, a.accent) }
            Demo.TIMER -> { genericExp.setCamera(0, inset); genericExp.bind(a, a.accent) }
        }
        aura.setAccent(a.accent, started)
        aura.fadeIntensity(if (d == Demo.MUSIC_PILL) 0.28f else 0.36f, started)
    }

    // ---------------- geometry ----------------

    private fun availW() = max(act.dpi(160), width - act.dpi(28))

    /** Final size of the pill for the current demo, using the same rules as the real overlay. */
    private fun targetSize(v: View): Pair<Int, Int> {
        val h = act.dpi(sp.int(Prefs.PILL_HEIGHT, 36))
        val wide = availW()
        return when (v) {
            collapsed -> {
                val pref = min(act.dpi(sp.int(Prefs.WIDTH, Prefs.DEFAULT_WIDTH)), wide)
                val need = 2 * (act.dpi(20) + act.dpi(34) + act.dpi(10))
                max(pref, need).coerceAtMost(wide) to h
            }
            banner -> {
                v.measure(View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                val collapsedW = min(act.dpi(sp.int(Prefs.WIDTH, Prefs.DEFAULT_WIDTH)), wide)
                val w = if (current.kind == Kind.VOLUME) wide else min(wide, max(collapsedW, v.measuredWidth + act.dpi(4)))
                v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                w to max(v.measuredHeight, act.dpi(60))
            }
            else -> {
                val w = min(wide, act.dpi(390))
                v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
                w to max(v.measuredHeight, act.dpi(84))
            }
        }
    }

    private fun roundness() = sp.int(Prefs.CORNER, 100) / 100f
    private fun radiusFor(v: View, h: Int) = roundness() * if (v === collapsed) h / 2f else act.dp(30f)

    private fun morphTo(target: View, animate: Boolean) {
        anim?.cancel()
        val (tw, th) = targetSize(target)
        val tr = radiusFor(target, th)
        // content views get their final size at once; the pill clips them while it morphs
        if (target !== collapsed) target.layoutParams = FrameLayout.LayoutParams(tw, th, Gravity.CENTER)
        else target.layoutParams = FrameLayout.LayoutParams(-1, -1)
        swapContent(target, animate)
        val fw = if (curW == 0) tw else curW; val fh = if (curH == 0) th else curH; val fr = if (curR == 0f) tr else curR
        if (!animate) { curW = tw; curH = th; curR = tr; applyGeometry(); return }
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 520
            interpolator = ease
            addUpdateListener {
                val t = it.animatedValue as Float
                curW = (fw + (tw - fw) * t).roundToInt(); curH = (fh + (th - fh) * t).roundToInt()
                curR = fr + (tr - fr) * t
                applyGeometry()
            }
            start()
        }
    }

    private fun swapContent(target: View, animate: Boolean) {
        shown = target
        for (v in contentViews) {
            v.animate().cancel()
            if (v === target) {
                v.visibility = VISIBLE
                if (animate) { v.alpha = 0f; v.animate().alpha(1f).setStartDelay(110).setDuration(260).start() } else v.alpha = 1f
            } else if (v.visibility == VISIBLE) {
                if (animate) v.animate().alpha(0f).setStartDelay(0).setDuration(120).withEndAction { if (shown !== v) v.visibility = GONE }.start()
                else v.visibility = GONE
            }
        }
    }

    // below the real status bar and camera, so the preview never sits on top of the camera (the distance setting still nudges it)
    private fun pillTop() = topInset + act.dpi(12) + act.dpi(sp.int(Prefs.OFFSET, Prefs.DEFAULT_OFFSET)).coerceAtMost(act.dpi(40)) / 3

    private fun applyGeometry() {
        val lp = pill.layoutParams as LayoutParams
        lp.width = curW; lp.height = curH; lp.topMargin = pillTop()
        lp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
        pill.layoutParams = lp
        pill.translationX = act.dp(sp.int(Prefs.XOFFSET, 0).toFloat()).coerceIn(-width / 4f, width / 4f)
        pillBg.cornerRadius = curR
        aura.radius = curR
        pill.invalidateOutline()

        // second pill, like the real double pill
        val showSecond = shown === collapsed && sp.bool(Prefs.DOUBLE_PILL, true)
        secondary.visibility = if (showSecond) VISIBLE else GONE
        if (showSecond) {
            val h = act.dpi(sp.int(Prefs.PILL_HEIGHT, 36))
            val gap = act.dpi(sp.int(Prefs.DOUBLE_GAP, 6))
            val side = if (sp.str(Prefs.DOUBLE_SIDE, "right") == "left") -1f else 1f
            val slp = secondary.layoutParams as LayoutParams
            slp.width = h; slp.height = h; slp.topMargin = pillTop(); slp.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            secondary.layoutParams = slp
            secondary.translationX = pill.translationX + side * (curW / 2f + gap + h / 2f)
            secondaryBg.setColor(bgColor())
        }
        requestLayout()
    }

    private fun bgColor(): Int {
        val base = sp.int(Prefs.BG_COLOR, Color.BLACK)
        val a = sp.int(Prefs.BG_ALPHA, 100) * 255 / 100
        return Color.argb(a, Color.red(base), Color.green(base), Color.blue(base))
    }

    /** Colour, outline and glow follow the settings. */
    private fun applyLook() {
        pillBg.setColor(bgColor())
        val accent = if (::current.isInitialized) current.accent else Color.WHITE
        pillBg.setStroke(if (sp.str(Prefs.OUTLINE, "none") == "accent") act.dpi(2) else 0, accent)
        if (sp.bool(Prefs.GLOW, false)) {
            pill.elevation = act.dp(10f)
            if (Build.VERSION.SDK_INT >= 28) { pill.outlineAmbientShadowColor = accent; pill.outlineSpotShadowColor = accent }
        } else pill.elevation = 0f
    }

    override fun onMeasure(w: Int, h: Int) {
        val footer = act.dpi(54)
        val want = pillTop() + curH + footer + act.dpi(10)
        super.onMeasure(w, MeasureSpec.makeMeasureSpec(max(want, act.dpi(150)), MeasureSpec.EXACTLY))
    }

    private var fitted = false

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        super.onSizeChanged(w, h, ow, oh)
        // First size known: fit the pill. Posted, because changing layout inside a layout pass can be dropped.
        if (!fitted && w > 0) { fitted = true; post { morphTo(viewFor(demo), false) } }
        else if (fitted && w != ow) post { onSharedPreferenceChanged(null, null) }   // e.g. rotation / fold
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        wall.shader = LinearGradient(0f, 0f, w, h, Color.rgb(18, 22, 58), Color.rgb(64, 24, 78), Shader.TileMode.CLAMP)
        rect.set(0f, 0f, w, h); c.drawRect(rect, wall)
        wall.shader = RadialGradient(w * 0.9f, h * 0.95f, w * 0.6f, Color.argb(150, 255, 110, 90), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(rect, wall)
        wall.shader = RadialGradient(w * 0.08f, h * 0.15f, w * 0.55f, Color.argb(130, 60, 130, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
        c.drawRect(rect, wall)
        wall.shader = null
    }

    // ---------------- lifecycle ----------------

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(this)
        handler.post(ticker)
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(this)
        handler.removeCallbacks(ticker)
        anim?.cancel()
        super.onDetachedFromWindow()
    }

    override fun onSharedPreferenceChanged(p: SharedPreferences?, key: String?) {
        if (key == Prefs.ENABLED) { master.isChecked = prefs.getBoolean(Prefs.ENABLED, true); return }
        if (width == 0) return
        applyLook()
        val v = shown ?: return
        // size / position settings: re-fit immediately (no morph), so sliders feel direct
        val (tw, th) = targetSize(v)
        if (v !== collapsed) v.layoutParams = FrameLayout.LayoutParams(tw, th, Gravity.CENTER)
        anim?.cancel()
        curW = tw; curH = th; curR = radiusFor(v, th)
        applyGeometry()
    }
}
