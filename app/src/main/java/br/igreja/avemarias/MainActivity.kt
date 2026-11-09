package br.igreja.avemarias

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ClipDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class MainActivity : Activity() {
    private val GOAL = 1000
    private val BG = Color.parseColor("#0D1330"); private val PANEL = Color.parseColor("#172046")
    private val INK = Color.parseColor("#EEF0FA"); private val MUTE = Color.parseColor("#A3ACCB")
    private val GOLD = Color.parseColor("#E3B95B"); private val ACC = Color.parseColor("#9FB2FF")
    private val LINE = Color.parseColor("#2B3868"); private val BG2 = Color.parseColor("#080C20")
    private val PHRASES = listOf("nossa morte", "pecadores", "cheia de graça")
    private val LABELS = arrayOf("Resposta do povo: “nossa morte”", "Resposta do povo: “pecadores”", "Quem puxa: “cheia de graça”")
    private val AVISOS = intArrayOf(0, 10, 25, 50, 100)
    private val BR = Locale("pt", "BR")

    private lateinit var prefs: SharedPreferences
    private var count = 0; private var startedAt = 0L; private var endedAt = 0L; private var saved = false
    private var phraseIdx = 0; private var avInt = 50
    private val stamps = ArrayList<Long>(); private val undo = ArrayList<Int>()
    private var listening = false; private var offline = true; private var credited = 0; private var resume = false
    private var sr: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())

    private lateinit var tvN: TextView; private lateinit var tvDz: TextView; private lateinit var tvInfo: TextView
    private lateinit var tvSt: TextView; private lateinit var tvHeard: TextView; private lateinit var bar: ProgressBar
    private lateinit var btnMic: Button; private lateinit var btnPerm: Button
    private val beads = ArrayList<View>()
    private var lastVoice = 0L; private var wasListening = false

    private fun now() = System.currentTimeMillis()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun round(fill: Int, stroke: Int, r: Int = 16) = GradientDrawable().apply { cornerRadius = dp(r).toFloat(); setColor(fill); setStroke(dp(1), stroke) }
    private fun ripple(d: Drawable) = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), d, null)
    private fun lpH(w: Float, hh: Int) = LinearLayout.LayoutParams(0, dp(hh), w).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    private fun lpTop(w: Int, hh: Int, top: Int) = LinearLayout.LayoutParams(w, hh).apply { topMargin = dp(top) }
    private fun tv(t: String, sp: Float, c: Int = INK, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(c); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun btn(t: String, bg: Int = PANEL, fg: Int = INK, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(fg); textSize = 15f; stateListAnimator = null
        background = ripple(round(bg, if (bg == PANEL) LINE else bg, 20)); setOnClickListener { f() }
    }
    private fun bump() { tvN.animate().cancel(); tvN.scaleX = 1.07f; tvN.scaleY = 1.07f; tvN.animate().scaleX(1f).scaleY(1f).setDuration(180).start() }
    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = BG; window.navigationBarColor = BG2
        prefs = getSharedPreferences("ave", MODE_PRIVATE)
        count = prefs.getInt("count", 0); startedAt = prefs.getLong("start", 0); endedAt = prefs.getLong("end", 0)
        saved = prefs.getBoolean("saved", false); phraseIdx = prefs.getInt("phrase", 0).coerceIn(0, PHRASES.size - 1)
        avInt = prefs.getInt("avInt", 50)
        prefs.getString("stamps", "")!!.split(",").mapNotNull { it.toLongOrNull() }.forEach { stamps.add(it) }

        val serif = Typeface.create("serif", Typeface.BOLD)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(12), dp(20), dp(28)) }
        val sv = ScrollView(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(BG, BG2))
            fitsSystemWindows = true; addView(root)
        }
        setContentView(sv)

        tvSt = tv("", 12f, MUTE).apply { setPadding(dp(12), dp(6), dp(12), dp(6)) }
        val title = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(tv("Ave-Marias", 28f, INK, true).apply { gravity = Gravity.START; typeface = serif })
            addView(tv("Primeiro sábado · 1000 Ave-Marias", 12f, MUTE).apply { gravity = Gravity.START })
        }
        root.addView(LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(title, LinearLayout.LayoutParams(0, -2, 1f)); addView(tvSt)
        })

        tvN = tv("0", 112f, INK, true).apply { typeface = serif; includeFontPadding = false }
        tvDz = tv("", 17f, GOLD, true).apply { typeface = serif }
        tvInfo = tv("", 13f, MUTE)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = GOAL
            val track = GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(LINE) }
            val fill = ClipDrawable(GradientDrawable().apply { cornerRadius = dp(4).toFloat(); setColor(GOLD) }, Gravity.START, ClipDrawable.HORIZONTAL)
            progressDrawable = LayerDrawable(arrayOf<Drawable>(track, fill)).apply { setId(0, android.R.id.background); setId(1, android.R.id.progress) }
        }
        val beadRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        for (i in 0 until 10) {
            val b = View(this); beads.add(b)
            beadRow.addView(b, LinearLayout.LayoutParams(dp(26), dp(26)).apply { setMargins(dp(3), 0, dp(3), 0) })
        }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(16), dp(22), dp(16), dp(18))
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.parseColor("#1D2A6B"), PANEL)).apply {
                cornerRadius = dp(28).toFloat(); setStroke(dp(1), Color.parseColor("#3A4A8F")) }
            addView(tvN); addView(tv("de 1000", 14f, MUTE))
            addView(beadRow, lpTop(-2, -2, 16)); addView(tvDz, lpTop(-2, -2, 16))
            addView(bar, lpTop(-1, dp(8), 12)); addView(tvInfo, lpTop(-2, -2, 10))
        }
        root.addView(hero, lpTop(-1, -2, 18))

        btnPerm = btn("Permitir microfone", PANEL, GOLD) { askMic() }
        btnMic = btn("Começar a ouvir", ACC, BG) { setListen(!listening) }.apply { textSize = 19f; typeface = Typeface.DEFAULT_BOLD }
        root.addView(btnPerm, lpTop(-1, dp(56), 16)); root.addView(btnMic, lpTop(-1, dp(68), 10))
        val ctrls = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            addView(btn("−1") { change(-1) }.apply { textSize = 22f }, lpH(1f, 72))
            addView(btn("+1", GOLD, BG) { change(1) }.apply { textSize = 36f; typeface = Typeface.DEFAULT_BOLD }, lpH(2.4f, 72))
            addView(btn("Desfazer") { if (undo.isNotEmpty()) change(-undo.removeAt(undo.lastIndex), true) }, lpH(1f, 72))
        }
        root.addView(ctrls, lpTop(-1, -2, 12))
        tvHeard = tv("Toque no número grande para corrigir a contagem.", 13f, MUTE).apply { setPadding(dp(8), dp(14), dp(8), dp(4)) }
        root.addView(tvHeard)
        root.addView(btn("Ajustes", Color.TRANSPARENT, MUTE) { ajustes() }, lpTop(-1, -2, 4))
        tvN.setOnClickListener { editCount() }
        render(); ui(); h.postDelayed(ticker, 1000)
    }

    private val ticker = object : Runnable {
        override fun run() { tvInfo.text = infoText(); h.postDelayed(this, 1000) }
    }

    private fun save() {
        prefs.edit().putInt("count", count).putLong("start", startedAt).putLong("end", endedAt).putBoolean("saved", saved)
            .putInt("phrase", phraseIdx).putInt("avInt", avInt)
            .putString("stamps", stamps.takeLast(100).joinToString(",")).apply()
    }
    private fun pos(c: Int): IntArray = if (c == 0) intArrayOf(1, 1, 0) else { val i = c - 1; val w = i % 50; intArrayOf(i / 50 + 1, w / 10 + 1, w % 10 + 1) }
    private fun dur(ms: Long): String { val t = (ms / 1000).coerceAtLeast(0); val hh = t / 3600; val m = t % 3600 / 60
        return if (hh > 0) "${hh}h ${"%02d".format(m)}min" else "${m}min ${"%02d".format(t % 60)}s" }
    private fun rate(): Double { val n = stamps.size; if (n < 10) return 0.0
        val dt = (stamps[n - 1] - stamps[0]) / 60000.0; return if (dt > 0) (n - 1) / dt else 0.0 }
    private fun infoText(): String {
        if (startedAt == 0L) return ""
        val p = arrayListOf(dur((if (endedAt != 0L) endedAt else now()) - startedAt)); val r = rate()
        if (r > 0 && count < GOAL) {
            p.add("${r.roundToInt()} por min")
            p.add("termina às " + SimpleDateFormat("HH:mm", BR).format(Date(now() + ((GOAL - count) / r * 60000).toLong())))
        }
        return p.joinToString(" · ")
    }
    private fun bead(on: Boolean) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(2), if (on) GOLD else Color.parseColor("#3D4C8F")); setColor(if (on) GOLD else Color.TRANSPARENT) }

    private fun render() {
        val p = pos(count)
        val txt = if (count >= GOAL) "Mil Ave-Marias rezadas. Amém!" else "Terço ${p[0]} de 20 · Dezena ${p[1]} de 5"
        tvN.text = "$count"; tvDz.text = txt; bar.progress = count; tvInfo.text = infoText()
        for (i in 0 until 10) beads[i].background = bead(i < p[2])
    }
    private fun ui() {
        btnMic.text = if (listening) "Pausar escuta" else "Começar a ouvir"
        btnMic.background = ripple(round(if (listening) GOLD else ACC, if (listening) GOLD else ACC, 24))
        tvSt.text = if (listening) "●  Ouvindo" else "Microfone desligado"
        tvSt.setTextColor(if (listening) GOLD else MUTE)
        tvSt.background = round(PANEL, if (listening) GOLD else LINE, 20)
        btnPerm.visibility = if (hasMic()) View.GONE else View.VISIBLE
    }
    private fun buzz(p: LongArray) { try { (getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createWaveform(p, -1)) } catch (_: Exception) {} }

    private fun change(delta: Int, nh: Boolean = false) {
        val prev = count; count = (count + delta).coerceIn(0, GOAL); if (count == prev) return
        val d = count - prev
        if (d > 0) { if (startedAt == 0L) startedAt = now(); repeat(d) { stamps.add(now()) }; while (stamps.size > 100) stamps.removeAt(0) }
        else repeat(minOf(-d, stamps.size)) { stamps.removeAt(stamps.lastIndex) }
        endedAt = if (count >= GOAL) (if (endedAt != 0L) endedAt else now()) else 0L
        if (!nh) { undo.add(d); if (undo.size > 30) undo.removeAt(0) }
        save(); render(); bump()
        if (d > 0) {
            if (count >= GOAL) { buzz(longArrayOf(0, 300, 100, 300, 100, 500)); saveSession() }
            else {
                if (count / 10 > prev / 10) buzz(longArrayOf(0, 120, 60, 120))
                if (avInt > 0 && count / avInt > prev / avInt) aviso()
            }
        }
    }
    private fun aviso() {
        if (listening) { resume = true; setListen(false) }
        buzz(longArrayOf(0, 200, 100, 200))
        AlertDialog.Builder(this).setTitle("Terço concluído").setMessage("$count Ave-Marias rezadas. Hora do canto?")
            .setCancelable(false).setPositiveButton("Continuar") { _, _ -> if (resume) { resume = false; setListen(true) } }.show()
    }

    // ---- microfone e voz ----
    private fun askMic() {
        if (hasMic()) { ui(); return }
        if (shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO) || !prefs.getBoolean("asked", false)) {
            prefs.edit().putBoolean("asked", true).apply()
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
        } else {
            Toast.makeText(this, "Libere o microfone nas configurações do app", Toast.LENGTH_LONG).show()
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")))
        }
    }
    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res); ui()
        if (hasMic()) tvHeard.text = "Microfone liberado. Agora toque em Começar a ouvir."
    }
    override fun onResume() { super.onResume(); ui(); if (wasListening && hasMic()) { wasListening = false; setListen(true) } }
    override fun onStop() { if (listening) { wasListening = true; setListen(false) }; super.onStop() }
    override fun onDestroy() { mute(false); sr?.destroy(); h.removeCallbacksAndMessages(null); super.onDestroy() }

    private fun mute(on: Boolean) { try { (getSystemService(Context.AUDIO_SERVICE) as AudioManager)
        .adjustStreamVolume(AudioManager.STREAM_MUSIC, if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE, 0) } catch (_: Exception) {} }
    private fun recIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR"); putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, offline); putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
    }
    private fun setListen(on: Boolean) {
        if (on) {
            if (!hasMic()) { askMic(); return }
            if (!SpeechRecognizer.isRecognitionAvailable(this)) { tvHeard.text = "Este aparelho não tem reconhecimento de voz. Instale ou atualize o app Google."; return }
            if (sr == null) { sr = SpeechRecognizer.createSpeechRecognizer(this); sr!!.setRecognitionListener(listener) }
            listening = true; ui(); mute(true); credited = 0; sr!!.startListening(recIntent())
        } else { listening = false; ui(); mute(false); sr?.cancel() }
    }
    private fun restart(delay: Long = 350) {
        if (!listening) return
        credited = 0
        h.postDelayed({ if (listening) { sr?.cancel(); sr?.startListening(recIntent()) } }, delay)
    }
    private fun handle(b: Bundle?) {
        val t = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase(BR) ?: return
        tvHeard.text = "Ouvindo: …" + t.takeLast(70)
        val m = t.split(PHRASES[phraseIdx]).size - 1
        if (m > credited) {
            val d = m - credited; credited = m
            if (now() - lastVoice >= 3000) { lastVoice = now(); change(d) }
        }
    }
    private val listener = object : RecognitionListener {
        override fun onPartialResults(b: Bundle?) = handle(b)
        override fun onResults(b: Bundle?) { handle(b); restart() }
        override fun onError(e: Int) {
            if (e == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) { setListen(false); askMic(); return }
            if ((e == 12 || e == 13) && offline) offline = false
            restart(if (e == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) 900 else 350)
        }
        override fun onReadyForSpeech(p: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(v: Float) {}
        override fun onBufferReceived(b: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(t: Int, b: Bundle?) {}
    }

    // ---- telão, ajustes e histórico ----
    private fun pickPhrase() = AlertDialog.Builder(this).setTitle("Frase que conta uma Ave-Maria")
        .setSingleChoiceItems(LABELS, phraseIdx) { d, w -> phraseIdx = w; save(); d.dismiss() }.show()
    private fun pickAviso() = AlertDialog.Builder(this).setTitle("Avisar a cada")
        .setSingleChoiceItems(AVISOS.map { if (it == 0) "Sem aviso" else "$it Ave-Marias" }.toTypedArray(), AVISOS.indexOf(avInt).coerceAtLeast(0)) { d, w -> avInt = AVISOS[w]; save(); d.dismiss() }.show()
    private fun editCount() {
        val et = EditText(this).apply { inputType = InputType.TYPE_CLASS_NUMBER; setText("$count"); setSelectAllOnFocus(true); setPadding(dp(20), dp(16), dp(20), dp(16)) }
        AlertDialog.Builder(this).setTitle("Quantas Ave-Marias já foram rezadas?").setView(et)
            .setPositiveButton("Salvar") { _, _ -> et.text.toString().toIntOrNull()?.let { setExact(it) } }
            .setNegativeButton("Cancelar", null).show()
    }
    private fun setExact(n: Int) {
        val v = n.coerceIn(0, GOAL); if (v == count) return
        undo.add(v - count); if (undo.size > 30) undo.removeAt(0)
        count = v; if (count > 0 && startedAt == 0L) startedAt = now()
        endedAt = if (count >= GOAL) (if (endedAt != 0L) endedAt else now()) else 0L
        stamps.clear(); save(); render()
    }
    private fun ajustes() = AlertDialog.Builder(this).setTitle("Ajustes")
        .setItems(arrayOf("Frase que conta: " + LABELS[phraseIdx].substringAfter(": "), "Aviso a cada: " + (if (avInt == 0) "sem aviso" else "$avInt"), "Histórico", "Encerrar contagem")) { _, w ->
            when (w) { 0 -> pickPhrase(); 1 -> pickAviso(); 2 -> showHist(); else -> endDialog() } }.show()
    private fun showHist() {
        val lines = (prefs.getString("hist", "") ?: "").split("\n").filter { it.isNotBlank() }.map {
            val p = it.split("|"); SimpleDateFormat("d MMM yyyy", BR).format(Date(p[0].toLong())) + ": " + p[1] + " Ave-Marias em " + dur(p[2].toLong()) }
        AlertDialog.Builder(this).setTitle("Histórico").setMessage(if (lines.isEmpty()) "Nenhum registro ainda." else lines.joinToString("\n\n"))
            .setPositiveButton("Fechar", null).show()
    }
    private fun saveSession() {
        if (count < 1 || saved) return; saved = true
        val ms = if (startedAt > 0) (if (endedAt > 0) endedAt else now()) - startedAt else 0
        prefs.edit().putString("hist", "${now()}|$count|$ms\n" + (prefs.getString("hist", "") ?: "")).apply(); save()
    }
    private fun clearSession() { count = 0; startedAt = 0; endedAt = 0; stamps.clear(); saved = false; undo.clear(); save(); render() }
    private fun endDialog() = AlertDialog.Builder(this).setTitle("Encerrar contagem").setMessage("Salvar no histórico e zerar?")
        .setPositiveButton("Salvar e zerar") { _, _ -> saveSession(); clearSession() }
        .setNeutralButton("Zerar sem salvar") { _, _ -> clearSession() }.setNegativeButton("Cancelar", null).show()
}
