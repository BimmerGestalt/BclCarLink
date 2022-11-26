package io.bimmergestalt.bclclient.helpers

import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData


object LiveDataHelpers {
    // Inspired by https://github.com/tmurakami/aackt/blob/master/lifecycle-livedata/src/main/java/com/github/tmurakami/aackt/lifecycle/Transformations.kt
    /** Like Transformations.map(LiveData), except supports an initial value */
    inline fun <T, R : Any> LiveData<T>.map(initialValue: R? = null, crossinline block: (T) -> R?): LiveData<R> {
        val result = MediatorLiveData<R>()
        initialValue?.also { result.value = it }
        result.addSource(this) {
            // only map nonnull data
            it?.also { result.value = block(it) }
        }
        return result
    }

    /**
     * Combine this LiveData and given LiveData to some calculated output LiveData
     *     Either LiveData object will trigger an update
     */
    fun <X,Y,R> LiveData<X>.combine(other: LiveData<Y>, combiner: (X, Y) -> R): LiveData<R> {
        val result = MediatorLiveData<R>()
        result.addSource(this) { x ->
            x ?: return@addSource
            val y = other.value ?: return@addSource
            result.value = combiner(x, y)
        }
        result.addSource(other) { y ->
            val x = this.value ?: return@addSource
            y ?: return@addSource
            result.value = combiner(x, y)
        }
        return result
    }

    /**
     * Formats this LiveData<Double> with the given format string
     */
    fun LiveData<Double>.format(format: String): LiveData<String> {
        val result = MediatorLiveData<String>()
        result.addSource(this) {
            val value = this.value
            if (value != null) {
                result.value = String.format(format, value)
            }
        }
        return result
    }
}