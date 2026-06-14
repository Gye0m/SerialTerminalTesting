package com.DSK.serial;

import java.nio.charset.Charset;

public class ProtocolConverter {
	public static byte[] convertAsciiToBytes(String text) {
		String packet = text + "\n";
		return packet.getBytes(Charset.forName("MS949"));
	}

	public static String convertBytesToAscii(byte[] buffer, int length) {
		return new String(buffer, 0, length, Charset.forName("MS949"));
	}

	public static byte[] convertHexToBytes(String hexText) throws NumberFormatException {
		String[] tokens = hexText.trim().split("\\s+");
		byte[] bytes = new byte[tokens.length];

		for (int i = 0; i < tokens.length; i++) {
			int parsedInt = Integer.parseInt(tokens[i], 16);
			bytes[i] = (byte) parsedInt;
		}
		return bytes;
	}

	public static String convertBytesToHex(byte[] buffer, int length) {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < length; i++) {
			// 마이너스 성향의 헥사 데이터 처리 위해 '0xFF' 씌워줌
			sb.append(String.format("%02X ", buffer[i] & 0xFF));
		}
		return sb.toString().trim();
	}

	// --------- 표준 CRC-16 계산 알고리즘 ---------
	// rawBytes 받아서 CRC 계산
	public static int calculateCRC16(byte[] bytes, int length) {
		int crc = 0xFFFF;
		for (int i = 0; i < length; i++) {
			crc ^= (bytes[i] & 0xFF); // 8비트로 쪼개서 비트 계산 -> 8
			for (int j = 0; j < 8; j++) {
				if ((crc & 0x0001) != 0) {
					crc = (crc >> 1) ^ 0xA001;
				} else {
					crc >>= 1;
				}
			}
		}
		return crc & 0xFFFF;
	}
}