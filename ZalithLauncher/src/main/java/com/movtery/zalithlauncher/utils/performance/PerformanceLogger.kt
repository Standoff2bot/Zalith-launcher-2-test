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

package com.movtery.zalithlauncher.utils.performance

import android.app.ActivityManager
import android.content.Context
import android.os.Debug
import com.movtery.zalithlauncher.ZLApplication
import com.movtery.zalithlauncher.utils.logging.Logger
import java.io.File
import java.io.FileWriter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Lightweight performance metrics logger for debugging and optimization.
 * Tracks FPS, memory usage, and renderer info when enabled via settings.
 * 
 * Logs are written to: launcher_cache/performance_log.txt
 * Enable via Settings > Advanced > Performance Logging
 */
object PerformanceLogger {
    private const val TAG = "PerformanceLogger"
    private val enabled = AtomicBoolean(false)
    private val lastLogTime = AtomicLong(0)
    private val frameCount = AtomicLong(0)
    private val lastFrameTime = AtomicLong(System.nanoTime())
    
    private var logFile: File? = null
    private var logWriter: FileWriter? = null
    
    fun setEnabled(enable: Boolean) {
        if (enable == enabled.get()) return
        
        enabled.set(enable)
        
        if (enable) {
            startLogging()
        } else {
            stopLogging()
        }
    }
    
    fun isEnabled(): Boolean = enabled.get()
    
    private fun startLogging() {
        try {
            val cacheDir = ZLApplication.getContext().cacheDir
            logFile = File(cacheDir, "performance_log.txt")
            logWriter = FileWriter(logFile, true)
            
            logWriter?.appendLine("=== Performance Logging Started ===")
            logWriter?.appendLine("Timestamp: ${System.currentTimeMillis()}")
            logWriter?.appendLine("Device: ${android.os.Build.MODEL}")
            logWriter?.appendLine("Android: ${android.os.Build.VERSION.RELEASE}")
            logWriter?.flush()
            
            Logger.info(TAG, "Performance logging enabled, writing to: ${logFile?.absolutePath}")
        } catch (e: Exception) {
            Logger.error(TAG, "Failed to start performance logging", e)
            enabled.set(false)
        }
    }
    
    private fun stopLogging() {
        try {
            logWriter?.appendLine("=== Performance Logging Stopped ===")
            logWriter?.appendLine("Total frames: ${frameCount.get()}")
            logWriter?.flush()
            logWriter?.close()
            logWriter = null
            
            Logger.info(TAG, "Performance logging disabled")
        } catch (e: Exception) {
            Logger.error(TAG, "Error stopping performance logging", e)
        }
    }
    
    /**
     * Call this every frame from renderer to track FPS
     */
    fun onFrame() {
        if (!enabled.get()) return
        
        frameCount.incrementAndGet()
        val currentTime = System.nanoTime()
        val elapsed = currentTime - lastFrameTime.get()
        lastFrameTime.set(currentTime)
        
        val currentMillis = System.currentTimeMillis()
        val lastLog = lastLogTime.get()
        
        if (currentMillis - lastLog >= 5000) {
            lastLogTime.set(currentMillis)
            logMetrics(elapsed)
        }
    }
    
    private fun logMetrics(lastFrameNanos: Long) {
        try {
            val fps = if (lastFrameNanos > 0) 1_000_000_000.0 / lastFrameNanos else 0.0
            val memInfo = getMemoryInfo()
            
            logWriter?.appendLine("--- Frame ${frameCount.get()} ---")
            logWriter?.appendLine("FPS: ${"%.1f".format(fps)}")
            logWriter?.appendLine("Frame time: ${"%.2f".format(lastFrameNanos / 1_000_000.0)}ms")
            logWriter?.appendLine("Used Memory: ${memInfo.usedMB}MB / ${memInfo.totalMB}MB")
            logWriter?.appendLine("Native Heap: ${memInfo.nativeHeapMB}MB")
            logWriter?.appendLine("GPU Renderer: ${memInfo.gpuRenderer}")
            logWriter?.appendLine("")
            logWriter?.flush()
        } catch (e: Exception) {
            Logger.error(TAG, "Error writing performance metrics", e)
        }
    }
    
    private data class MemoryInfo(
        val usedMB: Long,
        val totalMB: Long,
        val nativeHeapMB: Long,
        val gpuRenderer: String
    )
    
    private fun getMemoryInfo(): MemoryInfo {
        val runtime = Runtime.getRuntime()
        val usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024)
        val totalMemory = runtime.maxMemory() / (1024 * 1024)
        val nativeHeap = Debug.getNativeHeapAllocatedSize() / (1024 * 1024)
        
        val activityManager = ZLApplication.getContext().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        
        val renderer = System.getenv("POJAV_RENDERER") ?: "unknown"
        
        return MemoryInfo(
            usedMB = usedMemory,
            totalMB = totalMemory,
            nativeHeapMB = nativeHeap,
            gpuRenderer = renderer
        )
    }
}
