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
    @Test
    fun setAndGetSnapshotWork() {
        val testGraph = LiveGraph(RunImmediatelyExecutor())
        val lively = Lively(testGraph)
        val liveInt = lively.create(123)
        assertThat(liveInt.getSnapshot()).isEqualTo(123)

        liveInt.set(456)
        assertThat(liveInt.getSnapshot()).isEqualTo(456)
    }

    @Test
    fun observeWorks() {
        val executor = ManualExecutor()
        val testGraph = LiveGraph(executor)

        val lively = Lively(testGraph)
        val liveInt = lively.create(123)
        val liveStr1 = lively.create { liveInt.get().toString() }
        assertThat(liveStr1.getSnapshot()).isEqualTo("123")

        val liveStr2 = lively.create { liveStr1.get().reversed() }
        assertThat(liveStr2.getSnapshot()).isEqualTo("321")

        liveInt.set(456)
        assertThat(liveStr1.getSnapshot()).isEqualTo("123")
        assertThat(liveStr2.getSnapshot()).isEqualTo("321")

        executor.runNext() // liveStr1 updated first (because it was created first)
        assertThat(liveStr1.getSnapshot()).isEqualTo("456")
        assertThat(liveStr2.getSnapshot()).isEqualTo("321")

        executor.runNext()
        assertThat(liveStr1.getSnapshot()).isEqualTo("456")
        assertThat(liveStr2.getSnapshot()).isEqualTo("654")

        liveInt.set(789)
        executor.runRemaining()
        assertThat(liveStr1.getSnapshot()).isEqualTo("789")
        assertThat(liveStr2.getSnapshot()).isEqualTo("987")
    }

    @Test
    fun onValueChangedListenersWorkAsExpected() {
        val executor = ManualExecutor()
        val testGraph = LiveGraph(executor)

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
        assertThat(executor.isEmpty).isTrue()
        assertThat(strChanged).isFalse()
        assertThat(intChanged).isFalse()

        liveStr.set("123")
        assertThat(strChanged).isTrue()
        assertThat(intChanged).isFalse()

        executor.runRemaining()
        assertThat(intChanged).isTrue()
    }

    @Test
    fun dependenciesCanChange() {
        val executor = ManualExecutor()
        val testGraph = LiveGraph(executor)

        val lively = Lively(testGraph)

        val switch = lively.createBool()
        val intTrue = lively.createInt(1)
        val intFalse = lively.createInt(0)

        val finalInt = lively.create {
            if (switch.get()) intTrue.get() else intFalse.get()
        }

        assertThat(finalInt.getSnapshot()).isEqualTo(0)

        switch.set(true)
        executor.runRemaining()

        assertThat(finalInt.getSnapshot()).isEqualTo(1)

        // At this point, nothing should depend on `intFalse`
        intFalse.set(-1)
        assertThat(executor.isEmpty).isTrue()
        assertThat(finalInt.getSnapshot()).isEqualTo(1)

        switch.set(false)
        executor.runRemaining()
        assertThat(finalInt.getSnapshot()).isEqualTo(-1)
    }

    @Test
    fun cannotModifyLiveValueAfterFreezing() {
        val lively = Lively(LiveGraph(RunImmediatelyExecutor()))
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

        // But you can safely query the snapshot
        assertThat(liveStr.getSnapshot()).isEqualTo("initial")

        // You can add listeners to a frozen value, but it's a no-op
        liveStr.onValueChanged += {}
        liveStr.onFroze += {}

        // You can also call freeze on a frozen value (also a no-op)
        liveStr.freeze()

    }

    @Test
    fun youCanFreezeAValueBeforeItGetsUpdated() {
        val executor = ManualExecutor()
        val testGraph = LiveGraph(executor)

        val lively = Lively(testGraph)

        // A -> B -> C
        val liveStrC = lively.create("initial value")
        val liveStrB = lively.create { liveStrC.get() }
        val liveStrA = lively.create { liveStrB.get() }

        assertThat(liveStrA.getSnapshot()).isEqualTo("initial value")

        liveStrC.set("new value")
        liveStrA.freeze()

        executor.runRemaining()
        assertThat(liveStrA.getSnapshot()).isEqualTo("initial value")
    }

    @Test
    fun observeBlockCanReferenceFrozenLiveValues() {
        val executor = ManualExecutor()
        val lively = Lively(LiveGraph(executor))

        val toFreezeNow = lively.create(1).apply { freeze() }
        val toFreezeLater = lively.create(2)
        val neverFrozen = lively.create(3)

        val sum = lively.create { toFreezeNow.get() + toFreezeLater.get() + neverFrozen.get() }
        assertThat(sum.getSnapshot()).isEqualTo(6)

        toFreezeLater.freeze()
        neverFrozen.set(30)

        executor.runRemaining()
        assertThat(sum.getSnapshot()).isEqualTo(33)
    }

    @Test
    fun liveBehaviorWithObjectsWorks() {
        data class User(val name: String, val age: Int)

        val userJoe = User("Joe", 34)
        val userJane = User("Jane", 29)

        val executor = ManualExecutor()

        val lively = Lively(LiveGraph(executor))
        val liveUser = lively.create(userJoe)
        val liveDisplay = lively.create { "Name: ${liveUser.get().name}" }

        var nameUpdatedCount = 0
        liveDisplay.onValueChanged += { ++nameUpdatedCount }

        assertThat(liveDisplay.getSnapshot()).isEqualTo("Name: Joe")
        assertThat(nameUpdatedCount).isEqualTo(0)

        liveUser.set(userJane)
        executor.runRemaining()
        assertThat(liveDisplay.getSnapshot()).isEqualTo("Name: Jane")
        assertThat(nameUpdatedCount).isEqualTo(1)

        liveUser.set(userJoe)
        executor.runRemaining()
        assertThat(liveDisplay.getSnapshot()).isEqualTo("Name: Joe")
        assertThat(nameUpdatedCount).isEqualTo(2)

        // Different objects that equal each other don't trigger graph propogation
        val userJoeCopy = userJoe.copy()
        assertThat(userJoeCopy).isNotSameAs(userJoe)

        liveUser.set(userJoeCopy)
        assertThat(executor.isEmpty).isTrue()
    }
}