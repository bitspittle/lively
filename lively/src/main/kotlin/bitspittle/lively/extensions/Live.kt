package bitspittle.lively.extensions

import bitspittle.lively.SourceLive

/**
 * Bind this [SourceLive] to a target [SourceLive], meaning that when you change the value of one,
 * the other one is kept in sync. After binding, this live will be initialized to the value of the
 * target live.
 *
 * Once bound, they remain bound until one or the other is frozen.
 *
 * Logic for converting between the types must be provided. That said, the logic for type
 * conversion may not always succeed, e.g. "123a" should fail to convert to an Int, so these two
 * live values, though bound, can temporarily get out of sync.
 *
 * If you just want to bind two identically typed live values to each other, see the other [bindTo]
 * method.
 *
 * @param convert1to2 A function which converts the first type to the second type, returning null
 * to indicate the conversion isn't possible.
 *
 * @param convert2to1 The reverse operation of [convert1to2]
 */
fun <T1, T2> SourceLive<T1>.bindTo(live2: SourceLive<T2>, convert1to2: (T1) -> T2?, convert2to1: (T2) -> T1?) {
    val live1 = this // For readabilty, e.g. live1 and live2
    live1.onValueChanged += { value1 ->
        if (live2.frozen) {
            removeThisListener()
        } else {
            convert1to2(value1)?.let { live2.set(it) }
        }
    }
    live2.onValueChanged += { value2 ->
        if (live1.frozen) {
            removeThisListener()
        } else {
            convert2to1(value2)?.let { live1.set(it) }
        }
    }

    convert2to1(live2.getSnapshot())?.let { live1.set(it) }
}

/**
 * Bind two like-typed live values together. Whenever one value changes, the other will be set to
 * it.
 *
 * This can be useful if you have a model class with, say, a live String field, and a UI with a
 * text input (with a live String wrapping it). Just bind the two together and, when done with the
 * UI dialog, freeze the UI-related live values.
 */
fun <T> SourceLive<T>.bindTo(live2: SourceLive<T>) {
    bindTo(live2, { it }, { it })
}
