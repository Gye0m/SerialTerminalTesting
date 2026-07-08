package com.DSK.modbus.service;

import java.io.OutputStream;
import java.util.Arrays;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.modbus.model.constant.DataType;
import com.DSK.modbus.model.constant.EndianType;
import com.fazecast.jSerialComm.SerialPort;

/**
 * 시리얼 포트 연결 생명주기 관리.
 *
 * 핵심 설계 결정:
 * - isConnected·serialPort·outputStream·connectedPort → volatile
 *   disconnect()는 jSerialComm 내부 스레드(DISCONNECTED 이벤트)에서,
 *   connect()·isOpen()은 EDT에서 호출된다. volatile 없으면 캐시 불일치.
 * - disconnect()에서 isConnected를 먼저 false로 설정 후 포트 닫기.
 *   타임아웃 핸들러가 isOpen() 확인 후 진입하는 타이밍 창을 최소화한다.
 * - serialCommunicationSetting()에서 ModbusManager에도 직접 전파.
 *   이전: SerialConnectionManager 내부만 갱신 → TIMEOUT_MS 불일치.
 */
public class SerialConnectionManager {

	private static final Logger log = LoggerFactory.getLogger(SerialConnectionManager.class);

	// ── 연결 상태 (volatile — 멀티스레드 가시성 필수) ─────────────────────────
	private volatile SerialPort serialPort;
	private volatile OutputStream outputStream;
	private volatile boolean isConnected = false;
	private volatile String connectedPort;

	// ── 통신 설정 (ModbusManager와 이중 보관) ────────────────────────────────
	private int TIMEOUT_MS = 500;
	private int txDelayMs = 100;
	private EndianType defaultEndianType = EndianType.LITTLE_ENDIAN;
	private DataType defaultDataType = DataType.INT32;

	// 레거시 저장용 — 실제 SlaveID 라우팅은 PacketReceiver.setExpectedSlaveId()로 처리
	private int comSlaveId = 1;

	private final ModbusEventListener listener;

	public SerialConnectionManager(ModbusEventListener listener) {
		this.listener = listener;
	}

	// =========================================================================
	// 프로토콜 스펙 검증
	// =========================================================================
	public boolean validateCommunicationSpec(int baudRate, int dataBits, int stopBits, int parity) {
		boolean validBaud = Arrays.asList(9600, 19200, 38400, 57600, 115200).contains(baudRate);
		boolean validData = (dataBits == 7 || dataBits == 8);
		boolean validStop = (stopBits == SerialPort.ONE_STOP_BIT || stopBits == SerialPort.TWO_STOP_BITS);
		boolean validParity = (parity >= 0 && parity <= 4);
		return validBaud && validData && validStop && validParity;
	}

	// =========================================================================
	// connect 오버로드 (RawHexManager 없는 단순 버전)
	// =========================================================================
	public boolean connect(String portName, int baud, int dataBits, int stopBits, int parity,
			ModbusManager modbusManager) {
		return connect(portName, baud, dataBits, stopBits, parity, modbusManager, null);
	}

	// =========================================================================
	// connect — 포트 열기 + ModbusManager 초기화 + PacketReceiver 와이어링
	// =========================================================================
	public boolean connect(String portName, int baud, int dataBits, int stopBits, int parity,
			ModbusManager modbusManager, RawHexManager rawHexManager) {

		if (isConnected) {
			log.warn("이미 연결된 상태에서 재연결 시도 – 기존 포트를 먼저 닫습니다: {}", connectedPort);
			disconnect();
		}

		log.info("connect() 진입 – 포트명: {}", portName);
		try {
			SerialPort port = SerialPort.getCommPort(portName);
			port.setBaudRate(baud);
			port.setNumDataBits(dataBits);
			port.setNumStopBits(stopBits);
			port.setParity(parity);
			port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 0, 0);

			if (!port.openPort()) {
				log.error("openPort() 실패 – 포트가 이미 열려있거나 존재하지 않습니다: {}", portName);
				return false;
			}

			OutputStream os = port.getOutputStream();

			// 필드 할당 — isConnected는 마지막에 (disconnect()와의 경쟁 방지)
			this.serialPort = port;
			this.outputStream = os;
			this.connectedPort = portName;
			this.isConnected = true; // volatile write — 다른 스레드에 즉각 가시

			// ModbusManager 초기화 (필드 할당 완료 후 수행)
			modbusManager.initializeCommunicationTunnel(port, os, portName);

			// PacketReceiver 생성 및 attach
			// attach()는 반드시 initializeCommunicationTunnel() 이후 — receiver가 먼저
			// 이벤트를 받으면 ModbusManager 미초기화 상태로 processHex()가 호출됨
			PacketReceiver receiver = new PacketReceiver(this, listener, modbusManager);
			receiver.attach(port);
			modbusManager.setPacketReceiver(receiver);

			if (rawHexManager != null) {
				receiver.setRawHexManager(rawHexManager);
				rawHexManager.setPacketReceiver(receiver);
				rawHexManager.bind();
				log.info("RawHexManager 바인딩 완료");
			}

			log.info("포트 연결 및 PacketReceiver 등록 완료: {}", portName);
			return true;

		} catch (Exception e) {
			log.error("포트 연결 실패: {}", portName, e);
			isConnected = false; // 예외 발생 시 명시적 false
			disconnect(); // 부분적으로 열린 자원 정리
			return false;
		}
	}

	// =========================================================================
	// disconnect — 포트 자원 해제
	// =========================================================================
	public void disconnect() {
		isConnected = false; // volatile write — 먼저 차단하여 isOpen() stale true 방지
		try {
			SerialPort portToClose = this.serialPort;
			if (portToClose != null && portToClose.isOpen()) {
				portToClose.closePort();
				log.info("포트가 안전하게 닫혔습니다: {}", connectedPort);
			}
		} catch (Exception e) {
			log.error("포트 해제 오류", e);
		} finally {
			// stale 참조 제거 — 이후 outputStream.write() 시도 시 NPE로 명확히 실패
			this.outputStream = null;
		}
	}

	// =========================================================================
	// 상태 조회
	// =========================================================================
	public boolean isOpen() {
		return isConnected && serialPort != null && serialPort.isOpen();
	}

	public SerialPort getSerialPort() {
		return serialPort;
	}

	public OutputStream getOutputStream() {
		return outputStream;
	}

	// =========================================================================
	// 통신 설정
	// =========================================================================

	/**
	 * 통신 설정을 갱신하고 ModbusManager에도 즉시 전파한다.
	 *
	 * [이전 문제] SerialConnectionManager 내부 필드만 갱신
	 *   → ModbusManager.TIMEOUT_MS, txDelayMs는 기본값 그대로
	 *   → UI에서 타임아웃 변경해도 실제 통신에 미반영
	 *
	 * @param modbusManager null이면 로컬 필드만 갱신 (하위 호환)
	 */
	public void serialCommunicationSetting(int timeOut, int endian, int txDelay, int slaveId, int dataType,
			ModbusManager modbusManager) {
		this.TIMEOUT_MS = timeOut;
		this.txDelayMs = txDelay;
		this.comSlaveId = slaveId;

		this.defaultEndianType = (endian >= 0 && endian < EndianType.values().length) ? EndianType.values()[endian]
				: EndianType.LITTLE_ENDIAN;

		this.defaultDataType = (dataType >= 0 && dataType < DataType.values().length) ? DataType.values()[dataType]
				: DataType.INT32;

		if (modbusManager != null) {
			modbusManager.setTimeoutMs(timeOut);
			modbusManager.setTxDelayMs(txDelay);
		}
	}

	/** 하위 호환 오버로드 — ModbusManager 전파 없이 로컬만 갱신 */
	public void serialCommunicationSetting(int timeOut, int endian, int txDelay, int slaveId, int dataType) {
		serialCommunicationSetting(timeOut, endian, txDelay, slaveId, dataType, null);
	}

	// =========================================================================
	// getter
	// =========================================================================
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