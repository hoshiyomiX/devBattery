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

public class MainActivity extends Activity {
    
    private static final String DEBUG_FILE = "/sdcard/battery_debug.txt";
    private FileWriter debugWriter;
    private BatteryManager batteryManager;
    private WebView webView; // Store WebView reference for theme changes
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
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
    }
    
    // Detect system theme changes and notify JavaScript
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        // Detect theme change
        boolean isDark = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String theme = isDark ? "dark" : "light";
        
        logDebug("[THEME] System theme changed to: " + theme);
        
        // Notify JavaScript
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
            logDebug("=== DevBattery Monitor Log (SEPolicy Modified) ===");
            logDebug("Timestamp: " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()));
            logDebug("Device: " + android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL);
            
            // Log initial theme
            int uiMode = getResources().getConfiguration().uiMode;
            boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
            logDebug("Initial Theme: " + (isDark ? "dark" : "light"));
            debugWriter.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
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
    
    public class BatteryBridge {
        
        @JavascriptInterface
        public String getSystemTheme() {
            try {
                int uiMode = getResources().getConfiguration().uiMode;
                boolean isDark = (uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
                String theme = isDark ? "dark" : "light";
                return theme;
            } catch (Exception e) {
                return "dark"; // Default to dark on error
            }
        }
        
        @JavascriptInterface
        public String getRootStatus() {
            // SEPolicy modified version - no root needed
            return "{\"checked\":true,\"hasRoot\":false}";
        }
        
        @JavascriptInterface
        public String getBatteryData() {
            try {
                JSONObject data = new JSONObject();
                
                // Get battery status from Intent
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                
                if (batteryStatus == null) {
                    logDebug("ERROR: Battery Intent is null!");
                    data.put("error", "Battery Intent unavailable");
                    return data.toString();
                }
                
                // Get capacity from BatteryManager (API 21+)
                int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                
                // Get status from Intent
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                String statusStr = getStatusString(status);
                
                // Get current from BatteryManager (API 21+)
                int currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                
                // Get voltage & temperature from Intent (API 1+)
                int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                int tempDeci = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                
                // Build response
                data.put("capacity", String.valueOf(capacity));
                data.put("status", statusStr);
                data.put("voltage", String.valueOf(voltageMv));
                data.put("current_now", String.valueOf(currentUa));
                data.put("temp", String.valueOf(tempDeci));
                data.put("source", "sepolicy_modified");
                
                // Try to read charger voltage directly (SELinux policy allows this)
                String chargerVoltage = "0";
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    chargerVoltage = readChargerVoltageDirect();
                }
                data.put("charger_voltage", chargerVoltage);
                
                logDebug(String.format(Locale.US, "Battery: %d%% | %s | %d mV | %d uA | %d dC | Charger: %s mV", 
                    capacity, statusStr, voltageMv, currentUa, tempDeci, chargerVoltage));
                
                return data.toString();
            } catch (Exception e) {
                logDebug("ERROR in getBatteryData: " + e.getMessage());
                e.printStackTrace();
                return "{\"error\":\"" + e.getMessage() + "\"}";
            }
        }
        
        private String readChargerVoltageDirect() {
            // Read charger voltage directly without root (SEPolicy modified)
            String[] possiblePaths = {
                "/sys/devices/platform/charger/ADC_Charger_Voltage",
                "/sys/class/power_supply/usb/voltage_now",
                "/sys/class/power_supply/ac/voltage_now"
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
                            // Some paths return voltage in microvolts, convert to millivolts
                            if (value.length() > 4) {
                                long microvolts = Long.parseLong(value);
                                return String.valueOf(microvolts / 1000);
                            }
                            return value;
                        }
                    }
                } catch (Exception e) {
                    logDebug("Failed to read " + path + ": " + e.getMessage());
                }
            }
            return "0";
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
            info.append("Debug file: ").append(DEBUG_FILE).append("\n\n");
            info.append("Device: ").append(android.os.Build.MANUFACTURER).append(" ").append(android.os.Build.MODEL).append("\n");
            info.append("Android: ").append(android.os.Build.VERSION.RELEASE).append("\n");
            info.append("Version: SEPolicy Modified (No Root Required)\n\n");
            
            try {
                IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = registerReceiver(null, ifilter);
                
                int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                int current = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                int tempDeci = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                
                info.append("✓ Capacity: ").append(capacity).append("%\n");
                info.append("✓ Status: ").append(getStatusString(status)).append("\n");
                info.append("✓ Voltage: ").append(voltageMv).append(" mV\n");
                info.append("✓ Current: ").append(current).append(" µA\n");
                info.append("✓ Temp: ").append(tempDeci).append(" dC\n");
                
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    String chargerVoltage = readChargerVoltageDirect();
                    if (!chargerVoltage.equals("0")) {
                        info.append("✓ Charger Voltage: ").append(chargerVoltage).append(" mV (SEPolicy)\n");
                    } else {
                        info.append("✗ Charger Voltage: Not available\n");
                    }
                }
                
            } catch (Exception e) {
                info.append("❌ API failed: ").append(e.getMessage());
            }
            
            return info.toString();
        }
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (debugWriter != null) {
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