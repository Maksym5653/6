package com.mwai.overlay

import android.os.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val name  = AppPrefs.getName(this)
        val isDev = AppPrefs.isDev(this)
        val isPro = AppPrefs.isPro(this)

        val tvGreet = findViewById<TextView>(R.id.tv_greeting)
        val tvPlan  = findViewById<TextView>(R.id.tv_plan_badge)

        tvGreet.text = if (name.isNotEmpty()) AppPrefs.getGreeting(name) else "MW_AI v4.0"
        tvGreet.setTextColor(getColor(if (isDev) R.color.accent_red else R.color.text_primary))

        tvPlan.text  = when {
            isDev  -> "⚡ DEV"
            isPro  -> "◆ PRO"
            else   -> "FREE"
        }
        tvPlan.setBackgroundResource(if (isPro) R.drawable.bg_badge_pro else R.drawable.bg_badge_free)

        val nav = findViewById<BottomNavigationView>(R.id.bottom_nav)
        nav.menu.findItem(R.id.nav_dev)?.isVisible = isDev
        nav.setOnItemSelectedListener {
            when (it.itemId) {
                R.id.nav_home     -> load(HomeFragment())
                R.id.nav_settings -> load(SettingsFragment())
                R.id.nav_dev      -> load(DevFragment())
            }
            true
        }
        load(HomeFragment())

        // Check for updates in background
        if (savedInstanceState == null) checkUpdate()
    }

    private fun load(f: Fragment) {
        supportFragmentManager.beginTransaction()
            .setCustomAnimations(android.R.anim.fade_in, android.R.anim.fade_out)
            .replace(R.id.fragment_container, f).commit()
    }

    private fun checkUpdate() {
        Thread {
            try {
                val url = java.net.URL("https://raw.githubusercontent.com/Maksym5653/mwai/main/version.txt")
                val conn = url.openConnection().apply { connectTimeout = 3000; readTimeout = 3000 }
                val remote = conn.getInputStream().bufferedReader().readText().trim().toIntOrNull() ?: return@Thread
                if (remote > AppPrefs.VERSION_CODE) {
                    Handler(Looper.getMainLooper()).post {
                        Toast.makeText(this, "🔴 Доступне оновлення MW AI v$remote!", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (_: Exception) {}
        }.start()
    }

    fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
