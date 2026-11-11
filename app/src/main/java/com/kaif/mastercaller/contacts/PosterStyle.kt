package com.kaif.mastercaller.contacts

/** Font choices for the big caller name on the poster. */
enum class PosterFont(val label: String) { MODERN("Modern"), CLASSIC("Classic"), ELEGANT("Elegant"), MONO("Mono") }

/** Name colours (ARGB). */
enum class PosterColor(val label: String, val argb: Long) {
    WHITE("Ivory", 0xFFF1EDE3),
    CHAMPAGNE("Champagne", 0xFFB9A27B),
    SOFT_CHAMPAGNE("Soft champagne", 0xFFD3C2A2),
    PURE("White", 0xFFFFFFFF),
    SAGE("Sage", 0xFF9EBE9C),
    CLAY("Clay", 0xFFC77B73),
    OBSIDIAN("Obsidian", 0xFF151718),
}

/**
 * iPhone-style "contact poster" settings for a call wallpaper.
 * [depth] = the caller's name is drawn behind the person in the photo.
 */
data class PosterStyle(
    val depth: Boolean = true,
    val font: PosterFont = PosterFont.MODERN,
    val color: PosterColor = PosterColor.WHITE,
) {
    fun encode(): String = "${if (depth) 1 else 0}|${font.name}|${color.name}"

    companion object {
        fun decode(raw: String?): PosterStyle {
            if (raw.isNullOrBlank()) return PosterStyle()
            val p = raw.split('|')
            return PosterStyle(
                depth = p.getOrNull(0) != "0",
                font = PosterFont.entries.firstOrNull { it.name == p.getOrNull(1) } ?: PosterFont.MODERN,
                color = PosterColor.entries.firstOrNull { it.name == p.getOrNull(2) } ?: PosterColor.WHITE,
            )
        }
    }
}
