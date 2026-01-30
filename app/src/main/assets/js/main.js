/**
 * main.js
 * Application initialization and coordination
 */

(function() {
    'use strict';
    
    console.log('[Main] DevBattery Monitor v3.0 - Renuked');
    console.log('[Main] WebGL Fluid Simulation Mode');
    
    let fluidSim = null;
    let useWebGL = true;
    
    // ===================================
    // Initialization
    // ===================================
    
    function init() {
        console.log('[Main] Initializing...');
        
        // Initialize UI manager
        window.uiManager.init();
        
        // Setup theme
        initTheme();
        
        // Setup WebGL or fallback
        initVisuals();
        
        // Start battery monitoring
        window.batteryManager.start(1000);
        
        // Subscribe to battery updates
        window.batteryManager.subscribe(onBatteryUpdate);
        
        // Setup event listeners
        setupEventListeners();
        
        console.log('[Main] ✓ Ready');
    }
    
    function initTheme() {
        try {
            if (typeof Android !== 'undefined' && Android.getSystemTheme) {
                const theme = Android.getSystemTheme();
                window.uiManager.applyTheme(theme);
            } else {
                window.uiManager.applyTheme('dark');
            }
        } catch (error) {
            console.error('[Main] Theme init failed:', error);
            window.uiManager.applyTheme('dark');
        }
    }
    
    function initVisuals() {
        const canvas = document.getElementById('fluid-canvas');
        
        if (!canvas) {
            console.error('[Main] Canvas not found, using CSS fallback');
            useWebGL = false;
            return;
        }
        
        try {
            fluidSim = new FluidSimulation(canvas);
            
            if (!fluidSim.isInitialized) {
                throw new Error('FluidSimulation failed to initialize');
            }
            
            useWebGL = true;
            console.log('[Main] ✓ WebGL mode active');
            
            // Hide CSS fallback
            const fallback = document.querySelector('.liquid-fallback');
            if (fallback) fallback.style.display = 'none';
            
        } catch (error) {
            console.error('[Main] WebGL init failed, using CSS fallback:', error);
            useWebGL = false;
            
            // Show CSS fallback
            canvas.style.display = 'none';
        }
    }
    
    // ===================================
    // Battery Data Handler
    // ===================================
    
    function onBatteryUpdate(data) {
        if (data.error) {
            console.error('[Main] Battery error:', data.error);
            return;
        }
        
        // Update UI
        window.uiManager.update(data);
        
        // Update WebGL if active
        if (useWebGL && fluidSim) {
            const color = window.uiManager.getBatteryColor(data.capacity);
            
            fluidSim.updateCapacity(data.capacity);
            fluidSim.updateColor(...color.normalized);
            fluidSim.setCharging(data.isCharging);
        } else {
            // Update CSS fallback
            updateCSSFallback(data);
        }
    }
    
    function updateCSSFallback(data) {
        const liquid = document.querySelector('.liquid-fallback');
        if (!liquid) return;
        
        const color = window.uiManager.getBatteryColor(data.capacity);
        
        liquid.style.height = data.capacity + '%';
        liquid.style.backgroundColor = color.hex;
        liquid.style.boxShadow = `0 0 20px ${color.hex}`;
        
        // Toggle charging animation
        if (data.isCharging) {
            liquid.classList.add('charging');
        } else {
            liquid.classList.remove('charging');
        }
    }
    
    // ===================================
    // Event Listeners
    // ===================================
    
    function setupEventListeners() {
        // Debug panel triggers
        const batteryVisual = document.querySelector('.battery-visual');
        const badge = document.getElementById('root-badge');
        
        if (batteryVisual) {
            batteryVisual.addEventListener('click', () => {
                window.uiManager.showDebug();
            });
        }
        
        if (badge) {
            badge.addEventListener('click', () => {
                window.uiManager.showDebug();
            });
        }
        
        // Window resize
        window.addEventListener('resize', () => {
            if (fluidSim) {
                fluidSim.resize();
            }
        });
        
        // Visibility change (pause when hidden)
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                console.log('[Main] App hidden, pausing updates');
                // Could reduce update frequency here
            } else {
                console.log('[Main] App visible, resuming');
            }
        });
    }
    
    // ===================================
    // Global Functions (called from HTML)
    // ===================================
    
    window.showDebug = () => window.uiManager.showDebug();
    window.hideDebug = () => window.uiManager.hideDebug();
    window.copyDebug = () => window.uiManager.copyDebug();
    
    // Theme change from Android
    window.applyTheme = (theme) => {
        window.uiManager.applyTheme(theme);
        
        // Re-update colors if battery data exists
        const lastData = window.batteryManager.lastData;
        if (lastData) {
            onBatteryUpdate(lastData);
        }
    };
    
    // ===================================
    // Start App
    // ===================================
    
    // Wait for DOM ready
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
    
})();