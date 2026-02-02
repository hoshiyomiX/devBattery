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
    let bubbleSpawnInterval = null;

    /**
     * Compute how many bubbles to spawn based on current_now (mA)
     * and how many bubbles are currently on screen (currently).
     * Rules:
     * - 1-1000 mA: spawnCount = max(0, currently - current_now)
     * - 1000-2000 mA: spawnCount = currently
     * - >2000 mA: spawnCount = currently + current_now
     */
    function computeBubbleSpawn(current_now_ma, currently) {
        if (!current_now_ma || current_now_ma <= 0) return 0;
        if (current_now_ma <= 1000) {
            return Math.max(0, currently - current_now_ma);
        }
        if (current_now_ma <= 2000) {
            return currently;
        }
        // current_now_ma > 2000
        return currently + current_now_ma;
    }

    // Helper to spawn a single bubble with existing visuals
    function spawnBubble(container) {
        if (container.children.length > 20) return;  // cap
        const bubble = document.createElement('div');
        bubble.className = 'bubble';
        const size = Math.random() * 12 + 6;  // 6-18px
        const left = Math.random() * 80 + 10;
        const duration = Math.random() * 2 + 2.5; // 2.5-4.5s
        bubble.style.width = size + 'px';
        bubble.style.height = size + 'px';
        bubble.style.left = left + '%';
        bubble.style.animationDuration = duration + 's';
        container.appendChild(bubble);
        setTimeout(() => {
            if (bubble.parentNode) bubble.parentNode.removeChild(bubble);
        }, duration * 1000);
    }
    
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
        
        // RESTORE BUBBLES for charging animation
        toggleBubbleSpawning(data.isCharging);
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
    // BUBBLE SPAWNING (RESTORED)
    // ===================================
    
    function toggleBubbleSpawning(enable) {
        // Spawn bubbles inside the liquid area to keep animation within the bar
        // Previously this referenced a separate #bubbles container which caused
        // bubbles to render above other UI elements. Now we render inside the
        // liquid-container so the animation stays visually inside the bar.
        const container = document.getElementById('liquid-container');
        if (!container) return;
        
        if (enable) {
            if (!bubbleSpawnInterval) {
                bubbleSpawnInterval = setInterval(() => {
                    if (document.visibilityState === 'visible') {
                        spawnRandomBubble(container);
                    }
                }, 600);  // Faster spawn rate
            }
        } else {
            if (bubbleSpawnInterval) {
                clearInterval(bubbleSpawnInterval);
                bubbleSpawnInterval = null;
            }
            container.innerHTML = '';
        }
    }
    
    function spawnRandomBubble(container) {
        // Spawn multiple bubbles based on current and on-screen count
        if (container.children.length > 20) return;  // cap
        const currentMA = (window.batteryManager && window.batteryManager.lastData && window.batteryManager.lastData.currentMA) ? window.batteryManager.lastData.currentMA : 0;
        const toSpawn = Math.max(0, computeBubbleSpawn(currentMA, container.children.length));
        for (let i = 0; i < toSpawn; i++) {
            spawnBubble(container);
        }
    }
    
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
