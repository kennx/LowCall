package cc.niaoer.nocall.data

fun normalizePhone(raw: String): String = raw.filter { it.isDigit() }
