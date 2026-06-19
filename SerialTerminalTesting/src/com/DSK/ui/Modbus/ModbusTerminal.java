package com.DSK.ui.Modbus;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.model.dto.addressMap.Omni;
import com.DSK.model.dto.addressMap.Wizit;
import com.DSK.model.dto.common.Manufacturer;
import com.DSK.model.dto.common.MeterAddressMap;
import com.DSK.model.dto.common.MeterInfo;
import com.DSK.serial.ModbusManager;
import com.DSK.serial.OpenHistoryDialog;
import com.DSK.serial.constant.DataType;
import com.DSK.serial.constant.EndianType;
import com.DSK.serial.converter.ByteConverter;
import com.DSK.serial.converter.ProtocolConverter;

public class ModbusTerminal extends JFrame {
	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(ModbusTerminal.class);

	private final ModbusManager serialManager;

	private ModbusConfigPanel configPanel;

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
	private JButton clearSelectionBtn;
	private JButton selectAllBtn;
	private JButton returnAddressTableBtn;

	// 분석 테이블 및 하단 뷰어 컴포넌트
	private JTable rawResponseTable;
	private DefaultTableModel rawResponseModel;
	private JTextArea valueDetailArea;
	private final Map<Integer, byte[]> rowDataBytesMap = new HashMap<>();

	public ModbusTerminal() {
		serialManager = new ModbusManager(this);

		setTitle("Modbus RTU 시리얼 포트 통합 테스팅 매니저");
		setSize(1250, 850); // 레이아웃 확장에 맞춰 전체 창 크기 밸런스 조정
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout(12, 12));

		getContentPane().setBackground(Color.WHITE);

		initUI();
	}

	public void resetAllMeterTable() {
		SwingUtilities.invokeLater(() -> {
			for (int row = 0; row < meterModel.getRowCount(); row++) {
				meterModel.setValueAt(false, row, 1);
				meterModel.setValueAt("-", row, 5);
				meterModel.setValueAt("대기", row, 6);
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
			meterModel.setValueAt(value, row, 5);
			meterModel.setValueAt(status, row, 6);
		});
	}

	private void loadMeterItems() {
		if (currentAddressMap == null)
			return;

		meterModel.setRowCount(0);
		rowMap.clear();

		java.util.List<Integer> addrList = currentAddressMap.getAll().keySet().stream().sorted().toList();
		String[] comboItems = new String[addrList.size()];

		final java.util.concurrent.atomic.AtomicInteger rowNum = new java.util.concurrent.atomic.AtomicInteger(1);

		for (int i = 0; i < addrList.size(); i++) {
			int address = addrList.get(i);
			comboItems[i] = String.format("0x%04X", address);

			MeterInfo info = currentAddressMap.get(address);
			meterModel.addRow(
					new Object[] { rowNum.getAndIncrement(), false, comboItems[i], info.getName(), "값 쓰기", "-", "대기" });
			rowMap.put(address, meterModel.getRowCount() - 1);
		}

		javax.swing.table.TableColumn addressColumn = meterTable.getColumnModel().getColumn(2);
		JComboBox<String> addressComboBox = new JComboBox<>(comboItems);
		addressComboBox.setBackground(Color.WHITE);
		addressComboBox.setForeground(Color.BLACK);

		addressColumn.setCellEditor(new javax.swing.DefaultCellEditor(addressComboBox));
	}

	private void initUI() {
		DefaultTableCellRenderer rowColorRenderer = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				if (isSelected) {
					c.setBackground(table.getSelectionBackground());
					c.setForeground(table.getSelectionForeground());
				} else {
					if (row % 2 == 0) {
						c.setBackground(new Color(245, 245, 245));
					} else {
						c.setBackground(Color.WHITE);
					}
					c.setForeground(Color.BLACK);
				}
				return c;
			}
		};

		Font boldTitleFont = new Font("맑은 고딕", Font.BOLD, 12);
		Color softBorderColor = new Color(225, 230, 235);

		((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		((JPanel) getContentPane()).setBackground(Color.WHITE);

		// =================================================================
		// 1. 좌측 제어 패널 구역 (분석창 높이 대폭 확장)
		// =================================================================
		JPanel leftPanel = new JPanel();
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setPreferredSize(new Dimension(510, 0));
		leftPanel.setBackground(Color.WHITE);

		configPanel = new ModbusConfigPanel(this, serialManager);
		configPanel.setBackground(Color.WHITE);
		leftPanel.add(configPanel);
		leftPanel.add(Box.createVerticalStrut(8));

		JPanel companyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 5));
		companyPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(softBorderColor),
				"제조사 선택", TitledBorder.LEFT, TitledBorder.TOP, boldTitleFont));
		companyPanel.setBackground(Color.WHITE);
		JLabel compLabel = new JLabel("연결 제조사 프로토콜 :");
		compLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
		compLabel.setForeground(Color.BLACK);
		companyPanel.add(compLabel);

		companyComboBox = new JComboBox<>(Manufacturer.values());
		companyComboBox.setPreferredSize(new Dimension(240, 26));
		companyComboBox.setBackground(Color.WHITE);
		companyComboBox.setForeground(Color.BLACK);
		companyPanel.add(companyComboBox);
		leftPanel.add(companyPanel);
		leftPanel.add(Box.createVerticalStrut(8));

		JPanel rawHexPanel = new JPanel();
		rawHexPanel.setLayout(new BoxLayout(rawHexPanel, BoxLayout.Y_AXIS));
		rawHexPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(softBorderColor),
				"Raw HEX 송신/분석 제어", TitledBorder.LEFT, TitledBorder.TOP, boldTitleFont));
		rawHexPanel.setBackground(Color.WHITE);
		rawHexPanel.add(Box.createVerticalStrut(5));

		JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
		inputRow.setBackground(Color.WHITE);
		inputRow.setMaximumSize(new Dimension(Short.MAX_VALUE, 28));

		rawHexField = new JTextField();
		rawHexField.setPreferredSize(new Dimension(310, 24));
		rawHexField.setBackground(Color.WHITE);
		rawHexField.setForeground(Color.BLACK);

		sendRawBtn = new JButton("HEX 전송");
		sendRawBtn.setPreferredSize(new Dimension(85, 24));
		sendRawBtn.setBackground(Color.WHITE);
		sendRawBtn.setForeground(Color.BLACK);

		inputRow.add(rawHexField);
		inputRow.add(sendRawBtn);
		rawHexPanel.add(inputRow);
		rawHexPanel.add(Box.createVerticalStrut(4));

		JPanel optionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
		optionRow.setBackground(Color.WHITE);
		optionRow.setMaximumSize(new Dimension(Short.MAX_VALUE, 22));

		crcAutoCheck = new JCheckBox("CRC 자동 계산", true);
		crcAutoCheck.setBackground(Color.WHITE);
		crcAutoCheck.setForeground(Color.BLACK);
		crcAutoCheck.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

		crcPreviewLabel = new JLabel("송신 예정: -");
		crcPreviewLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		crcPreviewLabel.setForeground(Color.BLUE);

		optionRow.add(crcAutoCheck);
		optionRow.add(crcPreviewLabel);
		rawHexPanel.add(optionRow);
		rawHexPanel.add(Box.createVerticalStrut(6));

		String[] rawColumns = { "No.", "주소", "항목명", "검침 미리보기 값" };
		rawResponseModel = new DefaultTableModel(rawColumns, 0) {
			@Override
			public boolean isCellEditable(int row, int col) {
				return false;
			}
		};
		rawResponseTable = new JTable(rawResponseModel);
		rawResponseTable.setBackground(Color.WHITE);
		rawResponseTable.setForeground(Color.BLACK);
		rawResponseTable.setGridColor(new Color(240, 242, 245));
		rawResponseTable.setRowHeight(22);
		rawResponseTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

		rawResponseTable.getColumnModel().getColumn(0).setPreferredWidth(45);
		rawResponseTable.getColumnModel().getColumn(1).setPreferredWidth(75);
		rawResponseTable.getColumnModel().getColumn(2).setPreferredWidth(230);
		rawResponseTable.getColumnModel().getColumn(3).setPreferredWidth(140);

		JScrollPane responseScroll = new JScrollPane(rawResponseTable);
		responseScroll.setPreferredSize(new Dimension(0, 140));
		responseScroll.getViewport().setBackground(Color.WHITE);
		responseScroll.setBorder(BorderFactory.createLineBorder(new Color(230, 232, 235)));
		rawHexPanel.add(responseScroll);
		rawHexPanel.add(Box.createVerticalStrut(6));

		// 🎯 [크기 확장] 선택 항목 데이터 분석창 영역 높이를 120 -> 260으로 대폭 확대
		JPanel detailPanel = new JPanel(new BorderLayout());
		detailPanel.setBackground(Color.WHITE);
		detailPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(240, 240, 240)),
				"선택 항목 데이터 분석", TitledBorder.LEFT, TitledBorder.TOP, new Font("맑은 고딕", Font.BOLD, 11)));

		valueDetailArea = new JTextArea();
		valueDetailArea.setEditable(false);
		valueDetailArea.setBackground(new Color(252, 252, 253));
		valueDetailArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
		valueDetailArea.setForeground(Color.DARK_GRAY);
		valueDetailArea.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		JScrollPane detailScroll = new JScrollPane(valueDetailArea);
		detailScroll.setPreferredSize(new Dimension(0, 260)); // 👈 높이 대폭 증설
		detailPanel.add(detailScroll, BorderLayout.CENTER);
		rawHexPanel.add(detailPanel);
		rawHexPanel.add(Box.createVerticalStrut(4));

		rawResponseTable.getSelectionModel().addListSelectionListener(e -> {
			if (!e.getValueIsAdjusting()) {
				int selectedRow = rawResponseTable.getSelectedRow();
				if (selectedRow >= 0) {
					byte[] targetBytes = rowDataBytesMap.get(selectedRow);
					displaySelectedValueDetail(targetBytes);
				}
			}
		});

		JButton clearRawResponseBtn = new JButton("Raw Hex 데이터 비우기");
		clearRawResponseBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		clearRawResponseBtn.setBackground(Color.WHITE);
		clearRawResponseBtn.setForeground(Color.BLACK);
		clearRawResponseBtn.addActionListener(e -> {
			rawResponseModel.setRowCount(0);
			rowDataBytesMap.clear();
			valueDetailArea.setText("");
		});

		JPanel rightBtnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		rightBtnPanel.setBackground(Color.WHITE);
		rightBtnPanel.add(clearRawResponseBtn);
		rawHexPanel.add(rightBtnPanel);

		leftPanel.add(rawHexPanel);
		add(leftPanel, BorderLayout.WEST);

		// =================================================================
		// 2. 우측 모니터링 테이블 및 터미널 탭 패널 구역 (버튼 이관 포함)
		// =================================================================
		JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
		rightPanel.setBackground(Color.WHITE);

		String[] columns = { "No.", "선택", "주소", "항목명", "설정 제어", "현재 검침값", "통신 상태" };
		meterModel = new DefaultTableModel(columns, 0) {
			@Override
			public Class<?> getColumnClass(int col) {
				return col == 1 ? Boolean.class : String.class;
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				return col == 1 || col == 2;
			}
		};
		meterTable = new JTable(meterModel);
		meterTable.setBackground(Color.WHITE);
		meterTable.setFillsViewportHeight(true);
		meterTable.setForeground(Color.BLACK);
		meterTable.setGridColor(new Color(240, 242, 245));
		meterTable.setRowHeight(24);

		meterTable.getColumnModel().getColumn(0).setPreferredWidth(45);
		meterTable.getColumnModel().getColumn(1).setPreferredWidth(45);
		meterTable.getColumnModel().getColumn(2).setPreferredWidth(75);
		meterTable.getColumnModel().getColumn(3).setPreferredWidth(160);
		meterTable.getColumnModel().getColumn(4).setPreferredWidth(90);

		meterTable.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(MouseEvent e) {
				int row = meterTable.getSelectedRow();
				int col = meterTable.getSelectedColumn();
				if (row < 0)
					return;

				if (col == 4 || e.getClickCount() == 2) {
					String addrHex = meterModel.getValueAt(row, 2).toString();
					String itemName = meterModel.getValueAt(row, 3).toString();
					executeWriteDialog(addrHex, itemName, row);
				}
			}
		});

		JScrollPane meterScroll = new JScrollPane(meterTable);
		meterScroll.getViewport().setBackground(Color.WHITE);
		meterScroll.setBorder(BorderFactory.createLineBorder(softBorderColor));
		rightPanel.add(meterScroll, BorderLayout.CENTER);

		// 하단 공용 컴포넌트 선언 및 인스턴스화
		sendHexaBtn = new JButton("체크 항목 검침 요청");
		sendHexaBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		sendHexaBtn.setEnabled(false);
		sendHexaBtn.setBackground(Color.WHITE);
		sendHexaBtn.setForeground(Color.BLACK);
		sendHexaBtn.setPreferredSize(new Dimension(160, 28));

		JButton viewHistoryBtn = new JButton("데이터 검침 이력 조회");
		viewHistoryBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		viewHistoryBtn.setBackground(Color.WHITE);
		viewHistoryBtn.setForeground(Color.BLACK);
		viewHistoryBtn.setPreferredSize(new Dimension(160, 28));

		// 하단 실시간 로그 탭 영역 디자인 강화
		JTabbedPane logTab = new JTabbedPane();
		logTab.setBackground(Color.WHITE);
		logTab.setForeground(Color.BLACK);

		// 🎯 [우측 배치] 시스템 상태 로그 탭 구성
		JPanel systemLogTabPanel = new JPanel(new BorderLayout(5, 0));
		systemLogTabPanel.setBackground(Color.WHITE);
		systemLogArea = new JTextArea();
		systemLogArea.setEditable(false);
		systemLogArea.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		JScrollPane systemLogScroll = new JScrollPane(systemLogArea);
		systemLogScroll.getViewport().setBackground(Color.WHITE);
		systemLogScroll.setBorder(BorderFactory.createLineBorder(new Color(240, 240, 240)));

		JButton clearSystemLogBtn = new JButton("로그 비우기");
		clearSystemLogBtn.setBackground(Color.WHITE);
		clearSystemLogBtn.setForeground(Color.BLACK);
		clearSystemLogBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		clearSystemLogBtn.setPreferredSize(new Dimension(160, 24));

		// 오른쪽 제어 버튼들을 세로로 모아 배치하는 컨트롤러 패널 (시스템 로그용)
		JPanel sysRightControlPanel = new JPanel(new GridLayout(3, 1, 0, 6));
		sysRightControlPanel.setBackground(Color.WHITE);
		sysRightControlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		sysRightControlPanel.add(clearSystemLogBtn);
		sysRightControlPanel.add(sendHexaBtn);
		sysRightControlPanel.add(viewHistoryBtn);

		systemLogTabPanel.add(systemLogScroll, BorderLayout.CENTER);
		systemLogTabPanel.add(sysRightControlPanel, BorderLayout.EAST);

		// 🎯 [우측 배치] RX / TX 터미널 탭 구성 (버튼 객체 공유 불가하므로 각 탭 전용 액션바 연동 구조화)
		JPanel rxTxTabPanel = new JPanel(new BorderLayout(5, 0));
		rxTxTabPanel.setBackground(Color.WHITE);
		rxTxArea = new JTextArea();
		rxTxArea.setEditable(false);
		rxTxArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
		rxTxArea.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		JScrollPane rxTxScroll = new JScrollPane(rxTxArea);
		rxTxScroll.getViewport().setBackground(Color.WHITE);
		rxTxScroll.setBorder(BorderFactory.createLineBorder(new Color(240, 240, 240)));

		JButton clearRxTxBtn = new JButton("터미널 비우기");
		clearRxTxBtn.setBackground(Color.WHITE);
		clearRxTxBtn.setForeground(Color.BLACK);
		clearRxTxBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		clearRxTxBtn.setPreferredSize(new Dimension(160, 24));

		// 터미널 탭 전용 검침 복제 버튼 생성 (공유 컴포넌트의 한계를 Grid 복제 디자인으로 우회)
		JButton sendHexaBtnShared = new JButton("체크 항목 검침 요청");
		sendHexaBtnShared.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		sendHexaBtnShared.setBackground(Color.WHITE);
		sendHexaBtnShared.setForeground(Color.BLACK);
		sendHexaBtnShared.setEnabled(false);
		sendHexaBtnShared.addActionListener(e -> sendHexData());

		// 스위칭 연동을 위해 참조 바인딩
		this.sendHexaBtn.addPropertyChangeListener("enabled",
				evt -> sendHexaBtnShared.setEnabled((Boolean) evt.getNewValue()));

		JButton viewHistoryBtnShared = new JButton("데이터 검침 이력 조회");
		viewHistoryBtnShared.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		viewHistoryBtnShared.setBackground(Color.WHITE);
		viewHistoryBtnShared.setForeground(Color.BLACK);
		viewHistoryBtnShared.addActionListener(e -> viewHistoryBtn.getActionListeners()[0].actionPerformed(e));

		JPanel rxTxRightControlPanel = new JPanel(new GridLayout(3, 1, 0, 6));
		rxTxRightControlPanel.setBackground(Color.WHITE);
		rxTxRightControlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		rxTxRightControlPanel.add(clearRxTxBtn);
		rxTxRightControlPanel.add(sendHexaBtnShared);
		rxTxRightControlPanel.add(viewHistoryBtnShared);

		rxTxTabPanel.add(rxTxScroll, BorderLayout.CENTER);
		rxTxTabPanel.add(rxTxRightControlPanel, BorderLayout.EAST);

		logTab.addTab("시스템 상태 로그", systemLogTabPanel);
		logTab.addTab("RX / TX 바이트 터미널", rxTxTabPanel);
		logTab.setPreferredSize(new Dimension(0, 230));
		rightPanel.add(logTab, BorderLayout.SOUTH);

		add(rightPanel, BorderLayout.CENTER);

		// =================================================================
		// 3. 최상단 인포메이션 상단 바 구역
		// =================================================================
		JPanel topInfoPanel = new JPanel(new BorderLayout());
		topInfoPanel.setBackground(Color.WHITE);
		topInfoPanel.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, softBorderColor));

		JPanel topRightWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 12, 4));
		topRightWrapper.setBackground(Color.WHITE);

		JPanel topLeftWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 12, 4));
		topLeftWrapper.setBackground(Color.WHITE);

		returnAddressTableBtn = new JButton("원본 주소 맵 초기화");
		returnAddressTableBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		returnAddressTableBtn.setBackground(Color.WHITE);
		returnAddressTableBtn.setForeground(Color.BLACK);

		clearSelectionBtn = new JButton("검침값 초기화");
		clearSelectionBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		clearSelectionBtn.setBackground(Color.WHITE);
		clearSelectionBtn.setForeground(Color.BLACK);

		selectAllBtn = new JButton("전체 선택");
		selectAllBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		selectAllBtn.setBackground(Color.WHITE);
		selectAllBtn.setForeground(Color.BLACK);

		liveClockLabel = new JLabel("현재 시간: " + getCurrentTime());
		liveClockLabel.setFont(new Font("맑은 고딕", Font.BOLD, 14));
		liveClockLabel.setForeground(Color.BLACK);

		topLeftWrapper.add(liveClockLabel);
		startLiveClock();

		topRightWrapper.add(returnAddressTableBtn);
		topRightWrapper.add(clearSelectionBtn);
		topRightWrapper.add(selectAllBtn);

		topInfoPanel.add(topRightWrapper, BorderLayout.EAST);
		topInfoPanel.add(topLeftWrapper, BorderLayout.WEST);
		add(topInfoPanel, BorderLayout.NORTH);

		// =================================================================
		// 4. 이벤트 버스 제어 리스너 바인딩 구역
		// =================================================================
		companyComboBox.addActionListener(e -> {
			Manufacturer selected = (Manufacturer) companyComboBox.getSelectedItem();
			if (selected == Manufacturer.OMNI) {
				currentAddressMap = new Omni();
				appendSystemLog("Omni 프로토콜 주소 번지 맵 바인딩 완료");
			} else if (selected == Manufacturer.WIZIT) {
				currentAddressMap = new Wizit();
				appendSystemLog("Wizit 프로토콜 주소 번지 맵 바인딩 완료");
			}
			loadMeterItems();
		});
		companyComboBox.setSelectedIndex(0);

		returnAddressTableBtn.addActionListener(e -> {
			if (currentAddressMap == null) {
				JOptionPane.showMessageDialog(this, "선택된 제조사 프로토콜 맵이 존재하지 않습니다.", "인포", JOptionPane.WARNING_MESSAGE);
				return;
			}
			loadMeterItems();
			appendSystemLog("[테이블 초기화] 테이블 주소가 복구되었습니다.");
		});

		clearSelectionBtn.addActionListener(e -> {
			resetAllMeterTable();
			selectAllorRefresh();
		});
		selectAllBtn.addActionListener(e -> {
			selectAllorRefresh();
		});
		clearSystemLogBtn.addActionListener(e -> systemLogArea.setText(""));
		clearRxTxBtn.addActionListener(e -> rxTxArea.setText(""));
		sendRawBtn.addActionListener(e -> sendRawData());
		rawHexField.addActionListener(e -> sendRawData());
		sendHexaBtn.addActionListener(e -> sendHexData());

		viewHistoryBtn.addActionListener(e -> {
			if (serialManager == null) {
				JOptionPane.showMessageDialog(this, "시리얼 포트 상태를 확안하세요.");
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

		meterModel.addTableModelListener(e -> {
			if (e.getType() == javax.swing.event.TableModelEvent.UPDATE && e.getColumn() == 2) {
				int row = e.getFirstRow();
				if (row < 0 || currentAddressMap == null)
					return;

				Object changedAddrObj = meterModel.getValueAt(row, 2);
				if (changedAddrObj == null)
					return;

				String newAddrHex = changedAddrObj.toString();
				try {
					int newAddressKey = Integer.parseInt(newAddrHex.replace("0x", ""), 16);
					MeterInfo info = currentAddressMap.get(newAddressKey);

					if (info != null) {
						SwingUtilities.invokeLater(() -> {
							meterModel.setValueAt(info.getName(), row, 3);
							rowMap.put(newAddressKey, row);
						});
					}
				} catch (Exception ex) {
					log.error("테이블 인라인 콤보 주소 변경 데이터 갱신 중 예외", ex);
				}
			}
		});

		meterTable.setDefaultRenderer(Object.class, rowColorRenderer);
		rawResponseTable.setDefaultRenderer(Object.class, rowColorRenderer);
		meterTable.setDefaultRenderer(Number.class, rowColorRenderer);
		rawResponseTable.setDefaultRenderer(Number.class, rowColorRenderer);
	}

	private void executeWriteDialog(String addrHex, String itemName, int targetRow) {
		if (serialManager == null || !serialManager.isOpen()) {
			JOptionPane.showMessageDialog(this, "시리얼 커뮤니케이션 포트가 열려있지 않아 쓰기가 취소되었습니다.", "인포",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		String inputValue = JOptionPane.showInputDialog(this,
				"변경 설정할 정수(HEX 또는 10진수) 값을 입력하세요:\n(예: 0 또는 1, 세팅용 특정 상숫값)",
				String.format("%s (%s)", itemName, addrHex), JOptionPane.QUESTION_MESSAGE);

		if (inputValue == null || inputValue.trim().isEmpty())
			return;

		try {
			int targetAddress = Integer.parseInt(addrHex.replace("0x", ""), 16);
			int writeValue = Integer.parseInt(inputValue.trim());
			int slaveId = configPanel.getSlaveId();

			byte[] packet = new byte[8];
			packet[0] = (byte) (slaveId & 0xFF);
			packet[1] = 0x06;
			packet[2] = (byte) ((targetAddress >> 8) & 0xFF);
			packet[3] = (byte) (targetAddress & 0xFF);
			packet[4] = (byte) ((writeValue >> 8) & 0xFF);
			packet[5] = (byte) (writeValue & 0xFF);

			int crc = ProtocolConverter.calculateCRC16(packet, 6);
			packet[6] = (byte) (crc & 0xFF);
			packet[7] = (byte) ((crc >> 8) & 0xFF);

			meterModel.setValueAt("변경중..", targetRow, 6);
			String sendingHex = ProtocolConverter.convertBytesToHex(packet, packet.length);
			serialManager.sendRawHex(sendingHex, true);
			appendSystemLog(String.format("[싱글 레지스터 강제 쓰기 요청] 주소: %s -> 데이터: %d\n송신 바이트: [%s]", addrHex, writeValue,
					sendingHex));
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "올바른 숫자 입력 포맷이 아닙니다.", "에러", JOptionPane.ERROR_MESSAGE);
			appendSystemLog("[데이터 쓰기 진입 실패] " + ex.getMessage());
		}
	}

	public void updateRawResponseArea(String rawHex) {
		SwingUtilities.invokeLater(() -> {
			try {
				String hex = rawHex.replace(" ", "").trim();
				byte[] rawBytes = ProtocolConverter.convertHexToBytes(hex);

				if (rawBytes.length < 5)
					return;

				int dataLength = rawBytes[2] & 0xFF;

				int startAddress = serialManager.isOpen() ? ((rawBytes[0] & 0xFF) == 1
						? (((rawBytes[2] & 0xFF) > 0) ? (((ModbusManager) serialManager).isOpen() ? 0x004E : 0x0000)
								: 0x0000)
						: 0x0000) : 0x0000;
				try {
					java.lang.reflect.Field field = serialManager.getClass().getDeclaredField("currentRequestAddress");
					field.setAccessible(true);
					startAddress = field.getInt(serialManager);
				} catch (Exception e) {
					appendSystemLog("Raw HEX 응답 주소를 찾지 못했습니다.");
				}

				rawResponseModel.setRowCount(0);
				rowDataBytesMap.clear();

				int currentByteOffset = 3;
				int currentRegisterAddr = startAddress;

				while (currentByteOffset < 3 + dataLength) {
					MeterInfo info = (currentAddressMap != null) ? currentAddressMap.get(currentRegisterAddr) : null;

					String currentAddrStr = String.format("0x%04X", currentRegisterAddr);
					String currentName = (info != null) ? info.getName() : "정의되지 않은 주소 (UNKNOWN)";
					DataType dataType = (info != null && info.getDataType() != null) ? info.getDataType()
							: DataType.INT32;
					EndianType endianType = (info != null && info.getEndianType() != null) ? info.getEndianType()
							: EndianType.LITTLE;

					int itemSize = ByteConverter.getDataSize(dataType);
					if (currentByteOffset + itemSize > rawBytes.length - 2)
						break;

					Object convertedObj = ByteConverter.convert(rawBytes, currentByteOffset, dataType, endianType);
					String previewValue = (convertedObj != null) ? convertedObj.toString() + " " + info.getUnit() : "-";

					byte[] itemBytes = new byte[itemSize];
					System.arraycopy(rawBytes, currentByteOffset, itemBytes, 0, itemSize);

					int currentTableCount = rawResponseModel.getRowCount();

					if (info != null) {
						rawResponseModel.addRow(
								new Object[] { currentTableCount + 1, currentAddrStr, currentName, previewValue });
						rowDataBytesMap.put(currentTableCount, itemBytes);
					}
					currentByteOffset += itemSize;
					currentRegisterAddr += (itemSize / 2);
				}

				if (rawResponseModel.getRowCount() > 0) {
					rawResponseTable.setRowSelectionInterval(0, 0);
				}

			} catch (Exception ex) {
				log.error("Raw 응답 프레임 테이블 마샬링 가공 중 에러", ex);
			}
		});
	}

	private void displaySelectedValueDetail(byte[] d) {
		if (d == null || d.length == 0) {
			valueDetailArea.setText(" 선택된 데이터 스트림이 유효하지 않습니다.");
			return;
		}
		appendSystemLog(String.format("선택된 타겟 응답 바이트: %s", ProtocolConverter.convertBytesToHex(d, d.length)));

		int selectedRow = rawResponseTable.getSelectedRow();
		if (selectedRow < 0) {
			appendSystemLog("선택된 행이 없어 정보 출력이 불가합니다.");
			return;
		}
		String currentAddr = rawResponseModel.getValueAt(selectedRow, 1).toString();

		String rawHexSelectedDetailStr = serialManager.getSelectedValueDetail(d, currentAddr);
		valueDetailArea.setText(rawHexSelectedDetailStr);
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
			crcPreviewLabel.setText("송신 예정: [HEX 데이터 포맷 에러]");
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
			JOptionPane.showMessageDialog(this, "활성화된 시리얼 포트 세션이 없습니다.");
			return;
		}
		String hex = rawHexField.getText().trim();
		if (hex.isEmpty()) {
			JOptionPane.showMessageDialog(this, "송신할 HEX 문자열을 채워주십시오.");
			return;
		}
		try {
			serialManager.sendRawHex(hex, crcAutoCheck.isSelected());
		} catch (IllegalArgumentException ex) {
			JOptionPane.showMessageDialog(this, (ex.getMessage() != null ? ex.getMessage() : ""), "입력 에러",
					JOptionPane.WARNING_MESSAGE);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "물리 통신 전송 연동에 치명적 결함 발생");
			ex.printStackTrace();
		}
	}

	private void sendHexData() {
		SwingUtilities.invokeLater(() -> {
			for (int row = 0; row < meterModel.getRowCount(); row++) {
				meterModel.setValueAt("-", row, 5);
				meterModel.setValueAt("대기", row, 6);
			}
		});

		if (serialManager == null || !serialManager.isOpen()) {
			JOptionPane.showMessageDialog(this, "활성 개방 상태인 컴포트 대상이 확인되지 않습니다.");
			return;
		}

		boolean hasSelection = false;
		for (int row = 0; row < meterTable.getRowCount(); row++) {
			Boolean checked = (Boolean) meterModel.getValueAt(row, 1);
			if (!Boolean.TRUE.equals(checked))
				continue;

			hasSelection = true;
			String addressHex = meterModel.getValueAt(row, 2).toString();
			int startAddress = Integer.parseInt(addressHex.replace("0x", ""), 16);

			try {
				int slaveId = configPanel.getSlaveId();

				MeterInfo info = (currentAddressMap != null) ? currentAddressMap.get(startAddress) : null;
				DataType dataType = (info != null && info.getDataType() != null) ? info.getDataType() : DataType.INT32;

				int byteSize = ByteConverter.getDataSize(dataType);
				int registerCount = byteSize / 2;

				byte[] rawBytes = { (byte) (slaveId & 0xFF), 0x04, (byte) ((startAddress >> 8) & 0xFF),
						(byte) (startAddress & 0xFF), (byte) ((registerCount >> 8) & 0xFF),
						(byte) (registerCount & 0xFF) };

				int crc = ProtocolConverter.calculateCRC16(rawBytes, rawBytes.length);

				byte[] packet = new byte[8];
				System.arraycopy(rawBytes, 0, packet, 0, rawBytes.length);
				packet[6] = (byte) (crc & 0xFF);
				packet[7] = (byte) ((crc >> 8) & 0xFF);

				serialManager.enqueue(packet);
				meterModel.setValueAt("요청중", row, 5);
			} catch (Exception e) {
				meterModel.setValueAt("오류", row, 5);
			}
		}

		if (!hasSelection) {
			JOptionPane.showMessageDialog(this, "항목을 최소 한 개 이상 선택하세요.");
			return;
		}
		serialManager.startSend();
	}

	public void selectAllorRefresh() {
		String currentText = selectAllBtn.getText();
		boolean targetState;

		if (currentText.equals("전체 선택")) {
			targetState = true;
			selectAllBtn.setText("전체 해제");
			selectAllBtn.setForeground(Color.GRAY);
		} else {
			targetState = false;
			selectAllBtn.setText("전체 선택");
			selectAllBtn.setForeground(Color.BLACK);
		}
		int checkBoxColumnIndex = 1;
		int rowCount = meterModel.getRowCount();

		for (int row = 0; row < rowCount; row++) {
			meterModel.setValueAt(targetState, row, checkBoxColumnIndex);
		}
	}

	public void setSendButtonEnabled(boolean enabled) {
		if (sendHexaBtn != null)
			sendHexaBtn.setEnabled(enabled);
		if (sendRawBtn != null)
			sendRawBtn.setEnabled(enabled);
	}

	public void handlePhysicalDisconnect() {
		SwingUtilities.invokeLater(() -> {
			JOptionPane.showMessageDialog(null, "물리 전력 신호 단선 혹은 버스 이탈 상태가 확인되었습니다.\n케이블 플러그 하드웨어를 검증하세요.");
			configPanel.updateConnectionState(false);
		});
	}

	private void startLiveClock() {
		Timer clockTimer = new Timer(1000, e -> {
			liveClockLabel.setText("현재 시간: " + getCurrentTime());
		});
		clockTimer.start();
	}

	private String getCurrentTime() {
		return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
	}

	public static void main(String[] args) {
		com.formdev.flatlaf.FlatLightLaf.setup();
		SwingUtilities.invokeLater(() -> new ModbusTerminal().setVisible(true));
	}
}