package io.bimmergestalt.bcl

import java.io.OutputStream

class BclOutputStream(private val src: Short, private val dest: Short, private val output: BclPacketSender): OutputStream() {
    override fun write(b: Int) {
        output.writePacket(BclPacket.Specialized.Data(src, dest, ByteArray(b)))
    }

    override fun write(b: ByteArray?) {
        b ?: return
        output.writePacket(BclPacket.Specialized.Data(src, dest, b))
    }

    override fun write(b: ByteArray?, off: Int, len: Int) {
        b ?: return
        if (off == 0 && len == b.size)  {
            write(b)
        } else {
            write(b.sliceArray(off until len))
        }
    }

    override fun close() {
        output.writePacket(BclPacket.Specialized.Close(src, dest))
    }
}