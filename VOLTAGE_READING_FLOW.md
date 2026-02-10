# Voltage Reading Flow Documentation

## Overview
Aplikasi DevBattery Monitor menggunakan multi-layer approach untuk membaca nilai voltage dari baterai Android dengan fallback mechanism yang robust.

## Architecture Flow

### 1. Data Collection Layer (Android Native - MainActivity.java)
```
┌─────────────────────────────────────────────────────────────┐
│                     BATTERY BRIDGE                          │
│                    (JavascriptInterface)                    │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                getBatteryData() Method                      │
│  - Mengecek battery status (charging/discharging)            │
│  - Memilih metode pembacaan voltage sesuai status           │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │ STATUS CHECK     │
                    │                 │
                    │ Charging?       │
                    │                 │
                    └─────────────────┘
                              │
                    YES       │        NO
                              ▼
┌─────────────────────────┐   │   ┌──────────────────────────┐
│ readChargerVoltage     │   │   │ readVoltageFromSysfs     │
│ Direct()               │   │   │ ()                       │
│                        │   │   │                          │
│ Charger voltage paths  │   │   │ Battery voltage paths    │
└─────────────────────────┘   │   └──────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │   DATA RETURN   │
                    │   (mV format)   │
                    └─────────────────┘
```

### 2. Voltage Reading Methods

#### A. When Charging (readChargerVoltageDirect)
**Priority Order:**
1. `/sys/devices/platform/charger/ADC_Charger_Voltage`
2. `/sys/class/power_supply/usb/voltage_now`
3. `/sys/class/power_supply/ac/voltage_now`
4. `/sys/class/power_supply/battery/input_voltage_now`

**Process:**
- Read file content (microvolts - μV)
- Convert to millivolts: `μV / 1000 = mV`
- Return `0` if all paths fail

#### B. When Discharging (readVoltageFromSysfs)
**Priority Order:**
1. `/sys/class/power_supply/battery/voltage_now`
2. `/sys/class/power_supply/bmc156_battery/voltage_now`
3. `/sys/devices/platform/battery/power_supply/battery/voltage_now`

**Process:**
- Read file content (microvolts - μV)
- Convert to millivolts: `μV / 1000 = mV`
- Return `3700mV` as default fallback

### 3. Data Processing Layer (JavaScript Frontend)

#### Frontend Processing (main.js:402-413)
```javascript
// Backend sends mV, divide by 1000 for volts display
const voltageRaw = parseInt(data.voltage) || 1000; // mV from Android
const voltage = formatNumber(voltageRaw / 1000, 2); // Convert to V

// Power calculation: V × A = W
const power = formatPower(Math.abs((voltageRaw * current_uA) / 1000000000));
```

#### Battery Manager Processing (battery.js:68,71)
```javascript
// Additional processing in BatteryManager class
batteryData.voltageV = batteryData.voltage / 1000; // Convert to volts
batteryData.powerW = Math.abs((batteryData.voltage * batteryData.current) / 1000000000);
```

### 4. Data Flow Summary

```
Android Kernel (sysfs) → Android Framework → BatteryManager → JavaScript → UI Display
       μV                     μV             mV            mV/V        V/mA/W
```

## Error Handling & Fallback

### Security Handling
- **SELinux Denials**: Silent fallback to next path
- **File Not Found**: Continue to next path in priority list
- **Permission Denied**: Try alternative methods

### Fallback Values
- **Charging**: `0mV` (indicates charging voltage unavailable)
- **Discharging**: `3700mV` (typical 3.7V Li-ion battery)
- **Display**: `1.0V` (minimum safe fallback)

## Performance Considerations

### Update Frequency
- **Interval**: 1000ms (1 second)
- **Efficiency**: Single API call for all battery metrics
- **Caching**: No persistent caching, always real-time

### File System Access
- **Minimal Impact**: Sequential path checking
- **Graceful Degradation**: Fast fallback on unavailable paths
- **Security Compliant**: Works within SELinux constraints

## Troubleshooting

### Common Issues
1. **Voltage shows 0.00V**: Check charging status and sysfs paths
2. **Fixed voltage at 3.70V**: Using fallback, sysfs access blocked
3. **Inconsistent readings**: Kernel/hardware specific path variations

### Debug Information
Use `Android.getDebugInfo()` to check:
- SELinux domain and contexts
- File access permissions
- System log errors

## Implementation Notes

- **No Root Required**: Uses standard Android APIs and sysfs
- **Cross-Device Compatible**: Multiple path fallback for device variations
- **Power Efficient**: Minimal CPU usage with optimized file access
- **Real-Time**: Direct kernel reading with no intermediate layers