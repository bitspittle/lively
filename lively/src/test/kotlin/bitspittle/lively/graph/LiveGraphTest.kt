package bitspittle.lively.graph

import bitspittle.lively.Live
import bitspittle.lively.Lively
import bitspittle.lively.exec.Executor
import bitspittle.lively.exec.ManualExecutor
import bitspittle.lively.exec.RunImmediatelyExecutor
import bitspittle.lively.extensions.createInt
import bitspittle.truthish.assertThat
import bitspittle.truthish.assertThrows
import org.junit.Test
import java.lang.ref.WeakReference
import kotlin.IllegalStateException

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

    @Test
    fun executorCannotSneakilyRunTasksOnADifferentThread() {
        var exceptionThrownByExecutorThread = false
        val sneakyExecutor = object : Executor {
            override fun submit(runnable: () -> Unit) {
                Thread {
                    try {
                        runnable()
                    }
                    catch (e: IllegalStateException) {
                        exceptionThrownByExecutorThread = true
                    }
                }.apply {
                    start()
                    join()
                }
            }
        }
        val liveGraph = LiveGraph(sneakyExecutor)
        val lively = Lively(liveGraph)

        val liveInt1 = lively.create(123)
        val liveInt2 = lively.create { liveInt1.get() * 2 }

        assertThat(liveInt2.getSnapshot()).isEqualTo(246)

        assertThat(exceptionThrownByExecutorThread).isFalse()
        liveInt1.set(200)
        assertThat(exceptionThrownByExecutorThread).isTrue()
        assertThat(liveInt2.getSnapshot()).isEqualTo(246) // Invalid executor didn't affect state
    }

    @Test
    fun graphCleanedUpIfLiveValuesCanBeGarbageCollected() {
        lateinit var weakLively: WeakReference<Lively>
        lateinit var testGraph: LiveGraph

        // While not-null, this reference keeps live liveDst alive which keeps liveSrc alive too
        @Suppress("ASSIGNED_BUT_NEVER_ACCESSED_VARIABLE")
        var liveDstReference: Live<Int>? = null

        Thread {
            testGraph = LiveGraph(RunImmediatelyExecutor())
            val lively = Lively(testGraph)
            @Suppress("UNUSED_VARIABLE") // Var created for readability
            val liveOrphan = lively.createInt()
            val liveSrc = lively.createInt()
            val liveDst = lively.create { liveSrc.get() }

            assertThat(testGraph.nodeCount).isEqualTo(3)
            weakLively = WeakReference(lively)
            liveDstReference = liveDst
        }.apply {
            start()
            join()
        }

        assertThat(testGraph.nodeCount).isEqualTo(3)
        while (weakLively.get() != null) {
            System.gc()
            Thread.sleep(10)
        }
        assertThat(testGraph.nodeCount).isEqualTo(2)

        liveDstReference = null // Removing this allows both liveSrc and liveDst to be collected
        while (testGraph.nodeCount > 0) {
            System.gc()
            Thread.sleep(10)
        }
    }

    @Test
    fun updateOnlyPropogatesIfValueChanged() {
        val executor = ManualExecutor()
        val testGraph = LiveGraph(executor)
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
        executor.runRemaining()
        assertThat(middleIntChanged).isFalse()
        assertThat(finalIntChanged).isFalse()

        // Sanity check that deps propagate later
        dummyInt1.set(100)
        executor.runRemaining()
        assertThat(middleIntChanged).isTrue()
        assertThat(finalIntChanged).isTrue()
    }

    @Test
    fun verifyMultipleUpdatesAreBatchedIntoASingleObserveCall() {
        val executor = ManualExecutor()
        val testGraph = LiveGraph(executor)
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

        executor.runRemaining()
        assertThat(count).isEqualTo(1)
        assertThat(sum.getSnapshot()).isEqualTo(111)
    }

    @Test
    fun verifyMultipleUpdatesPropogateCorrectly() {
        // With this graph:
        //
        // A ←┬- D ← E ← G
        // B ←┘          |
        // C ←------ F ←-┘
        //
        // If we're not careful, and user changes A', B', and C' on a single frame...
        // A' → D'
        // D' → E'
        // E' → G'
        // B' → D' // ignored, D' is already pending
        // C' → F'
        // F' → G' // ignored, G' is already pending
        //
        // We want to make sure that G' gets F's changes, even though F' is later on the
        // update order due to C changing later.

        val executor = ManualExecutor()
        val testGraph = LiveGraph(executor)
        val lively = Lively(testGraph)

        val intA = lively.createInt(1)
        val intB = lively.createInt(10)
        val intC = lively.createInt(100)
        val intD = lively.create { intA.get() + intB.get() }
        val intE = lively.create { intD.get() }
        val intF = lively.create { intC.get() }
        val intG = lively.create { intE.get() + intF.get() }

        var updatedCount = 0
        intG.onValueChanged += { ++updatedCount }

        assertThat(intG.getSnapshot()).isEqualTo(111)
        assertThat(updatedCount).isEqualTo(0)

        intA.set(2)
        intB.set(20)
        intC.set(200)
        executor.runRemaining()

        assertThat(intG.getSnapshot()).isEqualTo(222)
        assertThat(updatedCount).isEqualTo(1)
    }

    @Test
    fun dependenciesUpdatedInAnOrderThatPreventsRedundantUpdates() {
        val lively = Lively(LiveGraph(RunImmediatelyExecutor()))

        // With this graph:
        //
        // A ← B ←-----┐
        // ↑           |
        // └-- C ← D ← E
        //
        // If we're not careful,
        // A' → B' + C'
        // B' → E'  // updated once
        // C' → D'
        // D' → E'' // updated twice!
        //
        // Instead, E should wait for both branches to propagate before getting updated itself

        val intA = lively.create(1)
        val intB = lively.create { intA.get() }
        val intC = lively.create { intA.get() }
        val intD = lively.create { intC.get() }
        val intE = lively.create { intB.get() + intD.get() }

        assertThat(intE.getSnapshot()).isEqualTo(2)

        var updateCount = 0
        intE.onValueChanged += { ++updateCount }

        assertThat(updateCount).isEqualTo(0)

        intA.set(2)
        assertThat(intE.getSnapshot()).isEqualTo(4)
        assertThat(updateCount).isEqualTo(1)
    }

    @Test
    fun freezingShouldRemoveNodesFromTheGraph() {
        val testGraph = LiveGraph(RunImmediatelyExecutor())
        val lively = Lively(testGraph)

        run {
            val live1 = lively.create(123)
            val live2 = lively.create(true)
            val live3 = lively.create { live1.get().toString() + live2.get().toString() }
            val live4 = lively.create { live3.get().reversed() }
            val live5 = lively.create { live1.get() + live4.get().length }

            assertThat(testGraph.nodeCount).isEqualTo(5)

            live5.freeze()
            assertThat(testGraph.nodeCount).isEqualTo(4)
            live4.freeze()
            assertThat(testGraph.nodeCount).isEqualTo(3)
            live3.freeze()
            assertThat(testGraph.nodeCount).isEqualTo(2)
            live2.freeze()
            assertThat(testGraph.nodeCount).isEqualTo(1)
            live1.freeze()
            assertThat(testGraph.nodeCount).isEqualTo(0)

            assertThat(live3.getSnapshot()).isEqualTo("123true")
            assertThat(live4.getSnapshot()).isEqualTo("eurt321")
            assertThat(live5.getSnapshot()).isEqualTo(130)
        }

        // observing lives which have all their dependencies frozen are auto-frozen
        run {
            val live1 = lively.create(123)
            val live2 = lively.create(true)
            val live3 = lively.create { live1.get().toString() + live2.get().toString() }
            val live4 = lively.create { live3.get().reversed() }
            lively.create { live1.get() + live4.get().length }

            assertThat(testGraph.nodeCount).isEqualTo(5)

            live1.freeze()
            assertThat(testGraph.nodeCount).isEqualTo(4)
            live2.freeze()
            assertThat(testGraph.nodeCount).isEqualTo(0)
        }
    }

    @Test
    fun graphUpdateHandlesException() {
        val testGraph = LiveGraph(RunImmediatelyExecutor())
        val lively = Lively(testGraph)

        val liveInt = lively.createInt(1)
        // Throws if liveInt is set to 0
        val liveFragile = lively.create { 20 / liveInt.get() }

        assertThat(liveFragile.getSnapshot()).isEqualTo(20)
        liveInt.set(2)
        assertThat(liveFragile.getSnapshot()).isEqualTo(10)

        assertThrows<ArithmeticException> { liveInt.set(0) }
        assertThat(liveFragile.getSnapshot()).isEqualTo(10) // Not updated due to exception

        // We can even recover
        liveInt.set(20)
        assertThat(liveFragile.getSnapshot()).isEqualTo(1)

        // Throw an exception one more time, to verify we don't leave any pending updates
        // behind if it happens.
        assertThat(testGraph.nodeCount).isEqualTo(2)
        assertThrows<ArithmeticException> { liveInt.set(0) }
        liveInt.freeze()
        assertThat(testGraph.nodeCount).isEqualTo(0) // Indirectly verifies 0 pending updates
    }
}