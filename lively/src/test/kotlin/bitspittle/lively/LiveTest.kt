package bitspittle.lively

import bitspittle.lively.exec.ManualExecutor
import bitspittle.lively.exec.RunImmediatelyExecutor
import bitspittle.lively.extensions.createBool
import bitspittle.lively.extensions.createInt
import bitspittle.lively.extensions.createString
import bitspittle.lively.graph.LiveGraph
import bitspittle.truthish.assertThat
import bitspittle.truthish.assertThrows
import org.junit.Test

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
    fun observeWorks() {
        val lively = Lively(testGraph)
        val liveInt = lively.create(123)
        val liveStr1 = lively.create { liveInt.get().toString() }
        assertThat(liveStr1.getSnapshot()).isEqualTo("123")

        val liveStr2 = lively.create { liveStr1.get().reversed() }
        assertThat(liveStr2.getSnapshot()).isEqualTo("321")

        liveInt.set(456)
        assertThat(liveStr1.getSnapshot()).isEqualTo("123")
        assertThat(liveStr2.getSnapshot()).isEqualTo("321")

        graphExecutor.runNext() // liveStr1 updated first (because it was created first)
        assertThat(liveStr1.getSnapshot()).isEqualTo("456")
        assertThat(liveStr2.getSnapshot()).isEqualTo("321")

        graphExecutor.runNext()
        assertThat(liveStr1.getSnapshot()).isEqualTo("456")
        assertThat(liveStr2.getSnapshot()).isEqualTo("654")

        liveInt.set(789)
        graphExecutor.runRemaining()
        assertThat(liveStr1.getSnapshot()).isEqualTo("789")
        assertThat(liveStr2.getSnapshot()).isEqualTo("987")
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
        val liveStr = lively.createString("initial")

        var wasFrozen = false
        liveStr.onFroze += { wasFrozen = true }

        assertThat(wasFrozen).isFalse()
        assertThat(liveStr.frozen).isFalse()

        liveStr.freeze()

        assertThat(wasFrozen).isTrue()
        assertThat(liveStr.frozen).isTrue()

        // Once frozen, many mutating functions cannot be called
        assertThrows<IllegalStateException> { liveStr.set("dummy") }
        assertThrows<IllegalStateException> { liveStr.freeze() }

        // But you can safely query the snapshot
        assertThat(liveStr.getSnapshot()).isEqualTo("initial")

        // You can add listeners to a frozen value, but it's a no-op
        liveStr.onValueChanged += {}
        liveStr.onFroze += {}
    }

    @Test
    fun youCanFreezeAValueBeforeItGetsUpdated() {
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
    fun observeBlockCanReferenceFrozenLiveValues() {
        val lively = Lively(testGraph)

        val toFreezeNow = lively.create(1).apply { freeze() }
        val toFreezeLater = lively.create(2)
        val neverFrozen = lively.create(3)

        val sum = lively.create { toFreezeNow.get() + toFreezeLater.get() + neverFrozen.get() }
        assertThat(sum.getSnapshot()).isEqualTo(6)

        toFreezeLater.freeze()
        neverFrozen.set(30)

        graphExecutor.runRemaining()
        assertThat(sum.getSnapshot()).isEqualTo(33)
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
    fun liveBehaviorWithObjectsWorks() {
        data class User(val name: String, val age: Int)

        val userJoe = User("Joe", 34)
        val userJane = User("Jane", 29)

        val lively = Lively(testGraph)
        val liveUser = lively.create(userJoe)
        val liveDisplay = lively.create { "Name: ${liveUser.get().name}" }

        var nameUpdatedCount = 0
        liveDisplay.onValueChanged += { ++nameUpdatedCount }

        assertThat(liveDisplay.getSnapshot()).isEqualTo("Name: Joe")
        assertThat(nameUpdatedCount).isEqualTo(0)

        liveUser.set(userJane)
        graphExecutor.runRemaining()
        assertThat(liveDisplay.getSnapshot()).isEqualTo("Name: Jane")
        assertThat(nameUpdatedCount).isEqualTo(1)

        liveUser.set(userJoe)
        graphExecutor.runRemaining()
        assertThat(liveDisplay.getSnapshot()).isEqualTo("Name: Joe")
        assertThat(nameUpdatedCount).isEqualTo(2)

        // Different objects that equal each other don't trigger graph propogation
        val userJoeCopy = userJoe.copy()
        assertThat(userJoeCopy).isNotSameAs(userJoe)

        liveUser.set(userJoeCopy)
        assertThat(graphExecutor.count).isEqualTo(0)
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

        fun wrapLabel(label: FakeLabel): SourceLive<String> {
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

    @Test
    fun twoLivesCanBeTwoWayBoundViaListeners() {
        // TODO: Actually move an API like this into Lively?
        fun <T1, T2> bind(
            live1: SourceLive<T1>,
            live2: SourceLive<T2>,
            convert1to2: (T1) -> T2?,
            convert2to1: (T2) -> T1?
        ) {
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

        val lively = Lively(LiveGraph(RunImmediatelyExecutor()))

        val liveInt = lively.createInt(123)
        val liveStr = lively.createString()
        val inSync = lively.create { liveInt.get().toString() == liveStr.get() }
        bind(liveStr, liveInt, { strVal -> strVal.toIntOrNull() }, { intVal -> intVal.toString() })

        assertThat(liveInt.getSnapshot()).isEqualTo(123)
        assertThat(liveStr.getSnapshot()).isEqualTo("123")
        assertThat(inSync.getSnapshot()).isTrue()

        liveInt.set(987)
        assertThat(liveStr.getSnapshot()).isEqualTo("987")
        assertThat(inSync.getSnapshot()).isTrue()

        liveStr.set("456")
        assertThat(liveInt.getSnapshot()).isEqualTo(456)
        assertThat(inSync.getSnapshot()).isTrue()

        liveStr.set("Uh oh")
        assertThat(liveInt.getSnapshot()).isEqualTo(456)
        assertThat(inSync.getSnapshot()).isFalse()

        liveStr.set("999")
        assertThat(liveInt.getSnapshot()).isEqualTo(999)
        assertThat(inSync.getSnapshot()).isTrue()

        liveStr.freeze()
        liveInt.set(0)

        assertThat(liveStr.getSnapshot()).isEqualTo("999")
        assertThat(liveInt.getSnapshot()).isEqualTo(0)
    }

    @Test
    fun canCreateSideEffectsViaLivelyListen() {
        val lively = Lively(testGraph)
        val liveInt = lively.create(123)

        var sideEffectInt = 0
        lively.listen {
            sideEffectInt = liveInt.get()
        }

        assertThat(sideEffectInt).isEqualTo(123)

        liveInt.set(9000)
        graphExecutor.runRemaining()
        assertThat(sideEffectInt).isEqualTo(9000)
    }

    @Test
    fun freezingShouldRemoveNodesFromTheGraph() {
        val lively = Lively(testGraph)

        val live1 = lively.create(123)
        val live2 = lively.create(true)
        val live3 = lively.create { live1.get().toString() + live2.get().toString() }
        val live4 = lively.create { live3.get().reversed() }
        val live5 = lively.create { live1.get() + live4.get().length }

        assertThat(testGraph.isEmpty()).isFalse()

        live4.freeze()
        assertThat(testGraph.isEmpty()).isFalse()
        live2.freeze()
        assertThat(testGraph.isEmpty()).isFalse()
        live3.freeze()
        assertThat(testGraph.isEmpty()).isFalse()
        live1.freeze()
        assertThat(testGraph.isEmpty()).isFalse()
        live5.freeze()
        assertThat(testGraph.isEmpty()).isTrue()

        assertThat(live3.getSnapshot()).isEqualTo("123true")
        assertThat(live4.getSnapshot()).isEqualTo("eurt321")
        assertThat(live5.getSnapshot()).isEqualTo(130)
    }

    @Test
    fun freezingCanBeDoneViaLively() {
        val lively = Lively(testGraph)

        val live1 = lively.create(123)
        val live2 = lively.create(true)
        val live3 = lively.create { live1.get().toString() + live2.get().toString() }
        val live4 = lively.create { live3.get().reversed() }
        lively.create { live1.get() + live4.get().length }

        assertThat(testGraph.isEmpty()).isFalse()
        lively.freeze()
        assertThat(testGraph.isEmpty()).isTrue()
    }
}