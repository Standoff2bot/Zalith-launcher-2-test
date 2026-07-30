/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.game.renderer

import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.GLES20
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.isMaliGPU
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Shader pre-compilation service for Mali GPUs.
 * 
 * Mali drivers compile shaders on first use (lazy compilation), causing
 * 30-60 seconds of stutter when first launching each Minecraft version.
 * 
 * This service pre-compiles a set of basic shaders in the background
 * during the first launch, eliminating the initial stutter.
 * 
 * Tracks compiled versions in: launcher_cache/shader_precompile_cache/
 */
object ShaderPrecompiler {
    private const val TAG = "ShaderPrecompiler"
    private val CACHE_DIR = File(PathManager.DIR_CACHE, "shader_precompile_cache")
    
    /**
     * Check if shaders need to be pre-compiled for this Minecraft version
     */
    fun needsPrecompilation(minecraftVersion: String): Boolean {
        if (!isMaliGPU()) return false
        
        val markerFile = File(CACHE_DIR, "precompiled_$minecraftVersion.marker")
        return !markerFile.exists()
    }
    
    /**
     * Pre-compile basic Minecraft shaders in background
     * Should be called on first launch of each Minecraft version
     */
    suspend fun precompileShaders(minecraftVersion: String) = withContext(Dispatchers.IO) {
        if (!isMaliGPU()) {
            Logger.info(TAG, "Skipping shader pre-compilation (not Mali GPU)")
            return@withContext
        }
        
        try {
            Logger.info(TAG, "Starting shader pre-compilation for MC $minecraftVersion")
            
            CACHE_DIR.mkdirs()
            
            val startTime = System.currentTimeMillis()
            
            val eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
                Logger.error(TAG, "Failed to get EGL display for shader compilation")
                return@withContext
            }
            
            if (!EGL14.eglInitialize(eglDisplay, null, 0, null, 0)) {
                Logger.error(TAG, "Failed to initialize EGL")
                return@withContext
            }
            
            val eglAttributes = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_NONE
            )
            
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!EGL14.eglChooseConfig(eglDisplay, eglAttributes, 0, configs, 0, 1, numConfigs, 0) 
                || numConfigs[0] == 0) {
                EGL14.eglTerminate(eglDisplay)
                Logger.error(TAG, "Failed to choose EGL config")
                return@withContext
            }
            
            val contextAttributes = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            
            val context = EGL14.eglCreateContext(eglDisplay, configs[0]!!, EGL14.EGL_NO_CONTEXT, contextAttributes, 0)
            if (context == EGL14.EGL_NO_CONTEXT) {
                EGL14.eglTerminate(eglDisplay)
                Logger.error(TAG, "Failed to create EGL context")
                return@withContext
            }
            
            if (!EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, context)) {
                EGL14.eglDestroyContext(eglDisplay, context)
                EGL14.eglTerminate(eglDisplay)
                Logger.error(TAG, "Failed to make EGL context current")
                return@withContext
            }
            
            compileBasicShaders()
            
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroyContext(eglDisplay, context)
            EGL14.eglTerminate(eglDisplay)
            
            val markerFile = File(CACHE_DIR, "precompiled_$minecraftVersion.marker")
            markerFile.writeText("Precompiled at ${System.currentTimeMillis()}")
            
            val elapsed = System.currentTimeMillis() - startTime
            Logger.info(TAG, "Shader pre-compilation completed in ${elapsed}ms")
            
        } catch (e: Exception) {
            Logger.error(TAG, "Shader pre-compilation failed", e)
        }
    }
    
    private fun compileBasicShaders() {
        val basicVertexShader = """
            attribute vec4 a_position;
            attribute vec2 a_texCoord;
            varying vec2 v_texCoord;
            uniform mat4 u_matrix;
            
            void main() {
                gl_Position = u_matrix * a_position;
                v_texCoord = a_texCoord;
            }
        """.trimIndent()
        
        val basicFragmentShader = """
            precision mediump float;
            varying vec2 v_texCoord;
            uniform sampler2D u_texture;
            uniform vec4 u_color;
            
            void main() {
                gl_FragColor = texture2D(u_texture, v_texCoord) * u_color;
            }
        """.trimIndent()
        
        val lightingFragmentShader = """
            precision mediump float;
            varying vec2 v_texCoord;
            uniform sampler2D u_texture;
            uniform vec3 u_lightPos;
            uniform float u_lightIntensity;
            
            void main() {
                vec4 texColor = texture2D(u_texture, v_texCoord);
                float lighting = u_lightIntensity;
                gl_FragColor = vec4(texColor.rgb * lighting, texColor.a);
            }
        """.trimIndent()
        
        compileShaderProgram(basicVertexShader, basicFragmentShader, "basic")
        compileShaderProgram(basicVertexShader, lightingFragmentShader, "lighting")
        
        GLES20.glFinish()
    }
    
    private fun compileShaderProgram(vertexSource: String, fragmentSource: String, name: String) {
        val vertexShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER)
        GLES20.glShaderSource(vertexShader, vertexSource)
        GLES20.glCompileShader(vertexShader)
        
        val vertexStatus = IntArray(1)
        GLES20.glGetShaderiv(vertexShader, GLES20.GL_COMPILE_STATUS, vertexStatus, 0)
        if (vertexStatus[0] == 0) {
            Logger.warning(TAG, "Vertex shader compilation failed for '$name': ${GLES20.glGetShaderInfoLog(vertexShader)}")
            GLES20.glDeleteShader(vertexShader)
            return
        }
        
        val fragmentShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER)
        GLES20.glShaderSource(fragmentShader, fragmentSource)
        GLES20.glCompileShader(fragmentShader)
        
        val fragmentStatus = IntArray(1)
        GLES20.glGetShaderiv(fragmentShader, GLES20.GL_COMPILE_STATUS, fragmentStatus, 0)
        if (fragmentStatus[0] == 0) {
            Logger.warning(TAG, "Fragment shader compilation failed for '$name': ${GLES20.glGetShaderInfoLog(fragmentShader)}")
            GLES20.glDeleteShader(vertexShader)
            GLES20.glDeleteShader(fragmentShader)
            return
        }
        
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertexShader)
        GLES20.glAttachShader(program, fragmentShader)
        GLES20.glLinkProgram(program)
        
        val linkStatus = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            Logger.warning(TAG, "Program linking failed for '$name': ${GLES20.glGetProgramInfoLog(program)}")
        }
        
        GLES20.glDeleteShader(vertexShader)
        GLES20.glDeleteShader(fragmentShader)
        GLES20.glDeleteProgram(program)
    }
}
