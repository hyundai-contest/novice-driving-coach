package com.example.hyundai_contest

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.UserManager

/**
 * Keeps coaching running with no screen open: started when the head unit boots (ignition on) and
 * whenever the app is opened. A foreground service survives the head unit's suspend while parked.
 */
class CoachService : Service() {
    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "주행 코칭", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("새싹운전")
            .setContentText("주행을 지켜보고 있어요")
            .setOngoing(true)
            .build()
        // Location type keeps GPS flowing with no activity on screen. Started at boot it also needs background
        // location ("항상 허용"); without it coaching still runs, speed falls back to the gear and places stay empty.
        try {
            check(checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED)
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: RuntimeException) {
            startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        }
        (application as CoachApp).recorder.start()
        return START_STICKY
    }

    companion object {
        private const val CHANNEL = "coach"

        fun start(context: Context) {
            // AAOS also boots the headless system user (0); coaching belongs to the driver's user only.
            if (UserManager.isHeadlessSystemUserMode() && context.getSystemService(UserManager::class.java).isSystemUser) return
            context.startForegroundService(Intent(context, CoachService::class.java))
        }
    }
}

/** Ignition on = the head unit boots = coaching starts, without the driver opening the app. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) CoachService.start(context)
    }
}
