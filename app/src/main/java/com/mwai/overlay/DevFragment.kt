package com.mwai.overlay

import android.os.Bundle
import android.view.*
import android.view.animation.*
import android.widget.*
import androidx.fragment.app.Fragment
import java.text.SimpleDateFormat
import java.util.*

class DevFragment : Fragment(R.layout.fragment_dev) {

    private val sdf = SimpleDateFormat("dd.MM HH:mm", Locale.getDefault())

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val ctx = requireContext()

        val tvUsers    = view.findViewById<TextView>(R.id.tv_users)
        val tvErrors   = view.findViewById<TextView>(R.id.tv_errors)
        val tvDevStats = view.findViewById<TextView>(R.id.tv_dev_stats)
        val btnClearU  = view.findViewById<Button>(R.id.btn_clear_users)
        val btnClearE  = view.findViewById<Button>(R.id.btn_clear_errors)
        val etProUser  = view.findViewById<EditText>(R.id.et_pro_user)
        val btnGrantPro= view.findViewById<Button>(R.id.btn_grant_pro)
        val tvGranted  = view.findViewById<TextView>(R.id.tv_granted)

        loadAll(tvUsers, tvErrors, tvDevStats)

        btnClearU.setOnClickListener {
            ctx.getSharedPreferences("mwai_prefs", android.content.Context.MODE_PRIVATE)
                .edit().remove("k_u").apply()
            loadAll(tvUsers, tvErrors, tvDevStats)
            toast("✓ Лог юзерів очищено")
        }
        btnClearE.setOnClickListener {
            AppPrefs.clearErrors(ctx); loadAll(tvUsers, tvErrors, tvDevStats); toast("✓ Помилки очищено")
        }

        // Dev can grant PRO to self (name = "Developer") or show it's granted
        tvGranted.text = if (AppPrefs.isPro(ctx)) "◆ PRO активовано" else ""

        btnGrantPro.setOnClickListener {
            // In real app would validate — here dev can toggle their own pro
            AppPrefs.setPlan(ctx, "pro")
            tvGranted.text = "◆ PRO активовано для Developer"
            toast("✓ PRO активовано!")
        }

        animCards(view)
    }

    private fun loadAll(tvU: TextView, tvE: TextView, tvS: TextView) {
        val ctx = requireContext()
        val users = AppPrefs.getUsers(ctx)
        val errors = AppPrefs.getErrors(ctx)

        tvU.text = if (users.isEmpty()) "> НЕМАЄ ДАНИХ"
        else users.joinToString("\n") { (n, t) -> "> [${sdf.format(Date(t))}] $n" }

        tvE.text = if (errors.isEmpty()) "> ПОМИЛОК НЕМАЄ ✓"
        else errors.take(20).joinToString("\n") { e ->
            "> [${sdf.format(Date(e.time))}] ${e.user}: ${e.message.take(60)}"
        }

        tvS.text = "USERS: ${users.size} | UNIQ: ${users.map{it.first}.toSet().size} | ERRORS: ${errors.size} | v${AppPrefs.VERSION}"
    }

    private fun animCards(root: View) {
        listOf(R.id.card_dev_header, R.id.card_users_log, R.id.card_errors_log, R.id.card_dev_actions)
            .forEachIndexed { i, id ->
                root.findViewById<View>(id)?.let { v ->
                    v.alpha = 0f; v.translationY = 40f
                    v.animate().alpha(1f).translationY(0f).setStartDelay(50L + i * 90)
                        .setDuration(350).setInterpolator(DecelerateInterpolator()).start()
                }
            }
    }

    private fun toast(m: String) = Toast.makeText(requireContext(), m, Toast.LENGTH_SHORT).show()
}
