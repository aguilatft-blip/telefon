package com.customdialer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.telecom.VideoProfile
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Guarda las llamadas activas y expone su estado a la pantalla de llamada. */
object CallManager {
    data class Info(
        val call: Call,
        val number: String,
        val name: String?,
        val state: Int,
        val connectTime: Long
    )

    private val tracked = mutableListOf<Call>()
    private val names = mutableMapOf<Call, String?>()
    private val callbacks = mutableMapOf<Call, Call.Callback>()
    private val _calls = MutableStateFlow<List<Info>>(emptyList())
    val calls: StateFlow<List<Info>> = _calls
    val audio = MutableStateFlow<CallAudioState?>(null)
    var service: InCallService? = null

    fun add(ctx: Context, call: Call) {
        names[call] = Repo.lookupName(ctx, numberOf(call))
        val cb = object : Call.Callback() {
            override fun onStateChanged(call: Call, state: Int) = publish()
            override fun onDetailsChanged(call: Call, details: Call.Details) = publish()
        }
        call.registerCallback(cb)
        callbacks[call] = cb
        tracked += call
        publish()
    }

    fun remove(call: Call) {
        callbacks.remove(call)?.let { call.unregisterCallback(it) }
        tracked -= call
        names.remove(call)
        publish()
    }

    private fun numberOf(c: Call): String = c.details?.handle?.schemeSpecificPart ?: ""

    private fun publish() {
        _calls.value = tracked.map {
            Info(it, numberOf(it), names[it], it.state, it.details?.connectTimeMillis ?: 0L)
        }
    }

    fun primary(list: List<Info>): Info? =
        list.firstOrNull { it.state == Call.STATE_RINGING } ?: list.lastOrNull()

    fun answer() { primary(_calls.value)?.call?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    fun reject() { primary(_calls.value)?.call?.reject(false, null) }
    fun hangup() { primary(_calls.value)?.call?.disconnect() }
    fun toggleHold() {
        val i = primary(_calls.value) ?: return
        if (i.state == Call.STATE_HOLDING) i.call.unhold() else i.call.hold()
    }
    fun setMuted(on: Boolean) { service?.setMuted(on) }
    fun setSpeaker(on: Boolean) {
        service?.setAudioRoute(if (on) CallAudioState.ROUTE_SPEAKER else CallAudioState.ROUTE_EARPIECE)
    }
}

/** El sistema conecta esta clase cuando la app es el teléfono predeterminado. */
class CallService : InCallService() {
    override fun onCreate() {
        super.onCreate()
        CallManager.service = this
    }

    override fun onDestroy() {
        CallManager.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        CallManager.add(this, call)
        CallManager.audio.value = callAudioState
        if (call.state == Call.STATE_RINGING) {
            CallManager.calls.value.firstOrNull { it.call == call }
                ?.let { CallNotifier.showIncoming(this, it) }
            if (!NotificationManagerCompat.from(this).areNotificationsEnabled()) {
                runCatching { startActivity(InCallActivity.intent(this)) }
            }
        } else {
            runCatching { startActivity(InCallActivity.intent(this)) }
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        CallManager.remove(call)
        CallNotifier.cancel(this)
    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        CallManager.audio.value = audioState
    }
}

object CallNotifier {
    private const val CHANNEL = "incoming_calls"
    private const val ID = 1
    const val ACTION_DECLINE = "com.customdialer.DECLINE"

    fun showIncoming(ctx: Context, info: CallManager.Info) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Llamadas entrantes", NotificationManager.IMPORTANCE_HIGH)
                .apply { setSound(null, null); enableVibration(false) } // el sistema ya hace sonar el tono
        )
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val open = PendingIntent.getActivity(ctx, 0, InCallActivity.intent(ctx), flags)
        val answer = PendingIntent.getActivity(ctx, 1, InCallActivity.intent(ctx, answer = true), flags)
        val decline = PendingIntent.getBroadcast(
            ctx, 2, Intent(ctx, CallActionReceiver::class.java).setAction(ACTION_DECLINE), flags
        )
        val title = info.name ?: info.number.ifBlank { "Número privado" }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(android.R.drawable.sym_call_incoming)
            .setContentTitle(title)
            .setContentText("Llamada entrante")
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setOngoing(true)
            .setContentIntent(open)
            .setFullScreenIntent(open, true)
            .addAction(0, "Rechazar", decline)
            .addAction(0, "Contestar", answer)
            .build()
        try {
            nm.notify(ID, n)
        } catch (_: SecurityException) {
        }
    }

    fun cancel(ctx: Context) = NotificationManagerCompat.from(ctx).cancel(ID)
}

class CallActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == CallNotifier.ACTION_DECLINE) CallManager.reject()
    }
}

object CallActions {
    fun place(ctx: Context, number: String) {
        val tm = ctx.getSystemService(TelecomManager::class.java)
        try {
            tm.placeCall(Uri.fromParts("tel", number, null), Bundle())
        } catch (_: SecurityException) {
            Toast.makeText(ctx, "Falta el permiso para hacer llamadas", Toast.LENGTH_LONG).show()
        }
    }
}
