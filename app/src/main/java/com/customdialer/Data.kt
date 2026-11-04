package com.customdialer

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.CallLog
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

data class Contact(
    val id: Long,
    val name: String,
    val number: String,
    val photoUri: String?,
    val starred: Boolean
)

data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val type: Int,
    val date: Long,
    val duration: Long
)

/** Lee contactos y registro de llamadas reales del teléfono. */
object Repo {

    fun contacts(ctx: Context): List<Contact> {
        val out = mutableListOf<Contact>()
        val proj = arrayOf(
            Phone.CONTACT_ID, Phone.DISPLAY_NAME, Phone.NUMBER,
            Phone.PHOTO_THUMBNAIL_URI, Phone.STARRED
        )
        try {
            ctx.contentResolver.query(
                Phone.CONTENT_URI, proj, null, null,
                "${Phone.DISPLAY_NAME} COLLATE LOCALIZED ASC"
            )?.use { c ->
                val seen = HashSet<String>()
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val num = c.getString(2) ?: continue
                    if (!seen.add("$id|${num.filter { it.isDigit() }}")) continue
                    out += Contact(id, c.getString(1) ?: num, num, c.getString(3), c.getInt(4) == 1)
                }
            }
        } catch (_: SecurityException) {
        }
        return out
    }

    fun recents(ctx: Context): List<CallEntry> {
        val out = mutableListOf<CallEntry>()
        val args = Bundle().apply {
            putInt(ContentResolver.QUERY_ARG_LIMIT, 150)
            putStringArray(ContentResolver.QUERY_ARG_SORT_COLUMNS, arrayOf(CallLog.Calls.DATE))
            putInt(
                ContentResolver.QUERY_ARG_SORT_DIRECTION,
                ContentResolver.QUERY_SORT_DIRECTION_DESCENDING
            )
        }
        val proj = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION
        )
        try {
            ctx.contentResolver.query(CallLog.Calls.CONTENT_URI, proj, args, null)?.use { c ->
                while (c.moveToNext()) {
                    out += CallEntry(
                        c.getLong(0), c.getString(1) ?: "", c.getString(2),
                        c.getInt(3), c.getLong(4), c.getLong(5)
                    )
                }
            }
        } catch (_: SecurityException) {
        }
        return out
    }

    fun clearRecents(ctx: Context) {
        try {
            ctx.contentResolver.delete(CallLog.Calls.CONTENT_URI, null, null)
        } catch (_: SecurityException) {
        }
    }

    fun lookupName(ctx: Context, number: String): String? {
        if (number.isBlank()) return null
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)
            )
            ctx.contentResolver.query(
                uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null
            )?.use { if (it.moveToFirst()) it.getString(0) else null }
        } catch (_: Exception) {
            null
        }
    }

    fun loadBitmap(ctx: Context, uri: String): Bitmap? = try {
        ctx.contentResolver.openInputStream(Uri.parse(uri))?.use { BitmapFactory.decodeStream(it) }
    } catch (_: Exception) {
        null
    }

    fun sameNumber(a: String, b: String): Boolean {
        val da = a.filter { it.isDigit() }
        val db = b.filter { it.isDigit() }
        if (da.isEmpty() || db.isEmpty()) return false
        return da.takeLast(9) == db.takeLast(9)
    }
}

/** Códigos secretos: un código marcado muestra una imagen. */
@Entity(tableName = "codes")
data class SecretCode(@PrimaryKey val code: String, val imagePath: String)

@Dao
interface CodeDao {
    @Query("SELECT * FROM codes ORDER BY code")
    fun all(): Flow<List<SecretCode>>

    @Query("SELECT * FROM codes WHERE code = :code")
    suspend fun find(code: String): SecretCode?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(c: SecretCode)

    @Delete
    suspend fun delete(c: SecretCode)
}

@Database(entities = [SecretCode::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun codes(): CodeDao

    companion object {
        @Volatile
        private var instance: AppDb? = null

        fun get(ctx: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(ctx.applicationContext, AppDb::class.java, "dialer.db")
                .build().also { instance = it }
        }
    }
}
