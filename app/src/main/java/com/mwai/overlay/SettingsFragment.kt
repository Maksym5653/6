package com.mwai.overlay

import android.os.Bundle
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.fragment.app.Fragment

class SettingsFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()

        val etKey       = view.findViewById<EditText>(R.id.et_api_key)
        val btnSave     = view.findViewById<Button>(R.id.btn_save_key)
        val tvKeyStatus = view.findViewById<TextView>(R.id.tv_key_status)
        val rbSingle    = view.findViewById<RadioButton>(R.id.rb_single)
        val rbDouble    = view.findViewById<RadioButton>(R.id.rb_double)
        val swAutoTap   = view.findViewById<Switch>(R.id.sw_auto_tap)
        val sbDelay     = view.findViewById<SeekBar>(R.id.sb_delay)
        val tvDelay     = view.findViewById<TextView>(R.id.tv_delay_val)
        val swReset     = view.findViewById<Switch>(R.id.sw_reset)
        val tvName      = view.findViewById<TextView>(R.id.tv_name)
        val btnUpgrade  = view.findViewById<Button>(R.id.btn_upgrade)
        val tvPlanInfo  = view.findViewById<TextView>(R.id.tv_plan_info)

        // API Key
        etKey.setText(AppPrefs.getApiKey(ctx))
        showKeyStatus(tvKeyStatus, AppPrefs.getApiKey(ctx))

        btnSave.setOnClickListener {
            val k = etKey.text.toString().trim()
            if (k.length > 10) { AppPrefs.setApiKey(ctx, k); showKeyStatus(tvKeyStatus, k); toast("✓ Збережено") }
            else toast("Невірний ключ")
        }

        // Tap mode
        if (AppPrefs.getTap(ctx) == "single") rbSingle.isChecked = true else rbDouble.isChecked = true
        val rg = view.findViewById<RadioGroup>(R.id.rg_tap)
        rg.setOnCheckedChangeListener { _, id ->
            AppPrefs.setTap(ctx, if (id == R.id.rb_single) "single" else "double")
        }

        // Auto-tap
        swAutoTap.isChecked = AppPrefs.isAutoTap(ctx)
        val delayMs = AppPrefs.getAutoDelay(ctx)
        sbDelay.progress = (delayMs / 500).coerceIn(1, 10)
        tvDelay.text = "${delayMs / 1000.0}с"
        swAutoTap.setOnCheckedChangeListener { _, v -> AppPrefs.setAutoTap(ctx, v) }
        sbDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, p: Int, u: Boolean) {
                val ms = p * 500
                AppPrefs.setAutoDelay(ctx, ms)
                tvDelay.text = "${ms / 1000.0}с"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        // Name
        tvName.text = "Ім'я: ${AppPrefs.getName(ctx).ifEmpty { "не задано" }}"
        swReset.setOnCheckedChangeListener { _, v ->
            if (v) { AppPrefs.setName(ctx, ""); AppPrefs.setDev(ctx, false); toast("Перезапусти додаток") }
        }

        // Plan
        val isPro = AppPrefs.isPro(ctx)
        tvPlanInfo.text = if (isPro) "◆ PRO активовано" else "FREE: ${AppPrefs.getShotsUsed(ctx)}/20 скріншотів"
        tvPlanInfo.setTextColor(requireContext().getColor(if (isPro) R.color.accent_red else R.color.text_secondary))
        btnUpgrade.visibility = if (isPro) View.GONE else View.VISIBLE
        btnUpgrade.setOnClickListener { showUpgradeDialog() }

        animCards(view)
    }

    private fun showUpgradeDialog() {
        val d = android.app.AlertDialog.Builder(requireContext())
            .setTitle("◆ MW AI PRO")
            .setMessage("Необмежена кількість аналізів\nПріоритетна обробка\nАвто-відповіді\n\nВартість: 30 грн/місяць\n\n(Зверніться до розробника для активації)")
            .setPositiveButton("Зрозуміло") { _, _ -> }
            .create()
        d.window?.setBackgroundDrawableResource(R.drawable.bg_card)
        d.show()
        d.getButton(android.app.AlertDialog.BUTTON_POSITIVE)
            ?.setTextColor(requireContext().getColor(R.color.accent_red))
    }

    private fun showKeyStatus(tv: TextView, key: String) {
        if (key.length > 10) {
            tv.text = "🔑 ${key.take(8)}••••${key.takeLast(4)}"
            tv.setTextColor(requireContext().getColor(R.color.accent_red))
        } else {
            tv.text = "⚠ Ключ не налаштовано"
            tv.setTextColor(requireContext().getColor(R.color.text_secondary))
        }
    }

    private fun animCards(root: View) {
        listOf(R.id.card_key, R.id.card_tap, R.id.card_autotap, R.id.card_account, R.id.card_plan_settings)
            .forEachIndexed { i, id ->
                root.findViewById<View>(id)?.let { v ->
                    v.alpha = 0f; v.translationY = 40f
                    v.animate().alpha(1f).translationY(0f).setStartDelay(60L + i * 80)
                        .setDuration(350).setInterpolator(DecelerateInterpolator()).start()
                }
            }
    }

    private fun toast(m: String) = Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()
}
