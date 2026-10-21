package com.acgames.painel

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object AppState {
    @Volatile var inForeground = false
}

/** Consulta o servidor a cada 10s e avisa quando chega solicitação de suporte, mensagem ou pagamento pendente. */
class NotifyService : Service() {

    companion object {
        const val CH_ALERT = "alertas"
        const val CH_SVC = "monitor"
        const val POLL_MS = 10_000L

        fun start(ctx: Context) {
            try {
                ContextCompat.startForegroundService(ctx, Intent(ctx, NotifyService::class.java))
            } catch (e: Exception) {
            }
        }
    }

    @Volatile private var running = false
    private var thread: Thread? = null
    private var wake: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannels()
        val n = NotificationCompat.Builder(this, CH_SVC)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("Painel AC")
            .setContentText("Monitorando novas solicitações")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setContentIntent(openApp())
            .build()
        ServiceCompat.startForeground(
            this, 1, n,
            if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        )
        if (!running) {
            running = true
            wake = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "painel:poll").apply { acquire() }
            thread = Thread { loop() }.also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        thread?.interrupt()
        try { wake?.release() } catch (e: Exception) {}
        super.onDestroy()
    }

    private fun loop() {
        val sp = getSharedPreferences("notify", Context.MODE_PRIVATE)
        while (running) {
            val key = SecureStore.get(this)
            if (key == null) { stopSelf(); break }
            try {
                fetch("/api/chats", key)?.let { j ->
                    check(sp, "wait", j.optInt("waiting"), 2, "Nova solicitação de suporte", "Um cliente entrou na fila de atendimento.")
                    check(sp, "unread", j.optInt("unread"), 3, "Nova mensagem", "Um cliente respondeu no suporte.")
                }
                // só a chave principal enxerga pagamentos; com a chave do suporte isso devolve null e é ignorado
                fetch("/api/payments?status=pending", key)?.let { j ->
                    check(sp, "pay", j.optInt("total"), 4, "Novo pagamento pendente", "Confira o Pix e confirme no painel.")
                }
            } catch (e: Exception) {
            }
            try { Thread.sleep(POLL_MS) } catch (e: InterruptedException) { break }
        }
    }

    /** A primeira leitura só grava a base; depois avisa quando o número sobe. */
    private fun check(sp: android.content.SharedPreferences, name: String, value: Int, id: Int, title: String, text: String) {
        val prev = sp.getInt(name, -1)
        if (prev >= 0 && value > prev && !AppState.inForeground) notifyUser(id, title, text)
        sp.edit().putInt(name, value).apply()
    }

    private fun fetch(path: String, key: String): JSONObject? {
        val c = URL(MainActivity.BASE + path).openConnection() as HttpURLConnection
        return try {
            c.connectTimeout = 8000
            c.readTimeout = 8000
            c.setRequestProperty("x-api-key", key)
            if (c.responseCode != 200) null else JSONObject(c.inputStream.bufferedReader().use { it.readText() })
        } finally {
            c.disconnect()
        }
    }

    private fun notifyUser(id: Int, title: String, text: String) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val n = NotificationCompat.Builder(this, CH_ALERT)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openApp())
            .build()
        try { NotificationManagerCompat.from(this).notify(id, n) } catch (e: SecurityException) {}
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this, 0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createChannels() {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CH_ALERT, "Alertas do painel", NotificationManager.IMPORTANCE_HIGH))
        nm.createNotificationChannel(NotificationChannel(CH_SVC, "Monitoramento", NotificationManager.IMPORTANCE_MIN))
    }
}
