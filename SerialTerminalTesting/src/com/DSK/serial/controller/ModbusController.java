package com.DSK.serial.controller;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.model.dto.common.LogDto;
import com.DSK.serial.core.PacketReceiver;
import com.DSK.serial.core.SerialConnectionManager;
import com.DSK.serial.manager.ModbusManager;
import com.DSK.serial.manager.RawHexManager;
import com.DSK.ui.modbus.ModbusTerminal;
import com.fazecast.jSerialComm.SerialPort;

public class ModbusController {
	private static final Logger log = LoggerFactory.getLogger(ModbusTerminal.class);

	private final SerialConnectionManager connectionManager;
	private final ModbusManager modbusManager;
	private final RawHexManager rawHexManager;
	private final ModbusTerminal terminal;
	private PacketReceiver packetReciever;

	public ModbusController(ModbusTerminal terminal, ModbusManager modbusManager) {
		this.modbusManager = modbusManager;
		this.terminal = terminal;
		this.connectionManager = new SerialConnectionManager(terminal);
		this.rawHexManager = new RawHexManager(terminal, this.connectionManager);
		this.packetReciever = new PacketReceiver(connectionManager, terminal, this.modbusManager);
	}

	// =========================================================================
	// [연결 및 제어 설정]
	// =========================================================================
	public boolean connectSerial(String port, int baud, int dataBits, int stopBits, int parity) {

		boolean valid = connectionManager.validateCommunicationSpec(baud, dataBits, stopBits, parity);

		if (!valid) {
			terminal.appendSystemLog(new LogDto("ERROR", "통신 규격 오류"));
			return false;
		}

		boolean success = connectionManager.connect(port, baud, dataBits, stopBits, parity, modbusManager,
				rawHexManager);

		if (success) {
			SerialPort serialPort = connectionManager.getSerialPort();
			packetReciever.attach(serialPort);

			String parityStr = switch (parity) {
			case SerialPort.NO_PARITY -> "None";
			case SerialPort.ODD_PARITY -> "Odd";
			case SerialPort.EVEN_PARITY -> "Even";
			case SerialPort.MARK_PARITY -> "Mark";
			case SerialPort.SPACE_PARITY -> "Space";
			default -> "Unknown";
			};

			StringBuilder logMsg = new StringBuilder();
			logMsg.append(String.format("\n시리얼 포트 연결 성공 (%s)\n", port));
			logMsg.append(String.format("   • 보드 레이트 : %d\n", baud));
			logMsg.append(String.format("   • 데이터 비트 : %d\n", dataBits));
			logMsg.append(String.format("   • 스톱 비트 : %d\n", stopBits));
			logMsg.append(String.format("   • 패리티 : %s\n", parityStr));

			terminal.appendSystemLog(new LogDto("SYSTEM", logMsg.toString()));

		} else {
			terminal.appendSystemLog(new LogDto("ERROR", "포트 연결 실패"));
		}

		return success;
	}

	public void disconnect() {
		connectionManager.disconnect();

		terminal.appendSystemLog(new LogDto("SYSTEM", "포트 연결 해제!"));
	}

	public void applySetting(int timeout, int endian, int txDelay, int slaveId, int dataType) {
		StringBuilder communicationControl = new StringBuilder();
		communicationControl.append("\n통신 제어 환경 설정 변경 완료\n");
		communicationControl.append(String.format("   • 타임아웃   (Timeout)         : %d ms\n", timeout));
		communicationControl.append(String.format("   • 송신 지연  (Tx Delay)       : %d ms\n", txDelay));

		LogDto systemMessage = new LogDto("SYSTEM", (communicationControl.toString()));
		terminal.appendSystemLog(systemMessage);
		connectionManager.serialCommunicationSetting(timeout, endian, txDelay, slaveId, dataType);
	}

	public boolean isConnected() {
		return connectionManager.isOpen();
	}

	// =========================================================================
	// [RawHex Manager 호출]
	// =========================================================================
	public void sendRawHex(String hex, boolean autoCrc) {
		try {
			if (!connectionManager.isOpen()) {
				JOptionPane.showMessageDialog(this.terminal, "활성화된 시리얼 포트 세션이 없습니다.");
				return;
			}
			if (hex.isEmpty()) {
				JOptionPane.showMessageDialog(this.terminal, "송신할 HEX 문자열을 채워주십시오.");
				return;
			}
			rawHexManager.sendRawHex(hex, autoCrc);
		} catch (Exception e) {
			e.printStackTrace();
			log.error("{}", e);
			JOptionPane.showMessageDialog(this.terminal, "Raw Hex 송신 과정에서 에러 발생");
			return;
		}
	}
}