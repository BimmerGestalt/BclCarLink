package io.bimmergestalt.bcl

import java.io.OutputStream

class BclOutputStream(private val src: Short, private val dest: Short, private val outputStream: OutputStream):
    OutputStream() {
    override fun write(b: Int) {
        BclPacket.Specialized.Data(src, dest, ByteArray(b)).writeTo(outputStream)
    }

    override fun write(b: ByteArray?) {
        b ?: return
        BclPacket.Specialized.Data(src, dest, b).writeTo(outputStream)
    }

    override fun write(b: ByteArray?, off: Int, len: Int) {
        b ?: return
        if (off == 0 && len == b.size)  {
            write(b)
        } else {
            BclPacket.Specialized.Data(src, dest, b.sliceArray(off until len)).writeTo(outputStream)
        }
    }
}