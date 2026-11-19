package app.orbit.cameraisland.activities

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import app.orbit.cameraisland.IslandAccessibilityService
import app.orbit.cameraisland.island.Intents
import app.orbit.cameraisland.island.IslandController

/**
 * Speaker for calls from other apps (WhatsApp, Telegram, Signal...). Android lets only the app that owns a call change
 * its audio output, so the only way is to press that app's own speaker button. Opt-in ([app.orbit.cameraisland.settings.Prefs.APP_CALL_SPEAKER]):
 * when tapped, this opens the call screen if it isn't already in front, looks in that one app's window for a button
 * named "speaker" (by its id or its label), presses it, and goes back to where you were. Nothing else on screen is
 * read, and nothing is kept.
 */
object InAppSpeaker {
    private val handler = Handler(Looper.getMainLooper())

    /*
     * How the call apps name the button (the view id is tried first: WhatsApp's is "speaker_btn").
     * WhatsApp: string voip_call_speakerphone_toggle_description ("Toggle Speakerphone"), all its translations.
     * Signal: WebRtcAudioOutputToggle__speaker ("Speaker"), all its translations. Telegram: VoipSpeaker ("Speaker").
     */
    /** Whole labels: a button labelled exactly this is the speaker button. */
    private val exact = listOf(
        // WhatsApp
        "Toggle Speakerphone", "الانتقال إلى مكبّر الصوت", "Səsucaldana keç", "Високоговорител", "স্পীকারফোেন টগল করুন",
        "Altaveu", "Přepínač reproduktora", "Skift højttaler", "Auf Lautsprecher schalten", "Ανοιχτή Ακρόαση",
        "Cambiar a manos libres", "Lülita kõlar", "تغییر حالت بلندگو", "Kaiutin", "Activer le haut-parleur",
        "સ્પીકર ફોન શરૂ કરો", "החלף לרמקול", "टॉगल स्पीकरफ़ोन", "Odabir zvučnika", "Kihagosítás ki/be",
        "Mati/Nyalakan Speakerphone", "Attiva vivavoce", "スピーカーフォン切り替え", "Сыртқы динамикті қосу", "스피커 선택/취소",
        "Perjungti į Garsiakalbį", "Pārslēgt uz skaļruni", "Спикерфон копче", "സ്പീക്കർഫോണ്‍ വേണം/വേണ്ട", "स्पीकरफोन टॉगल करा",
        "Togol Pembesar Suara", "Slå på/av høyttaler", "Luidspreker aan/uit", "ਸਪੀਕਰਫੋਨ ਬਦਲੋ", "Przełącz głośnik",
        "Altifalante", "Habilitar/Desabilitar Viva Voz", "Schimbați difuzor", "Громкая связь", "Prepínač reproduktora",
        "Vklop/izklop zvočnika", "Ndiz altoparlantin", "Дугме Звучника", "Växla till högtalartelefon", "Wezesha kipaza sauti",
        "ஒலிபெருக்கிக்கு மாற்று", "స్పీకర్ ఫోన్ ఆన్ / ఆఫ్ చేయడానికి", "ตัวเปิดปิดลำโพง", "Hoparlör Aç/Kapa", "Увімкнути гучномовець",
        "اسپکر فون کو ٹاگل کریں", "Baland ovoz", "Bật/tắt loa ngoài", "切换扩音", "切換擴音",
        // Signal (and Telegram's "Speaker")
        "Speaker", "Luidspreker", "مُكبِّر الصوت", "Dinamik", "Дынамік", "Говорител", "স্পিকার", "Zvučnik", "Hlasitě",
        "Højttaler", "Lautsprecher", "Μεγάφωνο", "Altavoz", "Valjuhääldi", "Bozgorailua", "بلندگو", "Haut-parleur",
        "Callaire", "Altofalante", "સ્પીકર", "स्पीकर", "Kihangosító", "Pengeras Suara", "Vivavoce", "רמקול", "スピーカー",
        "სპიკერი", "Үндеткіш", "បំពងសំឡេង", "ಸ್ಪೀಕರ್", "스피커", "Динамик", "Garsiakalbis", "Skaļrunis", "Звучник",
        "സ്പീക്കർ", "Pembesar suara", "စပီကာ", "Høyttaler", "ਸਪੀਕਰ", "Głośnik", "Alto-falante", "Difuzor", "Reproduktor",
        "Zvočnik", "Altoparlant", "Högtalare", "Msemaji", "ஒலி பெருக்கி", "స్పీకర్", "ลำโพง", "Hoparlör", "ياڭراتقۇ",
        "Динамік", "سپیکر", "Loa", "喇叭", "扬声器", "揚聲器",
    ).map(::norm).toSet()

    /** Words that name the speaker inside a longer label ("Speaker on", "Turn on speakerphone"...); clickable buttons only. */
    private val contains = listOf(
        "speaker", "lautsprecher", "haut-parleur", "altavoz", "altaveu", "altifalante", "alto-falante", "altofalante",
        "vivavoce", "viva voz", "manos libres", "luidspreker", "kaiutin", "högtalar", "høyttaler", "højttaler", "głośnik",
        "reproduktor", "kihangosít", "difuzor", "zvučnik", "zvočnik", "звучник", "динамик", "динамік", "дынамік",
        "громкая связь", "гучномовець", "говорител", "hoparlör", "μεγάφωνο", "ανοιχτή ακρόαση", "loa ngoài",
        "pengeras suara", "pembesar suara", "garsiakalb", "skaļrun", "valjuhääldi", "kõlar", "bozgorailu", "callaire",
        "altoparlant", "kipaza sauti", "səsucaldan", "רמקול", "مكبر الصوت", "بلندگو", "سپیکر", "اسپکر", "स्पीकर",
        "スピーカー", "스피커", "扬声器", "揚聲器", "扩音", "擴音", "ลำโพง", "സ്പീക്കർ", "ಸ್ಪೀಕರ್", "స్పీకర్", "સ્પીકર",
        "ਸਪੀਕਰ", "ஒலிபெருக்கி", "ஒலி பெருக்கி", "স্পিকার", "স্পীকার", "სპიკერი", "үндеткіш", "စပီကာ", "បំពងសំឡេង",
    ).map(::norm)

    /** Lower case without accents / diacritics or soft hyphens (Signal's Dutch "Luid­spreker" has one). */
    private fun norm(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("[\\p{Mn}\\u00AD\\u200B-\\u200D]"), "").trim()

    private const val TIMEOUT_MS = 3000L
    private const val POLL_MS = 150L

    fun available() = IslandAccessibilityService.instance != null

    /** Presses the speaker button of [d]'s call app; [done] runs afterwards (also when it couldn't). */
    fun press(ctx: Context, d: NotifData, done: () -> Unit) {
        val svc = IslandAccessibilityService.instance ?: return done()
        val previous = IslandController.foregroundApp
        withViewIds(svc, true)
        val finish = { withViewIds(svc, false); done() }

        // the call screen is already in front: just press it
        findButton(svc, d.pkg)?.let { click(it); handler.postDelayed(finish, 500); return }

        val pi = d.n.contentIntent
        if (pi == null || !Intents.send(ctx, pi)) {
            val fs = d.n.fullScreenIntent
            if (fs == null || !Intents.send(ctx, fs)) Intents.openPackage(ctx, d.pkg)
        }
        val start = SystemClock.uptimeMillis()
        val poll = object : Runnable {
            override fun run() {
                val node = findButton(svc, d.pkg)
                if (node == null && SystemClock.uptimeMillis() - start < TIMEOUT_MS) {
                    handler.postDelayed(this, POLL_MS); return
                }
                node?.let { click(it) }
                // give the app a moment to act on the press before its screen goes to the background
                handler.postDelayed({ goBack(svc, ctx, previous, d.pkg); finish() }, if (node != null) 350 else 0)
            }
        }
        handler.postDelayed(poll, POLL_MS)
    }

    /** View ids are only reported while this flag is set, so it is on just for the moment of the press. */
    private fun withViewIds(svc: AccessibilityService, on: Boolean) {
        val info = svc.serviceInfo ?: return
        val f = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        info.flags = if (on) info.flags or f else info.flags and f.inv()
        try { svc.serviceInfo = info } catch (_: Exception) {}
    }

    private fun findButton(svc: AccessibilityService, pkg: String): AccessibilityNodeInfo? {
        val roots = try { svc.windows.mapNotNull { it.root } } catch (_: Exception) { emptyList() }
            .ifEmpty { listOfNotNull(svc.rootInActiveWindow) }
        var best: AccessibilityNodeInfo? = null
        var bestScore = 0
        fun visit(n: AccessibilityNodeInfo, depth: Int) {
            if (depth > 40 || bestScore == 3) return
            if (n.isVisibleToUser) {
                val s = score(n)
                if (s > bestScore) { best = n; bestScore = s }
            }
            for (i in 0 until n.childCount) n.getChild(i)?.let { visit(it, depth + 1) }
        }
        for (root in roots) if (root.packageName?.toString() == pkg) visit(root, 0)
        return best
    }

    /** 3: its view id names the speaker; 2: its label is exactly a known speaker label; 1: a button whose label mentions it. */
    private fun score(n: AccessibilityNodeInfo): Int {
        val id = n.viewIdResourceName?.substringAfter(":id/")?.lowercase()
        if (id != null && id.contains("speaker")) return 3
        val labels = listOfNotNull(n.contentDescription, n.text).map { norm(it.toString()) }.filter { it.isNotEmpty() }
        if (labels.any { it in exact }) return 2
        if (n.isClickable && labels.any { l -> l.length <= 40 && contains.any { l.contains(it) } }) return 1
        return 0
    }

    private fun click(n: AccessibilityNodeInfo) {
        var cur: AccessibilityNodeInfo? = n
        while (cur != null && !cur.isClickable) cur = cur.parent
        (cur ?: n).performAction(AccessibilityNodeInfo.ACTION_CLICK)
    }

    /** Back to the app that was in front before the call screen was opened (home if it was the launcher). */
    private fun goBack(svc: AccessibilityService, ctx: Context, previous: String?, callPkg: String) {
        when {
            previous == callPkg -> svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            previous == null || previous == homePackage(ctx) || !Intents.openPackage(ctx, previous) ->
                svc.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
        }
    }

    private fun homePackage(ctx: Context): String? = try {
        ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)
            ?.activityInfo?.packageName
    } catch (_: Exception) { null }
}
