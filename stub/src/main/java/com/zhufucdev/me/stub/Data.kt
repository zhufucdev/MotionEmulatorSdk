package com.zhufucdev.me.stub

import kotlinx.serialization.Serializable
import java.io.OutputStream
import java.text.DateFormat

/**
 * Represents something that can be referred
 * with barely a string ID
 */
interface Data {
    val id: String
}

@Serializable
data class Metadata(val creationTime: Long, val name: String? = null)