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
            
            // Parse values with enhanced validation
            const batteryData = {
                capacity: this.validateCapacity(parseInt(data.capacity) || 0),
                status: data.status || 'Unknown',
                voltage: this.validateVoltage(parseInt(data.voltage) || 0),
                current: parseInt(data.current_now) || 0,
                temperature: this.validateTemperature(parseInt(data.temp) || 0),
                chargerVoltage: data.charger_voltage || '0',
                source: data.source || 'unknown',
                timestamp: Date.now(),
                intentAvailable: data.intent_available || false
            };
            
            // Calculate derived values
            batteryData.voltageV = batteryData.voltage / 1000;
            batteryData.currentMA = Math.floor(batteryData.current / 1000);
            batteryData.temperatureC = batteryData.temperature / 10;
            batteryData.powerW = Math.abs((batteryData.voltage * batteryData.current) / 1000000000);
            
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
                message: error.message,
                context: 'getBatteryData()'
            });
            
            // Enhanced error handling with fallback
            if (error.message.includes('Android bridge not available')) {
                this.notifyListeners({ 
                    error: 'Android bridge not available',
                    fallback: true 
                });
            } else if (error.message.includes('Battery Intent unavailable')) {
                console.log('[Battery] Battery intent unavailable, trying alternative methods...');
                this.notifyListeners({ 
                    error: 'Battery Intent unavailable - using fallback methods',
                    fallback: true 
                });
            } else {
                this.notifyListeners({ error: error.message });
            }
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
    
    validateCapacity(capacity) {
        if (capacity < 0 || capacity > 100) {
            console.warn(`[Battery] Invalid capacity ${capacity}, using fallback`);
            return 50; // Safe fallback
        }
        return capacity;
    }
    
    validateVoltage(voltage) {
        if (voltage < 2000 || voltage > 5000) {
            console.warn(`[Battery] Invalid voltage ${voltage}mV, using fallback`);
            return 3700; // Typical Li-ion voltage
        }
        return voltage;
    }
    
    validateTemperature(temp) {
        if (temp < 100 || temp > 600) {
            console.warn(`[Battery] Invalid temperature ${temp} (×0.1°C), using fallback`);
            return 250; // 25°C fallback
        }
        return temp;
    }
}

// Export singleton
window.batteryManager = new BatteryManager();