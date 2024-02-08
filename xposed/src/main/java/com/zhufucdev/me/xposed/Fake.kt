@file:Suppress("DEPRECATION")

package com.zhufucdev.me.xposed

import android.annotation.SuppressLint
import android.telephony.*
import android.telephony.cdma.CdmaCellLocation
import android.telephony.gsm.GsmCellLocation
import androidx.core.os.bundleOf
import com.zhufucdev.me.stub.CellMoment
import com.zhufucdev.me.stub.Motion
import com.zhufucdev.me.stub.RawSensorData
import com.zhufucdev.me.stub.duration
import com.zhufucdev.me.stub.minus
import com.zhufucdev.me.stub.plus
import com.zhufucdev.me.stub.times
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Result for [Motion.at]
 *
 * @param timelines the interpolated data
 * @param nextIndexes you probably won't care, it's about the algorithm
 */
data class MotionLerp(val timelines: Map<Int, RawSensorData>, val nextIndexes: Map<Int, Int>)

/**
 * Interpolate a moment, given a [progress] valued between
 * 0 and 1
 *
 * Note that step data are not present and the returned
 * value may never have existed
 *
 * @see [MotionLerp]
 */
fun Motion.at(progress: Float, lastInterp: MotionLerp? = null): MotionLerp {
    fun lerp(progr: Float, current: FloatArray, last: FloatArray) =
        (current - last) * progr + current

    if (timelines.isEmpty()) {
        return MotionLerp(emptyMap(), emptyMap())
    } else {
        val duration = timelines.duration()
        val elapsed = duration * progress
        val indexes = lastInterp?.nextIndexes?.toMutableMap() ?: mutableMapOf()
        val data = timelines.mapValues { (t, u) ->
            val lastIndex = indexes[t] ?: 0
            var nextIndex = lastIndex
            while (nextIndex < u.size && u[nextIndex].elapsed < elapsed) {
                nextIndex++
            }
            indexes[t] = nextIndex
            if (nextIndex > lastIndex) {
                val p =
                    (elapsed - u[lastIndex].elapsed) / (u[nextIndex].elapsed - u[lastIndex].elapsed)
                lerp(p, u[nextIndex].data, u[lastIndex].data)
            } else {
                u[nextIndex].data
            }
        }
        return MotionLerp(data, indexes)
    }
}

/**
 * Treat the array as a 3D vector
 * @return its length
 */
fun FloatArray.length(): Float {
    return sqrt(first().pow(2) + get(1).pow(2) + get(2).pow(2))
}

/**
 * To remove gravity from a 3D vector
 */
fun FloatArray.filterHighPass(): FloatArray {
    val gravity = FloatArray(3)
    val linearAcceleration = FloatArray(3)
    val alpha = 0.8f

    // Isolate the force of gravity with the low-pass filter.
    gravity[0] = alpha * gravity[0] + (1 - alpha) * this[0]
    gravity[1] = alpha * gravity[1] + (1 - alpha) * this[1]
    gravity[2] = alpha * gravity[2] + (1 - alpha) * this[2]

    // Remove the gravity contribution with the high-pass filter.
    linearAcceleration[0] = this[0] - gravity[0]
    linearAcceleration[1] = this[1] - gravity[1]
    linearAcceleration[2] = this[2] - gravity[2]
    return linearAcceleration
}

@SuppressLint("NewApi")
fun CellInfo.cellLocation(): CellLocation? =
    when (val id = cellIdentity) {
        is CellIdentityCdma ->
            CdmaCellLocation(
                bundleOf(
                    "baseStationId" to id.basestationId,
                    "baseStationLatitude" to id.latitude,
                    "baseStationLongitude" to id.longitude,
                    "systemId" to id.systemId,
                    "networkId" to id.networkId
                )
            )

        is CellIdentityGsm ->
            GsmCellLocation(
                bundleOf(
                    "lac" to id.lac,
                    "cid" to id.cid,
                    "psc" to id.psc
                )
            )

        else -> null
    }

@Suppress("DEPRECATION")
@SuppressLint("NewApi")
fun CellMoment.cellLocation(): CellLocation? {
    if (location != null) return location
    return if (cell.isNotEmpty()) {
        cell.first().cellLocation()
    } else {
        null
    }
}

@SuppressLint("NewApi")
fun CellMoment.neighboringInfo(): List<NeighboringCellInfo> {
    if (neighboring.isNotEmpty()) return neighboring
    if (cell.isNotEmpty()) {
        return cell.mapNotNull {
            when (it) {
                is CellInfoCdma -> NeighboringCellInfo(
                    it.cellSignalStrength.dbm,
                    it.cellIdentity.basestationId
                )

                is CellInfoGsm -> NeighboringCellInfo(
                    it.cellSignalStrength.rssi,
                    it.cellIdentity.cid
                )

                is CellInfoLte -> NeighboringCellInfo(
                    it.cellSignalStrength.rssi,
                    it.cellIdentity.ci
                )

                is CellInfoWcdma -> NeighboringCellInfo(
                    it.cellSignalStrength.dbm,
                    it.cellIdentity.cid
                )

                else -> null
            }
        }
    }
    return emptyList()
}

@SuppressLint("MissingPermission")
fun PhoneStateListener.treatWith(moment: CellMoment, mode: Int) {
    var mask = PhoneStateListener.LISTEN_CELL_INFO
    if (mode and mask == mask) {
        onCellInfoChanged(moment.cell)
    }
    mask = PhoneStateListener.LISTEN_CELL_LOCATION
    if (mode and mask == mask) {
        onCellLocationChanged(moment.cellLocation())
    }
}

@SuppressLint("NewApi", "MissingPermission")
fun TelephonyCallback.treatWith(moment: CellMoment) {
    if (this is TelephonyCallback.CellInfoListener) {
        onCellInfoChanged(moment.cell)
    }
    if (this is TelephonyCallback.CellLocationListener) {
        moment.cellLocation()?.let {
            onCellLocationChanged(it)
        }
    }
}
