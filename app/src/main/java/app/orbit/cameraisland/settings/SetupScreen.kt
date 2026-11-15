package app.orbit.cameraisland.settings

/** The Setup tab: permissions, when the island is allowed to show, the app's own theme, and version. */
object SetupScreen {
    fun build(ui: UiKit): SetupChecklist {
        ui.section("Permissions")
        val checklist = SetupChecklist(ui)
        checklist.build()

        ui.section("App")
        ui.choice("Theme", Prefs.THEME, "system", listOf("system" to "Match system", "light" to "Light", "dark" to "Dark")) {
            ui.act.recreate()
        }

        EverydayScreen.build(ui)

        val version = try { ui.act.packageManager.getPackageInfo(ui.act.packageName, 0).versionName } catch (_: Exception) { "" }
        ui.section("About")
        ui.note("Orbit $version. Everything stays on your device: Orbit has no internet access.")
        return checklist
    }
}
