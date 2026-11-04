package com.customdialer

import android.app.Application
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max

class DialerViewModel(app: Application) : AndroidViewModel(app) {
    private val db = AppDb.get(app)

    val contacts = MutableStateFlow<List<Contact>>(emptyList())
    val recents = MutableStateFlow<List<CallEntry>>(emptyList())
    val codes = db.codes().all()
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    var dial by mutableStateOf("")
    var keypadOpen by mutableStateOf(false)
    var revealed by mutableStateOf<SecretCode?>(null)

    fun refresh() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            contacts.value = withContext(Dispatchers.IO) { Repo.contacts(ctx) }
            recents.value = withContext(Dispatchers.IO) { Repo.recents(ctx) }
        }
    }

    fun clearRecents() {
        val ctx = getApplication<Application>()
        viewModelScope.launch {
            withContext(Dispatchers.IO) { Repo.clearRecents(ctx) }
            recents.value = emptyList()
        }
    }

    fun press(k: String) {
        dial += k
        if (k == "#") checkCode()
    }

    fun backspace() { dial = dial.dropLast(1) }

    private fun checkCode(): Boolean {
        val c = codes.value.firstOrNull { it.code == dial } ?: return false
        revealed = c
        dial = ""
        return true
    }

    fun call(number: String) = CallActions.place(getApplication(), number)

    fun callFromKeypad() {
        if (checkCode()) return
        if (dial.isBlank()) {
            recents.value.firstOrNull()?.let { dial = it.number }
            return
        }
        val n = dial
        dial = ""
        keypadOpen = false
        call(n)
    }

    /** Guarda la imagen reducida en el almacenamiento de la app y registra el código. */
    fun addCode(code: String, src: Uri, onDone: (String?) -> Unit) {
        viewModelScope.launch {
            val err = withContext(Dispatchers.IO) {
                try {
                    val bmp = decode(src) ?: return@withContext "No se pudo leer la imagen."
                    val dir = File(getApplication<Application>().filesDir, "codes").apply { mkdirs() }
                    val f = File(dir, "${System.nanoTime()}.jpg")
                    FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.JPEG, 85, it) }
                    db.codes().find(code)?.let { File(it.imagePath).delete() }
                    db.codes().upsert(SecretCode(code, f.path))
                    null
                } catch (e: Exception) {
                    "No se pudo guardar la imagen."
                }
            }
            onDone(err)
        }
    }

    fun deleteCode(c: SecretCode) {
        viewModelScope.launch(Dispatchers.IO) {
            File(c.imagePath).delete()
            db.codes().delete(c)
        }
    }

    private fun decode(uri: Uri): Bitmap? {
        val cr = getApplication<Application>().contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > 1400) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
    }
}
