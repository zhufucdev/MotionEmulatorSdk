package com.zhufucdev.me.stub

import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

/**
 * Wraps something for it to be possibly empty or blocked ([Motion] and [CellTimeline])
 */
open class Box<T>(val value: T? = null) {
    open val status: Toggle
        get() = Toggle.PRESENT

    override fun equals(other: Any?): Boolean =
        other is Box<*> && other.value == value && other.status == status

    override fun hashCode(): Int {
        return (value?.hashCode() ?: 0) + status.hashCode()
    }

    companion object {
        inline fun <reified T> decodeFromString(str: String): Box<T> {
            val value = when (str) {
                EMPTY_REF -> return EmptyBox()
                BLOCK_REF -> return BlockBox()
                NULL_REF -> null
                else -> Json.decodeFromString(serializer<T>(), str)
            }
            return Box(value)
        }
    }
}

/**
 * Something that bypasses to the original method
 */
class EmptyBox<T> : Box<T>() {
    override val status: Toggle
        get() = Toggle.NONE
}

/**
 * Something that can but doesn't have a value
 */
class BlockBox<T> : Box<T>() {
    override val status: Toggle
        get() = Toggle.BLOCK
}

/**
 * Status of a [Box]
 */
enum class Toggle {
    BLOCK, NONE, PRESENT
}

/**
 * Wraps something into a [Box]
 */
fun <T> T.box(): Box<T> = Box(this)

/**
 * Wraps something into a [Box] unless it's null, and in this case
 * wraps to [EmptyBox]
 */
fun <T> T.boxOrEmpty(): Box<T> = this?.let { Box(it) } ?: EmptyBox()

const val EMPTY_REF = "none"
const val BLOCK_REF = "block"
const val NULL_REF = "null"

/**
 * Encode a [Box] into [Json] with its [Box.value], or an [EmptyBox] into [EMPTY_REF], or a [BlockBox] into [BLOCK_REF]
 */
inline fun <reified T> Box<T>.encodeToString(): String =
    when (this) {
        is EmptyBox -> EMPTY_REF
        is BlockBox -> BLOCK_REF
        else -> value?.let { Json.encodeToString(serializer<T>(), it) } ?: "null"
    }

/**
 * Get its reference if a [Box]'s [Box.value] is a [Data]
 * - [EmptyBox]<[Data]> returns [EMPTY_REF]
 * - [BlockBox]<[Data]> returns [BLOCK_REF]
 * - [Box]<[Data]> returns [Box.value]->[Data.id]
 */
fun <T : Data> Box<T>.ref() =
    when (this) {
        is EmptyBox<T> -> EMPTY_REF
        is BlockBox<T> -> BLOCK_REF
        else -> value?.id ?: NULL_REF
    }

