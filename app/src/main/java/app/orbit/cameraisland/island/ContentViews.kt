package app.orbit.cameraisland.island

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import kotlin.math.max

internal fun iconOf(a: IslandActivity): Drawable =
    a.icon?.invoke() ?: ColorDrawable(Color.DKGRAY)

/** Small 6dp coloured dots (privacy / recording indicators) drawn inside the collapsed pill. */
class DotsView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var colors: List<Int> = emptyList()
        set(v) { field = v; requestLayout(); invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        val d = context.dpi(7)
        setMeasuredDimension(if (colors.isEmpty()) 0 else colors.size * d + (colors.size - 1) * context.dpi(3), d)
    }

    override fun onDraw(c: Canvas) {
        val d = context.dp(7f)
        colors.forEachIndexed { i, col ->
            paint.color = col
            c.drawCircle(d / 2 + i * (d + context.dp(3f)), height / 2f, d / 2, paint)
        }
    }
}

/** The small pill: [icon] ........ [trailing]. */
class CollapsedView(ctx: Context) : LinearLayout(ctx) {
    val lead = ImageView(ctx)
    val wave = WaveView(ctx)
    val ring = RingView(ctx)
    val timer = TextView(ctx)
    val label = TextView(ctx)
    val dot = View(ctx)
    val dots = DotsView(ctx)
    private val trailingHost = FrameLayout(ctx)
    private var timerSpec: Trailing.Timer? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        lead.scaleType = ImageView.ScaleType.CENTER_CROP
        roundClip(lead, 0f, oval = true)
        addView(lead, LayoutParams(ctx.dpi(26), ctx.dpi(26)))
        addView(View(ctx), LayoutParams(0, 1, 1f))
        Type.number(timer, 14f)
        Type.title(label, 13f)
        label.maxWidth = ctx.dpi(64)
        dot.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        trailingHost.addView(wave, FrameLayout.LayoutParams(ctx.dpi(20), ctx.dpi(14), Gravity.CENTER))
        trailingHost.addView(ring, FrameLayout.LayoutParams(ctx.dpi(20), ctx.dpi(20), Gravity.CENTER))
        trailingHost.addView(timer, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        trailingHost.addView(label, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        trailingHost.addView(dot, FrameLayout.LayoutParams(ctx.dpi(10), ctx.dpi(10), Gravity.CENTER))
        addView(dots, LayoutParams(-2, -2).apply { marginEnd = ctx.dpi(6) })
        addView(trailingHost, LayoutParams(-2, -2))
    }

    fun bind(a: IslandActivity, accent: Int, indicatorDots: List<Int>) {
        // glyphs sit in an accent-tinted disc, app icons / art stay full-bleed: same rule as the banner and expanded views
        if (a.isGlyph) {
            lead.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(tint(accent, 90)) }
            lead.scaleType = ImageView.ScaleType.FIT_CENTER
            val pd = context.dpi(5); lead.setPadding(pd, pd, pd, pd)
        } else {
            roundClip(lead, 0f, oval = true); lead.scaleType = ImageView.ScaleType.CENTER_CROP; lead.setPadding(0, 0, 0, 0)
        }
        lead.outlineProvider = android.view.ViewOutlineProvider.BACKGROUND; lead.clipToOutline = true
        lead.setImageDrawable(iconOf(a))
        lead.visibility = if (a.type == ActivityType.IDLE) INVISIBLE else VISIBLE
        bindTrailing(a, accent, indicatorDots)
    }

    /** Only the right-hand side (no icon load): enough to measure how wide the pill needs to be. */
    fun bindTrailing(a: IslandActivity, accent: Int, indicatorDots: List<Int>) {
        for (v in listOf<View>(wave, ring, timer, label, dot)) v.visibility = GONE
        timerSpec = null
        when (val t = a.trailing) {
            Trailing.None -> {}
            Trailing.Wave -> { wave.visibility = VISIBLE; wave.barColor = accent; wave.playing = a.media?.playing == true }
            is Trailing.Timer -> { timer.visibility = VISIBLE; timerSpec = t; timer.text = timerText(t) }
            is Trailing.Ring -> { ring.visibility = VISIBLE; ring.color = accent; ring.fraction = t.fraction }
            is Trailing.Label -> { label.visibility = VISIBLE; label.text = t.text }
            is Trailing.Dot -> {
                dot.visibility = VISIBLE
                (dot.background as GradientDrawable).setColor(t.color)
            }
        }
        dots.colors = indicatorDots
    }

    val hasTimer get() = timerSpec != null
    fun tick() { timerSpec?.let { timer.text = timerText(it) } }

    /** Natural width of everything right of the centre, so the pill can keep the camera cutout clear. */
    fun trailingWidthPx(): Int {
        val spec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        trailingHost.measure(spec, spec)
        var w = trailingHost.measuredWidth
        if (dots.colors.isNotEmpty()) { dots.measure(spec, spec); w += dots.measuredWidth + context.dpi(6) }
        return w
    }
}

/**
 * Banner (alerts, peeks, volume). The strip beside the camera is left clear; icon and text sit together in one row
 * below it: [icon] title / message ... value. For volume the middle of the row is the slider instead.
 */
class BannerView(ctx: Context) : LinearLayout(ctx) {
    private val spacer = View(ctx)
    val icon = ImageView(ctx)
    private val title = TextView(ctx)
    private val sub = TextView(ctx)
    private val value = TextView(ctx)
    private val seek = SeekBar(ctx)
    private val texts = LinearLayout(ctx)
    private val row = LinearLayout(ctx)
    private var volume: VolumeInfo? = null

    init {
        orientation = VERTICAL
        setPadding(ctx.dpi(18), 0, ctx.dpi(18), ctx.dpi(16))
        addView(spacer, LayoutParams(-1, ctx.dpi(30)))

        row.orientation = HORIZONTAL
        row.gravity = Gravity.CENTER_VERTICAL
        row.addView(icon, LayoutParams(ctx.dpi(40), ctx.dpi(40)))

        texts.orientation = VERTICAL
        Type.title(title, 16f)
        Type.body(sub)
        sub.isSingleLine = false
        sub.maxLines = 2
        texts.addView(title)
        texts.addView(sub, LayoutParams(-2, -2).apply { topMargin = ctx.dpi(1) })
        row.addView(texts, LayoutParams(0, -2, 1f).apply { marginStart = ctx.dpi(12) })

        slimBar(ctx, seek, 10f, 0f)
        seek.setPadding(0, ctx.dpi(8), 0, ctx.dpi(8))
        seek.progressBackgroundTintList = ColorStateList.valueOf(Color.argb(56, 255, 255, 255))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                if (!fromUser) return
                volume?.setPercent(p * 5)
                value.text = "${p * 5}%"
                IslandController.touchTransient()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        row.addView(seek, LayoutParams(0, -2, 1f).apply { marginStart = ctx.dpi(12) })

        Type.number(value, 15f)
        value.minWidth = ctx.dpi(40)
        value.gravity = Gravity.END
        row.addView(value, LayoutParams(-2, -2).apply { marginStart = ctx.dpi(10) })
        addView(row, LayoutParams(-1, -2))
    }

    /** [inset] = space down to below the camera; the first row of content starts there. */
    fun setCamera(@Suppress("UNUSED_PARAMETER") half: Int, inset: Int) {
        val h = maxOf(inset - context.dpi(6), context.dpi(12))
        if ((spacer.layoutParams as LayoutParams).height != h) {
            spacer.layoutParams = LayoutParams(-1, h); requestLayout()
        }
    }

    /** Natural width: icon + the wider of title and message (capped) + value. */
    override fun onMeasure(w: Int, h: Int) {
        if (MeasureSpec.getMode(w) == MeasureSpec.UNSPECIFIED) {
            val tw = maxOf(title.paint.measureText(title.text.toString()), sub.paint.measureText(sub.text.toString()))
            val textW = minOf(tw.toInt() + context.dpi(6), context.dpi(230))
            val valueW = if (value.visibility == VISIBLE) context.dpi(50) else 0
            val want = context.dpi(40) + context.dpi(12) + textW + valueW + paddingLeft + paddingRight
            super.onMeasure(MeasureSpec.makeMeasureSpec(want, MeasureSpec.EXACTLY), h)
        } else super.onMeasure(w, h)
    }

    fun bind(a: IslandActivity, accent: Int) {
        styleIconTile(icon, a, accent, 40)
        icon.setImageDrawable(iconOf(a))
        title.text = a.title
        sub.text = a.subtitle
        val v = a.volume
        volume = v
        if (a.kind == Kind.VOLUME && v != null) {
            texts.visibility = GONE
            seek.visibility = VISIBLE
            seek.max = 20                 // the bar moves in the same 5% steps as the number
            seek.progress = v.percent / 5
            seek.progressTintList = ColorStateList.valueOf(Color.WHITE)
            value.visibility = VISIBLE
            value.text = if (v.muted) "Muted" else "${v.percent}%"
        } else {
            texts.visibility = VISIBLE
            seek.visibility = GONE
            value.visibility = GONE
            sub.visibility = if (a.subtitle.isEmpty()) GONE else VISIBLE
        }
    }
}

/**
 * Expanded player. Art-forward: a large rounded cover whose colour also tints the island, title / artist beside it,
 * a thin progress bar with elapsed and remaining time underneath, and bare transport glyphs with a soft disc on play.
 */
class MediaExpandedView(ctx: Context) : LinearLayout(ctx) {
    val header = LinearLayout(ctx)
    private val art = ImageView(ctx)
    private val title = TextView(ctx)
    private val artist = TextView(ctx)
    private val wave = WaveView(ctx)
    private val elapsed = TextView(ctx)
    private val remaining = TextView(ctx)
    private val seek = SeekBar(ctx)
    private val progressBox = LinearLayout(ctx)
    private val prev = ImageButton(ctx)
    private val play = ImageButton(ctx)
    private val next = ImageButton(ctx)
    private var info: MediaInfo? = null
    private var dragging = false
    private var shownPlaying: Boolean? = null
    private var accentNow = Color.WHITE

    init {
        orientation = VERTICAL
        setPadding(ctx.dpi(20), ctx.dpi(16), ctx.dpi(20), ctx.dpi(10))
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        art.scaleType = ImageView.ScaleType.CENTER_CROP
        roundClip(art, ctx.dp(16f))
        header.addView(art, LayoutParams(ctx.dpi(68), ctx.dpi(68)))
        val texts = LinearLayout(ctx).apply { orientation = VERTICAL }
        Type.title(title, 18f)
        title.ellipsize = TextUtils.TruncateAt.MARQUEE
        title.marqueeRepeatLimit = -1
        title.isSelected = true
        Type.body(artist, 15f)
        texts.addView(title); texts.addView(artist, LayoutParams(-2, -2).apply { topMargin = ctx.dpi(2) })
        header.addView(texts, LayoutParams(0, -2, 1f).apply { marginStart = ctx.dpi(14); marginEnd = ctx.dpi(10) })
        header.addView(wave, LayoutParams(ctx.dpi(26), ctx.dpi(20)))
        addView(header)

        progressBox.orientation = VERTICAL
        Type.caption(elapsed); Type.caption(remaining)
        slimBar(ctx, seek, 5f, 12f)
        seek.max = 1000
        seek.progressBackgroundTintList = ColorStateList.valueOf(Color.argb(56, 255, 255, 255))
        seek.thumbTintList = ColorStateList.valueOf(Color.WHITE)
        seek.setPadding(ctx.dpi(6), ctx.dpi(10), ctx.dpi(6), ctx.dpi(4))
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val m = info ?: return
                if (fromUser) { elapsed.text = fmtClock(m.duration * p / 1000); remaining.text = "-" + fmtClock(m.duration - m.duration * p / 1000) }
            }
            override fun onStartTrackingTouch(s: SeekBar?) { dragging = true }
            override fun onStopTrackingTouch(s: SeekBar?) {
                dragging = false
                val m = info ?: return
                IslandController.seekMedia(m.duration * (s?.progress ?: 0) / 1000)
            }
        })
        progressBox.addView(seek, LayoutParams(-1, -2))
        val times = LinearLayout(ctx).apply { orientation = HORIZONTAL; setPadding(ctx.dpi(6), 0, ctx.dpi(6), 0) }
        times.addView(elapsed, LayoutParams(0, -2, 1f))
        times.addView(remaining)
        progressBox.addView(times, LayoutParams(-1, -2).apply { topMargin = -ctx.dpi(2) })
        addView(progressBox, LayoutParams(-1, -2).apply { topMargin = ctx.dpi(12) })

        val controls = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER }
        prev.setImageDrawable(IconDrawable(Icon.PREVIOUS, Color.WHITE))
        next.setImageDrawable(IconDrawable(Icon.NEXT, Color.WHITE))
        play.setImageDrawable(IconDrawable(Icon.PLAY, Color.WHITE))
        controls.addView(prev, control(prev, 56, 0.22f, disc = false) { IslandController.mediaPrevious() })
        controls.addView(play, control(play, 68, 0.2f, disc = true) { IslandController.mediaPlayPause() })
        controls.addView(next, control(next, 56, 0.22f, disc = false) { IslandController.mediaNext() })
        addView(controls, LayoutParams(-1, -2).apply { topMargin = ctx.dpi(4) })
    }

    fun setTopInset(px: Int) = setPadding(context.dpi(20), context.dpi(16) + px, context.dpi(20), context.dpi(10))

    private fun control(b: ImageButton, sizeDp: Int, padFrac: Float, disc: Boolean, onClick: () -> Unit): LayoutParams {
        val face = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (disc) Color.argb(36, 255, 255, 255) else Color.TRANSPARENT)
        }
        // round mask, or the press highlight flashes as a square
        val mask = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE) }
        b.background = RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)), face, mask)
        b.scaleType = ImageView.ScaleType.FIT_CENTER
        val pad = context.dpi((sizeDp * padFrac).toInt())
        b.setPadding(pad, pad, pad, pad)
        b.setOnClickListener { onClick() }
        return LayoutParams(context.dpi(sizeDp), context.dpi(sizeDp)).apply {
            marginStart = context.dpi(12); marginEnd = context.dpi(12)
        }
    }

    fun bind(a: IslandActivity, accent: Int) {
        val m = a.media ?: return
        info = m
        accentNow = accent
        art.setImageDrawable(iconOf(a))
        wave.barColor = accent
        wave.playing = m.playing
        seek.progressTintList = ColorStateList.valueOf(Color.WHITE)
        if (title.text.toString() != m.title) title.text = m.title
        artist.text = m.artist
        artist.visibility = if (m.artist.isEmpty()) GONE else VISIBLE
        if (shownPlaying != m.playing) {
            shownPlaying = m.playing
            play.setImageDrawable(IconDrawable(if (m.playing) Icon.PAUSE else Icon.PLAY, Color.WHITE))
        }
        val a2 = if (m.live) 1f else 0.35f
        prev.alpha = a2; next.alpha = a2
        prev.isEnabled = m.live; next.isEnabled = m.live
        seek.isEnabled = m.live && m.canSeek
        tick()
    }

    fun tick() {
        val m = info ?: return
        if (m.duration <= 0) { progressBox.visibility = INVISIBLE; return }
        progressBox.visibility = VISIBLE
        if (dragging) return
        val pos = m.currentPosition()
        seek.progress = ((pos * 1000) / m.duration).toInt()
        elapsed.text = fmtClock(pos)
        remaining.text = "-" + fmtClock(m.duration - pos)
    }
}

/**
 * Expanded view for everything else: header row with the app, details, a big timer / ring when there is one, a thin
 * progress bar, capsule buttons tinted with the accent (solid for call actions), and a glass reply field.
 */
class GenericExpandedView(ctx: Context, private val onWantKeyboard: () -> Unit, private val onDoneTyping: () -> Unit) : LinearLayout(ctx) {
    val header = LinearLayout(ctx)
    private val spacer = View(ctx)
    private val icon = ImageView(ctx)
    private val title = TextView(ctx)
    private val sub = TextView(ctx)
    private val timer = TextView(ctx)
    private val ring = RingView(ctx)
    private val label = TextView(ctx)
    private val bar = ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal)
    private val buttons = LinearLayout(ctx)
    private val replyRow = LinearLayout(ctx)
    private val replyField = EditText(ctx)
    private val replySend = ImageButton(ctx)
    private var timerSpec: Trailing.Timer? = null
    private var reply: ReplyAction? = null
    private var accentNow = Color.WHITE

    init {
        orientation = VERTICAL
        setPadding(ctx.dpi(18), 0, ctx.dpi(18), ctx.dpi(16))
        addView(spacer, LayoutParams(-1, ctx.dpi(30)))
        header.orientation = HORIZONTAL
        header.gravity = Gravity.CENTER_VERTICAL
        header.isClickable = true
        // icon and text together in one row below the camera, the live value (timer / ring / label) on the right
        header.addView(icon, LayoutParams(ctx.dpi(44), ctx.dpi(44)))
        val texts = LinearLayout(ctx).apply { orientation = VERTICAL }
        Type.title(title, 17f)
        Type.body(sub, 15f)
        sub.isSingleLine = false
        sub.maxLines = 3
        sub.ellipsize = TextUtils.TruncateAt.END
        texts.addView(title)
        texts.addView(sub, LayoutParams(-2, -2).apply { topMargin = ctx.dpi(1) })
        header.addView(texts, LayoutParams(0, -2, 1f).apply { marginStart = ctx.dpi(12); marginEnd = ctx.dpi(8) })
        Type.number(timer, 30f)
        Type.title(label, 15f)
        label.maxWidth = ctx.dpi(100)
        header.addView(timer); header.addView(label)
        header.addView(ring, LayoutParams(ctx.dpi(36), ctx.dpi(36)))
        addView(header, LayoutParams(-1, -2))

        bar.max = 1000
        slimBar(ctx, bar, 5f)
        bar.progressBackgroundTintList = ColorStateList.valueOf(Color.argb(56, 255, 255, 255))
        addView(bar, LayoutParams(-1, ctx.dpi(6)).apply { topMargin = ctx.dpi(14) })

        buttons.orientation = HORIZONTAL
        buttons.gravity = Gravity.CENTER
        addView(buttons, LayoutParams(-1, -2).apply { topMargin = ctx.dpi(14) })

        replyRow.orientation = HORIZONTAL
        replyRow.gravity = Gravity.CENTER_VERTICAL
        replyField.setTextColor(Color.WHITE)
        replyField.setHintTextColor(WHITE_50)
        replyField.textSize = 15f
        replyField.isSingleLine = true
        replyField.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        replyField.imeOptions = EditorInfo.IME_ACTION_SEND
        replyField.setPadding(ctx.dpi(16), 0, ctx.dpi(16), 0)
        replyField.background = glassShape(ctx, Color.argb(30, 255, 255, 255))
        // Overlay windows are not focusable by default; ask for focus the moment the field is touched.
        replyField.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) onWantKeyboard()
            false
        }
        replyField.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEND) { sendReply(); true } else false }
        replySend.setImageDrawable(IconDrawable(Icon.SEND, Color.WHITE))
        replySend.scaleType = ImageView.ScaleType.FIT_CENTER
        replySend.setPadding(ctx.dpi(10), ctx.dpi(10), ctx.dpi(10), ctx.dpi(10))
        replySend.setOnClickListener { sendReply() }
        replyRow.addView(replyField, LayoutParams(0, ctx.dpi(44), 1f))
        replyRow.addView(replySend, LayoutParams(ctx.dpi(44), ctx.dpi(44)).apply { marginStart = ctx.dpi(8) })
        addView(replyRow, LayoutParams(-1, -2).apply { topMargin = ctx.dpi(12) })
    }

    fun setCamera(@Suppress("UNUSED_PARAMETER") half: Int, inset: Int) {
        val h = maxOf(inset - context.dpi(6), context.dpi(12))
        if ((spacer.layoutParams as LayoutParams).height != h) { spacer.layoutParams = LayoutParams(-1, h); requestLayout() }
    }

    private fun sendReply() {
        val text = replyField.text.toString().trim()
        if (text.isEmpty()) return
        reply?.send?.invoke(text)
        replyField.setText("")
        context.getSystemService(InputMethodManager::class.java)
            ?.hideSoftInputFromWindow(replyField.windowToken, 0)
        onDoneTyping()
    }

    fun bind(a: IslandActivity, accent: Int) {
        accentNow = accent
        styleIconTile(icon, a, accent, 44)
        icon.setImageDrawable(iconOf(a))
        title.text = a.title
        sub.text = a.subtitle
        sub.visibility = if (a.subtitle.isEmpty()) GONE else VISIBLE
        header.setOnClickListener { IslandController.open(a) }
        for (v in listOf<View>(timer, ring, label)) v.visibility = GONE
        timerSpec = null
        when (val t = a.trailing) {
            is Trailing.Timer -> { timer.visibility = VISIBLE; timerSpec = t; timer.text = timerText(t) }
            is Trailing.Ring -> { ring.visibility = VISIBLE; ring.color = accent; ring.fraction = t.fraction }
            is Trailing.Label -> { label.visibility = VISIBLE; label.text = t.text }
            else -> {}
        }
        if (a.progress >= 0f) {
            bar.visibility = VISIBLE
            bar.progress = (a.progress * 1000).toInt()
            bar.progressTintList = ColorStateList.valueOf(accent)
        } else bar.visibility = GONE

        buttons.removeAllViews()
        buttons.visibility = if (a.buttons.isEmpty()) GONE else VISIBLE
        // many buttons, or long labels such as "Headphones" / a Bluetooth name: use the compact style so nothing is cut
        val compact = a.buttons.size > 3 || a.buttons.any { it.label.length > 9 }
        a.buttons.forEachIndexed { i, b ->
            buttons.addView(makeButton(b, compact), LayoutParams(0, context.dpi(if (compact) 40 else 46), 1f).apply {
                if (i > 0) marginStart = context.dpi(8)
            })
        }
        reply = a.reply
        replyRow.visibility = if (a.reply == null) GONE else VISIBLE
        replyField.hint = a.reply?.hint ?: ""
        replySend.background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(vivid(accent)) }
    }

    /** Neutral actions are capsules tinted with the accent; actions with their own colour (answer / decline) are solid. */
    private fun makeButton(b: ActionButton, compact: Boolean): TextView {
        val fill = if (b.color == 0) tint(accentNow, 64) else b.color
        return TextView(context).apply {
            text = b.label
            Type.title(this, if (compact) 13f else 15f)
            gravity = Gravity.CENTER
            setPadding(context.dpi(10), 0, context.dpi(10), 0)
            background = RippleDrawable(ColorStateList.valueOf(Color.argb(60, 255, 255, 255)),
                glassShape(context, fill, 100f, edge = b.color == 0), null)
            b.icon?.invoke()?.let {
                val s = context.dpi(18)
                it.setBounds(0, 0, s, s)
                it.setTint(Color.WHITE)
                setCompoundDrawables(it, null, null, null)
                compoundDrawablePadding = context.dpi(6)
            }
            isClickable = true
            setOnClickListener { b.run() }
        }
    }

    val hasTimer get() = timerSpec != null
    fun tick() { timerSpec?.let { timer.text = timerText(it) } }
    fun clearReply() { replyField.setText("") }

    /** Called once the island window has become focusable: focus the field and bring up the keyboard. */
    fun focusReply() {
        replyField.requestFocus()
        context.getSystemService(InputMethodManager::class.java)
            ?.showSoftInput(replyField, InputMethodManager.SHOW_IMPLICIT)
    }
}
