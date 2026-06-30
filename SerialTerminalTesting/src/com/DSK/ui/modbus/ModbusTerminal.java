package com.DSK.ui.modbus;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Point;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.model.dto.common.LogDto;
import com.DSK.model.dto.common.MeterRowDto;
import com.DSK.serial.constant.DataType;
import com.DSK.serial.constant.EndianType;
import com.DSK.serial.constant.FunctionCode;
import com.DSK.serial.controller.ModbusController;
import com.DSK.serial.controller.ProfileController;
import com.DSK.serial.converter.ByteConverter;
import com.DSK.serial.converter.ProtocolConverter;
import com.DSK.serial.manager.MapFileManager;
import com.DSK.serial.manager.ModbusManager;
import com.DSK.serial.manager.PendingRequest;
import com.DSK.ui.handler.TableTransferHandler;

public class ModbusTerminal extends JFrame {

	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(ModbusTerminal.class);

	private boolean isConnected = false;
	private volatile boolean isAutoPolling = false;

	private final ModbusManager serialManager;
	private ModbusController controller;

	private MapFileManager fileManager;
	private ProfileController profileController;

	private ModbusConfigPanel configPanel;
	private ModbusAutoPollingPanel autoPollingPanel;
	private ModbusRawHexPanel rawHexControlPanel;
	private ModbusTopBarPanel topBarPanel;

	private JTable meterTable;
	private DefaultTableModel meterModel;

	private JTextArea systemLogArea;
	private JTextArea rxTxArea;

	private final Map<Integer, Integer> rowMap = new HashMap<>();

	private JButton sendHexaBtn;
	private JCheckBox chkSystemLog, chkErrorLog;
	private JButton clearSelectionBtn, selectAllBtn, addRowBtn, btnSaveTerminal;
	private JButton btnSaveMap, btnImportMap, btnDeleteMap, btnNewMap, btnExportMap, btnDeleteSelected;
	private JComboBox<String> mapDropDown;

	private boolean isShowSystemLog = true;
	private boolean isShowErrorLog = true;
	private final int MAX_LOG_COUNT = 2000;
	private final List<LogDto> totalLogList = new java.util.ArrayList<>();

	private boolean isUpdatingAddress = false;
	private boolean hasSelection = false;

	private String currentGlobalValueFormat = "Unsigned";

	private boolean isMapComboResetting = false;

	private final ScheduledExecutorService autoScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "AutoPolling");
		t.setDaemon(true);
		return t;
	});
	private ScheduledFuture<?> autoTaskFuture;
	private int currentCycle = 0;

	private final Color zebraEvenColor = new Color(248, 249, 251);
	private final Color zebraOddColor = Color.WHITE;
	private final Font tableBaseFont = new Font("맑은 고딕", Font.PLAIN, 12);

	// ✅ [수정] JPopupMenu → JDialog 로 변경 (사유는 showBulkColumnEditPopup 주석 참조)
	private JDialog activeBulkEditDialog;

	public ModbusTerminal() {
		serialManager = new ModbusManager(this);
		this.controller = new ModbusController(this, this.serialManager);
		this.configPanel = new ModbusConfigPanel(this, this.controller);

		setTitle("Modbus RTU 시리얼 포트 통합 테스팅 매니저");
		setSize(1250, 850);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout(12, 12));
		getContentPane().setBackground(Color.WHITE);

		initUI();

		getContentPane().setPreferredSize(new Dimension(1200, 800));
		pack();
	}

	private void initUI() {

		DefaultTableCellRenderer rowColorRenderer = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				if (c instanceof JLabel)
					((JLabel) c).setHorizontalAlignment(JLabel.CENTER);
				if (isSelected) {
					c.setBackground(table.getSelectionBackground());
					c.setForeground(table.getSelectionForeground());
				} else {
					c.setBackground(row % 2 == 0 ? zebraEvenColor : zebraOddColor);
					c.setForeground(Color.BLACK);
				}
				return c;
			}
		};

		Font boldTitleFont = new Font("맑은 고딕", Font.BOLD, 12);
		Color softBorderColor = new Color(225, 230, 235);

		((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		((JPanel) getContentPane()).setBackground(Color.WHITE);

		final int slimWidth = 450;
		final int contentWidth = slimWidth - 25;

		JPanel leftPanel = new JPanel() {
			private static final long serialVersionUID = 1L;

			@Override
			public Dimension getPreferredSize() {
				Dimension d = super.getPreferredSize();
				return new Dimension(Math.min(d.width, slimWidth), d.height);
			}
		};
		leftPanel.setLayout(new BoxLayout(leftPanel, BoxLayout.Y_AXIS));
		leftPanel.setBackground(Color.WHITE);
		leftPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
		leftPanel.setMaximumSize(new Dimension(slimWidth, Integer.MAX_VALUE));

		configPanel.setBackground(Color.WHITE);
		configPanel.setMaximumSize(new Dimension(contentWidth, 210));
		configPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		leftPanel.add(configPanel);
		leftPanel.add(Box.createVerticalStrut(6));

		JPanel mapManagementPanel = new JPanel();
		mapManagementPanel.setLayout(new BoxLayout(mapManagementPanel, BoxLayout.Y_AXIS));
		mapManagementPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(softBorderColor),
				"검침 맵 테이블", TitledBorder.LEFT, TitledBorder.TOP, boldTitleFont));
		mapManagementPanel.setBackground(Color.WHITE);
		mapManagementPanel.setMaximumSize(new Dimension(contentWidth, 55));

		JPanel mapRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		mapRow.setBackground(Color.WHITE);

		int comboWidth = contentWidth - 200;
		mapDropDown = new JComboBox<>();
		mapDropDown.setPreferredSize(new Dimension(comboWidth, 24));

		btnDeleteMap = new JButton("삭제");
		btnImportMap = new JButton("파일 가져오기");
		btnImportMap.setPreferredSize(new Dimension(95, 24));
		btnDeleteMap.setPreferredSize(new Dimension(65, 24));

		for (JButton btn : new JButton[] { btnDeleteMap, btnImportMap }) {
			btn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
			btn.setBackground(Color.WHITE);
			btn.setForeground(Color.BLACK);
		}

		mapRow.add(mapDropDown);
		mapRow.add(btnImportMap);
		mapRow.add(btnDeleteMap);

		mapManagementPanel.add(mapRow);
		leftPanel.add(mapManagementPanel);
		leftPanel.add(Box.createVerticalStrut(6));

		this.rawHexControlPanel = new ModbusRawHexPanel(contentWidth, boldTitleFont, softBorderColor);
		leftPanel.add(rawHexControlPanel);
		leftPanel.add(Box.createVerticalStrut(6));

		autoPollingPanel = new ModbusAutoPollingPanel(this, serialManager);
		autoPollingPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
		autoPollingPanel.setMaximumSize(new Dimension(contentWidth, autoPollingPanel.getPreferredSize().height));
		leftPanel.add(autoPollingPanel);
		leftPanel.add(Box.createVerticalStrut(6));
		leftPanel.add(Box.createVerticalGlue());

		JScrollPane leftScrollContainer = new JScrollPane(leftPanel);
		leftScrollContainer.setPreferredSize(new Dimension(slimWidth, 0));
		leftScrollContainer.setMinimumSize(new Dimension(slimWidth, 0));
		leftScrollContainer.setMaximumSize(new Dimension(slimWidth, Short.MAX_VALUE));
		leftScrollContainer.setBorder(null);
		leftScrollContainer.setBackground(Color.WHITE);
		leftScrollContainer.getViewport().setBackground(Color.WHITE);
		leftScrollContainer.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		leftScrollContainer.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);

		JPanel rightPanel = new JPanel(new BorderLayout(0, 10));
		rightPanel.setBackground(Color.WHITE);

		Object[] columns = { "No.", "Check", "Slave Id", "Address", "Name", "FC", "Data Type", "Endian", "Value",
				"Unit", "State", "Scale", "DTO" };

		meterModel = new DefaultTableModel(columns, 0) {
			@Override
			public Class<?> getColumnClass(int col) {
				switch (col) {
				case 1:
					return Boolean.class;
				case 5:
					return FunctionCode.class;
				case 6:
					return DataType.class;
				case 7:
					return EndianType.class;
				default:
					return String.class;
				}
			}

			@Override
			public boolean isCellEditable(int row, int col) {
				if (isAutoPolling)
					return false;
				return col != 0 && col != 10 && col != 12;
			}
		};

		meterTable = new JTable(meterModel);

		JTextField addressField = new JTextField();
		addressField.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		meterTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(addressField));

		fileManager = new MapFileManager();
		profileController = new ProfileController(this, fileManager, meterModel, rowMap);

		JPopupMenu globalValueFormatMenu = new JPopupMenu();
		String[] formatOptions = { "Signed", "Unsigned", "32-bit Signed", "32-bit Unsigned", "64-bit Signed",
				"64-bit Unsigned", "32-bit Float", "64-bit Double" };
		for (String option : formatOptions) {
			JMenuItem item = new JMenuItem(option);
			item.addActionListener(e -> {
				this.currentGlobalValueFormat = option;
				for (int i = 0; i < meterModel.getRowCount(); i++) {
					Object cur = meterModel.getValueAt(i, 8);
					if (cur != null && !cur.toString().equals("-"))
						meterModel.setValueAt(reformatRawValue(cur.toString(), option), i, 8);
				}
			});
			globalValueFormatMenu.add(item);
		}

		JTableHeader header = meterTable.getTableHeader();
		header.addMouseListener(new java.awt.event.MouseAdapter() {
			private void tryShow(java.awt.event.MouseEvent e) {
				if (!e.isPopupTrigger())
					return;
				int viewCol = header.columnAtPoint(e.getPoint());
				if (viewCol < 0)
					return;
				int modelCol = meterTable.convertColumnIndexToModel(viewCol);

				if (modelCol == 8) {
					globalValueFormatMenu.show(header, e.getX(), e.getY());
				} else if (modelCol == 2 || modelCol == 5 || modelCol == 6 || modelCol == 7) {
					showBulkColumnEditPopup(modelCol, header, e.getX(), e.getY());
				}
			}

			@Override
			public void mousePressed(java.awt.event.MouseEvent e) {
				tryShow(e);
			}

			@Override
			public void mouseReleased(java.awt.event.MouseEvent e) {
				tryShow(e);
			}
		});

		meterTable.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		meterTable.setBackground(Color.WHITE);
		meterTable.setFillsViewportHeight(true);
		meterTable.setForeground(Color.BLACK);
		meterTable.setGridColor(new Color(240, 242, 245));
		meterTable.setRowHeight(24);
		meterTable.getTableHeader().setReorderingAllowed(false);

		meterTable.setFont(tableBaseFont);
		meterTable.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 12));
		meterTable.getTableHeader().setBackground(new Color(245, 247, 250));
		meterTable.getTableHeader().setForeground(new Color(60, 65, 75));

		meterTable.getColumnModel().getColumn(0).setPreferredWidth(40);
		meterTable.getColumnModel().getColumn(1).setPreferredWidth(40);
		meterTable.getColumnModel().getColumn(2).setPreferredWidth(60);
		meterTable.getColumnModel().getColumn(3).setPreferredWidth(80);
		meterTable.getColumnModel().getColumn(4).setPreferredWidth(140);
		meterTable.getColumnModel().getColumn(5).setPreferredWidth(110);
		meterTable.getColumnModel().getColumn(6).setPreferredWidth(85);
		meterTable.getColumnModel().getColumn(7).setPreferredWidth(160);
		meterTable.getColumnModel().getColumn(8).setPreferredWidth(80);
		meterTable.getColumnModel().getColumn(9).setPreferredWidth(60);
		meterTable.getColumnModel().getColumn(10).setPreferredWidth(80);
		meterTable.getColumnModel().getColumn(11).setPreferredWidth(60);

		meterTable.getColumnModel().getColumn(5)
				.setCellEditor(new DefaultCellEditor(new JComboBox<>(FunctionCode.values())));
		meterTable.getColumnModel().getColumn(6)
				.setCellEditor(new DefaultCellEditor(new JComboBox<>(DataType.values())));
		meterTable.getColumnModel().getColumn(7)
				.setCellEditor(new DefaultCellEditor(new JComboBox<>(EndianType.values())));

		DefaultTableCellRenderer stateRenderer = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				((JLabel) c).setHorizontalAlignment(JLabel.CENTER);
				c.setFont(new Font("맑은 고딕", Font.BOLD, 12));
				if (!isSelected) {
					c.setBackground(row % 2 == 0 ? zebraEvenColor : zebraOddColor);
					String state = value == null ? "" : value.toString();
					switch (state) {
					case "정상":
						c.setForeground(new Color(40, 167, 69));
						break;
					case "오류":
					case "타임아웃":
					case "CRC오류":
					case "장비거부":
					case "데이터오류":
						c.setForeground(new Color(200, 35, 51));
						break;
					case "요청중":
						c.setForeground(new Color(0, 110, 200));
						break;
					default:
						c.setForeground(new Color(130, 140, 150));
						break;
					}
				}
				return c;
			}
		};
		// =========================================================================
		// 각 칼럼 CSS 속성 접근
		// =========================================================================
		meterTable.getColumnModel().getColumn(10).setCellRenderer(stateRenderer);

		DefaultTableCellRenderer valueRenderer = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);

				((JLabel) c).setHorizontalAlignment(JLabel.CENTER);

				c.setFont(new Font("Consolas", Font.BOLD, 12));
				if (!isSelected) {
					c.setBackground(row % 2 == 0 ? zebraEvenColor : zebraOddColor);
					c.setForeground(Color.BLACK);
				}
				return c;
			}
		};
		meterTable.getColumnModel().getColumn(8).setCellRenderer(valueRenderer);

		// [각 행 DTO 들어가 있는 열 숨김]
		TableColumn dtoColumn = meterTable.getColumnModel().getColumn(12);
		dtoColumn.setMinWidth(0);
		dtoColumn.setMaxWidth(0);
		dtoColumn.setPreferredWidth(0);
		dtoColumn.setResizable(false);

		JScrollPane meterScroll = new JScrollPane(meterTable);
		meterScroll.getViewport().setBackground(Color.WHITE);
		meterScroll.setBorder(BorderFactory.createLineBorder(softBorderColor));
		rightPanel.add(meterScroll, BorderLayout.CENTER);

		JTabbedPane logTab = new JTabbedPane();
		logTab.setBackground(Color.WHITE);
		logTab.setForeground(Color.BLACK);

		JPanel systemLogTabPanel = new JPanel(new BorderLayout(5, 0));
		systemLogTabPanel.setBackground(Color.WHITE);
		systemLogArea = new JTextArea();
		systemLogArea.setEditable(false);
		systemLogArea.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		JScrollPane systemLogScroll = new JScrollPane(systemLogArea);
		systemLogScroll.getViewport().setBackground(Color.WHITE);
		systemLogScroll.setBorder(BorderFactory.createLineBorder(new Color(240, 240, 240)));

		JPanel sysRightControlPanel = new JPanel(new GridLayout(2, 1, 0, 6));
		sysRightControlPanel.setBackground(Color.WHITE);
		sysRightControlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		Dimension rightBtnSize = new Dimension(160, 80);

		sendHexaBtn = new JButton("체크 항목 검침 요청");
		sendHexaBtn.setEnabled(false);
		JButton viewHistoryBtn = new JButton("오류 이력 조회");
		for (JButton btn : new JButton[] { sendHexaBtn, viewHistoryBtn }) {
			btn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
			btn.setBackground(Color.WHITE);
			btn.setForeground(Color.BLACK);
			btn.setPreferredSize(rightBtnSize);
		}
		sysRightControlPanel.add(sendHexaBtn);
		sysRightControlPanel.add(viewHistoryBtn);
		systemLogTabPanel.add(systemLogScroll, BorderLayout.CENTER);
		systemLogTabPanel.add(sysRightControlPanel, BorderLayout.EAST);

		JPanel rxTxTabPanel = new JPanel(new BorderLayout(5, 0));
		rxTxTabPanel.setBackground(Color.WHITE);
		rxTxArea = new JTextArea();
		rxTxArea.setEditable(false);
		rxTxArea.setFont(new Font("Consolas", Font.PLAIN, 12));
		rxTxArea.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		JScrollPane rxTxScroll = new JScrollPane(rxTxArea);
		rxTxScroll.getViewport().setBackground(Color.WHITE);
		rxTxScroll.setBorder(BorderFactory.createLineBorder(new Color(240, 240, 240)));

		JPanel rxTxRightControlPanel = new JPanel(new GridLayout(2, 1, 0, 6));
		rxTxRightControlPanel.setBackground(Color.WHITE);
		rxTxRightControlPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

		JButton sendHexaBtnShared = new JButton("체크 항목 검침 요청");
		JButton viewHistoryBtnShared = new JButton("오류 이력 조회");
		sendHexaBtnShared.setEnabled(false);
		for (JButton btn : new JButton[] { sendHexaBtnShared, viewHistoryBtnShared }) {
			btn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
			btn.setBackground(Color.WHITE);
			btn.setForeground(Color.BLACK);
			btn.setPreferredSize(rightBtnSize);
		}
		rxTxRightControlPanel.add(sendHexaBtnShared);
		rxTxRightControlPanel.add(viewHistoryBtnShared);
		rxTxTabPanel.add(rxTxScroll, BorderLayout.CENTER);
		rxTxTabPanel.add(rxTxRightControlPanel, BorderLayout.EAST);

		logTab.addTab("시스템 상태 로그", systemLogTabPanel);
		logTab.addTab("RX / TX 바이트 터미널", rxTxTabPanel);

		CardLayout filterCardLayout = new CardLayout();
		JPanel globalFilterCardPanel = new JPanel(filterCardLayout);
		globalFilterCardPanel.setBackground(Color.WHITE);

		JPanel sysFilterLine = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		sysFilterLine.setBackground(Color.WHITE);
		chkSystemLog = new JCheckBox("시스템 로그", true);
		chkErrorLog = new JCheckBox("에러 로그", true);
		JButton clearSystemLogBtn = new JButton("로그 비우기");
		clearSystemLogBtn.setPreferredSize(new Dimension(120, 23));
		clearSystemLogBtn.setBackground(Color.WHITE);
		clearSystemLogBtn.setForeground(Color.BLACK);
		for (JCheckBox chk : new JCheckBox[] { chkSystemLog, chkErrorLog }) {
			chk.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
			chk.setBackground(Color.WHITE);
		}
		sysFilterLine.add(chkSystemLog);
		sysFilterLine.add(chkErrorLog);
		sysFilterLine.add(clearSystemLogBtn);

		JPanel rxTxFilterLine = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		rxTxFilterLine.setBackground(Color.WHITE);
		btnSaveTerminal = new JButton("TX / RX 로그 저장");
		JButton clearRxTxBtn = new JButton("터미널 비우기");
		for (JButton btn : new JButton[] { btnSaveTerminal, clearRxTxBtn }) {
			btn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
			btn.setBackground(Color.WHITE);
			btn.setForeground(Color.BLACK);
			btn.setPreferredSize(new Dimension(120, 23));
		}
		rxTxFilterLine.add(Box.createHorizontalStrut(2));
		rxTxFilterLine.add(btnSaveTerminal);
		rxTxFilterLine.add(clearRxTxBtn);

		globalFilterCardPanel.add(sysFilterLine, "SYS");
		globalFilterCardPanel.add(rxTxFilterLine, "RXTX");

		logTab.addChangeListener(
				e -> filterCardLayout.show(globalFilterCardPanel, logTab.getSelectedIndex() == 0 ? "SYS" : "RXTX"));

		JPanel mainLogContainer = new JPanel(null) {
			@Override
			public void doLayout() {
				logTab.setBounds(0, 0, getWidth(), getHeight());
				int fw = globalFilterCardPanel.getPreferredSize().width;
				int fh = globalFilterCardPanel.getPreferredSize().height;
				globalFilterCardPanel.setBounds(getWidth() - fw - 175, 5, fw, fh);
				super.doLayout();
			}
		};
		mainLogContainer.setBackground(Color.WHITE);
		mainLogContainer.add(globalFilterCardPanel);
		mainLogContainer.add(logTab);
		mainLogContainer.setPreferredSize(new Dimension(0, 230));
		rightPanel.add(mainLogContainer, BorderLayout.SOUTH);

		topBarPanel = new ModbusTopBarPanel();

		this.addRowBtn = topBarPanel.getAddRowBtn();
		this.clearSelectionBtn = topBarPanel.getClearSelectionBtn();
		this.selectAllBtn = topBarPanel.getSelectAllBtn();
		this.btnDeleteSelected = topBarPanel.getBtnDeleteSelected();

		final JTextField txtStartAddr = topBarPanel.getTxtStartAddr();
		final JTextField txtSlaveId = topBarPanel.getTxtSlaveId();
		final JRadioButton rdoHex = topBarPanel.getRdoHex();
		//		final JRadioButton rdoDec = topBarPanel.getRdoDec();
		final JComboBox<DataType> defaultDataTypeDropDown = topBarPanel.getDefaultDataTypeDropDown();
		final JComboBox<EndianType> defaultEndianDropDown = topBarPanel.getDefaultEndianDropDown();
		final JComboBox<String> mapFileCombo = topBarPanel.getMapFileCombo();

		btnSaveMap = new JButton("저장");
		btnNewMap = new JButton("새 파일");
		btnExportMap = new JButton("파일 내보내기");

		add(topBarPanel, BorderLayout.NORTH);

		JPanel masterContentPanel = new JPanel(new GridBagLayout());
		masterContentPanel.setBackground(Color.WHITE);
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.fill = GridBagConstraints.BOTH;

		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.0;
		gbc.weighty = 1.0;
		gbc.insets = new Insets(0, 0, 0, 10);
		masterContentPanel.add(leftScrollContainer, gbc);

		gbc.gridx = 1;
		gbc.gridy = 0;
		gbc.weightx = 1.0;
		gbc.weighty = 1.0;
		gbc.insets = new Insets(0, 0, 0, 0);
		masterContentPanel.add(rightPanel, gbc);

		JScrollPane globalMainScrollPane = new JScrollPane(masterContentPanel);
		globalMainScrollPane.setBorder(null);
		globalMainScrollPane.setBackground(Color.WHITE);
		globalMainScrollPane.getViewport().setBackground(Color.WHITE);
		globalMainScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		globalMainScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(globalMainScrollPane, BorderLayout.CENTER);

		meterTable.setDragEnabled(true);
		meterTable.setDropMode(javax.swing.DropMode.INSERT_ROWS);
		meterTable.setTransferHandler(new TableTransferHandler(this, meterModel, rowMap));
		meterTable.setDefaultRenderer(Object.class, rowColorRenderer);
		meterTable.setDefaultRenderer(Number.class, rowColorRenderer);

		chkSystemLog.addActionListener(e -> {
			isShowSystemLog = chkSystemLog.isSelected();
			refreshSystemLogView();
		});
		chkErrorLog.addActionListener(e -> {
			isShowErrorLog = chkErrorLog.isSelected();
			refreshSystemLogView();
		});
		clearSystemLogBtn.addActionListener(e -> {
			totalLogList.clear();
			systemLogArea.setText("");
		});
		clearRxTxBtn.addActionListener(e -> {
			if (JOptionPane.showConfirmDialog(this, "현재까지 기록된 로그 삭제 하시겠습니까?", "TX / RX 로그 터미널 삭제",
					JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION)
				rxTxArea.setText("");
		});
		btnSaveTerminal.addActionListener(e -> saveTerminalLog());

		viewHistoryBtn.addActionListener(e -> autoPollingPanel.refreshErrorRankingDialog());
		viewHistoryBtnShared.addActionListener(e -> autoPollingPanel.refreshErrorRankingDialog());

		meterTable.addKeyListener(new java.awt.event.KeyAdapter() {
			@Override
			public void keyPressed(java.awt.event.KeyEvent e) {
				if (!meterTable.isEditing())
					return;
				int key = e.getKeyCode();
				if (key == java.awt.event.KeyEvent.VK_ENTER) {
					meterTable.getCellEditor().stopCellEditing();
					e.consume();
				} else if (key == java.awt.event.KeyEvent.VK_ESCAPE) {
					meterTable.getCellEditor().cancelCellEditing();
					e.consume();
				}
			}
		});

		btnDeleteSelected.addActionListener(e -> {
			if (isAutoPolling) {
				JOptionPane.showMessageDialog(this, "자동 검침 중에는 행을 삭제할 수 없습니다.");
				return;
			}
			List<Integer> checkedRows = new java.util.ArrayList<>();
			for (int i = 0; i < meterModel.getRowCount(); i++)
				if (Boolean.TRUE.equals(meterModel.getValueAt(i, 1)))
					checkedRows.add(i);
			if (checkedRows.isEmpty()) {
				JOptionPane.showMessageDialog(this, "체크박스에 선택된 행이 없습니다.");
				return;
			}
			if (JOptionPane.showConfirmDialog(this, checkedRows.size() + "개의 체크된 행을 삭제하시겠습니까?", "행 삭제 확인",
					JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
				return;

			for (int i = checkedRows.size() - 1; i >= 0; i--) {
				int targetRow = checkedRows.get(i);
				MeterRowDto dto = (MeterRowDto) meterModel.getValueAt(targetRow, 12);
				if (dto != null)
					rowMap.remove(dto.getAddress());
				meterModel.removeRow(targetRow);
			}
			rowMap.clear();
			for (int i = 0; i < meterModel.getRowCount(); i++) {
				MeterRowDto dto = (MeterRowDto) meterModel.getValueAt(i, 12);
				if (dto != null)
					rowMap.put(dto.getAddress(), i);
				meterModel.setValueAt(i + 1, i, 0);
			}
			selectAllorRefresh(hasSelection);
		});

		btnNewMap.addActionListener(e -> profileController.createNewProfile());
		btnSaveMap.addActionListener(e -> profileController.saveProfile());
		btnDeleteMap.addActionListener(e -> {
			profileController.deleteProfile((String) mapDropDown.getSelectedItem());
			refreshMapDropDown();
		});
		btnImportMap.addActionListener(e -> profileController.importProfileFromDisk());
		btnExportMap.addActionListener(e -> profileController.exportProfileFromTable());
		mapDropDown.addActionListener(e -> {
			String selected = (String) mapDropDown.getSelectedItem();
			if (selected != null && !selected.trim().isEmpty())
				profileController.loadProfile(selected);
		});

		mapFileCombo.addActionListener(e -> {
			// 코드로 인덱스를 0으로 되돌리는 중이라면 로직을 타지 않고 탈출
			if (isMapComboResetting) {
				return;
			}

			int selectedIndex = mapFileCombo.getSelectedIndex();
			switch (selectedIndex) {
			case 0:
				btnSaveMap.doClick();
				break;
			case 1:
				promptAndSaveProfileAs();
				resetComboToZero(); // 액션 완료 후 0번으로 리셋
				break;
			case 2:
				btnNewMap.doClick();
				resetComboToZero(); // 액션 완료 후 0번으로 리셋
				break;
			case 3:
				btnExportMap.doClick();
				resetComboToZero(); // 액션 완료 후 0번으로 리셋
				break;
			default:
				break;
			}
		});

		addRowBtn.addActionListener(e -> {
			if (isAutoPolling) {
				JOptionPane.showMessageDialog(this, "자동 검침 중에는 행을 추가할 수 없습니다.");
				return;
			}

			String rawInput = txtStartAddr.getText().trim();
			int slaveId;
			try {
				slaveId = Integer.parseInt(txtSlaveId.getText().trim());
			} catch (NumberFormatException nfe) {
				JOptionPane.showMessageDialog(this, "Slave Id 는 정수만 입력 가능합니다.", "입력 오류", JOptionPane.WARNING_MESSAGE);
				return;
			}
			int parsedAddress = 0x0000;

			DataType targetDataType = (DataType) defaultDataTypeDropDown.getSelectedItem();
			EndianType targetEndianType = (EndianType) defaultEndianDropDown.getSelectedItem();
			if (targetDataType == null)
				targetDataType = DataType.INT16;
			if (targetEndianType == null)
				targetEndianType = EndianType.LITTLE_ENDIAN;

			if (meterModel.getRowCount() == 0) {
				try {
					String addrText = rawInput.isEmpty() ? "0" : rawInput;
					if (rdoHex.isSelected()) {
						if (addrText.toLowerCase().startsWith("0x"))
							addrText = addrText.substring(2);
						parsedAddress = Integer.parseInt(addrText, 16);
					} else {
						if (addrText.toLowerCase().startsWith("0x"))
							throw new NumberFormatException();
						parsedAddress = Integer.parseInt(addrText, 10);
					}
				} catch (NumberFormatException ex) {
					JOptionPane.showMessageDialog(this, "입력된 주소가 올바른 형식이 아닙니다.", "파싱 오류", JOptionPane.ERROR_MESSAGE);
					return;
				}
			} else {
				if (meterTable.isEditing())
					meterTable.getCellEditor().stopCellEditing();
				int lastRowIdx = meterModel.getRowCount() - 1;
				MeterRowDto lastDto = (MeterRowDto) meterModel.getValueAt(lastRowIdx, 12);
				if (lastDto != null) {
					Object tObj = meterModel.getValueAt(lastRowIdx, 6);
					DataType lastType = (tObj instanceof DataType) ? (DataType) tObj
							: DataType.valueOf(tObj.toString());
					parsedAddress = lastDto.getAddress() + getRegisterIncrement(lastType);
				}
			}

			if (rowMap.containsKey(parsedAddress)) {
				if (JOptionPane.showConfirmDialog(this, String.format("이미 등록된 주소(0x%04X)입니다. 추가하시겠습니까?", parsedAddress),
						"주소 중복", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION)
					return;
			}

			int defaultSlaveId = slaveId;
			MeterRowDto newDto = new MeterRowDto(parsedAddress, defaultSlaveId, "새 검침 항목",
					FunctionCode.READ_INPUT_REGISTERS, targetDataType, targetEndianType, "V", 1.0);

			int nextRowNum = meterModel.getRowCount() + 1;
			String displayAddress = rdoHex.isSelected() ? String.format("0x%04X", newDto.getAddress())
					: String.valueOf(newDto.getAddress());

			meterModel.addRow(new Object[] { nextRowNum, newDto.isSelected(), newDto.getSlaveId(), displayAddress,
					newDto.getItemName(), newDto.getFunctionCode(), newDto.getDataType(), newDto.getEndianType(), "-",
					newDto.getUnit(), "대기", newDto.getScale(), newDto });
			rowMap.put(parsedAddress, nextRowNum - 1);
			selectAllorRefresh(hasSelection);

			int nextPrediction = parsedAddress + getRegisterIncrement(targetDataType);
			txtStartAddr.setText(
					rdoHex.isSelected() ? String.format("0x%04X", nextPrediction) : String.valueOf(nextPrediction));
		});

		clearSelectionBtn.addActionListener(e -> {
			if (isAutoPolling) {
				JOptionPane.showMessageDialog(this, "자동 검침 중에는 값을 초기화할 수 없습니다.");
				return;
			}
			resetAllMeterTable();
		});
		selectAllBtn.addActionListener(e -> {
			if (isAutoPolling) {
				JOptionPane.showMessageDialog(this, "자동 검침 중에는 선택 상태를 변경할 수 없습니다.");
				return;
			}
			selectAllorRefresh(true);
		});
		sendHexaBtn.addActionListener(e -> sendSelectedData());
		sendHexaBtnShared.addActionListener(e -> sendSelectedData());

		sendHexaBtn.addPropertyChangeListener("enabled", e -> sendHexaBtnShared.setEnabled((Boolean) e.getNewValue()));

		rawHexControlPanel.getSendRawBtn().addActionListener(e -> {
			rawHexControlPanel.getRawResponseArea().setText("");
			sendRawData();
		});
		rawHexControlPanel.getRawHexField().addActionListener(e -> {
			rawHexControlPanel.getRawResponseArea().setText("");
			sendRawData();
		});
		rawHexControlPanel.getClearRawBtn().addActionListener(e -> clearRaw());
		rawHexControlPanel.getRawHexField().getDocument().addDocumentListener(new javax.swing.event.DocumentListener() {
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
		rawHexControlPanel.getCrcAutoCheck().addActionListener(e -> updateCrcPreview());

		meterModel.addTableModelListener(e -> {
			if (isUpdatingAddress)
				return;
			if (e.getType() != javax.swing.event.TableModelEvent.UPDATE)
				return;

			int row = e.getFirstRow();
			int col = e.getColumn();
			if (row < 0 || col < 0)
				return;

			MeterRowDto dto = (MeterRowDto) meterModel.getValueAt(row, 12);
			if (dto == null) {
				log.error("DTO 없음 – row={}", row);
				return;
			}
			Object val = meterModel.getValueAt(row, col);
			if (val == null)
				return;

			try {
				switch (col) {
				case 1:
					dto.setSelected((Boolean) val);
					selectAllorRefresh(false);
					break;

				case 2:
					try {
						dto.setSlaveId(Integer.parseInt(val.toString().trim()));
					} catch (NumberFormatException nfe) {
						log.warn("SlaveId 파싱 실패: {}", val);
						isUpdatingAddress = true;
						meterModel.setValueAt(String.valueOf(dto.getSlaveId()), row, col);
						isUpdatingAddress = false;
					}
					break;

				case 3:
					String input = val.toString().trim();
					int newAddr;
					if (input.startsWith("0x") || input.startsWith("0X")) {
						String hex = input.substring(2).trim();
						newAddr = hex.isEmpty() ? 0 : Integer.parseInt(hex, 16);
					} else {
						newAddr = Integer.parseInt(input);
					}
					if (newAddr < 0 || newAddr > 65535) {
						JOptionPane.showMessageDialog(this, "모드버스 주소 범위를 초과했습니다. (0 ~ 65535)");
						isUpdatingAddress = true;
						meterModel.setValueAt(String.format("0x%04X", dto.getAddress()), row, col);
						isUpdatingAddress = false;
						return;
					}
					rowMap.remove(dto.getAddress());
					dto.setAddress(newAddr);
					rowMap.put(newAddr, row);
					isUpdatingAddress = true;
					meterModel.setValueAt(String.format("0x%04X", newAddr), row, col);
					isUpdatingAddress = false;
					break;

				case 4:
					dto.setItemName(val.toString());
					break;

				case 5:
					dto.setFunctionCode(
							val instanceof FunctionCode ? (FunctionCode) val : FunctionCode.valueOf(val.toString()));
					break;

				case 6:
					dto.setDataType(val instanceof DataType ? (DataType) val : DataType.valueOf(val.toString()));
					break;

				case 7:
					dto.setEndianType(
							val instanceof EndianType ? (EndianType) val : EndianType.valueOf(val.toString()));
					break;

				case 9:
					dto.setUnit(val.toString());
					break;

				case 11:
					try {
						double scale = Double.parseDouble(val.toString().trim());
						if (scale == 0.0) {
							log.warn("Scale 0 불허 – 1.0 으로 복구");
							scale = 1.0;
							isUpdatingAddress = true;
							meterModel.setValueAt("1.0", row, col);
							isUpdatingAddress = false;
						}
						dto.setScale(scale);
					} catch (NumberFormatException nfe) {
						log.error("Scale 파싱 실패: {}", val);
						isUpdatingAddress = true;
						meterModel.setValueAt(String.valueOf(dto.getScale()), row, col);
						isUpdatingAddress = false;
					}
					break;
				}
			} catch (Exception ex) {
				log.error("셀 동기화 예외 – row={}, col={}", row, col, ex);
			}
		});

		refreshMapDropDown();
	}

	private void showBulkColumnEditPopup(int column, Component invoker, int x, int y) {
		if (isAutoPolling) {
			JOptionPane.showMessageDialog(this, "자동 검침 중에는 칼럼 값을 변경할 수 없습니다.", "변경 불가", JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (meterModel.getRowCount() == 0)
			return;

		if (activeBulkEditDialog != null && activeBulkEditDialog.isVisible()) {
			activeBulkEditDialog.dispose();
		}

		JDialog dialog = new JDialog(SwingUtilities.getWindowAncestor(this));
		dialog.setUndecorated(true);
		dialog.setModal(false);

		JPanel panel = new JPanel(new BorderLayout(6, 4));
		panel.setBackground(Color.WHITE);
		panel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(190, 195, 205), 1),
				BorderFactory.createEmptyBorder(6, 8, 6, 8)));

		JLabel title = new JLabel(getBulkEditTitle(column));
		title.setFont(new Font("맑은 고딕", Font.BOLD, 11));

		JComponent editor;
		Runnable applyAction;

		switch (column) {
		case 2: {
			JTextField field = new JTextField("1", 6);
			field.setHorizontalAlignment(JTextField.CENTER);
			editor = field;
			applyAction = () -> {
				try {
					int slaveId = Integer.parseInt(field.getText().trim());
					applyBulkColumnValue(2, slaveId);
				} catch (NumberFormatException nfe) {
					JOptionPane.showMessageDialog(this, "정수만 입력하세요.", "입력 오류", JOptionPane.WARNING_MESSAGE);
				}
			};
			break;
		}
		case 5: {
			JComboBox<FunctionCode> combo = new JComboBox<>(FunctionCode.values());
			editor = combo;
			applyAction = () -> applyBulkColumnValue(5, combo.getSelectedItem());
			break;
		}
		case 6: {
			JComboBox<DataType> combo = new JComboBox<>(DataType.values());
			editor = combo;
			applyAction = () -> applyBulkColumnValue(6, combo.getSelectedItem());
			break;
		}
		case 7: {
			JComboBox<EndianType> combo = new JComboBox<>(EndianType.values());
			editor = combo;
			applyAction = () -> applyBulkColumnValue(7, combo.getSelectedItem());
			break;
		}
		default:
			return;
		}

		JButton applyBtn = new JButton("전체 적용");
		applyBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		applyBtn.addActionListener(ev -> {
			applyAction.run();
			dialog.dispose();
		});

		JPanel editorRow = new JPanel(new BorderLayout(4, 0));
		editorRow.setBackground(Color.WHITE);
		editorRow.add(editor, BorderLayout.CENTER);
		editorRow.add(applyBtn, BorderLayout.EAST);

		panel.add(title, BorderLayout.NORTH);
		panel.add(editorRow, BorderLayout.CENTER);

		dialog.setContentPane(panel);
		dialog.pack();

		Point screenPoint = invoker.getLocationOnScreen();
		dialog.setLocation(screenPoint.x + x, screenPoint.y + y);

		dialog.addWindowFocusListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowLostFocus(java.awt.event.WindowEvent e) {
				dialog.dispose();
			}
		});

		activeBulkEditDialog = dialog;
		dialog.setVisible(true);
	}

	private String getBulkEditTitle(int column) {
		switch (column) {
		case 2:
			return "전체 행 Slave Id 일괄 변경";
		case 5:
			return "전체 행 Function Code 일괄 변경";
		case 6:
			return "전체 행 Data Type 일괄 변경";
		case 7:
			return "전체 행 Endian 일괄 변경";
		default:
			return "";
		}
	}

	private void applyBulkColumnValue(int column, Object value) {
		int rowCount = meterModel.getRowCount();
		for (int row = 0; row < rowCount; row++) {
			meterModel.setValueAt(value, row, column);
		}
		log.info("칼럼 {} 일괄 변경 완료 – 적용값: {}, 대상 행: {}", column, value, rowCount);
	}

	private void promptAndSaveProfileAs() {
		String newName = JOptionPane.showInputDialog(this, "저장할 맵 이름을 입력하세요:", "다른 이름으로 저장", JOptionPane.PLAIN_MESSAGE);
		if (newName == null)
			return;
		String trimmed = newName.trim();
		if (trimmed.isEmpty()) {
			JOptionPane.showMessageDialog(this, "이름을 입력해야 합니다.", "입력 오류", JOptionPane.WARNING_MESSAGE);
			return;
		}

		try {
			List<MeterRowDto> dtoList = new java.util.ArrayList<>();
			for (int row = 0; row < meterModel.getRowCount(); row++) {
				MeterRowDto dto = (MeterRowDto) meterModel.getValueAt(row, 12);
				if (dto != null)
					dtoList.add(dto);
			}

			fileManager.saveProfile(trimmed, dtoList);

			refreshMapDropDown();
			mapDropDown.setSelectedItem(trimmed);

			appendSystemLog(new LogDto("SYSTEM", "맵 프로필 '" + trimmed + "' 으로 저장 완료"));
			log.info("다른 이름으로 저장 완료: {}", trimmed);

		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "저장 중 오류가 발생했습니다: " + ex.getMessage(), "저장 오류",
					JOptionPane.ERROR_MESSAGE);
			log.error("다른 이름으로 저장 실패", ex);
		}
	}

	public void sendSelectedData() {
		if (!controller.isConnected()) {
			JOptionPane.showMessageDialog(this, "활성 개방 상태인 컴포트 대상이 확인되지 않습니다.");
			return;
		}
		//		resetAllMeterTable();
		if (!enqueueSelectedMeters()) {
			JOptionPane.showMessageDialog(this, "검침 항목을 최소 한 개 이상 선택하세요.");
			return;
		}
		serialManager.startSend();
	}

	public void sendSelectedDataAuto(int scanRate, int targetCycle, int retryCount) {
		if (!controller.isConnected()) {
			JOptionPane.showMessageDialog(this, "활성 개방 상태인 컴포트 대상이 확인되지 않습니다.");
			return;
		}
		stopAutoPolling();
		currentCycle = 0;
		serialManager.setRetryCount(retryCount);

		if (meterTable.isEditing()) {
			meterTable.getCellEditor().stopCellEditing();
		}
		isAutoPolling = true;
		setRowControlButtonsEnabled(false);

		String startMsg = String.format("자동 검침 시작 – 주기:%dms, 목표:%d회, 재시도:%d", scanRate, targetCycle, retryCount);
		log.info(startMsg);
		appendSystemLog(new LogDto("SYSTEM", startMsg));

		Runnable pollingTask = () -> {
			try {
				if (!serialManager.isCycleComplete()) {
					log.warn("이전 사이클 처리 중 – 현재 주기 스킵");
					return;
				}
				if (targetCycle > 0 && currentCycle >= targetCycle) {
					SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(this, "자동 검침 목표 사이클 완료했습니다."));
					stopAutoPolling();
					return;
				}
				if (!enqueueSelectedMeters()) {
					log.warn("선택된 항목 없음 – 자동 검침 중단");
					stopAutoPolling();
					return;
				}
				serialManager.startSend();
				currentCycle++;
				log.info("자동 검침 {}회/{}", currentCycle, targetCycle == 0 ? "무한" : String.valueOf(targetCycle));

				final int cycle = currentCycle;
				SwingUtilities.invokeLater(() -> autoPollingPanel.updateCycleDisplay(cycle, targetCycle));
			} catch (Exception ex) {
				log.error("자동 검침 스케줄러 예외", ex);
			}
		};

		autoTaskFuture = autoScheduler.scheduleAtFixedRate(pollingTask, 0, scanRate, TimeUnit.MILLISECONDS);
	}

	public void stopAutoPolling() {
		if (autoTaskFuture != null && !autoTaskFuture.isCancelled()) {
			autoTaskFuture.cancel(false);
			log.info("자동 검침 중지 (완료 사이클: {}회)", currentCycle);
			appendSystemLog(new LogDto("SYSTEM", String.format("자동 검침 중지 (완료: %d회)", currentCycle)));
			autoPollingPanel.pollStop();
		}
		isAutoPolling = false;
		setRowControlButtonsEnabled(true);
	}

	private void setRowControlButtonsEnabled(boolean enabled) {
		addRowBtn.setEnabled(enabled);
		clearSelectionBtn.setEnabled(enabled);
		selectAllBtn.setEnabled(enabled);
		btnDeleteSelected.setEnabled(enabled);
	}

	private boolean enqueueSelectedMeters() {
		boolean hasSelection = false;

		for (int row = 0; row < meterTable.getRowCount(); row++) {
			Boolean checked = (Boolean) meterModel.getValueAt(row, 1);
			if (!Boolean.TRUE.equals(checked))
				continue;

			hasSelection = true;

			MeterRowDto info = (MeterRowDto) meterModel.getValueAt(row, 12);
			if (info == null) {
				log.error("DTO 없음 – row={}", row);
				continue;
			}

			try {
				int startAddr = info.getAddress();
				int slaveId = info.getSlaveId();
				int fCode = info.getFunctionCode() != null ? info.getFunctionCode().getCode() : 0x04;
				int byteSize = ByteConverter
						.getDataSize(info.getDataType() != null ? info.getDataType() : DataType.UINT16);
				int regCount = byteSize / 2;

				byte[] rawBytes = { (byte) (slaveId & 0xFF), (byte) (fCode & 0xFF), (byte) ((startAddr >> 8) & 0xFF),
						(byte) (startAddr & 0xFF), (byte) ((regCount >> 8) & 0xFF), (byte) (regCount & 0xFF) };
				int crc = ProtocolConverter.calculateCRC16(rawBytes, rawBytes.length);
				byte[] packet = new byte[8];
				System.arraycopy(rawBytes, 0, packet, 0, rawBytes.length);
				packet[6] = (byte) (crc & 0xFF);
				packet[7] = (byte) ((crc >> 8) & 0xFF);

				PendingRequest request = new PendingRequest(packet, info, serialManager.getRetryCount());
				serialManager.enqueue(request);

				final int currentRow = row;
				SwingUtilities.invokeLater(() -> meterModel.setValueAt("요청중", currentRow, 10));

			} catch (Exception ex) {
				final int currentRow = row;
				SwingUtilities.invokeLater(() -> meterModel.setValueAt("오류", currentRow, 10));
				log.error("패킷 빌드 실패 – row={}", row, ex);
			}
		}
		return hasSelection;
	}

	public void updateMeterValue(int address, String value, String status) {
		Integer row = rowMap.get(address);
		if (row == null) {
			log.warn("주소 0x{} 에 해당하는 행 없음", String.format("%04X", address));
			return;
		}

		MeterRowDto dto = (MeterRowDto) meterModel.getValueAt(row, 12);

		String calculatedValue = value; // 기본값 세팅

		if (dto != null) {
			double scale = dto.getScale(); // MeterRowDto에 정의된 getScale() 호출
			try {
				double rawNum = Double.parseDouble(value);
				double scaledNum = rawNum * scale; // 스케일 연산

				calculatedValue = String.format("%.2f", scaledNum);

			} catch (NumberFormatException e) {
				calculatedValue = value;
			}
		}

		final String finalValue = calculatedValue;
		SwingUtilities.invokeLater(() -> {
			meterModel.setValueAt(finalValue, row, 8); // 값 반영
			meterModel.setValueAt(status, row, 10); // 상태 반영
		});
	}

	private int getRegisterIncrement(DataType type) {
		if (type == null)
			return 1;
		return ByteConverter.getDataSize(type) / 2;
	}

	public void refreshMapDropDown() {
		mapDropDown.removeAllItems();
		for (String profile : fileManager.getProfileList())
			mapDropDown.addItem(profile);
	}

	private void updateCrcPreview() {
		String text = rawHexControlPanel.getRawHexField().getText().trim();
		if (text.isEmpty()) {
			rawHexControlPanel.getCrcPreviewLabel().setText("최종 송신 예정: -");
			return;
		}
		try {
			byte[] inputBytes = ProtocolConverter.convertHexToBytes(text);
			if (rawHexControlPanel.getCrcAutoCheck().isSelected()) {
				int crc = ProtocolConverter.calculateCRC16(inputBytes, inputBytes.length);
				byte[] full = new byte[inputBytes.length + 2];
				System.arraycopy(inputBytes, 0, full, 0, inputBytes.length);
				full[full.length - 2] = (byte) (crc & 0xFF);
				full[full.length - 1] = (byte) ((crc >> 8) & 0xFF);
				rawHexControlPanel.getCrcPreviewLabel()
						.setText("송신 예정: " + ProtocolConverter.convertBytesToHex(full, full.length));
			} else {
				rawHexControlPanel.getCrcPreviewLabel().setText("송신 예정: " + text);
			}
		} catch (Exception e) {
			rawHexControlPanel.getCrcPreviewLabel().setText("송신 예정: [HEX 포맷 오류]");
		}
	}

	private void sendRawData() {
		String hex = rawHexControlPanel.getRawHexField().getText().trim();
		try {
			controller.sendRawHex(hex, rawHexControlPanel.getCrcAutoCheck().isSelected());
		} catch (IllegalArgumentException ex) {
			JOptionPane.showMessageDialog(this, ex.getMessage() != null ? ex.getMessage() : "", "입력 오류",
					JOptionPane.WARNING_MESSAGE);
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "물리 통신 전송 오류: " + ex.getMessage());
		}
	}

	private void saveTerminalLog() {
		String logContent = rxTxArea.getText().trim();
		if (logContent.isEmpty()) {
			JOptionPane.showMessageDialog(this, "저장할 터미널 로그 내용이 없습니다.", "알림", JOptionPane.WARNING_MESSAGE);
			return;
		}
		JFileChooser fc = new JFileChooser();
		fc.setDialogTitle("TX / RX 로그 파일로 저장");
		fc.setSelectedFile(new java.io.File("Modbus_TX_RX_log.txt"));
		if (fc.showSaveDialog(this) != JFileChooser.APPROVE_OPTION)
			return;
		java.io.File f = fc.getSelectedFile();
		if (!f.getAbsolutePath().toLowerCase().endsWith(".txt"))
			f = new java.io.File(f.getAbsolutePath() + ".txt");
		try (java.io.BufferedWriter w = new java.io.BufferedWriter(new java.io.FileWriter(f))) {
			w.write(logContent);
			JOptionPane.showMessageDialog(this, "저장 완료");
		} catch (java.io.IOException ex) {
			JOptionPane.showMessageDialog(this, "저장 오류: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
		}
	}

	public void selectAllorRefresh(boolean isButtonClicked) {
		int rowCount = meterModel.getRowCount();
		if (rowCount == 0) {
			selectAllBtn.setText("전체 선택");
			selectAllBtn.setForeground(Color.BLACK);
			return;
		}

		if (isButtonClicked) {
			boolean targetState = selectAllBtn.getText().equals("전체 선택");
			selectAllBtn.setText(targetState ? "전체 해제" : "전체 선택");
			selectAllBtn.setForeground(targetState ? Color.GRAY : Color.BLACK);

			isUpdatingAddress = true;
			for (int row = 0; row < rowCount; row++) {
				meterModel.setValueAt(targetState, row, 1);
				MeterRowDto dto = (MeterRowDto) meterModel.getValueAt(row, 12);
				if (dto != null)
					dto.setSelected(targetState);
			}
			isUpdatingAddress = false;
			sendHexaBtn.setEnabled(targetState);
			this.hasSelection = targetState;
		} else {
			boolean allChecked = true, hasAny = false;
			for (int row = 0; row < rowCount; row++) {
				boolean checked = Boolean.TRUE.equals(meterModel.getValueAt(row, 1));
				if (checked)
					hasAny = true;
				else
					allChecked = false;
			}
			selectAllBtn.setText(allChecked ? "전체 해제" : "전체 선택");
			selectAllBtn.setForeground(allChecked ? Color.GRAY : Color.BLACK);
			sendHexaBtn.setEnabled(hasAny);
			this.hasSelection = hasAny;
		}
	}

	public void resetAllMeterTable() {
		SwingUtilities.invokeLater(() -> {
			for (int row = 0; row < meterModel.getRowCount(); row++) {
				meterModel.setValueAt(false, row, 1);
				meterModel.setValueAt("-", row, 8);
				meterModel.setValueAt("대기", row, 10);
			}
		});
	}

	private String reformatRawValue(String currentText, String option) {
		try {
			double v = Double.parseDouble(currentText);
			switch (option) {
			case "Signed":
			case "32-bit Signed":
				return String.format("%.0f", v);
			case "32-bit Float":
			case "64-bit Double":
				return String.format("%.4f", v);
			default:
				return currentText;
			}
		} catch (NumberFormatException ex) {
			return currentText;
		}
	}

	public void appendTxRxTerminal(String hex) {
		SwingUtilities.invokeLater(() -> {
			rxTxArea.append(hex);
			rxTxArea.setCaretPosition(rxTxArea.getDocument().getLength());
		});
	}

	public void appendSystemLog(LogDto dto) {
		if (dto == null)
			return;
		if (totalLogList.size() >= MAX_LOG_COUNT)
			totalLogList.remove(0);
		totalLogList.add(dto);
		refreshSystemLogView();
	}

	private void refreshSystemLogView() {
		SwingUtilities.invokeLater(() -> {
			StringBuilder sb = new StringBuilder();
			for (LogDto entry : totalLogList) {
				String type = entry.getType();
				if ("SYSTEM".equalsIgnoreCase(type) && !isShowSystemLog)
					continue;
				if ("ERROR".equalsIgnoreCase(type) && !isShowErrorLog)
					continue;
				sb.append(entry.toString()).append("\n");
			}
			systemLogArea.setFont(new Font("Malgun Gothic", Font.PLAIN, 12));
			systemLogArea.setText(sb.toString());
			systemLogArea.setCaretPosition(systemLogArea.getDocument().getLength());
		});
	}

	public void clearRaw() {
		rawHexControlPanel.getRawHexField().setText("");
		rawHexControlPanel.getRawResponseArea().setText("");
		rawHexControlPanel.getCrcPreviewLabel().setText("송신 예정: -");
	}

	public void updateRawResponseArea(String hex) {
		rawHexControlPanel.getRawResponseArea().append(hex);
	}

	public void handlePhysicalDisconnect() {
		SwingUtilities.invokeLater(() -> {
			JOptionPane.showMessageDialog(null, "물리 전력 신호 단선 혹은 버스 이탈 상태가 확인되었습니다.\n케이블 플러그 하드웨어를 검증하세요.");
			configPanel.updateConnectionState(false);
		});
	}

	public void setConnected(boolean connected) {
		this.isConnected = connected;
		setSendButtonEnabled(connected);
		rawHexControlPanel.getSendRawBtn().setEnabled(connected);
	}

	public void setSendButtonEnabled(boolean enabled) {
		if (sendHexaBtn != null)
			sendHexaBtn.setEnabled(enabled);
	}

	// 0번으로 안전하게 되돌리는 헬퍼 메서드
	private void resetComboToZero() {
		isMapComboResetting = true; // 플래그 ON (리스너 먹통 만들기)
		topBarPanel.getMapFileCombo().setSelectedIndex(0);
		isMapComboResetting = false; // 플래그 OFF (다시 리스너 복구)
	}

	public boolean isConnected() {
		return this.isConnected;
	}

	public boolean getHasSelection() {
		return this.hasSelection;
	}

	public void setUpdatingAddress(boolean v) {
		this.isUpdatingAddress = v;
	}

	public boolean isUpdatingAddress() {
		return this.isUpdatingAddress;
	}

	public JComboBox<String> getMapDropDown() {
		return this.mapDropDown;
	}

	public static void main(String[] args) {
		com.formdev.flatlaf.FlatLightLaf.setup();
		System.setProperty("sun.java2d.uiScale", "true");
		SwingUtilities.invokeLater(() -> new ModbusTerminal().setVisible(true));
	}
}