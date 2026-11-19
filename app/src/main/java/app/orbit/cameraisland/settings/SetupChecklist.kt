package app.orbit.cameraisland.settings

import android.Manifest
import android.app.Activity
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.orbit.cameraisland.IslandAccessibilityService
import app.orbit.cameraisland.island.Icon
import app.orbit.cameraisland.island.IconDrawable
import app.orbit.cameraisland.island.dp

/**
 * Every permission Island can use, with live status and a button to grant each one.
 * Shown as one collapsible card: expanded while something needs attention, collapsed once all is well.
 */
class SetupChecklist(private val ui: UiKit) {
    private val act: Activity = ui.act

    private class Item(
        val label: String,
        val why: String,
        val required: Boolean,
        val granted: () -> Boolean,
        val grant: () -> Unit,
        val show: () -> Boolean = { true },
    ) {
        lateinit var row: View
        lateinit var dot: View
        lateinit var grantBtn: TextView
    }

    companion object {
        const val REQ_RUNTIME = 41
        fun listenerEnabled(a: Activity): Boolean {
            val flat = Settings.Secure.getString(a.contentResolver, "enabled_notification_listeners") ?: return false
            return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == a.packageName }
        }
        /** True while the island cannot work yet: notification access is missing, or nothing lets it draw on screen. */
        fun needsAttention(a: Activity) = !listenerEnabled(a) || !(accessibilityEnabled(a) || Settings.canDrawOverlays(a))

        fun accessibilityEnabled(a: Activity): Boolean {
            if (IslandAccessibilityService.instance != null) return true
            val flat = Settings.Secure.getString(a.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            return flat.split(":").any { ComponentName.unflattenFromString(it)?.packageName == a.packageName }
        }
    }

    private val items = ArrayList<Item>()
    private lateinit var headerDot: View
    private lateinit var headerTitle: TextView
    private lateinit var headerSummary: TextView
    private lateinit var headerChevron: ImageView
    private lateinit var list: LinearLayout
    private lateinit var restricted: View
    private var expanded = true
    private var userToggled = false

    /** Prominent disclosure (a Google Play requirement for sensitive access): the user must agree before we open the system screen. */
    private fun disclose(title: String, message: String, proceed: () -> Unit) {
        Ui.confirm(act, title, message, "Agree and continue", "Not now", proceed)
    }

    private fun runtime(vararg perms: String) = { act.requestPermissions(perms, REQ_RUNTIME) }
    private fun has(p: String) = act.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
    /** Round status glyph: a tick when fine, "!" when something is missing, an empty ring for optional items. */
    private fun statusBadge(sizeDp: Int) = ImageView(act).apply {
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ui.TEXT2) }
        val p = ui.dpi((sizeDp * 0.24f).toInt()); setPadding(p, p, p, p)
        layoutParams = LinearLayout.LayoutParams(ui.dpi(sizeDp), ui.dpi(sizeDp))
    }
    private fun setStatus(v: View, color: Int, ok: Boolean?) {
        (v.background as GradientDrawable).setColor(color)
        (v as ImageView).setImageDrawable(when (ok) { true -> IconDrawable(Icon.CHECK, Color.WHITE); false -> IconDrawable(Icon.EXCLAIM, Color.WHITE); null -> null })
    }

    fun build() {
        items += Item("Notification access", "Media controls, notifications, calls, timers.", true,
            { listenerEnabled(act) },
            {
                disclose("Notification access",
                    "Orbit reads your notifications and media sessions so it can show them on the island: messages, calls, " +
                        "timers, navigation and the music that is playing. It also lets you press their buttons and reply from the island.\n\n" +
                        "Everything stays on your device. Orbit has no internet access and never stores or sends your notifications.") {
                    act.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                }
            })
        items += Item("Accessibility service", "Draws over the status bar, runs gestures, hides in apps and while the notification panel is open.", false,
            { accessibilityEnabled(act) },
            {
                disclose("Accessibility service",
                    "Orbit uses Android's Accessibility API to:\n" +
                        "• draw the island over the status bar and lock screen\n" +
                        "• run the gestures you choose: home, recents, back, screenshot, lock screen, open the notification shade\n" +
                        "• notice which app is in front, whether it is full screen, and whether the notification shade is open, so the island can hide\n" +
                        "• if you turn it on, handle the volume keys for the island's volume slider\n" +
                        "• if you turn on \"Speaker button for app calls\", press the speaker button on a WhatsApp, Telegram or Signal call screen when you tap Speaker\n\n" +
                        "Apart from that one button, Orbit only looks at which windows are on screen (their type, size and app name), never at what is inside them. " +
                        "It does not read, record or send your screen, what you type or your passwords. Nothing leaves your device.") {
                    act.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            })
        items += Item("Display over other apps", "Only needed if the accessibility service is off.", false,
            { Settings.canDrawOverlays(act) },
            { act.startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:${act.packageName}"))) })
        items += Item("Unrestricted battery", "Stops Android from killing Orbit in the background.", false,
            { act.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(act.packageName) },
            // the list of apps (Orbit -> Unrestricted): the one-tap request needs a permission Google Play restricts
            { act.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) })
        items += Item("Bluetooth", "Show which Bluetooth device connected.", false,
            { has(Manifest.permission.BLUETOOTH_CONNECT) }, runtime(Manifest.permission.BLUETOOTH_CONNECT),
            show = { Build.VERSION.SDK_INT >= 31 })
        items += Item("Location", "Android only reveals the Wi-Fi name with it. Orbit never reads your location.", false,
            { has(Manifest.permission.ACCESS_FINE_LOCATION) },
            runtime(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        items += Item("Phone", "Answer or hang up through Android if a call notification has no button.", false,
            { has(Manifest.permission.ANSWER_PHONE_CALLS) }, runtime(Manifest.permission.ANSWER_PHONE_CALLS))

        // header row (tap to expand / collapse)
        headerDot = statusBadge(30)
        headerTitle = Ui.text(act, 17f, Ui.TEXT, medium = true).apply { text = "Permissions" }
        headerSummary = Ui.text(act, 13f, Ui.TEXT2).apply { setPadding(0, ui.dpi(2), 0, 0) }
        headerChevron = Ui.chevron(act)
        val texts = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            addView(headerTitle); addView(headerSummary)
        }
        val header = LinearLayout(act).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = ui.dpi(60)
            setPadding(ui.dpi(16), ui.dpi(10), ui.dpi(14), ui.dpi(10))
            background = Ui.ripple(null)
            isClickable = true
            addView(headerDot)
            addView(texts, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dpi(14) })
            addView(headerChevron)
            setOnClickListener { userToggled = true; expanded = !expanded; applyExpanded() }
        }
        ui.addRow(header)

        list = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL }
        for (it in items) {
            if (!it.show()) continue
            list.addView(View(act).apply { setBackgroundColor(Ui.DIVIDER) },
                LinearLayout.LayoutParams(-1, 1).apply { marginStart = ui.dpi(16) })
            it.dot = statusBadge(24)
            val label = Ui.text(act, 16f, Ui.TEXT).apply { text = it.label + if (it.required) "" else "  ·  Optional" }
            val why = Ui.text(act, 13f, Ui.TEXT2).apply { text = it.why; setPadding(0, ui.dpi(2), 0, 0) }
            it.grantBtn = Ui.text(act, 14f, Ui.ACCENT, medium = true).apply {
                text = "Grant"
                gravity = Gravity.CENTER
                setPadding(ui.dpi(16), ui.dpi(7), ui.dpi(16), ui.dpi(7))
                background = GradientDrawable().apply {
                    cornerRadius = act.dp(20f); setColor(Color.argb(38, Color.red(Ui.ACCENT), Color.green(Ui.ACCENT), Color.blue(Ui.ACCENT)))
                }
                setOnClickListener { _ -> it.grant() }
            }
            val texts2 = LinearLayout(act).apply { orientation = LinearLayout.VERTICAL; addView(label); addView(why) }
            it.row = LinearLayout(act).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(ui.dpi(16), ui.dpi(11), ui.dpi(14), ui.dpi(11))
                addView(it.dot)
                addView(texts2, LinearLayout.LayoutParams(0, -2, 1f).apply { marginStart = ui.dpi(14); marginEnd = ui.dpi(10) })
                addView(it.grantBtn)
            }
            list.addView(it.row)
        }

        restricted = LinearLayout(act).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(ui.dpi(16), ui.dpi(12), ui.dpi(16), ui.dpi(14))
            setBackgroundColor(Color.argb(28, Color.red(Ui.WARN), Color.green(Ui.WARN), Color.blue(Ui.WARN)))
            addView(Ui.text(act, 13f, Ui.WARN).apply {
                text = "Notification access or Accessibility greyed out (\"Restricted setting\")? Open App info → ⋮ menu → Allow restricted settings, then try again."
                setLineSpacing(0f, 1.15f)
            })
            addView(Ui.text(act, 15f, Ui.ACCENT, medium = true).apply {
                text = "Open app info"
                setPadding(0, ui.dpi(10), 0, 0)
                setOnClickListener {
                    act.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${act.packageName}")))
                }
            })
        }
        list.addView(restricted)
        ui.addRow(list, divider = true)
    }

    private fun applyExpanded() {
        list.visibility = if (expanded) View.VISIBLE else View.GONE
        headerChevron.animate().rotation(if (expanded) 90f else 0f).setDuration(150).start()
    }

    fun refresh() {
        var requiredOk = true
        for (it in items) {
            if (!it.show()) continue
            val g = it.granted()
            setStatus(it.dot, if (g) Ui.OK else if (it.required) Ui.BAD else Ui.SLIDER_TRACK, if (g) true else if (it.required) false else null)
            it.grantBtn.visibility = if (g) View.GONE else View.VISIBLE
            if (it.required && !g) requiredOk = false
        }
        val a11y = accessibilityEnabled(act)
        val overlayOk = a11y || Settings.canDrawOverlays(act)
        val allOk = requiredOk && overlayOk
        val needHelp = !listenerEnabled(act) || !a11y
        restricted.visibility = if (needHelp) View.VISIBLE else View.GONE

        val (title, color, summary) = when {
            !requiredOk -> Triple("Setup needed", Ui.BAD, "Grant notification access to start")
            !overlayOk -> Triple("Almost there", Ui.WARN, "Enable the accessibility service so the island can appear")
            else -> Triple("All set", Ui.OK, "Orbit is ready — play something")
        }
        headerTitle.text = title
        headerSummary.text = summary
        setStatus(headerDot, color, if (color == Ui.OK) true else false)
        if (!userToggled) expanded = !allOk
        applyExpanded()
    }
}
