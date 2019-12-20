package bitspittle.lively.thread

/**
 * Test that the current thread matches some target (i.e. this) thread.
 *
 * The [message] will be passed into a thrown exception if the threads don't match. It is lazily
 * instantiated to avoid string creation cost unless necessary. The message should end with
 * punctuation.
 */
fun Thread.expectCurrent(message: () -> String) {
    val currentThread = Thread.currentThread()
    if (this !== currentThread) {
        throw IllegalStateException(
            """
                $message

                Original thread: $this
                Current thread: $currentThread
            """.trimIndent())

    }
}