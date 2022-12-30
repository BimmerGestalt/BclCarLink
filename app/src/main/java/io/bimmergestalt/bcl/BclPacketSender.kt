package io.bimmergestalt.bcl

import org.tinylog.Logger
import java.io.OutputStream

class BclPacketSender(private val output: OutputStream) {
    @Suppress("BlockingMethodInNonBlockingContext") // Why does linter think this context is nonblocking
    fun write(packet: BclPacket) {
        if (packet.dest == 5001.toShort()) {
            Logger.debug {"Sending $packet"}
        }
        synchronized(this) {
            output.write(packet.serialize())
        }
    }
}