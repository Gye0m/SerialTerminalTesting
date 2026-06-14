package com.DSK.serial;

import com.DSK.model.dao.MeterReadingDAO;
import com.DSK.model.dto.MeterAddressMap;
import com.DSK.model.dto.MeterInfo;
import com.DSK.model.dto.MeterReading;
import com.DSK.ui.HexaTerminal;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.OutputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class HexaManager {
	private static final Logger log = LoggerFactory.getLogger(HexaManager.class);

	// 예외처리 -> 데이터 및 패킷 조립 제한 시간 할당
	private final ScheduledExecutorService timeoutScheduler = Executors.newSingleThreadScheduledExecutor();
	private ScheduledFuture<?> timeoutTask = null;

	private volatile boolean waitingResponse = false;
	private volatile long requestTime = 0;
	private final int TIMEOUT_MS = 500;

	private final MeterAddressMap meterMap = new MeterAddressMap();
	private final HexaTerminal terminal;
	private SerialPort activePort;
	private OutputStream outputStream;
	private boolean isPhysicalDisconnected = false;

	// 각 패킷마다 쓸 버퍼 할당
	private final byte[] hexReadBuffer = new byte[64];
	private int hexBufferIndex = 0;
	private int currentRequestAddress = 0x0000;

	// 오라클 저장용 저장 변수
	private MeterReadingDAO dao = new MeterReadingDAO();

	// 응답 요청 담을 바이트 배열을 담을 Queue리스트
	private final Queue<byte[]> requestQueue = new LinkedList<>();

	public HexaManager(HexaTerminal terminal) {
		this.terminal = terminal;
	}

	public synchronized void enqueue(byte[] packet) {
		requestQueue.offer(packet);
	}

	public synchronized void startSend() {
		log.info(
				"체크된 거 다 sendNextPacket()호출 \n====================================================================================================\n");
		if (!waitingResponse)
			sendNextPacket();
	}

	// 패킷 순서대로 처리
	// 에러 밸생시 -> 다음 패킷 처리
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

	// <옴니 RTU 방식>
	// ◈ 통신방식 : RS485(EIA/TIA-485) ,Modbus RTU 방식
	// ◈ 통신속도 : 9600 bps
	// ◈ 데이터비트 : 8비트
	// ◈ 패리티비트 : None
	// ◈ 스톱비트 : 1비트
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
			outputStream = activePort.getOutputStream();
			isPhysicalDisconnected = false;
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
		}
	}

	public boolean isOpen() {
		return activePort != null && activePort.isOpen();
	}

	// 한 패킷이 전송을 완전히 마치고 난 다음, sendNextPacket() 호출하자
	public synchronized void sendBytes(byte[] bytes) throws Exception {
		if (outputStream != null) {
			if (timeoutTask != null && !timeoutTask.isDone())
				timeoutTask.cancel(true);

			hexBufferIndex = 0;
			int start = ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
			this.currentRequestAddress = start;

			log.info("이번에 보내는 데이터 이름 : {}", meterMap.get(start) != null ? meterMap.get(start).getName() : "Unknown");

			waitingResponse = true;
			requestTime = System.currentTimeMillis();
			log.info("타이머 시작!!");

			// =============== 0.5초 지나면 오류 예외처리 ===============
			timeoutTask = timeoutScheduler.schedule(() -> {
				if (waitingResponse) {
					waitingResponse = false;
					log.error("[TIMEOUT] 0.5초 실시간 타임아웃 발생! 장비 무응답!");
					terminal.appendRxText(String.format("[TIMEOUT] 장비 무응답 (임계치: %dms) - 선로 연결을 확인해주세요.\n", TIMEOUT_MS));

					int slaveId = hexReadBuffer[0] & 0xFF;
					String addressIntToHexStr = String.format("0x%04X", start);
					if (dao.insertErrorData(slaveId, addressIntToHexStr, 1) == 1) {
						log.info("CRC 오류 데이터 DB 저장 완료!");
					} else
						log.info("CRC 오류 데이터 DB 저장 실패!!");

					if (activePort != null && activePort.isOpen()) {
						activePort.flushIOBuffers();
					}
					// 각 패킷 확인시에 0.5초 지나면 다음 패킷으로 전송
					sendNextPacket();
				}
			}, TIMEOUT_MS, TimeUnit.MILLISECONDS);

			outputStream.write(bytes);
			outputStream.flush();
		}
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

					byte[] newData = new byte[size];
					int numRead = activePort.readBytes(newData, newData.length);
					if (numRead > 0) {
						processHex(newData, numRead);
					}

				}
			}
		});
	}

	private int getExpectedLength() {
		// Address, function, byteCount까진 받자
		// byteCount = 0x0002로 고정
		if (hexBufferIndex < 3)
			return -1;

		int functionCode = hexReadBuffer[1] & 0xFF;
		switch (functionCode) {
		case 0x04:
			int byteCount = hexReadBuffer[2] & 0xFF;
			return 3 + byteCount + 2;
		// 쓰기 기능은 추후 로직 필요
		case 0x06:
			return 8;
		default:
			return -1;
		}
	}

	private void processHex(byte[] incomingData, int numRead) {
		String hex = ProtocolConverter.convertBytesToHex(incomingData, numRead);
		log.info("processHex 원시 데이터(BytesToHex) : {}", hex);

		// 패킷 완벽 조립되었는지 (CRC 확인)
		boolean isPacketComplete = false;

		for (int i = 0; i < numRead; i++) {
			if (hexBufferIndex >= hexReadBuffer.length) {
				hexBufferIndex = 0;
			}
			hexReadBuffer[hexBufferIndex++] = incomingData[i];

			int expectedLength = getExpectedLength();
			if (expectedLength == -1) {
				continue;
			}

			if (hexBufferIndex >= expectedLength) {
				int dataLength = expectedLength - 2;

				int receivedCRC = (hexReadBuffer[expectedLength - 2] & 0xFF)
						| ((hexReadBuffer[expectedLength - 1] & 0xFF) << 8);
				int calculatedCRC = ProtocolConverter.calculateCRC16(hexReadBuffer, dataLength);
				int slaveId = hexReadBuffer[0] & 0xFF;
				int startAddress = this.currentRequestAddress;

				if (receivedCRC == calculatedCRC) {
					long elapsedTime = 0;
					if (waitingResponse)
						elapsedTime = System.currentTimeMillis() - requestTime;
					waitingResponse = false;

					if (timeoutTask != null) {
						timeoutTask.cancel(true);
					}

					String pureData = ProtocolConverter.convertBytesToHex(hexReadBuffer, dataLength);
					StringBuilder valueStr = new StringBuilder();

					int byteCount = hexReadBuffer[2] & 0xFF;
					// 검침값 저장
					long value = 0;

					// 검침 데이터 형식 저장 일단 초기화
					MeterInfo info = null;

					for (int i1 = 0; i1 < byteCount / 4; i1++) {
						int registerAddress = startAddress + (i1 * 2);
						info = meterMap.get(registerAddress);

						int baseIdx = 3 + (i1 * 4);
						value = ((long) (hexReadBuffer[baseIdx] & 0xFF))
								| ((long) (hexReadBuffer[baseIdx + 1] & 0xFF) << 8)
								| ((long) (hexReadBuffer[baseIdx + 2] & 0xFF) << 16)
								| ((long) (hexReadBuffer[baseIdx + 3] & 0xFF) << 24);
						if (info != null) {
							valueStr.append("[").append(info.getName()).append("] : ").append(value).append(" ")
									.append(info.getUnit()).append("\n");
						} else {
							valueStr.append(String.format("UNKNOWN [0x%04X] : %d\n", registerAddress, value));
						}
					}
					terminal.appendRxText(String.format("[CRC 일치 확인]\n[장치 주소]: %d \n%s[패킷 수신 소요 시간] : %dms\n", slaveId,
							valueStr, elapsedTime));
					terminal.appendRxText(
							"[수신한 원본 데이터 : " + pureData + "]\n\n");

					// 오라클에 저장
					log.info("=== DB 저장 로직 진입 ===");
					String addressIntToHexStr = String.format("0x%04X", startAddress);
					log.info("저장할 이름 : {}", info.getName());

					MeterReading dto = new MeterReading(0, slaveId, addressIntToHexStr, info.getName(), value,
							"SYSDATE", info.getUnit());
					if (dao.insert(dto) == 1) {
						log.info("검침 데이터 정상 저장!!");
					} else
						log.error("검침 데이터 저장 간에 오류 발생");

					hexBufferIndex = 0;
					if (activePort != null && activePort.isOpen()) {
						activePort.flushIOBuffers();
					}

					isPacketComplete = true;
					break;
				} else {
					// =============== CRC 오류 예외처리 ===============
					String errorData = ProtocolConverter.convertBytesToHex(hexReadBuffer, expectedLength);
					terminal.appendRxText("[Modbus CRC 오류 발생!] -> " + errorData + "\n");

					String addressIntToHexStr = String.format("0x%04X", startAddress);

//					MeterInfo errorInfo = meterMap.get(startAddress);
//					String errorItemName = (errorInfo != null) ? errorInfo.getName() : "UNKNOWN";

					log.error("=== CRC 에러 발생: DB 저장 진입 ===");
//					log.error("에러 장치: {}, 에러 주소: {}, 에러 항목: {}", slaveId, addressIntToHexStr, errorItemName);

					// 장치 주소, 오류난 주소값 (주소값 역시 오류 발생 가능!!), ERROR_CODE = 2
					if (dao.insertErrorData(slaveId, addressIntToHexStr, 2) == 1) {
						log.info("CRC 오류 데이터 DB 저장 완료!");
					} else
						log.info("CRC 오류 데이터 DB 저장 실패!!");
					if (expectedLength > 1) {
						System.arraycopy(hexReadBuffer, 1, hexReadBuffer, 0, expectedLength - 1);
						hexBufferIndex = expectedLength - 1;
					} else {
						hexBufferIndex = 0;
					}
				}
			}
		}

		// RS-485의 반이중(Half-Duplex) 특성 방지
		// - 장비가 컴퓨터한테 데이터 다보내고 내부적으로 쉬는 시간 갖도록
		// - 자바가 응답 받자마자 요청 데이터 발송하면 데이터 유실 발생 가능
		if (isPacketComplete) {
			// 다음 명령 수행할때 까지 딜레이 0.1초 부여
			timeoutScheduler.schedule(this::sendNextPacket, 100, TimeUnit.MILLISECONDS);
		}
	}

	// dao 데이터 넘겨줌
	public List<MeterReading> getMeterHistory(int deviceId, String addressMap, int dateSelected) {
		return dao.getMetersByAddress(deviceId, addressMap, dateSelected);
	}
}