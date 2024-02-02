package com.zhufucdev.me.plugin

import android.os.SystemClock
import com.zhufucdev.me.stub.Box
import com.zhufucdev.me.stub.CellTimeline
import com.zhufucdev.me.stub.EmptyBox
import com.zhufucdev.me.stub.Emulation
import com.zhufucdev.me.stub.EmulationInfo
import com.zhufucdev.me.stub.Motion
import com.zhufucdev.me.stub.Toggle
import com.zhufucdev.me.stub.Trace
import com.zhufucdev.me.stub.duration
import com.zhufucdev.me.stub.length
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * An scheduler is the first thing that picks the
 * [Emulation] request, perceives it and deploy
 * to concrete actions
 */
abstract class AbstractScheduler {
    abstract val packageName: String

    var isWorking = false
        private set

    /**
     * Duration of this emulation in seconds
     */
    protected var duration = -1.0
    protected var length = 0.0

    /**
     * How many satellites to simulate
     *
     * 0 to not simulate
     */
    var satellites: Int = 0
        private set

    private var loopStart = 0L
    val loopElapsed get() = SystemClock.elapsedRealtime() - loopStart
    protected val loopProgress get() = (loopElapsed / duration / 1000).toFloat()

    protected suspend fun ServerScope.startEmulation(emulation: Emulation) {
        when {
            emulation.trace.status == Toggle.PRESENT -> {
                length = emulation.trace.value!!.length()
                duration = length / emulation.velocity
            }
            emulation.motion.status == Toggle.PRESENT -> {
                length = 0.0
                duration = emulation.motion.value!!.moments.duration().toDouble()
            }
            emulation.cells.status == Toggle.PRESENT -> {
                length = 0.0
                duration = emulation.cells.value!!.moments.duration().toDouble()
            }
            else -> {
                length = 0.0
                duration = -1.0
            }
        }

        satellites = emulation.satelliteCount

        sendStarted(EmulationInfo(duration, length, packageName))
        onEmulationStarted(emulation)
        for (i in 0 until emulation.repeat) {
            loopStart = SystemClock.elapsedRealtime()
            coroutineScope {
                launch {
                    startStepsEmulation(emulation.motion, emulation.velocity)
                }
                launch {
                    startMotionSimulation(emulation.motion)
                }
                launch {
                    startTraceEmulation(emulation.trace)
                }
                launch {
                    startCellEmulation(emulation.cells)
                }
            }
        }

        onEmulationCompleted(emulation)
    }

    open fun onEmulationStarted(emulation: Emulation) {
        isWorking = true
    }

    open fun onEmulationCompleted(emulation: Emulation) {
        isWorking = false
    }

    open suspend fun ServerScope.startStepsEmulation(motion: Box<Motion>, velocity: Double) {
    }

    open suspend fun ServerScope.startMotionSimulation(motion: Box<Motion>) {
    }

    open suspend fun ServerScope.startTraceEmulation(trace: Box<Trace>) {
    }

    open suspend fun ServerScope.startCellEmulation(cells: Box<CellTimeline>) {
    }
}
