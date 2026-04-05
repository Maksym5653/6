package com.mwai.overlay

import android.content.Context

object AppPrefs {
    private const val F = "mwai_prefs"
    const val DEV_CODE = "mak00066891"
    const val VERSION  = "4.0"
    const val VERSION_CODE = 4

    // Keys
    private const val K_API      = "k_a"
    private const val K_NAME     = "k_n"
    private const val K_DEV      = "k_d"
    private const val K_TAP      = "k_t"   // "single"/"double"
    private const val K_USERS    = "k_u"
    private const val K_ERRORS   = "k_e"
    private const val K_PLAN     = "k_p"   // "free"/"pro"
    private const val K_SHOTS    = "k_s"   // screenshot count in current period
    private const val K_SHOTS_TS = "k_st"  // period start timestamp
    private const val K_AUTO_TAP = "k_at"  // auto-tap answers on/off
    private const val K_AUTO_DELAY = "k_ad" // auto-tap delay ms
    private const val DEFAULT_KEY = "AIzaSyCdSIIIT25FqueEJTU7FX0qOHwaZNFFsjQ"

    fun p(ctx: Context) = ctx.getSharedPreferences(F, Context.MODE_PRIVATE)

    // API KEY — XOR obfuscated
    fun getApiKey(ctx: Context): String {
        val raw = p(ctx).getString(K_API, null) ?: return DEFAULT_KEY
        return xor(raw)
    }
    fun setApiKey(ctx: Context, key: String) = p(ctx).edit().putString(K_API, xor(key)).apply()

    // NAME
    fun getName(ctx: Context) = p(ctx).getString(K_NAME, "") ?: ""
    fun setName(ctx: Context, v: String) = p(ctx).edit().putString(K_NAME, v).apply()

    // DEV MODE
    fun isDev(ctx: Context) = p(ctx).getBoolean(K_DEV, false)
    fun setDev(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(K_DEV, v).apply()

    // TAP MODE
    fun getTap(ctx: Context) = p(ctx).getString(K_TAP, "double") ?: "double"
    fun setTap(ctx: Context, v: String) = p(ctx).edit().putString(K_TAP, v).apply()

    // PLAN
    fun getPlan(ctx: Context) = p(ctx).getString(K_PLAN, "free") ?: "free"
    fun setPlan(ctx: Context, v: String) = p(ctx).edit().putString(K_PLAN, v).apply()
    fun isPro(ctx: Context) = getPlan(ctx) == "pro" || isDev(ctx)

    // SCREENSHOT LIMIT (Free = 20 per 2 days)
    fun canScreenshot(ctx: Context): Boolean {
        if (isPro(ctx)) return true
        val prefs = p(ctx)
        val now = System.currentTimeMillis()
        val periodStart = prefs.getLong(K_SHOTS_TS, 0L)
        val twoDays = 2L * 24 * 60 * 60 * 1000
        // Reset period if expired
        if (now - periodStart > twoDays) {
            prefs.edit().putInt(K_SHOTS, 0).putLong(K_SHOTS_TS, now).apply()
            return true
        }
        return prefs.getInt(K_SHOTS, 0) < 20
    }
    fun countScreenshot(ctx: Context) {
        if (isPro(ctx)) return
        val prefs = p(ctx)
        val now = System.currentTimeMillis()
        val periodStart = prefs.getLong(K_SHOTS_TS, 0L)
        val twoDays = 2L * 24 * 60 * 60 * 1000
        if (now - periodStart > twoDays) {
            prefs.edit().putInt(K_SHOTS, 1).putLong(K_SHOTS_TS, now).apply()
        } else {
            prefs.edit().putInt(K_SHOTS, prefs.getInt(K_SHOTS, 0) + 1).apply()
        }
    }
    fun getShotsUsed(ctx: Context): Int {
        val prefs = p(ctx)
        val now = System.currentTimeMillis()
        val periodStart = prefs.getLong(K_SHOTS_TS, 0L)
        val twoDays = 2L * 24 * 60 * 60 * 1000
        if (now - periodStart > twoDays) return 0
        return prefs.getInt(K_SHOTS, 0)
    }

    // AUTO-TAP
    fun isAutoTap(ctx: Context) = p(ctx).getBoolean(K_AUTO_TAP, false)
    fun setAutoTap(ctx: Context, v: Boolean) = p(ctx).edit().putBoolean(K_AUTO_TAP, v).apply()
    fun getAutoDelay(ctx: Context) = p(ctx).getInt(K_AUTO_DELAY, 2000)
    fun setAutoDelay(ctx: Context, v: Int) = p(ctx).edit().putInt(K_AUTO_DELAY, v).apply()

    // USER LOG
    fun logUser(ctx: Context, name: String) {
        val existing = p(ctx).getString(K_USERS, "") ?: ""
        p(ctx).edit().putString(K_USERS, existing + "$name|${System.currentTimeMillis()}\n").apply()
    }
    fun getUsers(ctx: Context): List<Pair<String, Long>> {
        val raw = p(ctx).getString(K_USERS, "") ?: ""
        return raw.trim().lines().filter { it.contains("|") }.mapNotNull {
            val s = it.split("|"); if (s.size >= 2) Pair(s[0], s[1].toLongOrNull() ?: 0L) else null
        }.sortedByDescending { it.second }
    }

    // ERROR LOG
    fun logError(ctx: Context, user: String, error: String) {
        val existing = p(ctx).getString(K_ERRORS, "") ?: ""
        val entry = "$user|${System.currentTimeMillis()}|$error\n"
        // Keep last 50 lines
        val lines = (existing + entry).lines().filter { it.isNotBlank() }.takeLast(50)
        p(ctx).edit().putString(K_ERRORS, lines.joinToString("\n") + "\n").apply()
    }
    data class ErrorEntry(val user: String, val time: Long, val message: String)
    fun getErrors(ctx: Context): List<ErrorEntry> {
        val raw = p(ctx).getString(K_ERRORS, "") ?: ""
        return raw.trim().lines().filter { it.contains("|") }.mapNotNull {
            val parts = it.split("|", limit = 3)
            if (parts.size >= 3) ErrorEntry(parts[0], parts[1].toLongOrNull() ?: 0L, parts[2])
            else null
        }.sortedByDescending { it.time }
    }
    fun clearErrors(ctx: Context) = p(ctx).edit().remove(K_ERRORS).apply()

    // GREETING
    fun getGreeting(name: String): String {
        val h = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val time = when { h in 5..11 -> "Доброго ранку"; h in 12..17 -> "Добрий день"; h in 18..22 -> "Добрий вечір"; else -> "Доброї ночі" }
        return "$time, $name"
    }

    private fun xor(s: String) = s.map { (it.code xor 0x5A).toChar() }.joinToString("")
}
