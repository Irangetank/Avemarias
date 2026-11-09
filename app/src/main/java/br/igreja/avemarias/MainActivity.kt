package br.igreja.avemarias

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.util.TypedValue
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
    private val LINE = Color.parseColor("#2B3868")
    private val PHRASES = listOf("nossa morte", "pecadores", "cheia de graça")
    private val LABELS = arrayOf("Resposta do povo: “nossa morte”", "Resposta do povo: “pecadores”", "Quem puxa: “cheia de graça”")
    private val AVISOS = intArrayOf(0, 10, 25, 50, 100)
    private val BR = Locale("pt", "BR")

    private lateinit var prefs: SharedPreferences
    private var count = 0; private var startedAt = 0L; private var endedAt = 0L; private var saved = false
    private var phraseIdx = 0; private var avInt = 50; private var intencao = ""
    private val stamps = ArrayList<Long>(); private val undo = ArrayList<Int>()
    private var listening = false; private var offline = true; private var credited = 0; private var resume = false
    private var sr: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())

    private lateinit var tvN: TextView; private lateinit var tvDz: TextView; private lateinit var tvInfo: TextView
    private lateinit var tvSt: TextView; private lateinit var tvHeard: TextView; private lateinit var bar: ProgressBar
    private lateinit var btnMic: Button; private lateinit var btnPerm: Button
    private val beads = ArrayList<View>()
    private var telao: Dialog? = null
    private var tN: TextView? = null; private var tDz: TextView? = null; private var tInt: TextView? = null; private var tInfo: TextView? = null

    private fun now() = System.currentTimeMillis()
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun round(fill: Int, stroke: Int) = GradientDrawable().apply { cornerRadius = dp(14).toFloat(); setColor(fill); setStroke(dp(1), stroke) }
    private fun lp(w: Float) = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) }
    private fun tv(t: String, sp: Float, c: Int = INK, bold: Boolean = false) = TextView(this).apply {
        text = t; textSize = sp; setTextColor(c); gravity = Gravity.CENTER
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }
    private fun btn(t: String, bg: Int = PANEL, fg: Int = INK, f: () -> Unit) = Button(this).apply {
        text = t; isAllCaps = false; setTextColor(fg); background = round(bg, if (bg == PANEL) LINE else bg)
        setOnClickListener { f() }
    }
    private fun row(vararg v: Pair<View, Float>) = LinearLayout(this).apply { v.forEach { addView(it.first, lp(it.second)) } }
    private fun hasMic() = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        window.statusBarColor = BG; window.navigationBarColor = BG
        prefs = getSharedPreferences("ave", MODE_PRIVATE)
        count = prefs.getInt("count", 0); startedAt = prefs.getLong("start", 0); endedAt = prefs.getLong("end", 0)
        saved = prefs.getBoolean("saved", false); phraseIdx = prefs.getInt("phrase", 0).coerceIn(0, PHRASES.size - 1)
        avInt = prefs.getInt("avInt", 50); intencao = prefs.getString("int", "") ?: ""
        prefs.getString("stamps", "")!!.split(",").mapNotNull { it.toLongOrNull() }.forEach { stamps.add(it) }

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16), dp(12), dp(16), dp(24)) }
        val sv = ScrollView(this).apply { setBackgroundColor(BG); fitsSystemWindows = true; addView(root) }
        setContentView(sv)

        tvSt = tv("Microfone desligado", 13f, MUTE).apply { gravity = Gravity.END }
        root.addView(tv("Ave-Marias", 26f, INK, true).apply { gravity = Gravity.START; typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) })
        root.addView(tvSt)

        tvN = tv("0", 96f, INK, true).apply { typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD) }
        tvDz = tv("", 16f, INK, true)
        tvInfo = tv("", 13f, MUTE)
        bar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { max = GOAL; progressTintList = ColorStateList.valueOf(ACC) }
        val beadRow = LinearLayout(this).apply { gravity = Gravity.CENTER }
        for (i in 0 until 10) {
            val b = View(this); beads.add(b)
            beadRow.addView(b, LinearLayout.LayoutParams(dp(24), dp(24)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(14), dp(14), dp(14), dp(14))
            background = round(PANEL, LINE)
            addView(tvN); addView(tv("de 1000", 14f, MUTE)); addView(beadRow); addView(tvDz); addView(bar); addView(tvInfo)
        }
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })

        btnPerm = btn("Permitir microfone", PANEL, INK) { askMic() }
        btnMic = btn("Começar a ouvir", ACC, BG) { setListen(!listening) }
        root.addView(btnPerm, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(10) })
        root.addView(btnMic, LinearLayout.LayoutParams(-1, dp(64)).apply { topMargin = dp(6) })

        root.addView(row(
            btn("−1") { change(-1) } to 1f,
            btn("+1", PANEL, INK) { change(1) }.apply { textSize = 26f } to 2f,
            btn("Desfazer") { if (undo.isNotEmpty()) change(-undo.removeAt(undo.lastIndex), true) } to 1f))
        tvHeard = tv("Toque em começar e deixe o celular perto de quem puxa a oração.", 13f, MUTE)
        root.addView(tvHeard)
        root.addView(row(btn("Telão") { showTelao() } to 1f, btn("Frase") { pickPhrase() } to 1f, btn("Aviso") { pickAviso() } to 1f))
        root.addView(row(btn("Intenções") { editIntencao() } to 1f, btn("Histórico") { showHist() } to 1f, btn("Encerrar") { endDialog() } to 1f))

        render(); ui(); h.postDelayed(ticker, 1000)
    }

    private val ticker = object : Runnable {
        override fun run() { tvInfo.text = infoText(); tInfo?.text = tvInfo.text; h.postDelayed(this, 1000) }
    }

    private fun save() {
        prefs.edit().putInt("count", count).putLong("start", startedAt).putLong("end", endedAt).putBoolean("saved", saved)
            .putInt("phrase", phraseIdx).putInt("avInt", avInt).putString("int", intencao)
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
    private fun bead(on: Boolean) = GradientDrawable().apply { shape = GradientDrawable.OVAL; setStroke(dp(2), GOLD); setColor(if (on) GOLD else Color.TRANSPARENT) }

    private fun render() {
        val p = pos(count)
        val txt = if (count >= GOAL) "Mil Ave-Marias rezadas. Amém!" else "Terço ${p[0]} de 20 · Dezena ${p[1]} de 5"
        tvN.text = "$count"; tvDz.text = txt; bar.progress = count; tvInfo.text = infoText()
        for (i in 0 until 10) beads[i].background = bead(i < p[2])
        tN?.text = "$count"; tDz?.text = txt; tInt?.text = intencao; tInfo?.text = tvInfo.text
    }
    private fun ui() {
        btnMic.text = if (listening) "Pausar escuta" else "Começar a ouvir"
        btnMic.background = round(if (listening) GOLD else ACC, if (listening) GOLD else ACC)
        tvSt.text = if (listening) "Ouvindo…" else "Microfone desligado"; tvSt.setTextColor(if (listening) GOLD else MUTE)
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
        save(); render()
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
    override fun onResume() { super.onResume(); ui() }
    override fun onStop() { if (listening) setListen(false); super.onStop() }
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
        if (m > credited) { val d = m - credited; credited = m; change(d) }
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
    private fun showTelao() {
        val d = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setBackgroundColor(BG); setPadding(dp(16), dp(16), dp(16), dp(16)) }
        tN = tv("", 100f, INK, true).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            setAutoSizeTextTypeUniformWithConfiguration(60, 420, 4, TypedValue.COMPLEX_UNIT_SP)
        }
        tDz = tv("", 24f, INK, true); tInt = tv("", 28f, GOLD, true); tInfo = tv("", 18f, MUTE)
        l.addView(tN, LinearLayout.LayoutParams(-1, 0, 1f)); l.addView(tDz); l.addView(tInt); l.addView(tInfo)
        d.setContentView(l); l.setOnClickListener { d.dismiss() }
        d.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        d.setOnDismissListener { telao = null; tN = null; tDz = null; tInt = null; tInfo = null }
        telao = d; d.show(); render()
        Toast.makeText(this, "Toque na tela para sair do telão", Toast.LENGTH_SHORT).show()
    }
    private fun pickPhrase() = AlertDialog.Builder(this).setTitle("Frase que conta uma Ave-Maria")
        .setSingleChoiceItems(LABELS, phraseIdx) { d, w -> phraseIdx = w; save(); d.dismiss() }.show()
    private fun pickAviso() = AlertDialog.Builder(this).setTitle("Avisar a cada")
        .setSingleChoiceItems(AVISOS.map { if (it == 0) "Sem aviso" else "$it Ave-Marias" }.toTypedArray(), AVISOS.indexOf(avInt).coerceAtLeast(0)) { d, w -> avInt = AVISOS[w]; save(); d.dismiss() }.show()
    private fun editIntencao() {
        val et = EditText(this).apply { setText(intencao); hint = "Ex.: Pelos doentes da comunidade"; setPadding(dp(20), dp(16), dp(20), dp(16)) }
        AlertDialog.Builder(this).setTitle("Intenções do dia (aparecem no telão)").setView(et)
            .setPositiveButton("Salvar") { _, _ -> intencao = et.text.toString(); save(); render() }.setNegativeButton("Cancelar", null).show()
    }
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
