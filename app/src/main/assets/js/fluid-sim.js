/**
 * fluid-sim.js
 * WebGL Fluid Simulation using advanced shaders
 */

class FluidSimulation {
    constructor(canvas) {
        this.canvas = canvas;
        this.gl = null;
        this.program = null;
        this.uniforms = {};
        this.isInitialized = false;
        this.animationId = null;
        
        // State
        this.capacity = 0.5;
        this.color = [0.816, 0.737, 1.0]; // Default: #D0BCFF
        this.isCharging = 0;
        this.startTime = Date.now();
        
        // Bubble System
        this.bubbles = [];
        this.maxBubbles = 100;
        
        this.init();
    }
    
    init() {
        try {
            // Get WebGL context
            this.gl = this.canvas.getContext('webgl') || this.canvas.getContext('experimental-webgl');
            
            if (!this.gl) {
                console.error('[FluidSim] WebGL not supported');
                return false;
            }
            
            console.log('[FluidSim] WebGL context created');
            
            // Set canvas size
            this.resize();
            
            // Load shaders
            this.loadShaders();
            
            // Setup geometry (fullscreen quad)
            this.setupGeometry();
            
            this.isInitialized = true;
            console.log('[FluidSim] ✓ Initialized');
            
            // Start rendering
            this.startRendering();
            
            return true;
            
        } catch (error) {
            console.error('[FluidSim] Init failed:', error);
            return false;
        }
    }
    
    async loadShaders() {
        const gl = this.gl;
        
        // Load shader source from files
        const vertexSource = await this.loadShaderFile('shaders/fluid.vert');
        const fragmentSource = await this.loadShaderFile('shaders/fluid.frag');
        
        // Compile shaders
        const vertexShader = this.compileShader(gl.VERTEX_SHADER, vertexSource);
        const fragmentShader = this.compileShader(gl.FRAGMENT_SHADER, fragmentSource);
        
        // Link program
        this.program = gl.createProgram();
        gl.attachShader(this.program, vertexShader);
        gl.attachShader(this.program, fragmentShader);
        gl.linkProgram(this.program);
        
        if (!gl.getProgramParameter(this.program, gl.LINK_STATUS)) {
            throw new Error('Program link failed: ' + gl.getProgramInfoLog(this.program));
        }
        
        gl.useProgram(this.program);
        
        // Get uniform locations
        this.uniforms = {
            time: gl.getUniformLocation(this.program, 'u_time'),
            capacity: gl.getUniformLocation(this.program, 'u_capacity'),
            color: gl.getUniformLocation(this.program, 'u_color'),
            resolution: gl.getUniformLocation(this.program, 'u_resolution'),
            isCharging: gl.getUniformLocation(this.program, 'u_isCharging'),
            // Note: You will need to add a u_bubbles uniform in your shader file
            // bubbles: gl.getUniformLocation(this.program, 'u_bubbles') 
        };
        
        console.log('[FluidSim] Shaders compiled and linked');
    }
    
    async loadShaderFile(path) {
        try {
            const response = await fetch(path);
            if (!response.ok) throw new Error('Failed to load ' + path);
            return await response.text();
        } catch (error) {
            console.error('[FluidSim] Shader load failed:', error);
            throw error;
        }
    }
    
    compileShader(type, source) {
        const gl = this.gl;
        const shader = gl.createShader(type);
        gl.shaderSource(shader, source);
        gl.compileShader(shader);
        
        if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
            const info = gl.getShaderInfoLog(shader);
            gl.deleteShader(shader);
            throw new Error('Shader compile failed: ' + info);
        }
        
        return shader;
    }
    
    setupGeometry() {
        const gl = this.gl;
        
        // Fullscreen quad vertices
        const vertices = new Float32Array([
            -1, -1,
             1, -1,
            -1,  1,
             1,  1
        ]);
        
        const buffer = gl.createBuffer();
        gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
        gl.bufferData(gl.ARRAY_BUFFER, vertices, gl.STATIC_DRAW);
        
        const positionLoc = gl.getAttribLocation(this.program, 'a_position');
        gl.enableVertexAttribArray(positionLoc);
        gl.vertexAttribPointer(positionLoc, 2, gl.FLOAT, false, 0, 0);
    }
    
    resize() {
        const dpr = window.devicePixelRatio || 1;
        const rect = this.canvas.getBoundingClientRect();
        
        this.canvas.width = rect.width * dpr;
        this.canvas.height = rect.height * dpr;
        
        if (this.gl) {
            this.gl.viewport(0, 0, this.canvas.width, this.canvas.height);
        }
    }
    
    startRendering() {
        const render = () => {
            this.render();
            this.animationId = requestAnimationFrame(render);
        };
        render();
    }
    
    updateBubbles() {
        // Spawn new bubbles if charging
        if (this.isCharging > 0) {
            if (this.bubbles.length < this.maxBubbles) {
                this.bubbles.push(new Bubble());
            }
        }

        // Update existing bubbles
        for (let i = this.bubbles.length - 1; i >= 0; i--) {
            let b = this.bubbles[i];
            b.update();
            
            // Remove bubbles that go off the top of the screen
            if (b.y < -1.0) {
                this.bubbles.splice(i, 1);
            }
        }
    }
    
    render() {
        if (!this.isInitialized) return;
        
        const gl = this.gl;
        const time = (Date.now() - this.startTime) * 0.001; // Convert to seconds
        
        // Clear
        gl.clearColor(0, 0, 0, 0);
        gl.clear(gl.COLOR_BUFFER_BIT);
        
        // Enable blending for transparency
        gl.enable(gl.BLEND);
        gl.blendFunc(gl.SRC_ALPHA, gl.ONE_MINUS_SRC_ALPHA);
        
        // Update bubble physics
        this.updateBubbles();
        
        // Set uniforms
        gl.uniform1f(this.uniforms.time, time);
        gl.uniform1f(this.uniforms.capacity, this.capacity);
        gl.uniform3fv(this.uniforms.color, this.color);
        gl.uniform2f(this.uniforms.resolution, this.canvas.width, this.canvas.height);
        gl.uniform1i(this.uniforms.isCharging, this.isCharging);
        
        // Note: You would pass bubble data here if your shader supports it
        // gl.uniform4fv(this.uniforms.bubbles, this.flattenBubbles());
        
        // Draw fullscreen quad
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    }
    
    // Helper to flatten bubble array for shader (if needed)
    flattenBubbles() {
        // Placeholder for shader data passing
        return new Float32Array(0);
    }
    
    // Public API
    updateCapacity(percentage) {
        this.capacity = Math.max(0, Math.min(1, percentage / 100));
    }
    
    updateColor(r, g, b) {
        this.color = [r, g, b];
    }
    
    setCharging(charging) {
        this.isCharging = charging ? 1 : 0;
    }
    
    destroy() {
        if (this.animationId) {
            cancelAnimationFrame(this.animationId);
        }
        if (this.gl) {
            const ext = this.gl.getExtension('WEBGL_lose_context');
            if (ext) ext.loseContext();
        }
        console.log('[FluidSim] Destroyed');
    }
}

// Bubble Class
class Bubble {
    constructor() {
        // Random X position within screen bounds (-0.8 to 0.8 to avoid edges)
        this.x = (Math.random() * 1.6) - 0.8;
        // Start at the bottom (y = 1.0)
        this.y = 1.0;
        // Random speed
        this.speed = 0.002 + Math.random() * 0.004;
        // Random size
        this.size = 0.01 + Math.random() * 0.02;
        // Wobble offset
        this.wobble = Math.random() * Math.PI * 2;
    }
    
    update() {
        // Move up
        this.y -= this.speed;
        // Add wobble effect
        this.wobble += 0.05;
        this.x += Math.sin(this.wobble) * 0.002;
    }
}

// Export for use in other modules
window.FluidSimulation = FluidSimulation;
