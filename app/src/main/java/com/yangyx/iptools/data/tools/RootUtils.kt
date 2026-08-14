package com.yangyx.iptools.data.tools

import java.io.File

enum class RootState(val label: String, val isRooted: Boolean) {
    CHECKING("正在检测 Root...", false),
    AUTHORIZED("已授权 Root 权限 (su)", true),
    NOT_AUTHORIZED("包含 su 但未授权/拒绝", false),
    NO_ROOT("未 Root 设备", false)
}

object RootUtils {

    private val SU_PATHS = arrayOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/data/local/xbin/su",
        "/data/local/bin/su",
        "/system/sd/xbin/su",
        "/system/bin/failsafe/su",
        "/data/local/su",
        "/su/bin/su",
        "/magisk/.core/bin/su"
    )

    /**
     * Find the absolute path to the 'su' binary.
     */
    fun findSuBinaryPath(): String {
        for (path in SU_PATHS) {
            if (File(path).exists()) return path
        }
        return "su"
    }

    /**
     * Check if 'su' binary exists in standard system locations.
     */
    fun checkSuExists(): Boolean {
        for (path in SU_PATHS) {
            if (File(path).exists()) return true
        }
        return false
    }

    /**
     * Safely test if root access is granted via su.
     * Note: Should be called on a background I/O dispatcher.
     */
    fun checkRootState(): RootState {
        val suPath = findSuBinaryPath()
        return try {
            val proc = ProcessBuilder(suPath, "-c", "id").apply {
                redirectErrorStream(true)
            }.start()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(proc.inputStream))
            val output = reader.readLine() ?: ""
            // Drain remaining output
            while (reader.readLine() != null) { /* drain */ }
            val exitCode = proc.waitFor()
            if (exitCode == 0 && (output.contains("uid=0") || output.contains("root"))) {
                RootState.AUTHORIZED
            } else if (checkSuExists()) {
                RootState.NOT_AUTHORIZED
            } else {
                RootState.NO_ROOT
            }
        } catch (_: Exception) {
            if (checkSuExists()) RootState.NOT_AUTHORIZED else RootState.NO_ROOT
        }
    }

    /**
     * Quick check if root privilege is available.
     */
    fun isRootAvailable(): Boolean {
        return checkRootState().isRooted
    }

    /**
     * Safely execute a shell command via Root (su).
     */
    fun executeSuCmd(cmd: String): Boolean {
        val suPath = findSuBinaryPath()
        return try {
            val proc = ProcessBuilder(suPath, "-c", cmd).apply {
                redirectErrorStream(true)
            }.start()
            val reader = java.io.BufferedReader(java.io.InputStreamReader(proc.inputStream))
            while (reader.readLine() != null) { /* drain stream */ }
            proc.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Get Root privilege status string for UI/Logging.
     */
    fun getRootStatusDescription(): String {
        return checkRootState().label
    }
}
