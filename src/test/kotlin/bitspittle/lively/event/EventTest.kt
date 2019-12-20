package bitspittle.lively.event

import bitspittle.truthish.assertThat
import org.junit.Test

class EventTest {
    @Test
    fun canAddAndRemoveListeners() {
        run {
            val event = MutableEvent<String>()
            var count = 0
            val listener: (String) -> Unit = { count++ }
            event += listener

            assertThat(count).isEqualTo(0)
            event("dummy1")
            event("dummy2")
            assertThat(count).isEqualTo(2)

            event -= listener
            event("dummy3")
            event("dummy4")
            assertThat(count).isEqualTo(2)
        }

        run {
            val unitEvent = MutableUnitEvent()
            var count = 0
            val listener: () -> Unit = { count++ }
            unitEvent += listener

            assertThat(count).isEqualTo(0)
            unitEvent()
            unitEvent()
            assertThat(count).isEqualTo(2)

            unitEvent -= listener
            unitEvent()
            unitEvent()
            assertThat(count).isEqualTo(2)
        }
    }
}