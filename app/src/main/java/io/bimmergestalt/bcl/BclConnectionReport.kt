package io.bimmergestalt.bcl

data class BclConnectionReport(
    val startTimestamp: Long,
    val bytesRead: Long,
    val bytesWritten: Long,
    val numConnections: Int,
    val instanceId: Short,
    val watchdogRtt: Long,
    val huBufSize: Int,
    val remainingAckBytes: Long,
    val state: String
)