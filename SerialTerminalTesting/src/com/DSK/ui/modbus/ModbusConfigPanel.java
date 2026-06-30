package com.DSK.ui.modbus;

import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.TitledBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.serial.controller.ModbusController;
import com.fazecast.jSerialComm.SerialPort;

public class ModbusConfigPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(ModbusConfigPanel.class);

	private final ModbusTerminal terminal;

	// --- [통신 타입 선택] ---
	private JRadioButton rdoSerial, rdoTcp;
	private CardLayout leftCardLayout;
	private JPanel leftCardPanel;

	// --- [기존 물리 포트 컴포넌트] ---
	private JComboBox<String> portCombo, baudCombo, parityCombo, stopCombo, dataBitsCombo;

	// --- [신규 TCP/IP 컴포넌트] ---
	private JTextField ipField, tcpPortField;

	// 하드웨어 연결 및 프로토콜 제어 버튼
	private JButton connectBtn;
	private JButton connectProtocolBtn;

	// --- [모드버스 세부 설정 컴포넌트] ---
	private JTextField timeoutField, txDelayField, slaveIdField;
	private JComboBox<String> endianCombo, dataTypeCombo;

	private ModbusController controller;

	public ModbusConfigPanel(ModbusTerminal terminal, ModbusController modbusController) {
		this.terminal = terminal;
		this.controller = modbusController;

		// 좌/우 패널 배치를 위해 1행 2열 그리드 레이아웃 사용
		setLayout(new GridLayout(1, 2, 15, 0));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		initConfigComponent();
	}

	private void initConfigComponent() {
		Font titleFont = new Font("맑은 고딕", Font.BOLD, 12);

		// =========================================================================
		// 1. 좌측 패널: 하드웨어 연결 설정 (시리얼 / TCP 카드 레이아웃 적용)
		// =========================================================================
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setBackground(Color.WHITE);
		leftPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "인터페이스 설정",
				TitledBorder.LEFT, TitledBorder.TOP, titleFont));

		// [1-A] 상단 통신 모드 선택기
		JPanel modePanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
		modePanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
		// 🎨 [배경색 변경] 라디오 버튼 패널 배경 흰색 지정
		modePanel.setBackground(Color.WHITE);

		rdoSerial = new JRadioButton("Serial (RTU)", true);
		rdoTcp = new JRadioButton("TCP/IP");
		rdoSerial.setBackground(Color.WHITE);
		rdoTcp.setBackground(Color.WHITE);

		ButtonGroup modeGroup = new ButtonGroup();
		modeGroup.add(rdoSerial);
		modeGroup.add(rdoTcp);

		modePanel.add(rdoSerial);
		modePanel.add(rdoTcp);
		leftPanel.add(modePanel);
		leftPanel.add(Box.createVerticalStrut(5));

		// [1-B] 카드 레이아웃 메인 본체 생성
		leftCardLayout = new CardLayout();
		leftCardPanel = new JPanel(leftCardLayout);
		leftCardPanel.setBackground(Color.WHITE);

		// ---------------- [시리얼 설정 카드 화면] ----------------
		JPanel serialCard = new JPanel();
		serialCard.setLayout(new BoxLayout(serialCard, BoxLayout.Y_AXIS));
		serialCard.setMaximumSize(new Dimension(Short.MAX_VALUE, 145));
		// 🎨 [배경색 변경] 시리얼 카드 배경 흰색 지정
		serialCard.setBackground(Color.WHITE);

		portCombo = new JComboBox<>();
		refreshAvailablePorts();
		baudCombo = new JComboBox<>(new String[] { "9600", "19200", "38400", "115200" });
		dataBitsCombo = new JComboBox<>(new String[] { "8", "7" });
		dataBitsCombo.setSelectedItem("8");
		parityCombo = new JComboBox<>(new String[] { "NO", "ODD", "EVEN", "MARK", "SPACE" });
		stopCombo = new JComboBox<>(new String[] { "1", "2" });

		serialCard.add(createFormRow("Port:", portCombo));
		serialCard.add(createFormRow("Baud Rate:", baudCombo));
		serialCard.add(createFormRow("Data Bits:", dataBitsCombo));
		serialCard.add(createFormRow("Parity Bit:", parityCombo));
		serialCard.add(createFormRow("Stop Bits:", stopCombo));
		serialCard.add(Box.createVerticalGlue());

		// ---------------- [TCP/IP 설정 카드 화면] ----------------
		JPanel tcpCard = new JPanel();
		tcpCard.setLayout(new BoxLayout(tcpCard, BoxLayout.Y_AXIS));
		tcpCard.setMaximumSize(new Dimension(Short.MAX_VALUE, 60));
		// 🎨 [배경색 변경] TCP 카드 배경 흰색 지정
		tcpCard.setBackground(Color.WHITE);

		ipField = new JTextField("192.168.0.100");
		tcpPortField = new JTextField("502");

		tcpCard.add(createFormRow("IP 주소:", ipField));
		tcpCard.add(createFormRow("포트 번호:", tcpPortField));
		tcpCard.add(Box.createVerticalGlue());

		leftCardPanel.add(serialCard, "SERIAL");
		leftCardPanel.add(tcpCard, "TCP");

		leftCardPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 150));
		leftPanel.add(leftCardPanel);
		leftPanel.add(Box.createVerticalGlue());

		// 하단 공통 포트 연결 버튼 구역
		JPanel leftBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		leftBtnPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
		// 🎨 [배경색 변경] 버튼 포장 패널 배경 흰색 지정
		leftBtnPanel.setBackground(Color.WHITE);

		connectBtn = new JButton("연결");
		connectBtn.setBackground(Color.WHITE); // 버튼 자체 톤 매칭
		leftBtnPanel.add(connectBtn);

		leftPanel.add(leftBtnPanel);
		leftPanel.add(Box.createVerticalStrut(5));

		// =========================================================================
		// 2. 우측 패널: Modbus RTU 프로토콜 스펙 제어 (타이트 정렬 개편)
		// =========================================================================
		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setBackground(Color.WHITE);

		rightPanel
				.setBorder(
						BorderFactory.createCompoundBorder(
								BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "제어 설정",
										TitledBorder.LEFT, TitledBorder.TOP, titleFont),
								BorderFactory.createEmptyBorder(2, 5, 2, 15)));

		rightPanel.setPreferredSize(new Dimension(240, 150));
		rightPanel.setMinimumSize(new Dimension(240, 150));
		rightPanel.setMaximumSize(new Dimension(240, 150));

		Dimension fieldSize = new Dimension(135, 22);

		timeoutField = new JTextField("500");
		txDelayField = new JTextField("100");
		slaveIdField = new JTextField("1");

		for (JTextField field : new JTextField[] { timeoutField, txDelayField, slaveIdField }) {
			field.setPreferredSize(fieldSize);
		}

		endianCombo = new JComboBox<>(
				new String[] { "LITTLE_ENDIAN", "BIG_ENDIAN", "LITTLE_ENDIAN_BYTE_SWAP", "BIG_ENDIAN_BYTE_SWAP" });
		dataTypeCombo = new JComboBox<>(new String[] { "UINT16", "INT16", "UINT32", "INT32", "FLOAT32", "DOUBLE64" });

		for (JComboBox<?> combo : new JComboBox[] { endianCombo, dataTypeCombo }) {
			combo.setPreferredSize(fieldSize);
			combo.setBackground(Color.WHITE);
			combo.setFont(new Font("Consolas", Font.PLAIN, 11)); // 가독성 좋은 고정폭 폰트 유지
		}

		rightPanel.add(createFormRow("Timeout(ms):", timeoutField));
		rightPanel.add(createFormRow("TX Delay(ms)", txDelayField));
		rightPanel.add(Box.createVerticalGlue());

		// 버튼 영역도 왼쪽이나 중앙으로 이쁘게 정렬
		JPanel rightBtnPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 45, 0));
		rightBtnPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
		rightBtnPanel.setBackground(Color.WHITE);

		connectProtocolBtn = new JButton("통신 설정");
		connectProtocolBtn.setBackground(Color.WHITE);
		connectProtocolBtn.setPreferredSize(new Dimension(100, 24)); // 버튼 크기 명시
		rightBtnPanel.add(connectProtocolBtn);

		rightPanel.add(rightBtnPanel);
		rightPanel.add(Box.createVerticalStrut(5));

		// --- [이벤트 라디오/버튼 바인딩] ---
		rdoSerial.addActionListener(e -> leftCardLayout.show(leftCardPanel, "SERIAL"));
		rdoTcp.addActionListener(e -> leftCardLayout.show(leftCardPanel, "TCP"));

		connectBtn.addActionListener(e -> toggleConnection());
		connectProtocolBtn.addActionListener(e -> toggleCommunicationSetting());

		// 메인 패널 조립
		add(leftPanel);
		add(rightPanel);
	}

	// =========================================================================
	// 통신 인터페이스 설정 연결
	// =========================================================================
	private void toggleCommunicationSetting() {
		if (!controller.isConnected()) {
			JOptionPane.showMessageDialog(this, "포트 연결이 해제된 상태입니다.");
			return;
		}
		int timeOut = Integer.parseInt(timeoutField.getText());
		int endianType = (endianCombo.getSelectedIndex());
		int dataType = (dataTypeCombo.getSelectedIndex());
		int txDelay = Integer.parseInt(txDelayField.getText());
		int slaveId = Integer.parseInt(slaveIdField.getText());

		controller.applySetting(timeOut, endianType, txDelay, slaveId, dataType);
	}

	public void refreshAvailablePorts() {
		SerialPort[] ports = SerialPort.getCommPorts();
		portCombo.removeAllItems();
		for (SerialPort port : ports) {
			portCombo.addItem(port.getSystemPortName());
		}
		if (terminal != null) {
			terminal.resetAllMeterTable();
		}
	}

	private void toggleConnection() {
		// 1. 이미 연결된 상태에서 버튼을 누른 경우 (해제 로직)
		if (controller.isConnected() || connectBtn.getText().equals("연결 해제")) {

			if (rdoSerial.isSelected()) {
				controller.disconnect(); // 기존 시리얼 포트 닫기
			} else {
				// TODO: serialManager.disconnectTcp(); 처럼 TCP 소켓 닫는 메서드 연동 필요
				log.info("TCP/IP 연결 해제 요청");
			}

			// 공통: 연결 상태 스위칭 해제 및 컴포넌트 활성화
			updateConnectionState(false);
			return;
		}

		// 2. 연결되지 않은 상태에서 버튼을 누른 경우 (연결 로직)
		if (rdoSerial.isSelected()) {
			// --- [Serial 연결 구역] ---
			if (portCombo.getSelectedItem() == null) {
				JOptionPane.showMessageDialog(this, "연결할 COM 포트가 존재하지 않습니다.");
				return;
			}
			String portName = portCombo.getSelectedItem().toString();
			int baudRate = Integer.parseInt(baudCombo.getSelectedItem().toString());
			int dataBits = Integer.parseInt(dataBitsCombo.getSelectedItem().toString());
			int stopBits = (stopCombo.getSelectedIndex() == 1) ? SerialPort.TWO_STOP_BITS : SerialPort.ONE_STOP_BIT;
			int parity = parityCombo.getSelectedIndex();

			if (controller.connectSerial(portName, baudRate, dataBits, stopBits, parity)) {
				log.info("Serial Port connected: {}", portName);
				updateConnectionState(true);
			} else {
				JOptionPane.showMessageDialog(this, "포트 개방에 실패하였습니다.");
			}
		} else {
			// --- [TCP/IP 연결 구역] ---
			String ip = ipField.getText().trim();
			int tcpPort = Integer.parseInt(tcpPortField.getText().trim());

			// TODO: 매니저 싱크에 맞게 실제 소켓 연결 코드 매핑
			// boolean success = serialManager.connectingTcp(ip, tcpPort);
			log.info("TCP/IP 연결 요청: {}:{}", ip, tcpPort);

			// 가연결 상태 전환 (테스트용)
			updateConnectionState(true);
		}
	}

	private JPanel createFormRow(String labelText, JComponent component) {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setMaximumSize(new Dimension(Short.MAX_VALUE, 28));
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
		row.setBackground(Color.WHITE);

		// 1. 라벨 가로폭 살짝 좁혀서 우측 컴포넌트 영역 확보 (90px ➔ 80px)
		JLabel label = new JLabel(labelText);
		label.setPreferredSize(new Dimension(80, 25));
		label.setMinimumSize(new Dimension(80, 25));
		label.setMaximumSize(new Dimension(80, 25));
		label.setHorizontalAlignment(SwingConstants.RIGHT);
		label.setFont(new Font("맑은 고딕", Font.PLAIN, 11)); // 본문 스케일에 맞춤

		row.add(label);
		row.add(Box.createHorizontalStrut(8)); // 간격 살짝 최적화

		// 🔥 [핵심 교정] BoxLayout이 컴포넌트를 우측으로 무제한 늘리는 것을 원천 차단합니다.
		// component가 가진 PreferredSize(우리가 밖에서 설정한 105px 등)를 최대 크기로 락(Lock)을 겁니다.
		component.setMaximumSize(component.getPreferredSize());
		row.add(component);

		// 🎨 [마법의 한 줄] 컴포넌트가 왼쪽으로 바짝 붙고, 잔여 우측 공간은 빈 공백이 채우도록 밀어줍니다.
		row.add(Box.createHorizontalGlue());

		return row;
	}

	// =================== 시리얼/TCP 연결 상태에 따른 컴포넌트 락(Lock) 가드 ===================
	public void updateConnectionState(boolean connected) {
		connectBtn.setText(connected ? "연결 해제" : "연결");

		terminal.setSendButtonEnabled(connected);
		terminal.setConnected(connected);

		// 상단 모드 라디오 버튼 잠금
		rdoSerial.setEnabled(!connected);
		rdoTcp.setEnabled(!connected);

		// 시리얼 관련 컴포넌트 처리
		portCombo.setEnabled(!connected);
		baudCombo.setEnabled(!connected);
		dataBitsCombo.setEnabled(!connected);
		parityCombo.setEnabled(!connected);
		stopCombo.setEnabled(!connected);

		// TCP/IP 관련 컴포넌트 처리
		ipField.setEnabled(!connected);
		tcpPortField.setEnabled(!connected);
	}
}