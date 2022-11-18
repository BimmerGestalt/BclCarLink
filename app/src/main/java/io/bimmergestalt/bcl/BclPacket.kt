package io.bimmergestalt.bcl

import io.bimmergestalt.bcl.ByteArrayExt.toHexString
import org.tinylog.kotlin.Logger
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset


open class BclPacket(
	val command: COMMAND,
	val src: Short,
	val dest: Short,
	val data: ByteArray
) {
	companion object {
		fun deserialize(data: ByteArray): BclPacket {
			return readFrom(ByteArrayInputStream(data))
		}

		@Throws(IOException::class)
		fun readFrom(stream: InputStream): BclPacket {
			val reader = DataInputStream(stream)
			val command = COMMAND.fromShort(reader.readShort())
			val src = reader.readShort()
			val dest = reader.readShort()
			val dataSize = reader.readShort().toInt()
			val data = ByteArray(dataSize)
			reader.readFully(data)
			val packet = BclPacket(command, src, dest, data).asSpecialized()
			Logger.debug { "Read $packet"}
			return packet
		}
	}

	/**
	 * Possible commands from https://github.com/BimmerGestalt/wireshark_bmw_bcl/blob/main/bmw_bcl.lua#L33
	 */
	enum class COMMAND(val value: Short) {
		UNKNOWN(0),
		OPEN(1),
		DATA(2),
		CLOSE(3),
		HANDSHAKE(4),
		SELECTPROTO(5),
		DATAACK(6),
		ACK(7),
		KNOCK(8),
		LAUNCH(9),
		HANGUP(10),
		BROADCAST(11),
		REGISTER(12);

		companion object {
			fun fromShort(value: Short): COMMAND = values().firstOrNull { it.value == value } ?: UNKNOWN
		}
	}

	/** Common packets */
	object Specialized {
		class Handshake(data: ByteArray = ByteArray(8)): BclPacket(COMMAND.HANDSHAKE, 0, 0, data) {
			var version by ShortField(data, 0)
			var instanceId by ShortField(data, 2)
			var bufferSize by IntField(data, 4)
			override fun toString(): String {
				return "BclPacket\$Handshake(version=$version, instanceId=$instanceId, bufferSize=0x${bufferSize.toString(16)})"
			}

		}
		class SelectProto(data: ByteArray = ByteArray(2)): BclPacket(COMMAND.SELECTPROTO, 0, 0, data) {
			var version by ShortField(data, 0)
			override fun toString(): String {
				return "BclPacket\$SelectProto(version=$version)"
			}
		}
		fun SelectProto(version: Short): SelectProto {
			return SelectProto().apply { this.version = version }
		}
		class Knock(data: ByteArray = ByteArray(12)): BclPacket(COMMAND.KNOCK, 0, 0, data) {
			val serial: ByteArray
			val btAddr: ByteArray
			val macAddr: ByteArray
			val wifiAddr: ByteArray
			val appType: Short
			val param1: Short
			init {
				val buffer = ByteBuffer.wrap(data)
				serial = ByteArray(buffer.short.toInt())
				buffer.get(serial)
				btAddr = ByteArray(buffer.short.toInt())
				buffer.get(btAddr)
				macAddr = ByteArray(buffer.short.toInt())
				buffer.get(macAddr)
				wifiAddr = ByteArray(buffer.short.toInt())
				buffer.get(wifiAddr)
				appType = buffer.short
				param1 = buffer.short
			}
			override fun toString(): String {
				return "BclPacket\$Knock(appType=$appType, serial=${serial.toString(Charset.defaultCharset())}, btAddr=${btAddr.toString(
					Charset.defaultCharset())}, data=${data.toHexString()})"
			}
		}
		fun Knock(serial: ByteArray, btAddr: ByteArray,
		          macAddr: ByteArray, wifiAddr: ByteArray,
		          appType: Short, param1: Short): Knock {
			val buffer = ByteBuffer.allocate(128)
			buffer.putShort(serial.size.toShort())
			buffer.put(serial)
			buffer.putShort(btAddr.size.toShort())
			buffer.put(btAddr)
			buffer.putShort(macAddr.size.toShort())
			buffer.put(macAddr)
			buffer.putShort(wifiAddr.size.toShort())
			buffer.put(wifiAddr)
			buffer.putShort(appType)
			buffer.putShort(param1)
			val output = ByteArray(buffer.position())
			buffer.flip()
			buffer.get(output)
			return Knock(output)
		}
		class Open(src: Short, dest: Short): BclPacket(COMMAND.OPEN, src, dest, ByteArray(0)) {
			override fun toString(): String {
				return "BclPacket\$Open(src=$src, dest=$dest)"
			}
		}
		class Close(src: Short, dest: Short): BclPacket(COMMAND.CLOSE, src, dest, ByteArray(0)) {
			override fun toString(): String {
				return "BclPacket\$Close(src=$src, dest=$dest)"
			}
		}
		class Data(src: Short, dest: Short, data: ByteArray): BclPacket(COMMAND.DATA, src, dest, data) {
			override fun toString(): String {
				return if (data.size < 32) {
					"BclPacket\$Data(src=$src, dest=$dest, data=${data.contentToString()})"
				} else {
					"BclPacket\$Data(src=$src, dest=$dest, dataLen=${data.size})"
				}
			}
		}
		class DataAck(data: ByteArray = ByteArray(4)): BclPacket(COMMAND.DATAACK, 0, 0, data) {
			var count by IntField(data, 0)
			override fun toString(): String {
				return "BclPacket\$DataAck(count=$count)"
			}
		}
		fun DataAck(count: Int): DataAck {
			return DataAck().apply { this.count = count }
		}
	}

	override fun equals(other: Any?): Boolean {
		if (this === other) return true
		if (other !is BclPacket) return false

		other

		if (command != other.command) return false
		if (src != other.src) return false
		if (dest != other.dest) return false
		if (!data.contentEquals(other.data)) return false

		return true
	}

	override fun hashCode(): Int {
		var result = command.hashCode()
		result = 31 * result + src
		result = 31 * result + dest
		result = 31 * result + data.contentHashCode()
		return result
	}



	fun serialize(): ByteArray {
		val output = ByteArrayOutputStream(8 + data.size)
		writeTo(output)
		return output.toByteArray()
	}

	fun writeTo(stream: OutputStream) {
		Logger.debug { "Writing $this"}
		// write to the stream, needs to be big endian
		val writer = DataOutputStream(stream)
		writer.writeShort(command.value.toInt())
		writer.writeShort(src.toInt())
		writer.writeShort(dest.toInt())
		writer.writeShort(data.size)
		writer.write(data)
		Thread.sleep(200)
	}

	fun asSpecialized(): BclPacket {
		return if (command == COMMAND.HANDSHAKE && data.size == 8) {
			Specialized.Handshake(data)
		} else if (command == COMMAND.KNOCK && data.size > 12) {
			Specialized.Knock(data)
		} else if (command == COMMAND.OPEN) {
			Specialized.Open(src, dest)
		} else if (command == COMMAND.CLOSE) {
			Specialized.Close(src, dest)
		} else if (command == COMMAND.DATA) {
			Specialized.Data(src, dest, data)
		} else if (command == COMMAND.DATAACK) {
			Specialized.DataAck(data)
		} else {
			this
		}
	}

	override fun toString(): String {
		return if (data.size < 32) {
			"BclPacket($command, src=$src, dest=$dest, data=${data.contentToString()})"
		} else {
			"BclPacket($command, src=$src, dest=$dest, dataLen=${data.size})"
		}
	}
}

