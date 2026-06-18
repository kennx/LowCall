package cc.niaoer.nocall.data

import android.content.Context
import android.provider.ContactsContract

class ContactLookup(private val context: Context) {
    fun isInContacts(phoneNumber: String): Boolean {
        val normalized = normalizePhone(phoneNumber)
        if (normalized.isBlank()) return false
        val tail = normalized.takeLast(minOf(normalized.length, 11))
        val cursor = context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
            "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
            arrayOf("%$tail"),
            null
        )
        return cursor?.use { c ->
            while (c.moveToNext()) {
                val stored = c.getString(0) ?: continue
                if (normalizePhone(stored).endsWith(tail)) return@use true
            }
            false
        } ?: false
    }
}
