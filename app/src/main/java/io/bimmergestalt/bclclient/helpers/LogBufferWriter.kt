package io.bimmergestalt.bclclient.helpers

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asFlow
import com.google.common.collect.EvictingQueue
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.map
import org.tinylog.Level
import org.tinylog.core.LogEntry
import org.tinylog.core.LogEntryValue
import org.tinylog.writers.Writer
import java.util.*

val Level.abbreviation: String
    get() = this.name.substring(0, 1)

fun Date.formatTime(): String {
    val calendar = Calendar.getInstance()
    calendar.time = this
    return "${calendar[Calendar.HOUR]}:${calendar[Calendar.MINUTE]}:${calendar[Calendar.SECOND]}.${calendar[Calendar.MILLISECOND]}"
}

@Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UnstableApiUsage")
class LogBufferWriter(options: java.util.Map<String, String>): Writer {
    @OptIn(FlowPreview::class)
    companion object {
        val buffer = EvictingQueue.create<String>(1000)
        private val _latestBuffer = MutableLiveData(buffer)
        val messages: Flow<String> = _latestBuffer.asFlow()
            .debounce(300)
            .map {
                synchronized(buffer) {
                    it.joinToString("\n")
                }
            }
    }

    override fun getRequiredLogEntryValues(): Collection<LogEntryValue> {
        return setOf(LogEntryValue.LEVEL, LogEntryValue.MESSAGE)
    }

    override fun write(logEntry: LogEntry?) {
        logEntry ?: return
        logEntry.level ?: return
        logEntry.message ?: return
        synchronized(buffer) {
            buffer.add("${logEntry.level.abbreviation} ${logEntry.timestamp.toDate().formatTime()} ${logEntry.message}")
        }
        _latestBuffer.value = buffer
    }

    override fun flush() {
        // noop
    }

    override fun close() {
        buffer.clear()
    }
}