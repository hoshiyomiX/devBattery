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
import org.json.JSONObject
import java.io.File

class MainActivity : AppCompatActivity() {

    constructor() : super()

    private lateinit var webView: WebView
    private var batteryReceiver: BroadcastReceiver? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        webView = findViewById(R.id.activity_main_webview)
        webView.settings.javaScriptEnabled = true
        webView.webViewClient = WebViewClient()

        // Injeksi JavaScript interface
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun getBatteryData(): String {
                return getBatteryStatusJSON()
            }

            @android.webkit.JavascriptInterface
            fun getDebugInfo(): String {
                return getDeviceDebugInfo()
            }

            @android.webkit.JavascriptInterface
            fun getSystemTheme(): String {
                return "dark" // Force dark theme for Material Design 3
            }
        }, "Android")

        webView.loadUrl("file:///android_asset/index.html")

        setupBatteryBroadcast()
    }

    private fun setupBatteryBroadcast() {
        batteryReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                webView.evaluateJavascript("if(typeof updateBattery === 'function') updateBattery();", null)
            }
        }
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
    }

    private fun getBatteryStatusJSON(): String {
        return try {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            
            // Baca level baterai (0-100)
            val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
            val batteryPct = if (level >= 0 && scale > 0) (level * 100 / scale.toFloat()).toInt() else 0

            // Baca tegangan dari sysfs (fallback ke 1000 mV jika gagal)
            val voltage = readVoltageFromSysfs()

            // Status charging
            val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val statusText = when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                BatteryManager.BATTERY_STATUS_FULL -> "Full"
                BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not Charging"
                else -> "Unknown"
            }

            // Arus (microamps ke milliamps)
            val currentNow = batteryStatus?.getIntExtra(BatteryManager.EXTRA_CURRENT_NOW, 0) ?: 0

            // Temperatur (dalam 0.1°C)
            val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0

            // Build JSON
            val json = JSONObject()
            json.put("capacity", batteryPct)
            json.put("voltage", voltage)  // dalam mV
            json.put("current_now", currentNow)  // dalam µA
            json.put("temp", temp)  // dalam 0.1°C
            json.put("status", statusText)
            
            json.toString()
        } catch (e: Exception) {
            JSONObject().apply {
                put("error", e.message ?: "Unknown error")
            }.toString()
        }
    }

    private fun readVoltageFromSysfs(): Int {
        return try {
            val voltageFile = File("/sys/devices/platform/charger/ADC_Charger_Voltage")
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
        val voltageFile = File("/sys/devices/platform/charger/ADC_Charger_Voltage")
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