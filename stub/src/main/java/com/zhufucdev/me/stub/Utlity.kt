package com.zhufucdev.me.stub

import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import kotlin.random.Random

const val SERIALIZATION_ID = "com.zhufucdev.motion_emulator"

fun Point.offsetFixed(): Point =
    with(if (coordinateSystem == CoordinateSystem.GCJ02) MapProjector else BypassProjector) { toIdeal() }
        .toPoint(CoordinateSystem.WGS84)

fun Point.android(
    provider: String = LocationManager.GPS_PROVIDER,
    speed: Float = 0F,
): Location {
    val result = Location(provider).apply {
        // fake some data
        time = System.currentTimeMillis()
        elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            elapsedRealtimeUncertaintyNanos = 5000.0 + (Random.nextDouble() - 0.5) * 1000
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verticalAccuracyMeters = Random.nextFloat() * 10
        }
        accuracy = 1F
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            verticalAccuracyMeters = 0.1F
            if (speed > 0) {
                speedAccuracyMetersPerSecond = 0.01F
            }
        }
    }


    val fixed = offsetFixed()
    result.latitude = fixed.latitude
    result.longitude = fixed.longitude
    result.speed = speed

    return result
}

/**
 * Generate a salted trace, where all points are
 * involved with random factors
 */
fun Trace.generateSaltedTrace(): List<Point> {
    val salt = this.salt
    return if (salt != null) {
        val runtime = salt.runtime()
        val projector =
            if (coordinateSystem == CoordinateSystem.GCJ02) MapProjector else BypassProjector
        points.map {
            runtime.apply(
                point = it,
                projector = projector,
                parent = this
            ).toPoint(this.coordinateSystem)
        }
    } else {
        points
    }
}

fun Pair<Point, Point>.estimateDistance() =
    if (second.coordinateSystem == CoordinateSystem.WGS84 && first.coordinateSystem == CoordinateSystem.WGS84) {
        with(MapProjector) {
            second.distanceIdeal(first)
        }
    } else if (second.coordinateSystem == CoordinateSystem.GCJ02 && first.coordinateSystem == CoordinateSystem.GCJ02) {
        with(MapProjector) {
            second.distance(first)
        }
    } else {
        throw IllegalArgumentException("second comes with a different coordination " +
                "system (${second.coordinateSystem.name}) than first")
    }

fun estimateSpeed(current: Pair<Point, Long>, last: Pair<Point, Long>) =
    (last.first to current.first).estimateDistance() / (current.second - last.second) * 1000

/**
 * Length of a trace, including the sealing
 */
fun Trace.length(): Double {
    var sum = 0.0
    for (i in 1 until points.size) {
        sum += (points[i] to points[i - 1]).estimateDistance()
    }
    sum += (points.last() to points.first()).estimateDistance()
    return sum
}
