package com.DSK.ui;

import com.DSK.model.dto.common.Manufacturer;
import com.DSK.model.dto.common.MeterAddressMap;
import com.DSK.model.dto.common.MeterInfo;
import com.DSK.model.dto.addressMap.*;
import com.DSK.serial.HexaManager;
import com.DSK.serial.OpenHistoryDialog;
import com.DSK.serial.converter.ProtocolConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Map;
import java.util.HashMap;

public class HexaTerminal extends JFrame {
	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(HexaTerminal.class);

	private final HexaManager serialManager;

	private ConfigPanel configPanel;

	private JTable meterTable;
	private DefaultTableModel meterModel;

	private JTextArea systemLogArea;
	private JTextArea rxTxArea;

	private final Map<Integer, Integer> rowMap = new HashMap<>();

	private MeterAddressMap currentAddressMap;
	private JComboBox<Manufacturer> companyComboBox;

	private JButton sendHexaBtn;

	private JTextField rawHexField;
	private JButton sendRawBtn;
	private JCheckBox crcAutoCheck;
	private JLabel crcPreviewLabel;

	private JLabel liveClockLabel;
	private JTextArea rawResponseArea;

	public HexaTerminal() {
		serialManager = new HexaManager(this);

		setTitle("옴니/위지트 다기종 Modbus RTU 시리얼 포트 테스팅");
		setSize(1150, 750); // 번호 열 공간 확보를 위해 가로 너비 살짝 확장
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout(10, 10));

		getContentPane().setBackground(Color.WHITE);

		initUI();
	}

	public void resetAllMeterTable() {
		SwingUtilities.invokeLater(() -> {
			for (int row = 0; row < meterModel.getRowCount(); row++) {
				meterModel.setValueAt(false, row, 1); // 💡 인덱스 1 (선택 체크박스)
				meterModel.setValueAt("-", row, 5); // 💡 인덱스 5 (현재 검침값)
				meterModel.setValueAt("대기", row, 6); // 💡 인덱스 6 (상태)
			}
		});
	}

	public void updateMeterValue(int address, String value, String status) {
		log.info("응답 주소 = {}", address);
		Integer row = rowMap.get(address);
		if (row == null) {
			log.error("{전달 받은 주소값이 없거나 요청 수신간에 오류 발생!}");
			return;
		}
		SwingUtilities.invokeLater(() -> {
			meterModel.setValueAt(value, row, 5); // 💡 인덱스 한 칸씩 뒤로 밀림 반영
			meterModel.setValueAt(status, row, 6);
		});
	}

	private void loadMeterItems() {
		if (currentAddressMap == null)
			return;

		meterModel.setRowCount(0);
		rowMap.clear();

		final java.util.concurrent.atomic.AtomicInteger rowNum = new java.util.concurrent.atomic.AtomicInteger(1);

		currentAddressMap.getAll().keySet().stream().sorted().forEach(address -> {
			MeterInfo info = currentAddressMap.get(address);
			meterModel.addRow(new Object[] { rowNum.getAndIncrement(), // 0번 열: 번호
					false, String.format("0x%04X", address), // 2번 열: 주소
					info.getName(), // 3번 열: 항목명
					"값 쓰기", "-", "대기" });
			rowMap.put(address, meterModel.getRowCount() - 1);
		});
		log.info("================= {} 프로토콜 맵 로드 완료 =================\n", companyComboBox.getSelectedItem());
	}

	private void initUI() {
		((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
		((JPanel) getContentPane()).setBackground(Color.WHITE);

		// =================================================================
		// 1. 좌측 콤팩트 제어 패널
		// =================================================================
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setPreferredSize(new Dimension(500, 0));
		leftPanel.setBackground(Color.WHITE);

		configPanel = new ConfigPanel(this, serialManager);
		configPanel.setBackground(Color.WHITE);
		leftPanel.add(configPanel);
		leftPanel.add(Box.createVerticalStrut(10));

		JPanel companyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 5));
		companyPanel.setBorder(
				BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)), "제조사 선택"));
		companyPanel.setBackground(Color.WHITE);
		JLabel compLabel = new JLabel("연결 제조사 : ");
		compLabel.setForeground(Color.BLACK);
		companyPanel.add(compLabel);

		companyComboBox = new JComboBox<>(Manufacturer.values());
		companyComboBox.setPreferredSize(new Dimension(220, 25));
		companyComboBox.setBackground(Color.WHITE);
		companyComboBox.setForeground(Color.BLACK);
		companyPanel.add(companyComboBox);
		leftPanel.add(companyPanel);
		leftPanel.add(Box.createVerticalStrut(10));

		JPanel rawHexPanel = new JPanel();
		rawHexPanel.setLayout(new BoxLayout(rawHexPanel, BoxLayout.Y_AXIS));
		rawHexPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)),
				"Raw HEX 디버깅 및 분석"));
		rawHexPanel.setBackground(Color.WHITE);

		JPanel inputRow = new JPanel(new BorderLayout(5, 0));
		inputRow.setBackground(Color.WHITE);
		rawHexField = new JTextField();
		rawHexField.setBackground(Color.WHITE);
		rawHexField.setForeground(Color.BLACK);
		sendRawBtn = new JButton("HEX 전송");
		sendRawBtn.setBackground(Color.WHITE);
		sendRawBtn.setForeground(Color.BLACK);
		inputRow.add(rawHexField, BorderLayout.CENTER);
		inputRow.add(sendRawBtn, BorderLayout.EAST);
		rawHexPanel.add(inputRow);
		rawHexPanel.add(Box.createVerticalStrut(5));

		JPanel optionRow = new JPanel(new BorderLayout());
		optionRow.setBackground(Color.WHITE);
		crcAutoCheck = new JCheckBox("CRC 자동 계산", true);
		crcAutoCheck.setBackground(Color.WHITE);
		crcAutoCheck.setForeground(Color.BLACK);
		crcPreviewLabel = new JLabel("송신 예정: -");
		crcPreviewLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		crcPreviewLabel.setForeground(Color.DARK_GRAY);
		optionRow.add(crcAutoCheck, BorderLayout.WEST);
		optionRow.add(crcPreviewLabel, BorderLayout.EAST);
		rawHexPanel.add(optionRow);
		rawHexPanel.add(Box.createVerticalStrut(5));

		rawResponseArea = new JTextArea(8, 20);
		rawResponseArea.setEditable(false);
		rawResponseArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
		rawResponseArea.setBackground(Color.WHITE);
		rawResponseArea.setForeground(Color.BLACK);
		rawResponseArea.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createLineBorder(new Color(220, 220, 220)), BorderFactory.createEmptyBorder(5, 5, 5, 5)));
		JScrollPane responseScroll = new JScrollPane(rawResponseArea);
		responseScroll.getViewport().setBackground(Color.WHITE);
		rawHexPanel.add(responseScroll);
		rawHexPanel.add(Box.createVerticalStrut(5));

		JButton clearRawResponseBtn = new JButton("분석창 초기화");
		clearRawResponseBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		clearRawResponseBtn.setBackground(Color.WHITE);
		clearRawResponseBtn.setForeground(Color.BLACK);
		JPanel rightBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		rightBtnPanel.setBackground(Color.WHITE);
		rightBtnPanel.add(clearRawResponseBtn);
		rawHexPanel.add(rightBtnPanel);

		leftPanel.add(rawHexPanel);
		leftPanel.add(Box.createVerticalStrut(10));

		JPanel actionBtnPanel = new JPanel(new GridLayout(2, 1, 0, 8));
		actionBtnPanel.setBackground(Color.WHITE);
		sendHexaBtn = new JButton("선택 항목 검침 요청");
		sendHexaBtn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
		sendHexaBtn.setEnabled(false);
		sendHexaBtn.setBackground(Color.WHITE);
		sendHexaBtn.setForeground(Color.BLACK);

		JButton viewHistoryBtn = new JButton("데이터 검침 이력 전체 조회");
		viewHistoryBtn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
		viewHistoryBtn.setBackground(Color.WHITE);
		viewHistoryBtn.setForeground(Color.BLACK);

		actionBtnPanel.add(sendHexaBtn);
		actionBtnPanel.add(viewHistoryBtn);
		leftPanel.add(actionBtnPanel);

		add(leftPanel, BorderLayout.WEST);

		// =================================================================
		// 2. 우측 모니터링 패널
		// =================================================================
		JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
		rightPanel.setBackground(Color.WHITE);

		// 💡 [변경] 맨 앞에 "No." 컬럼 추가
		String[] columns = { "No.", "선택", "주소", "항목명", "기능 설정", "현재 검침값", "상태" };
		meterModel = new DefaultTableModel(columns, 0) {
			@Override
			public Class<?> getColumnClass(int columnIndex) {
				// 💡 체크박스 열 인덱스가 1번으로 한 칸 밀림
				return columnIndex == 1 ? Boolean.class : String.class;
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				// 💡 체크박스 열(인덱스 1)만 편집 가능하게 변경
				return col == 1;
			}
		};
		meterTable = new JTable(meterModel);
		meterTable.setBackground(Color.WHITE);
		meterTable.setFillsViewportHeight(true);
		meterTable.setForeground(Color.BLACK);
		meterTable.setGridColor(new Color(235, 235, 235));

		// 💡 [테이블 각 열의 기본 너비 밸런스 조정]
		meterTable.getColumnModel().getColumn(0).setPreferredWidth(45); // No. 열
		meterTable.getColumnModel().getColumn(1).setPreferredWidth(45); // 선택 열
		meterTable.getColumnModel().getColumn(2).setPreferredWidth(70); // 주소 열
		meterTable.getColumnModel().getColumn(3).setPreferredWidth(150); // 항목명 열
		meterTable.getColumnModel().getColumn(4).setPreferredWidth(90); // 기능 설정 열

		// 💡 쓰기 액션 탐지 인덱스 번호 조정 (기능 설정 열 = 인덱스 4)
		meterTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int row = meterTable.getSelectedRow();
				int col = meterTable.getSelectedColumn();
				if (row < 0)
					return;

				// '기능 설정' 열(인덱스 4)을 클릭했거나 행을 더블클릭했을 때 실행
				if (col == 4 || e.getClickCount() == 2) {
					String addrHex = meterModel.getValueAt(row, 2).toString(); // 주소는 인덱스 2번
					String itemName = meterModel.getValueAt(row, 3).toString(); // 항목명은 인덱스 3번
					executeWriteDialog(addrHex, itemName, row);
				}
			}
		});

		JScrollPane meterScroll = new JScrollPane(meterTable);
		meterScroll.getViewport().setBackground(Color.WHITE);
		meterScroll.setBorder(BorderFactory.createLineBorder(new Color(220, 220, 220)));
		rightPanel.add(meterScroll, BorderLayout.CENTER);

		JTabbedPane logTab = new JTabbedPane();
		logTab.setBackground(Color.WHITE);
		logTab.setForeground(Color.BLACK);

		JPanel systemLogTabPanel = new JPanel(new BorderLayout(5, 0));
		systemLogTabPanel.setBackground(Color.WHITE);
		systemLogArea = new JTextArea();
		systemLogArea.setEditable(false);
		JScrollPane systemLogScroll = new JScrollPane(systemLogArea);
		systemLogScroll.getViewport().setBackground(Color.WHITE);
		JButton clearSystemLogBtn = new JButton("로그 비우기");
		clearSystemLogBtn.setBackground(Color.WHITE);
		clearSystemLogBtn.setForeground(Color.BLACK);
		JPanel sysTopRightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
		sysTopRightPanel.setBackground(Color.WHITE);
		sysTopRightPanel.add(clearSystemLogBtn);
		systemLogTabPanel.add(systemLogScroll, BorderLayout.CENTER);
		systemLogTabPanel.add(sysTopRightPanel, BorderLayout.EAST);

		JPanel rxTxTabPanel = new JPanel(new BorderLayout(5, 0));
		rxTxTabPanel.setBackground(Color.WHITE);
		rxTxArea = new JTextArea();
		rxTxArea.setEditable(false);
		rxTxArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
		JScrollPane rxTxScroll = new JScrollPane(rxTxArea);
		rxTxScroll.getViewport().setBackground(Color.WHITE);
		JButton clearRxTxBtn = new JButton("터미널 비우기");
		clearRxTxBtn.setBackground(Color.WHITE);
		clearRxTxBtn.setForeground(Color.BLACK);
		JPanel rxTxBtnWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
		rxTxBtnWrapper.setBackground(Color.WHITE);
		rxTxBtnWrapper.add(clearRxTxBtn);
		rxTxTabPanel.add(rxTxScroll, BorderLayout.CENTER);
		rxTxTabPanel.add(rxTxBtnWrapper, BorderLayout.EAST);

		logTab.addTab("시스템 상태 로그", systemLogTabPanel);
		logTab.addTab("실시간 RX / TX 데이터 터미널", rxTxTabPanel);
		logTab.setPreferredSize(new Dimension(0, 220));
		rightPanel.add(logTab, BorderLayout.SOUTH);

		add(rightPanel, BorderLayout.CENTER);

		// =================================================================
		// 3. 상단 인포메이션 바
		// =================================================================
		JPanel topInfoPanel = new JPanel(new BorderLayout());
		topInfoPanel.setBackground(Color.WHITE);
		topInfoPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(230, 230, 230)));
		liveClockLabel = new JLabel("현재 시간: " + getCurrentTime());
		liveClockLabel.setFont(new Font("맑은 고딕", Font.BOLD, 13));
		liveClockLabel.setForeground(new Color(50, 50, 50));
		topInfoPanel.add(liveClockLabel, BorderLayout.EAST);
		startLiveClock();
		add(topInfoPanel, BorderLayout.NORTH);

		// =================================================================
		// 4. 리스너 바인딩
		// =================================================================
		companyComboBox.addActionListener(e -> {
			Manufacturer selected = (Manufacturer) companyComboBox.getSelectedItem();
			if (selected == Manufacturer.OMNI) {
				currentAddressMap = new Omni();
			} else if (selected == Manufacturer.WIZIT) {
				currentAddressMap = new Wizit();
			}
			loadMeterItems();
		});
		companyComboBox.setSelectedIndex(0);

		clearRawResponseBtn.addActionListener(e -> {
			rawResponseArea.setText("수신 데이터 \n\n[데이터 분석 결과]\n여기에 파싱된 상세 내역이 표시됩니다.");
		});
		clearSystemLogBtn.addActionListener(e -> systemLogArea.setText(""));
		clearRxTxBtn.addActionListener(e -> rxTxArea.setText(""));
		sendRawBtn.addActionListener(e -> sendRawData());
		sendHexaBtn.addActionListener(e -> sendHexData());
		viewHistoryBtn.addActionListener(e -> {
			if (serialManager == null) {
				JOptionPane.showMessageDialog(this, "시리얼 연결을 확인하세요.");
				return;
			}
			new OpenHistoryDialog(this, serialManager);
		});
		rawHexField.getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
			public void insertUpdate(javax.swing.event.DocumentEvent e) {
				updateCrcPreview();
			}

			public void removeUpdate(javax.swing.event.DocumentEvent e) {
				updateCrcPreview();
			}

			public void changedUpdate(javax.swing.event.DocumentEvent e) {
				updateCrcPreview();
			}
		});
		crcAutoCheck.addActionListener(e -> updateCrcPreview());
	}

	// ============== write(0x06) ==============
	private void executeWriteDialog(String addrHex, String itemName, int targetRow) {
		if (serialManager == null || !serialManager.isOpen()) {
			JOptionPane.showMessageDialog(this, "시리얼 포트 연결이 끊겨있어 쓰기 동작이 불가합니다.", "연결 오류", JOptionPane.WARNING_MESSAGE);
			return;
		}

		String inputValue = JOptionPane.showInputDialog(this,
				"▶ 선택 항목: " + itemName + " (" + addrHex + ")\n"
						+ "변경 설정할 정수(HEX 또는 10진수) 값을 입력하세요:\n(예: 0 또는 1, 세팅용 특정 상숫값)",
				"Modbus RTU 프리셋 파라미터 값 쓰기 (FC 06)", JOptionPane.QUESTION_MESSAGE);

		if (inputValue == null || inputValue.trim().isEmpty())
			return;

		try {
			int targetAddress = Integer.parseInt(addrHex.replace("0x", ""), 16);
			int writeValue = Integer.parseInt(inputValue.trim());
			int slaveId = configPanel.getSlaveId();

			byte[] packet = new byte[8];
			packet[0] = (byte) (slaveId & 0xFF);
			packet[1] = 0x06; // 💡 0x06은 "너 사물함 내용물 바꿔라"라는 쓰기 명령 코드!
			packet[2] = (byte) ((targetAddress >> 8) & 0xFF);
			packet[3] = (byte) (targetAddress & 0xFF);
			packet[4] = (byte) ((writeValue >> 8) & 0xFF);
			packet[5] = (byte) (writeValue & 0xFF);

			int crc = ProtocolConverter.calculateCRC16(packet, 6);

			packet[6] = (byte) (crc & 0xFF);
			packet[7] = (byte) ((crc >> 8) & 0xFF);

//			serialManager.enqueue(packet);
//			serialManager.startSend();

			meterModel.setValueAt("변경중..", targetRow, 6);
			String sendingHex = ProtocolConverter.convertBytesToHex(packet, packet.length);
			serialManager.sendRawHex(sendingHex, true);
			appendSystemLog(String.format("[파라미터 쓰기 요청] 주소: %s -> 설정 요청값: %d\n보내는 원시 헥사 데이터 : [%s]", addrHex,
					writeValue, sendingHex));
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "정수 숫자 형식만 대입 가능합니다.", "입력 포맷 오류", JOptionPane.ERROR_MESSAGE);
			appendSystemLog("[쓰기 에러] " + ex.getMessage());
		}
	}

	public void updateRawResponseArea(String rawHex, String parsedDetails) {
		SwingUtilities.invokeLater(() -> {
			String currentTime = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
			String text = "[수신 Hex 원시 데이터] " + rawHex + "\n" + "[수신 시간]      " + currentTime + "\n" + "[데이터 분석 결과]\n"
					+ parsedDetails;
			rawResponseArea.setText(text);
			rawResponseArea.setCaretPosition(0);
		});
	}

	private void updateCrcPreview() {
		String text = rawHexField.getText().trim();
		if (text.isEmpty()) {
			crcPreviewLabel.setText("최종 송신 예정: -");
			return;
		}
		try {
			byte[] inputBytes = ProtocolConverter.convertHexToBytes(text);
			if (crcAutoCheck.isSelected()) {
				int crc = ProtocolConverter.calculateCRC16(inputBytes, inputBytes.length);
				byte[] fullPacket = new byte[inputBytes.length + 2];
				System.arraycopy(inputBytes, 0, fullPacket, 0, inputBytes.length);
				fullPacket[fullPacket.length - 2] = (byte) (crc & 0xFF);
				fullPacket[fullPacket.length - 1] = (byte) ((crc >> 8) & 0xFF);
				String fullHex = ProtocolConverter.convertBytesToHex(fullPacket, fullPacket.length);
				crcPreviewLabel.setText("송신 예정: " + fullHex);
			} else {
				crcPreviewLabel.setText("송신 예정: " + text);
			}
		} catch (Exception e) {
			crcPreviewLabel.setText("송신 예정: [HEX 에러]");
		}
	}

	public void appendTx(String hex) {
		SwingUtilities.invokeLater(() -> {
			rxTxArea.append("[TX] " + hex + " [" + getCurrentTime() + "]\n");
			rxTxArea.setCaretPosition(rxTxArea.getDocument().getLength());
		});
	}

	public void appendRx(String hex) {
		SwingUtilities.invokeLater(() -> {
			rxTxArea.append("[RX] " + hex + " [" + getCurrentTime() + "]\n");
			rxTxArea.setCaretPosition(rxTxArea.getDocument().getLength());
		});
	}

	public void appendTerminal(String msg) {
		SwingUtilities.invokeLater(() -> {
			rxTxArea.append(msg + "\n");
			rxTxArea.setCaretPosition(rxTxArea.getDocument().getLength());
		});
	}

	public void appendSystemLog(String msg) {
		SwingUtilities.invokeLater(() -> {
			systemLogArea.append(String.format("[%s] %s\n", getCurrentTime(), msg));
			systemLogArea.setCaretPosition(systemLogArea.getDocument().getLength());
		});
	}

	private void sendRawData() {
		if (!serialManager.isOpen()) {
			JOptionPane.showMessageDialog(this, "포트를 먼저 연결하세요.");
			return;
		}
		String hex = rawHexField.getText().trim();
		if (hex.isEmpty()) {
			JOptionPane.showMessageDialog(this, "HEX 데이터를 입력하세요.");
			return;
		}
		try {
			serialManager.sendRawHex(hex, crcAutoCheck.isSelected());
		} catch (IllegalArgumentException ex) {
			JOptionPane.showMessageDialog(this, (ex.getMessage() != null ? ex.getMessage() : ""), "입력 오류",
					JOptionPane.WARNING_MESSAGE);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "HEX 전송 과정에서 에러가 발생했습니다.");
			ex.printStackTrace();
		}
	}

	private void sendHexData() {
		SwingUtilities.invokeLater(() -> {
			for (int row = 0; row < meterModel.getRowCount(); row++) {
				meterModel.setValueAt("-", row, 5); // 💡 검침값 컬럼 인덱스 5
				meterModel.setValueAt("대기", row, 6); // 💡 상태 컬럼 인덱스 6
			}
		});

		if (serialManager == null || !serialManager.isOpen()) {
			JOptionPane.showMessageDialog(this, "시리얼 포트가 열려있지 않습니다.");
			return;
		}

		boolean hasSelection = false;
		for (int row = 0; row < meterTable.getRowCount(); row++) {
			Boolean checked = (Boolean) meterModel.getValueAt(row, 1); // 💡 체크박스는 인덱스 1
			if (!Boolean.TRUE.equals(checked))
				continue;

			hasSelection = true;
			String addressHex = meterModel.getValueAt(row, 2).toString(); // 💡 주소는 인덱스 2
			int startAddress = Integer.parseInt(addressHex.replace("0x", ""), 16);

			try {
				int slaveId = configPanel.getSlaveId();
				byte[] rawBytes = { (byte) (slaveId & 0xFF), 0x04, (byte) ((startAddress >> 8) & 0xFF),
						(byte) (startAddress & 0xFF), 0x00, 0x02 };
				int crc = ProtocolConverter.calculateCRC16(rawBytes, rawBytes.length);

				byte[] packet = new byte[8];
				System.arraycopy(rawBytes, 0, packet, 0, rawBytes.length);
				packet[6] = (byte) (crc & 0xFF);
				packet[7] = (byte) ((crc >> 8) & 0xFF);

				serialManager.enqueue(packet);
				meterModel.setValueAt("요청중", row, 5); // 💡 검침값 칸에 요청중 표시
				appendSystemLog("[요청] 주소 " + meterModel.getValueAt(row, 2) + " 데이터 검침 시작");
			} catch (Exception e) {
				meterModel.setValueAt("오류", row, 5);
			}
		}

		if (!hasSelection) {
			JOptionPane.showMessageDialog(this, "검침 항목을 선택하세요.");
			return;
		}
		serialManager.startSend();
	}

	public void setSendButtonEnabled(boolean enabled) {
		if (sendHexaBtn != null)
			sendHexaBtn.setEnabled(enabled);
		if (sendRawBtn != null)
			sendRawBtn.setEnabled(enabled);
	}

	public void handlePhysicalDisconnect() {
		SwingUtilities.invokeLater(() -> {
			JOptionPane.showMessageDialog(null, "물리적 연결이 끊어졌습니다!\n케이블 상태를 확인하세요.");
			configPanel.updateConnectionState(false);
		});
	}

	private void startLiveClock() {
		javax.swing.Timer clockTimer = new javax.swing.Timer(1000, e -> {
			liveClockLabel.setText("현재 시간: " + getCurrentTime());
		});
		clockTimer.start();
	}

	private String getCurrentTime() {
		return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
	}

	public static void main(String[] args) {
		com.formdev.flatlaf.FlatLightLaf.setup();
		SwingUtilities.invokeLater(() -> new HexaTerminal().setVisible(true));
	}
}