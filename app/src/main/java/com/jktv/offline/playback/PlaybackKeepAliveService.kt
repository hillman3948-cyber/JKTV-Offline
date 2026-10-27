package com.jktv.offline.playback

import android.app.*
import android.content.*
import android.os.*
import android.net.wifi.WifiManager
import com.jktv.offline.MainActivity

/** User-started media session lifetime; no background autostart or song reconstruction. */
class PlaybackKeepAliveService:Service() {
    private val handler=Handler(Looper.getMainLooper())
    private val expire=Runnable {stopSelf()}
    private var cpu:PowerManager.WakeLock?=null
    private var wifi:WifiManager.WifiLock?=null
    override fun onBind(intent:Intent?)=null
    override fun onCreate() {
        super.onCreate()
        val manager=getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel("jktv_playback","Karaoke playback",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification=Notification.Builder(this,"jktv_playback").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("JKTV karaoke session").setContentText("Playback and Wi-Fi remotes are active")
            .setContentIntent(open).setOngoing(true).setCategory(Notification.CATEGORY_TRANSPORT).build()
        startForeground(8383,notification)
        cpu=(getSystemService(POWER_SERVICE) as PowerManager).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"JKTV:PlaybackSession").apply {setReferenceCounted(false)}
        @Suppress("DEPRECATION")
        wifi=(applicationContext.getSystemService(WIFI_SERVICE) as WifiManager).createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF,"JKTV:StageNetwork").apply {setReferenceCounted(false)}
    }
    override fun onStartCommand(intent:Intent?,flags:Int,startId:Int):Int {
        // Refreshed by the live session. A missing owner cannot hold locks indefinitely.
        handler.removeCallbacks(expire);handler.postDelayed(expire,120000)
        cpu?.acquire(120000);wifi?.let {if(!it.isHeld)it.acquire()}
        return START_NOT_STICKY
    }
    override fun onDestroy(){handler.removeCallbacks(expire);cpu?.let{if(it.isHeld)it.release()};wifi?.let{if(it.isHeld)it.release()};super.onDestroy()}
    companion object {
        fun start(context:Context) {context.startForegroundService(Intent(context,PlaybackKeepAliveService::class.java))}
        fun stop(context:Context) {context.stopService(Intent(context,PlaybackKeepAliveService::class.java))}
    }
}
