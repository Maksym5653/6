package com.mwai.overlay

import android.app.*
import android.content.Intent
import android.graphics.*
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.util.Base64
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.core.app.NotificationCompat
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class OverlayService : Service() {

    companion object {
        var isRunning = false
        const val CH = "mwai_ch"
        const val GEMINI = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent"
    }

    private lateinit var wm: WindowManager
    private var fabView: View? = null
    private var panelView: View? = null

    // Ghost: separate view + its own position + tap tracker
    private var ghostView: View? = null
    private var ghostX = 24
    private var ghostY = 0
    private var ghostLastTap = 0L

    private var fabSavedX = 24
    private var fabSavedY = 0
    private var panelOpen = false
    private var lastFabTap = 0L
    private val DBL = 380L

    private var projCode = -1
    private var projData: Intent? = null
    private var projection: MediaProjection? = null
    private var vDisplay: VirtualDisplay? = null
    private var imgReader: ImageReader? = null

    private val h = Handler(Looper.getMainLooper())
    private var sw = 0; var sh = 0

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS).build()

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        sw = resources.displayMetrics.widthPixels
        sh = resources.displayMetrics.heightPixels
        fabSavedY = sh / 3; ghostY = sh / 3
        createCh(); startForeground(1, buildNote())
    }

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        if (i?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        projCode = i?.getIntExtra("result_code", -1) ?: -1
        projData = i?.getParcelableExtra("data")
        if (projCode != -1 && projData != null) initProjection()
        showFab()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy(); isRunning = false
        listOf(fabView, panelView, ghostView).forEach { safeRm(it) }
        try { vDisplay?.release() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        try { imgReader?.close() } catch (_: Exception) {}
    }

    override fun onBind(i: Intent?) = null

    // ─── PROJECTION ───────────────────────────────────────────────

    private fun initProjection() {
        try {
            safeCleanProjection()
            projection = (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager)
                .getMediaProjection(projCode, projData!!)
            imgReader = ImageReader.newInstance(sw, sh, PixelFormat.RGBA_8888, 2)
            vDisplay = projection!!.createVirtualDisplay(
                "mwai", sw, sh, resources.displayMetrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imgReader!!.surface, null, null)
        } catch (e: Exception) {
            projection = null
            logErr("initProjection: ${e.message}")
        }
    }

    private fun safeCleanProjection() {
        try { vDisplay?.release() } catch (_: Exception) {}
        try { projection?.stop() } catch (_: Exception) {}
        try { imgReader?.close() } catch (_: Exception) {}
    }

    // ─── FAB ──────────────────────────────────────────────────────

    private fun showFab() {
        if (fabView != null) return
        fabView = LayoutInflater.from(this).inflate(R.layout.view_fab, null)
        val lp = fabLP()
        var ix=0; var iy=0; var tx=0f; var ty=0f; var drag=false

        fabView!!.setOnTouchListener { _, e ->
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { ix=lp.x; iy=lp.y; tx=e.rawX; ty=e.rawY; drag=false; true }
                MotionEvent.ACTION_MOVE -> {
                    if (Math.abs(e.rawX-tx)>10||Math.abs(e.rawY-ty)>10) {
                        drag=true; lp.x=(ix-(e.rawX-tx)).toInt(); lp.y=(iy+(e.rawY-ty)).toInt()
                        try{wm.updateViewLayout(fabView,lp)}catch(_:Exception){}
                    }; true
                }
                MotionEvent.ACTION_UP -> {
                    if (!drag) {
                        fabSavedX=lp.x; fabSavedY=lp.y
                        val now = System.currentTimeMillis()
                        val tapMode = AppPrefs.getTap(this)
                        if (tapMode == "single") {
                            // single = analyze, very fast double = hide
                            if (now - lastFabTap < 200) { animFabOut { showGhost() } }
                            else { ripple(lp.x,lp.y); if(panelOpen) hidePanel() else capture() }
                        } else {
                            if (now - lastFabTap < DBL) { animFabOut { showGhost() } }
                            else {
                                h.postDelayed({
                                    if (System.currentTimeMillis()-lastFabTap >= DBL) {
                                        ripple(lp.x,lp.y); if(panelOpen) hidePanel() else capture()
                                    }
                                }, DBL)
                            }
                        }
                        lastFabTap = now
                    } else { fabSavedX=lp.x; fabSavedY=lp.y }
                    true
                }
                else -> false
            }
        }
        wm.addView(fabView, lp)
        fabView!!.scaleX=0f; fabView!!.scaleY=0f; fabView!!.alpha=0f
        fabView!!.animate().scaleX(1f).scaleY(1f).alpha(1f).setDuration(400)
            .setInterpolator(OvershootInterpolator(2f)).start()
        glowPulse()
    }

    private fun fabLP() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.END; x=fabSavedX; y=fabSavedY }

    private fun animFabOut(done: () -> Unit) {
        fabView?.animate()?.scaleX(0f)?.scaleY(0f)?.alpha(0f)?.setDuration(220)
            ?.setInterpolator(AccelerateInterpolator())
            ?.withEndAction { safeRm(fabView); fabView=null; done() }?.start()
    }

    private fun glowPulse() {
        val glow = fabView?.findViewById<View>(R.id.fab_glow) ?: return
        h.post(object : Runnable {
            override fun run() {
                if (fabView==null) return
                glow.animate().alpha(0.05f).scaleX(1.9f).scaleY(1.9f).setDuration(900).withEndAction {
                    glow.animate().alpha(0.85f).scaleX(1f).scaleY(1f).setDuration(900).withEndAction {
                        h.post(this) }.start()
                }.start()
            }
        })
    }

    // ─── GHOST — FIXED ────────────────────────────────────────────
    // Key fix: use setOnTouchListener with raw coordinates and own tap counter

    private fun showGhost() {
        safeRm(ghostView); ghostView = null
        ghostLastTap = 0L  // RESET tap state every time ghost shows

        val gv = View(this)
        ghostView = gv
        ghostX = fabSavedX; ghostY = fabSavedY

        val lp = WindowManager.LayoutParams(100, 100,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.END; x=ghostX; y=ghostY }

        gv.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                val now = System.currentTimeMillis()
                val diff = now - ghostLastTap
                if (ghostLastTap > 0 && diff < DBL) {
                    // Double tap confirmed — restore FAB
                    safeRm(ghostView); ghostView = null
                    showFab()
                } else {
                    ghostLastTap = now
                }
                true
            } else false
        }

        try { wm.addView(gv, lp) } catch (e: Exception) { logErr("showGhost: ${e.message}") }
    }

    // ─── RIPPLE ───────────────────────────────────────────────────

    private fun ripple(x: Int, y: Int) {
        try {
            val rv = View(this)
            rv.setBackgroundResource(R.drawable.bg_ripple)
            val sz = 140
            val lp = WindowManager.LayoutParams(sz, sz,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
                PixelFormat.TRANSLUCENT
            ).apply { gravity = Gravity.TOP or Gravity.END; this.x=x-sz/4; this.y=y-sz/4 }
            wm.addView(rv, lp)
            rv.scaleX=0f; rv.scaleY=0f; rv.alpha=0.9f
            rv.animate().scaleX(2.3f).scaleY(2.3f).alpha(0f).setDuration(500)
                .withEndAction { safeRm(rv) }.start()
        } catch (_: Exception) {}
    }

    // ─── CAPTURE ──────────────────────────────────────────────────

    private fun capture() {
        // Check plan limit
        if (!AppPrefs.canScreenshot(this)) {
            showPanel(false, "🔴 ЛІМІТ ВИЧЕРПАНО\n\nFree план: 20 скріншотів / 2 дні\n\nДля необмеженого використання перейди на ◆ PRO (30 грн/міс)\nЗверніться до розробника.")
            return
        }
        if (projection == null) {
            if (projCode != -1 && projData != null) {
                initProjection()
                h.postDelayed({
                    if (projection != null) capture()
                    else showPanel(false, "❌ Не вдалося відновити захоплення.\n\nЗупини і запусти MW AI знову.\nНатисни ► ПОЧАТИ ЗАПИС у діалозі.")
                }, 700); showPanel(true)
            } else {
                showPanel(false, "❌ Немає дозволу на захоплення.\n\nЗупини і запусти MW AI знову.")
            }
            return
        }

        AppPrefs.countScreenshot(this)
        showPanel(true)

        Thread {
            Thread.sleep(350)
            val bmp = getBitmap()
            if (bmp == null) {
                val err = "Не вдалося захопити кадр"
                logErr(err)
                h.post { updatePanel("❌ $err\nСпробуй ще раз.") }
                return@Thread
            }
            callGemini(toB64(bmp))
        }.start()
    }

    private fun getBitmap(): Bitmap? {
        return try {
            var img = imgReader?.acquireLatestImage()
            var attempts = 0
            while (img == null && attempts++ < 6) { Thread.sleep(120); img = imgReader?.acquireLatestImage() }
            img ?: return null
            val p = img.planes[0]
            val rp = p.rowStride - p.pixelStride * sw
            val bmp = Bitmap.createBitmap(sw + rp/p.pixelStride, sh, Bitmap.Config.ARGB_8888)
            bmp.copyPixelsFromBuffer(p.buffer); img.close()
            Bitmap.createBitmap(bmp, 0, 0, sw, sh)
        } catch (e: Exception) { logErr("getBitmap: ${e.message}"); null }
    }

    private fun toB64(bmp: Bitmap): String {
        val w = minOf(1080, bmp.width); val h2 = (w.toFloat()*bmp.height/bmp.width).toInt()
        val out = ByteArrayOutputStream()
        Bitmap.createScaledBitmap(bmp, w, h2, true).compress(Bitmap.CompressFormat.JPEG, 80, out)
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }

    // ─── GEMINI ───────────────────────────────────────────────────

    private fun callGemini(b64: String) {
        val key  = AppPrefs.getApiKey(this)
        val name = AppPrefs.getName(this)
        val isDev = AppPrefs.isDev(this)
        val autoTap = AppPrefs.isAutoTap(this)

        val prompt = buildString {
            append("Ти MW AI — Android AI-асистент")
            if (name.isNotEmpty()) append(" для $name")
            append(".\nПроаналізуй скріншот:\n")
            append("• Питання/тест → відповіді\n• Гра → стратегія\n• Форма → заповнення\n• Математика → рішення\n• Текст → резюме\n")
            if (autoTap) append("• ВАЖЛИВО: якщо є варіанти відповідей — вкажи КОНКРЕТНО який натиснути (A/B/C або текст)\n")
            if (isDev) append("• [DEV] технічні деталі зображення\n")
            append("Відповідай українською. Коротко. Макс 250 слів.")
        }

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply { put("inline_data", JSONObject().apply { put("mime_type","image/jpeg"); put("data",b64) }) })
                    put(JSONObject().apply { put("text", prompt) })
                })
            }))
            put("generationConfig", JSONObject().apply { put("temperature",0.3); put("maxOutputTokens",1024) })
        }

        http.newCall(Request.Builder().url("$GEMINI?key=$key")
            .addHeader("Content-Type","application/json")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .build()
        ).enqueue(object: Callback {
            override fun onFailure(c: Call, e: IOException) {
                logErr("Gemini network: ${e.message}")
                h.post { updatePanel("❌ Немає інтернету:\n${e.message}") }
            }
            override fun onResponse(c: Call, r: Response) {
                val rb = r.body?.string() ?: ""
                if (r.isSuccessful) {
                    try {
                        val txt = JSONObject(rb).getJSONArray("candidates")
                            .getJSONObject(0).getJSONObject("content")
                            .getJSONArray("parts").getJSONObject(0).getString("text")
                        h.post { updatePanel(txt) }
                        // Auto-tap: show toast with action hint after delay
                        if (AppPrefs.isAutoTap(this@OverlayService)) {
                            val delay = AppPrefs.getAutoDelay(this@OverlayService).toLong()
                            h.postDelayed({ showAutoTapHint(txt) }, delay)
                        }
                    } catch (e: Exception) {
                        val err = "Parse error: ${e.message}"
                        logErr(err); h.post { updatePanel("❌ $err") }
                    }
                } else {
                    val err = try { JSONObject(rb).getJSONObject("error").getString("message") }
                              catch (_:Exception) { "HTTP ${r.code}" }
                    logErr("Gemini API: $err"); h.post { updatePanel("❌ Gemini: $err") }
                }
            }
        })
    }

    private fun showAutoTapHint(answer: String) {
        // Extract first line as the key answer
        val hint = answer.lines().firstOrNull { it.isNotBlank() }?.take(80) ?: return
        Toast.makeText(this, "🤖 $hint", Toast.LENGTH_LONG).show()
    }

    // ─── PANEL ────────────────────────────────────────────────────

    private fun showPanel(loading: Boolean, text: String = "") {
        if (panelView != null) { updatePanel(if (loading) null else text.ifEmpty { null }); return }
        panelView = LayoutInflater.from(this).inflate(R.layout.view_panel, null)

        val lp = WindowManager.LayoutParams((sw*0.93).toInt(), WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; y=80 }

        val pb   = panelView!!.findViewById<ProgressBar>(R.id.progress_bar)
        val tv   = panelView!!.findViewById<TextView>(R.id.tv_content)
        val btnX = panelView!!.findViewById<ImageButton>(R.id.btn_close)
        val btnR = panelView!!.findViewById<ImageButton>(R.id.btn_refresh)
        val tvH  = panelView!!.findViewById<TextView>(R.id.tv_header)

        val n = AppPrefs.getName(this)
        tvH.text = if (n.isNotEmpty()) "MW AI  ·  $n" else "MW AI"

        if (loading) { pb.visibility=View.VISIBLE; tv.text="⚡ Аналізую..." }
        else if (text.isNotEmpty()) { pb.visibility=View.GONE; tv.text=text }

        var closeTap = 0L
        btnX.setOnClickListener {
            val now = System.currentTimeMillis()
            popBtn(btnX)
            if (now - closeTap < DBL) { hidePanel(); animFabOut {}; showGhost() } else hidePanel()
            closeTap = now
        }
        btnR.setOnClickListener {
            popBtn(btnR); updatePanel(null)
            Thread { Thread.sleep(300); getBitmap()?.let { callGemini(toB64(it)) }
                ?: h.post { updatePanel("❌ Скріншот не вдався") }
            }.start()
        }

        var iy=0; var ty=0f
        panelView!!.setOnTouchListener { _, e ->
            when(e.action) {
                MotionEvent.ACTION_DOWN -> { iy=lp.y; ty=e.rawY; false }
                MotionEvent.ACTION_MOVE -> {
                    lp.y = (iy-(e.rawY-ty)).toInt().coerceAtLeast(0)
                    try{wm.updateViewLayout(panelView,lp)}catch(_:Exception){}; true
                }
                else -> false
            }
        }

        wm.addView(panelView, lp)
        panelView!!.translationY=600f; panelView!!.alpha=0f
        panelView!!.animate().translationY(0f).alpha(1f).setDuration(360)
            .setInterpolator(DecelerateInterpolator(1.8f)).start()
        panelOpen = true
    }

    private fun updatePanel(txt: String?) {
        if (panelView==null) { showPanel(txt==null); return }
        val pb = panelView!!.findViewById<ProgressBar>(R.id.progress_bar)
        val tv = panelView!!.findViewById<TextView>(R.id.tv_content)
        if (txt==null) { pb.visibility=View.VISIBLE; tv.text="⚡ Аналізую..." }
        else { pb.visibility=View.GONE; tv.alpha=0f; tv.text=txt; tv.animate().alpha(1f).setDuration(300).start() }
    }

    private fun hidePanel() {
        panelView?.animate()?.translationY(600f)?.alpha(0f)?.setDuration(300)
            ?.setInterpolator(AccelerateInterpolator())
            ?.withEndAction { safeRm(panelView); panelView=null; panelOpen=false }?.start()
    }

    private fun popBtn(v: View) {
        v.animate().scaleX(0.8f).scaleY(0.8f).setDuration(70).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(140).setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

    // ─── HELPERS ──────────────────────────────────────────────────

    private fun safeRm(v: View?) { try{v?.let{wm.removeView(it)}}catch(_:Exception){} }
    private fun logErr(msg: String) = AppPrefs.logError(this, AppPrefs.getName(this).ifEmpty{"unknown"}, msg)

    private fun createCh() = NotificationChannel(CH, "MW AI", NotificationManager.IMPORTANCE_LOW)
        .also { getSystemService(NotificationManager::class.java).createNotificationChannel(it) }

    private fun buildNote(): Notification {
        val open = PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE)
        val stop = PendingIntent.getService(this,1,Intent(this,OverlayService::class.java).apply{action="STOP"},PendingIntent.FLAG_IMMUTABLE)
        return NotificationCompat.Builder(this, CH)
            .setContentTitle("MW AI ⚡").setContentText("Натисни MW AI для аналізу")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(open).addAction(android.R.drawable.ic_delete,"Стоп",stop)
            .setOngoing(true).build()
    }
}
