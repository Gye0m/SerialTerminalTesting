package com.DSK.modbus.service.codec;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.DSK.modbus.model.constant.DataType;
import com.DSK.modbus.model.constant.EndianType;

/**
 * Modbus 레지스터 바이트 배열 → Java 값 변환기.
 *
 * [엔디안 처리 방식]
 * BIG_ENDIAN             : ABCD → ABCD (변경 없음)
 * LITTLE_ENDIAN          : ABCD → DCBA (전체 역순)
 * LITTLE_ENDIAN_BYTE_SWAP: ABCD → CDAB (워드 단위 스왑)
 * BIG_ENDIAN_BYTE_SWAP   : ABCD → BADC (바이트 단위 스왑)
 *
 * [2바이트(UINT16/INT16) 주의]
 * wordSwap/byteSwap은 4바이트/8바이트만 처리한다.
 * 2바이트 타입에 LITTLE_ENDIAN_BYTE_SWAP 또는 BIG_ENDIAN_BYTE_SWAP을
 * 지정하면 바이트 순서가 변경되지 않는다 (= BIG_ENDIAN 동작).
 * 2바이트 little-endian이 필요하면 LITTLE_ENDIAN을 사용할 것.
 */
public class ByteConverter {

	private ByteConverter() {
	}

	// =========================================================================
	// 변환 — 핵심 public API
	// =========================================================================

	/**
	 * 바이트 배열을 지정된 DataType/EndianType으로 변환해 Java 숫자 객체로 반환한다.
	 *
	 * <p>반환 타입 정리:
	 * <ul>
	 *   <li>UINT16  → Integer (0 ~ 65535)</li>
	 *   <li>INT16   → Integer (-32768 ~ 32767)</li>
	 *   <li>UINT32  → Long (0 ~ 4,294,967,295)</li>
	 *   <li>INT32   → Integer</li>
	 *   <li>FLOAT32 → Float  ← NaN·Infinity 반환 가능 ({@link #isSpecialFloatValue} 로 확인)</li>
	 *   <li>DOUBLE64→ Double ← NaN·Infinity 반환 가능</li>
	 * </ul>
	 */
	public static Object convert(byte[] buffer, int offset, DataType dataType, EndianType endianType) {
		if (dataType == null)
			throw new IllegalArgumentException("dataType 이 null 입니다.");
		if (endianType == null)
			throw new IllegalArgumentException("endianType 이 null 입니다.");
		if (buffer == null)
			throw new IllegalArgumentException("buffer 가 null 입니다.");

		int size = getDataSize(dataType);

		if (offset < 0 || offset + size > buffer.length) {
			throw new IllegalArgumentException(
					String.format("버퍼 범위 초과 — offset=%d, 필요크기=%d, 버퍼길이=%d", offset, size, buffer.length));
		}

		byte[] bytes = new byte[size];
		System.arraycopy(buffer, offset, bytes, 0, size);
		bytes = reorder(bytes, endianType);

		ByteBuffer bb = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);

		return switch (dataType) {
		case UINT16 -> bb.getShort() & 0xFFFF;
		case INT16 -> (int) bb.getShort();
		case UINT32 -> bb.getInt() & 0xFFFFFFFFL;
		case INT32 -> bb.getInt();
		case FLOAT32 -> bb.getFloat(); // NaN·Infinity 가능 — 호출부에서 isSpecialFloatValue() 확인
		case DOUBLE64 -> bb.getDouble(); // NaN·Infinity 가능
		default -> throw new IllegalArgumentException("지원하지 않는 타입: " + dataType);
		};
	}

	// =========================================================================
	// [신규] 특수값 감지 헬퍼 — FLOAT32/DOUBLE64 NaN·Infinity 감지
	//
	// 슬레이브가 센서 이상(단선, 측정 불가) 시 0x7FC00000(Float NaN) 같은
	// 특수값을 반환하는 경우가 있다. 이를 그대로 DB나 UI에 흘리면:
	//   - String.format("%.4f", NaN) → "NaN" 문자열 저장
	//   - 수치 비교 쿼리 오작동 / 잘못된 집계
	// 호출부(ModbusManager.handleReadResponse)에서 convert() 결과를 이 메서드로
	// 검사한 후 처리를 분기한다.
	// =========================================================================

	/**
	 * FLOAT32·DOUBLE64 변환 결과가 NaN 또는 Infinity인지 확인한다.
	 * 다른 타입(Integer, Long)은 항상 false를 반환한다.
	 *
	 * @param raw {@link #convert} 의 반환값
	 * @return true이면 슬레이브 센서 이상 또는 오버플로우 의심
	 */
	public static boolean isSpecialFloatValue(Object raw) {
		if (raw instanceof Float f)
			return Float.isNaN(f) || Float.isInfinite(f);
		if (raw instanceof Double d)
			return Double.isNaN(d) || Double.isInfinite(d);
		return false;
	}

	/**
	 * raw 값이 NaN인지만 확인한다 (Infinity는 false).
	 */
	public static boolean isNaN(Object raw) {
		if (raw instanceof Float f)
			return Float.isNaN(f);
		if (raw instanceof Double d)
			return Double.isNaN(d);
		return false;
	}

	/**
	 * raw 값이 Infinity인지만 확인한다 (NaN은 false).
	 */
	public static boolean isInfinite(Object raw) {
		if (raw instanceof Float f)
			return Float.isInfinite(f);
		if (raw instanceof Double d)
			return Double.isInfinite(d);
		return false;
	}

	// =========================================================================
	// 크기 조회
	// =========================================================================

	public static int getDataSize(DataType dataType) {
		if (dataType == null)
			throw new IllegalArgumentException("dataType 이 null 입니다.");
		return switch (dataType) {
		case UINT16, INT16 -> 2;
		case UINT32, INT32, FLOAT32 -> 4;
		case DOUBLE64 -> 8;
		default -> throw new IllegalArgumentException("지원하지 않는 타입: " + dataType);
		};
	}

	// =========================================================================
	// 내부 — 바이트 재배열
	// =========================================================================

	private static byte[] reorder(byte[] bytes, EndianType endianType) {
		return switch (endianType) {
		case BIG_ENDIAN -> bytes;
		case LITTLE_ENDIAN -> reverse(bytes);
		case LITTLE_ENDIAN_BYTE_SWAP -> wordSwap(bytes);
		case BIG_ENDIAN_BYTE_SWAP -> byteSwap(bytes);
		default -> throw new IllegalArgumentException("지원하지 않는 엔디안: " + endianType);
		};
	}

	/** ABCD → DCBA */
	private static byte[] reverse(byte[] bytes) {
		byte[] result = bytes.clone();
		for (int i = 0, j = result.length - 1; i < j; i++, j--) {
			byte tmp = result[i];
			result[i] = result[j];
			result[j] = tmp;
		}
		return result;
	}

	/**
	 * 워드(2바이트) 단위 스왑.
	 *
	 * [4바이트] ABCD → CDAB  (워드 2개 역전: [AB][CD] → [CD][AB])
	 * [8바이트] ABCDEFGH → GHEFCDAB  (워드 4개 역전: [AB][CD][EF][GH] → [GH][EF][CD][AB])
	 *
	 * ✅ [수정] 8바이트 케이스 교체
	 *   이전: EFGHABCD — 32비트 상위/하위 블록 교환 (4바이트 CDAB 논리와 불일치)
	 *   변경: GHEFCDAB — 워드 단위 완전 역전 (4바이트와 동일한 논리 적용)
	 *
	 * ⚠️ 실장비 확인 필수: 슬레이브 벤더에 따라 EFGHABCD 관례를 사용하는 장비가 있음.
	 *   (예: 일부 Schneider Modicon 계열)
	 *   현장 장비의 레지스터 설명서에서 Double 값의 바이트 배치를 Hex로 확인 후 적용할 것.
	 *
	 * [2바이트] 단일 워드 — 스왑 대상 없음, BIG_ENDIAN과 동일하게 원본 반환.
	 *   2바이트 little-endian이 필요하면 LITTLE_ENDIAN 모드를 사용할 것.
	 */
	private static byte[] wordSwap(byte[] bytes) {
		if (bytes.length == 4)
			return new byte[] { bytes[2], bytes[3], bytes[0], bytes[1] };
		if (bytes.length == 8)
			// ✅ [수정] 워드 4개 완전 역전: [GH][EF][CD][AB]
			return new byte[] { bytes[6], bytes[7], bytes[4], bytes[5], bytes[2], bytes[3], bytes[0], bytes[1] };
		// 2바이트: 단일 워드, 스왑 불가 → 원본 반환 (= BIG_ENDIAN 동작)
		return bytes;
	}

	/**
	 * 바이트 단위 스왑 (각 워드 내 2바이트 순서 반전).
	 *
	 * [4바이트] ABCD → BADC
	 * [8바이트] ABCDEFGH → BADCFEHG
	 * [2바이트] 원본 반환 (= BIG_ENDIAN 동작)
	 *
	 * ⚠️ INT16/UINT16 + BIG_ENDIAN_BYTE_SWAP 조합:
	 *   2바이트 타입은 여기서 변환이 일어나지 않는다.
	 *   AB → BA가 필요하면 LITTLE_ENDIAN(전체 역순)을 사용할 것.
	 */
	private static byte[] byteSwap(byte[] bytes) {
		if (bytes.length == 4)
			return new byte[] { bytes[1], bytes[0], bytes[3], bytes[2] };
		if (bytes.length == 8)
			return new byte[] { bytes[1], bytes[0], bytes[3], bytes[2], bytes[5], bytes[4], bytes[7], bytes[6] };
		// 2바이트: 원본 반환 (= BIG_ENDIAN 동작)
		return bytes;
	}
}