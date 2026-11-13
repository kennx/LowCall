package cc.niaoer.lowcall.data

data class PhoneDisplay(val number: String, val extension: String? = null)

fun formatPhoneForDisplay(raw: String): PhoneDisplay {
    val trimmed = raw.trim()
    val extensionMatch = EXTENSION_REGEX.find(trimmed)
    val base = extensionMatch?.let { trimmed.substring(0, it.range.first).trim() } ?: trimmed
    val extension = extensionMatch?.groupValues?.get(1)

    if (base.count { it == '+' } > 1 || base.any { !it.isDigit() && it !in "+- ()." }) {
        return PhoneDisplay(trimmed)
    }

    val digits = base.filter { it.isDigit() }
    val number = when {
        digits.isEmpty() -> base
        base.startsWith('+') -> formatInternational(digits)
        else -> formatChineseNumber(digits) ?: digits
    }
    return PhoneDisplay(number = number, extension = extension)
}

private fun formatInternational(digits: String): String {
    val countryCode = COUNTRY_CODES.firstOrNull { digits.startsWith(it) }
        ?: return "+${groupDigits(digits)}"
    val national = digits.removePrefix(countryCode)
    if (countryCode == "86") {
        formatChineseNumber(national)?.let { return "+86 $it" }
    }
    return "+$countryCode ${groupDigits(national)}"
}

private fun formatChineseNumber(digits: String): String? {
    if (digits.length == 11 && digits.startsWith('1')) {
        return "${digits.substring(0, 3)} ${digits.substring(3, 7)} ${digits.substring(7)}"
    }
    if (!digits.startsWith('0')) return null

    val areaLength = if (digits.drop(1).take(2) in SHORT_AREA_CODES) 3 else 4
    if (digits.length <= areaLength) return null
    val local = digits.substring(areaLength)
    if (local.length !in 7..8) return null
    val localFormatted = if (local.length == 7) {
        "${local.substring(0, 3)} ${local.substring(3)}"
    } else {
        "${local.substring(0, 4)} ${local.substring(4)}"
    }
    return "${digits.substring(0, areaLength)} $localFormatted"
}

private fun groupDigits(digits: String): String {
    val groups = mutableListOf<String>()
    var index = 0
    while (index < digits.length) {
        val remaining = digits.length - index
        val size = if (remaining == 4) 4 else minOf(3, remaining)
        groups.add(digits.substring(index, index + size))
        index += size
    }
    return groups.joinToString(" ")
}

fun formatLocationForDisplay(location: String): String {
    if (location.length % 2 != 0) return location
    val midpoint = location.length / 2
    return if (location.substring(0, midpoint) == location.substring(midpoint)) {
        location.substring(0, midpoint)
    } else {
        location
    }
}

private val EXTENSION_REGEX = Regex("(?:x|;|,|转)\\s*(\\d+)\\s*$", RegexOption.IGNORE_CASE)
private val SHORT_AREA_CODES = setOf("10") + (20..29).map { it.toString() }
private val COUNTRY_CODES = (
    "1 7 20 27 30 31 32 33 34 36 39 40 41 43 44 45 46 47 48 49 51 52 53 54 55 56 57 58 " +
        "60 61 62 63 64 65 66 81 82 84 86 90 91 92 93 94 95 98 211 212 213 216 218 220 " +
        "221 222 223 224 225 226 227 228 229 230 231 232 233 234 235 236 237 238 239 240 " +
        "241 242 243 244 245 246 247 248 249 250 251 252 253 254 255 256 257 258 260 261 " +
        "262 263 264 265 266 267 268 269 290 291 297 298 299 350 351 352 353 354 355 356 " +
        "357 358 359 370 371 372 373 374 375 376 377 378 380 381 382 383 385 386 387 389 " +
        "420 421 423 500 501 502 503 504 505 506 507 508 509 590 591 592 593 594 595 596 " +
        "597 598 599 670 672 673 674 675 676 677 678 679 680 681 682 683 685 686 687 688 " +
        "689 690 691 692 800 808 850 852 853 855 856 870 878 880 881 882 883 886 888 960 " +
        "961 962 963 964 965 966 967 968 970 971 972 973 974 975 976 977 979 992 993 994 " +
        "995 996 998"
    ).split(' ').toSet().sortedByDescending { it.length }
