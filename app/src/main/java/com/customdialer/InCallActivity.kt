package com.customdialer

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telecom.Call
import android.telecom.CallAudioState
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

class InCallActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        handle(intent)
        setContent {
            DialerTheme(forceDark = true) { InCallScreen(onEmpty = { finishAndRemoveTask() }) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(i: Intent) {
        if (i.getBooleanExtra(EXTRA_ANSWER, false)) {
            i.removeExtra(EXTRA_ANSWER)
            CallManager.answer()
        }
    }

    companion object {
        private const val EXTRA_ANSWER = "answer"
        fun intent(ctx: Context, answer: Boolean = false): Intent =
            Intent(ctx, InCallActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_ANSWER, answer)
    }
}

@Composable
fun InCallScreen(onEmpty: () -> Unit) {
    val ctx = LocalContext.current
    val calls by CallManager.calls.collectAsState()
    val audio by CallManager.audio.collectAsState()
    val info = CallManager.primary(calls)

    LaunchedEffect(calls.isEmpty()) {
        if (calls.isEmpty()) {
            delay(700)
            if (CallManager.calls.value.isEmpty()) onEmpty()
        }
    }
    LaunchedEffect(info?.state) {
        if (info != null && info.state != Call.STATE_RINGING) CallNotifier.cancel(ctx)
    }
    val seconds by produceState(0L, info?.connectTime) {
        while (true) {
            val t = info?.connectTime ?: 0L
            value = if (t > 0) (System.currentTimeMillis() - t) / 1000 else 0L
            delay(500)
        }
    }

    val ringing = info?.state == Call.STATE_RINGING
    val status = when (info?.state) {
        Call.STATE_RINGING -> "Llamada entrante"
        Call.STATE_DIALING, Call.STATE_CONNECTING -> "Llamando…"
        Call.STATE_ACTIVE -> "%02d:%02d".format(seconds / 60, seconds % 60)
        Call.STATE_HOLDING -> "En espera"
        Call.STATE_DISCONNECTING, Call.STATE_DISCONNECTED -> "Finalizando…"
        else -> ""
    }
    val title = info?.name ?: info?.number?.ifBlank { null } ?: "Número privado"

    Column(
        Modifier.fillMaxSize().background(Color(0xFF0B0F14)).systemBarsPadding().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(70.dp))
        Avatar(info?.name, null, 96.dp)
        Spacer(Modifier.height(20.dp))
        Text(title, color = Color.White, fontSize = 30.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(status, color = Color(0xFFAAB3BD), fontSize = 16.sp)
        Spacer(Modifier.weight(1f))
        if (!ringing) {
            val muted = audio?.isMuted == true
            val speaker = audio?.route == CallAudioState.ROUTE_SPEAKER
            Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                RoundAction(if (muted) Icons.Default.MicOff else Icons.Default.Mic, "Silenciar", muted) {
                    CallManager.setMuted(!muted)
                }
                RoundAction(Icons.Default.VolumeUp, "Altavoz", speaker) { CallManager.setSpeaker(!speaker) }
                RoundAction(Icons.Default.Pause, "En espera", info?.state == Call.STATE_HOLDING) {
                    CallManager.toggleHold()
                }
            }
            Spacer(Modifier.height(34.dp))
            BigButton(Icons.Default.CallEnd, Color(0xFFFF453A), "Colgar") { CallManager.hangup() }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(70.dp)) {
                BigButton(Icons.Default.CallEnd, Color(0xFFFF453A), "Rechazar") { CallManager.reject() }
                BigButton(Icons.Default.Call, Color(0xFF2FB560), "Contestar") { CallManager.answer() }
            }
        }
        Spacer(Modifier.height(30.dp))
    }
}

@Composable
private fun RoundAction(icon: ImageVector, label: String, active: Boolean, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(60.dp).clip(CircleShape)
                .background(if (active) Color.White else Color(0xFF1F2630))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) { Icon(icon, label, tint = if (active) Color.Black else Color.White) }
        Spacer(Modifier.height(8.dp))
        Text(label, color = Color(0xFFCFD6DD), fontSize = 12.sp)
    }
}

@Composable
private fun BigButton(icon: ImageVector, color: Color, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(76.dp).clip(CircleShape).background(color).clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) { Icon(icon, label, tint = Color.White, modifier = Modifier.size(34.dp)) }
}
