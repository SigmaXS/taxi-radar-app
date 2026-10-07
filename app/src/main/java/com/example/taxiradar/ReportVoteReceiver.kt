package com.example.taxiradar

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Кнопки «Ещё здесь / Уже нет» в уведомлении о метке на дороге. */
class ReportVoteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", -1)
        if (id < 0) return
        val still = intent.getBooleanExtra("still", true)
        val app = context.applicationContext
        app.getSystemService(NotificationManager::class.java)?.cancel(RoadReports.ROAD_NOTIFICATION_ID)
        app.getSystemService(NotificationManager::class.java)?.cancel(RoadReports.ROAD_ALERT_ID)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val ok = RoadReports.vote(app, id, still)
                kotlinx.coroutines.withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(app, app.getString(if (ok) R.string.map_vote_thanks else R.string.clients_no_connection), android.widget.Toast.LENGTH_SHORT).show()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
