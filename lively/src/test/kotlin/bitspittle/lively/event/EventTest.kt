package bitspittle.lively.event

import bitspittle.truthish.assertThat
import org.junit.Test

class EventTest {
    @Test
    fun canAddAndRemoveListeners() {
        run {
            val event = MutableEvent<String>()
            var count1 = 0
            val listener1: EventListener<String> = { count1++ }

            var removeAfterNextTime = false
            var count2 = 0
            val listener2: EventListener<String> = {
                ++count2
                if (removeAfterNextTime) {
                    removeThisListener()
                }
            }

            event += listener1
            event += listener2

            assertThat(count1).isEqualTo(0)
            assertThat(count2).isEqualTo(0)

            event("dummy1")
            event("dummy2")
            assertThat(count1).isEqualTo(2)
            assertThat(count2).isEqualTo(2)

            event -= listener1
            event("dummy3")
            event("dummy4")
            assertThat(count1).isEqualTo(2)
            assertThat(count2).isEqualTo(4)

            removeAfterNextTime = true
            event("dummy5")
            event("dummy6")
            assertThat(count1).isEqualTo(2)
            assertThat(count2).isEqualTo(5)
        }

        run {
//            val unitEvent = MutableUnitEvent()
//            var count = 0
//            val listener: () -> Unit = { count++ }
//            unitEvent += listener
//
//            assertThat(count).isEqualTo(0)
//            unitEvent()
//            unitEvent()
//            assertThat(count).isEqualTo(2)
//
//            unitEvent -= listener
//            unitEvent()
//            unitEvent()
//            assertThat(count).isEqualTo(2)
            val unitEvent = MutableUnitEvent()
            var count1 = 0
            val listener1: UnitEventListener = { count1++ }

            var removeAfterNextTime = false
            var count2 = 0
            val listener2: UnitEventListener = {
                ++count2
                if (removeAfterNextTime) {
                    removeThisListener()
                }
            }

            unitEvent += listener1
            unitEvent += listener2

            assertThat(count1).isEqualTo(0)
            assertThat(count2).isEqualTo(0)

            unitEvent()
            unitEvent()
            assertThat(count1).isEqualTo(2)
            assertThat(count2).isEqualTo(2)

            unitEvent -= listener1
            unitEvent()
            unitEvent()
            assertThat(count1).isEqualTo(2)
            assertThat(count2).isEqualTo(4)

            removeAfterNextTime = true
            unitEvent()
            unitEvent()
            assertThat(count1).isEqualTo(2)
            assertThat(count2).isEqualTo(5)
        }
    }
}