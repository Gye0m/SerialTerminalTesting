package com.DSK.modbus.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.DSK.modbus.model.constant.FunctionCode;

/**
 * [ErrorStatusDto]
 * 역할: 검침 항목(슬레이브ID + FC + 주소) 단위의 누적 오류 통계를 보관하는 모델(DTO)
 * 주요 기능:
 * - 기능 1: 오류 유형별 카운팅 — 타임아웃 / CRC 오류 / 장비거부(Modbus Exception) / 물리 단선을
 *           각각 독립 카운터로 집계 (원인 진단 정확성 확보)
 * - 기능 2: 재시도 횟수 및 연속 실패 횟수 추적 — 정상 응답 수신 시 연속 실패만 리셋
 * - 기능 3: 마지막 오류 발생 시각 기록 (오류 이력 다이얼로그 표시용)
 *
 * 스레드 모델:
 * - write: timeoutScheduler 스레드 (ModbusManager의 오류 핸들러)
 * - read : EDT (오류 이력 다이얼로그, Health Monitor)
 * - 모든 증감 메서드는 synchronized, 카운터 필드는 volatile — torn read 차단
 */
public class ErrorStatusDto {

	// =========================================================================
	// ✅ [수정] slaveId, functionCode 필드 추가
	// 기존: address 단독 키 → 같은 주소를 다른 슬레이브/FC로 사용할 때 충돌
	// 변경: (slaveId, functionCode, address) 3개 복합 식별자로 고유성 보장
	// =========================================================================
	private final int slaveId;
	private final FunctionCode functionCode;
	private final int address;
	private final String itemName;

	// [CONC-3] 카운터 필드 volatile — timeoutScheduler 스레드 write, EDT read 사이
	// torn read 방지. 증감 메서드는 아래에서 synchronized로 원자성 보장.
	private volatile int totalErrCount;
	private volatile int crcErrCount;
	private volatile int timeoutCount;
	private volatile int modbusExceptionCount; // [BUG-3] Modbus Exception 전용 카운터 (timeout과 분리)
	private volatile int physicalDisconnectCount; // 물리 단선 횟수 (전역 이벤트; 패널 카운터와 병행)
	private volatile int totalRetryCount;
	private volatile int consecutiveFails;
	private volatile String lastErrTime;

	// =========================================================================
	// 생성자
	// =========================================================================

	/** ✅ [신규] 올바른 생성자 — slaveId + FC 포함 */
	public ErrorStatusDto(int slaveId, FunctionCode functionCode, int address, String itemName) {
		this.slaveId = slaveId;
		this.functionCode = functionCode != null ? functionCode : FunctionCode.READ_INPUT_REGISTERS;
		this.address = address;
		this.itemName = itemName;
		this.totalErrCount = 0;
		this.crcErrCount = 0;
		this.timeoutCount = 0;
		this.modbusExceptionCount = 0;
		this.physicalDisconnectCount = 0;
		this.totalRetryCount = 0;
		this.consecutiveFails = 0;
		this.lastErrTime = "-";
	}

	/**
	 * @deprecated slaveId/FC가 기본값(Slave=0, FC=READ_INPUT_REGISTERS)으로 초기화됨.
	 *             신규 코드는 반드시 위 4인자 생성자를 사용할 것.
	 */
	@Deprecated
	public ErrorStatusDto(int address, String itemName) {
		this(0, FunctionCode.READ_INPUT_REGISTERS, address, itemName);
	}

	// =========================================================================
	// 오류 기록 메서드
	// =========================================================================

	// [CONC-3] 모든 증감 메서드 synchronized — 복합 연산(++)의 원자성 보장.
	// timeoutScheduler 스레드가 write, EDT가 getter로 read 하는 구조에서
	// read-modify-write 경합을 차단한다.

	public synchronized void incrementRetry() {
		this.totalRetryCount++;
		updateTime();
	}

	public synchronized void triggerTimeout() {
		this.totalErrCount++;	
		this.timeoutCount++;
		this.consecutiveFails++;
		updateTime();
	}

	public synchronized void triggerCrcError() {
		this.totalErrCount++;
		this.crcErrCount++;
		this.consecutiveFails++;
		updateTime();
	}

	/**
	 * [BUG-3] Modbus Exception(장비 거부) 전용 기록.
	 * 이전에는 triggerTimeout()으로 대리 처리해 timeoutCount가 오염됐다.
	 * Exception은 타임아웃이 아닌 "슬레이브가 요청을 거부한 것"이므로
	 * 별도 카운터로 분리해야 원인 진단이 정확해진다.
	 */
	public synchronized void triggerModbusException() {
		this.totalErrCount++;
		this.modbusExceptionCount++;
		this.consecutiveFails++;
		updateTime();
	}

	/** ✅ [신규] 물리 단선 발생 기록 */
	public synchronized void triggerPhysicalDisconnect() {
		this.physicalDisconnectCount++;
		this.totalErrCount++;
		this.consecutiveFails++;
		updateTime();
	}

	public synchronized void resetConsecutiveFails() {
		this.consecutiveFails = 0;
	}

	private void updateTime() {
		this.lastErrTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss:SSS\n"));
	}

	// =========================================================================
	// Getter
	// =========================================================================

	public int getSlaveId() {
		return slaveId;
	}

	public FunctionCode getFunctionCode() {
		return functionCode;
	}

	public int getAddress() {
		return address;
	}

	public String getItemName() {
		return itemName;
	}

	public int getTotalErrCount() {
		return totalErrCount;
	}

	public int getCrcErrCount() {
		return crcErrCount;
	}

	public int getTimeoutCount() {
		return timeoutCount;
	}

	public int getModbusExceptionCount() {
		return modbusExceptionCount;
	}

	public int getPhysicalDisconnectCount() {
		return physicalDisconnectCount;
	}

	public int getTotalRetryCount() {
		return totalRetryCount;
	}

	public int getConsecutiveFails() {
		return consecutiveFails;
	}

	public String getLastErrTime() {
		return lastErrTime;
	}
}