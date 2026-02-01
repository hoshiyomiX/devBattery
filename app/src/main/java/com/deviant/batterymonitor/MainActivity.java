package com.deviant.batterymonitor;

import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebSettings;
import android.webkit.JavascriptInterface;
import android.app.Activity;
import android.os.BatteryManager;
import android.os.Build;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    
    private static final String DEBUG_FILE = "/sdcard/battery_debug.txt";
    private static final int MAX_HISTORY = 10;
    private static final String BUILD_VERSION = "RENUKED v3.0 - NO ROOT";
    private FileWriter debugWriter;
    private BatteryManager batteryManager;
    private WebView webView;
    private List<String> batteryHistory = new ArrayList<>();
    private int updateCount = 0;
    private long startTime;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        startTime = System.currentTimeMillis();
        
        // Initialize BatteryManager
        batteryManager = (BatteryManager) getSystemService(Context.BATTERY_SERVICE);
        
        webView = new WebView(this);
        setContentView(webView);
        
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        
        webView.addJavascriptInterface(new BatteryBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
        
        initDebugFile();
        checkAVCDenials();
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        boolean isDark = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String theme = isDark ? "dark" : "light";
        
        logDebug("[THEME] System theme changed to: " + theme);
        
        if (webView != null) {
            runOnUiThread(() -> {
                webView.evaluateJavascript(
                    "if (typeof applyTheme === 'function') { applyTheme('" + theme + "'); }",
                    null
                );
            });
        }
    }
    
    private void initDebugFile() {
        try {
            File debugFile = new File(DEBUG_FILE);
            debugWriter = new FileWriter(debugFile, false);
            
            logDebug("Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            logDebug("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
            logDebug("Android: " + Build.VERSION.RELEASE + " (SDK " + Build.VERSION.SDK_INT + ")");
            logDebug("Board: " + Build.BOARD);
            
            int uiMode = getResources().getConfiguration().uiMode;
            boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            logDebug("Initial Theme: " + (isDark ? "dark" : "light"));
            logDebug("");
            
            checkSELinuxStatus();
            logDebug("");
            
            debugWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private void checkSELinuxStatus() {
        try {
            logDebug("--- SELINUX STATUS ---");
            Process process = Runtime.getRuntime().exec("getenforce");
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String status = reader.readLine();
            reader.close();
            process.destroy();
            
            logDebug("Enforcing Mode: " + (status != null ? status : "unknown"));
            
            if ("Enforcing".equalsIgnoreCase(status)) {
                logDebug("⚠️ SELinux is ENFORCING - AVC denials will block access");
            } else if ("Permissive".equalsIgnoreCase(status)) {
                logDebug("ℹ️  SELinux is PERMISSIVE - AVC denials logged but not enforced");
            } else {
                logDebug("ℹ️  SELinux is DISABLED");
            }
        } catch (Exception e) {
            logDebug("Cannot determine SELinux status: " + e.getMessage());
        }
    }
    
    private void checkAVCDenials() {
        new Thread(() -> {
            try {
                logDebug("--- LOGCAT AVC DENIALS ---");
                
                Process process = Runtime.getRuntime().exec("logcat -d -b all -v time *:S avc:V");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                String line;
                int logcatDenialCount = 0;
                List<String> batteryDenials = new ArrayList<>();
                List<String> otherDenials = new ArrayList<>();
                
                while ((line = reader.readLine()) != null) {
                    if (line.contains("avc: denied") || line.contains("avc:  denied")) {
                        logcatDenialCount++;
                        if (line.contains("sysfs") || line.contains("battery") || line.contains("charger") || 
                            line.contains("power_supply") || line.contains("devices/platform")) {
                            batteryDenials.add(line);
                        } else {
                            if (otherDenials.size() < 3) {
                                otherDenials.add(line);
                            }
                        }
                    }
                }
                
                reader.close();
                process.destroy();
                
                if (batteryDenials.size() > 0) {
                    logDebug("❌ CRITICAL: " + batteryDenials.size() + " AVC denial(s) blocking battery/charger access");
                    logDebug("   This prevents reading charger voltage from sysfs");
                    logDebug("");
                    logDebug("   Latest AVC denial(s):");
                    for (String denial : batteryDenials) {
                        logDebug("   " + denial);
                    }
                    logDebug("");
                    logDebug("   FIX: Add SEPolicy rules for:");
                    logDebug("   - Allow app to read /sys/class/power_supply/*");
                    logDebug("   - Allow app to read /sys/devices/platform/charger/*");
                } else if (otherDenials.size() > 0) {
                    logDebug("⚠️  " + otherDenials.size() + " AVC denial(s) detected (not battery-related)");
                    for (String denial : otherDenials) {
                        logDebug("   " + denial);
                    }
                } else {
                    logDebug("✓ No AVC denials blocking battery/charger access");
                }
                
                if (logcatDenialCount == 0) {
                    logDebug("ℹ️  No AVC denials found in logcat");
                }
                logDebug("");
                
                logDebug("--- DMESG AVC DENIALS ---");
                
                Process dmesgProcess = Runtime.getRuntime().exec("dmesg");
                BufferedReader dmesgReader = new BufferedReader(new InputStreamReader(dmesgProcess.getInputStream()));
                
                int dmesgDenialCount = 0;
                List<String> dmesgBatteryDenials = new ArrayList<>();
                
                while ((line = dmesgReader.readLine()) != null) {
                    if (line.contains("avc: denied") || line.contains("avc:  denied") || 
                        (line.contains("SELinux") && (line.contains("denied") || line.contains("denial")))) {
                        dmesgDenialCount++;
                        if (line.contains("sysfs") || line.contains("battery") || line.contains("charger") || 
                            line.contains("power_supply") || line.contains("devices/platform")) {
                            dmesgBatteryDenials.add(line);
                        }
                    }
                }
                
                dmesgReader.close();
                dmesgProcess.destroy();
                
                if (dmesgBatteryDenials.size() > 0) {
                    logDebug("❌ CRITICAL: " + dmesgBatteryDenials.size() + " AVC denial(s) in dmesg blocking battery/charger access");
                    logDebug("");
                    logDebug("   Latest dmesg AVC denial(s):");
                    for (String denial : dmesgBatteryDenials) {
                        logDebug("   " + denial);
                    }
                } else if (dmesgDenialCount > 0) {
                    logDebug("⚠️  " + dmesgDenialCount + " AVC denial(s) detected in dmesg (not battery-related)");
                } else {
                    logDebug("✓ No AVC denials in dmesg");
                }
                logDebug("");
                
                logDebug("--- SYSFS ACCESS TEST ---");
                String[] sysfsPaths = {
                    "/sys/class/power_supply/",
                    "/sys/devices/platform/charger/",
                    "/sys/class/power_supply/battery/"
                };
                
                for (String path : sysfsPaths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.canRead()) {
                        logDebug("✓ " + path + " - Accessible");
                    } else {
                        logDebug("✗ " + path + " - Blocked (AVC denial)");
                    }
                }
                logDebug("");
                
            } catch (Exception e) {
                logDebug("Cannot read logcat/dmesg for AVC analysis: " + e.getMessage());
                logDebug("   (Expected on production/user builds without logcat access)");
                logDebug("");
            }
        }).start();
    }
    
    private void logDebug(String message) {
        try {
            if (debugWriter != null) {
                debugWriter.write(message + "\n");
                debugWriter.flush();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    private String getTimeStamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }
    
    public class BatteryBridge {
        
        @JavascriptInterface
        public String getSystemTheme() {
            try {
                int uiMode = getResources().getConfiguration().uiMode;
                boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                return isDark ? "dark" : "light";
            } catch (Exception e) {
                return "dark";
            }
        }
        
        @JavascriptInterface
        public String getRootStatus() {
            return "{\"checked\":true,\"hasRoot\":false}";
        }
        
        @JavascriptInterface
        public String getBatteryData() {
            try {
                JSONObject data = new JSONObject();
                updateCount++;
                
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                
                if (batteryStatus == null) {
                    logDebug("[" + getTimeStamp() + "] ERROR: Battery Intent is null!");
                    data.put("error", "Battery Intent unavailable");
                    return data.toString();
                }
                
                int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                String statusStr = getStatusString(status);
                int currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                int voltageMv = readVoltageFromSysfs();
                int tempDeci = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                
                String chargerVoltage = "0";
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    chargerVoltage = readChargerVoltageDirect();
                }
                
                data.put("capacity", String.valueOf(capacity));
                data.put("status", statusStr);
                data.put("voltage", String.valueOf(voltageMv));
                data.put("current_now", String.valueOf(currentUa));
                data.put("temp", String.valueOf(tempDeci));
                data.put("source", "sepolicy_modified");
                data.put("charger_voltage", chargerVoltage);
                
                String historyEntry = String.format(Locale.US, 
                    "[%s] %d%% | %s | %.2fV | %dmA | %.1f°C | Charger: %smV",
                    getTimeStamp(), capacity, statusStr, voltageMv/1000.0, 
                    currentUa/1000, tempDeci/10.0, chargerVoltage);
                
                batteryHistory.add(historyEntry);
                if (batteryHistory.size() > MAX_HISTORY) {
                    batteryHistory.remove(0);
                }
                
                if (updateCount % 10 == 0) {
                    logDebug("--- BATTERY UPDATE #" + updateCount + " ---");
                    logDebug(historyEntry);
                    
                    if (!chargerVoltage.equals("0")) {
                        logDebug("✓ Charger voltage detected: " + chargerVoltage + " mV");
                    }
                    logDebug("");
                }
                
                return data.toString();
            } catch (Exception e) {
                logDebug("[" + getTimeStamp() + "] ERROR in getBatteryData: " + e.getMessage());
                e.printStackTrace();
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
        
        private String readChargerVoltageDirect() {
            String[] possiblePaths = {
                "/sys/devices/platform/charger/ADC_Charger_Voltage",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/ac/voltage_now",
                "/sys/class/power_supply/battery/input_voltage_now"
            };
            
            for (String path : possiblePaths) {
                try {
                    File file = new File(path);
                    if (file.exists() && file.canRead()) {
                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        String value = reader.readLine();
                        reader.close();
                        
                        if (value != null && !value.isEmpty()) {
                            value = value.trim();
                            if (value.length() > 4) {
                                long microvolts = Long.parseLong(value);
                                return String.valueOf(microvolts / 1000);
                            }
                            return value;
                        }
                    }
                } catch (FileNotFoundException e) {
                    // Path doesn't exist
                } catch (SecurityException e) {
                    logDebug("[" + getTimeStamp() + "] ❌ AVC DENIAL: Cannot read " + path);
                    logDebug("   This is a SELinux policy violation blocking sysfs access");
                    logDebug("   FIX: Add SEPolicy allow rule for this path");
                } catch (Exception e) {
                    logDebug("[" + getTimeStamp() + "] Error reading " + path + ": " + e.getMessage());
                }
            }
            return "0";
        }
        
        private int readVoltageFromSysfs() {
            try {
                File file = new File("/sys/devices/platform/charger/ADC_Charger_Voltage");
                if (file.exists() && file.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String value = reader.readLine();
                    reader.close();

                    if (value != null && !value.isEmpty()) {
                        value = value.trim();
                        long microvolts = Long.parseLong(value);
                        return (int)(microvolts / 1000);
                    }
                }
            } catch (SecurityException e) {
                logDebug("[" + getTimeStamp() + "] ❌ AVC DENIAL: SELinux blocking /sys/devices/platform/charger/ADC_Charger_Voltage");
                logDebug("   Required: allow app sysfs_file:read");
            } catch (Exception e) {
                logDebug("[" + getTimeStamp() + "] Failed to read voltage: " + e.getMessage());
            }
            return 1000;
        }
        
        private String getStatusString(int status) {
            switch (status) {
                case BatteryManager.BATTERY_STATUS_CHARGING:
                    return "Charging";
                case BatteryManager.BATTERY_STATUS_DISCHARGING:
                    return "Discharging";
                case BatteryManager.BATTERY_STATUS_FULL:
                    return "Full";
                case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                    return "Not charging";
                default:
                    return "Unknown";
            }
        }
        
        @JavascriptInterface
        public String getDebugInfo() {
            StringBuilder info = new StringBuilder();
            
            try {
                info.append("--- SELINUX / AVC STATUS ---\n");
                String selinuxStatus = getSELinuxStatus();
                info.append("SELinux Mode: ").append(selinuxStatus).append("\n");
                
                if ("Enforcing".equals(selinuxStatus)) {
                    info.append("⚠️  AVC denials will BLOCK sysfs access\n");
                    info.append("   Check debug log for specific denial details\n");
                } else if ("Permissive".equals(selinuxStatus)) {
                    info.append("ℹ️  AVC denials logged but NOT enforced\n");
                } else {
                    info.append("✓ SELinux disabled - no AVC blocking\n");
                }
                info.append("\n");
                
                info.append("--- LOGCAT AVC DENIALS ---\n");
                List<String> logcatDenials = getLogcatAVCDenials();
                if (!logcatDenials.isEmpty()) {
                    info.append("Found ").append(logcatDenials.size()).append(" AVC denial(s) in logcat:\n");
                    for (String denial : logcatDenials) {
                        info.append("  ").append(denial).append("\n");
                    }
                } else {
                    info.append("✓ No AVC denials in logcat\n");
                }
                info.append("\n");
                
                info.append("--- DMESG AVC DENIALS ---\n");
                List<String> dmesgDenials = getDmesgAVCDenials();
                if (!dmesgDenials.isEmpty()) {
                    info.append("Found ").append(dmesgDenials.size()).append(" AVC denial(s) in dmesg:\n");
                    for (String denial : dmesgDenials) {
                        info.append("  ").append(denial).append("\n");
                    }
                } else {
                    info.append("✓ No AVC denials in dmesg\n");
                }
                info.append("\n");
                
                info.append("--- SYSFS ACCESS CHECK ---\n");
                String[] sysfsPaths = {
                    "/sys/class/power_supply/",
                    "/sys/devices/platform/charger/",
                    "/sys/class/power_supply/battery/"
                };
                
                for (String path : sysfsPaths) {
                    File dir = new File(path);
                    if (dir.exists() && dir.canRead()) {
                        info.append("✓ ").append(path).append(" - Accessible\n");
                    } else {
                        info.append("✗ ").append(path).append(" - Blocked (AVC denial)\n");
                    }
                }
                info.append("\n");
                
                info.append("--- BUILD INFO ---\n");
                info.append("Build: ").append(BUILD_VERSION).append("\n");
                info.append("Package: com.deviant.batterymonitor\n\n");
                
                info.append("--- SYSTEM INFO ---\n");
                info.append("Device: ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");
                info.append("Android: ").append(Build.VERSION.RELEASE).append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n");
                info.append("Board: ").append(Build.BOARD).append("\n");
                info.append("Debug Log: ").append(DEBUG_FILE).append("\n\n");
                
                long uptime = (System.currentTimeMillis() - startTime) / 1000;
                info.append("--- SESSION STATS ---\n");
                info.append("Uptime: ").append(uptime).append(" seconds\n");
                info.append("Updates: ").append(updateCount).append("\n\n");
                
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                
                int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                int current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int voltageMv = readVoltageFromSysfs();
                int tempDeci = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                
                info.append("--- CURRENT STATE ---\n");
                info.append("✓ Capacity: ").append(capacity).append("%\n");
                info.append("✓ Status: ").append(getStatusString(status)).append("\n");
                info.append("✓ Voltage: ").append(voltageMv).append(" mV (").append(String.format(Locale.US, "%.2f", voltageMv/1000.0)).append(" V)\n");
                info.append("✓ Current: ").append(current).append(" µA (").append(current/1000).append(" mA)\n");
                info.append("✓ Temp: ").append(tempDeci).append(" dC (").append(String.format(Locale.US, "%.1f", tempDeci/10.0)).append(" °C)\n");
                
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    String chargerVoltage = readChargerVoltageDirect();
                    if (!chargerVoltage.equals("0")) {
                        info.append("✓ Charger: ").append(chargerVoltage).append(" mV (SEPolicy)\n");
                    } else {
                        info.append("✗ Charger: Not available - likely blocked by AVC denial\n");
                        info.append("   Check ").append(DEBUG_FILE).append(" for details\n");
                    }
                }
                
                if (!batteryHistory.isEmpty()) {
                    info.append("\n--- BATTERY HISTORY (Last ").append(batteryHistory.size()).append(") ---\n");
                    for (String entry : batteryHistory) {
                        info.append(entry).append("\n");
                    }
                }
                
            } catch (Exception e) {
                info.append("❌ Error generating debug info: ").append(e.getMessage());
            }
            
            return info.toString();
        }
        
        private String getSELinuxStatus() {
            try {
                Process process = Runtime.getRuntime().exec("getenforce");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String status = reader.readLine();
                reader.close();
                process.destroy();
                return status != null ? status.trim() : "unknown";
            } catch (Exception e) {
                return "unknown";
            }
        }
        
        private List<String> getLogcatAVCDenials() {
            List<String> denials = new ArrayList<>();
            try {
                Process process = Runtime.getRuntime().exec("logcat -d -b all -v time *:S avc:V");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("avc: denied") || line.contains("avc:  denied")) {
                        denials.add(line);
                    }
                }
                
                reader.close();
                process.destroy();
            } catch (Exception e) {
                logDebug("Cannot read logcat for AVC analysis: " + e.getMessage());
            }
            return denials;
        }
        
        private List<String> getDmesgAVCDenials() {
            List<String> denials = new ArrayList<>();
            try {
                Process process = Runtime.getRuntime().exec("dmesg");
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("avc: denied") || line.contains("avc:  denied") || 
                        line.contains("SELinux") && (line.contains("denied") || line.contains("denial"))) {
                        denials.add(line);
                    }
                }
                
                reader.close();
                process.destroy();
            } catch (Exception e) {
                logDebug("Cannot read dmesg for AVC analysis: " + e.getMessage());
            }
            return denials;
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (debugWriter != null) {
                long uptime = (System.currentTimeMillis() - startTime) / 1000;
                logDebug("--- SESSION END ---");
                logDebug("Build: " + BUILD_VERSION);
                logDebug("Total Updates: " + updateCount);
                logDebug("Uptime: " + uptime + " seconds");
                logDebug("Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
                logDebug("\n========================================\n");
                debugWriter.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    @Override
    public void onBackPressed() {
        finishAffinity();
    }
}