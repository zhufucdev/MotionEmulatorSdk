package com.zhufucdev.me.stub

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer
import android.hardware.Sensor

typealias RawSensorData = FloatArray
typealias MotionTimeline = List<SensorMoment>

/**
 * Basic motion record unit
 *
 * @param data SensorType to its value
 * @param elapsed Time from start (in sec.)
 */
@Serializable(SensorMomentSerializer::class)
data class SensorMoment(override var elapsed: Float, val data: RawSensorData) : Moment {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SensorMoment

        if (elapsed != other.elapsed) return false
        if (!data.contentEquals(other.data)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = elapsed.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }
}


class SensorMomentSerializer : KSerializer<SensorMoment> {
    private val delegateSerializer = serializer<FloatArray>()
    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun deserialize(decoder: Decoder): SensorMoment {
        val arr = decoder.decodeSerializableValue(delegateSerializer)
        return SensorMoment(arr[0], arr.sliceArray(arr.indices.drop(1)))
    }

    override fun serialize(encoder: Encoder, value: SensorMoment) {
        encoder.encodeSerializableValue(delegateSerializer, floatArrayOf(value.elapsed, *value.data))
    }
}

/**
 * Motion record to reproduce sensor events
 * @param timelines A map between [Sensor.getId] and it's [SensorMoment]s
 */
@Serializable
data class Motion(
    override val id: String,
    val timelines: Map<Int, MotionTimeline>,
) : Data
