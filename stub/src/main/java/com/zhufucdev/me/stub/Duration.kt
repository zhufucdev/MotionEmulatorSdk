package com.zhufucdev.me.stub

/**
 * Get the time span (difference between max and min [Moment.elapsed]) in a timeline
 *
 * @return time span in seconds
 */
fun List<Moment>.timespan(): Float {
    if (isEmpty()) return 0F
    if (size == 1) return first().elapsed
    return last().elapsed - first().elapsed
}

/**
 * Duration (max [Moment.elapsed]) in a timeline
 */
fun <T : Moment> List<T>.duration() = lastOrNull()?.elapsed ?: 0f

/**
 * [List.timespan] but for [Map]s
 */
fun <T> Map<T, List<Moment>>.timespan() =
    maxOf { it.value.maxOf { m -> m.elapsed } } - minOf { minOf { it.value.minOf { m -> m.elapsed } } }

/**
 * [List.duration] but for [Map]s
 */
fun <T> Map<T, List<Moment>>.duration() = maxOf { it.value.maxOf { m -> m.elapsed } }