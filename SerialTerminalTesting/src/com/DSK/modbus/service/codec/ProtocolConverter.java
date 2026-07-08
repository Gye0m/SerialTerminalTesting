package com.DSK.modbus.service.codec;

import java.nio.charset.Charset;

/**
 * [ProtocolConverter]
 * 역할: 통신 데이터의 형식 변환(ASCII/HEX/바이트)과 Modbus CRC-16 계산을 담당하는 정적 유틸리티
 * 주요 기능:
 * - 기능 1: ASCII ↔ byte[] 변환 (MS949 인코딩 — 국내 산업장비 한글 응답 대응)
 * - 기능 2: HEX 문자열 ↔ byte[] 변환 (Raw HEX 수동 송신 패널에서 사용)
 * - 기능 3: CRC-16(Modbus) 계산 — 다항식 0xA001, 초기값 0xFFFF 표준 알고리즘
 *
 * 모든 메서드는 상태 없는(stateless) 정적 메서드 — 스레드 안전
 */
public class ProtocolConverter {

	/**
	 * ASCII 문자열을 전송용 바이트 배열로 변환한다. 개행(\n)을 종단자로 덧붙인다.
	 * @param text 전송할 문자열
	 * @return MS949 인코딩된 바이트 배열 (개행 포함)
	 */
	public static byte[] convertAsciiToBytes(String text) {
		String packet = text + "\n";
		return packet.getBytes(Charset.forName("MS949"));
	}

	public static String convertBytesToAscii(byte[] buffer, int length) {
		return new String(buffer, 0, length, Charset.forName("MS949"));
	}

	public static byte[] convertHexToBytes(String hexText) {
		hexText = hexText.replaceAll("\\s+", "");

		if (hexText.length() % 2 != 0) {
			throw new IllegalArgumentException("HEX 길이는 짝수여야 합니다.");
		}
		byte[] bytes = new byte[hexText.length() / 2];

		for (int i = 0; i < bytes.length; i++) {
			bytes[i] = (byte) Integer.parseInt(hexText.substring(i * 2, i * 2 + 2), 16);
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
	/**
	 * Modbus RTU 표준 CRC-16을 계산한다.
	 * 알고리즘: 초기값 0xFFFF, 다항식 0xA001(reflected 0x8005), 바이트 단위 XOR 후 8회 시프트.
	 * @param bytes  CRC를 계산할 원본 데이터 (CRC 필드 제외 구간)
	 * @param length 계산에 포함할 바이트 수 (보통 packet.length - 2)
	 * @return 16비트 CRC 값 (하위 바이트 먼저 전송하는 little-endian으로 패킷에 부착)
	 */
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