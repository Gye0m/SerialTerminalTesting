package com.DSK.serial.core;

import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import com.DSK.serial.manager.ModbusManager;
import com.DSK.serial.manager.RawHexManager;
import com.DSK.ui.modbus.ModbusTerminal;
import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;

public class PacketReceiver {

	private final SerialConnectionManager connectionManager;
	private final ModbusTerminal terminal;
	private final ModbusManager modbusManager;

	// Raw HEX 테스트 응답을 받기 위한 참조. 부가 기능이라 null 가드.
	private RawHexManager rawHexManager;

	private final ByteArrayOutputStream rxBuffer = new ByteArrayOutputStream();

	// ✅ [수정] 이전엔 1로 고정된 채 절대 바뀌지 않았다 — Slave ID 1이 아닌 장비와
	// 통신하면 응답이 전부 "잘못된 슬레이브"로 간주되어 버려지는 버그였다.
	// 송신 직전에 setExpectedSlaveId() 로 갱신해주는 구조로 바꾼다.
	//
	// volatile 인 이유: 이 필드는 시리얼 이벤트 스레드(serialEvent 콜백)에서 읽고,
	// 송신을 트리거하는 다른 스레드(EDT 또는 autoScheduler)에서 쓴다. volatile 없이는
	// 쓰기가 읽는 스레드에 즉시 보이지 않을 수 있다.
	private volatile int currentSlaveId = 1;

	public PacketReceiver(SerialConnectionManager connectionManager, ModbusTerminal terminal,
			ModbusManager modbusManager) {
		this.connectionManager = connectionManager;
		this.terminal = terminal;
		this.modbusManager = modbusManager;
	}

	/**
	 * Raw HEX 매니저 연결. 포트가 열리고 PacketReceiver 를 attach() 하는 시점에
	 * 한 번 호출해주면 된다. 호출하지 않아도 ModbusManager 기반 수동/자동 검침
	 * 동작에는 전혀 영향이 없다.
	 */
	public void setRawHexManager(RawHexManager rawHexManager) {
		this.rawHexManager = rawHexManager;
	}

	/**
	 * ✅ [신규] 다음에 들어올 응답이 어느 Slave ID 의 것인지 갱신한다.
	 * ModbusManager.sendBytes() / RawHexManager.sendRawHex() 양쪽 모두 패킷을
	 * 실제로 쓰기(outputStream.write) 직전에 이 메서드를 호출해야 한다.
	 * 패킷의 첫 바이트가 곧 목적지 Slave ID 이므로 호출부에서 별도 파라미터 없이
	 * packet[0] & 0xFF 값을 그대로 넘기면 된다.
	 */
	public void setExpectedSlaveId(int slaveId) {
		this.currentSlaveId = slaveId;
	}

	public void attach(SerialPort port) {
		port.addDataListener(new SerialPortDataListener() {
			@Override
			public int getListeningEvents() {
				return SerialPort.LISTENING_EVENT_DATA_AVAILABLE | SerialPort.LISTENING_EVENT_PORT_DISCONNECTED;
			}

			@Override
			public void serialEvent(SerialPortEvent event) {
				if (event.getEventType() == SerialPort.LISTENING_EVENT_PORT_DISCONNECTED) {
					connectionManager.disconnect();
					terminal.handlePhysicalDisconnect();
					return;
				}
				if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
					int size = port.bytesAvailable();
					if (size <= 0)
						return;
					byte[] data = new byte[size];
					int read = port.readBytes(data, size);
					if (read <= 0)
						return;
					synchronized (this) {
						rxBuffer.write(data, 0, read);
						while (true) {
							byte[] buf = rxBuffer.toByteArray();
							if (buf.length == 0)
								break;
							if ((buf[0] & 0xFF) != currentSlaveId) {
								rxBuffer.reset();
								if (buf.length > 1) {
									rxBuffer.write(buf, 1, buf.length - 1);
								}
								continue;
							}
							int expectedLength = modbusManager.getExpectedLength(buf);
							if (expectedLength == -1 || buf.length < expectedLength)
								break;
							byte[] packet = Arrays.copyOf(buf, expectedLength);

							// 완성된 패킷을 ModbusManager 뿐 아니라 RawHexManager 에도 전달.
							// 두 매니저 모두 자기 내부 waitingResponse 플래그로 스스로 게이트하므로,
							// "지금 기다리는 중이 아닌" 매니저는 조용히 무시한다.
							modbusManager.onModbusPacket(packet);
							if (rawHexManager != null) {
								rawHexManager.onReceive(packet);
							}

							rxBuffer.reset();
							if (buf.length > expectedLength) {
								rxBuffer.write(buf, expectedLength, buf.length - expectedLength);
							} else {
								break;
							}
						}
					}
				}
			}
		});
	}
}