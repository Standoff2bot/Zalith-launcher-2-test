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

package com.movtery.zalithlauncher.game.version.installed.utils

import com.google.gson.JsonObject
import com.movtery.zalithlauncher.utils.GSON
import com.movtery.zalithlauncher.utils.file.readText
import com.movtery.zalithlauncher.utils.logging.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.zip.ZipFile

private const val TAG = "MinecraftVulkanSupport"

/**
 * Mojang 官方原生 Vulkan 渲染后端相关工具。
 *
 * Minecraft Java 版自 26.2-snapshot-1（data/world_version = 4883）起，在客户端内置了实验性的
 * 原生 Vulkan 渲染器（通过 options.txt 中的 preferredGraphicsBackend 键切换，
 * 而不是通过本启动器已有的 GL4ES/Zink/VirGL 等"渲染器"转译层）。
 * 26.1 (Tiny Takeover, 2026年3月) 尚不包含该功能，实际起始版本是 26.2 (Chaos Cubed, 2026-06-16)。
 *
 * 判断某个版本是否支持该功能，需要读取客户端 Jar 内的 version.json 并解析 world_version 字段，
 * 而不能仅凭版本名称字符串（因为快照/自定义命名实例的名称并不规范）。
 * 由于这需要打开并解压 Jar 内的一个条目，这里按 (路径, 修改时间, 文件大小) 缓存结果，
 * 避免每次启动、以及设置界面里的提示重复解析同一个 Jar。
 */
object MinecraftVulkanSupport {
    /** 26.2-snapshot-1 对应的数据版本号，参见 https://zh.minecraft.wiki/w/版本信息文件格式 */
    const val MIN_WORLD_VERSION_FOR_NATIVE_VULKAN = 4883

    private data class CacheKey(val path: String, val lastModified: Long, val length: Long)

    //简单有界缓存：一般情况下已安装版本数量不会很多，这里做个数量上限防止无限增长
    private val cache = ConcurrentHashMap<CacheKey, Boolean>()
    private const val MAX_CACHE_ENTRIES = 128

    /**
     * @return 该客户端 Jar 对应的 Minecraft 版本是否内置了原生 Vulkan 渲染后端
     */
    suspend fun supportsNativeVulkan(clientJar: File): Boolean {
        if (!clientJar.exists()) return false
        val key = CacheKey(clientJar.absolutePath, clientJar.lastModified(), clientJar.length())

        cache[key]?.let { return it }

        val result = readWorldVersion(clientJar)
            ?.let { it >= MIN_WORLD_VERSION_FOR_NATIVE_VULKAN }
            ?: false

        if (cache.size >= MAX_CACHE_ENTRIES) cache.clear()
        cache[key] = result
        return result
    }

    private suspend fun readWorldVersion(clientJar: File): Int? = withContext(Dispatchers.IO) {
        runCatching {
            ZipFile(clientJar).use { zip ->
                zip.getEntry("version.json")
                    ?.readText(zip)
                    ?.let { GSON.fromJson(it, JsonObject::class.java) }
                    //https://zh.minecraft.wiki/w/版本信息文件格式
                    ?.get("world_version")?.asInt
            }
        }.onFailure { e ->
            Logger.warning(TAG, "Unable to determine the data version of this client Jar, possibly due to an outdated version.", e)
        }.getOrNull()
    }
}
