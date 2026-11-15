package app.orbit.cameraisland.settings

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.TextView
import app.orbit.cameraisland.island.Icon
import app.orbit.cameraisland.island.dp
import app.orbit.cameraisland.island.dpi

/**
 * Programmatic settings UI: a scrolling column of grouped cards. [section] starts a labelled card;
 * every row added after it lands in that card, separated by hairline dividers.
 * Pass a [title] for a sub-screen with a back button, or null to build a custom header (home screen).
 */
class UiKit(val act: Activity, title: String? = null, val embedded: Boolean = false) {
    @Suppress("unused") private val theme = Ui.init(act)   // first, so every colour below is the right one
    val prefs: SharedPreferences = Prefs.get(act)
    /** Per-screen values for the position / look settings (separate on foldables). */
    private val scopeKey: String? by lazy { ScreenProfile.scopeOrNull(act) }
    private val sp = ScreenPrefs(prefs) { scopeKey }

    /** Put this in setContentView. */
    val root = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
    val col = LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dpi(16), dpi(2), dpi(16), dpi(32))
    }
    val scroll = ScrollView(act).apply { isVerticalScrollBarEnabled = false; addView(col) }

    private var card: LinearLayout? = null
    private val refreshers = ArrayList<() -> Unit>()

    init {
        // embedded in the main screen: the host supplies the bars, insets and back navigation
        if (title != null) {
            if (!embedded) root.addView(Ui.topBar(act))
            col.addView(Ui.largeTitle(act, title))
        }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        if (!embedded) Ui.padForInsets(act, root)
    }

    fun dpi(v: Int) = act.dpi(v)

    /** Keeps [v] fixed above the scrolling list (e.g. a live preview that stays visible while you drag sliders). */
    fun pinned(v: View) {
        root.addView(v, root.indexOfChild(scroll), LinearLayout.LayoutParams(-1, -2).apply {
            setMargins(dpi(16), dpi(2), dpi(16), dpi(6))
        })
    }

    /** Re-reads values shown in value rows (call from onResume). */
    fun refresh() = refreshers.forEach { it() }

    // ---------------- structure ----------------

    private fun hostCard(): LinearLayout = card ?: LinearLayout(act).apply {
        orientation = LinearLayout.VERTICAL
        background = Ui.round(act, Ui.SURFACE, 22f)
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        col.addView(this, LinearLayout.LayoutParams(-1, -2))
        card = this
    }

    /** Adds a row to the current card (starting one if needed). */
    fun addRow(v: View, divider: Boolean = true, dividerInset: Int = 16) {
        val c = hostCard()
        if (divider && c.childCount > 0) {
            c.addView(View(act).apply { setBackgroundColor(Ui.DIVIDER) },
                LinearLayout.LayoutParams(-1, 1).apply { marginStart = dpi(dividerInset) })
        }
        c.addView(v, LinearLayout.LayoutParams(-1, -2))
    }

    /** Adds a view outside any card (e.g. the live preview). */
    fun raw(v: View, topDp: Int = 8) {
        card = null
        col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(topDp) })
    }

    fun add(v: View) = raw(v, 0)

    fun section(title: String) {
        card = null
        col.addView(Ui.text(act, 13f, Ui.TEXT2).apply {
            text = title.uppercase()
            letterSpacing = 0.04f
            setPadding(dpi(16), dpi(22), 0, dpi(7))
        })
    }

    fun title(text: String) = raw(Ui.largeTitle(act, text), 0)

    /** Footnote: sits under the card it belongs to (iOS "footer"), or free-standing before the first section. */
    fun note(text: String, color: Int = Ui.TEXT2) {
        val t = Ui.text(act, 13f, color).apply {
            this.text = text
            setLineSpacing(0f, 1.12f)
            setPadding(dpi(16), dpi(if (card == null) 8 else 7), dpi(16), dpi(2))
        }
        card = null
        col.addView(t, LinearLayout.LayoutParams(-1, -2))
    }

    fun spacer(dp: Int = 12) = col.addView(View(act), LinearLayout.LayoutParams(1, dpi(dp)))

    // ---------------- row building blocks ----------------

    private fun rowBase(clickable: Boolean) = LinearLayout(act).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dpi(52)
        setPadding(dpi(16), dpi(9), dpi(16), dpi(9))
        if (clickable) { isClickable = true; background = Ui.ripple(null) }
    }

    private fun textBlock(title: String, desc: String?, titleColor: Int = Ui.TEXT): LinearLayout =
        LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(Ui.text(act, 17f, titleColor).apply { text = title })
            if (desc != null) addView(Ui.text(act, 13f, Ui.TEXT2).apply {
                text = desc; setPadding(0, dpi(3), 0, 0); setLineSpacing(0f, 1.1f)
            })
        }

    private fun valueText(text: String) = Ui.text(act, 17f, Ui.TEXT2).apply { this.text = text; maxLines = 1 }

    private fun smallIcon(d: Drawable) = ImageView(act).apply { setImageDrawable(d) }

    // ---------------- rows ----------------

    fun toggle(label: String, key: String, def: Boolean, desc: String? = null, onChange: ((Boolean) -> Unit)? = null): IosSwitch {
        val sw = IosSwitch(act).apply {
            isChecked = sp.bool(key, def)
            this.onChange = { c -> prefs.edit().putBoolean(sp.key(key), c).apply(); onChange?.invoke(c) }
        }
        val row = rowBase(true).apply {
            addView(textBlock(label, desc), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dpi(14) })
            addView(sw)
            setOnClickListener { sw.toggle() }
        }
        addRow(row)
        return sw
    }

    private fun styledBar(min: Int, max: Int, value: Int, onChange: (Int, Boolean) -> Unit) = SeekBar(act).apply {
        this.max = max - min
        progress = (value - min).coerceIn(0, max - min)
        val h = dpi(4)
        val track = Ui.round(act, Ui.SLIDER_TRACK, 2f)
        val fill = ClipDrawable(Ui.round(act, Ui.ACCENT, 2f), Gravity.START, ClipDrawable.HORIZONTAL)
        progressDrawable = LayerDrawable(arrayOf(track, fill)).apply {
            setId(0, android.R.id.background); setId(1, android.R.id.progress)
            for (i in 0..1) { setLayerHeight(i, h); setLayerGravity(i, Gravity.CENTER_VERTICAL) }
        }
        val d = dpi(28)
        thumb = GradientDrawable().apply {
            shape = GradientDrawable.OVAL; setColor(Color.WHITE); setSize(d, d)
            setStroke(dpi(1), Color.argb(if (Ui.dark) 0 else 60, 0, 0, 0))
        }
        thumbOffset = 0
        splitTrack = false
        setPadding(d / 2, dpi(6), d / 2, dpi(6))
        setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) = onChange(p + min, fromUser)
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
    }

    fun slider(label: String, key: String, def: Int, min: Int, max: Int, unit: String = "dp") {
        val value = sp.int(key, def)
        val v = valueText("$value$unit")
        val bar = styledBar(min, max, value) { n, fromUser ->
            v.text = "$n$unit"
            if (fromUser) prefs.edit().putInt(sp.key(key), n).apply()
        }
        val top = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(Ui.text(act, 17f, Ui.TEXT).apply { text = label }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(v)
        }
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16), dpi(13), dpi(16), dpi(7))
            addView(top)
            addView(bar, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(4); marginStart = -dpi(14); marginEnd = -dpi(14) })
        }
        addRow(row)
    }

    /** Text option stored under [key]. */
    fun choice(label: String, key: String, def: String, options: List<Pair<String, String>>, onChange: ((String) -> Unit)? = null) =
        chooser(label, def, options, { sp.str(key, def) ?: def }, { prefs.edit().putString(sp.key(key), it).apply(); onChange?.invoke(it) })

    /** Number option stored under [key] (e.g. a timeout). */
    fun longChoice(label: String, key: String, def: Long, options: List<Pair<Long, String>>) =
        chooser(label, def, options, { prefs.getLong(key, def) }, { prefs.edit().putLong(key, it).apply() })

    /** A row showing the current option on the right; tapping opens a bottom sheet. */
    private fun <T> chooser(label: String, def: T, options: List<Pair<T, String>>, get: () -> T, set: (T) -> Unit) {
        fun current() = (options.firstOrNull { it.first == get() } ?: options.first { it.first == def }).second
        val value = valueText(current()).apply { gravity = Gravity.END; maxWidth = (act.resources.displayMetrics.widthPixels * 0.5f).toInt(); ellipsize = android.text.TextUtils.TruncateAt.END }
        val row = rowBase(true).apply {
            addView(Ui.text(act, 17f, Ui.TEXT).apply { text = label }, LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dpi(12) })
            addView(value)
            addView(smallIcon(UpDownDrawable(Ui.TEXT2)), LinearLayout.LayoutParams(dpi(18), dpi(18)).apply { marginStart = dpi(4) })
            setOnClickListener {
                Ui.sheet(act, label, options.map { it.second }, options.indexOfFirst { it.first == get() }) { i ->
                    set(options[i].first); value.text = current()
                }
            }
        }
        addRow(row)
    }

    /** Row of colour swatches, spread across the full width. */
    fun colors(label: String, key: String, def: Int, palette: List<Int>) {
        val swatches = ArrayList<Swatch>()
        fun mark() { val cur = sp.int(key, def); swatches.forEach { it.chosen = it.color == cur } }
        val strip = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
        palette.forEach { c ->
            val v = Swatch(act, c).apply { setOnClickListener { prefs.edit().putInt(sp.key(key), c).apply(); mark() } }
            swatches.add(v)
            strip.addView(v, LinearLayout.LayoutParams(0, dpi(40), 1f))
        }
        mark()
        val row = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpi(16), dpi(13), dpi(16), dpi(10))
            addView(Ui.text(act, 17f, Ui.TEXT).apply { text = label })
            addView(strip, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dpi(8) })
        }
        addRow(row)
    }

    /** One colour dot; the selected one gets a ring around it. */
    private class Swatch(val act: Activity, val color: Int) : View(act) {
        private val p = Paint(Paint.ANTI_ALIAS_FLAG)
        var chosen = false
            set(v) { field = v; invalidate() }
        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val r = minOf(width, height) / 2f - act.dp(1f)
            val inner = r - act.dp(if (chosen) 5f else 2f)
            p.style = Paint.Style.FILL; p.color = color; c.drawCircle(cx, cy, inner, p)
            // hairline so black / white dots stay visible on either background
            p.style = Paint.Style.STROKE; p.strokeWidth = act.dp(1f); p.color = Color.argb(60, 128, 128, 128); c.drawCircle(cx, cy, inner, p)
            if (chosen) { p.strokeWidth = act.dp(2.2f); p.color = Ui.ACCENT; c.drawCircle(cx, cy, r - act.dp(1f), p) }
        }
    }

    /** Time of day stored as minutes since midnight. */
    fun time(label: String, key: String, def: Int) {
        fun fmt(m: Int) = "%02d:%02d".format(m / 60, m % 60)
        val value = valueText(fmt(sp.int(key, def)))
        val row = rowBase(true).apply {
            addView(Ui.text(act, 17f, Ui.TEXT).apply { text = label }, LinearLayout.LayoutParams(0, -2, 1f))
            addView(value)
            setOnClickListener {
                val cur = sp.int(key, def)
                android.app.TimePickerDialog(act, { _, h, m -> prefs.edit().putInt(sp.key(key), h * 60 + m).apply(); value.text = fmt(h * 60 + m) },
                    cur / 60, cur % 60, true).show()
            }
        }
        addRow(row)
    }

    /** A navigation row with a live value on the right (refreshed on resume). */
    fun valueRow(label: String, desc: String? = null, value: () -> String, onClick: () -> Unit) {
        val v = valueText(value())
        refreshers += { v.text = value() }
        val row = rowBase(true).apply {
            addView(textBlock(label, desc), LinearLayout.LayoutParams(0, -2, 1f).apply { marginEnd = dpi(12) })
            addView(v)
            addView(Ui.chevron(act), LinearLayout.LayoutParams(dpi(18), dpi(18)).apply { marginStart = dpi(4) })
            setOnClickListener { onClick() }
        }
        addRow(row)
    }

    /** An action row (accent-coloured label). */
    fun button(label: String, onClick: () -> Unit): View {
        val row = rowBase(true).apply {
            addView(Ui.text(act, 17f, Ui.ACCENT).apply { text = label })
            setOnClickListener { onClick() }
        }
        addRow(row)
        return row
    }

    /** A row that opens something: gradient icon badge, title, subtitle, chevron. */
    fun link(title: String, subtitle: String, icon: Icon, color: Int, onClick: () -> Unit) {
        val row = rowBase(true).apply {
            minimumHeight = dpi(64)
            addView(Ui.badge(act, icon, color, 36), LinearLayout.LayoutParams(dpi(36), dpi(36)))
            addView(textBlock(title, subtitle), LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = dpi(14); marginEnd = dpi(8) })
            addView(Ui.chevron(act), LinearLayout.LayoutParams(dpi(18), dpi(18)))
            setOnClickListener { onClick() }
        }
        addRow(row, dividerInset = 66)
    }

    /** Home-screen grid: two tiles per row, each with a gradient icon badge, title and subtitle. */
    class Tile(val title: String, val subtitle: String, val icon: Icon, val color: Int, val onClick: () -> Unit)

    fun tiles(items: List<Tile>) {
        card = null
        val gap = dpi(10)
        items.chunked(2).forEach { pair ->
            val line = LinearLayout(act).apply { orientation = LinearLayout.HORIZONTAL }
            pair.forEachIndexed { i, t ->
                val tile = LinearLayout(act).apply {
                    orientation = LinearLayout.VERTICAL
                    minimumHeight = dpi(112)
                    setPadding(dpi(15), dpi(14), dpi(15), dpi(14))
                    background = Ui.ripple(Ui.round(act, Ui.SURFACE, 22f))
                    isClickable = true
                    setOnClickListener { t.onClick() }
                    addView(Ui.badge(act, t.icon, t.color, 34), LinearLayout.LayoutParams(dpi(34), dpi(34)))
                    addView(View(act), LinearLayout.LayoutParams(1, 0, 1f))
                    addView(Ui.text(act, 16f, Ui.TEXT, medium = true).apply { text = t.title; setPadding(0, dpi(10), 0, 0) })
                    addView(Ui.text(act, 12f, Ui.TEXT2).apply { text = t.subtitle; setPadding(0, dpi(2), 0, 0); maxLines = 2; setLineSpacing(0f, 1.05f) })
                }
                line.addView(tile, LinearLayout.LayoutParams(0, -1, 1f).apply { if (i == 0 && pair.size == 2) marginEnd = gap / 2; if (i == 1) marginStart = gap / 2 })
            }
            col.addView(line, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = gap })
        }
    }
}

object Palette {
    val BG = listOf(Color.BLACK, Color.rgb(28, 28, 30), Color.rgb(10, 24, 56), Color.rgb(40, 12, 48), Color.rgb(8, 40, 28), Color.rgb(60, 14, 20), Color.rgb(70, 70, 76))
    val ACCENT = listOf(
        Color.rgb(48, 209, 88), Color.rgb(10, 132, 255), Color.rgb(255, 69, 58), Color.rgb(255, 159, 10),
        Color.rgb(255, 214, 10), Color.rgb(191, 90, 242), Color.rgb(100, 210, 255), Color.rgb(255, 255, 255),
    )
}
