package com.example.sensorcollector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

class SensorService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START = "com.example.sensorcollector.START"
        const val ACTION_STOP = "com.example.sensorcollector.STOP"
        const val EXTRA_URL = "server_url"
        private const val CHANNEL_ID = "sensor_collection"
        private const val NOTIFICATION_ID = 41
    }

    private lateinit var sensorManager: SensorManager
    private var scheduler: ScheduledExecutorService? = null
    private var endpoint: String = ""
    private var wakeLock: PowerManager.WakeLock? = null
    private val latest = ConcurrentHashMap<String, JSONObject>()

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(SENSOR_SERVICE) as SensorManager
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopCollection()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_START -> {
                endpoint = intent.getStringExtra(EXTRA_URL).orEmpty()
                if (endpoint.isBlank()) {
                    stopSelf()
                    return START_NOT_STICKY
                }
                startForeground(NOTIFICATION_ID, buildNotification("Collecting sensor data"))
                startCollection()
            }
        }
        return START_STICKY
    }

    private fun startCollection() {
        stopCollection()
        latest.clear()

        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        sensors.forEach { sensor ->
            sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "SensorCollector:Sampling")
            .apply { setReferenceCounted(false); acquire() }

        scheduler = Executors.newSingleThreadScheduledExecutor()
        scheduler?.scheduleAtFixedRate({
            sendSnapshot()
        }, 0, 1, TimeUnit.SECONDS)
    }

    private fun stopCollection() {
        scheduler?.shutdownNow()
        scheduler = null
        sensorManager.unregisterListener(this)
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    override fun onSensorChanged(event: SensorEvent?) {
        event ?: return
        val s = event.sensor
        val values = JSONArray()
        event.values.forEach { values.put(it.toDouble()) }

        val key = "${s.type}:${s.name}:${s.vendor}"
        latest[key] = JSONObject().apply {
            put("name", s.name)
            put("vendor", s.vendor)
            put("type", s.type)
            put("typeName", s.stringType)
            put("power", s.power)
            put("resolution", s.resolution)
            put("maxRange", s.maximumRange)
            put("values", values)
            put("accuracy", event.accuracy)
            put("timestampNs", event.timestamp)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun sendSnapshot() {
        val array = JSONArray()
        latest.values.forEach { array.put(it) }

        val payload = JSONObject().apply {
            put("device", Build.MODEL)
            put("manufacturer", Build.MANUFACTURER)
            put("androidVersion", Build.VERSION.RELEASE)
            put("timestamp", System.currentTimeMillis())
            put("sensors", array)
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4000
                readTimeout = 4000
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
            }
            connection.outputStream.use {
                it.write(payload.toString().toByteArray(Charsets.UTF_8))
            }
            val code = connection.responseCode
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(
                NOTIFICATION_ID,
                buildNotification(if (code in 200..299) "Sending ${array.length()} sensors" else "Server HTTP $code")
            )
        } catch (_: Exception) {
            val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            manager.notify(NOTIFICATION_ID, buildNotification("Server unavailable; retrying"))
        } finally {
            connection?.disconnect()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Sensor collection", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
    }

    private fun buildNotification(message: String): Notification {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("Sensor Collector")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("Sensor Collector")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.stat_notify_sync)
                .setOngoing(true)
                .build()
        }
    }

    override fun onDestroy() {
        stopCollection()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
