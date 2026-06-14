package com.DSK.ui;

import com.DSK.serial.HexaManager;
import com.fazecast.jSerialComm.SerialPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import java.awt.*;

public class ConfigPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(ConfigPanel.class);
	private final HexaTerminal terminal;
	private final HexaManager serialManager;

	private JComboBox<String> portCombo, baudCombo, parityCombo, stopCombo, dataCombo;
	private JButton connectBtn, chatRefreshBtn;

	public ConfigPanel(HexaTerminal terminal, HexaManager serialManager) {
		this.terminal = terminal;
		this.serialManager = serialManager;
		setLayout(new GridLayout(2, 5, 5, 5));
		setBorder(BorderFactory.createTitledBorder("시리얼 포트 설정"));
		initComponent();
	}

	private void initComponent() {
		add(new JLabel("포트 선택:", JLabel.RIGHT));
		portCombo = new JComboBox<>();
		refreshAvailablePorts();
		add(portCombo);

		add(new JLabel("Baud Rate:", JLabel.RIGHT));
		baudCombo = new JComboBox<>(new String[] { "9600" });
		add(baudCombo);

		add(new JLabel("Data Bits:", JLabel.RIGHT));
		dataCombo = new JComboBox<>(new String[] { "7", "8" });
		add(dataCombo);

		add(new JLabel("Parity Bit:", JLabel.RIGHT));
		parityCombo = new JComboBox<>(new String[] { "NO", "ODD", "EVEN", "MARK", "SPACE" });
		add(parityCombo);

		add(new JLabel("Stop Bits:", JLabel.RIGHT));
		stopCombo = new JComboBox<>(new String[] { "1", "2" });
		add(stopCombo);

		connectBtn = new JButton("포트 연결");
		connectBtn.addActionListener(e -> toggleConnection());
		add(connectBtn);

		chatRefreshBtn = new JButton("채팅 초기화");
		chatRefreshBtn.addActionListener(e -> {
			terminal.clearLogArea();
		});
		add(chatRefreshBtn);
	}

	public void refreshAvailablePorts() {
		SerialPort[] ports = SerialPort.getCommPorts();
		portCombo.removeAllItems();
		for (SerialPort port : ports) {
			portCombo.addItem(port.getSystemPortName());
		}
	}

	private void toggleConnection() {
		if (serialManager.isOpen()) {
			serialManager.disconnect();
			terminal.appendRxText("[SYSTEM MESSAGE] 포트 연결이 해제되었습니다.\n");
			updateConnectionState(false);
			terminal.resetCheckList();
			return;
		}

		if (portCombo.getSelectedItem() == null) {
			JOptionPane.showMessageDialog(this, "연결 COM 없음");
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
				terminal.appendRxText("[SYSTEM MESSAGE] " + portName + " 포트가 연결되었습니다.\n");
				updateConnectionState(true);
			} else {
				JOptionPane.showMessageDialog(this, "포트 연결에 실패하였습니다.");
			}
		} else {
			JOptionPane.showMessageDialog(this, "해당 프로토콜 규격에 맞지 않는 설정입니다.\n스펙을 재설정 해주세요.");
		}
	}

	public void updateConnectionState(boolean connected) {
		connectBtn.setText(connected ? "포트 해제" : "포트 연결");
		terminal.setSendButtonEnabled(connected);
		portCombo.setEnabled(!connected);
		baudCombo.setEnabled(!connected);
		dataCombo.setEnabled(!connected);
		parityCombo.setEnabled(!connected);
		stopCombo.setEnabled(!connected);
	}
}