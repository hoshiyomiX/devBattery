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
import android.widget.Toast;
import android.os.Process;
import android.system.OsConstants;
import android.system.Os;
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
        settings.setAllowFileAccess(true);
        
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
                

                
                return data.toString();
            } catch (Exception e) {
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
                    // SELinux blocking access - silent fallback
                } catch (Exception e) {
                    // Error reading path - silent fallback
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
                // SELinux blocking - silent fallback
            } catch (Exception e) {
                // Failed to read voltage - silent fallback
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
                info.append("=== APK DEBUG INFORMATION ===\n\n");
                
                // APK Path
                String apkPath = getPackageInfo();
                info.append("📦 APK PATH:\n");
                info.append("  ").append(apkPath).append("\n\n");
                
                // SELinux Domain Check
                info.append("🔒 SELINUX DOMAIN:\n");
                String selinuxDomain = getSelinuxDomain();
                info.append("  ").append(selinuxDomain).append("\n\n");
                
                // APK File SELinux Context
                info.append("📁 APK FILE SELINUX CONTEXT:\n");
                String selinuxContext = getApkSelinuxContext(apkPath);
                info.append("  ").append(selinuxContext).append("\n\n");
                
                // System Debug Logs
                info.append("📋 SYSTEM DEBUG LOGS:\n");
                String debugLogs = getSystemDebugLogs();
                info.append(debugLogs).append("\n");
                
            } catch (Exception e) {
                info.append("❌ Error generating debug info: ").append(e.getMessage());
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
                int pid = Process.myPid();
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
                
                // Try to read dmesg for kernel messages
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
        super.onDestroy();
    }
    
    @Override
    public void onBackPressed() {
        finishAffinity();
    }
}