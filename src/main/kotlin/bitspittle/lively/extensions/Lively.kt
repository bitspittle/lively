package bitspittle.lively.extensions

import bitspittle.lively.Lively

fun Lively.createBool(value: Boolean = false) = create(value)
fun Lively.createByte(value: Byte = 0) = create(value)
fun Lively.createShort(value: Short = 0) = create(value)
fun Lively.createInt(value: Int = 0) = create(value)
fun Lively.createLong(value: Long = 0) = create(value)
fun Lively.createFloat(value: Float = 0f) = create(value)
fun Lively.createDouble(value: Double = 0.0) = create(value)
fun Lively.createString(value: String = "") = create(value)
