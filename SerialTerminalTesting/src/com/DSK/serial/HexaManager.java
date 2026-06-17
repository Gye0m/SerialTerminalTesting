package com.DSK.serial;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.model.dao.MeterReadingDAO;
import com.DSK.model.dto.MeterReading;
import com.DSK.model.dto.addressMap.Omni;
import com.DSK.model.dto.common.MeterInfo;
import com.DSK.serial.converter.ByteConverter;
import com.DSK.serial.converter.DataType;
import com.DSK.serial.converter.EndianType;
import com.DSK.serial.converter.ProtocolConverter;
import com.DSK.ui.HexaTerminal;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

public class HexaManager {
	private static final Logger log = LoggerFactory.getLogger(HexaManager.class);

	// 예외처리 -> 데이터 및 패킷 조립 제한 시간 할당
	private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> timeoutTask = null;

	private volatile boolean waitingResponse = false;

	// 걸리는 시간 참조해서 시스템 로그에 출력
	private volatile long requestTime = 0;

	private final Omni meterMap = new Omni();
	private final HexaTerminal terminal;
	private SerialPort activePort;
	private OutputStream outputStream;
	private boolean isPhysicalDisconnected = false;

	// 각 패킷마다 쓸 버퍼 할당
	private final ByteArrayOutputStream rxStreamBuffer = new ByteArrayOutputStream();

	private int currentRequestAddress = 0x0000;
	private int currentSlaveId = 1; // 💡 타임아웃 발생 시 안전한 DB 저장을 위한 슬레이브 ID 백업 필드

	private boolean isRawTesting = false; // 현재 수신된 패킷이 Hex 테스트인지

	private String connectedPort;

	// 통신 제어 설정 용 변수
	private int TIMEOUT_MS = 500;
	private int txDelayMs = 100;
	private EndianType defaultEndian = EndianType.LITTLE;
	private int comSlaveId = 1;

	// 오라클 저장용 저장 변수
	private MeterReadingDAO dao = new MeterReadingDAO();

	// 송신 요청 담을 큐
	private final Queue<byte[]> requestQueue = new LinkedList<>();

	public HexaManager(HexaTerminal terminal) {
		this.terminal = terminal;
	}

	public synchronized void enqueue(byte[] packet) {
		log.info("전달받은 바이트 배열 => {}", packet);
		requestQueue.offer(packet);
	}

	public synchronized void sendRawHex(String hex, boolean autoCrc) throws Exception {
		byte[] inputBytes = ProtocolConverter.convertHexToBytes(hex);
		if (inputBytes == null || inputBytes.length == 0) {
			throw new IllegalArgumentException("올바르지 않은 HEX 형식입니다.");
		}

		this.isRawTesting = true;

		byte[] packet;

		// 자동 계산 체크가 켜져 있을 때만 CRC 연산 처리
		if (autoCrc) {
			int crc = ProtocolConverter.calculateCRC16(inputBytes, inputBytes.length);
			packet = new byte[inputBytes.length + 2];
			System.arraycopy(inputBytes, 0, packet, 0, inputBytes.length);
			packet[packet.length - 2] = (byte) (crc & 0xFF); // CRC Low
			packet[packet.length - 1] = (byte) ((crc >> 8) & 0xFF); // CRC High
		} else {
			if (inputBytes.length >= 6) {
				int dataLength = inputBytes.length - 2;

				int userCRC = ((inputBytes[inputBytes.length - 1] & 0xFF) << 8)
						| (inputBytes[inputBytes.length - 2] & 0xFF);

				int actualCRC = ProtocolConverter.calculateCRC16(inputBytes, dataLength);

				if (userCRC != actualCRC) {
					throw new IllegalArgumentException(
							"입력된 패킷의 CRC 값이 올바르지 않습니다.\n(입력값: " + String.format("0x%04X", userCRC) + " / 실제 필요한값: "
									+ String.format("0x%04X", actualCRC) + ")");
				}
			} else {
				throw new IllegalArgumentException("CRC를 포함한 온전한 모드버스 패킷 형식이 아닙니다. (최소 6바이트 이상)");
			}

			packet = inputBytes;
		}

		if (packet.length >= 4) {
			int start = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);
			this.currentRequestAddress = start;
		}

		requestQueue.offer(packet);

		if (!waitingResponse)
			sendNextPacket();
	}

	public synchronized void startSend() {
		log.info(
				"체크된 거 다 sendNextPacket() 호출\n====================================================================================================\n");
		if (!waitingResponse)
			sendNextPacket();
	}

	// 패킷 순서대로 처리
	private synchronized void sendNextPacket() {
		byte[] packet = requestQueue.poll();
		if (packet == null) {
			waitingResponse = false;
			log.info("모든 큐 패킷 전송 완료.");
			return;
		}

		waitingResponse = true;
		try {
			sendBytes(packet);
		} catch (Exception e) {
			log.error("다음 패킷 전송 과정에서 에러 발생!!", e);
			waitingResponse = false;
			sendNextPacket();
		}
	}

	public boolean validateCommunicationSpec(int baudRate, int dataBits, int stopBits, int parity) {
		return baudRate == 9600 && dataBits == 8 && stopBits == SerialPort.ONE_STOP_BIT
				&& parity == SerialPort.NO_PARITY;
	}

	public boolean connectingPort(String portName, int baudRate, int dataBits, int stopBits, int parity) {
		activePort = SerialPort.getCommPort(portName);
		if (activePort.openPort()) {
			activePort.setBaudRate(baudRate);
			activePort.setNumDataBits(dataBits);
			activePort.setNumStopBits(stopBits);
			activePort.setParity(parity);
			this.connectedPort = portName;

			outputStream = activePort.getOutputStream();
			isPhysicalDisconnected = false;

			String parityStr = switch (parity) {
			case SerialPort.NO_PARITY -> "None";
			case SerialPort.ODD_PARITY -> "Odd";
			case SerialPort.EVEN_PARITY -> "Even";
			case SerialPort.MARK_PARITY -> "Mark";
			case SerialPort.SPACE_PARITY -> "Space";
			default -> "Unknown";
			};

			StringBuilder logMsg = new StringBuilder();
			logMsg.append(String.format("시리얼 포트 연결 성공 (%s)\n", portName));
			logMsg.append(String.format("   • 보드 레이트 (Baud Rate) : %d bps\n", baudRate));
			logMsg.append(String.format("   • 데이터 비트 (Data Bits) : %d bit\n", dataBits));
			logMsg.append(String.format("   • 스톱 비트   (Stop Bits) : %s\n",
					(stopBits == SerialPort.ONE_POINT_FIVE_STOP_BITS ? "1.5" : String.valueOf(stopBits))));
			logMsg.append(String.format("   • 패리티 비트 (Parity Bit) : %s\n", parityStr));
			logMsg.append("--------------------------------------------------");

			terminal.appendSystemLog(logMsg.toString());

			initListener();
			return true;
		}
		return false;
	}

	public void disconnect() {
		if (activePort != null && activePort.isOpen()) {
			if (timeoutTask != null)
				timeoutTask.cancel(true);
			activePort.closePort();
			activePort = null;
			terminal.resetAllMeterTable();

			StringBuilder disconnectLogMsg = new StringBuilder();

			disconnectLogMsg.append(String.format("\n%s 포트 연결 해제!", this.connectedPort));
			disconnectLogMsg.append("\n--------------------------------------------------");

			terminal.appendSystemLog(disconnectLogMsg.toString());
		}
	}

	public boolean isOpen() {
		return activePort != null && activePort.isOpen();
	}

	public void communicationSetting(int scanRate, double timeOut, int endian, double txDelay, int slaveId) {
		this.TIMEOUT_MS = (int) (timeOut);
		this.comSlaveId = slaveId;
		if (endian >= 0 && endian < EndianType.values().length) {
			this.defaultEndian = EndianType.values()[endian];
		} else {
			this.defaultEndian = EndianType.LITTLE;
		}
		this.txDelayMs = (int) (txDelay);

		StringBuilder communicationControl = new StringBuilder();
		communicationControl.append("통신 제어 환경 설정 변경 완료\n");
		communicationControl
				.append(String.format("   • 기본 장치 번호 (Slave ID)  : %d (0x%02X)\n", this.comSlaveId, this.comSlaveId));
		communicationControl.append(String.format("   • 스캔 주기 (Scan Rate) : %d ms\n", scanRate));
		communicationControl.append(String.format("   • 타임아웃   (Timeout)      : %d ms\n", this.TIMEOUT_MS));
		communicationControl.append(String.format("   • 엔디안      (Endian)     : %s\n", defaultEndian));
		communicationControl.append(String.format("   • 송신 지연  (Tx Delay)   : %d ms\n", this.txDelayMs));
		communicationControl.append("--------------------------------------------------");

		log.info("\n{}", communicationControl.toString());
		terminal.appendSystemLog(communicationControl.toString());
	}

	public synchronized void sendBytes(byte[] bytes) throws Exception {
		requestTime = System.currentTimeMillis();
		if (outputStream == null)
			return;

		if (timeoutTask != null && !timeoutTask.isDone())
			timeoutTask.cancel(true);
		rxStreamBuffer.reset();

		currentSlaveId = bytes[0] & 0xFF;
		int start = ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
		currentRequestAddress = start;

		if (requestQueue.isEmpty() && !isRawTesting) {
			this.isRawTesting = false;
		}

		waitingResponse = true;

		timeoutTask = timeoutScheduler.schedule(() -> {
			synchronized (HexaManager.this) {
				if (!waitingResponse)
					return;

				waitingResponse = false;
				long elapsedTime = System.currentTimeMillis() - requestTime;

				log.error("[TIMEOUT] 실시간 타임아웃 발생! 장비 무응답! 제한시간: {}ms (실제 소요: {}ms)", TIMEOUT_MS, elapsedTime);

				terminal.updateMeterValue(start, "-", "TIMEOUT");
				terminal.appendTerminal("[RX] TIMEOUT (" + TIMEOUT_MS + "ms)");
				String addressIntToHexStr = String.format("0x%04X", start);
				terminal.appendSystemLog(String.format("[에러] 장치 번호 #%d - 주소 %s 장비 무응답 (타임아웃 %dms 경과)", currentSlaveId,
						addressIntToHexStr, elapsedTime));

				dao.insertErrorData(currentSlaveId, addressIntToHexStr, 1);

				rxStreamBuffer.reset();

				if (activePort != null && activePort.isOpen()) {
					activePort.flushIOBuffers();
				}
				sendNextPacket();
			}
		}, TIMEOUT_MS, TimeUnit.MILLISECONDS);

		terminal.appendTx(ProtocolConverter.convertBytesToHex(bytes, bytes.length));
		outputStream.write(bytes);
		outputStream.flush();
	}

	private void initListener() {
		activePort.addDataListener(new SerialPortDataListener() {
			@Override
			public int getListeningEvents() {
				return SerialPort.LISTENING_EVENT_DATA_AVAILABLE | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
			}

			@Override
			public void serialEvent(SerialPortEvent event) {
				if (isPhysicalDisconnected)
					return;

				if (event.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
					isPhysicalDisconnected = true;
					log.error("물리적 연결 해제됨");
					disconnect();
					terminal.handlePhysicalDisconnect();
					return;
				}

				if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
					int size = activePort.bytesAvailable();
					if (size <= 0)
						return;

					byte[] data = new byte[size];
					int read = activePort.readBytes(data, size);

					if (read > 0) {
						synchronized (HexaManager.this) {
							rxStreamBuffer.write(data, 0, read);
							byte[] currentBuffer = rxStreamBuffer.toByteArray();
							int expectedLength = getExpectedLength(currentBuffer);

							if (expectedLength != -1 && currentBuffer.length >= expectedLength) {
								byte[] completePacket = Arrays.copyOf(currentBuffer, expectedLength);
								processHex(completePacket);
								rxStreamBuffer.reset();

								if (currentBuffer.length > expectedLength) {
									rxStreamBuffer.write(currentBuffer, expectedLength,
											currentBuffer.length - expectedLength);
								}
							}
						}
					}
				}
			}
		});
	}

	private int getExpectedLength(byte[] buffer) {
		if (buffer == null || buffer.length < 3)
			return -1;

		int function = buffer[1] & 0xFF;

		// 장비가 거절 에러 응답(Exception Packet: 0x84, 0x86 등)을 보낸 경우 무조건 5바이트 고정 처리
		if ((function & 0x80) != 0) {
			return 5;
		}

		switch (function) {
		case 0x04:
			int byteCount = buffer[2] & 0xFF;
			return 3 + byteCount + 2; // Header(3) + Data(N) + CRC(2)
		case 0x06:
			return 8; // 설정 쓰기 응답은 8바이트 고정
		default:
			return -1;
		}
	}

	private synchronized void processHex(byte[] incomingData) {
		if (!waitingResponse)
			return;

		int packetLength = incomingData.length;
		String hex = ProtocolConverter.convertBytesToHex(incomingData, packetLength);
		log.info("완성된 Modbus 패킷 처리 시작 (processHex) : {}", hex);

		boolean isPacketComplete = false;
		int dataLength = packetLength - 2;
		int startAddress = currentRequestAddress;

		int receivedCRC = (Integer) ByteConverter.convert(incomingData, packetLength - 2, DataType.UINT16,
				EndianType.LITTLE);
		int calculatedCRC = ProtocolConverter.calculateCRC16(incomingData, dataLength);

		if (receivedCRC == calculatedCRC) {
			long elapsedTime = System.currentTimeMillis() - requestTime;
			terminal.appendRx(hex + " (" + elapsedTime + "ms)");

			waitingResponse = false;

			if (timeoutTask != null) {
				timeoutTask.cancel(true);
			}

			String addressHexStr = String.format("0x%04X", startAddress);
			int functionCode = incomingData[1] & 0xFF;

			// -----------------------------------------------------------------
			// ✨ [교정 분기 1] 장비가 명시적으로 응답 거부(Modbus Exception)한 경우 처리
			// -----------------------------------------------------------------
			if ((functionCode & 0x80) != 0) {
				int exceptionCode = incomingData[2] & 0xFF;
				String errReason = switch (exceptionCode) {
				case 1 -> "지원하지 않는 기능 코드(Illegal Function)";
				case 2 -> "존재하지 않거나 접근 권한이 없는 데이터 주소(Illegal Data Address)";
				case 3 -> "허용 한도를 초과한 값 대입(Illegal Data Value)";
				case 4 -> "장치 내부 오류 혹은 쓰기 잠금 상태(Slave Device Failure)";
				default -> "기타 장비 정의 에러 코드";
				};

				terminal.updateMeterValue(startAddress, "-", "장비거부");
				terminal.appendSystemLog(String.format("[변경실패] 장치 #%d - 주소 %s 변경 거부 사유: %s (코드: 0x%02X)",
						currentSlaveId, addressHexStr, errReason, exceptionCode));

				this.isRawTesting = false;
				if (activePort != null && activePort.isOpen()) {
					activePort.flushIOBuffers();
				}
				timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
				return;
			}

			// -----------------------------------------------------------------
			// ✨ [교정 분기 2] FC 06 파라미터 값 덮어쓰기 성공 응답 가로채기
			// -----------------------------------------------------------------
			if (functionCode == 0x06) {
				int confirmedAddress = ((incomingData[2] & 0xFF) << 8) | (incomingData[3] & 0xFF);
				int confirmedValue = ((incomingData[4] & 0xFF) << 8) | (incomingData[5] & 0xFF);
				String confirmedAddrHex = String.format("0x%04X", confirmedAddress);

				log.info("[덮어쓰기 성공 확인] 장치:#{} 주소:{} -> 값:{} 변경 완료", currentSlaveId, confirmedAddrHex, confirmedValue);

				// UI 동기화 및 성공 로그 출력
				terminal.updateMeterValue(confirmedAddress, String.valueOf(confirmedValue), "정상");
				terminal.appendSystemLog(
						String.format("[변경성공] 장치 번호 #%d - 주소 %s 파라미터가 성공적으로 '%d'(으)로 덮어써졌습니다. (응답시간: %dms)",
								currentSlaveId, confirmedAddrHex, confirmedValue, elapsedTime));

				this.isRawTesting = false;
				if (activePort != null && activePort.isOpen()) {
					activePort.flushIOBuffers();
				}
				timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
				return;
			}

			// 만약 내가 직접 쏜 Raw HEX 패킷(FC 04 등)에 대한 응답이라면?
			if (isRawTesting) {
				log.info("수동 테스트(Raw HEX) 응답 파싱 시작.");

				terminal.appendSystemLog(
						String.format("[수동 성공] 장치 번호: #%d 수동 명령 응답 완료 (소요 시간: %dms)", currentSlaveId, elapsedTime));

				StringBuilder rawDetails = new StringBuilder();
				int byteCount = incomingData[2] & 0xFF;
				int currentOffset = 3;
				int registerAddress = startAddress;

				while (currentOffset < 3 + byteCount) {
					MeterInfo info = meterMap.get(registerAddress);

					if (info == null) {
						rawDetails.append(String.format("   • [0x%04X] 미등록 주소 (건너뜀)\n", registerAddress));
						currentOffset += 2;
						registerAddress += 1;
						continue;
					}

					int dataSize = ByteConverter.getDataSize(info.getDataType());
					EndianType activeEndian = defaultEndian != null ? defaultEndian : info.getEndianType();
					Object activeValue = ByteConverter.convert(incomingData, currentOffset, info.getDataType(),
							activeEndian);

					rawDetails.append(String.format("   • %s (%s) [주소: 0x%04X, 매핑타입: %s]\n", info.getName(),
							info.getUnit(), registerAddress, info.getDataType()));
					rawDetails.append(String.format("     [현재 세팅 값] => (%s) : %s\n", activeEndian, activeValue));
					rawDetails.append("     --------------------------------------------------\n");

					if (dataSize == 4) {
						Object intBig = ByteConverter.convert(incomingData, currentOffset, DataType.INT32,
								EndianType.BIG);
						Object intLittle = ByteConverter.convert(incomingData, currentOffset, DataType.INT32,
								EndianType.LITTLE);
						Object intWSwap = ByteConverter.convert(incomingData, currentOffset, DataType.INT32,
								EndianType.WORD_SWAP);
						Object fltBig = ByteConverter.convert(incomingData, currentOffset, DataType.FLOAT32,
								EndianType.BIG);
						Object fltLittle = ByteConverter.convert(incomingData, currentOffset, DataType.FLOAT32,
								EndianType.LITTLE);
						Object fltWSwap = ByteConverter.convert(incomingData, currentOffset, DataType.FLOAT32,
								EndianType.WORD_SWAP);

						rawDetails.append(
								String.format("     └─  정수(INT32)   => BIG: %-10s | LITTLE: %-10s | W-SWAP: %s\n",
										intBig, intLittle, intWSwap));
						rawDetails.append(
								String.format("     └─  실수(FLOAT32) => BIG: %-10s | LITTLE: %-10s | W-SWAP: %s\n",
										fltBig, fltLittle, fltWSwap));
					} else if (dataSize == 2) {
						Object i16Big = ByteConverter.convert(incomingData, currentOffset, DataType.INT16,
								EndianType.BIG);
						Object i16Little = ByteConverter.convert(incomingData, currentOffset, DataType.INT16,
								EndianType.LITTLE);
						Object u16Big = ByteConverter.convert(incomingData, currentOffset, DataType.UINT16,
								EndianType.BIG);
						Object u16Little = ByteConverter.convert(incomingData, currentOffset, DataType.UINT16,
								EndianType.LITTLE);

						rawDetails.append(
								String.format("     └─  정수(INT16)   => BIG: %-10s | LITTLE: %s\n", i16Big, i16Little));
						rawDetails.append(
								String.format("     └─  부호X(UINT16) => BIG: %-10s | LITTLE: %s\n", u16Big, u16Little));
					} else if (dataSize == 8) {
						Object dblBig = ByteConverter.convert(incomingData, currentOffset, DataType.DOUBLE64,
								EndianType.BIG);
						Object dblLittle = ByteConverter.convert(incomingData, currentOffset, DataType.DOUBLE64,
								EndianType.LITTLE);

						byte[] raw8Bytes = new byte[8];
						System.arraycopy(incomingData, currentOffset, raw8Bytes, 0, 8);

						java.nio.ByteBuffer bbBig = java.nio.ByteBuffer.wrap(raw8Bytes)
								.order(java.nio.ByteOrder.BIG_ENDIAN);
						long int64Big = bbBig.getLong();

						byte[] reversed8 = new byte[8];
						for (int i = 0; i < 8; i++)
							reversed8[i] = raw8Bytes[7 - i];
						java.nio.ByteBuffer bbLittle = java.nio.ByteBuffer.wrap(reversed8)
								.order(java.nio.ByteOrder.BIG_ENDIAN);
						long int64Little = bbLittle.getLong();

						rawDetails.append(
								String.format("     └─  실수(DOUBLE64) => BIG: %-20s | LITTLE: %s\n", dblBig, dblLittle));
						rawDetails.append(String.format("     └─  정수(INT64/Long) => BIG: %-20d | LITTLE: %d\n",
								int64Big, int64Little));
					}

					rawDetails.append("==================================================\n");
					currentOffset += dataSize;
					registerAddress += dataSize / 2;
				}

				if (rawDetails.length() == 0) {
					rawDetails.append("   측정된 데이터가 없거나 분석할 수 없습니다.");
				}

				terminal.updateRawResponseArea(hex, rawDetails.toString());
				isRawTesting = false;

				if (activePort != null && activePort.isOpen()) {
					activePort.flushIOBuffers();
				}

				timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
				return;
			}

			// ------------------------------------------------------------
			// 아래는 오직 [자동 검침 요청(FC 04)]일 때만 실행되는 기존 테이블 파싱 구역
			// ------------------------------------------------------------
			StringBuilder valueStr = new StringBuilder();
			int byteCount = incomingData[2] & 0xFF;
			int currentOffset = 3;
			int registerAddress = startAddress;

			while (currentOffset < 3 + byteCount) {
				MeterInfo info = meterMap.get(registerAddress);

				if (info == null) {
					valueStr.append(String.format("UNKNOWN [0x%04X]\n", registerAddress));
					currentOffset += 4;
					registerAddress += 2;
					continue;
				}

				EndianType endian = defaultEndian != null ? defaultEndian : info.getEndianType();
				Object value = ByteConverter.convert(incomingData, currentOffset, info.getDataType(), endian);

				valueStr.append(value).append(info.getUnit()).append(" ");
				int dataSize = ByteConverter.getDataSize(info.getDataType());

				currentOffset += dataSize;
				registerAddress += dataSize / 2;
			}

			String value = valueStr.toString().trim();
			terminal.updateMeterValue(startAddress, value, "정상");

			terminal.appendSystemLog(String.format("[성공] 장치 번호 #%d - 주소 %s 검침 완료 (%s) -> 소요 시간: %dms", currentSlaveId,
					addressHexStr, value, elapsedTime));

			if (activePort != null && activePort.isOpen()) {
				activePort.flushIOBuffers();
			}

			isPacketComplete = true;

		} else {
			// CRC 오류 처리 구역
			waitingResponse = false;
			if (timeoutTask != null) {
				timeoutTask.cancel(true);
			}

			long elapsedTime = System.currentTimeMillis() - requestTime;
			String addressHexStr = String.format("0x%04X", startAddress);

			terminal.updateMeterValue(startAddress, "-", "CRC 오류");
			terminal.appendTerminal("[RX] CRC ERROR : " + hex + " (" + elapsedTime + "ms)");

			terminal.appendSystemLog(String.format("[오류] 장치 번호 #%d - 주소 %s 수신 패킷 CRC 불일치 깨짐 (%dms 소요)", currentSlaveId,
					addressHexStr, elapsedTime));

			log.error("=== CRC 에러 발생 ===");
			sendNextPacket();
		}

		if (isPacketComplete) {
			timeoutScheduler.schedule(this::sendNextPacket, txDelayMs, TimeUnit.MILLISECONDS);
		}
	}

	public List<MeterReading> getMeterHistory(int deviceId, String addressMap, int dateSelected) {
		return dao.getMetersByAddress(deviceId, addressMap, dateSelected);
	}

	public List<MeterReading> getErrorMeterHistory(int deviceId, String addressMap, int dateSelected) {
		return dao.getErrorMetersByAddress(deviceId, addressMap, dateSelected);
	}
}