package com.yangyx.iptools.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.yangyx.iptools.MainActivity
import com.yangyx.iptools.R
import com.yangyx.iptools.data.tools.FrpEngine
import java.util.concurrent.ConcurrentHashMap

class IpToolsBackgroundService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    override fun onCreate() {
        super.onCreate()
        acquireWakeAndWifiLocks()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_SYNC_TASKS

        when (action) {
            ACTION_STOP_ALL_TASKS -> {
                Log.i(TAG, "Received ACTION_STOP_ALL_TASKS from user")
                stopAllTasksInternal()
                stopForegroundAndSelf()
                return START_NOT_STICKY
            }
            ACTION_UPDATE_TASK -> {
                val taskKey = intent?.getStringExtra(EXTRA_TASK_KEY) ?: ""
                val taskDesc = intent?.getStringExtra(EXTRA_TASK_DESC) ?: ""
                if (taskKey.isNotBlank()) {
                    activeTasksMap[taskKey] = taskDesc
                }
            }
            ACTION_REMOVE_TASK -> {
                val taskKey = intent?.getStringExtra(EXTRA_TASK_KEY) ?: ""
                if (taskKey.isNotBlank()) {
                    activeTasksMap.remove(taskKey)
                }
            }
        }

        if (activeTasksMap.isEmpty()) {
            stopForegroundAndSelf()
            return START_NOT_STICKY
        }

        val notification = buildForegroundNotification()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var fgsType = 0
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC or ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else {
                    fgsType = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                }
                startForeground(NOTIFICATION_ID, notification, fgsType)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}")
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (_: Exception) {}
        }

        return START_STICKY
    }

    private fun acquireWakeAndWifiLocks() {
        try {
            if (wakeLock == null) {
                val pm = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = pm?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IPTools:BackgroundWakeLock")?.apply {
                    setReferenceCounted(false)
                    acquire(24 * 60 * 60 * 1000L) // 24 hours max fallback
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WakeLock acquisition error: ${e.message}")
        }

        try {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    @Suppress("DEPRECATION")
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm?.createWifiLock(mode, "IPTools:BackgroundWifiLock")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "WifiLock acquisition error: ${e.message}")
        }
    }

    private fun releaseLocks() {
        try {
            wakeLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        wakeLock = null

        try {
            wifiLock?.let {
                if (it.isHeld) it.release()
            }
        } catch (_: Exception) {}
        wifiLock = null
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "后台任务与穿透服务保持",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持 FRP 穿透隧道连接、网络扫描和测速任务在后台稳定运行"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(): Notification {
        val tasks = activeTasksMap.values.toList()
        val title = if (tasks.size == 1) {
            "IPTools 任务后台运行中"
        } else {
            "IPTools 后台运行中 (${tasks.size} 个任务)"
        }

        val contentText = if (tasks.isNotEmpty()) {
            tasks.joinToString(" | ")
        } else {
            "网络工具服务保持中"
        }

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAllIntent = Intent(this, IpToolsBackgroundService::class.java).apply {
            action = ACTION_STOP_ALL_TASKS
        }
        val stopAllPendingIntent = PendingIntent.getService(
            this,
            1,
            stopAllIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_iptools)
            .setContentTitle(title)
            .setContentText(contentText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(tasks.joinToString("\n• ", prefix = "• ")))
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "停止所有任务", stopAllPendingIntent)
            .build()
    }

    private fun stopAllTasksInternal() {
        activeTasksMap.clear()
        try {
            FrpEngine.stopClient()
            FrpEngine.stopServer()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping FRP: ${e.message}")
        }
        onStopAllRequestedListener?.invoke()
    }

    private fun stopForegroundAndSelf() {
        releaseLocks()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    override fun onDestroy() {
        releaseLocks()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "IpToolsBgService"
        const val CHANNEL_ID = "iptools_background_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_UPDATE_TASK = "com.yangyx.iptools.ACTION_UPDATE_TASK"
        const val ACTION_REMOVE_TASK = "com.yangyx.iptools.ACTION_REMOVE_TASK"
        const val ACTION_SYNC_TASKS = "com.yangyx.iptools.ACTION_SYNC_TASKS"
        const val ACTION_STOP_ALL_TASKS = "com.yangyx.iptools.ACTION_STOP_ALL_TASKS"

        const val EXTRA_TASK_KEY = "extra_task_key"
        const val EXTRA_TASK_DESC = "extra_task_desc"

        // Task Keys
        const val KEY_FRP_CLIENT = "FRP_CLIENT"
        const val KEY_FRP_SERVER = "FRP_SERVER"
        const val KEY_FSCAN = "FSCAN"
        const val KEY_PORT_SCAN = "PORT_SCAN"
        const val KEY_PING = "PING"
        const val KEY_IPERF = "IPERF"
        const val KEY_TRACE = "TRACE"

        private val activeTasksMap = ConcurrentHashMap<String, String>()

        var onStopAllRequestedListener: (() -> Unit)? = null

        fun startOrUpdateTask(context: Context, taskKey: String, taskDescription: String) {
            activeTasksMap[taskKey] = taskDescription
            val intent = Intent(context, IpToolsBackgroundService::class.java).apply {
                action = ACTION_UPDATE_TASK
                putExtra(EXTRA_TASK_KEY, taskKey)
                putExtra(EXTRA_TASK_DESC, taskDescription)
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start foreground service: ${e.message}")
            }
        }

        fun removeTask(context: Context, taskKey: String) {
            activeTasksMap.remove(taskKey)
            if (activeTasksMap.isEmpty()) {
                stopAll(context)
            } else {
                val intent = Intent(context, IpToolsBackgroundService::class.java).apply {
                    action = ACTION_REMOVE_TASK
                    putExtra(EXTRA_TASK_KEY, taskKey)
                }
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (_: Exception) {}
            }
        }

        fun stopAll(context: Context) {
            activeTasksMap.clear()
            val intent = Intent(context, IpToolsBackgroundService::class.java).apply {
                action = ACTION_STOP_ALL_TASKS
            }
            try {
                context.startService(intent)
            } catch (_: Exception) {}
        }

        fun hasActiveTasks(): Boolean = activeTasksMap.isNotEmpty()

        fun getActiveTasksCount(): Int = activeTasksMap.size
    }
}
