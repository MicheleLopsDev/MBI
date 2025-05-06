package io.github.luposolitario.mbi


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.bumptech.glide.Glide
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RadioPlayerService : Service() {

    private lateinit var player: ExoPlayer
    private var wakeLock: PowerManager.WakeLock? = null
    private var name: String? = null
    private var icon: String? = null
    private var radioUrl: String? = null

    companion object {
        const val ACTION_START = "ACTION_START_RADIO"
        const val ACTION_STOP = "ACTION_STOP_RADIO"
        const val ACTION_TOGGLE = "ACTION_TOGGLE_RADIO"
        private const val CHANNEL_ID = "radio_playback"
        private const val NOTIF_ID = 42
        var isRunning = false
        var isPlayerAlive = false
        var isPlaying = false
    }

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
        isRunning = true
    }

    override fun onDestroy() {
        stopPlayback()
        super.onDestroy()
        isRunning = false
    }

    override fun onBind(intent: Intent?): IBinder = binder

    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): RadioPlayerService = this@RadioPlayerService
    }

    fun startRadioPlayer(name: String?, icon: String?, radioUrl: String?, player: ExoPlayer) {
        if (radioUrl.isNullOrBlank()) return
        isPlayerAlive = true
        acquireWakeLock()

        val mediaItem = MediaItem.Builder().setUri(radioUrl.toString()).build()

        player.apply {
            setMediaItem(mediaItem)
            prepare()
            playWhenReady = true
        }

        // Usa una coroutine per costruire la notifica in modo asincrono
        CoroutineScope(Dispatchers.Main).launch {
            val notification = createNotification(name ?: "Radio", icon)
            startForeground(NOTIF_ID, notification)
        }
    }


    fun toggleRadioPlayer(name: String?, icon: String?, radioUrl: String?, player: ExoPlayer) {
        acquireWakeLock()

        if (player.isPlaying)
            pauseRadio()
        else
            playRadio()

        // Usa una coroutine per costruire la notifica in modo asincrono
        CoroutineScope(Dispatchers.Main).launch {
            val notification = createNotification(name ?: "Radio", icon)
            startForeground(NOTIF_ID, notification)
        }
    }

    fun stopRadio() {
        stopPlayback()
        stopForeground(true)
        stopSelf()
    }

    private fun stopPlayback() {
        if (player.playbackState != ExoPlayer.STATE_IDLE &&
            player.playbackState != ExoPlayer.STATE_ENDED
        ) {
            player.stop()
        }
        player.release()
        wakeLock?.release()
        wakeLock = null
        isPlayerAlive = false
    }

    private fun playRadio() {
        player.play()
        isPlaying = true
    }

    private fun pauseRadio() {
        player.pause()
        isPlaying = false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val powerManager = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock =
            powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "RadioService::Wakelock")
        wakeLock?.acquire(60 * 60 * 1000L)
    }

    suspend fun Context.loadBitmap(url: String): Bitmap? = withContext(Dispatchers.IO) {
        return@withContext try {
            Glide.with(this@loadBitmap)
                .asBitmap()
                .load(url)
                .submit()
                .get()       // blocca *solo* questo thread di I/O
        } catch (e: Exception) {
            Log.w("RadioService", "Icon download failed", e)
            null
        }
    }

    // 1) Sessione (una sola volta)
    private val mediaSession by lazy {
        MediaSessionCompat(this, "RadioSession").apply {
            isActive = true
        }
    }

    // 2) Metadati con copertina
    private fun updateMetadata(title: String, bmp: Bitmap?) {
        val meta = MediaMetadataCompat.Builder()
            .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bmp)
            .build()
        mediaSession.setMetadata(meta)
    }

    private suspend fun createNotification(title: String, iconUrl: String?): Notification {

        val largeBmp = iconUrl?.let { applicationContext.loadBitmap(it) }
            ?: BitmapFactory.decodeResource(resources, R.drawable.pngtreevector_radio_icon_4091198)

        updateMetadata(title, largeBmp)        // ← importa!

        val notif = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.pngtreevector_radio_icon_4091198)              // icona 24dp mono
            .setContentTitle(title)
            .setContentText("Streaming in corso")
            .setLargeIcon(largeBmp)
            .addAction(
                R.drawable.start_button_icon_4545797, "Pause",
                PendingIntent.getService(
                    this, 0,
                    Intent(this, RadioPlayerService::class.java).apply {
                        action = RadioPlayerService.ACTION_TOGGLE
                        putExtra("name", name)
                        putExtra("icon", icon)
                        putExtra("url", radioUrl.toString())
                    },
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
                ),
            )
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(mediaSession.sessionToken)   // ★ il pezzo mancante
                    .setShowActionsInCompactView(0)
            )
            .setForegroundServiceBehavior(
                NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE   // API 34+
            )
            .setOngoing(true)
            .build()

        startForeground(NOTIF_ID, notif)          // entro 5 s dallo start

        return notif
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        name = intent?.getStringExtra("name") ?: "Radio"
        icon = intent?.getStringExtra("icon")
        radioUrl = intent?.getStringExtra("url")

        when (intent?.action) {
            ACTION_START -> {
                // Usa il player condiviso
                if (isPlayerAlive) {
                    player = PlayerHolder.exoPlayer ?: ExoPlayer.Builder(this).build()
                } else {
                    player = ExoPlayer.Builder(this).build()
                }

                if (!radioUrl.isNullOrBlank()) {
                    startRadioPlayer(name, icon, radioUrl, player)
                }
            }

            ACTION_TOGGLE -> {
                toggleRadioPlayer(name, icon, radioUrl, player)
            }

            ACTION_STOP -> {
                stopRadio()
            }
        }
        return START_STICKY
    }


    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            val mgr = getSystemService(NotificationManager::class.java)
            val ch = NotificationChannel(
                CHANNEL_ID, "Riproduzione radio",
                NotificationManager.IMPORTANCE_LOW
            )
            mgr.createNotificationChannel(ch)
        }
    }

}
