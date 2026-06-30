package com.DSK.serial.converter;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.DSK.serial.constant.DataType;
import com.DSK.serial.constant.EndianType;

public class ByteConverter {

	private ByteConverter() {
	}

	public static Object convert(byte[] buffer, int offset, DataType dataType, EndianType endianType) {

		int size = getDataSize(dataType);

		byte[] bytes = new byte[size];
		System.arraycopy(buffer, offset, bytes, 0, size);

		bytes = reorder(bytes, endianType);

		ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

		switch (dataType) {

		case UINT16:
			return bb.getShort() & 0xFFFF;

		case INT16:
			return (int) bb.getShort();

		case UINT32:
			return bb.getInt() & 0xFFFFFFFFL;

		case INT32:
			return bb.getInt();

		case FLOAT32:
			return bb.getFloat();

		case DOUBLE64:
			return bb.getDouble();

		default:
			throw new IllegalArgumentException("지원하지 않는 타입 : " + dataType);
		}
	}

	public static int getDataSize(DataType dataType) {

		switch (dataType) {

		case UINT16:
		case INT16:
			return 2;

		case UINT32:
		case INT32:
		case FLOAT32:
			return 4;

		case DOUBLE64:
			return 8;

		default:
			throw new IllegalArgumentException("지원하지 않는 타입 : " + dataType);
		}
	}

	private static byte[] reorder(byte[] bytes, EndianType endianType) {

		switch (endianType) {

		case BIG_ENDIAN:
			return bytes;

		case LITTLE_ENDIAN:
			return reverse(bytes);

		case LITTLE_ENDIAN_BYTE_SWAP:
			return wordSwap(bytes);

		case BIG_ENDIAN_BYTE_SWAP:
			return byteSwap(bytes);

		default:
			throw new IllegalArgumentException("지원하지 않는 엔디안 : " + endianType);
		}
	}

	/**
	 * ABCD -> DCBA
	 */
	private static byte[] reverse(byte[] bytes) {

		byte[] result = bytes.clone();

		for (int i = 0; i < result.length / 2; i++) {

			byte temp = result[i];

			result[i] = result[result.length - 1 - i];

			result[result.length - 1 - i] = temp;
		}

		return result;
	}

	/**
	 * ABCD -> CDAB
	 */
	private static byte[] wordSwap(byte[] bytes) {

		if (bytes.length != 4) {
			return bytes;
		}

		return new byte[] { bytes[2], bytes[3], bytes[0], bytes[1] };
	}

	/**
	 * ABCD -> BADC
	 */
	private static byte[] byteSwap(byte[] bytes) {

		if (bytes.length != 4) {
			return bytes;
		}

		return new byte[] { bytes[1], bytes[0], bytes[3], bytes[2] };
	}
}