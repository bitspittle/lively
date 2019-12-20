package bitspittle.lively

import bitspittle.lively.exec.ManualExecutor
import bitspittle.lively.graph.LiveGraph
import bitspittle.truthish.assertThat
import bitspittle.truthish.assertThrows
import org.junit.Test
import java.lang.IllegalArgumentException


/**
 * TODO: Header comment.
 */
class LiveTest {
    private val graphExecutor = ManualExecutor()
    private val testGraph = LiveGraph(graphExecutor)

    @Test
    fun setAndGetSnapshotWork() {
        val lively = Lively(testGraph)
        val liveInt = lively.create(123)
        assertThat(liveInt.getSnapshot()).isEqualTo(123)

        liveInt.set(456)
        assertThat(liveInt.getSnapshot()).isEqualTo(456)
    }

    @Test
    fun primitiveCreateMethodsWork() {
        val lively = Lively(testGraph)
        val liveByte: Live<Byte> = lively.createByte()
        val liveShort: Live<Short> = lively.createShort()
        val liveInt: Live<Int> = lively.createInt()
        val liveLong: Live<Long> = lively.createLong()
        val liveFloat: Live<Float> = lively.createFloat()
        val liveDouble: Live<Double> = lively.createDouble()
        val liveString: Live<String> = lively.createString()

        assertThat(liveByte.getSnapshot()).isEqualTo(0.toByte())
        assertThat(liveShort.getSnapshot()).isEqualTo(0.toShort())
        assertThat(liveInt.getSnapshot()).isEqualTo(0)
        assertThat(liveLong.getSnapshot()).isEqualTo(0.toLong())
        assertThat(liveFloat.getSnapshot()).isEqualTo(0.0f)
        assertThat(liveDouble.getSnapshot()).isEqualTo(0.0)
        assertThat(liveString.getSnapshot()).isEqualTo("")
    }

    @Test
    fun observeWorks() {
        val lively = Lively(testGraph)
        val liveInt = lively.create(123)
        val liveStr1 = lively.createString()
        assertThat(liveStr1.getSnapshot()).isEmpty()

        liveStr1.observe { liveInt.get().toString() }
        val liveStr2 = lively.create { liveInt.get().toString() }

        assertThat(liveStr1.getSnapshot()).isEqualTo("123")
        assertThat(liveStr2.getSnapshot()).isEqualTo("123")

        liveInt.set(456)
        assertThat(liveStr1.getSnapshot()).isEqualTo("123")
        assertThat(liveStr2.getSnapshot()).isEqualTo("123")

        graphExecutor.runNext() // liveStr1 updated first (because it was created first)
        assertThat(liveStr1.getSnapshot()).isEqualTo("456")
        assertThat(liveStr2.getSnapshot()).isEqualTo("123")

        liveInt.set(789)
        graphExecutor.runRemaining()
        assertThat(liveStr1.getSnapshot()).isEqualTo("789")
        assertThat(liveStr2.getSnapshot()).isEqualTo("789")
    }

    @Test
    fun cyclesNotAllowed() {
        val lively = Lively(testGraph)

        val liveInt1 = lively.createInt()
        val liveInt2 = lively.createInt()
        val liveInt3 = lively.createInt()

        liveInt3.observe { liveInt2.get() }
        liveInt2.observe { liveInt1.get() }

        assertThrows<IllegalArgumentException> {
            liveInt1.observe { liveInt3.get() }
        }

        // Clearing the observe callback clears the dependencies
        liveInt3.clearObserve()
        liveInt1.observe { liveInt3.get() }
    }

    @Test
    fun onValueChangedListenersWorkAsExpected() {
        val lively = Lively(testGraph)

        val liveStr = lively.create("987")
        val liveInt = lively.create { liveStr.get().toInt() }

        var strChanged = false
        var intChanged = false
        liveStr.onValueChanged += { strChanged = true }
        liveInt.onValueChanged += { intChanged = true }
        assertThat(strChanged).isFalse()
        assertThat(intChanged).isFalse()

        liveStr.set("987")
        // Setting to the same value is totally ignored
        assertThat(graphExecutor.count).isEqualTo(0)
        assertThat(strChanged).isFalse()
        assertThat(intChanged).isFalse()

        liveStr.set("123")
        assertThat(strChanged).isTrue()
        assertThat(intChanged).isFalse()

        graphExecutor.runRemaining()
        assertThat(intChanged).isTrue()
    }

    @Test
    fun dependenciesCanChange() {
        val lively = Lively(testGraph)

        val switch = lively.createBool()
        val intTrue = lively.createInt(1)
        val intFalse = lively.createInt(0)

        val finalInt = lively.create {
            if (switch.get()) intTrue.get() else intFalse.get()
        }

        assertThat(finalInt.getSnapshot()).isEqualTo(0)

        switch.set(true)
        graphExecutor.runRemaining()

        assertThat(finalInt.getSnapshot()).isEqualTo(1)

        // At this point, nothing should depend on `intFalse`
        intFalse.set(-1)
        assertThat(graphExecutor.count).isEqualTo(0)
        assertThat(finalInt.getSnapshot()).isEqualTo(1)

        switch.set(false)
        graphExecutor.runRemaining()
        assertThat(finalInt.getSnapshot()).isEqualTo(-1)
    }

    @Test
    fun cannotModifyLiveValueAfterFreezing() {
        val lively = Lively(testGraph)
        val liveStr = lively.createString()

        var wasFrozen = false
        liveStr.onFroze += { wasFrozen = true }

        assertThat(wasFrozen).isFalse()
        assertThat(liveStr.frozen).isFalse()

        liveStr.freeze()

        assertThat(wasFrozen).isTrue()
        assertThat(liveStr.frozen).isTrue()

        // Once frozen, a bunch of mutating functions cannot be called
        assertThrows<IllegalStateException> { liveStr.set("dummy") }
        assertThrows<IllegalStateException> { liveStr.observe { "dummy" } }
        assertThrows<IllegalStateException> { liveStr.clearObserve() }
        assertThrows<IllegalStateException> { liveStr.freeze() }

        // But you can safely query the snapshot
        assertThat(liveStr.getSnapshot()).isEmpty()
    }

    @Test
    fun safeToFreezeAValueBeforeItGetsUpdated() {
        val lively = Lively(testGraph)

        // A -> B -> C
        val liveStrC = lively.create("initial value")
        val liveStrB = lively.create { liveStrC.get() }
        val liveStrA = lively.create { liveStrB.get() }

        assertThat(liveStrA.getSnapshot()).isEqualTo("initial value")

        liveStrC.set("new value")
        liveStrA.freeze()

        graphExecutor.runRemaining()
        assertThat(liveStrA.getSnapshot()).isEqualTo("initial value")
    }

    @Test
    fun updateOnlyPropogatesIfValueChanged() {
        val lively = Lively(testGraph)

        val switch = lively.create(false)

        val dummyInt1 = lively.create(10)
        val dummyInt2 = lively.create(10)
        val middleInt = lively.create { if (switch.get()) dummyInt1.get() else dummyInt2.get() }
        val finalInt = lively.create { middleInt.get() }

        assertThat(finalInt.getSnapshot()).isEqualTo(10)

        var middleIntChanged = false
        var finalIntChanged = false
        middleInt.onValueChanged += { middleIntChanged = true }
        finalInt.onValueChanged += { finalIntChanged = true }

        switch.set(true)
        graphExecutor.runRemaining()
        assertThat(middleIntChanged).isFalse()
        assertThat(finalIntChanged).isFalse()

        // Sanity check that deps propagate later
        dummyInt1.set(100)
        graphExecutor.runRemaining()
        assertThat(middleIntChanged).isTrue()
        assertThat(finalIntChanged).isTrue()
    }

    @Test
    fun verifyIntermediateValuesAreOnlyUpdatedOnce() {
        val lively = Lively(testGraph)

        val int1 = lively.create(1)
        val int2 = lively.create(2)
        val int3 = lively.create(3)
        val sum = lively.create { int1.get() + int2.get() + int3.get() }

        var count = 0
        sum.onValueChanged += { ++count }

        assertThat(count).isEqualTo(0)
        assertThat(sum.getSnapshot()).isEqualTo(6)

        int1.set(100)
        int2.set(10)
        int3.set(1)

        graphExecutor.runRemaining()
        assertThat(count).isEqualTo(1)
        assertThat(sum.getSnapshot()).isEqualTo(111)
    }


    @Test
    fun liveCanWrapOtherValueTypes() {
        val lively = Lively(testGraph)

        // A UI label class looks something like this...
        class FakeLabel {
            val listeners = mutableListOf<(FakeLabel) -> Unit>()
            var text: String = ""
                set(value) {
                    if (value != field) {
                        field = value
                        listeners.forEach { it(this) }
                    }
                }
        }

        fun wrapLabel(label: FakeLabel): MutableLive<String> {
            val liveLabel = lively.create(label.text)
            val listener: (FakeLabel) -> Unit = { sender -> liveLabel.set(sender.text) }
            label.listeners.add(listener)
            liveLabel.onValueChanged += { text -> label.text = text }
            liveLabel.onFroze += { label.listeners.remove(listener) }

            return liveLabel
        }

        val sourceLabel = FakeLabel()
        sourceLabel.text = "Initial text"

        val liveLabel = wrapLabel(sourceLabel)
        assertThat(sourceLabel.listeners.size).isEqualTo(1)
        val allCaps = lively.create { liveLabel.get().toUpperCase() }
        assertThat(liveLabel.getSnapshot()).isEqualTo("Initial text")
        assertThat(allCaps.getSnapshot()).isEqualTo("INITIAL TEXT")

        sourceLabel.text = "New text"
        graphExecutor.runRemaining()
        assertThat(liveLabel.getSnapshot()).isEqualTo("New text")
        assertThat(allCaps.getSnapshot()).isEqualTo("NEW TEXT")

        liveLabel.set("Updated text")
        graphExecutor.runRemaining()
        assertThat(sourceLabel.text).isEqualTo("Updated text")
        assertThat(allCaps.getSnapshot()).isEqualTo("UPDATED TEXT")

        liveLabel.freeze()
        assertThat(sourceLabel.listeners.isEmpty())
    }
}