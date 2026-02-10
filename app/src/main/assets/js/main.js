/**
 * main.js
 * Consolidated application logic for DevBattery Monitor
 */

(function() {
    'use strict';
    
    // Global state variables
    let debugData = '';
    let hasRoot = false;
    let previousValues = {};
    let isCharging = false;
    let bubbleSpawnInterval = null;
    let currentMA = 0; // mA
    let bubbleCount = 0;
    let activeBubbles = new Set(); // Track bubble elements for proper cleanup
    let lastStatus = '';
    let currentCapacity = 0;
    let currentTheme = 'dark';
    let drainLeakTimeout = null;
    let activeDrainLeaks = 0;
    const MAX_DRAIN_LEAKS = 3;
    const MIN_INTERVAL = 5000;
    const MAX_INTERVAL = 10000;
    const DROPLET_DURATION = 1100;
    const SPLASH_TIMING = 1045;
    const LANDING_OFFSET = 15;
    const containerHeight = 200;
    const MAX_BUBBLES = 12; // Fixed limit for smooth performance
    
    // Manual bubble simulation toggle state
    let isBubbleSimulationEnabled = false;
    
    // Theme management
    function applyTheme(theme) {
        currentTheme = theme;
        const root = document.documentElement;
        
        if (theme === 'light') {
            root.style.setProperty('--md-sys-color-primary', '#6750A4');
            root.style.setProperty('--md-sys-color-surface', '#FFFBFE');
            root.style.setProperty('--md-sys-color-on-surface', '#1C1B1F');
            document.querySelector('meta[name="theme-color"]').setAttribute('content', '#FFFBFE');
        } else {
            root.style.setProperty('--md-sys-color-primary', '#D0BCFF');
            root.style.setProperty('--md-sys-color-surface', '#1C1B1F');
            root.style.setProperty('--md-sys-color-on-surface', '#E6E0E9');
            document.querySelector('meta[name="theme-color"]').setAttribute('content', '#1C1B1F');
        }
    }
    
    // Utility functions
    function formatPower(value) {
        const num = parseFloat(value);
        if (isNaN(num)) return value;
        return Math.min(99.9, num).toFixed(2).replace(/\.00$/, '');
    }
    
    function formatNumber(value, decimals = 1) {
        const num = parseFloat(value);
        if (isNaN(num)) return value;
        return Math.min(99.9, num).toFixed(decimals).replace(/\.0$/, '');
    }
    
    function calculateLandingPosition() {
        const tile = document.getElementById('tile-capacity');
        const rect = tile.getBoundingClientRect();
        const viewportHeight = window.innerHeight;
        const landingY = viewportHeight - LANDING_OFFSET;
        const spawnY = rect.bottom - 10; // Adjust spawn position
        const fallDistance = Math.max(50, landingY - spawnY); // Ensure minimum fall distance
        
        return {
            spawnX: rect.right - 20,
            spawnY: spawnY,
            landingY: landingY,
            landingX: rect.right - 20,
            fallDistance: fallDistance
        };
    }
    
    // Bubble management
    function computeBubbleSpawn(current_now_ma, currently) {
        if (isBubbleSimulationEnabled) {
            const spawnCount = Math.random() < 0.7 ? 1 : (Math.random() < 0.9 ? 2 : 0);
            const availableSlots = MAX_BUBBLES - currently;
            return Math.min(spawnCount, availableSlots);
        }
        
        if (current_now_ma <= 0) {
            return 0;
        }
        
        const baseSpawnCount = Math.floor(current_now_ma / 800);
        const spawnCount = baseSpawnCount > 0 ? (baseSpawnCount + (Math.random() < 0.3 ? 1 : 0)) : 0;
        
        const availableSlots = MAX_BUBBLES - currently;
        return Math.min(spawnCount, availableSlots);
    }

    function spawnRandomBubble() {
        const container = document.getElementById('liquid-container');
        const liquid = document.getElementById('liquid');
        const toSpawn = Math.max(0, computeBubbleSpawn(currentMA, bubbleCount));
        
        if (bubbleCount >= MAX_BUBBLES) return;
        
        const containerHeight = container.offsetHeight;
        const containerWidth = container.offsetWidth;
        const liquidHeightPx = liquid.offsetHeight;
        if (liquidHeightPx <= 0) return;
        
        const riseDistance = liquidHeightPx - 2; // Bubbles fade 2px below liquid surface
        
        for (let i = 0; i < toSpawn; i++) {
            if (bubbleCount >= MAX_BUBBLES) break;
            
            const bubble = document.createElement('div');
            bubble.className = 'bubble';
            
            // 3 distinct bubble sizes with pronounced differences
            const sizeCategory = Math.random();
            let size, speedMultiplier, curveMultiplier;
            
            if (sizeCategory < 0.33) {
                // Small bubbles (33% chance)
                size = 4 + Math.random() * 2; // 4-6px
                speedMultiplier = 0.5; // Rise 50% faster
                curveMultiplier = 0.4; // Minimal curve
            } else if (sizeCategory < 0.67) {
                // Medium bubbles (33% chance)
                size = 7 + Math.random() * 3; // 7-9px
                speedMultiplier = 0.8; // Rise 20% faster
                curveMultiplier = 0.8; // Moderate curve
            } else {
                // Large bubbles (34% chance)
                size = 10 + Math.random() * 4; // 10-14px
                speedMultiplier = 1.3; // Rise 30% slower
                curveMultiplier = 1.8; // Maximum curve
            }
            
            const left = Math.random() * 80 + 10; // Better distribution across container
            
            const heightRatio = liquidHeightPx / containerHeight;
            // Rise duration optimized for performance
            const baseDuration = 2.2; // Reduced base duration
            const duration = baseDuration * speedMultiplier * (0.8 + Math.random() * 0.1);
            const finalDuration = Math.max(1.0, Math.min(3.5, duration));
            
            // Simplified curve calculation for better performance
            const baseCurve = 6; // Reduced curve amplitude
            const curveVariation = 0.5 + Math.random() * 0.5;
            const curveAmplitude = baseCurve * curveMultiplier * curveVariation;
            
            bubble.style.width = size + 'px';
            bubble.style.height = size + 'px';
            bubble.style.left = left + '%';
            bubble.style.bottom = '0';
            bubble.style.setProperty('--rise-distance', -riseDistance + 'px');
            bubble.style.setProperty('--curve-amplitude', curveAmplitude + 'px');
            bubble.style.animationDuration = finalDuration + 's';
            const animationDelay = Math.random() * 1.2 + 0.2; // Random delay 0.2-1.4s for better spacing
            bubble.style.animationDelay = animationDelay + 's';
            
            // Ensure bubble has proper initial state
            bubble.style.opacity = '0';
            bubble.style.willChange = 'transform, opacity';
            
            container.appendChild(bubble);
            bubbleCount++;
            activeBubbles.add(bubble);
            
            // Start the animation after a brief delay to ensure proper initialization
            requestAnimationFrame(() => {
                bubble.style.opacity = '';
            });
            
            // Precise bubble cleanup - match animation duration exactly to prevent premature disappearing
            const totalAnimationTime = (finalDuration + animationDelay) * 1000;
            setTimeout(() => {
                if (bubble && bubble.parentNode === container) {
                    container.removeChild(bubble);
                    bubbleCount = Math.max(0, bubbleCount - 1);
                    activeBubbles.delete(bubble);
                }
            }, totalAnimationTime + 100); // Add 100ms buffer to ensure animation completes
        }
    }
        
    function toggleBubbleSpawning(active) {
        if (active && !bubbleSpawnInterval) {
            bubbleSpawnInterval = setInterval(() => {
                spawnRandomBubble();
            }, 600); // Consistent interval for natural timing
        } else if (!active && bubbleSpawnInterval) {
            clearInterval(bubbleSpawnInterval);
            bubbleSpawnInterval = null;
            // Clean up all active bubbles immediately
            activeBubbles.forEach(bubble => {
                if (bubble.parentNode) {
                    bubble.parentNode.removeChild(bubble);
                }
            });
            activeBubbles.clear();
            document.querySelectorAll('.bubble').forEach(b => b.remove());
            bubbleCount = 0;
        }
    }
    
    // Drain effects
    function spawnSplash(landingY, landingX) {
        const splashColor = getBatteryColor(currentCapacity);
        const particleCount = 6 + Math.floor(Math.random() * 4);
        
        for (let i = 0; i < particleCount; i++) {
            const particle = document.createElement('div');
            particle.className = 'splash-particle';
            particle.style.top = landingY + 'px';
            particle.style.left = landingX + 'px';
            
            const direction = (Math.random() - 0.5) * 2;
            const distance = (20 + Math.random() * 25) * Math.sign(direction || 1);
            
            particle.style.setProperty('--splash-x', distance + 'px');
            particle.style.setProperty('--splash-color', splashColor);
            particle.style.animationDelay = (Math.random() * 0.05) + 's';
            
            document.body.appendChild(particle);
            setTimeout(() => {
                if (particle.parentNode) {
                    particle.parentNode.removeChild(particle);
                }
            }, 500);
        }
    }
    
    function spawnDrainDrip() {
        if (activeDrainLeaks >= MAX_DRAIN_LEAKS) return;
        
        const overlay = document.getElementById('drain-leak-overlay');
        const drip = document.createElement('div');
        drip.className = 'drain-drip';
        
        const pos = calculateLandingPosition();
        
        // Set positioning and animation properties
        drip.style.top = pos.spawnY + 'px';
        drip.style.left = pos.spawnX + 'px';
        drip.style.setProperty('--fall-distance', pos.fallDistance + 'px');
        drip.style.setProperty('--drain-color', getBatteryColor(currentCapacity));
        
        // Ensure the droplet is visible and properly positioned
        drip.style.opacity = '0.9';
        drip.style.position = 'fixed';
        drip.style.zIndex = '999';
        
        // Debug: ensure fall distance is valid
        if (pos.fallDistance <= 0) {
            console.warn('Invalid fall distance calculated:', pos.fallDistance);
            return;
        }
        
        overlay.appendChild(drip);
        activeDrainLeaks++;
        
        // Set will-change before animation for GPU optimization
        drip.style.willChange = 'transform, opacity';
        
        // Start animation with linear timing for smooth constant-speed fall
        drip.style.animation = 'dropletFall 0.5s cubic-bezier(0.4, 0, 1, 1) forwards';

        // Create splash effect at 84% of animation (synced with simplified keyframes)
        const splashTimeout = setTimeout(() => {
            spawnSplash(pos.landingY, pos.landingX);
        }, 420);
        
        // Clean up droplet using animationend event for precise timing
        const cleanupDrip = () => {
            clearTimeout(splashTimeout);
            if (drip && drip.parentNode === overlay) {
                overlay.removeChild(drip);
                activeDrainLeaks--;
            }
        };
        
        drip.addEventListener('animationend', cleanupDrip, { once: true });
        drip.addEventListener('animationcancel', cleanupDrip, { once: true });
    }
    
    function scheduleNextDrip() {
        if (!drainLeakTimeout) return;
        
        const nextInterval = Math.floor(Math.random() * (MAX_INTERVAL - MIN_INTERVAL + 1)) + MIN_INTERVAL;
        drainLeakTimeout = setTimeout(() => {
            spawnDrainDrip();
            scheduleNextDrip();
        }, nextInterval);
    }
    
    function toggleDrainLeak(active) {
        if (active && !drainLeakTimeout) {
            drainLeakTimeout = true;
            setTimeout(() => {
                if (!drainLeakTimeout) return;
                spawnDrainDrip();
                scheduleNextDrip();
            }, 3000);
        } else if (!active && drainLeakTimeout) {
            clearTimeout(drainLeakTimeout);
            drainLeakTimeout = null;
            document.querySelectorAll('.drain-drip, .splash-particle').forEach(el => el.remove());
            activeDrainLeaks = 0;
        }
    }
    
    // Color utilities
    function getBatteryColor(percentage) {
        const stages = [
            { percent: 0, color: [211, 47, 47] },
            { percent: 10, color: [211, 47, 47] },
            { percent: 10, color: [244, 67, 54] },
            { percent: 20, color: [244, 67, 54] },
            { percent: 20, color: [255, 87, 34] },
            { percent: 30, color: [255, 87, 34] },
            { percent: 30, color: [255, 152, 0] },
            { percent: 40, color: [255, 152, 0] },
            { percent: 40, color: [255, 193, 7] },
            { percent: 50, color: [255, 193, 7] },
            { percent: 50, color: [255, 235, 59] },
            { percent: 60, color: [255, 235, 59] },
            { percent: 60, color: [205, 220, 57] },
            { percent: 70, color: [205, 220, 57] },
            { percent: 70, color: [139, 195, 74] },
            { percent: 80, color: [139, 195, 74] },
            { percent: 80, color: [102, 187, 106] },
            { percent: 90, color: [102, 187, 106] },
            { percent: 90, color: [76, 175, 80] },
            { percent: 100, color: [76, 175, 80] }
        ];
        
        let lowerStage = stages[0];
        let upperStage = stages[stages.length - 1];
        
        for (let i = 0; i < stages.length - 1; i++) {
            if (percentage >= stages[i].percent && percentage <= stages[i + 1].percent) {
                lowerStage = stages[i];
                upperStage = stages[i + 1];
                break;
            }
        }
        
        const range = upperStage.percent - lowerStage.percent;
        let ratio = 0;
        
        if (range > 0) {
            ratio = (percentage - lowerStage.percent) / range;
        }
        
        const r = Math.floor(lowerStage.color[0] + (upperStage.color[0] - lowerStage.color[0]) * ratio);
        const g = Math.floor(lowerStage.color[1] + (upperStage.color[1] - lowerStage.color[1]) * ratio);
        const b = Math.floor(lowerStage.color[2] + (upperStage.color[2] - lowerStage.color[2]) * ratio);
        
        return `rgba(${r}, ${g}, ${b}, 0.9)`;
    }
    
    // Liquid update
    function updateLiquid(percentage) {
        const liquid = document.getElementById('liquid');
        currentCapacity = percentage;
        liquid.style.height = percentage + '%';
        
        const color = getBatteryColor(percentage);
        liquid.style.background = `linear-gradient(180deg, 
            ${getBatteryColor(Math.min(100, percentage + 8))} 0%, 
            ${getBatteryColor(Math.min(100, percentage + 4))} 50%, 
            ${color} 100%)`;
    }
    
    // Value animation
    function animateValue(elementId, newValue) {
        const element = document.getElementById(elementId);
        if (previousValues[elementId] !== newValue) {
            element.classList.add('updating');
            setTimeout(() => element.classList.remove('updating'), 400);
        }
        element.textContent = newValue;
        previousValues[elementId] = newValue;
    }
    
    // Battery data update
    function updateBattery() {
        try {
            const data = JSON.parse(Android.getBatteryData());
            if (data.error) {
                return;
            }
            
            const capacity = parseInt(data.capacity) || 0;
            const status = data.status || lastStatus || "Unknown";
            
            // VOLTAGE: Backend now sends mV (already converted from sysfs uV, or fallback 1000mV)
            // Just divide by 1000 to get volts for display
            const voltageRaw = parseInt(data.voltage) || 1000; // Fallback to 1V if missing
            const voltage = formatNumber(voltageRaw / 1000, 2);
            
            const current = Math.floor((parseInt(data.current_now) || 0) / 1000);
            // Update global current MA for spawn logic
            currentMA = current;
            const temperature = formatNumber((parseInt(data.temp) || 0) / 10, 1);
            
            // Power calc: backend voltage (mV) * current (uA) / 1,000,000 = milliwatts, then /1000 = watts
            const power = formatPower(Math.abs((voltageRaw * (parseInt(data.current_now) || 0)) / 1000000000));
            
            if (status !== "Unknown") lastStatus = status;
            
            animateValue('capacity-value', capacity);
            document.getElementById('status-label').textContent = lastStatus;
            animateValue('voltage-value', voltage);
            animateValue('current-value', current);
            animateValue('power-value', power);
            animateValue('temp-value', temperature);
            
            updateLiquid(capacity);
            
            const chargingActive = lastStatus.toLowerCase() === 'charging';
            
            // Logic: If manual toggle is ON, use that. If OFF, use battery status.
            if (isBubbleSimulationEnabled) {
                toggleBubbleSpawning(true);
            } else {
                toggleBubbleSpawning(chargingActive);
            }
            
            // Update isCharging for other logic that depends on it
            isCharging = isBubbleSimulationEnabled || chargingActive;
            
            const isDraining = lastStatus.toLowerCase() === 'discharging' || lastStatus.toLowerCase() === 'not charging';
            toggleDrainLeak(isDraining);
        } catch (e) {
        }
    }
    
    // Debug functions
    function showDebug() {
        try {
            debugData = Android.getDebugInfo();
            document.getElementById('debug-output').textContent = debugData;
            document.getElementById('debug-card').classList.add('show');
        } catch (e) {
        }
    }
    
    function copyDebug() {
        if (!debugData) showDebug();
        
        const textarea = document.createElement('textarea');
        textarea.value = debugData;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        
        try {
            document.execCommand('copy');
        } catch (e) {
        }
        
        document.body.removeChild(textarea);
    }
    
    function toggleBubbleSimulation() {
        isBubbleSimulationEnabled = !isBubbleSimulationEnabled;
        const btnText = document.getElementById('bubble-btn-text');
        
        if (isBubbleSimulationEnabled) {
            btnText.textContent = "Bubbles: On";
            toggleBubbleSpawning(true);
        } else {
            btnText.textContent = "Bubbles: Off";
            toggleBubbleSpawning(false);
        }
    }
    
    // Make functions globally available
    window.showDebug = showDebug;
    window.copyDebug = copyDebug;
    window.toggleBubbleSimulation = toggleBubbleSimulation;
    window.applyTheme = applyTheme;
    
    // Initialize
    try {
        if (typeof Android !== 'undefined' && Android.getSystemTheme) {
            applyTheme(Android.getSystemTheme());
        } else {
            applyTheme('dark');
        }
    } catch (e) {
        applyTheme('dark');
    }
    
    setTimeout(() => {
        updateBattery();
    }, 300);
    
    setInterval(updateBattery, 1000);
    
})();
