package app.orbit.cameraisland.settings

import app.orbit.cameraisland.MainActivity

/** The Activities tab: everything the island can show, one row each. Opening one also puts it on the preview. */
object ActivitiesScreen {
    fun build(ui: UiKit, open: (MainActivity.Section) -> Unit) {
        ui.section("What the island shows")
        for (s in MainActivity.Section.values()) ui.link(s.title, s.subtitle, s.icon, s.color) { open(s) }
        ui.note("Tap the island at the top to preview each of these as it will look on your screen.")
    }
}
