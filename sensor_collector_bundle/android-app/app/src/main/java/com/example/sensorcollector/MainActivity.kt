package com.example.sensorcollector

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var urlInput: EditText
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        val heading = TextView(this).apply {
            text = "Sensor Collector"
            textSize = 25f
        }

        urlInput = EditText(this).apply {
            hint = "http://192.168.1.5:5000/sensors"
            setSingleLine(true)
            setText(getPreferences(MODE_PRIVATE).getString("server_url", ""))
        }

        status = TextView(this).apply {
            text = "اكتب عنوان السيرفر ثم اضغط Start"
            textSize = 16f
        }

        val start = Button(this).apply {
            text = "Start"
            setOnClickListener {
                val endpoint = urlInput.text.toString().trim()
                if (!endpoint.startsWith("http://") && !endpoint.startsWith("https://")) {
                    status.text = "أدخل عنواناً يبدأ بـ http:// أو https://"
                    return@setOnClickListener
                }
                getPreferences(MODE_PRIVATE).edit()
                    .putString("server_url", endpoint).apply()

                val intent = Intent(this@MainActivity, SensorService::class.java)
                    .setAction(SensorService.ACTION_START)
                    .putExtra(SensorService.EXTRA_URL, endpoint)

                startForegroundService(intent)
                status.text = "تم طلب بدء جمع البيانات. راقب إشعار التطبيق."
            }
        }

        val stop = Button(this).apply {
            text = "Stop"
            setOnClickListener {
                val intent = Intent(this@MainActivity, SensorService::class.java)
                    .setAction(SensorService.ACTION_STOP)
                startService(intent)
                status.text = "تم طلب إيقاف جمع البيانات."
            }
        }

        layout.addView(heading)
        layout.addView(TextView(this).apply { text = "Server URL" })
        layout.addView(urlInput)
        layout.addView(start)
        layout.addView(stop)
        layout.addView(status)
        setContentView(layout)
    }
}
