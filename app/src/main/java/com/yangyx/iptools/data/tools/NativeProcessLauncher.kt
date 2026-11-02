package com.yangyx.iptools.data.tools

import android.content.Context
import android.os.Build
import android.util.Log
import java.io.File
import java.io.IOException

enum class ProcessExecMode(val label: String, val desc: String) {
    AUTO("智能模式 (优先Root)", "检测到Root环境优先使用Root提权，无Root自动直接执行"),
    NON_ROOT("免 Root 直接运行", "使用直接执行路径或系统动态链接器拉起"),
    ROOT("强制 Root 提权", "强制使用 su 超级用户提权执行")
}

data class ProcessSpawnResult(
    val process: Process,
    val modeUsed: String,
    val commandLine: String
)

object NativeProcessLauncher {

    private const val TAG = "NativeProcessLauncher"

    private val LINKER_CANDIDATES = listOf(
        "/system/bin/linker64",
        "/apex/com.android.runtime/bin/linker64",
        "/system/bin/bootstrap/linker64",
        "/system/bin/linker",
        "/apex/com.android.runtime/bin/linker",
        "/system/bin/bootstrap/linker"
    )

    fun findAvailableLinker(): String? {
        for (path in LINKER_CANDIDATES) {
            val f = File(path)
            if (f.exists() && f.canExecute()) {
                return path
            }
        }
        for (path in LINKER_CANDIDATES) {
            if (File(path).exists()) {
                return path
            }
        }
        return null
    }

    /**
     * Spawns a native binary process:
     * - AUTO: Checks if Root is available -> Prioritizes Root (su); if not rooted or su fails -> Direct exec -> Linker -> Shell.
     * - ROOT: Forces Root (su).
     * - NON_ROOT: Skips Root, uses Direct exec -> Linker -> Shell.
     */
    fun startProcess(
        context: Context,
        binaryFile: File,
        args: List<String>,
        execMode: ProcessExecMode = ProcessExecMode.AUTO,
        env: Map<String, String> = emptyMap(),
        workDir: File? = null
    ): ProcessSpawnResult {
        val binDir = workDir ?: binaryFile.parentFile ?: context.filesDir
        val isRooted = try { RootUtils.isRootAvailable() } catch (_: Exception) { false }
        val suBinary = try { RootUtils.findSuBinaryPath() } catch (_: Exception) { "su" }

        try {
            binaryFile.setReadable(true, false)
            binaryFile.setWritable(true, false)
            binaryFile.setExecutable(true, false)
        } catch (_: Exception) {}

        // Priority 1: If user chose ROOT, or AUTO with device rooted -> Try su first!
        if (execMode == ProcessExecMode.ROOT || (execMode == ProcessExecMode.AUTO && isRooted)) {
            try {
                try {
                    RootUtils.executeSuCmd("chmod 777 ${binaryFile.absolutePath}")
                } catch (_: Exception) {}

                val fullCmd = "${binaryFile.absolutePath} ${args.joinToString(" ")}"
                val pb = ProcessBuilder(suBinary, "-c", "cd ${binDir.absolutePath} && $fullCmd")
                pb.directory(binDir)
                pb.redirectErrorStream(true)
                pb.environment().putAll(env)
                pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
                val proc = pb.start()
                return ProcessSpawnResult(proc, "Root 提权 (su)", fullCmd)
            } catch (e: Exception) {
                if (execMode == ProcessExecMode.ROOT) {
                    throw IOException("Root 提权执行失败: ${e.message}")
                }
                Log.d(TAG, "Root execution failed in AUTO mode, trying direct execution...")
            }
        }

        val failureExceptions = mutableListOf<String>()

        // Priority 2: Direct Execution (Works on devices allowing execution in app dir or with permissiveness)
        try {
            val cmdList = listOf(binaryFile.absolutePath) + args
            val pb = ProcessBuilder(cmdList)
            pb.directory(binDir)
            pb.redirectErrorStream(true)
            pb.environment().putAll(env)
            pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
            val proc = pb.start()
            return ProcessSpawnResult(proc, "免 Root (直接运行)", cmdList.joinToString(" "))
        } catch (e: Exception) {
            failureExceptions.add("直接执行失败: ${e.message}")
            Log.d(TAG, "Direct exec failed: ${e.message}, attempting dynamic linker...")
        }

        // Priority 3: Try System Dynamic Linker
        val linker = findAvailableLinker()
        if (linker != null) {
            try {
                val cmdList = listOf(linker, binaryFile.absolutePath) + args
                val pb = ProcessBuilder(cmdList)
                pb.directory(binDir)
                pb.redirectErrorStream(true)
                pb.environment().putAll(env)
                pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
                val proc = pb.start()
                return ProcessSpawnResult(proc, "免 Root (动态链接器 ${File(linker).name})", cmdList.joinToString(" "))
            } catch (e: Exception) {
                failureExceptions.add("动态链接器 ($linker) 执行失败: ${e.message}")
                Log.d(TAG, "Linker exec failed: ${e.message}, attempting /system/bin/sh...")
            }
        }

        // Priority 4: Try Standard Shell
        try {
            val fullCmd = "${binaryFile.absolutePath} ${args.joinToString(" ")}"
            val pb = ProcessBuilder("/system/bin/sh", "-c", "cd ${binDir.absolutePath} && $fullCmd")
            pb.directory(binDir)
            pb.redirectErrorStream(true)
            pb.environment().putAll(env)
            pb.environment()["TMPDIR"] = context.cacheDir.absolutePath
            val proc = pb.start()
            return ProcessSpawnResult(proc, "免 Root (系统 Shell)", fullCmd)
        } catch (e: Exception) {
            failureExceptions.add("系统 Shell 执行失败: ${e.message}")
        }

        throw IOException("二进制进程启动失败:\n${failureExceptions.joinToString("\n")}\n提示: 若设备已 Root，请授予 Root 权限以完美运行。")
    }
}
