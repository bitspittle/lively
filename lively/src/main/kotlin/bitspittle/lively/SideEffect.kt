package bitspittle.lively

/**
 * A value produced by [Lively.observe]. This represents a general block of logic which runs as the
 * result of one or more target [Live] values changing.
 *
 * Unlike an [ObservingLive], a side effect doesn't produce a value. Its purpose is to run
 * some endpoint operations that happen as a result of live values changing.
 *
 * In graph terms, it can be thought of as always being a sink (exactly as [SourceLive] is always
 * a source).
 */
class SideEffect(private val wrapped: FreezableLive<*>) {
    fun freeze() {
        wrapped.freeze()
    }
}