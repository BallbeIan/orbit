package app.orbit.cameraisland.settings

object LiveScreen {
    fun build(ui: UiKit) {
        ui.section("Live activities")
        ui.note("Ongoing and important notifications become live activities with working buttons. Tap to expand and use them; tap the header to open the app.")
        ui.toggle("Calls", Prefs.LIVE_CALLS, true, "Caller, Answer / Decline, call timer, mute, speaker, hang up.")
        lateinit var speakerSwitch: IosSwitch
        speakerSwitch = ui.toggle("Speaker button for app calls", Prefs.APP_CALL_SPEAKER, false,
            "Android doesn't let other apps switch a WhatsApp, Telegram or Signal call to the speaker. With this on, " +
            "the Speaker button briefly opens the call screen, presses the app's own speaker button and takes you back. " +
            "To do that Orbit looks for that one button inside the call app's window, only when you tap it. Needs the accessibility service.") { on ->
            if (!on) return@toggle
            // Prominent disclosure (Play policy): it stays off unless the user agrees.
            speakerSwitch.isChecked = false
            ui.prefs.edit().putBoolean(Prefs.APP_CALL_SPEAKER, false).apply()
            Ui.confirm(ui.act, "Speaker button for app calls",
                "When you tap Speaker on a call from an app such as WhatsApp, Telegram or Signal, Orbit uses the Accessibility " +
                    "service to open that app's call screen, find its speaker button, press it, and take you back.\n\n" +
                    "Orbit looks inside the call app's window only for that button, only at the moment you tap Speaker. " +
                    "It reads nothing else, stores nothing, and nothing leaves your device.",
                "Agree and turn on", "Not now") {
                ui.prefs.edit().putBoolean(Prefs.APP_CALL_SPEAKER, true).apply()
                speakerSwitch.isChecked = true
            }
        }
        ui.toggle("Timers & stopwatches", Prefs.LIVE_TIMERS, true, "Live-counting timer in the pill, with pause / stop buttons.")
        ui.toggle("Navigation", Prefs.LIVE_NAV, true, "Next turn, distance and instruction from Maps and similar apps.")
        ui.toggle("Progress, rides, deliveries, downloads", Prefs.LIVE_PROGRESS, true, "Progress ring in the pill; Android 16 Live Updates are used when present.")
        ui.toggle("Alarm ringing", Prefs.LIVE_ALARM, true, "Snooze and Dismiss straight from the island.")
        ui.toggle("Other ongoing notifications (apps set to Live)", Prefs.LIVE_OTHER, true)
        ui.section("Notification peeks")
        ui.toggle("Show notifications in the island", Prefs.PEEK, true, "Messages can be answered inline when the app supports quick reply.")
        ui.section("Per app")
        ui.valueRow("Per-app behaviour", "Auto, peek only, live activity or off",
            { val n = ui.prefs.all.keys.count { it.startsWith(Prefs.APP_MODE_PREFIX) }; if (n == 0) "All auto" else "$n customised" }) {
            AppListActivity.open(ui.act, "modes", "", "Apps",
                "Auto: smart · Peek: short banner only · Live: stays interactive · Off: never shown")
        }
    }
}
