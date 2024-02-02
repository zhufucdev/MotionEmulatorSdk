package com.zhufucdev.me.stub

/**
 * Wraps a variable
 */
class MutableBox<T>(var value: T)

/**
 * Wraps a variable and returns its [MutableBox]
 */
fun <T> T.mutbox() = MutableBox(this)
