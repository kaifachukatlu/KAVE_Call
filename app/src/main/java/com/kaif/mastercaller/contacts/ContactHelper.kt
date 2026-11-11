package com.kaif.mastercaller.contacts

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract
import android.util.Log
import androidx.core.content.ContextCompat

/** A caller found in the phone's saved contacts. Only real stored data, never guessed. */
data class ContactMatch(
    val name: String,
    val photoUri: Uri?,
    val ringtoneUri: Uri?,
    /** Full-resolution contact photo, used as the call-screen wallpaper. */
    val fullPhotoUri: Uri? = null,
    /** Stable contact id (survives edits/syncs); keys app-only wallpapers. */
    val lookupKey: String? = null,
)

/** Resolves phone numbers against the local contacts database via ContactsContract.PhoneLookup. */
class ContactHelper(context: Context) {

    private val appContext = context.applicationContext

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Returns the saved contact for [number], or null if unknown / no permission / lookup failed. */
    fun lookup(number: String?): ContactMatch? {
        if (number.isNullOrBlank() || !hasPermission()) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(
            ContactsContract.PhoneLookup.DISPLAY_NAME,
            ContactsContract.PhoneLookup.PHOTO_THUMBNAIL_URI,
            ContactsContract.PhoneLookup.CUSTOM_RINGTONE,
            ContactsContract.PhoneLookup.PHOTO_URI,
            ContactsContract.PhoneLookup.LOOKUP_KEY,
        )
        return try {
            appContext.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (!c.moveToFirst()) return@use null
                val name = c.getString(0)?.trim()
                if (name.isNullOrEmpty()) return@use null
                ContactMatch(
                    name = name,
                    photoUri = c.getString(1)?.let { Uri.parse(it) },
                    ringtoneUri = c.getString(2)?.let { Uri.parse(it) },
                    fullPhotoUri = c.getString(3)?.let { Uri.parse(it) },
                    lookupKey = c.getString(4),
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Contact lookup failed", e)
            null
        }
    }

    fun canWriteContacts(): Boolean =
        ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Saves [jpeg] as the contact's photo in the phone's contacts database, so Google Phone, Contacts and
     * (for Google-synced contacts) your Google account show it too. Writes to the contact's Google account
     * entry when it has one, otherwise to the phone-local entry; never to WhatsApp/Telegram-style entries.
     * Call off the main thread.
     */
    fun writeContactPhoto(lookupKey: String, jpeg: java.io.File): Boolean {
        if (!canWriteContacts() || !jpeg.exists()) return false
        return try {
            val resolver = appContext.contentResolver
            val contactUri = ContactsContract.Contacts.lookupContact(
                resolver, Uri.withAppendedPath(ContactsContract.Contacts.CONTENT_LOOKUP_URI, lookupKey),
            ) ?: return false
            val contactId = android.content.ContentUris.parseId(contactUri)
            val rawId = pickWritableRawContact(contactId) ?: return false
            val photoUri = Uri.withAppendedPath(
                android.content.ContentUris.withAppendedId(ContactsContract.RawContacts.CONTENT_URI, rawId),
                ContactsContract.RawContacts.DisplayPhoto.CONTENT_DIRECTORY,
            )
            resolver.openAssetFileDescriptor(photoUri, "rw")?.use { fd ->
                fd.createOutputStream().use { out -> jpeg.inputStream().use { it.copyTo(out) } }
            } ?: return false
            true
        } catch (e: Exception) {
            Log.w(TAG, "Writing contact photo failed", e)
            false
        }
    }

    private fun pickWritableRawContact(contactId: Long): Long? {
        val skip = listOf("whatsapp", "telegram", "signal", "viber", "skype", "facebook", "duo", "meet")
        val rows = ArrayList<Pair<Long, String>>()
        appContext.contentResolver.query(
            ContactsContract.RawContacts.CONTENT_URI,
            arrayOf(ContactsContract.RawContacts._ID, ContactsContract.RawContacts.ACCOUNT_TYPE),
            "${ContactsContract.RawContacts.CONTACT_ID}=? AND ${ContactsContract.RawContacts.DELETED}=0",
            arrayOf(contactId.toString()),
            null,
        )?.use { c -> while (c.moveToNext()) rows += c.getLong(0) to (c.getString(1) ?: "") }
        val usable = rows.filter { (_, type) -> skip.none { type.contains(it, ignoreCase = true) } }
        return (usable.firstOrNull { it.second == "com.google" } ?: usable.firstOrNull())?.first
    }

    /** Lookup key + display name for a contact returned by the system contact picker. */
    fun pickedContact(contactUri: Uri): Pair<String, String>? = try {
        val projection = arrayOf(ContactsContract.Contacts.LOOKUP_KEY, ContactsContract.Contacts.DISPLAY_NAME)
        appContext.contentResolver.query(contactUri, projection, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val key = c.getString(0) ?: return@use null
            key to (c.getString(1)?.takeIf { it.isNotBlank() } ?: "Contact")
        }
    } catch (e: Exception) {
        Log.w(TAG, "Picked contact read failed", e)
        null
    }

    /** Decodes a contact photo, downsampled so the longest side is at most [maxSide] px. */
    fun loadPhoto(uri: Uri, maxSide: Int = 1440): Bitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        appContext.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        appContext.contentResolver.openInputStream(uri)?.use { stream -> BitmapFactory.decodeStream(stream, null, opts) }
    } catch (e: Exception) {
        Log.w(TAG, "Photo load failed", e)
        null
    }

    private companion object {
        const val TAG = "ContactHelper"
    }
}
