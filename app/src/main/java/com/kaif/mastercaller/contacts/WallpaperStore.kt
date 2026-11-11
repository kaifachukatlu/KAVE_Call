package com.kaif.mastercaller.contacts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.Properties

/** A call wallpaper chosen inside Master Caller AI for one contact. */
data class AppWallpaper(val lookupKey: String, val contactName: String, val uri: Uri)

/**
 * Call-screen wallpapers that live only inside this app (private storage). Your Contacts app is never changed.
 *
 * Priority on a call: this app's photo for that contact -> the contact's own photo -> this app's default
 * wallpaper -> the built-in background.
 */
class WallpaperStore(context: Context) {

    private val dir = File(context.applicationContext.filesDir, "wallpapers").apply { mkdirs() }
    private val indexFile = File(dir, "index.properties")
    private val resolver = context.applicationContext.contentResolver

    private val _entries = MutableStateFlow<List<AppWallpaper>>(emptyList())
    val entries: StateFlow<List<AppWallpaper>> = _entries.asStateFlow()

    private val _default = MutableStateFlow<Uri?>(null)
    val defaultWallpaper: StateFlow<Uri?> = _default.asStateFlow()

    init {
        refresh()
    }

    /** App-only wallpaper for a contact (by its stable lookup key), if one was set. */
    fun forContact(lookupKey: String?): Uri? {
        if (lookupKey.isNullOrBlank()) return null
        return fileFor(lookupKey).takeIf { it.exists() }?.let { Uri.fromFile(it) }
    }

    fun default(): Uri? = defaultFile().takeIf { it.exists() }?.let { Uri.fromFile(it) }

    /** Copies and downsizes the picked image into private storage. Call off the main thread. */
    fun setForContact(lookupKey: String, contactName: String, source: Uri): Boolean {
        if (!saveImage(source, fileFor(lookupKey))) return false
        val index = loadIndex()
        index.setProperty(lookupKey, contactName)
        saveIndex(index)
        refresh()
        return true
    }

    fun removeForContact(lookupKey: String) {
        fileFor(lookupKey).delete()
        val index = loadIndex()
        index.remove(lookupKey)
        saveIndex(index)
        refresh()
    }

    fun setDefault(source: Uri): Boolean {
        val ok = saveImage(source, defaultFile())
        refresh()
        return ok
    }

    fun removeDefault() {
        defaultFile().delete()
        refresh()
    }

    // --- internals -----------------------------------------------------------------------------

    private fun refresh() {
        val index = loadIndex()
        _entries.value = index.stringPropertyNames()
            .mapNotNull { key -> forContact(key)?.let { AppWallpaper(key, index.getProperty(key), versioned(it)) } }
            .sortedBy { it.contactName.lowercase() }
        _default.value = default()?.let { versioned(it) }
    }

    /** Adds the file's timestamp so image views reload after a photo is replaced. */
    private fun versioned(uri: Uri): Uri {
        val f = File(uri.path ?: return uri)
        return uri.buildUpon().appendQueryParameter("v", f.lastModified().toString()).build()
    }

    private fun fileFor(lookupKey: String) = File(dir, "c_" + lookupKey.hashCode().toUInt().toString(16) + ".jpg")

    private fun defaultFile() = File(dir, "default.jpg")

    private fun saveImage(source: Uri, target: File): Boolean = try {
        // ImageDecoder applies EXIF rotation, so phone-camera photos come out the right way up.
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(resolver, source)) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val scale = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
        }
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        tmp.renameTo(target) || (tmp.copyTo(target, overwrite = true).exists() && tmp.delete())
    } catch (e: Exception) {
        Log.w(TAG, "Could not save wallpaper", e)
        false
    }

    private fun loadIndex(): Properties = Properties().apply {
        if (indexFile.exists()) indexFile.inputStream().use { load(it) }
    }

    private fun saveIndex(index: Properties) {
        indexFile.outputStream().use { index.store(it, "Master Caller AI wallpapers") }
    }

    private companion object {
        const val TAG = "WallpaperStore"
        const val MAX_SIDE = 1600
    }
}
