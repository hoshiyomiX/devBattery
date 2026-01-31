package com.deviant.batterymonitor

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Bundle
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AppCompatActivity
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private var batteryReceiver: BroadcastReceiver? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.webView)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        // Injeksi JavaScript interface
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun getBatteryInfo(): String {
                return getBatteryStatus()
            }

            @android.webkit.JavascriptInterface
            fun getDebugInfo(): String {
                return getDeviceDebugInfo()
            }
        }, "AndroidBridge")

        webView.loadUrl("file:///android_asset/index.html")

        setupBatteryBroadcast()
    }

    private fun setupBatteryBroadcast() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                webView.evaluateJavascript("if(typeof updateBatteryUI === 'function') updateBatteryUI();", null)
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun getBatteryStatus(): String {
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        // Baca level baterai (0-100)
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 0

        // Baca tegangan dari sysfs (fallback ke 1000 mV jika gagal)
        val voltage = readVoltageFromSysfs()

        // Status charging
        val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || 
                        status == BatteryManager.BATTERY_STATUS_FULL

        // Temperatur (dalam 0.1°C, konversi ke °C)
        val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
        val tempCelsius = temp / 10.0

        return "$batteryPct|$voltage|${if (isCharging) 1 else 0}|$tempCelsius"
    }

    private fun readVoltageFromSysfs(): Int {
        return try {
            val voltageFile = File("/sys/class/power_supply/battery/voltage_now")
            if (voltageFile.exists() && voltageFile.canRead()) {
                val voltageNow = voltageFile.readText().trim().toIntOrNull() ?: 0
                // voltage_now dalam microvolts, konversi ke millivolts
                voltageNow / 1000
            } else {
                1000 // Fallback ke 1V (1000 mV) untuk non-root
            }
        } catch (e: Exception) {
            1000 // Fallback ke 1V jika error
        }
    }

    private fun getDeviceDebugInfo(): String {
        val debugLines = mutableListOf<String>()
        
        debugLines.add("=== VOLTAGE READ DEBUG ===")
        
        // Test 1: Check file existence
        val voltageFile = File("/sys/class/power_supply/battery/voltage_now")
        debugLines.add("File exists: ${voltageFile.exists()}")
        debugLines.add("Can read: ${voltageFile.canRead()}")
        debugLines.add("File path: ${voltageFile.absolutePath}")
        
        // Test 2: Actual voltage read
        val voltage = readVoltageFromSysfs()
        debugLines.add("Voltage read: $voltage mV")
        
        // Test 3: Raw file content
        try {
            if (voltageFile.exists()) {
                val raw = voltageFile.readText().trim()
                debugLines.add("Raw value: $raw µV")
                debugLines.add("Converted: ${raw.toIntOrNull()?.div(1000)} mV")
            }
        } catch (e: Exception) {
            debugLines.add("Read error: ${e.message}")
        }
        
        // Test 4: Battery intent (untuk comparison)
        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        debugLines.add("Battery level: $level%")
        
        // Test 5: SELinux context
        try {
            val selinux = Runtime.getRuntime().exec("getenforce").inputStream.bufferedReader().readText().trim()
            debugLines.add("SELinux: $selinux")
        } catch (e: Exception) {
            debugLines.add("SELinux: unknown")
        }

        return debugLines.joinToString("\n")
    }

    override fun onDestroy() {
        super.onDestroy()
        batteryReceiver?.let { unregisterReceiver(it) }
    }
}