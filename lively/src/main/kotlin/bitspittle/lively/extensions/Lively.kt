package bitspittle.lively.extensions

import bitspittle.lively.Lively

fun Lively.sourceBool(value: Boolean = false) = source(value)
fun Lively.sourceByte(value: Byte = 0) = source(value)
fun Lively.sourceShort(value: Short = 0) = source(value)
fun Lively.sourceInt(value: Int = 0) = source(value)
fun Lively.sourceLong(value: Long = 0) = source(value)
fun Lively.sourceFloat(value: Float = 0f) = source(value)
fun Lively.sourceDouble(value: Double = 0.0) = source(value)
fun Lively.sourceString(value: String = "") = source(value)
fun <T: Any> Lively.sourceNullable(value: T? = null) = source(value)
