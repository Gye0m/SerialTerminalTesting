package com.DSK.modbus.service;

import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

/**
 * jSerialComm 이벤트 기반 패킷 수신 및 조립.
 *
 * [★ 재시도 핵심 수정 — FIX-★ 단편화(Fragmentation) 처리]
 * getExpectedLength() 반환 -1의 두 가지 의미를 구분해야 한다:
 *
 *   케이스 A: buf.length < 3  → 아직 바이트 미도착 (SlaveID·FC·ByteCount 미완성)
 *             → break(대기). 절대 스킵 금지.
 *
 *   케이스 B: buf.length >= 3 && FC 불명
 *             → 진짜 알 수 없는 FC → 선두 1바이트 스킵
 *
 * 이전 코드는 케이스 A를 B와 동일하게 "선두 스킵"으로 처리했다.
 * 예: 9600bps에서 [0x01] 1바이트 도착 → getExpectedLength=-1 → 0x01(SlaveID) 날림
 *     이후 [0x04][0x04][data...] 도착 → SlaveID 불일치 → 전체 폐기 → 타임아웃 → 재시도
 *
 * [FIX-1] attach() 시 rxBuffer 초기화 — 재연결 시 잔여 바이트 오염 방지
 * [FIX-2] 물리 단선 즉각 차단 — handlePortDisconnect() 선행 후 disconnect()
 * [FIX-3] synchronized 잠금 최소화 — 패킷 추출만 lock, processHex()는 lock 해제 후
 * [FIX-4] SlaveID 탐색 O(n²) → O(n) 개선
 */
public class PacketReceiver {

	private static final Logger log = LoggerFactory.getLogger(PacketReceiver.class);

	// Modbus RTU 표준 최대 프레임 256바이트
	// 초과 시 desync로 판단하여 버퍼 강제 초기화
	private static final int MAX_FRAME_BYTES = 256;

	private final SerialConnectionManager connectionManager;
	private final ModbusEventListener listener;
	private final ModbusManager modbusManager;

	// RawHexManager — null 가능 (부가 기능)
	private RawHexManager rawHexManager;

	private final ByteArrayOutputStream rxBuffer = new ByteArrayOutputStream();

	// volatile: jSerialComm 내부 스레드(read)와 송신 스레드(write) 사이 가시성 보장
	private volatile int currentSlaveId = 1;

	public PacketReceiver(SerialConnectionManager connectionManager, ModbusEventListener listener,
			ModbusManager modbusManager) {
		this.connectionManager = connectionManager;
		this.listener = listener;
		this.modbusManager = modbusManager;
	}

	public void setRawHexManager(RawHexManager rawHexManager) {
		this.rawHexManager = rawHexManager;
	}

	public void setExpectedSlaveId(int slaveId) {
		this.currentSlaveId = slaveId;
	}

	// =========================================================================
	// [FIX-1] attach() — rxBuffer 초기화
	//
	// 재연결 시 이전 연결의 불완전한 바이트가 남아있으면
	// 새 포트의 첫 응답이 오염된 버퍼와 합쳐져 getExpectedLength() 오작동
	// =========================================================================
	public void attach(SerialPort port) {
		synchronized (this) {
			rxBuffer.reset();
		}
		log.debug("PacketReceiver attach — rxBuffer 초기화, 기대 SlaveID={}", currentSlaveId);

		port.addDataListener(new SerialPortDataListener() {
			@Override
			public int getListeningEvents() {
				return SerialPort.LISTENING_EVENT_DATA_AVAILABLE | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
			}

			@Override
			public void serialEvent(SerialPortEvent event) {

				// =====================================================================
				// [FIX-2] 물리 단선 — modbusManager 즉각 차단 후 disconnect
				//
				// 이전 문제:
				//   connectionManager.disconnect() → listener.onPhysicalDisconnect()
				//   → SafeEventDispatcher.invokeLater() → EDT에서 handlePortDisconnect()
				//
				//   disconnect()와 handlePortDisconnect() 사이 공백에서
				//   timeoutScheduler가 닫힌 포트에 write 시도
				//   → IOException → sendNextPacket() 스케줄 루프 폭발
				//
				// 수정 흐름:
				//   ① modbusManager.handlePortDisconnect()  ← synchronized 즉각 차단
				//   ② connectionManager.disconnect()         ← 포트 자원 해제
				//   ③ listener.onPhysicalDisconnect()        ← UI 업데이트(invokeLater)
				// =====================================================================
				if (event.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
					log.warn("물리 단선 감지 — ModbusManager 즉각 차단 후 포트 해제");
					modbusManager.handlePortDisconnect(); // synchronized — 타임아웃·큐 즉각 취소
					connectionManager.disconnect();
					listener.onPhysicalDisconnect(); // SafeEventDispatcher → invokeLater(EDT)
					return;
				}

				if (event.getEventType() != SerialPort.LISTENING_EVENT_DATA_AVAILABLE)
					return;

				int size = port.bytesAvailable();
				if (size <= 0)
					return;

				byte[] data = new byte[size];
				int read = port.readBytes(data, size);
				if (read <= 0)
					return;

				// =====================================================================
				// [FIX-3] 잠금 범위 최소화
				//
				// 이전: synchronized 블록 안에서 modbusManager.onModbusPacket() 호출
				//        processHex()[synchronized(ModbusManager.this)] 대기 동안
				//        PacketReceiver.this 잠금 과도 보유 → 다음 이벤트 지연
				//
				// 수정: 패킷 추출 및 rxBuffer 정리만 lock 안에서, processHex()는 lock 해제 후
				// =====================================================================
				List<byte[]> completedPackets = extractPackets(data, read);

				for (byte[] packet : completedPackets) {
					modbusManager.onModbusPacket(packet);
					if (rawHexManager != null) {
						rawHexManager.onReceive(packet);
					}
				}
			}
		});
	}

	// =========================================================================
	// 패킷 추출 — synchronized(PacketReceiver.this) 범위
	//
	// [FIX-★] 단편화 수신 핵심 수정
	//   getExpectedLength() == -1 && buf.length < 3 → break (대기)
	//   getExpectedLength() == -1 && buf.length >= 3 → 선두 스킵 (FC 불명)
	//
	// [FIX-4] SlaveID 탐색 O(n) — findSlaveIdOffset() 한 번의 선형 탐색으로
	//   이전의 1바이트씩 버리며 루프하는 O(n²) 방식을 제거
	// =========================================================================
	private synchronized List<byte[]> extractPackets(byte[] data, int read) {
		rxBuffer.write(data, 0, read);
		List<byte[]> result = new ArrayList<>();

		while (true) {
			byte[] buf = rxBuffer.toByteArray();
			if (buf.length == 0)
				break;

			// 노이즈 과다 또는 장기 FC 불명 누적 → 강제 초기화
			if (buf.length > MAX_FRAME_BYTES) {
				log.warn("rxBuffer 과대 ({} bytes) — 버퍼 강제 초기화 (노이즈 또는 FC 불명 장기 누적)", buf.length);
				rxBuffer.reset();
				break;
			}

			// [BUG-1] 선두 바이트가 이미 기대 SlaveID면 탐색 자체를 생략한다.
			//
			// findSlaveIdOffset()는 버퍼 전체를 뒤져 일치 바이트를 찾는데,
			// 정상 응답의 데이터 영역에 우연히 SlaveID 값(예: 0x01)이 있으면
			// 그 위치를 프레임 시작으로 오인해 정상 헤더를 잘라버릴 수 있다.
			//
			// 요청-응답 1:1 순차 게이팅 환경에서는 buf[0]이 곧 프레임 시작이므로,
			// buf[0]==SlaveID인 정상 케이스에서는 탐색을 건너뛰어 오인식을 원천 차단한다.
			// buf[0]이 불일치할 때만(선두 노이즈) 탐색으로 복구를 시도한다.
			if ((buf[0] & 0xFF) == currentSlaveId) {
				// 정상 경로 — 탐색 없이 바로 길이 판단으로 진행
			} else {
				int validStart = findSlaveIdOffset(buf, currentSlaveId);
				if (validStart < 0) {
					// 버퍼 전체에 유효 SlaveID 없음 → 전부 노이즈 폐기
					log.debug("버퍼 전체 SlaveID 불일치 ({} bytes) — 전체 폐기", buf.length);
					rxBuffer.reset();
					break;
				}
				// validStart > 0 (validStart==0은 위 if에서 이미 처리되어 도달 불가)
				// 선두 노이즈 바이트를 한 번에 제거
				log.debug("선두 노이즈 {} bytes 제거", validStart);
				rxBuffer.reset();
				rxBuffer.write(buf, validStart, buf.length - validStart);
				continue; // 정제된 버퍼로 재검사
			}

			// buf[0] == currentSlaveId — 길이 확인
			int expectedLength = modbusManager.getExpectedLength(buf);

			if (expectedLength == -1) {
				// =====================================================================
				// [FIX-★] 핵심 수정 — 반환 -1의 두 케이스 구분
				//
				// buf.length < 3: SlaveID·FC·ByteCount가 아직 다 안 온 단편화 상황
				//   → break(대기). 스킵하면 SlaveID 바이트가 날아가 다음 단편에서
				//     SlaveID 불일치 → 전체 폐기 → 타임아웃 → 불필요한 재시도 발생
				//
				// buf.length >= 3: FC는 받았는데 getExpectedLength()가 -1
				//   → 진짜 알 수 없는 FC → 선두 1바이트 스킵하고 재탐색
				// =====================================================================
				if (buf.length < 3) {
					log.debug("단편화 수신 ({} bytes) — SlaveID·FC·ByteCount 수신 대기 중", buf.length);
					break; // 더 많은 바이트를 기다림 (스킵 절대 금지)
				}
				// buf.length >= 3 이지만 FC 불명
				log.debug("FC 불명 (buf[1]=0x{}, buf.length={}) — 선두 1바이트 스킵", String.format("%02X", buf[1] & 0xFF),
						buf.length);
				rxBuffer.reset();
				if (buf.length > 1)
					rxBuffer.write(buf, 1, buf.length - 1);
				continue;
			}

			if (buf.length < expectedLength) {
				// 패킷 미완성 — 더 기다림
				break;
			}

			// 완성된 패킷 추출
			byte[] packet = Arrays.copyOf(buf, expectedLength);
			result.add(packet);

			rxBuffer.reset();
			if (buf.length > expectedLength) {
				// 잔여 바이트 보존 후 루프 재진입 (연속 응답 처리)
				rxBuffer.write(buf, expectedLength, buf.length - expectedLength);
			} else {
				break;
			}
		}

		return result;
	}

	/**
	 * buf 안에서 targetSlaveId와 일치하는 첫 번째 바이트 위치를 O(n)으로 반환.
	 * 없으면 -1.
	 */
	private static int findSlaveIdOffset(byte[] buf, int targetSlaveId) {
		for (int i = 0; i < buf.length; i++) {
			if ((buf[i] & 0xFF) == targetSlaveId)
				return i;
		}
		return -1;
	}
}