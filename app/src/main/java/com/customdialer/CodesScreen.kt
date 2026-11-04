package com.customdialer

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun CodesScreen(vm: DialerViewModel, onBack: () -> Unit, onChangePin: () -> Unit) {
    val codes by vm.codes.collectAsState()
    var code by remember { mutableStateOf("") }
    var uri by remember { mutableStateOf<Uri?>(null) }
    var err by remember { mutableStateOf("") }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) {
        if (it != null) { uri = it; err = "" }
    }

    Column(Modifier.fillMaxSize()) {
        IconButton(onClick = onBack, Modifier.padding(start = 6.dp, top = 6.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver")
        }
        Text(
            "Códigos secretos", fontSize = 28.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        TextButton(onClick = onChangePin, Modifier.padding(start = 12.dp)) { Text("Cambiar clave") }
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter { c -> c.isDigit() || c == '*' || c == '#' } },
                label = { Text("Código, por ejemplo *#1234#") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedButton(onClick = { pick.launch("image/*") }, Modifier.fillMaxWidth()) {
                Text(if (uri == null) "Elegir imagen" else "Imagen elegida · Cambiar")
            }
            if (err.isNotEmpty()) Text(err, color = Color(0xFFFF453A), fontSize = 13.sp)
            Button(
                onClick = {
                    val u = uri
                    when {
                        code.length !in 2..20 -> err = "Usa de 2 a 20 caracteres: números, * o #."
                        u == null -> err = "Elige una imagen para este código."
                        else -> vm.addCode(code, u) { e ->
                            err = e ?: ""
                            if (e == null) { code = ""; uri = null }
                        }
                    }
                },
                Modifier.fillMaxWidth()
            ) { Text("Guardar código") }
        }
        if (codes.isEmpty()) {
            Text(
                "Aún no tienes códigos. Escribe uno, elige una imagen y guárdalo.",
                Modifier.padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(codes) { c ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FileImage(c.imagePath, Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)))
                    Column(Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(c.code, fontSize = 20.sp)
                        Text("Muestra una imagen", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TextButton(onClick = { vm.deleteCode(c) }) { Text("Eliminar", color = Color(0xFFFF453A)) }
                }
            }
        }
    }
}

@Composable
fun RevealDialog(code: SecretCode, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            Modifier.fillMaxSize().background(Color.Black).systemBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            FileImage(
                code.imagePath,
                Modifier.fillMaxWidth().weight(1f).padding(20.dp),
                ContentScale.Fit
            )
            Text(code.code, color = Color(0xFFAAB3BD), fontSize = 16.sp, modifier = Modifier.padding(8.dp))
            Button(onClick = onClose, Modifier.padding(bottom = 24.dp)) { Text("Cerrar") }
        }
    }
}
