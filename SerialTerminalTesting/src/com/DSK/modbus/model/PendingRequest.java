package com.DSK.modbus.model;

import java.util.Arrays;

import com.DSK.modbus.model.constant.DataType;
import com.DSK.modbus.model.constant.EndianType;
import com.DSK.modbus.model.constant.FunctionCode;

/**
 * 송신 큐에 올라가는 요청 1건의 런타임 컨텍스트.
 *
 * ┌──────────────────────────────────────────────────────┐
 * │  MeterRowDto   → 테이블 행 설정값 (정적 구성)           │
 * │  PendingRequest → 요청 1건 실행 단위 (동적, 소멸형)      │
 * │  ErrorStatusDto → 주소별 누적 에러 통계 (전역 유지)      │
 * └──────────────────────────────────────────────────────┘
 *
 * PendingRequest 가 직접 보유하는 것:
 *   - byte[] packet       : MeterRowDto 에 없고 있어선 안 되는 전송용 바이트
 *   - int retryRemaining  : 요청 실행 중 소멸되는 재시도 잔여 횟수
 *
 * 메타데이터(주소·SlaveId·DataType·EndianType·Scale·ItemName)는
 * MeterRowDto 에 위임해 필드 중복을 제거한다.
 */
public class PendingRequest {

	// ── 자체 보유 필드 ────────────────────────────────────────────────────────
	private final byte[] packet; // CRC 포함 완성 패킷 (방어 복사)
	private int retryRemaining;

	// ── MeterRowDto 참조 (읽기 전용) ─────────────────────────────────────────
	private final MeterRowDto rowDto;

	// =========================================================================
	// 생성자
	// =========================================================================
	public PendingRequest(byte[] packet, MeterRowDto rowDto, int retryCount) {
		if (packet == null)
			throw new IllegalArgumentException("packet 은 null 불가");
		if (rowDto == null)
			throw new IllegalArgumentException("rowDto 는 null 불가");

		this.packet = Arrays.copyOf(packet, packet.length);
		this.rowDto = rowDto;
		this.retryRemaining = Math.max(0, retryCount);
	}

	// =========================================================================
	// MeterRowDto 위임 접근자
	// =========================================================================
	public int getAddress() {
		return rowDto.getAddress();
	}

	public int getSlaveId() {
		return rowDto.getSlaveId();
	}

	public DataType getDataType() {
		return rowDto.getDataType() != null ? rowDto.getDataType() : DataType.UINT16;
	}

	public EndianType getEndianType() {
		return rowDto.getEndianType() != null ? rowDto.getEndianType() : EndianType.LITTLE_ENDIAN;
	}

	public double getScale() {
		return rowDto.getScale() == 0.0 ? 1.0 : rowDto.getScale();
	}

	public FunctionCode getFunctionCode() {
		return rowDto.getFunctionCode() != null ? rowDto.getFunctionCode() : FunctionCode.READ_INPUT_REGISTERS;
	}

	public String getItemName() {
		return rowDto.getItemName();
	}

	public MeterRowDto getRowDto() {
		return rowDto;
	}

	// =========================================================================
	// 자체 필드 접근자
	// =========================================================================
	public byte[] getPacket() {
		return packet;
	}

	public int getRetryRemaining() {
		return retryRemaining;
	}

	public boolean canRetry() {
		return retryRemaining > 0;
	}

	/**
	 * 재시도용 사본 반환. 원본 객체는 변경하지 않는다.
	 * packet 은 불변이므로 재복사 없이 공유해도 안전하다.
	 */
	public PendingRequest retryInstance() {
		return new PendingRequest(this.packet, this.rowDto, this.retryRemaining - 1);
	}

	@Override
	public String toString() {
		String str = String.format("PendingRequest[addr=0x%04X, slave=%d, type=%s, endian=%s, retry=%d, item=%s]",
				getAddress(), getSlaveId(), getDataType(), getEndianType(), retryRemaining, getItemName());
		return str;
	}
}
