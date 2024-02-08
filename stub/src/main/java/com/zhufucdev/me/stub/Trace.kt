package com.zhufucdev.me.stub

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.descriptors.serialDescriptor
import kotlinx.serialization.encoding.CompositeDecoder.Companion.DECODE_DONE
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.encoding.decodeStructure
import kotlinx.serialization.encoding.encodeStructure
import kotlinx.serialization.serializer

/**
 * A location on the Earth. Get it?
 */
@Serializable(PointSerializer::class)
class Point : Vector2D {
    val coordinateSystem: CoordinateSystem

    /**
     * Constructs a [Point], in WGS84 coordinate system
     */
    constructor(latitude: Double, longitude: Double) : super(latitude, longitude) {
        coordinateSystem = CoordinateSystem.WGS84
    }

    /**
     * Constructs a [Point], in a given coordinate system
     */
    constructor(latitude: Double, longitude: Double, coordinateSystem: CoordinateSystem) : super(
        latitude,
        longitude
    ) {
        this.coordinateSystem = coordinateSystem
    }

    val latitude get() = x

    val longitude get() = y

    companion object {
        val zero get() = Point(0.0, 0.0)
    }
}

/**
 * Composed of series of [Point]s.
 * @param name to call the trace
 * @param points to describe the trace's shape and direction
 */
@Serializable(TraceSerializer::class)
data class Trace(
    override val id: String,
    override val points: List<Point>,
    val coordinateSystem: CoordinateSystem = CoordinateSystem.GCJ02,
    val salt: Salt2dData? = null
) : Data, ClosedShape

enum class CoordinateSystem {
    /**
     * GCJ-02 is an encrypted coordinate system made by government of China mainland,
     * to keep their map data safe. To learn more, go ahead to
     * [Restrictions on geographic data in China](https://en.wikipedia.org/wiki/Restrictions_on_geographic_data_in_China)
     */
    GCJ02,

    /**
     * WGS-84 is a standard coordinate system, which data can be fetched
     * directly from GPS report. To learn more,
     * [here's wikipedia](https://en.wikipedia.org/wiki/World_Geodetic_System)
     */
    WGS84
}

class TraceSerializer : KSerializer<Trace> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("$SERIALIZATION_ID.data.Trace") {
            element("id", serialDescriptor<Int>())
            element("coordSys", serialDescriptor<CoordinateSystem>(), isOptional = true)
            element("points", serialDescriptor<Point>())
            element("salt", serialDescriptor<Salt2dData>(), isOptional = true)
        }

    override fun deserialize(decoder: Decoder): Trace = decoder.decodeStructure(descriptor) {
        var id = ""
        var coordinateSystem = CoordinateSystem.GCJ02
        var points: List<Point> = emptyList()
        var salt: Salt2dData? = null
        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> id = decodeStringElement(descriptor, index)
                1 -> coordinateSystem = decodeSerializableElement(descriptor, index, serializer())
                2 -> points = decodeSerializableElement(descriptor, index, serializer())
                3 -> salt = decodeSerializableElement(descriptor, index, serializer<Salt2dData>())
            }
        }

        Trace(
            id,
            points.map { Point(it.latitude, it.longitude, coordinateSystem) },
            coordinateSystem,
            salt
        )
    }

    override fun serialize(encoder: Encoder, value: Trace) =
        encoder.encodeStructure(descriptor) {
            encodeStringElement(descriptor, 0, value.id)
            encodeSerializableElement(descriptor, 1, serializer(), value.coordinateSystem)
            encodeSerializableElement(descriptor, 2, serializer(), value.points)
            value.salt?.let {
                encodeSerializableElement(descriptor, 3, serializer(), it)
            }
        }

}

/**
 * **This serializer doesn't come with coordination system support**
 */
class PointSerializer : KSerializer<Point> {
    private val delegateSerializer = serializer<DoubleArray>()
    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun deserialize(decoder: Decoder): Point {
        val arr = decoder.decodeSerializableValue(delegateSerializer)
        return Point(arr[0], arr[1])
    }

    override fun serialize(encoder: Encoder, value: Point) {
        encoder.encodeSerializableValue(
            delegateSerializer,
            doubleArrayOf(value.latitude, value.longitude)
        )
    }
}

class PointSerializerCoord : KSerializer<Point> {
    private val doubleArraySerializer = serializer<DoubleArray>()
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("$SERIALIZATION_ID.data.Point") {
            element("point", serialDescriptor<DoubleArray>())
            element("coord", serialDescriptor<CoordinateSystem>())
        }

    override fun deserialize(decoder: Decoder): Point = decoder.decodeStructure(descriptor) {
        var point: DoubleArray? = null
        var coordinateSystem = CoordinateSystem.WGS84
        loop@ while (true) {
            when (val index = decodeElementIndex(descriptor)) {
                DECODE_DONE -> break@loop
                0 -> point = decodeSerializableElement(descriptor, 0, doubleArraySerializer)
                1 -> coordinateSystem = decodeSerializableElement(descriptor, index, serializer())
            }
        }

        point ?: error("No location value")

        Point(point[0], point[1], coordinateSystem)
    }

    override fun serialize(encoder: Encoder, value: Point) =
        encoder.encodeStructure(descriptor) {
            encodeSerializableElement(
                descriptor,
                0,
                doubleArraySerializer,
                doubleArrayOf(value.latitude, value.longitude)
            )
            encodeSerializableElement(descriptor, 1, serializer(), value.coordinateSystem)
        }
}