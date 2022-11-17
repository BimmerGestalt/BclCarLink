package io.bimmergestalt.bclclient.bcl

import io.bimmergestalt.bcl.BclPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BclPacketTest {
	@Test
	fun testHandshake() {
		val data = ByteArray(8)
		data[1] = 0x04
		data[3] = 0x0e
		data[6] = 0x80.toByte()
		val packet = BclPacket(BclPacket.COMMAND.HANDSHAKE, 0, 0, data)
		val handshakePacket = packet.asSpecialized()
		assert(handshakePacket is BclPacket.Specialized.Handshake)
		assertTrue(packet.data === handshakePacket.data)
		if (handshakePacket is BclPacket.Specialized.Handshake) {
			assertEquals(0x8000, handshakePacket.bufferSize)
			assertEquals(4.toShort(), handshakePacket.version)
			assertEquals(14.toShort(), handshakePacket.instanceId)

			handshakePacket.instanceId = 18
			assertEquals(18.toByte(), data[3])
		}

		// serialize
		val fullData = packet.serialize()
		assertEquals(8 + 8, fullData.size)
		assertEquals(8.toByte(), fullData[7])

		// deserialize
		val newPacket = BclPacket.deserialize(fullData)
		assert(newPacket is BclPacket.Specialized.Handshake)
	}
}