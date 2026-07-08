package com.DSK.modbus.service;

import com.DSK.modbus.model.MeterRowDto;
import com.DSK.modbus.model.PendingRequest;
import com.DSK.modbus.model.ErrorStatusDto;
import java.util.Map;

/**
 * RTU(SerialConnectionManager) / TCP(TcpConnectionManager) 공통 계약.
 * ModbusManager는 이 인터페이스만 의존 — 통신 계층 교체 시 상위 로직 무변경.
 */
public interface IModbusConnectionManager {

	/** 연결 (RTU: portName+baud / TCP: ip+port) */
	boolean connect(ModbusManager modbusManager);

	/** 연결 해제 */
	void disconnect();

	/** 연결 상태 */
	boolean isOpen();

	/** 요청 패킷 빌드 */
	PendingRequest buildRequest(MeterRowDto info);

	/** 요청 큐 적재 */
	void enqueue(PendingRequest request);

	/** 전송 시작 */
	void startSend();

	/** 오류 맵 조회 */
	Map<Long, ErrorStatusDto> getErrorMap();

	void clearAllErrorStatus();

	boolean isCycleComplete();

	// ── TCP 전환 시 CRC 제거·MBAP 헤더 처리는 구현체 내부에서 처리
	// 이 인터페이스는 호출 계약만 정의
}