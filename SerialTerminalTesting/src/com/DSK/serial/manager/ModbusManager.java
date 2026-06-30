package com.DSK.serial.manager;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.model.dao.MeterReadingDAO;
import com.DSK.model.dto.MeterReading;
import com.DSK.model.dto.common.ErrorStatusDto;
import com.DSK.model.dto.common.LogDto;
import com.DSK.serial.constant.DataType;
import com.DSK.serial.constant.EndianType;
import com.DSK.serial.converter.ByteConverter;
import com.DSK.serial.converter.ProtocolConverter;
import com.DSK.serial.core.PacketReceiver;
import com.DSK.ui.modbus.ModbusTerminal;
import com.fazecast.jSerialComm.SerialPort;

public class ModbusManager {

	private static final Logger log = LoggerFactory.getLogger(ModbusManager.class);

	// ──────────────── 타임아웃 스케줄러 ────────────────
	private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "Modbus-Timeout");
		t.setDaemon(true);
		return t;
	});
	private ScheduledFuture<?> timeoutTask = null;

	// ──────────────── 통신 상태 ────────────────
	private volatile boolean waitingResponse = false;
	private volatile long requestTime = 0;

	// ──────────────── 의존 객체 ────────────────
	private final ModbusTerminal terminal;
	private SerialPort activePort;
	private OutputStream outputStream;

	// ──────────────── RX 버퍼 ────────────────
	private final ByteArrayOutputStream rxStreamBuffer = new ByteArrayOutputStream();

	// ──────────────── 현재 처리 중인 요청 ────────────────
	private PendingRequest currentPendingRequest = null;
	private int currentRequestAddress = 0x0000;
	private int currentSlaveId = 1;

	// ──────────────── 통신 설정 ────────────────
	private int TIMEOUT_MS = 500;
	private int txDelayMs = 100;
	private int retryCount = 3;
	private EndianType defaultEndianType = EndianType.LITTLE_ENDIAN;
	private DataType defaultDataType = DataType.UINT32;

	// ──────────────── DB ────────────────
	private final MeterReadingDAO dao = new MeterReadingDAO();

	// ──────────────── 송신 큐 ────────────────
	// ArrayDeque 를 써야 addFirst()(재시도 시 맨 앞 재삽입)가 가능하다.
	private final Deque<PendingRequest> requestQueue = new ArrayDeque<>();

	// ──────────────── 에러 맵 ────────────────
	// Key: 레지스터 주소, Value: 누적 에러 통계
	// ConcurrentHashMap: timeoutScheduler 스레드 ↔ EDT 동시 접근 가능
	private final Map<Integer, ErrorStatusDto> errorMap = new ConcurrentHashMap<>();

	// ──────────────── PacketReceiver 연결 ────────────────
	private PacketReceiver packetReceiver;

	// =========================================================================
	// 생성자
	// =========================================================================
	public ModbusManager(ModbusTerminal terminal) {
		this.terminal = terminal;
	}

	public void setPacketReceiver(PacketReceiver packetReceiver) {
		this.packetReceiver = packetReceiver;
	}

	// =========================================================================
	// 포트 연결 초기화 (SerialConnectionManager.connect() 에서 호출)
	// =========================================================================
	public void initializeCommunicationTunnel(SerialPort port, OutputStream os, String portName) {
		this.activePort = port;
		this.outputStream = os;

		// 재연결 시 상태 완전 초기화
		this.waitingResponse = false;
		this.currentPendingRequest = null;
		synchronized (this) {
			requestQueue.clear();
			rxStreamBuffer.reset();
		}

		log.info("ModbusManager 초기화 완료: {}", portName);
	}

	// =========================================================================
	// 큐 관리
	// =========================================================================

	public synchronized void enqueue(PendingRequest request) {
		requestQueue.offer(request);
		log.debug("큐 삽입: {}", request);
	}

	public void startSend() {
		synchronized (this) {
			if (!waitingResponse) {
				sendNextPacket();
			} else {
				log.warn("이전 패킷 처리 중 – 중복 발송 차단");
			}
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
		// ✅ 이 시점에 행별 Slave ID 가 확정된다 — 이후 sendBytes() 는 이 값을
		// 그대로 신뢰하고 쓴다 (패킷 바이트를 다시 까서 추측하지 않는다).
		currentSlaveId = request.getSlaveId();
		waitingResponse = true;

		try {
			sendBytes(request.getPacket());
		} catch (Exception e) {
			log.error("패킷 전송 실패: {}", request, e);
			terminal.appendSystemLog(new LogDto("ERROR", "패킷 전송 실패: " + e.getMessage()));
			waitingResponse = false;
			sendNextPacket();
		}
	}

	// =========================================================================
	// 물리 바이트 송신
	// =========================================================================
	public synchronized void sendBytes(byte[] bytes) throws Exception {
		if (outputStream == null)
			throw new IllegalStateException("포트 미연결");

		// ✅ [수정] bytes[0] 을 다시 파싱하던 방식 → currentSlaveId 를 그대로 사용.
		// sendNextPacket() 에서 이미 request.getSlaveId() 로 확정해둔 값이 단일
		// 진실원천이다. "패킷 첫 바이트 = Slave ID" 라는 암묵적 가정에 또 다른 곳에서
		// 의존하지 않게 되어, 패킷 조립 방식이 바뀌어도 이 로직은 영향받지 않는다.
		if (packetReceiver != null) {
			packetReceiver.setExpectedSlaveId(currentSlaveId);
		} else {
			log.warn("packetReceiver 가 연결되지 않았습니다 – SerialConnectionManager.connect() 에서 "
					+ "modbusManager.setPacketReceiver() 호출 여부를 확인하세요.");
		}

		if (timeoutTask != null && !timeoutTask.isDone())
			timeoutTask.cancel(false);
		rxStreamBuffer.reset();
		requestTime = System.currentTimeMillis();

		// TX 로그
		String txHex = ProtocolConverter.convertBytesToHex(bytes, bytes.length);
		String txTimeStr = nowMillisStr();
		SwingUtilities.invokeLater(() -> terminal.appendTxRxTerminal("[" + txTimeStr + "][TX] " + txHex + "\n"));

		// ── 타임아웃 핸들러 ──────────────────────────────────────────────────
		final PendingRequest sentRequest = currentPendingRequest;

		timeoutTask = timeoutScheduler.schedule(() -> {
			synchronized (ModbusManager.this) {
				if (!waitingResponse)
					return;

				long elapsed = System.currentTimeMillis() - requestTime;
				waitingResponse = false;

				// ── 재시도 가능 ──────────────────────────────────────────────
				if (sentRequest != null && sentRequest.canRetry()) {
					PendingRequest retryReq = sentRequest.retryInstance();
					requestQueue.addFirst(retryReq);

					ErrorStatusDto errDto = getOrCreateErrorStatus(sentRequest);
					errDto.incrementRetry();

					log.warn("[TIMEOUT] 재시도 – {} 남은 횟수: {}", sentRequest.getItemName(), retryReq.getRetryRemaining());

					terminal.appendSystemLog(new LogDto("ERROR",
							String.format("[재시도] %s (0x%04X) – 재시도 %d회 남음 (%dms 경과)", sentRequest.getItemName(),
									sentRequest.getAddress(), retryReq.getRetryRemaining(), elapsed)));

					rxStreamBuffer.reset();
					flushPort();
					timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
					return;
				}

				// ── 재시도 소진 → 최종 실패 ─────────────────────────────────
				int addr = (sentRequest != null) ? sentRequest.getAddress() : currentRequestAddress;
				String addrHex = String.format("0x%04X", addr);

				ErrorStatusDto errDto = (sentRequest != null) ? getOrCreateErrorStatus(sentRequest)
						: errorMap.computeIfAbsent(addr, k -> new ErrorStatusDto(k, "Unknown"));
				errDto.triggerTimeout();

				log.error("[TIMEOUT 최종] {} {} – {}ms", addrHex, sentRequest != null ? sentRequest.getItemName() : "?",
						elapsed);

				terminal.appendSystemLog(new LogDto("ERROR",
						String.format("[TIMEOUT] %s %s 무응답 (%dms) | 누적 오류:%d, 연속:%d",
								sentRequest != null ? sentRequest.getItemName() : "Unknown", addrHex, elapsed,
								errDto.getTotalErrCount(), errDto.getConsecutiveFails())));

				String rxTimeStr = nowMillisStr();
				SwingUtilities.invokeLater(() -> {
					terminal.appendTxRxTerminal("[" + rxTimeStr + "][RX] TIMEOUT (" + elapsed + "ms)\n");
					terminal.updateMeterValue(addr, "-", "타임아웃");
				});

				dao.insertErrorData(currentSlaveId, addrHex, 1);
				rxStreamBuffer.reset();
				flushPort();
				sendNextPacket();
			}
		}, TIMEOUT_MS, TimeUnit.MILLISECONDS);

		outputStream.write(bytes);
		outputStream.flush();
	}

	// =========================================================================
	// PacketReceiver 에서 완성 패킷 수신 시 호출
	// =========================================================================
	public void onModbusPacket(byte[] packet) {
		processHex(packet);
	}

	// =========================================================================
	// 예상 패킷 길이 (PacketReceiver 에서 완성 여부 판단)
	// =========================================================================
	public int getExpectedLength(byte[] buffer) {
		if (buffer == null || buffer.length < 3)
			return -1;
		int function = buffer[1] & 0xFF;
		if ((function & 0x80) != 0)
			return 5; // Modbus Exception 고정
		switch (function) {
		case 0x01:
		case 0x02:
		case 0x03:
		case 0x04: {
			int byteCount = buffer[2] & 0xFF;
			return 3 + byteCount + 2;
		}
		case 0x06:
			return 8;
		case 0x10:
			return 8;
		default:
			return -1;
		}
	}

	// =========================================================================
	// 응답 패킷 처리
	// =========================================================================
	private synchronized void processHex(byte[] incomingData) {
		if (!waitingResponse)
			return;

		int len = incomingData.length;
		String hex = ProtocolConverter.convertBytesToHex(incomingData, len);
		log.info("processHex: {}", hex);

		int startAddress = currentRequestAddress;

		// ── CRC 검증 ─────────────────────────────────────────────────────────
		int dataLen = len - 2;
		int receivedCRC = (Integer) ByteConverter.convert(incomingData, len - 2, DataType.UINT16,
				EndianType.LITTLE_ENDIAN);
		int calculatedCRC = ProtocolConverter.calculateCRC16(incomingData, dataLen);

		if (receivedCRC != calculatedCRC) {
			handleCrcError(startAddress);
			return;
		}

		// ── CRC 정상 ─────────────────────────────────────────────────────────
		long elapsed = System.currentTimeMillis() - requestTime;
		String rxTimeStr = nowMillisStr();
		SwingUtilities.invokeLater(
				() -> terminal.appendTxRxTerminal("[" + rxTimeStr + "][RX] " + hex + " (" + elapsed + "ms)\n"));

		waitingResponse = false;
		if (timeoutTask != null)
			timeoutTask.cancel(false);

		// 정상 수신 시 연속 실패 리셋
		ErrorStatusDto errDto = errorMap.get(startAddress);
		if (errDto != null)
			errDto.resetConsecutiveFails();

		int functionCode = incomingData[1] & 0xFF;
		String addrHex = String.format("0x%04X", startAddress);

		if ((functionCode & 0x80) != 0) {
			handleModbusException(incomingData, startAddress, addrHex, elapsed);
			return;
		}
		if (functionCode == 0x06) {
			handleWriteResponse(incomingData, elapsed);
			return;
		}

		handleReadResponse(incomingData, startAddress, elapsed);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Read 응답 파싱
	// ─────────────────────────────────────────────────────────────────────────
	private void handleReadResponse(byte[] incomingData, int startAddress, long elapsed) {
		// PendingRequest → MeterRowDto 위임으로 메타데이터 취득
		DataType dataType = (currentPendingRequest != null) ? currentPendingRequest.getDataType() : DataType.UINT16;
		EndianType endianType = (currentPendingRequest != null) ? currentPendingRequest.getEndianType()
				: EndianType.LITTLE_ENDIAN;
		double scale = (currentPendingRequest != null) ? currentPendingRequest.getScale() : 1.0;
		String itemName = (currentPendingRequest != null) ? currentPendingRequest.getItemName() : "?";

		int byteCount = incomingData[2] & 0xFF;
		int dataOffset = 3;
		int expectedSize = ByteConverter.getDataSize(dataType);

		String displayValue;
		String status;

		if (byteCount < expectedSize || (dataOffset + expectedSize) > (incomingData.length - 2)) {
			log.warn("[{}] 바이트 불일치 – 기대:{}B 수신:{}B UINT16 폴백", itemName, expectedSize, byteCount);
			displayValue = (byteCount >= 2)
					? formatValue(DataType.UINT16,
							((Number) ByteConverter.convert(incomingData, dataOffset, DataType.UINT16, endianType))
									.doubleValue() * scale)
					: "파싱오류";
			status = "데이터오류";
		} else {
			Object raw = ByteConverter.convert(incomingData, dataOffset, dataType, endianType);
			double numeric = ((Number) raw).doubleValue() * scale;
			displayValue = formatValue(dataType, numeric);
			status = "정상";
			log.info("[{}] 0x{} {} {} scale={} → {}", itemName, String.format("%04X", startAddress), dataType,
					endianType, scale, displayValue);
		}

		terminal.appendSystemLog(new LogDto("SYSTEM", String.format("[수신] %s (0x%04X) → %s [%s] (%dms)", itemName,
				startAddress, displayValue, status, elapsed)));

		SwingUtilities.invokeLater(() -> terminal.updateMeterValue(startAddress, displayValue, status));

		flushPort();
		timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Write Single Register 응답
	// ─────────────────────────────────────────────────────────────────────────
	private void handleWriteResponse(byte[] incomingData, long elapsed) {
		int confirmedAddr = ((incomingData[2] & 0xFF) << 8) | (incomingData[3] & 0xFF);
		int confirmedValue = ((incomingData[4] & 0xFF) << 8) | (incomingData[5] & 0xFF);
		String addrHex = String.format("0x%04X", confirmedAddr);

		log.info("[쓰기 완료] 장치:{} 주소:{} 값:{}", currentSlaveId, addrHex, confirmedValue);

		SwingUtilities
				.invokeLater(() -> terminal.updateMeterValue(confirmedAddr, String.valueOf(confirmedValue), "정상"));

		terminal.appendSystemLog(new LogDto("SYSTEM",
				String.format("[변경성공] 장치 #%d 주소 %s → %d (%dms)", currentSlaveId, addrHex, confirmedValue, elapsed)));

		flushPort();
		timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// Modbus Exception 응답
	// ─────────────────────────────────────────────────────────────────────────
	private void handleModbusException(byte[] incomingData, int startAddress, String addrHex, long elapsed) {
		int exCode = incomingData[2] & 0xFF;
		String reason = switch (exCode) {
		case 1 -> "지원하지 않는 기능 코드";
		case 2 -> "존재하지 않는 데이터 주소";
		case 3 -> "허용 한도 초과 값";
		case 4 -> "장치 내부 오류 / 쓰기 잠금";
		default -> "장비 정의 에러 " + exCode;
		};

		ErrorStatusDto errDto = getOrCreateErrorStatus(currentPendingRequest, startAddress);
		errDto.triggerTimeout();

		log.error("[Exception] Slave:{} Addr:{} 사유:{}", currentSlaveId, addrHex, reason);

		SwingUtilities.invokeLater(() -> terminal.updateMeterValue(startAddress, "-", "장비거부"));

		terminal.appendSystemLog(new LogDto("ERROR", String.format("[장비거부] 장치 #%d 주소 %s – %s (코드:0x%02X) | 누적:%d",
				currentSlaveId, addrHex, reason, exCode, errDto.getTotalErrCount())));

		flushPort();
		timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// CRC 에러
	// ─────────────────────────────────────────────────────────────────────────
	private void handleCrcError(int startAddress) {
		long elapsed = System.currentTimeMillis() - requestTime;
		String addrHex = String.format("0x%04X", startAddress);

		waitingResponse = false;
		if (timeoutTask != null)
			timeoutTask.cancel(false);

		ErrorStatusDto errDto = getOrCreateErrorStatus(currentPendingRequest, startAddress);
		errDto.triggerCrcError();

		log.error("[CRC ERROR] {} {}ms | crcErrCount={}", addrHex, elapsed, errDto.getCrcErrCount());

		SwingUtilities.invokeLater(() -> terminal.updateMeterValue(startAddress, "-", "CRC오류"));

		terminal.appendSystemLog(new LogDto("ERROR", String.format("[CRC ERROR] %s (%dms) | CRC오류:%d, 누적:%d", addrHex,
				elapsed, errDto.getCrcErrCount(), errDto.getTotalErrCount())));

		synchronized (this) {
			rxStreamBuffer.reset();
		}
		flushPort();
		timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
	}

	// =========================================================================
	// ErrorStatusDto 헬퍼
	// =========================================================================

	/** PendingRequest 에서 itemName 을 꺼내 ErrorStatusDto 를 생성하거나 반환 */
	private ErrorStatusDto getOrCreateErrorStatus(PendingRequest request) {
		String itemName = (request != null) ? request.getItemName() : "Unknown";
		int addr = (request != null) ? request.getAddress() : currentRequestAddress;
		return errorMap.computeIfAbsent(addr, k -> new ErrorStatusDto(k, itemName));
	}

	/** request 가 null 일 경우 fallback address 를 사용하는 오버로드 */
	private ErrorStatusDto getOrCreateErrorStatus(PendingRequest request, int fallbackAddress) {
		if (request != null)
			return getOrCreateErrorStatus(request);
		return errorMap.computeIfAbsent(fallbackAddress, k -> new ErrorStatusDto(k, "Unknown"));
	}

	/**
	 * 터미널의 "오류 이력 조회" 버튼에서 호출.
	 * 읽기 전용 뷰를 반환 – 외부에서 직접 수정 불가.
	 */
	public Map<Integer, ErrorStatusDto> getErrorMap() {
		return Collections.unmodifiableMap(errorMap);
	}

	public void clearErrorStatus(int address) {
		errorMap.remove(address);
	}

	public void clearAllErrorStatus() {
		errorMap.clear();
	}

	// =========================================================================
	// 유틸
	// =========================================================================

	private String formatValue(DataType dataType, double value) {
		switch (dataType) {
		case FLOAT32:
		case DOUBLE64:
			return String.format("%.4f", value);
		default:
			long lv = (long) value;
			return ((double) lv == value) ? String.valueOf(lv) : String.format("%.4f", value);
		}
	}

	private void flushPort() {
		if (activePort != null && activePort.isOpen())
			activePort.flushIOBuffers();
	}

	private String nowMillisStr() {
		return java.time.LocalDateTime.now()
				.format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS"));
	}

	// =========================================================================
	// 상태 조회
	// =========================================================================

	/** 자동 검침 패널에서 이전 사이클 완료 여부 확인 */
	public synchronized boolean isCycleComplete() {
		return !waitingResponse && requestQueue.isEmpty();
	}

	public boolean isWaitingResponse() {
		return waitingResponse;
	}

	// =========================================================================
	// 설정 Getter / Setter
	// =========================================================================

	public void setRetryCount(int retryCount) {
		this.retryCount = retryCount;
	}

	public int getRetryCount() {
		return this.retryCount;
	}

	public void setTimeoutMs(int ms) {
		this.TIMEOUT_MS = ms;
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

	public ByteArrayOutputStream getRxStreamBuffer() {
		return rxStreamBuffer;
	}

	public List<MeterReading> getMeterHistory(int deviceId, String addressMap, int dateSelected) {
		return dao.getMetersByAddress(deviceId, addressMap, dateSelected);
	}
}