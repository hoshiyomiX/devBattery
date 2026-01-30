// Fragment Shader - fluid.frag
// Advanced fluid simulation with metaballs and perlin noise

precision highp float;

varying vec2 v_uv;

// Uniforms from JavaScript
uniform float u_time;
uniform float u_capacity;        // Battery percentage 0.0-1.0
uniform vec3 u_color;            // Dynamic battery color
uniform vec2 u_resolution;
uniform int u_isCharging;        // 1 = charging, 0 = discharging

// ===================================
// Noise Functions
// ===================================

// Hash function for pseudo-random
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// 2D Perlin-like noise
float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    
    // Smooth interpolation
    vec2 u = f * f * (3.0 - 2.0 * f);
    
    // Four corners
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    
    // Mix
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Fractal Brownian Motion (layered noise)
float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    
    for (int i = 0; i < 4; i++) {
        value += amplitude * noise(p * frequency);
        frequency *= 2.0;
        amplitude *= 0.5;
    }
    
    return value;
}

// ===================================
// Metaball Functions
// ===================================

float metaball(vec2 p, vec2 center, float radius) {
    float dist = length(p - center);
    return radius / (dist * dist + 0.001); // Avoid division by zero
}

// ===================================
// Main Shader
// ===================================

void main() {
    vec2 uv = v_uv;
    vec2 aspectUV = uv;
    aspectUV.x *= u_resolution.x / u_resolution.y;
    
    // ===================================
    // Wave Generation
    // ===================================
    
    // Base liquid level
    float liquidLevel = u_capacity;
    
    // Multiple sine waves for realistic surface
    float wave1 = sin(uv.x * 10.0 + u_time * 2.0) * 0.015;
    float wave2 = sin(uv.x * 15.0 - u_time * 1.5) * 0.01;
    float wave3 = sin(uv.x * 8.0 + u_time * 2.5) * 0.008;
    
    // Perlin noise for organic turbulence
    float turbulence = fbm(vec2(uv.x * 5.0, u_time * 0.3)) * 0.02;
    
    // Charging adds more vigorous waves
    float chargingBoost = u_isCharging == 1 ? 1.5 : 1.0;
    float waveHeight = liquidLevel + (wave1 + wave2 + wave3 + turbulence) * chargingBoost;
    
    // ===================================
    // Metaballs for Organic Shape
    // ===================================
    
    float metaballField = 0.0;
    
    // Main liquid body
    vec2 center = vec2(0.5, waveHeight - 0.3);
    metaballField += metaball(uv, center, 0.2);
    
    // Additional metaballs for organic movement (charging only)
    if (u_isCharging == 1) {
        vec2 bubble1 = vec2(
            0.3 + sin(u_time * 1.2) * 0.1,
            waveHeight - 0.4 + cos(u_time * 1.5) * 0.05
        );
        metaballField += metaball(uv, bubble1, 0.05);
        
        vec2 bubble2 = vec2(
            0.7 + cos(u_time * 1.5) * 0.1,
            waveHeight - 0.35 + sin(u_time * 1.8) * 0.05
        );
        metaballField += metaball(uv, bubble2, 0.04);
    }
    
    // ===================================
    // Liquid Fill Shape
    // ===================================
    
    // Simple threshold for liquid presence
    float liquidMask = step(uv.y, waveHeight);
    
    // Smooth the wave edge
    liquidMask = smoothstep(waveHeight - 0.02, waveHeight + 0.02, uv.y);
    liquidMask = 1.0 - liquidMask; // Invert
    
    // Combine with metaballs for top surface only
    if (uv.y > waveHeight - 0.1 && uv.y < waveHeight + 0.05) {
        liquidMask = max(liquidMask, smoothstep(0.4, 0.6, metaballField));
    }
    
    // ===================================
    // Color Gradient
    // ===================================
    
    // Vertical gradient from darker to lighter
    vec3 darkColor = u_color * 0.6;
    vec3 lightColor = u_color * 1.2;
    vec3 finalColor = mix(darkColor, lightColor, uv.y);
    
    // Add shimmer/specular highlight near top
    float specular = smoothstep(waveHeight - 0.05, waveHeight, uv.y);
    specular *= (sin(uv.x * 20.0 + u_time * 3.0) * 0.5 + 0.5);
    finalColor += vec3(1.0) * specular * 0.15;
    
    // ===================================
    // Refraction Effect
    // ===================================
    
    // Distort background based on liquid surface
    vec2 refractOffset = vec2(
        sin(uv.y * 15.0 + u_time * 2.0) * 0.003,
        0.0
    );
    
    // Apply refraction to color (subtle)
    finalColor += fbm(uv + refractOffset) * 0.05;
    
    // ===================================
    // Output
    // ===================================
    
    // Final alpha based on liquid mask
    float alpha = liquidMask;
    
    // Add glow for low battery
    if (u_capacity < 0.2) {
        float pulse = sin(u_time * 3.0) * 0.5 + 0.5;
        finalColor = mix(finalColor, vec3(1.0, 0.3, 0.3), pulse * 0.3);
    }
    
    gl_FragColor = vec4(finalColor, alpha);
}