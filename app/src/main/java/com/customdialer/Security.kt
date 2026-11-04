package com.customdialer

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.security.MessageDigest
import java.security.SecureRandom

/** Guarda la clave de la tuerca (solo su huella cifrada, nunca la clave en texto). */
object PinStore {
    private fun prefs(ctx: Context) = ctx.getSharedPreferences("security", Context.MODE_PRIVATE)

    fun hasPin(ctx: Context): Boolean = prefs(ctx).contains("hash")

    private fun hash(pin: String, salt: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest((salt + pin).toByteArray())
            .joinToString("") { "%02x".format(it) }

    fun setPin(ctx: Context, pin: String) {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        val salt = bytes.joinToString("") { "%02x".format(it) }
        prefs(ctx).edit().putString("salt", salt).putString("hash", hash(pin, salt)).apply()
    }

    fun check(ctx: Context, pin: String): Boolean {
        val p = prefs(ctx)
        val salt = p.getString("salt", null) ?: return false
        return p.getString("hash", null) == hash(pin, salt)
    }
}

/** mode: "create" (primera vez), "verify" (pedir clave) o "change" (nueva clave). */
@Composable
fun PinDialog(mode: String, onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val ctx = LocalContext.current
    var pin by remember { mutableStateOf("") }
    var pin2 by remember { mutableStateOf("") }
    var err by remember { mutableStateOf("") }
    val creating = mode != "verify"
    val title = when (mode) {
        "create" -> "Crea una clave"
        "change" -> "Nueva clave"
        else -> "Ingresa tu clave"
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it.filter { c -> c.isDigit() }.take(12) },
                    label = { Text(if (creating) "Clave (mínimo 4 números)" else "Clave") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                if (creating) {
                    OutlinedTextField(
                        value = pin2,
                        onValueChange = { pin2 = it.filter { c -> c.isDigit() }.take(12) },
                        label = { Text("Repite la clave") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (err.isNotEmpty()) Text(err, color = Color(0xFFFF453A))
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (creating) {
                    if (pin.length < 4) {
                        err = "Usa al menos 4 números."
                    } else if (pin != pin2) {
                        err = "Las claves no coinciden."
                    } else {
                        PinStore.setPin(ctx, pin)
                        onSuccess()
                    }
                } else {
                    if (PinStore.check(ctx, pin)) {
                        onSuccess()
                    } else {
                        err = "Clave incorrecta."
                        pin = ""
                    }
                }
            }) { Text(if (creating) "Guardar" else "Entrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}
