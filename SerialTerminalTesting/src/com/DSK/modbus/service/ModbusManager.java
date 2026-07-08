package com.DSK.modbus.service;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.modbus.model.ErrorStatusDto;
import com.DSK.modbus.model.LogDto;
import com.DSK.modbus.model.MeterRowDto;
import com.DSK.modbus.model.PendingRequest;
import com.DSK.modbus.model.constant.DataType;
import com.DSK.modbus.model.constant.EndianType;
import com.DSK.modbus.model.constant.FunctionCode;
import com.DSK.modbus.service.codec.ByteConverter;
import com.DSK.modbus.service.codec.ProtocolConverter;
import com.fazecast.jSerialComm.SerialPort;

/**
 * Modbus RTU 통신 코어 — 패킷 빌드, 큐 관리, 응답 처리, 오류 처리.
 *
 * [스레드 모델]
 * ┌─ EDT ─────────────────────────────────────────────────────────────┐
 * │  버튼 클릭 → startSend() / enqueue() / cancelPendingRequests()    │
 * │  updateMeterValue() ← SafeEventDispatcher.invokeLater()로 도착    │
 * └───────────────────────────────────────────────────────────────────┘
 * ┌─ Modbus-Timeout (단일 스레드) ─────────────────────────────────────┐
 * │  timeoutTask 람다 → synchronized(ModbusManager.this)              │
 * │  sendNextPacket() 스케줄 → 이 스레드에서 sendBytes() 실행         │
 * └───────────────────────────────────────────────────────────────────┘
 * ┌─ jSerialComm 내부 스레드 ──────────────────────────────────────────┐
 * │  PacketReceiver.serialEvent() → onModbusPacket() → processHex()  │
 * └───────────────────────────────────────────────────────────────────┘
 *
 * [주요 수정 내역]
 * FIX-1: activePort, outputStream, currentPendingRequest → volatile
 * FIX-2: TIMEOUT_MS, txDelayMs, retryCount → volatile
 * FIX-3: initializeCommunicationTunnel() 전체 synchronized + timeoutTask 선취소
 * FIX-4: sendNextPacket() catch → timeoutTask 취소 + 재귀 대신 schedule
 * FIX-5: handleReadResponse() → NaN/Infinity 감지 + UINT32 정밀도 주의
 * FIX-6: handleWriteResponse() → FC 0x0F/0x10 응답 bytes[4..5]는 값이 아닌 수량
 * FIX-7: processHex() → 지연 응답 폐기 시 진단 로그 추가
 * FIX-8: handleCrcError() 내 중첩 synchronized 제거
 * FIX-9: DateTimeFormatter → static final (매 호출마다 생성 방지)
 * FIX-10: startSend() → synchronized 메서드로 통일
 * FIX-11: shutdown() 추가 — timeoutScheduler 명시적 종료
 * DB 제거: DAO 의존성 전체 제거 (추후 별도 논의)
 */
public class ModbusManager {
	// ===== [DEMO ONLY] — 배포 전 이 블록 전체 제거 =====
	// TODO: 제거 대상: 아래 필드 선언 1개 + setter 1개 + onModbusPacket 내 주입 블록
	private volatile boolean simulateCrcError = false;
	private volatile boolean simulateException = false;

	private static final Logger log = LoggerFactory.getLogger(ModbusManager.class);

	// [FIX-9] DateTimeFormatter → static final
	// DateTimeFormatter는 immutable + thread-safe → 공유 안전
	private static final DateTimeFormatter LOG_TIME_FMT = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss.SSS");

	// ── 타임아웃 스케줄러 (단일 스레드 — Modbus RTU 순차 통신 보장) ──────────
	private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "Modbus-Timeout");
		t.setDaemon(true);
		return t;
	});
	private ScheduledFuture<?> timeoutTask = null;

	// ── 통신 상태 ──────────────────────────────────────────────────────────────
	private volatile boolean waitingResponse = false;
	private volatile long requestTime = 0;

	// ── 의존 객체 ──────────────────────────────────────────────────────────────
	private final ModbusEventListener listener;

	// [FIX-1] volatile — initializeCommunicationTunnel(EDT 또는 외부 스레드)과
	//         sendBytes/flushPort(timeoutScheduler 스레드) 사이 가시성 보장
	private volatile SerialPort activePort;
	private volatile OutputStream outputStream;
	private volatile PendingRequest currentPendingRequest = null;

	// ── RX 버퍼 ───────────────────────────────────────────────────────────────
	private final ByteArrayOutputStream rxStreamBuffer = new ByteArrayOutputStream();

	// ── 현재 처리 중인 요청 메타 ────────────────────────────────────────────────
	// [CONC-1] volatile — sendNextPacket/sendBytes/processHex는 모두 synchronized라
	// happens-before가 성립하지만, 지연 응답(late response) 진단 로그 등 일부 경로에서
	// lock 밖 읽기 가능성을 원천 차단하기 위해 volatile로 명시.
	private volatile int currentRequestAddress = 0x0000;
	private volatile int currentSlaveId = 1;

	// [FIX-2] volatile — setter(EDT)와 timeoutScheduler 스레드 사이 가시성 보장
	private volatile int TIMEOUT_MS = 500;
	private volatile int txDelayMs = 100;
	private volatile int retryCount = 3;

	private EndianType defaultEndianType = EndianType.LITTLE_ENDIAN;
	private DataType defaultDataType = DataType.UINT32;

	// ── 송신 큐 (ArrayDeque — 모든 접근은 synchronized 보호) ─────────────────
	private final Deque<PendingRequest> requestQueue = new ArrayDeque<>();

	// ── 에러 맵: Long 복합키 = (slaveId<<32)|(fc<<16)|address ────────────────
	private final Map<Long, ErrorStatusDto> errorMap = new ConcurrentHashMap<>();

	// ── 패킷 평균 응답 시간 ────────────────
	private volatile int avgPacketResponseTime = 0;
	private volatile int responseCount = 0;
	private volatile long totalResponseTime = 0;

	// ── PacketReceiver ────────────────────────────────────────────────────────
	private PacketReceiver packetReceiver;

	// =========================================================================
	// 생성자
	// =========================================================================
	public ModbusManager(ModbusEventListener listener) {
		this.listener = new SafeEventDispatcher(listener);
	}

	public void setPacketReceiver(PacketReceiver packetReceiver) {
		this.packetReceiver = packetReceiver;
	}

	// =========================================================================
	// [FIX-11] shutdown — 자원 해제
	//
	// timeoutScheduler는 daemon 스레드라 JVM 종료 시 강제 종료되나,
	// 재연결마다 new ModbusManager()를 생성하는 패턴이라면 명시 호출 필요.
	// 포트 닫기 직전 또는 WindowClosing 이벤트에서 호출하라.
	// =========================================================================
	public void shutdown() {
		timeoutScheduler.shutdownNow();
		log.info("ModbusManager shutdown 완료");
	}

	// =========================================================================
	// 포트 연결 초기화
	// =========================================================================

	/**
	 * [FIX-3] 전체 메서드 synchronized 처리.
	 *
	 * 이전: activePort, outputStream, currentPendingRequest를 lock 없이 수정
	 *        timeoutScheduler가 flushPort/sendBytes 실행 중이면 Race Condition
	 *
	 * 수정: 진행 중인 timeoutTask를 먼저 취소 → 모든 필드를 lock 안에서 일괄 수정
	 */
	public synchronized void initializeCommunicationTunnel(SerialPort port, OutputStream os, String portName) {
		if (timeoutTask != null && !timeoutTask.isDone()) {
			timeoutTask.cancel(true);
			timeoutTask = null;
		}
		this.activePort = port;
		this.outputStream = os;
		this.waitingResponse = false;
		this.currentPendingRequest = null;
		requestQueue.clear();
		rxStreamBuffer.reset();

		log.info("ModbusManager 초기화 완료: {}", portName);
		listener.onTxRx(String.format("[%s][SYSTEM]\n%s connected\n\n", nowLogTime(), portName));
	}

	// =========================================================================
	// 패킷 빌드
	// =========================================================================
	public PendingRequest buildRequest(MeterRowDto info) {
		try {
			int startAddr = info.getAddress();
			int slaveId = info.getSlaveId();
			int fCode = info.getFunctionCode() != null ? info.getFunctionCode().getCode() : 0x04;
			DataType dtype = info.getDataType() != null ? info.getDataType() : DataType.UINT16;
			int byteSize = ByteConverter.getDataSize(dtype);
			int regCount = Math.max(byteSize / 2, 2);

			// UINT16/INT16: Math.max(1,2)=2 → 2레지스터 강제 요청
			// 일부 슬레이브는 4바이트 응답 대신 Exception Code 03 반환 가능
			// → handleModbusException()이 처리하나 1레지스터 fallback 없음
			if (dtype == DataType.UINT16 || dtype == DataType.INT16) {
				log.debug("[buildRequest] {}타입은 2레지스터 요청 — 슬레이브가 1레지스터만 지원 시 Exception 가능 (addr=0x{})", dtype,
						String.format("%04X", startAddr));
			}
			if (dtype == DataType.DOUBLE64) {
				log.debug("[buildRequest] DOUBLE64 4레지스터 요청 — 슬레이브 최대 레지스터 수 확인 필요 (addr=0x{})",
						String.format("%04X", startAddr));
			}

			byte[] rawBytes = { (byte) (slaveId & 0xFF), (byte) (fCode & 0xFF), (byte) ((startAddr >> 8) & 0xFF),
					(byte) (startAddr & 0xFF), (byte) ((regCount >> 8) & 0xFF), (byte) (regCount & 0xFF) };
			int crc = ProtocolConverter.calculateCRC16(rawBytes, rawBytes.length);

			byte[] packet = new byte[8];
			System.arraycopy(rawBytes, 0, packet, 0, rawBytes.length);
			packet[6] = (byte) (crc & 0xFF);
			packet[7] = (byte) ((crc >> 8) & 0xFF);

			log.debug("[buildRequest] item={} slave={} addr=0x{} fc=0x{} regCount={} dtype={}", info.getItemName(),
					slaveId, String.format("%04X", startAddr), String.format("%02X", fCode), regCount, dtype);

			return new PendingRequest(packet, info, retryCount);

		} catch (Exception ex) {
			log.error("패킷 빌드 실패 – item={}", info.getItemName(), ex);
			return null;
		}
	}

	// =========================================================================
	// 큐 관리
	// =========================================================================
	public synchronized void enqueue(PendingRequest request) {
		requestQueue.offer(request);
		log.debug("큐 삽입: {}", request);
	}

	// [FIX-10] synchronized 메서드로 통일 (이전: synchronized 블록 래퍼 혼용)
	public synchronized void startSend() {
		if (!waitingResponse) {
			sendNextPacket();
		} else {
			log.warn("이전 패킷 처리 중 – 중복 발송 차단");
			listener.onTxRx(
					String.format("[%s][WARN]\nRequest skipped — previous request still pending\n", nowLogTime()));
		}
	}

	private synchronized void sendNextPacket() {
		PendingRequest request = requestQueue.poll();
		if (request == null) {
			waitingResponse = false;
			currentPendingRequest = null;
			log.info("큐 소진 – 전송 완료");
			return;
		}
		currentPendingRequest = request;
		currentRequestAddress = request.getAddress();
		currentSlaveId = request.getSlaveId();
		waitingResponse = true;

		try {
			sendBytes(request.getPacket());
		} catch (Exception e) {
			// [FIX-4] 예외 시 timeoutTask 명시 취소 + 재귀 대신 스케줄
			//
			// 이전: sendNextPacket() 재귀 호출
			//   큐에 N개 항목 + 포트 오류 반복 시 N레벨 재귀 → 스택 오버플로우 위험
			//
			// 수정: 스케줄러에 위임 → 현재 lock 해제 후 실행 (재귀 없음)
			if (timeoutTask != null && !timeoutTask.isDone()) {
				timeoutTask.cancel(false);
			}
			log.error("패킷 전송 실패: {}", request, e);
			listener.onSystemLog(new LogDto("ERROR", "패킷 전송 실패: " + e.getMessage()));
			listener.onTxRx(String.format("\n[%s][ERROR][PORT DISCONNECTION]\nFailed to write bytes: %s\n",
					nowLogTime(), e.getMessage()));
			waitingResponse = false;
			timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
		}
	}

	// =========================================================================
	// 물리 바이트 송신 + 타임아웃 핸들러
	// =========================================================================
	public synchronized void sendBytes(byte[] bytes) throws Exception {
		if (outputStream == null)
			throw new IllegalStateException("포트 미연결");

		if (packetReceiver != null) {
			packetReceiver.setExpectedSlaveId(currentSlaveId);
		} else {
			log.warn("packetReceiver 가 연결되지 않았습니다.");
		}

		int RetryNum = RetryNumber(currentPendingRequest);
		int maxRetrys = maxRetrys();

		if (timeoutTask != null && !timeoutTask.isDone())
			timeoutTask.cancel(false);
		rxStreamBuffer.reset();
		requestTime = System.currentTimeMillis();

		// TX 로그
		String txHex = ProtocolConverter.convertBytesToHex(bytes, bytes.length);
		String itemNameForLog = (currentPendingRequest != null) ? currentPendingRequest.getItemName() : "Unknown";

		listener.onTxRx(String.format("[%s][TX][Retry %d/%d][%s]\n%s\n", nowLogTime(), RetryNum, maxRetrys,
				itemNameForLog, txHex));

		log.info("[TX] addr=0x{} slaveId={} Retry={}/{} TIMEOUT_MS={}ms", String.format("%04X", currentRequestAddress),
				currentSlaveId, RetryNum, maxRetrys, TIMEOUT_MS);

		final PendingRequest sentRequest = currentPendingRequest;
		final int sentRetry = RetryNum;
		final int sentMax = maxRetrys;

		timeoutTask = timeoutScheduler.schedule(() -> {
			synchronized (ModbusManager.this) {
				if (!waitingResponse)
					return;

				long elapsed = System.currentTimeMillis() - requestTime;
				waitingResponse = false;

				// ── TIMEOUT 로그 ──────────────────────────────────────────
				ErrorStatusDto errDto = getOrCreateErrorStatus(sentRequest);

				listener.onTxRx(String.format("[%s][TIMEOUT][Retry %d/%d]\nNo response within %dms\n", nowLogTime(),
						sentRetry, sentMax, TIMEOUT_MS));

				// ── 재시도 가능 ───────────────────────────────────────────
				if (sentRequest != null && sentRequest.canRetry()) {
					errDto.triggerTimeout();

					PendingRequest retryReq = sentRequest.retryInstance();
					requestQueue.addFirst(retryReq);

					errDto.incrementRetry();

					int nextRetry = sentRetry + 1;

					log.warn("[TIMEOUT] 재시도 – {} Retry={}/{} elapsed={}ms", sentRequest.getItemName(), nextRetry,
							sentMax, elapsed);

					rxStreamBuffer.reset();
					flushPort();
					timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
					return;
				}

				// ── 재시도 소진 → 최종 실패 ──────────────────────────────
				int addr = (sentRequest != null) ? sentRequest.getAddress() : currentRequestAddress;
				int slaveIdForUi = (sentRequest != null) ? sentRequest.getSlaveId() : currentSlaveId;
				int timeoutFc = (sentRequest != null && sentRequest.getFunctionCode() != null)
						? sentRequest.getFunctionCode().getCode()
						: 0x04;
				String addrHex = String.format("0x%04X", addr);
				String itemName = (sentRequest != null) ? sentRequest.getItemName() : "Unknown";

				listener.onTxRx(String.format("[%s][ERROR][%s][RETRY_EXHAUSTED]\n", nowLogTime(), itemName));

				//				ErrorStatusDto errDto = getOrCreateErrorStatus(sentRequest, addr);
				errDto.triggerTimeout();

				log.error("[TIMEOUT 최종] {} {} – {}ms | 누적:{} 연속:{}", addrHex, itemName, elapsed,
						errDto.getTotalErrCount(), errDto.getConsecutiveFails());

				listener.onSystemLog(new LogDto("ERROR", String.format("[TIMEOUT] %s %s 무응답 (%dms) | 누적오류:%d 연속실패:%d",
						itemName, addrHex, elapsed, errDto.getTotalErrCount(), errDto.getConsecutiveFails())));

				listener.onMeterValueUpdated(slaveIdForUi, addr, timeoutFc, "-", "타임아웃", this.avgPacketResponseTime);

				rxStreamBuffer.reset();
				flushPort();
				sendNextPacket();
			}
		}, TIMEOUT_MS, TimeUnit.MILLISECONDS);

		outputStream.write(bytes);
		outputStream.flush();
	}

	// =========================================================================
	// PacketReceiver → 완성 패킷 수신
	// TODO: 시연 후 삭제
	// =================================================================ㄴ========
	public void setSimulateCrcError(boolean on) {
		this.simulateCrcError = on;
		log.warn("[DEMO] CRC 오류 주입 {}", on ? "ON ← 시연 모드" : "OFF");
	}

	public void setSimulateException(boolean on) {
		this.simulateException = on;
		log.warn("[DEMO] 장비거부 주입 {}", on ? "ON ← 시연 모드" : "OFF");
	}
	// ===== [DEMO ONLY] END =====

	public void onModbusPacket(byte[] packet) {
		// [BUG-2] 최소 길이 방어 — PacketReceiver는 완성 패킷만 넘기지만,
		// 이 메서드가 public이라 외부 경로로 짧은 배열이 들어오면
		// processHex()의 (len-2) 인덱스 계산이 음수가 되어 예외 발생.
		// 최소 프레임 = SlaveID(1) + FC(1) + CRC(2) = 4바이트.
		if (packet == null || packet.length < 4) {
			log.warn("[onModbusPacket] 비정상 짧은 패킷 폐기 (len={})", packet == null ? 0 : packet.length);
			return;
		}

		// ===== [DEMO ONLY] CRC 오염 주입 =====
		// 원본 배열을 건드리지 않고 clone() 후 오염 — rawHexManager 등 공유 참조 보호
		// 마지막 바이트(CRC_L) XOR 0xFF → receivedCRC != calculatedCRC 보장
		if (simulateCrcError) {
			byte[] corrupted = packet.clone();
			corrupted[corrupted.length - 2] ^= (byte) 0xFF;
			log.warn("[DEMO] CRC 오염 주입 적용 (len={}, 원본CRC_L=0x{} → 오염=0x{})", packet.length,
					String.format("%02X", packet[packet.length - 2] & 0xFF),
					String.format("%02X", corrupted[corrupted.length - 2] & 0xFF));
			processHex(corrupted);
			return;
		}
		// ===== [DEMO ONLY] END =====

		// ===== [DEMO ONLY] 장비거부 주입 =====
		// 정상 응답을 Exception 응답 프레임으로 교체한다.
		// 구조: [SlaveID][FC|0x80][ExceptionCode=0x02][CRC_L][CRC_H]
		//   - SlaveID : 원본 그대로 유지 (SlaveID 검증 통과 보장)
		//   - FC|0x80 : 최상위 비트 ON → processHex()가 Exception으로 분기
		//   - ExCode  : 0x02 (Illegal Data Address) — 가장 흔한 거부 코드
		//   - CRC     : 새 5바이트 기준 재계산 (CRC 검증 통과해야 Exception 처리 가능)
		// 주의: CRC가 틀리면 Exception이 아닌 CRC오류로 잡힌다 — 반드시 재계산
		if (simulateException) {
			byte[] ex = new byte[5];
			ex[0] = packet[0]; // SlaveID 유지
			ex[1] = (byte) ((packet[1] & 0xFF) | 0x80); // FC 최상위 비트 ON
			ex[2] = 0x02; // ExceptionCode: Illegal Data Address
			int crc = ProtocolConverter.calculateCRC16(ex, 3);
			ex[3] = (byte) (crc & 0xFF);
			ex[4] = (byte) ((crc >> 8) & 0xFF);
			log.warn("[DEMO] 장비거부 주입 적용 — SlaveID={} 원본FC=0x{} → 0x{} ExCode=0x02", packet[0] & 0xFF,
					String.format("%02X", packet[1] & 0xFF), String.format("%02X", ex[1] & 0xFF));
			processHex(ex);
			return;
		}
		// ===== [DEMO ONLY] END =====

		processHex(packet);
	}
	// TODO: 시연 후 삭제 -> 장비 거부, CRC 에러 주입

	// =========================================================================
	// 예상 패킷 길이 — PacketReceiver에서 호출
	// =========================================================================
	public int getExpectedLength(byte[] buffer) {
		if (buffer == null || buffer.length < 3)
			return -1; // 단편화 상황 — PacketReceiver가 대기로 처리해야 함
		int function = buffer[1] & 0xFF;
		if ((function & 0x80) != 0)
			return 5; // Exception 응답: ID(1) + FC(1) + ExCode(1) + CRC(2)
		return switch (function) {
		case 0x01, 0x02, 0x03, 0x04 -> {
			int byteCount = buffer[2] & 0xFF;
			yield 3 + byteCount + 2; // ID + FC + BC + Data + CRC
		}
		case 0x05, 0x06, 0x0F, 0x10 -> 8; // Write 응답 고정 8바이트
		default -> -1; // 알 수 없는 FC
		};
	}

	// =========================================================================
	// 응답 패킷 처리
	// =========================================================================
	private synchronized void processHex(byte[] incomingData) {
		if (!waitingResponse) {
			// [FIX-7] 지연 응답 감지 — 이전: silent drop, 수정: 진단 로그 추가
			// 타임아웃 처리 후 도착한 지연 응답이거나, waitingResponse=false 상태의 불필요한 패킷
			log.debug("[지연응답 폐기] waitingResponse=false 상태에서 패킷 수신 (len={}, slaveId={})", incomingData.length,
					incomingData.length > 0 ? (incomingData[0] & 0xFF) : -1);
			return;
		}

		int len = incomingData.length;
		String hex = ProtocolConverter.convertBytesToHex(incomingData, len);
		int startAddress = currentRequestAddress;
		long elapsed = System.currentTimeMillis() - requestTime;

		// ── CRC 검증 ──────────────────────────────────────────────────────────
		int dataLen = len - 2;
		int receivedCRC = (Integer) ByteConverter.convert(incomingData, len - 2, DataType.UINT16,
				EndianType.LITTLE_ENDIAN);
		int calculatedCRC = ProtocolConverter.calculateCRC16(incomingData, dataLen);

		if (receivedCRC != calculatedCRC) {
			handleCrcError(incomingData, startAddress, calculatedCRC, receivedCRC, elapsed);
			return;
		}

		// ── [R-6] 응답 SlaveID + FC 검증 (waitingResponse 변경 전에 수행) ──────
		// CRC까지 통과한 유효 프레임이라도, 응답의 SlaveID/FC가 현재 요청과 다르면
		// "내가 기다리던 응답이 아니다".
		//
		// 발생 시나리오: 슬레이브 A 요청 → 타임아웃 직전 → B로 전환되는 찰나에
		//   A의 지연 응답이 도착. 요청 컨텍스트는 이미 B인데 A 데이터를 B로 파싱하면
		//   검침값이 오염된다.
		//
		// 처리: waitingResponse를 내리지도, timeoutTask를 취소하지도 않고 그대로 폐기.
		//   → 진짜 응답을 계속 기다리거나, 안 오면 타임아웃 → 재시도로 자연 복구.
		//
		// 주의: 이 검증은 반드시 waitingResponse=false / timeoutTask.cancel() 전에 해야 한다.
		//   먼저 응답 완료 처리를 해버리면, 폐기 후 요청이 영원히 대기 상태로 남는다.
		int respSlaveId = incomingData[0] & 0xFF;
		if (respSlaveId != currentSlaveId) {
			log.warn("[SlaveID 불일치 폐기] 기대={} 수신={} — 다른 슬레이브 응답 또는 지연 응답 (len={})", currentSlaveId, respSlaveId, len);
			listener.onTxRx(
					String.format("[%s][WARN][SLAVE_MISMATCH]\nExpected SlaveID=%d but received=%d — discarded\n",
							nowLogTime(), currentSlaveId, respSlaveId));
			return; // waitingResponse=true 유지 → 계속 대기
		}

		int respFc = incomingData[1] & 0xFF;
		// Exception 응답(최상위 비트 set)은 요청 FC에 0x80을 씌운 정상 오류 응답이므로 제외.
		// 일반 응답인데 FC가 요청과 다르면 프레임 오정렬/오매칭 → 폐기.
		if ((respFc & 0x80) == 0 && currentPendingRequest != null && currentPendingRequest.getFunctionCode() != null) {
			int expectedFc = currentPendingRequest.getFunctionCode().getCode();
			if (respFc != expectedFc) {
				log.warn("[FC 불일치 폐기] 기대=0x{} 수신=0x{} slave={} — 프레임 오정렬 의심", String.format("%02X", expectedFc),
						String.format("%02X", respFc), currentSlaveId);
				listener.onTxRx(
						String.format("[%s][WARN][FC_MISMATCH]\nExpected FC=0x%02X but received=0x%02X — discarded\n",
								nowLogTime(), expectedFc, respFc));
				return; // waitingResponse=true 유지 → 계속 대기
			}
		}

		// ── CRC 정상 + SlaveID/FC 일치 → 유효 응답 확정 ────────────────────────
		waitingResponse = false;
		if (timeoutTask != null)
			timeoutTask.cancel(false);

		updateAverageResponseTime(elapsed);

		int functionCode = incomingData[1] & 0xFF;
		String fcType = ((functionCode & 0x80) != 0) ? "EXCEPTION" : (functionCode >= 0x05) ? "WRITE" : "READ";

		int RetryNum = RetryNumber(currentPendingRequest);
		int maxRetrys = maxRetrys();

		listener.onTxRx(String.format("[%s][RX][Retry %d/%d][%s]\n%s  (%dms)\n", nowLogTime(), RetryNum, maxRetrys,
				fcType, hex, elapsed));

		// ── 연속 실패 카운터 리셋 ─────────────────────────────────────────────
		{
			int fcForLookup = (currentPendingRequest != null && currentPendingRequest.getFunctionCode() != null)
					? currentPendingRequest.getFunctionCode().getCode()
					: 0x04;
			ErrorStatusDto dtoToReset = errorMap.get(errKey(currentSlaveId, fcForLookup, startAddress));
			if (dtoToReset != null)
				dtoToReset.resetConsecutiveFails();
		}

		String addrHex = String.format("0x%04X", startAddress);

		if ((functionCode & 0x80) != 0) {
			handleModbusException(incomingData, startAddress, addrHex, elapsed);
			return;
		}
		if (functionCode == 0x05 || functionCode == 0x06 || functionCode == 0x0F || functionCode == 0x10) {
			handleWriteResponse(incomingData, elapsed);
			return;
		}
		handleReadResponse(incomingData, startAddress, elapsed);
	}

	// ── Read 응답 파싱 ─────────────────────────────────────────────────────────
	private void handleReadResponse(byte[] incomingData, int startAddress, long elapsed) {
		DataType dataType = (currentPendingRequest != null) ? currentPendingRequest.getDataType() : DataType.UINT16;
		EndianType endianType = (currentPendingRequest != null) ? currentPendingRequest.getEndianType()
				: EndianType.LITTLE_ENDIAN;
		double scale = (currentPendingRequest != null) ? currentPendingRequest.getScale() : 1.0;
		// PendingRequest.getScale()이 이미 0.0 → 1.0 보정을 수행하므로 여기선 추가 방어 불필요
		// 단, 디버깅을 위해 scale=1.0 보정 발생 시 경고 로그를 남길 수 있음
		String itemName = (currentPendingRequest != null) ? currentPendingRequest.getItemName() : "?";
		int fc = (currentPendingRequest != null && currentPendingRequest.getFunctionCode() != null)
				? currentPendingRequest.getFunctionCode().getCode()
				: 0x04;

		int byteCount = incomingData[2] & 0xFF;
		int dataOffset = 3;
		int expectedSize = ByteConverter.getDataSize(dataType);

		String displayValue;
		String status;

		if (byteCount < expectedSize || (dataOffset + expectedSize) > (incomingData.length - 2)) {
			log.warn("[{}] 바이트 부족 — 기대:{}B 수신:{}B (addr=0x{} slave={})", itemName, expectedSize, byteCount,
					String.format("%04X", startAddress), currentSlaveId);
			listener.onSystemLog(new LogDto("ERROR", String.format("[파싱오류] %s (0x%04X) 기대=%dB 수신=%dB (%dms)", itemName,
					startAddress, expectedSize, byteCount, elapsed)));
			displayValue = "파싱오류";
			status = "데이터오류";
		} else {
			// =====================================================================
			// [FIX-5] FLOAT32/DOUBLE64 NaN·Infinity 감지
			//
			// 슬레이브가 센서 이상(단선, 측정 불가) 시 0x7FC00000(Float NaN) 등
			// 특수값을 반환하는 경우가 있다.
			// NaN을 그대로 흘리면: String.format("%.4f", NaN) → "NaN" 문자열
			// DB·UI에서 수치 처리 오류 및 잘못된 집계를 유발한다.
			//
			// UINT32 정밀도 주의:
			// UINT32는 Long으로 반환 → double 변환 시 2^53 이상에서 정밀도 손실
			// 4,294,967,295 * 0.001 = 4,294,967.295가 정확히 표현되지 않을 수 있음
			// 고정밀도 누적 계측이 필요하면 BigDecimal 전환 고려
			// =====================================================================
			Object raw = ByteConverter.convert(incomingData, dataOffset, dataType, endianType);

			if (ByteConverter.isSpecialFloatValue(raw)) {
				// NaN 또는 Infinity — 슬레이브 센서 이상 의심
				String specialDesc = ByteConverter.isNaN(raw) ? "NaN" : "Infinity";
				log.warn("[{}] 센서 특수값({}) 수신 — 슬레이브 센서 이상 의심 (addr=0x{} slave={})", itemName, specialDesc,
						String.format("%04X", startAddress), currentSlaveId);
				listener.onSystemLog(
						new LogDto("ERROR", String.format("[센서이상] %s (0x%04X) %s 값 수신 — 슬레이브 센서 확인 필요 (%dms)", itemName,
								startAddress, specialDesc, elapsed)));
				displayValue = "센서이상(" + specialDesc + ")";
				status = "데이터오류";
			} else {
				double numeric = ((Number) raw).doubleValue() * scale;
				displayValue = formatValue(dataType, numeric);
				status = "정상";
				log.info("[{}] 0x{} byteCount={}B parseSize={}B {} {} scale={} → {}", itemName,
						String.format("%04X", startAddress), byteCount, expectedSize, dataType, endianType, scale,
						displayValue);
			}
		}

		listener.onMeterValueUpdated(currentSlaveId, startAddress, fc, displayValue, status,
				this.avgPacketResponseTime);
		flushPort();
		timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
	}

	// ── Write 응답 ─────────────────────────────────────────────────────────────
	private void handleWriteResponse(byte[] incomingData, long elapsed) {
		int confirmedAddr = ((incomingData[2] & 0xFF) << 8) | (incomingData[3] & 0xFF);
		String addrHex = String.format("0x%04X", confirmedAddr);
		int writeFc = incomingData[1] & 0xFF;

		// =====================================================================
		// [FIX-6] FC 0x0F/0x10 응답의 bytes[4..5] 오해석 수정
		//
		// FC 0x06 (Write Single Register):
		//   bytes[4..5] = 기록한 레지스터 값 ← 맞음
		// FC 0x05 (Write Single Coil):
		//   bytes[4..5] = 코일 값 (0xFF00 or 0x0000) ← 맞음
		// FC 0x10 (Write Multiple Registers):
		//   bytes[4..5] = 기록한 레지스터 수량 ← 값이 아님
		// FC 0x0F (Write Multiple Coils):
		//   bytes[4..5] = 기록한 코일 수량 ← 값이 아님
		//
		// 이전: 모든 Write FC에서 bytes[4..5]를 "값"으로 해석 → 0x10 응답 시 로그 오류
		// =====================================================================
		if (writeFc == 0x06 || writeFc == 0x05) {
			// 단일 쓰기: bytes[4..5] = 기록된 값
			int confirmedValue = ((incomingData[4] & 0xFF) << 8) | (incomingData[5] & 0xFF);
			log.info("[쓰기 완료] 장치:{} 주소:{} 값:{} ({}ms)", currentSlaveId, addrHex, confirmedValue, elapsed);
			listener.onMeterValueUpdated(currentSlaveId, confirmedAddr, writeFc, String.valueOf(confirmedValue), "정상",
					this.avgPacketResponseTime);
			listener.onSystemLog(new LogDto("SYSTEM", String.format("[변경성공] 장치 #%d 주소 %s → 값:%d (%dms)", currentSlaveId,
					addrHex, confirmedValue, elapsed)));
		} else {
			// 멀티 쓰기 (FC 0x0F, 0x10): bytes[4..5] = 기록된 수량 (값 아님)
			int confirmedQty = ((incomingData[4] & 0xFF) << 8) | (incomingData[5] & 0xFF);
			log.info("[쓰기 완료] 장치:{} 주소:{} 기록수량:{} (FC=0x{} {}ms)", currentSlaveId, addrHex, confirmedQty,
					String.format("%02X", writeFc), elapsed);
			listener.onMeterValueUpdated(currentSlaveId, confirmedAddr, writeFc, "write_ok", "정상",
					this.avgPacketResponseTime);
			listener.onSystemLog(new LogDto("SYSTEM", String.format("[변경성공] 장치 #%d 주소 %s FC=0x%02X 기록수량:%d (%dms)",
					currentSlaveId, addrHex, writeFc, confirmedQty, elapsed)));
		}

		flushPort();
		timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
	}

	// ── Modbus Exception 응답 처리 ───────────────────────────────────────────────
	// Modbus Exception은 "통신 실패"가 아니라,
	// 슬레이브가 요청을 정상적으로 수신한 뒤 처리할 수 없음을 알려주는 응답이다.
	// (응답 Function Code = 원래 FC + 0x80)
	//
	// 예)
	// 요청 : 01 03 00 00 00 02
	// 응답 : 01 83 02 CRC CRC
	//
	// 83 = Exception Response (03 + 0x80)
	// 02 = Illegal Data Address
	//
	// 즉, 장비는 살아있지만 요청을 거부한 상황이다.
	private void handleModbusException(byte[] incomingData, int startAddress, String addrHex, long elapsed) {
		//		updateAverageResponseTime(elapsed);
		// Exception Code 추출
		// 예) 01 83 02 → 02
		int exCode = incomingData[2] & 0xFF;

		// Exception 비트(0x80)를 제거하여 원래 Function Code 복원
		// 예) 0x83 & 0x7F = 0x03
		int originalFc = incomingData[1] & 0x7F;

		// Exception Code를 사람이 읽을 수 있는 문자열로 변환
		String reason = switch (exCode) {
		case 1 -> "Illegal Function"; // 지원하지 않는 기능(Function Code) 요청 시 발생
		case 2 -> "Illegal Data Address"; // 잘못되거나 존재하지 않는 데이터 주소 접근 시 발생
		case 3 -> "Illegal Data Value"; // 요청한 데이터의 값이나 범위가 올바르지 않을 때 발생
		case 4 -> "Slave Device Failure"; // 슬레이브(종단) 장치가 명령 수행 중 내부 오류 발생 시
		default -> "Device-Specific Error " + exCode; // 규격 외 장비 제조사 고유의 에러 코드 처리
		};

		// TX/RX 터미널에 Exception 정보 출력
		listener.onTxRx(String.format("[%s][ERROR][MODBUS EXCEPTION]\nFC=%02X  ExceptionCode=%02X (%s)\n", nowLogTime(),
				originalFc, exCode, reason));

		// Exception 발생 횟수 증가
		//
		// ※ Timeout이 아니다.
		// 장비가 응답하지 않은 것이 아니라,
		// "요청을 처리할 수 없다"고 정상적으로 응답한 것이므로
		// Timeout Count가 아닌 ModbusException Count를 증가시킨다.
		ErrorStatusDto errDto = getOrCreateErrorStatus(currentPendingRequest, startAddress);
		errDto.triggerModbusException();

		// UI 갱신에 사용할 Function Code
		int exFc = (currentPendingRequest != null && currentPendingRequest.getFunctionCode() != null)
				? currentPendingRequest.getFunctionCode().getCode()
				: 0x04;

		// 검침 항목명(전압, 전류 등)
		String itemName = (currentPendingRequest != null) ? currentPendingRequest.getItemName() : "Unknown";

		// 개발자 로그(SLF4J)
		log.error("[Exception] Slave:{} Addr:{} FC:0x{} Code:0x{} ({}) | 누적:{}", currentSlaveId, addrHex,
				String.format("%02X", originalFc), String.format("%02X", exCode), reason, errDto.getTotalErrCount());

		// Meter 상태를 "장비거부"로 갱신
		listener.onMeterValueUpdated(currentSlaveId, startAddress, exFc, "-", "장비거부", this.avgPacketResponseTime);

		// 시스템 로그 출력
		listener.onSystemLog(new LogDto("ERROR", String.format("[장비거부] 장치#%d %s %s — %s (코드:0x%02X) | 누적:%d",
				currentSlaveId, itemName, addrHex, reason, exCode, errDto.getTotalErrCount())));

		// 이번 요청은 Exception 응답으로 종료되었으므로
		// 버퍼를 비우고 다음 검침 요청으로 진행한다.
		flushPort();
		timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
	}

	// ── CRC 에러 + 재시도 ──────────────────────────────────────────────────────
	private void handleCrcError(byte[] rawData, int startAddress, int calcCRC, int recvCRC, long elapsed) {
		int RetryNum = RetryNumber(currentPendingRequest);
		int maxRetrys = maxRetrys();
		String addrHex = String.format("0x%04X", startAddress);

		waitingResponse = false;
		if (timeoutTask != null)
			timeoutTask.cancel(false);

		// RX_RAW + CRC ERROR 로그
		String rawHex = ProtocolConverter.convertBytesToHex(rawData, rawData.length);
		listener.onTxRx(String.format(
				"[%s][RX][CRC ERROR][Retry %d/%d]\n%s  (%dms)  " + "Expected=%02X %02X  Received=%02X %02X\n",
				nowLogTime(), RetryNum, maxRetrys, rawHex, elapsed, (calcCRC >> 8) & 0xFF, calcCRC & 0xFF,
				(recvCRC >> 8) & 0xFF, recvCRC & 0xFF));

		ErrorStatusDto errDto = getOrCreateErrorStatus(currentPendingRequest, startAddress);

		// ── 재시도 가능 ───────────────────────────────────────────────────────
		if (currentPendingRequest != null && currentPendingRequest.canRetry()) {
			PendingRequest retryReq = currentPendingRequest.retryInstance();
			requestQueue.addFirst(retryReq);

			errDto.incrementRetry();
			errDto.triggerCrcError();

			int nextRetry = RetryNum + 1;
			String itemName = currentPendingRequest.getItemName();

			listener.onSystemLog(new LogDto("ERROR", String.format("[CRC재시도] %s (%s) — %d/%d (%dms) | CRC누적:%d",
					itemName, addrHex, nextRetry, maxRetrys, elapsed, errDto.getCrcErrCount())));

			log.warn("[CRC ERROR] 재시도 – {} ({}) Retry={}/{} elapsed={}ms", itemName, addrHex, nextRetry, maxRetrys,
					elapsed);

			// [FIX-8] 중첩 synchronized 제거
			// processHex()가 이미 synchronized → 내부에서 synchronized(this) 재진입은
			// Java reentrant이라 deadlock 없으나 코드 노이즈
			rxStreamBuffer.reset();
			flushPort();
			timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
			return;
		}

		// ── 재시도 소진 → 최종 실패 ──────────────────────────────────────────
		errDto.triggerCrcError();

		String itemName = (currentPendingRequest != null) ? currentPendingRequest.getItemName() : "Unknown";
		int crcFc = (currentPendingRequest != null && currentPendingRequest.getFunctionCode() != null)
				? currentPendingRequest.getFunctionCode().getCode()
				: 0x04;

		listener.onTxRx(String.format("[%s][ERROR][RETRY_EXHAUSTED]\n", nowLogTime()));
		//		listener.onTxRx("\n");

		log.error("[CRC ERROR 최종] {} {} elapsed={}ms | CRC오류:{} 누적:{}", itemName, addrHex, elapsed,
				errDto.getCrcErrCount(), errDto.getTotalErrCount());

		listener.onMeterValueUpdated(currentSlaveId, startAddress, crcFc, "-", "CRC오류", this.avgPacketResponseTime);
		listener.onSystemLog(new LogDto("ERROR", String.format("[CRC ERROR] %s %s (%dms) | CRC:%d 누적:%d", itemName,
				addrHex, elapsed, errDto.getCrcErrCount(), errDto.getTotalErrCount())));

		rxStreamBuffer.reset(); // [FIX-8] 중첩 synchronized 제거
		flushPort();
		timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
	}

	// =========================================================================
	// ErrorStatusDto 헬퍼 — Long 복합키
	// =========================================================================

	/**
	 * [RF-2] Long 복합키 생성을 MeterRowDto.rowKey()에 위임.
	 *
	 * 이전엔 errKey()가 자체적으로 (slaveId<<32)|(fc<<16)|address 비트 패킹을 구현했다.
	 * MeterRowDto.rowKey()와 비트 배치가 우연히 같았기에 동작했지만,
	 * 한쪽만 수정되면 errorMap 키와 rowIndex 키가 어긋나
	 * "응답이 엉뚱한 행에 꽂히는" 치명적 오매칭이 발생한다.
	 * 단일 소스(MeterRowDto.rowKey)로 통일해 그 위험을 제거한다.
	 */
	private static long errKey(int slaveId, int fc, int address) {
		return MeterRowDto.rowKey(slaveId, fc, address);
	}

	private ErrorStatusDto getOrCreateErrorStatus(PendingRequest request) {
		if (request == null) {
			long key = errKey(currentSlaveId, 0x04, currentRequestAddress);
			return errorMap.computeIfAbsent(key, k -> new ErrorStatusDto(currentSlaveId,
					FunctionCode.READ_INPUT_REGISTERS, currentRequestAddress, "Unknown"));
		}
		int slaveId = request.getSlaveId();
		int addr = request.getAddress();
		FunctionCode fc = request.getFunctionCode() != null ? request.getFunctionCode()
				: FunctionCode.READ_INPUT_REGISTERS;
		long key = errKey(slaveId, fc.getCode(), addr);
		return errorMap.computeIfAbsent(key, k -> new ErrorStatusDto(slaveId, fc, addr, request.getItemName()));
	}

	private ErrorStatusDto getOrCreateErrorStatus(PendingRequest request, int fallbackAddress) {
		if (request != null)
			return getOrCreateErrorStatus(request);
		long key = errKey(currentSlaveId, 0x04, fallbackAddress);
		return errorMap.computeIfAbsent(key,
				k -> new ErrorStatusDto(currentSlaveId, FunctionCode.READ_INPUT_REGISTERS, fallbackAddress, "Unknown"));
	}

	public Map<Long, ErrorStatusDto> getErrorMap() {
		return Collections.unmodifiableMap(errorMap);
	}

	/** 해당 address를 가진 모든 SlaveID/FC 조합 항목을 제거한다. */
	public void clearErrorStatus(int address) {
		errorMap.entrySet().removeIf(e -> e.getValue().getAddress() == address);
	}

	public void clearAllErrorStatus() {
		errorMap.clear();
		log.info("ErrorStatusDto 전체 초기화 완료");
	}

	// =========================================================================
	// 유틸
	// =========================================================================

	private String nowLogTime() {
		// LocalTime 대신 LocalDateTime을 사용하여 날짜(연/월/일) 정보까지 제공합니다.
		return LocalDateTime.now().format(LOG_TIME_FMT);
	}

	/** Retry = (retryCount - remaining) + 1 */
	private int RetryNumber(PendingRequest req) {
		if (req == null)
			return 1;
		return (retryCount - req.getRetryRemaining()) + 1;
	}

	/** maxRetrys = retryCount + 1 (최초 1회 포함) */
	private int maxRetrys() {
		return retryCount + 1;
	}

	private String formatValue(DataType dataType, double value) {
		return switch (dataType) {
		case FLOAT32, DOUBLE64 -> String.format("%.4f", value);
		default -> {
			long lv = (long) value;
			yield ((double) lv == value) ? String.valueOf(lv) : String.format("%.4f", value);
		}
		};
	}

	private void flushPort() {
		if (activePort != null && activePort.isOpen())
			activePort.flushIOBuffers();
	}

	// =========================================================================
	// 상태 조회 및 제어
	// =========================================================================

	public synchronized void cancelPendingRequests() {
		int cancelled = requestQueue.size();
		requestQueue.clear();
		log.info("요청 큐 비움 — {}건 취소 (현재 진행 중 요청 완료 후 정지)", cancelled);
	}

	public synchronized void handlePortDisconnect() {
		requestQueue.clear();
		waitingResponse = false;
		currentPendingRequest = null;
		if (timeoutTask != null && !timeoutTask.isDone()) {
			timeoutTask.cancel(true);
			timeoutTask = null;
		}
		log.warn("포트 단선 — 통신 상태 즉시 초기화 (큐 비움, waitingResponse=false, 타임아웃 취소)");
	}

	public synchronized boolean isCycleComplete() {
		return !waitingResponse && requestQueue.isEmpty();
	}

	public boolean isWaitingResponse() {
		return waitingResponse;
	}

	private void updateAverageResponseTime(long elapsed) {
		this.totalResponseTime += elapsed;
		this.responseCount++;
		this.avgPacketResponseTime = (int) (totalResponseTime / responseCount);

		log.debug("[응답시간 평균] 현재:{}ms 평균:{}ms (count={})", elapsed, avgPacketResponseTime, responseCount);
	}

	// =========================================================================
	// 설정 Getter / Setter
	// =========================================================================
	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	public int getRetryCount() {
		return retryCount;
	}

	public void setTimeoutMs(int ms) {
		this.TIMEOUT_MS = ms;
		log.info("ModbusManager.TIMEOUT_MS 변경됨: {}ms", ms);
	}

	public void setTxDelayMs(int ms) {
		this.txDelayMs = ms;
	}

	public DataType getDefaultDataType() {
		return defaultDataType;
	}

	public EndianType getDefaultEndianType() {
		return defaultEndianType;
	}

	public int getavgPacketResponseTime() {
		return avgPacketResponseTime;
	}

	public ByteArrayOutputStream getRxStreamBuffer() {
		return rxStreamBuffer;
	}
}