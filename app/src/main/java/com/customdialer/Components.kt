package com.customdialer

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.isSystemInDarkTheme
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val Teal = Color(0xFF0A93B3)
val CallGreen = Color(0xFF2FB560)
val EndRed = Color(0xFFFF453A)
private val shades = listOf(Color(0xFF0A93B3), Color(0xFF3F6FD1), Color(0xFF2E9E6B))

@Composable
fun DialerTheme(forceDark: Boolean = false, content: @Composable () -> Unit) {
    val dark = forceDark || isSystemInDarkTheme()
    val scheme = if (dark) darkColorScheme(
        primary = Color(0xFF2AA7D8),
        background = Color(0xFF0E0E10),
        surface = Color(0xFF0E0E10),
        surfaceVariant = Color(0xFF262629),
        onSurfaceVariant = Color(0xFFA0A0A5)
    ) else lightColorScheme(
        primary = Color(0xFF0B86B3),
        background = Color.White,
        surface = Color.White,
        surfaceVariant = Color(0xFFEFF1F4),
        onSurfaceVariant = Color(0xFF6B7078)
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

fun initials(name: String): String {
    val w = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return w.take(2).joinToString("") { it.take(1) }.uppercase()
}

@Composable
fun Avatar(name: String?, photo: String?, size: Dp) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, photo) {
        value = photo?.let {
            withContext(Dispatchers.IO) { Repo.loadBitmap(ctx, it)?.asImageBitmap() }
        }
    }
    val shade = shades[(name ?: "").length % shades.size]
    Box(
        Modifier.size(size).clip(CircleShape).background(shade),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else if (!name.isNullOrBlank() && name.any { it.isLetter() }) {
            Text(initials(name), color = Color.White, fontSize = (size.value * 0.36f).sp)
        } else {
            Icon(Icons.Default.Person, null, tint = Color.White, modifier = Modifier.size(size / 2))
        }
    }
}

@Composable
fun FileImage(path: String, modifier: Modifier, scale: ContentScale = ContentScale.Crop) {
    val bmp by produceState<ImageBitmap?>(null, path) {
        value = withContext(Dispatchers.IO) { BitmapFactory.decodeFile(path)?.asImageBitmap() }
    }
    val b = bmp
    if (b != null) Image(b, null, modifier, contentScale = scale)
    else Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))
}

@Composable
fun ContactRow(
    title: String,
    subtitle: String,
    photo: String?,
    avatarName: String?,
    titleColor: Color = Color.Unspecified,
    onCall: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onCall).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(avatarName, photo, 60.dp)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
            Text(title, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = titleColor)
            Text(
                subtitle, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(
            onClick = onCall,
            modifier = Modifier.size(46.dp)
                .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(Icons.Default.Phone, "Llamar", tint = MaterialTheme.colorScheme.primary)
        }
    }
}

private fun toneFor(k: String): Int = when (k) {
    "0" -> ToneGenerator.TONE_DTMF_0
    "1" -> ToneGenerator.TONE_DTMF_1
    "2" -> ToneGenerator.TONE_DTMF_2
    "3" -> ToneGenerator.TONE_DTMF_3
    "4" -> ToneGenerator.TONE_DTMF_4
    "5" -> ToneGenerator.TONE_DTMF_5
    "6" -> ToneGenerator.TONE_DTMF_6
    "7" -> ToneGenerator.TONE_DTMF_7
    "8" -> ToneGenerator.TONE_DTMF_8
    "9" -> ToneGenerator.TONE_DTMF_9
    "*" -> ToneGenerator.TONE_DTMF_S
    "#" -> ToneGenerator.TONE_DTMF_P
    else -> -1
}

private val letters = mapOf(
    "2" to "ABC", "3" to "DEF", "4" to "GHI", "5" to "JKL",
    "6" to "MNO", "7" to "PQRS", "8" to "TUV", "9" to "WXYZ", "0" to "+"
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Keypad(
    dial: String,
    hint: String?,
    onKey: (String) -> Unit,
    onBack: () -> Unit,
    onClear: () -> Unit,
    onCall: () -> Unit,
    onHide: () -> Unit
) {
    val surf = MaterialTheme.colorScheme.surfaceVariant
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val tone = remember {
        try { ToneGenerator(AudioManager.STREAM_DTMF, 100) } catch (e: Exception) { null }
    }
    DisposableEffect(Unit) { onDispose { tone?.release() } }
    val haptic = LocalHapticFeedback.current
    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .systemBarsPadding().padding(bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(40.dp))
        Text(
            dial, fontSize = 38.sp, fontWeight = FontWeight.Light, maxLines = 1,
            modifier = Modifier.heightIn(min = 50.dp).padding(horizontal = 20.dp)
        )
        Text(
            hint ?: "", color = MaterialTheme.colorScheme.primary, fontSize = 15.sp,
            maxLines = 1, modifier = Modifier.heightIn(min = 22.dp)
        )
        Spacer(Modifier.weight(1f))
        listOf(
            listOf("1", "2", "3"), listOf("4", "5", "6"),
            listOf("7", "8", "9"), listOf("*", "0", "#")
        ).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(26.dp)) {
                row.forEach { k ->
                    Box(
                        Modifier.size(76.dp).clip(CircleShape).background(surf)
                            .combinedClickable(
                                onClick = {
                                    val t = toneFor(k)
                                    if (t >= 0) tone?.startTone(t, 150)
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onKey(k)
                                },
                                onLongClick = if (k == "0") ({ onKey("+") }) else null
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(k, fontSize = 30.sp, fontWeight = FontWeight.Light)
                            val sub = letters[k] ?: ""
                            if (sub.isNotEmpty()) Text(sub, fontSize = 10.sp, color = muted, letterSpacing = 2.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(26.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(76.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onHide) {
                    Icon(Icons.Default.KeyboardArrowDown, "Ocultar teclado", tint = muted, modifier = Modifier.size(34.dp))
                }
            }
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(CallGreen).clickable(onClick = onCall),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.Phone, "Llamar", tint = Color.White, modifier = Modifier.size(34.dp)) }
            Box(
                Modifier.size(76.dp).clip(CircleShape)
                    .combinedClickable(onClick = onBack, onLongClick = onClear),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.AutoMirrored.Filled.Backspace, "Borrar", tint = muted, modifier = Modifier.size(30.dp)) }
        }
    }
}
