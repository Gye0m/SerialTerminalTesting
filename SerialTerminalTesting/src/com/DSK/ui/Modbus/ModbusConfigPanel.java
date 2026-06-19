package com.DSK.ui.Modbus;

import com.DSK.serial.ModbusManager;
import com.fazecast.jSerialComm.SerialPort;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;

public class ModbusConfigPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(ModbusConfigPanel.class);

	private final ModbusTerminal terminal;
	private final ModbusManager serialManager;

	// --- [기존 물리 포트 컴포넌트] ---
	private JComboBox<String> portCombo, baudCombo, parityCombo, stopCombo, dataCombo;

	// 하드웨어 연결
	private JButton connectBtn;

	// 프로토콜 스펙 제어
	private JButton connectProtocolBtn;

	// --- [모드버스 세부 설정 컴포넌트] ---
	private JTextField scanRateField, timeoutField, txDelayField, slaveIdField;
	private JComboBox<String> endianCombo;

	public ModbusConfigPanel(ModbusTerminal terminal, ModbusManager serialManager) {
		this.terminal = terminal;
		this.serialManager = serialManager;

		// 좌/우 패널을 50:50으로 배치하기 위해 1행 2열 그리드 레이아웃 사용
		setLayout(new GridLayout(1, 2, 15, 0));
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		initComponent();
	}

	private void initComponent() {
		// 타이틀 폰트 통일
		Font titleFont = new Font("맑은 고딕", Font.BOLD, 12);

		// =========================================================================
		// 1. 좌측 패널: 시리얼 하드웨어 연결 설정 (세로 정렬)
		// =========================================================================
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "1. 시리얼 하드웨어 연결 설정",
				TitledBorder.LEFT, TitledBorder.TOP, titleFont));

		portCombo = new JComboBox<>();
		refreshAvailablePorts();
		baudCombo = new JComboBox<>(new String[] { "9600", "19200", "38400", "115200" });
		dataCombo = new JComboBox<>(new String[] { "8", "7" });
		dataCombo.setSelectedItem("8");
		parityCombo = new JComboBox<>(new String[] { "NO", "ODD", "EVEN", "MARK", "SPACE" });
		stopCombo = new JComboBox<>(new String[] { "1", "2" });

		// 한 행씩 칼정렬하여 추가
		leftPanel.add(createFormRow("포트 선택:", portCombo));
		leftPanel.add(createFormRow("Baud Rate:", baudCombo));
		leftPanel.add(createFormRow("Data Bits:", dataCombo));
		leftPanel.add(createFormRow("Parity Bit:", parityCombo));
		leftPanel.add(createFormRow("Stop Bits:", stopCombo));

		// 💡 [수정] 버튼을 컴포넌트들의 맨 하단으로 밀어버리기 위한 수직 Glue 추가
		leftPanel.add(Box.createVerticalGlue());

		// 💡 [수정] 좌측 버튼 패널을 가운데(CENTER) 정렬로 변경
		JPanel leftBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		leftBtnPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
		connectBtn = new JButton("포트 연결");
		leftBtnPanel.add(connectBtn);

		leftPanel.add(leftBtnPanel);
		leftPanel.add(Box.createVerticalStrut(5)); // 테두리와의 미세한 간격을 위한 하단 여백

		// =========================================================================
		// 2. 우측 패널: Modbus RTU 프로토콜 스펙 제어 (세로 정렬)
		// =========================================================================
		JPanel rightPanel = new JPanel();
		rightPanel.setLayout(new BoxLayout(rightPanel, BoxLayout.Y_AXIS));
		rightPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "2. 통신 제어 설정",
				TitledBorder.LEFT, TitledBorder.TOP, titleFont));

		scanRateField = new JTextField("1000");
		timeoutField = new JTextField("500");
		txDelayField = new JTextField("100");
		endianCombo = new JComboBox<>(
				new String[] { "리틀 엔디안 (Little)", "빅 엔디안 (Big)", "워드 스왑 (Word Swap)", "바이트 스왑 (Byte Swap)" });
		slaveIdField = new JTextField("1");

		// 한 행씩 칼정렬하여 추가
		rightPanel.add(createFormRow("Scan Rate(ms):", scanRateField));
		rightPanel.add(createFormRow("Timeout(ms):", timeoutField));
		rightPanel.add(createFormRow("Default Endian:", endianCombo));
		rightPanel.add(createFormRow("TX Delay(ms)", txDelayField));
		rightPanel.add(createFormRow("Slave ID", slaveIdField));

		// 💡 [수정] 버튼을 컴포넌트들의 맨 하단으로 밀어버리기 위한 수직 Glue 추가
		rightPanel.add(Box.createVerticalGlue());

		// 💡 [수정] 우측 버튼 패널도 동일하게 가운데(CENTER) 정렬로 정렬 패널 감싸기
		JPanel rightBtnPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		rightBtnPanel.setMaximumSize(new Dimension(Short.MAX_VALUE, 30));
		connectProtocolBtn = new JButton("통신 설정");
		rightBtnPanel.add(connectProtocolBtn);

		rightPanel.add(rightBtnPanel);
		rightPanel.add(Box.createVerticalStrut(5)); // 테두리와의 미세한 간격을 위한 하단 여백

		// 이벤트 바인딩
		connectBtn.addActionListener(e -> toggleConnection());
		connectProtocolBtn.addActionListener(e -> toggleCommunicationSetting());

		// 메인 패널에 최종 조립
		add(leftPanel);
		add(rightPanel);
	}

	// =================== 통신 제어 설정 ===================
	private void toggleCommunicationSetting() {
		if (!serialManager.isOpen()) {
			JOptionPane.showMessageDialog(this, "포트 연결이 해제된 상태입니다.");
			return;
		}
		int scanRate = Integer.parseInt(scanRateField.getText());
		double timeOut = Double.parseDouble(timeoutField.getText());
		int endian = (endianCombo.getSelectedIndex());
		double txDelay = Double.parseDouble(txDelayField.getText());
		int slaveId = Integer.parseInt(slaveIdField.getText());

		serialManager.communicationSetting(scanRate, timeOut, endian, txDelay, slaveId);
	}

	private JPanel createFormRow(String labelText, JComponent component) {
		JPanel row = new JPanel();
		row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
		row.setMaximumSize(new Dimension(Short.MAX_VALUE, 28));
		row.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));

		JLabel label = new JLabel(labelText);
		label.setPreferredSize(new Dimension(90, 25));
		label.setMinimumSize(new Dimension(90, 25));
		label.setMaximumSize(new Dimension(90, 25));
		label.setHorizontalAlignment(SwingConstants.RIGHT);

		row.add(label);
		row.add(Box.createHorizontalStrut(10));
		row.add(component);

		return row;
	}

	public void refreshAvailablePorts() {
		SerialPort[] ports = SerialPort.getCommPorts();
		portCombo.removeAllItems();
		for (SerialPort port : ports) {
			portCombo.addItem(port.getSystemPortName());
		}
		terminal.resetAllMeterTable();
	}

	// =================== 검침 요청 로직 ===================
	private void toggleConnection() {
		if (serialManager.isOpen()) {
			serialManager.disconnect();
			updateConnectionState(false);
			return;
		}

		if (portCombo.getSelectedItem() == null) {
			JOptionPane.showMessageDialog(this, "연결할 COM 포트가 존재하지 않습니다.");
			return;
		}

		String portName = portCombo.getSelectedItem().toString();
		int baudRate = Integer.parseInt(baudCombo.getSelectedItem().toString());
		int dataBits = Integer.parseInt(dataCombo.getSelectedItem().toString());
		int stopBits = (stopCombo.getSelectedIndex() == 1) ? SerialPort.TWO_STOP_BITS : SerialPort.ONE_STOP_BIT;
		int parity = parityCombo.getSelectedIndex();

		if (serialManager.validateCommunicationSpec(baudRate, dataBits, stopBits, parity)) {
			if (serialManager.connectingPort(portName, baudRate, dataBits, stopBits, parity)) {
				log.info("Port connected: {}, {}, {}, {}, {}", portName, baudRate, dataBits, stopBits, parity);
				updateConnectionState(true);
			} else {
				JOptionPane.showMessageDialog(this, "포트 개방에 실패하였습니다. 타 프로그램 점유 여부를 확인하세요.");
			}
		} else {
			JOptionPane.showMessageDialog(this, "해당 프로토콜 규격에 맞지 않는 설정입니다.\n스펙을 재설정 해주세요.");
		}
	}

	// =================== 시리얼 연결되었을 때 버튼 무효화 ===================
	public void updateConnectionState(boolean connected) {
		connectBtn.setText(connected ? "포트 해제" : "포트 연결");
		terminal.setSendButtonEnabled(connected);

		portCombo.setEnabled(!connected);
		baudCombo.setEnabled(!connected);
		dataCombo.setEnabled(!connected);
		parityCombo.setEnabled(!connected);
		stopCombo.setEnabled(!connected);
	}

	public int getSlaveId() {
		return Integer.parseInt(slaveIdField.getText());
	}
}