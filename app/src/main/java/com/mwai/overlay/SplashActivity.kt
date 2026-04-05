package com.mwai.overlay

import android.content.Intent
import android.graphics.Typeface
import android.os.*
import android.view.*
import android.view.animation.*
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val h = Handler(Looper.getMainLooper())
    private val bootLines = listOf(
        "> MW_AI_SYSTEM v4.0",
        "> ІНІЦІАЛІЗАЦІЯ НЕЙРОМЕРЕЖ...",
        "> ПІДКЛЮЧЕННЯ ДО GEMINI API..",
        "> ЗАВАНТАЖЕННЯ МОДУЛІВ.......",
        "> ШИФРУВАННЯ КАНАЛУ.........",
        "> СИСТЕМА ГОТОВА. ІДЕНТИФІКУЙ СЕБЕ."
    )
    private var lineIdx = 0
    private var charIdx = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        val tvBoot      = findViewById<TextView>(R.id.tv_boot_text)
        val layoutBoot  = findViewById<View>(R.id.layout_boot)
        val layoutLogin = findViewById<View>(R.id.layout_login)
        val etName      = findViewById<EditText>(R.id.et_name)
        val btnEnter    = findViewById<Button>(R.id.btn_enter)
        val tvMsg       = findViewById<TextView>(R.id.tv_msg)
        val tvDevBadge  = findViewById<TextView>(R.id.tv_dev_badge)

        // Skip intro if already logged in
        if (AppPrefs.getName(this).isNotEmpty()) {
            h.postDelayed({ goMain() }, 600); return
        }

        layoutLogin.visibility = View.GONE
        tvBoot.typeface = Typeface.MONOSPACE

        typeLine(tvBoot) {
            layoutBoot.animate().alpha(0f).setDuration(500).withEndAction {
                layoutBoot.visibility = View.GONE
                layoutLogin.visibility = View.VISIBLE
                layoutLogin.alpha = 0f
                layoutLogin.animate().alpha(1f).setDuration(400).start()
                etName.requestFocus()
                blinkBtn(btnEnter)
            }.start()
        }

        btnEnter.setOnClickListener { handleInput(etName, tvMsg, tvDevBadge) }
        etName.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) { handleInput(etName, tvMsg, tvDevBadge); true } else false
        }
    }

    // ─── TYPEWRITER ───────────────────────────────────────────────

    private val displayedLines = mutableListOf<String>()

    private fun typeLine(tv: TextView, onDone: () -> Unit) {
        if (lineIdx >= bootLines.size) { h.postDelayed(onDone, 400); return }
        val line = bootLines[lineIdx]
        charIdx = 0
        typeChar(tv, line, onDone)
    }

    private fun typeChar(tv: TextView, line: String, onDone: () -> Unit) {
        if (charIdx > line.length) {
            displayedLines.add(line)
            lineIdx++
            h.postDelayed({ typeLine(tv, onDone) }, 150)
            return
        }
        val current = displayedLines.joinToString("\n") +
                (if (displayedLines.isNotEmpty()) "\n" else "") +
                line.substring(0, charIdx) + "█"
        tv.text = current
        charIdx++
        h.postDelayed({ typeChar(tv, line, onDone) }, 28)
    }

    // ─── INPUT ────────────────────────────────────────────────────

    private fun handleInput(et: EditText, tvMsg: TextView, tvBadge: TextView) {
        val input = et.text.toString().trim()
        if (input.isEmpty()) { shake(et); tvMsg.text = "> ПОМИЛКА: ВВЕДИ ПОЗИВНИЙ"; tvMsg.visibility = View.VISIBLE; return }

        if (input == AppPrefs.DEV_CODE) {
            AppPrefs.setDev(this, true)
            AppPrefs.setPlan(this, "pro")
            AppPrefs.setName(this, "Developer")
            AppPrefs.logUser(this, "Developer[DEV]")
            tvBadge.visibility = View.VISIBLE
            tvMsg.text = "> ROOT ACCESS GRANTED ✓"
            tvMsg.visibility = View.VISIBLE
        } else {
            AppPrefs.setDev(this, false)
            AppPrefs.setName(this, input)
            AppPrefs.logUser(this, input)
            tvMsg.text = "> ДОСТУП НАДАНО: $input ✓"
            tvMsg.visibility = View.VISIBLE
        }
        h.postDelayed({ glitch { goMain() } }, 900)
    }

    private fun glitch(done: () -> Unit) {
        val root = window.decorView
        var n = 0
        val r = object : Runnable {
            override fun run() {
                root.translationX = if (n % 2 == 0) 10f else -10f
                n++
                if (n < 8) h.postDelayed(this, 45)
                else { root.translationX = 0f; root.animate().alpha(0f).setDuration(250).withEndAction { done() }.start() }
            }
        }
        h.post(r)
    }

    private fun shake(v: View) {
        TranslateAnimation(-18f, 18f, 0f, 0f).apply {
            duration = 55; repeatCount = 5; repeatMode = Animation.REVERSE
        }.also { v.startAnimation(it) }
    }

    private fun blinkBtn(btn: Button) {
        h.post(object : Runnable {
            var vis = true
            override fun run() {
                btn.alpha = if (vis) 1f else 0.5f; vis = !vis; h.postDelayed(this, 700)
            }
        })
    }

    private fun goMain() {
        startActivity(Intent(this, MainActivity::class.java))
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }
}
