package app.orbit.cameraisland

import android.app.Activity
import android.content.ComponentName
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.view.Gravity
import android.view.View
import android.window.OnBackInvokedDispatcher
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.orbit.cameraisland.island.Icon
import app.orbit.cameraisland.island.IconDrawable
import app.orbit.cameraisland.island.IslandController
import app.orbit.cameraisland.island.dp
import app.orbit.cameraisland.island.dpi
import app.orbit.cameraisland.settings.ActivitiesScreen
import app.orbit.cameraisland.settings.AlertsScreen
import app.orbit.cameraisland.settings.AppearanceScreen
import app.orbit.cameraisland.settings.BatteryScreen
import app.orbit.cameraisland.settings.GesturesScreen
import app.orbit.cameraisland.settings.IslandPreviewCard
import app.orbit.cameraisland.settings.IslandPreviewCard.Demo
import app.orbit.cameraisland.settings.LiveScreen
import app.orbit.cameraisland.settings.MusicScreen
import app.orbit.cameraisland.settings.SetupChecklist
import app.orbit.cameraisland.settings.SetupScreen
import app.orbit.cameraisland.settings.Ui
import app.orbit.cameraisland.settings.UiKit

/**
 * The settings app. One screen: the live island preview on top (always visible, so every change is seen at once),
 * the page below, and four tabs. Opening an activity such as Music or Battery pushes its settings onto the same
 * screen and puts that situation on the preview.
 */
class MainActivity : Activity() {

    enum class Tab(val label: String, val icon: Icon, val demo: Demo) {
        LOOK("Look", Icon.SLIDERS, Demo.MUSIC_PILL),
        ACTIVITIES("Activities", Icon.BOLT, Demo.MESSAGE),
        GESTURES("Gestures", Icon.TOUCH, Demo.MUSIC_PILL),
        SETUP("Setup", Icon.GEAR, Demo.MUSIC_PILL),
    }

    enum class Section(val title: String, val subtitle: String, val icon: Icon, val color: Int, val demo: Demo, val build: (UiKit) -> Unit) {
        MUSIC("Music", "Pause behaviour", Icon.WAVEFORM, Color.rgb(0, 190, 180), Demo.MUSIC_OPEN, { MusicScreen.build(it) }),
        LIVE("Live activities", "Calls, timers, navigation, per-app rules", Icon.BOLT, Color.rgb(255, 149, 0), Demo.TIMER, { LiveScreen.build(it) }),
        BATTERY("Battery & charging", "Charging animation and alerts", Icon.BATTERY, Color.rgb(52, 199, 89), Demo.CHARGING, { BatteryScreen.build(it) }),
        ALERTS("System alerts", "Wi-Fi, Bluetooth, volume, privacy dots", Icon.BELL, Color.rgb(0, 122, 255), Demo.VOLUME, { AlertsScreen.build(it) }),
    }

    private lateinit var preview: IslandPreviewCard
    private lateinit var content: FrameLayout
    private lateinit var tabBar: LinearLayout
    private lateinit var strip: View
    private val tabButtons = HashMap<Tab, Pair<ImageView, TextView>>()
    private lateinit var setupBadge: View

    private val uis = ArrayList<UiKit>()
    private val pages = HashMap<Tab, View>()
    private var checklist: SetupChecklist? = null
    private val pushed = ArrayList<Pair<View, Section>>()
    private var tab = Tab.LOOK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.init(this)
        tab = Tab.values().getOrNull(savedInstanceState?.getInt("tab", 0) ?: 0) ?: Tab.LOOK

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(Ui.BG) }
        preview = IslandPreviewCard(this)
        root.addView(preview, LinearLayout.LayoutParams(-1, -2))

        strip = buildStrip()
        root.addView(strip, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dpi(14), dpi(10), dpi(14), 0) })

        content = FrameLayout(this)
        root.addView(content, LinearLayout.LayoutParams(-1, 0, 1f))

        tabBar = buildTabBar()
        root.addView(tabBar, LinearLayout.LayoutParams(-1, -2))

        setupInsets(root)
        setContentView(root)
        select(tab, firstTime = true)

        if (Build.VERSION.SDK_INT >= 33) {
            onBackInvokedDispatcher.registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT) {
                if (!popPage()) finish()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab.ordinal)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (Build.VERSION.SDK_INT >= 33) return   // handled by the back callback
        if (!popPage()) @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ---------------- window ----------------

    @Suppress("DEPRECATION")
    private fun setupInsets(root: View) {
        if (Build.VERSION.SDK_INT >= 30) window.setDecorFitsSystemWindows(false)
        Ui.systemBars(this)
        // the wallpaper under the status bar is always dark, so the status bar icons are always light
        window.decorView.systemUiVisibility = window.decorView.systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR.inv()
        if (Build.VERSION.SDK_INT >= 30) window.insetsController?.setSystemBarsAppearance(0, android.view.WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS)
        root.setOnApplyWindowInsetsListener { _, ins ->
            preview.setTopInset(ins.systemWindowInsetTop)
            tabBar.setPadding(0, 0, 0, ins.systemWindowInsetBottom)
            ins
        }
        root.requestApplyInsets()
    }

    // ---------------- chrome ----------------

    private fun buildTabBar(): LinearLayout {
        val line = View(this).apply { setBackgroundColor(Ui.DIVIDER) }
        val wrap = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        wrap.addView(line, LinearLayout.LayoutParams(-1, 1))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (t in Tab.values()) {
            val icon = ImageView(this).apply { setImageDrawable(IconDrawable(t.icon, Ui.TEXT2)) }
            val label = Ui.text(this, 11f, Ui.TEXT2, medium = true).apply { text = t.label; gravity = Gravity.CENTER; setPadding(0, dpi(3), 0, 0) }
            val iconBox = FrameLayout(this).apply {
                addView(icon, FrameLayout.LayoutParams(dpi(26), dpi(26), Gravity.CENTER))
                if (t == Tab.SETUP) {
                    setupBadge = View(context).apply {
                        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.BAD) }
                        visibility = View.GONE
                    }
                    addView(setupBadge, FrameLayout.LayoutParams(dpi(9), dpi(9), Gravity.TOP or Gravity.END).apply { setMargins(0, 0, dpi(-2), 0) })
                }
            }
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dpi(8), 0, dpi(8))
                background = Ui.ripple(null)
                isClickable = true
                addView(iconBox, LinearLayout.LayoutParams(dpi(30), dpi(28)))
                addView(label)
                setOnClickListener { select(t) }
            }
            tabButtons[t] = icon to label
            row.addView(item, LinearLayout.LayoutParams(0, -2, 1f))
        }
        wrap.addView(row, LinearLayout.LayoutParams(-1, -2))
        // the bottom system inset is added as padding on this container
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.SURFACE)
            addView(wrap, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun buildStrip(): View = LinearLayout(this).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dpi(16), dpi(12), dpi(12), dpi(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(16f)
            setColor(Color.argb(40, Color.red(Ui.WARN), Color.green(Ui.WARN), Color.blue(Ui.WARN)))
        }
        visibility = View.GONE
        isClickable = true
        addView(Ui.text(context, 15f, Ui.TEXT, medium = true).apply { text = "Finish setup to start using Orbit" },
            LinearLayout.LayoutParams(0, -2, 1f))
        addView(Ui.chevron(context, color = Ui.TEXT2))
        setOnClickListener { select(Tab.SETUP) }
    }

    private fun updateAttention() {
        val needs = SetupChecklist.needsAttention(this)
        setupBadge.visibility = if (needs) View.VISIBLE else View.GONE
        strip.visibility = if (needs && tab != Tab.SETUP && pushed.isEmpty()) View.VISIBLE else View.GONE
    }

    // ---------------- pages ----------------

    private fun pageFor(t: Tab): View = pages.getOrPut(t) {
        val ui = when (t) {
            Tab.LOOK -> UiKit(this, "Look", embedded = true).also { AppearanceScreen.build(it) }
            Tab.ACTIVITIES -> UiKit(this, "Activities", embedded = true).also { ActivitiesScreen.build(it) { s -> openSection(s) } }
            Tab.GESTURES -> UiKit(this, "Gestures", embedded = true).also { GesturesScreen.build(it) }
            Tab.SETUP -> UiKit(this, "Setup", embedded = true).also { checklist = SetupScreen.build(it); checklist?.refresh() }
        }
        uis.add(ui)
        ui.root
    }

    private fun select(t: Tab, firstTime: Boolean = false) {
        if (!firstTime && t == tab && pushed.isEmpty()) return
        tab = t
        pushed.clear()
        content.removeAllViews()
        val page = pageFor(t)
        (page.parent as? FrameLayout)?.removeView(page)
        content.addView(page, FrameLayout.LayoutParams(-1, -1))
        if (!firstTime) { page.alpha = 0f; page.animate().alpha(1f).setDuration(160).start() }
        for ((tb, v) in tabButtons) {
            val c = if (tb == t) Ui.ACCENT else Ui.TEXT2
            v.first.setImageDrawable(IconDrawable(tb.icon, c)); v.second.setTextColor(c)
        }
        preview.setDemo(t.demo, animate = !firstTime)
        updateAttention()
    }

    private fun openSection(s: Section) {
        val ui = UiKit(this, s.title, embedded = true).also { s.build(it) }
        uis.add(ui)
        val back = Ui.topBar(this, backLabel = tab.label, onBack = { popPage() }, showTheme = false)
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Ui.BG)
            addView(back)
            addView(ui.root, LinearLayout.LayoutParams(-1, 0, 1f))
        }
        pushed.add(page to s)
        content.addView(page, FrameLayout.LayoutParams(-1, -1))
        page.translationX = content.width.toFloat().coerceAtLeast(dp(400f))
        page.animate().translationX(0f).setDuration(260).start()
        preview.setDemo(s.demo)
        updateAttention()
    }

    private fun popPage(): Boolean {
        val top = pushed.removeLastOrNull() ?: return false
        top.first.animate().translationX(content.width.toFloat()).setDuration(220).withEndAction {
            content.removeView(top.first)
        }.start()
        preview.setDemo(pushed.lastOrNull()?.second?.demo ?: tab.demo)
        updateAttention()
        return true
    }

    // ---------------- lifecycle ----------------

    override fun onResume() {
        super.onResume()
        checklist?.refresh()
        uis.forEach { it.refresh() }
        updateAttention()
        if (SetupChecklist.listenerEnabled(this)) {
            val svc = IslandService.instance
            if (svc != null) svc.refresh()
            else NotificationListenerService.requestRebind(ComponentName(this, IslandService::class.java))
        }
        IslandController.recompute()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        checklist?.refresh()
        updateAttention()
    }
}
