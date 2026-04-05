package com.mwai.overlay

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.widget.Toast

class ScreenCaptureActivity : Activity() {
    private val RC = 100
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        try {
            startActivityForResult(
                (getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent(), RC)
        } catch (e: Exception) {
            AppPrefs.logError(this, AppPrefs.getName(this), "ScreenCapture init: ${e.message}")
            Toast.makeText(this, "Помилка запуску: ${e.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC && resultCode == RESULT_OK && data != null) {
            startForegroundService(Intent(this, OverlayService::class.java).apply {
                putExtra("result_code", resultCode); putExtra("data", data)
            })
            Toast.makeText(this, "✓ MW AI активовано!", Toast.LENGTH_SHORT).show()
        } else {
            AppPrefs.logError(this, AppPrefs.getName(this), "Користувач відхилив дозвіл захоплення екрану")
            Toast.makeText(this, "⚠ Натисни ПОЧАТИ ЗАПИС щоб дозволити аналіз!", Toast.LENGTH_LONG).show()
        }
        finish()
    }
}
