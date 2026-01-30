// Fragment Shader - fluid.frag
// Advanced fluid simulation with MORE WAVES and NO GLOW

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

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

float noise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    
    float a = hash(i);
    float b = hash(i + vec2(1.0, 0.0));
    float c = hash(i + vec2(0.0, 1.0));
    float d = hash(i + vec2(1.0, 1.0));
    
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.5;
    float frequency = 1.0;
    
    for (int i = 0; i < 5; i++) {
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
    return radius / (dist * dist + 0.001);
}

// ===================================
// Main Shader
// ===================================

void main() {
    vec2 uv = v_uv;
    vec2 aspectUV = uv;
    aspectUV.x *= u_resolution.x / u_resolution.y;
    
    // ===================================
    // INCREASED WAVE GENERATION
    // ===================================
    
    float liquidLevel = u_capacity;
    
    // MUCH MORE WAVES (increased amplitude and count)
    float wave1 = sin(uv.x * 12.0 + u_time * 2.5) * 0.035;  // 2x amplitude
    float wave2 = sin(uv.x * 18.0 - u_time * 2.0) * 0.025;  // 2x amplitude
    float wave3 = sin(uv.x * 10.0 + u_time * 3.0) * 0.020;  // 2x amplitude
    float wave4 = sin(uv.x * 22.0 - u_time * 1.8) * 0.015;  // NEW wave
    float wave5 = cos(uv.x * 15.0 + u_time * 2.2) * 0.018;  // NEW wave
    
    // MORE turbulence
    float turbulence = fbm(vec2(uv.x * 8.0, u_time * 0.5)) * 0.04;  // 2x amplitude
    
    // Charging makes it EVEN MORE wavy
    float chargingBoost = u_isCharging == 1 ? 2.0 : 1.0;  // Increased from 1.5
    float waveHeight = liquidLevel + (wave1 + wave2 + wave3 + wave4 + wave5 + turbulence) * chargingBoost;
    
    // ===================================
    // Metaballs for Organic Shape
    // ===================================
    
    float metaballField = 0.0;
    
    vec2 center = vec2(0.5, waveHeight - 0.3);
    metaballField += metaball(uv, center, 0.2);
    
    if (u_isCharging == 1) {
        vec2 bubble1 = vec2(
            0.3 + sin(u_time * 1.5) * 0.12,
            waveHeight - 0.4 + cos(u_time * 1.8) * 0.06
        );
        metaballField += metaball(uv, bubble1, 0.06);
        
        vec2 bubble2 = vec2(
            0.7 + cos(u_time * 1.8) * 0.12,
            waveHeight - 0.35 + sin(u_time * 2.2) * 0.06
        );
        metaballField += metaball(uv, bubble2, 0.05);
    }
    
    // ===================================
    // Liquid Fill Shape
    // ===================================
    
    float liquidMask = step(uv.y, waveHeight);
    liquidMask = smoothstep(waveHeight - 0.03, waveHeight + 0.03, uv.y);
    liquidMask = 1.0 - liquidMask;
    
    if (uv.y > waveHeight - 0.1 && uv.y < waveHeight + 0.05) {
        liquidMask = max(liquidMask, smoothstep(0.4, 0.6, metaballField));
    }
    
    // ===================================
    // Color Gradient (NO GLOW)
    // ===================================
    
    vec3 darkColor = u_color * 0.6;
    vec3 lightColor = u_color * 1.2;
    vec3 finalColor = mix(darkColor, lightColor, uv.y);
    
    // Subtle shimmer (NOT glow)
    float specular = smoothstep(waveHeight - 0.05, waveHeight, uv.y);
    specular *= (sin(uv.x * 20.0 + u_time * 3.0) * 0.5 + 0.5);
    finalColor += vec3(1.0) * specular * 0.08;  // Reduced from 0.15
    
    // ===================================
    // Refraction Effect
    // ===================================
    
    vec2 refractOffset = vec2(
        sin(uv.y * 18.0 + u_time * 2.5) * 0.005,
        0.0
    );
    
    finalColor += fbm(uv + refractOffset) * 0.03;
    
    // ===================================
    // Output (NO low battery glow)
    // ===================================
    
    float alpha = liquidMask;
    
    gl_FragColor = vec4(finalColor, alpha);
}