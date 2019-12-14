package bitspittle.lively.internal

import bitspittle.truthish.assertThat
import org.junit.Test

class LiveGraphTest {
    @Test
    fun eachThreadGetsItsOwnLiveGraph() {
        var liveGraph1: LiveGraph? = null
        var liveGraph2: LiveGraph? = null

        val threads = listOf(
            Thread { liveGraph1 = LiveGraph.instance },
            Thread { liveGraph2 = LiveGraph.instance }
        )

        threads.forEach { thread -> thread.start() }
        threads.forEach { thread -> thread.join() }

        assertThat(liveGraph1!!).isNotSameAs(liveGraph2!!)
    }
}