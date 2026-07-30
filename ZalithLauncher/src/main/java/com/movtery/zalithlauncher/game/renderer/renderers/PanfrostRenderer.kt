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

package com.movtery.zalithlauncher.game.renderer.renderers

import android.os.Build
import com.movtery.zalithlauncher.game.renderer.RendererInterface
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.isMaliGPU

object PanfrostRenderer : RendererInterface {
    override fun getRendererId(): String = "gallium_panfrost"

    override fun getUniqueIdentifier(): String = "9b2808c4-11af-4c72-a9c6-94c940396475"

    override fun getRendererName(): String = "Panfrost (Mali)"

    override fun getRendererSummary(): String = "Shader cache + no-error + AFBC off"

    override fun getMaxMCVersion(): String = "1.21.4"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        val baseEnv = mutableMapOf(
            // Shader cache for Panfrost — reuses compiled shaders across launches
            "MESA_SHADER_CACHE_DIR" to PathManager.DIR_CACHE.absolutePath,
            "MESA_GLSL_CACHE_DIR" to PathManager.DIR_CACHE.absolutePath,
            // Disable GL error checking for ~5-10% perf gain on Mali
            "MESA_NO_ERROR" to "1",
            // Disable AFBC on Mali G-series (G710) — avoids texture corruption
            // and saves memory bandwidth. Remove if no visual glitches occur.
            "PAN_MESA_DEBUG" to "noafbc",
            // Prefer low-latency rendering over vsync
            "vblank_mode" to "0",
        )
        
        // Enable 4x MSAA on Mali G710+ for better anti-aliasing with minimal overhead
        if (isMaliGPU() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            baseEnv["PAN_MESA_DEBUG"] = "noafbc,msaa4"
        }
        
        baseEnv
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libOSMesa_2300d.so"
}