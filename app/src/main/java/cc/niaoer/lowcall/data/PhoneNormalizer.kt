package cc.niaoer.lowcall.data

fun normalizePhone(raw: String): String = raw.filter { it.isDigit() }
