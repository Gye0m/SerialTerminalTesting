package com.DSK.modbus.service;

import com.DSK.modbus.model.LogDto;

/**
 * [ModbusEventListener]
 * 역할: 통신 레이어(ModbusManager)가 UI 레이어로 이벤트를 전달하는 콜백 인터페이스
 * 주요 기능:
 * - 기능 1: 로그 이벤트 — 시스템 로그(onSystemLog), TX/RX 터미널 로그(onTxRx)
 * - 기능 2: 검침 결과 반영 — onMeterValueUpdated(슬레이브/주소/FC로 행 식별 후 값·상태 갱신)
 * - 기능 3: 장애 알림 — 물리 단선(onPhysicalDisconnect), Raw HEX 응답(onRawResponse)
 *
 * 구현체: ModbusTerminal(실제 UI 처리), SafeEventDispatcher(EDT 라우팅 + 예외 방어 래퍼).
 * 통신 스레드는 반드시 SafeEventDispatcher를 거쳐 이 인터페이스를 호출해야 한다.
 */
public interface ModbusEventListener {

	/** 시스템/에러 로그 한 줄 추가. */
	void onSystemLog(LogDto log);

	/** TX/RX 터미널 로그 추가. */
	void onTxRx(String hex);

	/**
	 * 검침 응답으로 특정 행의 값/상태 갱신.
	 *
	 * @param slaveId 응답 슬레이브 ID
	 * @param address 레지스터 주소
	 * @param fc      요청 Function Code (행 고유 식별에 사용)
	 * @param value   표시 값
	 * @param status  상태 문자열 ("정상", "타임아웃", "CRC오류", "장비거부" 등)
	 * @param avgPacketResponseTime 패킷 평균 웅답 시간
	 */
	void onMeterValueUpdated(int slaveId, int address, int fc, String value, String status,
			int avgPacketResponseTime);

	/** Raw HEX 응답 도착 시 응답창 갱신. */
	void onRawResponse(String hex);

	/** 물리 포트 단선 감지. */
	void onPhysicalDisconnect();
	/** 검침 끝남. */
	//	void onMeteringFinished();
}