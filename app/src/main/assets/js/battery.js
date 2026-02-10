/**
 * battery.js
 * Battery data management and Android bridge interface
 */

class BatteryManager {
    constructor() {
        this.listeners = [];
        this.lastData = null;
        this.updateInterval = null;
        
        // Stats
        this.stats = {
            updates: 0,
            startTime: Date.now(),
            errors: []
        };
    }
    
    start(interval = 1000) {
        console.log('[Battery] Starting updates (interval: ' + interval + 'ms)');
        
        // Initial update
        this.update();
        
        // Periodic updates
        this.updateInterval = setInterval(() => {
            this.update();
        }, interval);
    }
    
    stop() {
        if (this.updateInterval) {
            clearInterval(this.updateInterval);
            this.updateInterval = null;
        }
        console.log('[Battery] Stopped');
    }
    
    update() {
        try {
            // Check if Android bridge exists
            if (typeof Android === 'undefined' || !Android.getBatteryData) {
                throw new Error('Android bridge not available');
            }
            
            // Get data from Android
            const jsonData = Android.getBatteryData();
            const data = JSON.parse(jsonData);
            
            if (data.error) {
                throw new Error(data.error);
            }
            
            // Parse values
            const batteryData = {
                capacity: parseInt(data.capacity) || 0,
                status: data.status || 'Unknown',
                voltage: parseInt(data.voltage) || 0,
                current: parseInt(data.current_now) || 0,
                temperature: parseInt(data.temp) || 0,
                chargerVoltage: data.charger_voltage || '0',
                source: data.source || 'unknown',
                timestamp: Date.now()
            };
            
            // DERIVED VALUES CONVERSION: Backend raw data → human-readable units
            batteryData.voltageV = batteryData.voltage / 1000;        // mV → V (voltage)
            batteryData.currentMA = Math.floor(batteryData.current / 1000); // μA → mA (current)
            batteryData.temperatureC = batteryData.temperature / 10;    // deci-Celsius → Celsius
            batteryData.powerW = Math.abs((batteryData.voltage * batteryData.current) / 1000000000); // mV×μA → W
            
            // Status flags
            batteryData.isCharging = batteryData.status.toLowerCase() === 'charging';
            batteryData.isDischarging = batteryData.status.toLowerCase() === 'discharging' || 
                                        batteryData.status.toLowerCase() === 'not charging';
            
            this.lastData = batteryData;
            this.stats.updates++;
            
            // Notify listeners
            this.notifyListeners(batteryData);
            
        } catch (error) {
            console.error('[Battery] Update failed:', error);
            this.stats.errors.push({
                time: Date.now(),
                message: error.message
            });
            
            // Notify listeners of error
            this.notifyListeners({ error: error.message });
        }
    }
    
    subscribe(callback) {
        this.listeners.push(callback);
        
        // Immediately call with last data if available
        if (this.lastData) {
            callback(this.lastData);
        }
        
        return () => {
            this.listeners = this.listeners.filter(cb => cb !== callback);
        };
    }
    
    notifyListeners(data) {
        this.listeners.forEach(callback => {
            try {
                callback(data);
            } catch (error) {
                console.error('[Battery] Listener error:', error);
            }
        });
    }
    
    getDebugInfo() {
        try {
            if (typeof Android !== 'undefined' && Android.getDebugInfo) {
                return Android.getDebugInfo();
            }
            return 'Android debug info not available';
        } catch (error) {
            return 'Error: ' + error.message;
        }
    }
    
    getStats() {
        return {
            ...this.stats,
            uptime: Date.now() - this.stats.startTime,
            hasData: this.lastData !== null
        };
    }
}

// Export singleton
window.batteryManager = new BatteryManager();