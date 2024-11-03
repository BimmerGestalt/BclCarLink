package io.bimmergestalt.bcl

import org.tinylog.Logger
import java.io.OutputStream

interface BclPacketSender {
    fun writePacket(packet: BclPacket)
}
class BclPacketSenderConcrete(private val output: OutputStream): BclPacketSender {
    override fun writePacket(packet: BclPacket) {
        if (packet.dest == 5001.toShort()) {
            Logger.debug {"Sending $packet"}
        }
        synchronized(this) {
            output.write(packet.serialize())
        }
    }
}