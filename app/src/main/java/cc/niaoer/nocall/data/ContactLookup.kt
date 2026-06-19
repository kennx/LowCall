package cc.niaoer.nocall.data

import android.content.Context
import android.provider.ContactsContract

fun isInContacts(context: Context, phoneNumber: String): Boolean {
    val normalized = normalizePhone(phoneNumber)
    if (normalized.isBlank()) return false
    val tail = normalized.takeLast(minOf(normalized.length, 11))
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
        "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?",
        arrayOf("%$tail"),
        null
    )?.use { cursor ->
        while (cursor.moveToNext()) {
            val stored = cursor.getString(0) ?: continue
            if (normalizePhone(stored).endsWith(tail)) return true
        }
    }
    return false
}
