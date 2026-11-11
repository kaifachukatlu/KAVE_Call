package com.kaif.mastercaller.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.CallLog
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

data class PhoneNumber(val number: String, val label: String)

data class PhoneContact(
    val id: Long,
    val name: String,
    val photoUri: Uri?,
    val starred: Boolean,
    val numbers: List<PhoneNumber>,
    val lookupKey: String? = null,
    val fullPhotoUri: Uri? = null,
) {
    /** Stable link to this contact (survives syncs); used to open Android's contact editor. */
    val contactUri: Uri
        get() = if (lookupKey != null) ContactsContract.Contacts.getLookupUri(id, lookupKey)
        else android.content.ContentUris.withAppendedId(ContactsContract.Contacts.CONTENT_URI, id)
}

enum class CallKind { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, OTHER }

data class RecentCall(
    val id: Long,
    val number: String,
    val name: String?,
    val photoUri: Uri?,
    val kind: CallKind,
    val timeMillis: Long,
    val durationSec: Long,
)

/** Read-only access to the phone's contacts and call history (same data Google Phone shows). */
class PhoneDirectory(context: Context) {

    private val appContext = context.applicationContext

    fun canReadContacts() = granted(Manifest.permission.READ_CONTACTS)

    fun canReadCallLog() = granted(Manifest.permission.READ_CALL_LOG)

    /** All contacts that have a phone number, alphabetical, one entry per person. */
    fun loadContacts(): List<PhoneContact> {
        if (!canReadContacts()) return emptyList()
        val byId = LinkedHashMap<Long, PhoneContact>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.TYPE,
            ContactsContract.CommonDataKinds.Phone.LABEL,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.STARRED,
            ContactsContract.CommonDataKinds.Phone.LOOKUP_KEY,
            ContactsContract.CommonDataKinds.Phone.PHOTO_URI,
        )
        try {
            appContext.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projection, null, null,
                "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val name = c.getString(1)?.trim().orEmpty()
                    val number = c.getString(2)?.trim().orEmpty()
                    if (name.isEmpty() || number.isEmpty()) continue
                    val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(
                        appContext.resources, c.getInt(3), c.getString(4),
                    ).toString()
                    val existing = byId[id]
                    val normalized = number.filter { it.isDigit() || it == '+' }
                    if (existing == null) {
                        byId[id] = PhoneContact(
                            id, name, c.getString(5)?.let(Uri::parse), c.getInt(6) == 1, listOf(PhoneNumber(number, label)),
                            lookupKey = c.getString(7), fullPhotoUri = c.getString(8)?.let(Uri::parse),
                        )
                    } else if (existing.numbers.none { it.number.filter { ch -> ch.isDigit() || ch == '+' } == normalized }) {
                        byId[id] = existing.copy(numbers = existing.numbers + PhoneNumber(number, label))
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Contacts load failed", e)
        }
        return byId.values.toList()
    }

    /** Most recent calls first (Android's call log, written by the system for every call). */
    fun loadRecents(limit: Int = 300): List<RecentCall> {
        if (!canReadCallLog()) return emptyList()
        val out = ArrayList<RecentCall>()
        val projection = arrayOf(
            CallLog.Calls._ID, CallLog.Calls.NUMBER, CallLog.Calls.CACHED_NAME, CallLog.Calls.CACHED_PHOTO_URI,
            CallLog.Calls.TYPE, CallLog.Calls.DATE, CallLog.Calls.DURATION,
        )
        try {
            appContext.contentResolver.query(
                CallLog.Calls.CONTENT_URI, projection, null, null, "${CallLog.Calls.DATE} DESC",
            )?.use { c ->
                while (c.moveToNext() && out.size < limit) {
                    val kind = when (c.getInt(4)) {
                        CallLog.Calls.INCOMING_TYPE -> CallKind.INCOMING
                        CallLog.Calls.OUTGOING_TYPE -> CallKind.OUTGOING
                        CallLog.Calls.MISSED_TYPE -> CallKind.MISSED
                        CallLog.Calls.REJECTED_TYPE -> CallKind.REJECTED
                        CallLog.Calls.BLOCKED_TYPE -> CallKind.BLOCKED
                        else -> CallKind.OTHER
                    }
                    out += RecentCall(
                        id = c.getLong(0),
                        number = c.getString(1).orEmpty(),
                        name = c.getString(2)?.takeIf { it.isNotBlank() },
                        photoUri = c.getString(3)?.let(Uri::parse),
                        kind = kind,
                        timeMillis = c.getLong(5),
                        durationSec = c.getLong(6),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Call log load failed", e)
        }
        // Android only fills cached names for the stock dialer, so look missing ones up in Contacts.
        if (canReadContacts()) {
            val cache = HashMap<String, Pair<String, Uri?>?>()
            for (i in out.indices) {
                val call = out[i]
                if (call.name != null || call.number.isBlank()) continue
                val hit = cache.getOrPut(call.number) { lookupName(call.number) } ?: continue
                out[i] = call.copy(name = hit.first, photoUri = call.photoUri ?: hit.second)
            }
        }
        return out
    }

    private fun lookupName(number: String): Pair<String, Uri?>? = try {
        appContext.contentResolver.query(
            Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number)),
            arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME, ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI),
            null, null, null,
        )?.use { c -> if (c.moveToFirst()) c.getString(0)?.let { it to c.getString(1)?.let(Uri::parse) } else null }
    } catch (e: Exception) {
        null
    }

    private fun granted(p: String) =
        ContextCompat.checkSelfPermission(appContext, p) == PackageManager.PERMISSION_GRANTED

    private companion object {
        const val TAG = "PhoneDirectory"
    }
}
