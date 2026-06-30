package com.DSK.serial.core;

import java.io.OutputStream;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.serial.constant.DataType;
import com.DSK.serial.constant.EndianType;
import com.DSK.serial.manager.ModbusManager;
import com.DSK.serial.manager.RawHexManager;
import com.DSK.ui.modbus.ModbusTerminal;
import com.fazecast.jSerialComm.SerialPort;

public class SerialConnectionManager {

	private static final Logger log = LoggerFactory.getLogger(SerialConnectionManager.class);

	private SerialPort serialPort;
	private OutputStream outputStream;
	private boolean isConnected = false;
	private String connectedPort;

	private int TIMEOUT_MS = 500;
	private int txDelayMs = 100;

	private EndianType defaultEndianType = EndianType.LITTLE_ENDIAN;
	private DataType defaultDataType = DataType.INT32;

	// ✅ [수정] "Slave Id 1로 고정" 의도는 폐기되었다. 이 필드는 더 이상 통신을
	// 제한하지 않는다 — 실제 검침은 행(MeterRowDto)마다 다른 Slave ID 로 자유롭게
	// 동작하며, 그 라우팅은 ModbusManager → PacketReceiver.setExpectedSlaveId() 로
	// 매 요청마다 갱신된다. 이 필드는 serialCommunicationSetting() 으로 외부에서
	// 설정 가능한 값을 그대로 보관만 할 뿐 어디서도 검증에 쓰이지 않는, 사실상
	// 레거시 저장용 필드다. 다른 곳에서 참조하지 않는다면 제거를 고려해도 된다.
	private int comSlaveId = 1;

	private final ModbusTerminal terminal;

	public SerialConnectionManager(ModbusTerminal terminal) {
		this.terminal = terminal;
	}

	// ==================== 프로토콜 규격 맞는지 확인 ====================
	public boolean validateCommunicationSpec(int baudRate, int dataBits, int stopBits, int parity) {
		boolean validBaud = Arrays.asList(9600, 19200, 38400, 57600, 115200).contains(baudRate);
		boolean validData = (dataBits == 7 || dataBits == 8);
		boolean validStop = (stopBits == SerialPort.ONE_STOP_BIT || stopBits == SerialPort.TWO_STOP_BITS);
		boolean validParity = (parity >= 0 && parity <= 4);
		return validBaud && validData && validStop && validParity;
	}

	// ==================== 연결 ====================

	/**
	 * 기존 호출부 호환용 오버로드. RawHexManager 없이 호출하면 Raw HEX 테스트
	 * 기능은 비활성 상태로 남지만(이전과 동일한 동작), Modbus 검침의 멀티 Slave ID
	 * 라우팅은 정상 동작한다 — 그 부분은 modbusManager 만으로 충분하기 때문.
	 */
	public boolean connect(String portName, int baud, int dataBits, int stopBits, int parity,
			ModbusManager modbusManager) {
		return connect(portName, baud, dataBits, stopBits, parity, modbusManager, null);
	}

	/**
	 * ✅ [수정] RawHexManager 를 함께 받아 PacketReceiver 와 양방향으로 엮는다.
	 *
	 * 여기서 두 가지를 반드시 같이 처리해야 한다:
	 * 1) modbusManager.setPacketReceiver(receiver) — 이게 빠지면 PendingRequest
	 *    마다 다른 Slave ID 로 검침해도 PacketReceiver 가 그 변경을 전혀 모른 채
	 *    예전 Slave ID 응답만 기다리게 된다. 멀티 Slave 검침이 실제로 동작하려면
	 *    반드시 필요한 연결이다.
	 * 2) rawHexManager 가 null 이 아니면 receiver.setRawHexManager() /
	 *    rawHexManager.setPacketReceiver() / rawHexManager.bind() 를 전부 호출.
	 *    이 세 줄이 빠지면 Raw HEX 송신 시 "바인딩되지 않았습니다" 예외가 난다.
	 */
	public boolean connect(String portName, int baud, int dataBits, int stopBits, int parity,
			ModbusManager modbusManager, RawHexManager rawHexManager) {
		System.out.println("➡️ [디버그] 1. connect 메서드 진입 성공! 포트명: " + portName);
		try {
			serialPort = SerialPort.getCommPort(portName);
			serialPort.setBaudRate(baud);
			serialPort.setNumDataBits(dataBits);
			serialPort.setNumStopBits(stopBits);
			serialPort.setParity(parity);
			serialPort.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);

			System.out.println("➡️ [디버그] 2. 포트 설정 완료. 이제 openPort() 시도합니다...");

			if (!serialPort.openPort()) {
				System.err.println("❌ [디버그] 3-실패. openPort()가 false를 반환했습니다! 포트가 이미 열려있거나 존재하지 않습니다.");
				return false;
			}

			System.out.println("➡️ [디버그] 4-성공. openPort() 성공! 스트림 연결합니다.");
			outputStream = serialPort.getOutputStream();
			isConnected = true;
			connectedPort = portName;

			modbusManager.initializeCommunicationTunnel(this.serialPort, this.outputStream, portName);

			PacketReceiver receiver = new PacketReceiver(this, this.terminal, modbusManager);
			receiver.attach(serialPort);

			// ✅ [신규] 멀티 Slave ID 검침이 실제로 동작하려면 반드시 필요한 연결.
			modbusManager.setPacketReceiver(receiver);

			// ✅ [신규] Raw HEX 테스트 기능 와이어링 (전달받은 경우에만).
			if (rawHexManager != null) {
				receiver.setRawHexManager(rawHexManager);
				rawHexManager.setPacketReceiver(receiver);
				rawHexManager.bind();
				log.info("RawHexManager 바인딩 및 PacketReceiver 연결 완료");
			}

			log.info("포트 연결 및 패킷 리시버 등록 완료: {}", portName);
			return true;
		} catch (Exception e) {
			System.err.println("❌ [디버그] 예외 발생으로 catch 블록 탈출!");
			log.error("포트 연결 실패: {}", portName, e);
			disconnect();
			return false;
		}
	}

	// ==================== disconnect ====================
	public void disconnect() {
		isConnected = false;
		try {
			if (serialPort != null && serialPort.isOpen()) {
				serialPort.closePort();
				log.info("포트가 안전하게 닫혔습니다: {}", connectedPort);
			}
		} catch (Exception e) {
			log.error("포트 해제 오류", e);
		}
	}

	// ==================== 상태 ====================
	public boolean isOpen() {
		return isConnected && serialPort != null && serialPort.isOpen();
	}

	public SerialPort getSerialPort() {
		return serialPort;
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}

	// ==================== 통신 설정 ====================
	public void serialCommunicationSetting(int timeOut, int endian, int txDelay, int slaveId, int dataType) {

		this.TIMEOUT_MS = timeOut;
		this.txDelayMs = txDelay;
		this.comSlaveId = slaveId;

		if (endian >= 0 && endian < EndianType.values().length) {
			this.defaultEndianType = EndianType.values()[endian];
		} else {
			this.defaultEndianType = EndianType.LITTLE_ENDIAN;
		}

		if (dataType >= 0 && dataType < DataType.values().length) {
			this.defaultDataType = DataType.values()[dataType];
		} else {
			this.defaultDataType = DataType.INT32;
		}
	}

	// ==================== getter ====================

	public int getTimeout() {
		return TIMEOUT_MS;
	}

	public int getTxDelay() {
		return txDelayMs;
	}

	public EndianType getDefaultEndianType() {
		return defaultEndianType;
	}

	public DataType getDefaultDataType() {
		return defaultDataType;
	}

	public int getComSlaveId() {
		return comSlaveId;
	}
}