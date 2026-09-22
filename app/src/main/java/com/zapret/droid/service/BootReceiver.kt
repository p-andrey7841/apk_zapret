package com.zapret.droid.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val prefs: SharedPreferences = context.getSharedPreferences("zapret_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("autostart", false)) return

        val strategyId = prefs.getString("strategy_id", "general") ?: "general"
        val telegramEnabled = prefs.getBoolean("telegram_enabled", true)

        val vpnIntent = Intent(context, ZapretVpnService::class.java).apply {
            action = ZapretVpnService.ACTION_START
            putExtra(ZapretVpnService.EXTRA_STRATEGY_ID, strategyId)
            putExtra(ZapretVpnService.EXTRA_TELEGRAM_ENABLED, telegramEnabled)
        }
        context.startForegroundService(vpnIntent)
    }
}
