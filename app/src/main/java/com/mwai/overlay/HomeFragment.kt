package com.mwai.overlay

import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.fragment.app.Fragment

class HomeFragment : Fragment(R.layout.fragment_home) {

    private val h = Handler(Looper.getMainLooper())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ctx = requireContext()

        val btnLaunch = view.findViewById<Button>(R.id.btn_launch)
        val btnStop   = view.findViewById<Button>(R.id.btn_stop)
        val tvStatus  = view.findViewById<TextView>(R.id.tv_status)
        val dot       = view.findViewById<View>(R.id.status_dot)
        val tvPlan    = view.findViewById<TextView>(R.id.tv_plan_info)
        val tvShots   = view.findViewById<TextView>(R.id.tv_shots)

        updateStatus(tvStatus, dot, btnLaunch)
        updatePlanInfo(tvPlan, tvShots)
        pulseDot(dot)

        animCards(view, listOf(R.id.card_status, R.id.card_actions, R.id.card_plan))

        btnLaunch.setOnClickListener {
            pop(it)
            h.postDelayed({
                if (Settings.canDrawOverlays(ctx)) startService()
                else requestPerm()
            }, 120)
        }
        btnStop.setOnClickListener {
            pop(it)
            ctx.stopService(Intent(ctx, OverlayService::class.java))
            h.postDelayed({ updateStatus(tvStatus, dot, btnLaunch) }, 300)
        }
    }

    private fun updatePlanInfo(tvPlan: TextView, tvShots: TextView) {
        val ctx = requireContext()
        val isPro = AppPrefs.isPro(ctx)
        val used = AppPrefs.getShotsUsed(ctx)
        tvPlan.text = if (isPro) "◆ PRO — необмежено" else "FREE — ліміт 20 скріншотів / 2 дні"
        tvPlan.setTextColor(requireContext().getColor(if (isPro) R.color.accent_red else R.color.text_secondary))
        tvShots.text = if (isPro) "Скріншотів: ∞" else "Використано: $used / 20"
    }

    private fun startService() {
        startActivity(Intent(requireContext(), ScreenCaptureActivity::class.java))
        Toast.makeText(requireContext(), "⚡ MW AI запускається...", Toast.LENGTH_SHORT).show()
    }

    private fun requestPerm() {
        Toast.makeText(requireContext(), "Дозволь відображення поверх додатків", Toast.LENGTH_LONG).show()
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${requireContext().packageName}")))
    }

    private fun updateStatus(tv: TextView, dot: View, btn: Button) {
        val run = OverlayService.isRunning
        tv.text = if (run) "● АКТИВНО" else "○ OFFLINE"
        tv.setTextColor(requireContext().getColor(if (run) R.color.accent_red else R.color.text_secondary))
        dot.setBackgroundResource(if (run) R.drawable.bg_dot_red else R.drawable.bg_dot_inactive)
        btn.text = if (run) "⟳  ПЕРЕЗАПУСТИТИ" else "▶  ЗАПУСТИТИ MW AI"
    }

    private fun pulseDot(v: View) {
        ScaleAnimation(1f, 1.7f, 1f, 1.7f, Animation.RELATIVE_TO_SELF, .5f, Animation.RELATIVE_TO_SELF, .5f).apply {
            duration = 700; repeatCount = Animation.INFINITE; repeatMode = Animation.REVERSE
            interpolator = AccelerateDecelerateInterpolator()
        }.also { v.startAnimation(it) }
    }

    private fun pop(v: View) {
        v.animate().scaleX(.93f).scaleY(.93f).setDuration(70).withEndAction {
            v.animate().scaleX(1f).scaleY(1f).setDuration(150).setInterpolator(OvershootInterpolator()).start()
        }.start()
    }

    private fun animCards(root: View, ids: List<Int>) {
        ids.forEachIndexed { i, id ->
            root.findViewById<View>(id)?.let { v ->
                v.alpha = 0f; v.translationY = 50f
                v.animate().alpha(1f).translationY(0f).setStartDelay(80L + i * 100)
                    .setDuration(380).setInterpolator(DecelerateInterpolator()).start()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        view?.let {
            updateStatus(it.findViewById(R.id.tv_status), it.findViewById(R.id.status_dot), it.findViewById(R.id.btn_launch))
            updatePlanInfo(it.findViewById(R.id.tv_plan_info), it.findViewById(R.id.tv_shots))
        }
    }
}
