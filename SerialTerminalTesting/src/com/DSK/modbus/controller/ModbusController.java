package com.DSK.modbus.controller;

import javax.swing.JOptionPane;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.modbus.model.LogDto;
import com.DSK.modbus.model.constant.ConnMode;
import com.DSK.modbus.service.ModbusManager;
import com.DSK.modbus.service.RawHexManager;
import com.DSK.modbus.service.SerialConnectionManager;
//import com.DSK.modbus.service.TcpConnectionManager; TODO
import com.DSK.modbus.view.ModbusTerminal;
import com.fazecast.jSerialComm.SerialPort;

/**
 * [ModbusController]
 * 역할: 연결 방식(Serial/TCP)을 추상화하여 UI와 통신 레이어를 중개하는 컨트롤러
 * 주요 기능:
 * - 기능 1: 시리얼 연결/해제 — 통신 규격 검증 후 SerialConnectionManager에 위임,
 *           성공 시 ConnMode.SERIAL 전환 및 연결 정보 시스템 로그 출력
 * - 기능 2: TCP 연결 예비 구조 — 현재 스텁(항상 실패 반환), 추후 TcpConnectionManager 위임 예정
 * - 기능 3: 통신 설정 전파 (applySetting) — Timeout/TxDelay를 ModbusManager와
 *           SerialConnectionManager 양쪽에 일괄 반영 (설정 불일치 방지)
 * - 기능 4: Raw HEX 수동 송신 중개 — 연결 상태/입력값 검증 후 RawHexManager에 위임
 *
 * 상태 관리: currentMode(NONE/SERIAL/TCP)로 활성 연결 모드를 단일 지점에서 추적
 */
public class ModbusController {
	private static final Logger log = LoggerFactory.getLogger(ModbusController.class); // ✅ 클래스명 매칭 수정

	private ConnMode currentMode = ConnMode.NONE;

	private final SerialConnectionManager connectionManager;
	//	private final TcpConnectionManager tcpManager; // 🌟 추후 추가할 멤버 변수
	private final ModbusManager modbusManager;
	private final RawHexManager rawHexManager;
	private final ModbusTerminal terminal;

	private String connectedPortOrIp = null; // ✅ 변수명 범용적으로 변경 (Port 또는 IP 저장)

	public ModbusController(ModbusTerminal terminal, ModbusManager modbusManager) {
		this.modbusManager = modbusManager;
		this.terminal = terminal;
		this.connectionManager = new SerialConnectionManager(terminal);
		// this.tcpConnectionManager = new TcpConnectionManager(terminal); // 🌟 추후 초기화
		this.rawHexManager = new RawHexManager(terminal, this.connectionManager);
	}

	// =========================================================================
	// [연결 및 제어 설정]
	// =========================================================================

	// 1. 시리얼 연결
	public boolean connectSerial(String port, int baud, int dataBits, int stopBits, int parity) {
		boolean valid = connectionManager.validateCommunicationSpec(baud, dataBits, stopBits, parity);
		if (!valid) {
			terminal.appendSystemLog(new LogDto("ERROR", "통신 규격 오류"));
			return false;
		}

		boolean success = connectionManager.connect(port, baud, dataBits, stopBits, parity, modbusManager,
				rawHexManager);

		if (success) {
			this.currentMode = ConnMode.SERIAL; // 🌟 시리얼 모드 활성화
			this.connectedPortOrIp = port;

			String parityStr = switch (parity) {
			case SerialPort.NO_PARITY -> "None";
			case SerialPort.ODD_PARITY -> "Odd";
			case SerialPort.EVEN_PARITY -> "Even";
			case SerialPort.MARK_PARITY -> "Mark";
			case SerialPort.SPACE_PARITY -> "Space";
			default -> "Unknown";
			};

			StringBuilder logMsg = new StringBuilder();
			logMsg.append(String.format("\n시리얼 포트 연결 (%s)\n", port));
			logMsg.append(String.format("   ├─ Baud Rate  : %d\n", baud));
			logMsg.append(String.format("   ├─ Data Bits  : %d\n", dataBits));
			logMsg.append(String.format("   ├─ Stop Bits  : %d\n", stopBits));
			logMsg.append(String.format("   └─ Parity Bit : %s\n", parityStr));
			terminal.appendSystemLog(new LogDto("SYSTEM", logMsg.toString()));
		} else {
			terminal.appendSystemLog(new LogDto("ERROR", "포트 연결 실패"));
		}
		return success;
	}

	// 2. 🌟 TCP/IP 연결 예비 로직 완성
	public boolean connectTcp(String ip, int tcpPort) {
		log.info("Modbus TCP 연결 시도: {}:{}", ip, tcpPort);

		// [예시 구조] 추후 구현할 tcpConnectionManager에 연결을 위임합니다.
		// boolean success = tcpConnectionManager.connect(ip, tcpPort, modbusManager, rawHexManager);
		boolean success = false; // 빌드 유지를 위한 가짜 값 (추후 실제 연결 결과 대입)

		if (success) {
			this.currentMode = ConnMode.TCP; // 🌟 TCP 모드 활성화
			this.connectedPortOrIp = String.format("%s:%d", ip, tcpPort);

			StringBuilder logMsg = new StringBuilder();
			logMsg.append(String.format("\nModbus TCP/IP 연결 성공 (%s:%d)\n", ip, tcpPort));
			terminal.appendSystemLog(new LogDto("SYSTEM", logMsg.toString()));
		} else {
			terminal.appendSystemLog(new LogDto("ERROR", String.format("TCP/IP 연결 실패 (%s:%d)", ip, tcpPort)));
		}

		// 현재는 테스트용으로 UI 상태 변화를 보기 위해 true를 리턴하도록 해두셔도 됩니다.
		return success;
	}

	// 3. 연결 해제 통합 제어
	public void disconnect() {
		if (currentMode == ConnMode.SERIAL) {
			connectionManager.disconnect();
		} else if (currentMode == ConnMode.TCP) {
			// tcpConnectionManager.disconnect(); // 🌟 추후 TCP 소켓 닫기 호출
		}

		terminal.appendSystemLog(new LogDto("SYSTEM", String.format("[%s] 연결 해제 완료\n", this.connectedPortOrIp)));
		this.currentMode = ConnMode.NONE; // 🌟 상태 초기화
		this.connectedPortOrIp = null;
	}

	// 4. 상태 확인 통합
	public boolean isConnected() {
		return switch (currentMode) {
		case SERIAL -> connectionManager.isOpen();
		case TCP -> false; // 추후: tcpConnectionManager.isConnected() 로 대체
		case NONE -> false;
		};
	}

	public void applySetting(int timeout, int endian, int txDelay, int slaveId, int dataType) {
		StringBuilder communicationControl = new StringBuilder();
		communicationControl.append("\n통신 제어 환경 설정 변경 완료\n");
		communicationControl.append(String.format("   ├─ Timeout  : %d ms\n", timeout));
		communicationControl.append(String.format("   └─ Tx Delay : %d ms\n", txDelay));

		terminal.appendSystemLog(new LogDto("SYSTEM", communicationControl.toString()));

		modbusManager.setTimeoutMs(timeout);
		modbusManager.setTxDelayMs(txDelay);

		// 시리얼 관련 세팅 외에 필요하다면 TCP용 세팅 분기 필요
		if (currentMode == ConnMode.SERIAL) {
			connectionManager.serialCommunicationSetting(timeout, endian, txDelay, slaveId, dataType);
		}
	}

	// =========================================================================
	// [RawHex Manager 호출]
	// =========================================================================
	public void sendRawHex(String hex, boolean autoCrc) {
		try {
			if (!isConnected()) { // ✅ 통합된 isConnected() 사용
				JOptionPane.showMessageDialog(this.terminal, "통신 미연결 상태입니다.\n연결 상태를 확인하세요.");
				return;
			}
			if (hex.isEmpty()) {
				JOptionPane.showMessageDialog(this.terminal, "송신할 HEX 문자열을 채워주십시오.");
				return;
			}

			// 🌟 중요: Modbus RTU는 끝에 CRC 2바이트가 붙지만, Modbus TCP는 CRC를 쓰지 않고 앞에 MBAP 헤더가 붙습니다.
			// 따라서 TCP 모드일 때는 autoCrc 옵션을 무시하거나 TCP 전용 전송 로직으로 분기해야 합니다.
			if (currentMode == ConnMode.SERIAL) {
				rawHexManager.sendRawHex(hex, autoCrc);
			} else if (currentMode == ConnMode.TCP) {
				// tcpRawHexManager.sendRawHex(hex); // 추후 구현
			}

		} catch (Exception e) {
			log.error("Raw Hex 송신 과정에서 예외 발생", e);
			JOptionPane.showMessageDialog(this.terminal, "Raw Hex 송신 과정에서 에러 발생");
		}
	}
}