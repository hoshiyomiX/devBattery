package com.hoshiyomi.devbattery

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsetsController
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebSettings
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.File
import org.json.JSONObject

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var themeManager: ThemeManager
    private var hasRootAccess = false
    private var rootCheckDone = false
    
    // Voltage debug tracking
    private val voltageAttemptLog = mutableListOf<VoltageAttempt>()
    private val maxLogEntries = 50

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            webView.post {
                webView.evaluateJavascript("if(typeof updateBattery === 'function') updateBattery();", null)
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        themeManager = ThemeManager(this)
        
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setupImmersiveMode()
        
        webView = WebView(this)
        setContentView(webView)
        
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = true
            allowContentAccess = true
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                safeBrowsingEnabled = true
            }
            
            cacheMode = WebSettings.LOAD_DEFAULT
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }
        }
        
        webView.addJavascriptInterface(AndroidBridge(), "Android")
        WebView.setWebContentsDebuggingEnabled(true)
        
        webView.loadUrl("file:///android_asset/index.html")
        
        registerReceiver(batteryReceiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        checkRootAccess()
    }

    private fun setupImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.apply {
                systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val theme = themeManager.getCurrentTheme()
        webView.post {
            webView.evaluateJavascript(
                """if(typeof applyTheme === 'function') applyTheme('$theme');""",
                null
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(batteryReceiver)
        } catch (e: Exception) {
        }
        webView.destroy()
    }

    private fun checkRootAccess() {
        Thread {
            hasRootAccess = isDeviceRooted()
            rootCheckDone = true
        }.start()
    }

    private fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/system/app/Superuser.apk",
            "/sbin/su",
            "/system/bin/su",
            "/system/xbin/su",
            "/data/local/xbin/su",
            "/data/local/bin/su",
            "/system/sd/xbin/su",
            "/system/bin/failsafe/su",
            "/data/local/su",
            "/su/bin/su"
        )
        
        for (path in paths) {
            if (File(path).exists()) return true
        }
        
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "id"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readLine()
            process.waitFor()
            output?.contains("uid=0") == true
        } catch (e: Exception) {
            false
        }
    }
    
    data class VoltageAttempt(
        val timestamp: Long,
        val path: String,
        val method: String,
        val success: Boolean,
        val value: Int,
        val error: String?,
        val exitCode: Int?,
        val selinuxDenied: Boolean
    )

    inner class AndroidBridge {
        
        @JavascriptInterface
        fun getSystemTheme(): String {
            return themeManager.getCurrentTheme()
        }
        
        @JavascriptInterface
        fun getBatteryData(): String {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            
            return try {
                val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
                val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
                val capacity = if (level >= 0 && scale > 0) {
                    (level * 100 / scale.toFloat()).toInt()
                } else 0
                
                val status = batteryStatus?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
                val statusText = when (status) {
                    BatteryManager.BATTERY_STATUS_CHARGING -> "Charging"
                    BatteryManager.BATTERY_STATUS_DISCHARGING -> "Discharging"
                    BatteryManager.BATTERY_STATUS_FULL -> "Full"
                    BatteryManager.BATTERY_STATUS_NOT_CHARGING -> "Not charging"
                    else -> "Unknown"
                }
                
                val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0) ?: 0
                val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                
                val bm = getSystemService(Context.BATTERY_SERVICE) as BatteryManager
                val currentNow = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW)
                } else 0
                
                // Try multiple voltage paths with detailed logging
                val chargerVoltage = if (hasRootAccess) {
                    tryMultipleVoltagePaths()
                } else 0
                
                val powerNow = if (hasRootAccess) {
                    readSysfsFileWithLogging("/sys/class/power_supply/battery/power_now", "power_now")
                } else 0
                
                JSONObject().apply {
                    put("capacity", capacity)
                    put("status", statusText)
                    put("voltage", voltage)
                    put("current_now", currentNow)
                    put("temp", temp)
                    put("charger_voltage", chargerVoltage)
                    put("power_now", powerNow)
                }.toString()
                
            } catch (e: Exception) {
                JSONObject().apply {
                    put("error", e.message ?: "Unknown error")
                }.toString()
            }
        }
        
        @JavascriptInterface
        fun getDebugInfo(): String {
            return buildString {
                append("========== VOLTAGE TILE DEBUG ==========\n\n")
                
                append("--- SYSTEM INFO ---\n")
                append("Device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                append("Android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                append("Kernel: ${System.getProperty("os.version")}\n")
                append("Root Access: ${if (hasRootAccess) "YES" else "NO"}\n")
                append("Current Theme: ${themeManager.getCurrentTheme()}\n\n")
                
                append("--- VOLTAGE READ ATTEMPTS (Last ${voltageAttemptLog.size}) ---\n")
                if (voltageAttemptLog.isEmpty()) {
                    append("No attempts yet\n")
                } else {
                    voltageAttemptLog.takeLast(20).forEach { attempt ->
                        val timeStr = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.US)
                            .format(java.util.Date(attempt.timestamp))
                        append("[$timeStr] ${attempt.path}\n")
                        append("  Method: ${attempt.method}\n")
                        append("  Success: ${attempt.success}\n")
                        if (attempt.success) {
                            append("  Value: ${attempt.value} (${attempt.value / 1000.0}V)\n")
                        } else {
                            append("  Error: ${attempt.error}\n")
                            append("  Exit Code: ${attempt.exitCode}\n")
                            append("  SELinux Denied: ${attempt.selinuxDenied}\n")
                        }
                        append("\n")
                    }
                }
                
                append("\n--- SYSFS FILE EXPLORATION ---\n")
                append(explorePowerSupplyFiles())
                
                append("\n--- LOGCAT (Last 30 lines, filtered: power_supply, voltage) ---\n")
                append(getLogcat())
                
                append("\n--- DMESG (Last 30 lines, filtered: power, voltage, charger) ---\n")
                append(getDmesg())
                
                append("\n--- SELINUX AVC DENIALS (Last 20) ---\n")
                append(getSelinuxDenials())
                
                append("\n--- FILE PERMISSIONS CHECK ---\n")
                append(checkFilePermissions())
            }
        }
        
        @JavascriptInterface
        fun getRootStatus(): String {
            return JSONObject().apply {
                put("hasRoot", hasRootAccess)
                put("checked", rootCheckDone)
            }.toString()
        }
        
        @JavascriptInterface
        fun requestRootAccess() {
            Thread {
                try {
                    val process = Runtime.getRuntime().exec("su")
                    process.outputStream.write("id\n".toByteArray())
                    process.outputStream.flush()
                    process.outputStream.close()
                    process.waitFor()
                    hasRootAccess = process.exitValue() == 0
                    rootCheckDone = true
                } catch (e: Exception) {
                    hasRootAccess = false
                }
            }.start()
        }
        
        private fun tryMultipleVoltagePaths(): Int {
            val paths = listOf(
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/usb/input_voltage_now",
                "/sys/class/power_supply/usb/voltage_max",
                "/sys/class/power_supply/ac/voltage_now",
                "/sys/class/power_supply/battery/input_suspend",
                "/sys/class/qcom-battery/voltage_now",
                "/sys/class/power_supply/main/voltage_now"
            )
            
            for (path in paths) {
                val value = readSysfsFileWithLogging(path, "charger_voltage")
                if (value > 0) return value
            }
            
            return 0
        }
        
        private fun readSysfsFileWithLogging(path: String, label: String): Int {
            if (!hasRootAccess) return 0
            
            val startTime = System.currentTimeMillis()
            var value = 0
            var error: String? = null
            var exitCode: Int? = null
            var selinuxDenied = false
            
            try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat $path"))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val errorReader = BufferedReader(InputStreamReader(process.errorStream))
                
                val line = reader.readLine()
                val errorOutput = errorReader.readText()
                
                process.waitFor()
                exitCode = process.exitValue()
                
                if (exitCode == 0 && line != null) {
                    value = line.trim().toIntOrNull() ?: 0
                } else {
                    error = errorOutput.ifEmpty { "Empty output or parse error" }
                    selinuxDenied = errorOutput.contains("Permission denied") || 
                                    errorOutput.contains("selinux") ||
                                    errorOutput.contains("avc")
                }
                
            } catch (e: Exception) {
                error = e.message ?: e.javaClass.simpleName
            }
            
            val attempt = VoltageAttempt(
                timestamp = startTime,
                path = path,
                method = "su -c cat",
                success = value > 0,
                value = value,
                error = error,
                exitCode = exitCode,
                selinuxDenied = selinuxDenied
            )
            
            voltageAttemptLog.add(attempt)
            if (voltageAttemptLog.size > maxLogEntries) {
                voltageAttemptLog.removeAt(0)
            }
            
            return value
        }
        
        private fun explorePowerSupplyFiles(): String {
            if (!hasRootAccess) return "Root required\n"
            
            return try {
                val process = Runtime.getRuntime().exec(arrayOf(
                    "su", "-c", 
                    "find /sys/class/power_supply -name '*voltage*' -o -name '*charger*' 2>/dev/null | head -50"
                ))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                process.waitFor()
                output.ifEmpty { "No voltage/charger files found\n" }
            } catch (e: Exception) {
                "Error: ${e.message}\n"
            }
        }
        
        private fun getLogcat(): String {
            if (!hasRootAccess) return "Root required\n"
            
            return try {
                val process = Runtime.getRuntime().exec(arrayOf(
                    "su", "-c",
                    "logcat -d -t 30 -e 'power_supply|voltage|charger' *:W"
                ))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                process.waitFor()
                output.ifEmpty { "No matching logcat entries\n" }
            } catch (e: Exception) {
                "Error: ${e.message}\n"
            }
        }
        
        private fun getDmesg(): String {
            if (!hasRootAccess) return "Root required\n"
            
            return try {
                val process = Runtime.getRuntime().exec(arrayOf(
                    "su", "-c",
                    "dmesg | grep -iE 'power|voltage|charger' | tail -30"
                ))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                process.waitFor()
                output.ifEmpty { "No matching dmesg entries\n" }
            } catch (e: Exception) {
                "Error: ${e.message}\n"
            }
        }
        
        private fun getSelinuxDenials(): String {
            if (!hasRootAccess) return "Root required\n"
            
            return try {
                val process = Runtime.getRuntime().exec(arrayOf(
                    "su", "-c",
                    "dmesg | grep 'avc.*denied' | tail -20"
                ))
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                val output = reader.readText()
                process.waitFor()
                output.ifEmpty { "No AVC denials found (good!)\n" }
            } catch (e: Exception) {
                "Error: ${e.message}\n"
            }
        }
        
        private fun checkFilePermissions(): String {
            if (!hasRootAccess) return "Root required\n"
            
            val paths = listOf(
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/battery/voltage_now",
                "/sys/class/power_supply/ac/voltage_now"
            )
            
            return buildString {
                paths.forEach { path ->
                    try {
                        val process = Runtime.getRuntime().exec(arrayOf(
                            "su", "-c",
                            "ls -lZ $path 2>&1"
                        ))
                        val reader = BufferedReader(InputStreamReader(process.inputStream))
                        val output = reader.readLine()
                        process.waitFor()
                        append("$path:\n  $output\n")
                    } catch (e: Exception) {
                        append("$path: Error - ${e.message}\n")
                    }
                }
            }
        }
    }
}