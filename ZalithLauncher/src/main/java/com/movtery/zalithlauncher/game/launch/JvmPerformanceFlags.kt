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

package com.movtery.zalithlauncher.game.launch

import com.movtery.zalithlauncher.utils.string.splitPreservingQuotes

/**
 * 推荐的 JVM 性能调优参数（可选，通过设置界面的按钮手动应用，不会自动强加给所有用户）。
 *
 * 背景：本启动器目前不会显式指定垃圾回收器，实际使用哪种 GC 完全取决于所选 Java 运行时的默认值
 * （例如 JDK 8 默认是 Parallel GC，是吞吐量优先、暂停时间较长的回收器；JDK 9+ 默认是 G1GC）。
 * Minecraft 是延迟敏感的交互式程序，Parallel GC 的长时间 Full GC 停顿会直接表现为掉帧/卡顿。
 * 这里显式启用 G1GC，并使用一组按客户端场景（数百 MB ~ 数 GB 堆，而不是服务端动辄 8GB+）
 * 收窄过的暂停时间调优参数，而不是直接照搬面向服务端的 "Aikar's flags"。
 *
 * 这不是被强制应用的默认值：是否使用由玩家在设置界面里主动点击后决定，且：
 *  - 如果玩家已经手动指定了任意 GC（-XX:+Use*GC），则不会追加或覆盖，尊重玩家的显式选择；
 *  - 如果这些参数已经存在，不会重复添加。
 */
object JvmPerformanceFlags {
    private val GC_FLAG_REGEX = Regex("^-XX:[+-]Use\\w*GC$")

    val RECOMMENDED_FLAGS: List<String> = listOf(
        "-XX:+UseG1GC",
        "-XX:MaxGCPauseMillis=40",
        "-XX:+ParallelRefProcEnabled",
        "-XX:G1NewSizePercent=20",
        "-XX:G1ReservePercent=20",
        "-XX:G1HeapRegionSize=8M",
        "-XX:G1MixedGCCountTarget=4",
        "-XX:InitiatingHeapOccupancyPercent=15"
    )

    /** @return 是否可以应用（即用户尚未手动指定过其他 GC，且尚未完整应用过） */
    fun canApply(currentArgs: String): Boolean {
        val tokens = currentArgs.splitPreservingQuotes()
        if (tokens.any { GC_FLAG_REGEX.matches(it) && it !in RECOMMENDED_FLAGS }) return false
        return !RECOMMENDED_FLAGS.all { it in tokens }
    }

    /**
     * 将推荐参数追加到现有 JVM 参数字符串后面。
     * 如果用户已经手动指定了其他 GC，原样返回，不做任何改动。
     */
    fun appendTo(currentArgs: String): String {
        val tokens = currentArgs.splitPreservingQuotes().toMutableList()

        //用户已自行指定了别的GC（例如 -XX:+UseZGC/-XX:+UseParallelGC/-XX:+UseSerialGC），
        //尊重用户的显式选择，不做任何改动
        if (tokens.any { GC_FLAG_REGEX.matches(it) && it !in RECOMMENDED_FLAGS }) {
            return currentArgs
        }

        val toAdd = RECOMMENDED_FLAGS.filter { it !in tokens }
        if (toAdd.isEmpty()) return currentArgs

        return (tokens + toAdd).joinToString(" ")
    }
}
