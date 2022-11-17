package io.bimmergestalt.bcl

import io.bimmergestalt.bcl.ByteArrayExt.getIntAt
import io.bimmergestalt.bcl.ByteArrayExt.getShortAt
import io.bimmergestalt.bcl.ByteArrayExt.setIntAt
import io.bimmergestalt.bcl.ByteArrayExt.setShortAt
import kotlin.reflect.KProperty

object ByteArrayExt {
	fun ByteArray.getIntAt(index: Int): Int {
		if (index < 0 || index + 3 >= size) {
			throw ArrayIndexOutOfBoundsException(index)
		}
		return (0xff and this[index].toInt() shl 24) +
				(0xff and this[index + 1].toInt() shl 16) +
				(0xff and this[index + 2].toInt() shl 8) +
				(0xff and this[index + 3].toInt() shl 0)
	}
	fun ByteArray.getShortAt(index: Int): Short {
		if (index < 0 || index + 1 >= size) {
			throw ArrayIndexOutOfBoundsException(index)
		}
		return ((0xff and this[index].toInt() shl 8) +
				(0xff and this[index + 1].toInt() shl 0)).toShort()
	}
	fun ByteArray.setIntAt(index: Int, value: Int) {
		if (index < 0 || index + 3 >= size) {
			throw ArrayIndexOutOfBoundsException(index)
		}
		this[index] = (0xff and (value shr 24)).toByte()
		this[index + 1] = (0xff and (value shr 16)).toByte()
		this[index + 2] = (0xff and (value shr 8)).toByte()
		this[index + 3] = (0xff and (value shr 0)).toByte()
	}
	fun ByteArray.setShortAt(index: Int, value: Int) {
		if (index < 0 || index + 1 >= size) {
			throw ArrayIndexOutOfBoundsException(index)
		}
		this[index] = (0xff and (value shr 8)).toByte()
		this[index + 1] = (0xff and (value shr 0)).toByte()
	}
	fun ByteArray.setShortAt(index: Int, value: Short) = setShortAt(index, value.toInt())
}

class ShortField(val data: ByteArray, val index: Int) {
	operator fun getValue(_t: Any?, _p: KProperty<*>): Short = data.getShortAt(index)
	operator fun setValue(_t: Any?, _p: KProperty<*>, value: Short) = data.setShortAt(index, value)
}
class IntField(val data: ByteArray, val index: Int) {
	operator fun getValue(_t: Any?, _p: KProperty<*>): Int = data.getIntAt(index)
	operator fun setValue(_t: Any?, _p: KProperty<*>, value: Int) = data.setIntAt(index, value)
}