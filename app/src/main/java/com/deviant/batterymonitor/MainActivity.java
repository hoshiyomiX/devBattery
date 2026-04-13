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
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.os.Process;
import org.json.JSONObject;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends Activity {
    
    private static final int MAX_HISTORY = 10;
    private static final String BUILD_VERSION = "RENUKED v3.0 - NO ROOT";
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
        settings.setAllowFileAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        
        webView.addJavascriptInterface(new BatteryBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");
    }
    
    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        
        boolean isDark = (newConfig.uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        String theme = isDark ? "dark" : "light";
        
        System.out.println("[THEME] System theme changed to: " + theme);
        
        if (webView != null) {
            runOnUiThread(() -> {
                webView.evaluateJavascript(
                    "if (typeof applyTheme === 'function') { applyTheme('" + theme + "'); }",
                    null
                );
            });
        }
    }
    
    private String getTimeStamp() {
        return new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date());
    }
        
    private void logDebugError(String category, String operation, String message) {
        String timestamp = getTimeStamp();
        System.out.println(String.format("[%s][DEBUG][%s] %s: %s", 
            timestamp, category, operation, message));
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
                    data.put("error", "Battery Intent unavailable");
                    return data.toString();
                }
                
                int capacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
                int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                String statusStr = getStatusString(status);
                int currentUa = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                String chargerVoltage = "0";
                int voltageMv;
                int tempDeci = batteryStatus.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1);
                
                // Charging detection
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    chargerVoltage = readChargerVoltageDirect();
                    try {
                        voltageMv = Integer.parseInt(chargerVoltage.trim());
                    } catch (NumberFormatException nfe) {
                        voltageMv = 1000;
                        logDebugError("ChargerVoltage", "parseInt failed", nfe.getMessage());
                    }
                    data.put("voltage_source", "charger");
                } else {
                    voltageMv = getBatteryVoltageFromManager();
                    data.put("voltage_source", "battery");
                }
                
                data.put("capacity", String.valueOf(capacity));
                data.put("status", statusStr);
                data.put("voltage", String.valueOf(voltageMv));
                data.put("current_now", String.valueOf(currentUa));
                data.put("temp", String.valueOf(tempDeci));
                data.put("source", "sepolicy_modified");
                data.put("charger_voltage", chargerVoltage);
                data.put("is_charging", status == BatteryManager.BATTERY_STATUS_CHARGING);
                
                // Dynamic voltage source data
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    data.put("voltage_display_label", "Charging Voltage");
                    data.put("voltage_source_info", "Source: Charger ADC (sysfs)");
                } else {
                    data.put("voltage_display_label", "Battery Voltage");
                    data.put("voltage_source_info", "Source: BatteryManager API (microvolts->mV)");
                }
                
                // Detailed voltage source debug
                System.out.println("[VOLTAGE] Debug - Status: " + statusStr + ", Voltage source: " + data.get("voltage_source") + 
                    ", Value: " + voltageMv + "mV, API Level: " + android.os.Build.VERSION.SDK_INT);
                if (status == BatteryManager.BATTERY_STATUS_CHARGING) {
                    System.out.println("[VOLTAGE] Debug - Charger path: /sys/devices/platform/charger/ADC_Charger_Voltage");
                } else {
                    System.out.println("[VOLTAGE] Debug - Using BatteryManager.BATTERY_PROPERTY_VOLTAGE_NOW");
                }
                
                String historyEntry = String.format(Locale.US, 
                    "[%s] %d%% | %s | %.2fV | %dmA | %.1f°C | Charger: %smV",
                    getTimeStamp(), capacity, statusStr, voltageMv/1000.0, 
                    currentUa/1000, tempDeci/10.0, chargerVoltage);
                
                batteryHistory.add(historyEntry);
                if (batteryHistory.size() > MAX_HISTORY) {
                    batteryHistory.remove(0);
                }
                
                return data.toString();
            } catch (Exception e) {
                e.printStackTrace();
                try {
                    return new JSONObject().put("error", e.getMessage()).toString();
                } catch (Exception jsonErr) {
                    return "{\"error\":\"unknown\"}";
                }
            }
        }
        
        private String readChargerVoltageDirect() {
            String[] possiblePaths = {
                "/sys/devices/platform/charger/ADC_Charger_Voltage"
            };
            
            for (String path : possiblePaths) {
                try {
                    File file = new File(path);
                    System.out.println("[DEBUG] Voltage Debug: Checking path " + path + " exists=" + file.exists() + " readable=" + file.canRead());
                    
                    if (file.exists() && file.canRead()) {
                        BufferedReader reader = new BufferedReader(new FileReader(file));
                        String value = reader.readLine();
                        reader.close();
                        
                        System.out.println("[DEBUG] Voltage Debug: Raw charger voltage value: '" + value + "'");
                        
                        if (value != null && !value.isEmpty()) {
                            value = value.trim();
                            if (value.length() > 4) {
                                long microvolts = Long.parseLong(value);
                                int millivolts = (int)(microvolts / 1000);
                                System.out.println("[DEBUG] Voltage Debug: Converted charger voltage: " + millivolts + "mV");
                                return String.valueOf(millivolts);
                            }
                            System.out.println("[DEBUG] Voltage Debug: Using raw charger voltage: " + value + "mV");
                            return value;
                        }
                    }
                } catch (FileNotFoundException e) {
                    System.out.println("[DEBUG] Voltage Debug: File not found for " + path + " - " + e.getMessage());
                } catch (SecurityException e) {
                    System.out.println("[DEBUG] Voltage Debug: SELinux blocking access to " + path + " - " + e.getMessage());
                    logDebugError("ChargerVoltage", "SELinux blocking access", e.getMessage());
                } catch (Exception e) {
                    System.out.println("[DEBUG] Voltage Debug: Error reading " + path + " - " + e.getMessage());
                    logDebugError("ChargerVoltage", "Error reading path", e.getMessage());
                }
            }
            System.out.println("[DEBUG] Voltage Debug: All charger voltage paths failed, using fallback 1000mV");
            return "1000"; // Default 1V for power calculation
        }
        
        private int getBatteryVoltageFromManager() {
            System.out.println("[DEBUG] Voltage Debug: Starting battery voltage reading, API level: " + android.os.Build.VERSION.SDK_INT);
            
            // Try BatteryManager API first (API 21+)
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP_MR1) {
                try {
                    // Use reflection: BATTERY_PROPERTY_VOLTAGE_NOW is a hidden internal constant
                    java.lang.reflect.Field voltageField = BatteryManager.class.getDeclaredField("BATTERY_PROPERTY_VOLTAGE_NOW");
                    int voltageProperty = voltageField.getInt(null);
                    int voltageUv = batteryManager.getIntProperty(voltageProperty);
                    System.out.println("[DEBUG] Voltage Debug: BatteryManager voltage (microvolts): " + voltageUv);
                    if (voltageUv > 0) {
                        // BatteryManager returns in microvolts, convert to millivolts
                        int result = voltageUv / 1000;
                        System.out.println("[DEBUG] Voltage Debug: BatteryManager converted voltage: " + result + "mV");
                        return result;
                    }
                    System.out.println("[DEBUG] Voltage Debug: BatteryManager returned invalid value: " + voltageUv);
                } catch (Exception e) {
                    System.out.println("[DEBUG] Voltage Debug: BatteryManager exception: " + e.getMessage());
                    logDebugError("BatteryVoltage", "BatteryManager error", e.getMessage());
                }
            } else {
                System.out.println("[DEBUG] Voltage Debug: API < 22, skipping BatteryManager");
            }
            
            // For API < 23 or fallback, use battery intent
            try {
                IntentFilter filter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                Intent batteryStatus = MainActivity.this.registerReceiver(null, filter);
                if (batteryStatus != null) {
                    int voltageMv = batteryStatus.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);
                    System.out.println("[DEBUG] Voltage Debug: Battery intent voltage: " + voltageMv + "mV");
                    if (voltageMv > 0) {
                        // EXTRA_VOLTAGE is already in millivolts
                        System.out.println("[DEBUG] Voltage Debug: Using battery intent voltage: " + voltageMv + "mV");
                        return voltageMv;
                    }
                    System.out.println("[DEBUG] Voltage Debug: Battery intent voltage invalid (<= 0)");
                } else {
                    System.out.println("[DEBUG] Voltage Debug: Battery intent status is null");
                }
            } catch (Exception e) {
                System.out.println("[DEBUG] Voltage Debug: Battery intent exception: " + e.getMessage());
                logDebugError("BatteryVoltage", "Intent error", e.getMessage());
            }
            
            // Fallback: try to read from remaining sysfs path
            try {
                String fallbackPath = "/sys/devices/platform/charger/ADC_Charger_Voltage";
                File file = new File(fallbackPath);
                System.out.println("[DEBUG] Voltage Debug: Battery fallback checking path " + fallbackPath + " exists=" + file.exists() + " readable=" + file.canRead());
                
                if (file.exists() && file.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(file));
                    String value = reader.readLine();
                    reader.close();
                    
                    System.out.println("[DEBUG] Voltage Debug: Battery fallback raw value: '" + value + "'");
                    
                    if (value != null && !value.isEmpty()) {
                        value = value.trim();
                        long microvolts = Long.parseLong(value);
                        int result = (int)(microvolts / 1000);
                        System.out.println("[DEBUG] Voltage Debug: Battery fallback converted: " + result + "mV");
                        return result;
                    }
                }
            } catch (SecurityException e) {
                System.out.println("[DEBUG] Voltage Debug: Battery fallback SELinux blocked: " + e.getMessage());
                logDebugError("BatteryVoltage", "SELinux blocking fallback access", e.getMessage());
            } catch (Exception e) {
                System.out.println("[DEBUG] Voltage Debug: Battery fallback error: " + e.getMessage());
                logDebugError("BatteryVoltage", "Error reading fallback", e.getMessage());
            }
            
            // Return 1000mV fallback if no voltage reading available (same as charger fallback)
            System.out.println("[DEBUG] Voltage Debug: All battery voltage methods failed, using fallback 1000mV");
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
                info.append("=== APK DEBUG INFORMATION ===\n\n");
                
                // Voltage Source Data
                info.append("VOLTAGE SOURCE DATA:\n");
                try {
                    IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                    Intent batteryStatus = MainActivity.this.registerReceiver(null, ifilter);
                    if (batteryStatus != null) {
                        int status = batteryStatus.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                        String voltageSource = (status == BatteryManager.BATTERY_STATUS_CHARGING) ? "charger" : "battery";
                        String chargerVoltage = readChargerVoltageDirect();
                        int batteryVoltage = getBatteryVoltageFromManager();
                        
                        info.append("  Source: ").append(voltageSource).append("\n");
                        info.append("  Charger Voltage: ").append(chargerVoltage).append("mV\n");
                        info.append("  Battery Voltage: ").append(batteryVoltage).append("mV\n");
                    } else {
                        info.append("  Battery status unavailable\n");
                    }
                } catch (Exception e) {
                    info.append("  Error: ").append(e.getMessage()).append("\n");
                }
                info.append("\n");
                
                // APK Path
                String apkPath = getPackageInfo();
                info.append("APK PATH:\n");
                info.append("  ").append(apkPath).append("\n\n");
                
                // SELinux Domain Check
                info.append("SELINUX DOMAIN:\n");
                String selinuxDomain = getSelinuxDomain();
                info.append("  ").append(selinuxDomain).append("\n\n");
                
                // APK File SELinux Context
                info.append("APK FILE SELINUX CONTEXT:\n");
                String selinuxContext = getApkSelinuxContext(apkPath);
                info.append("  ").append(selinuxContext).append("\n\n");
                
                // System Debug Logs
                info.append("SYSTEM DEBUG LOGS:\n");
                String debugLogs = getSystemDebugLogs();
                info.append(debugLogs).append("\n");
                
            } catch (Exception e) {
                info.append("Error generating debug info: ").append(e.getMessage());
            }
            
            return info.toString();
        }
        
        private String getPackageInfo() {
            try {
                return getApplicationInfo().sourceDir;
            } catch (Exception e) {
                return "unknown: " + e.getMessage();
            }
        }
        
        private String getSelinuxDomain() {
            try {
                String domain = "unknown";
                
                // Try to read from /proc/self/attr/current
                File attrFile = new File("/proc/self/attr/current");
                if (attrFile.exists() && attrFile.canRead()) {
                    BufferedReader reader = new BufferedReader(new FileReader(attrFile));
                    String line = reader.readLine();
                    reader.close();
                    if (line != null && !line.isEmpty()) {
                        domain = line.trim();
                    }
                }
                
                return domain;
            } catch (Exception e) {
                return "error: " + e.getMessage();
            }
        }
        
        private String getApkSelinuxContext(String apkPath) {
            try {
                if (apkPath.equals("unknown") || apkPath.startsWith("error")) {
                    return "cannot determine - APK path unknown";
                }
                
                File apkFile = new File(apkPath);
                if (!apkFile.exists()) {
                    return "file not found: " + apkPath;
                }
                
                // SELinux context access requires reflection on newer Android versions
                try {
                    Class<?> OsClass = Class.forName("android.system.Os");
                    java.lang.reflect.Method getfileconMethod = OsClass.getMethod("getfilecon", String.class);
                    Object result = getfileconMethod.invoke(null, apkPath);
                    if (result instanceof Object[] && ((Object[]) result).length > 0) {
                        String context = (String) ((Object[]) result)[0];
                        if (context != null && !context.isEmpty()) {
                            return context;
                        }
                    }
                } catch (Exception e) {
                    // Fallback to manual check
                }
                
                // Fallback: try to read from /proc/self/mountinfo for mount context
                return "context unavailable - permission denied";
                
            } catch (Exception e) {
                return "error: " + e.getMessage();
            }
        }
        
        private String getSystemDebugLogs() {
            StringBuilder logs = new StringBuilder();
            
            try {
                // Try to read logcat for SELinux denials
                String[] logcatCmd = {"logcat", "-d", "-s", "audit:*", "*:E"};
java.lang.Process logcatProcess = Runtime.getRuntime().exec(logcatCmd);
                
                BufferedReader logcatReader = new BufferedReader(
                    new InputStreamReader(logcatProcess.getInputStream()));
                
                String line;
                int logCount = 0;
                while ((line = logcatReader.readLine()) != null && logCount < 5) {
                    if (line.toLowerCase().contains("selinux") || 
                        line.toLowerCase().contains("avc: denied") ||
                        line.toLowerCase().contains("perm=deny")) {
                        logs.append("  ").append(line.trim()).append("\n");
                        logCount++;
                    }
                }
                logcatReader.close();
                logcatProcess.destroy();
                
                if (logCount == 0) {
                    logs.append("  No recent SELinux denials found\n");
                }
                
                // Try to read dmesg for kernel messages (kept for debugging purpose)
                try {
                    String[] dmesgCmd = {"dmesg"};
                    java.lang.Process dmesgProcess = Runtime.getRuntime().exec(dmesgCmd);
                    
                    BufferedReader dmesgReader = new BufferedReader(
                        new InputStreamReader(dmesgProcess.getInputStream()));
                    
                    logs.append("\n");
                    int dmesgCount = 0;
                    while ((line = dmesgReader.readLine()) != null && dmesgCount < 3) {
                        if (line.toLowerCase().contains("selinux") || 
                            line.toLowerCase().contains("audit")) {
                            logs.append("  KERNEL: ").append(line.trim()).append("\n");
                            dmesgCount++;
                        }
                    }
                    dmesgReader.close();
                    dmesgProcess.destroy();
                    
                    if (dmesgCount == 0) {
                        logs.append("  No relevant kernel messages found\n");
                    }
                    
                } catch (Exception e) {
                    logs.append("  dmesg access denied: ").append(e.getMessage()).append("\n");
                }
                
            } catch (Exception e) {
                logs.append("  Error reading system logs: ").append(e.getMessage()).append("\n");
            }
            
            return logs.toString();
        }
    }
    
    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.getSettings().setJavaScriptEnabled(false);
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
    
    @Override
    public void onBackPressed() {
        finishAffinity();
    }
}
