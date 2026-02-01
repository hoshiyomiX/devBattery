/**
 * ui.js
 * UI update and color management
 */

class UIManager {
    constructor() {
        this.elements = {};
        this.currentTheme = 'dark';
    }
    
    init() {
        // Cache DOM elements
        this.elements = {
            capacityValue: document.getElementById('capacity-value'),
            statusLabel: document.getElementById('status-label'),
            voltageValue: document.getElementById('voltage-value'),
            currentValue: document.getElementById('current-value'),
            powerValue: document.getElementById('power-value'),
            tempValue: document.getElementById('temp-value'),
            debugCard: document.getElementById('debug-card'),
            debugOutput: document.getElementById('debug-output')
        };
        
        console.log('[UI] Initialized');
    }
    
    update(data) {
        if (data.error) {
            console.error('[UI] Error:', data.error);
            return;
        }
        
        // Update text values with animation
        this.animateValue('capacityValue', data.capacity);
        this.setText('statusLabel', data.status);
        this.animateValue('voltageValue', data.voltageV.toFixed(2));
        this.animateValue('currentValue', data.currentMA);
        this.animateValue('powerValue', data.powerW.toFixed(2));
        this.animateValue('tempValue', data.temperatureC.toFixed(1));
    }
    
    animateValue(elementKey, value) {
        const el = this.elements[elementKey];
        if (!el) return;
        
        // Simple fade if value changes
        const currentValue = el.textContent;
        if (currentValue !== value.toString()) {
            el.style.opacity = '0.5';
            setTimeout(() => {
                el.textContent = value;
                el.style.opacity = '1';
            }, 100);
        }
    }
    
    setText(elementKey, text) {
        const el = this.elements[elementKey];
        if (el) el.textContent = text;
    }
    
    getBatteryColor(percentage) {
        const isLight = this.currentTheme === 'light';
        
        // RGB values for different states
        const colors = {
            critical: isLight ? [179, 38, 30] : [242, 184, 181],
            low: isLight ? [230, 81, 0] : [239, 176, 65],
            medium: isLight ? [103, 80, 164] : [208, 188, 255],
            high: isLight ? [46, 125, 50] : [140, 214, 163]
        };
        
        let color;
        if (percentage <= 15) color = colors.critical;
        else if (percentage <= 30) color = colors.low;
        else if (percentage <= 80) color = colors.medium;
        else color = colors.high;
        
        return {
            rgb: color,
            hex: this.rgbToHex(color[0], color[1], color[2]),
            normalized: color.map(c => c / 255) // For WebGL
        };
    }
    
    rgbToHex(r, g, b) {
        return '#' + [r, g, b].map(x => {
            const hex = x.toString(16);
            return hex.length === 1 ? '0' + hex : hex;
        }).join('');
    }
    
    applyTheme(theme) {
        this.currentTheme = theme;
        document.documentElement.setAttribute('data-theme', theme);
        console.log('[UI] Theme:', theme);
    }
    
    showDebug() {
        const card = this.elements.debugCard;
        const output = this.elements.debugOutput;
        
        if (!card || !output) return;
        
        // Get debug info from battery manager
        const debugInfo = window.batteryManager.getDebugInfo();
        output.textContent = debugInfo;
        card.classList.add('show');
    }
    
    hideDebug() {
        const card = this.elements.debugCard;
        if (card) card.classList.remove('show');
    }
    
    copyDebug() {
        const output = this.elements.debugOutput;
        if (!output) return;
        
        const text = output.textContent;
        
        // Create temporary textarea
        const textarea = document.createElement('textarea');
        textarea.value = text;
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        
        try {
            document.execCommand('copy');
            console.log('[UI] Copied to clipboard');
        } catch (error) {
            console.error('[UI] Failed to copy:', error);
        }
        
        document.body.removeChild(textarea);
    }
}

// Export singleton
window.uiManager = new UIManager();