package com.zhufucdev.me.stub

class MutableBox<T>(var value: T)

fun <T> T.mutbox() = MutableBox(this)
