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
    // BUBBLE SPAWNING (DISABLED - handled by index.html)
    // ===================================
    
    // All bubble spawning logic is now handled by the index.html system
    // to avoid conflicts and ensure single bubble container
    
    // ===================================
    // Initialization
    // ===================================
    
    function init() {
        console.log('[Main] Initializing...');
        
        window.uiManager.init();
        initTheme();
        initVisuals();
        
        window.batteryManager.start(1000);
        window.batteryManager.subscribe(onBatteryUpdate);
        
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
            
            const fallback = document.querySelector('.liquid-fallback');
            if (fallback) fallback.style.display = 'none';
            
        } catch (error) {
            console.error('[Main] WebGL init failed, using CSS fallback:', error);
            useWebGL = false;
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
        
        window.uiManager.update(data);
        
        if (useWebGL && fluidSim) {
            const color = window.uiManager.getBatteryColor(data.capacity);
            
            fluidSim.updateCapacity(data.capacity);
            fluidSim.updateColor(...color.normalized);
            fluidSim.setCharging(data.isCharging);
        } else {
            updateCSSFallback(data);
        }
        
        // BUBBLES handled by index.html system - call the index.html function
        if (typeof toggleBubbleSpawning === 'function') {
            toggleBubbleSpawning(data.isCharging);
        }
    }
    
    function updateCSSFallback(data) {
        const liquid = document.querySelector('.liquid-fallback');
        if (!liquid) return;
        
        const color = window.uiManager.getBatteryColor(data.capacity);
        
        liquid.style.height = data.capacity + '%';
        liquid.style.backgroundColor = color.hex;
        // NO BOX SHADOW (glow removed)
    }
    
    // ===================================
    // BUBBLE SPAWNING (DISABLED - handled by index.html)
    // ===================================
    
    // Bubble spawning is now handled entirely by the index.html system
    // to avoid conflicts and duplicate bubble containers
    
    // ===================================
    // Event Listeners
    // ===================================
    
    function setupEventListeners() {
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
        
        window.addEventListener('resize', () => {
            if (fluidSim) {
                fluidSim.resize();
            }
        });
        
        document.addEventListener('visibilitychange', () => {
            if (document.hidden) {
                console.log('[Main] App hidden');
            } else {
                console.log('[Main] App visible');
            }
        });
    }
    
    // ===================================
    // Global Functions
    // ===================================
    
    window.showDebug = () => window.uiManager.showDebug();
    window.hideDebug = () => window.uiManager.hideDebug();
    window.copyDebug = () => window.uiManager.copyDebug();
    
    window.applyTheme = (theme) => {
        window.uiManager.applyTheme(theme);
        const lastData = window.batteryManager.lastData;
        if (lastData) {
            onBatteryUpdate(lastData);
        }
    };
    
    // ===================================
    // Start App
    // ===================================
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }
    
})();
