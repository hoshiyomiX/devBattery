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
            isCharging: gl.getUniformLocation(this.program, 'u_isCharging')
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
        
        // Set uniforms
        gl.uniform1f(this.uniforms.time, time);
        gl.uniform1f(this.uniforms.capacity, this.capacity);
        gl.uniform3fv(this.uniforms.color, this.color);
        gl.uniform2f(this.uniforms.resolution, this.canvas.width, this.canvas.height);
        gl.uniform1i(this.uniforms.isCharging, this.isCharging);
        
        // Draw fullscreen quad
        gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
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

// Export for use in other modules
window.FluidSimulation = FluidSimulation;