package com.zapret.droid

import android.content.Intent
import android.content.SharedPreferences
import android.net.VpnService
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.switchmaterial.SwitchMaterial
import com.zapret.droid.service.ZapretVpnService
import com.zapret.droid.strategies.Strategy
import com.zapret.droid.strategies.StrategyPresets
import com.zapret.droid.ui.LogAdapter
import com.zapret.droid.ui.StrategyAdapter

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var strategyAdapter: StrategyAdapter
    private lateinit var logAdapter: LogAdapter

    private lateinit var btnToggle: MaterialButton
    private lateinit var btnTelegramConnect: MaterialButton
    private lateinit var tvStatus: TextView
    private lateinit var switchTelegram: SwitchMaterial
    private lateinit var switchAutostart: SwitchMaterial
    private lateinit var rvStrategies: RecyclerView
    private lateinit var rvLogs: RecyclerView
    private lateinit var statusIndicator: View

    private var selectedStrategy: Strategy = StrategyPresets.GENERAL

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) startVpnService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        prefs = getSharedPreferences("zapret_prefs", MODE_PRIVATE)

        initViews()
        loadPreferences()
        setupStrategyList()
        setupLogList()
        setupClickListeners()
        updateStatusUi()

        ZapretVpnService.onLogUpdate = { msg ->
            runOnUiThread {
                logAdapter.addLog(msg)
                rvLogs.scrollToPosition(0)
            }
        }
    }

    private fun initViews() {
        btnToggle = findViewById(R.id.btnToggle)
        btnTelegramConnect = findViewById(R.id.btnTelegramConnect)
        tvStatus = findViewById(R.id.tvStatus)
        switchTelegram = findViewById(R.id.switchTelegram)
        switchAutostart = findViewById(R.id.switchAutostart)
        rvStrategies = findViewById(R.id.rvStrategies)
        rvLogs = findViewById(R.id.rvLogs)
        statusIndicator = findViewById(R.id.statusIndicator)
    }

    private fun loadPreferences() {
        val savedId = prefs.getString("strategy_id", "general") ?: "general"
        selectedStrategy = StrategyPresets.ALL.find { it.id == savedId } ?: StrategyPresets.GENERAL
        switchTelegram.isChecked = prefs.getBoolean("telegram_enabled", true)
        switchAutostart.isChecked = prefs.getBoolean("autostart", false)
    }

    private fun setupStrategyList() {
        strategyAdapter = StrategyAdapter(StrategyPresets.ALL) { strategy ->
            selectedStrategy = strategy
            prefs.edit().putString("strategy_id", strategy.id).apply()
        }
        strategyAdapter.selectedId = selectedStrategy.id
        rvStrategies.layoutManager = LinearLayoutManager(this)
        rvStrategies.adapter = strategyAdapter
    }

    private fun setupLogList() {
        logAdapter = LogAdapter()
        rvLogs.layoutManager = LinearLayoutManager(this)
        rvLogs.adapter = logAdapter

        // Load existing logs
        if (ZapretVpnService.logBuffer.isNotEmpty()) {
            logAdapter.setLogs(ZapretVpnService.logBuffer.toList().reversed())
        }
    }

    private fun setupClickListeners() {
        btnToggle.setOnClickListener {
            if (ZapretVpnService.isRunning) {
                stopVpnService()
            } else {
                requestVpnPermission()
            }
        }

        switchTelegram.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("telegram_enabled", checked).apply()
            updateStatusUi()
        }

        btnTelegramConnect.setOnClickListener {
            openTelegramProxy()
        }

        switchAutostart.setOnCheckedChangeListener { _, checked ->
            prefs.edit().putBoolean("autostart", checked).apply()
        }
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        ZapretVpnService.log("Запуск стратегии: ${selectedStrategy.name} (${selectedStrategy.id})")
        val intent = Intent(this, ZapretVpnService::class.java).apply {
            action = ZapretVpnService.ACTION_START
            putExtra(ZapretVpnService.EXTRA_STRATEGY_ID, selectedStrategy.id)
            putExtra(ZapretVpnService.EXTRA_TELEGRAM_ENABLED, switchTelegram.isChecked)
        }
        startForegroundService(intent)
        updateStatusUi()
    }

    private fun stopVpnService() {
        val intent = Intent(this, ZapretVpnService::class.java).apply {
            action = ZapretVpnService.ACTION_STOP
        }
        startService(intent)
        updateStatusUi()
    }

    private fun updateStatusUi() {
        val running = ZapretVpnService.isRunning
        btnToggle.text = if (running) "Остановить" else "Запустить"
        tvStatus.text = if (running) "Активен — ${ZapretVpnService.currentStrategyId}" else "Остановлен"
        statusIndicator.setBackgroundResource(
            if (running) R.drawable.indicator_active else R.drawable.indicator_inactive
        )
        rvStrategies.alpha = if (running) 0.5f else 1.0f
        rvStrategies.isEnabled = !running
        btnTelegramConnect.visibility = if (running && switchTelegram.isChecked)
            android.view.View.VISIBLE else android.view.View.GONE
    }

    private fun openTelegramProxy() {
        val deepLink = "tg://proxy?server=127.0.0.1&port=1443&secret="
        try {
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse(deepLink)))
        } catch (_: Exception) {
            // Telegram не установлен — открыть t.me ссылку в браузере
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW,
                android.net.Uri.parse("https://t.me/proxy?server=127.0.0.1&port=1443&secret=")))
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatusUi()
    }

    override fun onDestroy() {
        super.onDestroy()
        ZapretVpnService.onLogUpdate = null
    }
}
