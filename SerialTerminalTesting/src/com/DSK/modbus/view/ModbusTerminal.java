package com.DSK.modbus.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Point;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.DefaultCellEditor;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
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

import com.DSK.modbus.controller.ModbusController;
import com.DSK.modbus.controller.ProfileController;
import com.DSK.modbus.model.LogDto;
import com.DSK.modbus.model.MeterRowDto;
import com.DSK.modbus.model.MeterRowIndex;
import com.DSK.modbus.model.PendingRequest;
import com.DSK.modbus.model.constant.DataType;
import com.DSK.modbus.model.constant.EndianType;
import com.DSK.modbus.model.constant.FunctionCode;
import com.DSK.modbus.service.MapFileManager;
import com.DSK.modbus.service.ModbusEventListener;
import com.DSK.modbus.service.ModbusManager;
import com.DSK.modbus.service.codec.ByteConverter;
import com.DSK.modbus.service.codec.ProtocolConverter;
import com.DSK.modbus.view.handler.TableTransferHandler;

public class ModbusTerminal extends JFrame implements ModbusEventListener {

	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(ModbusTerminal.class);

	private boolean isConnected = false;
	private boolean isAutoPolling = false;

	private final ModbusManager serialManager;
	private ModbusController controller;

	private MapFileManager fileManager;
	private ProfileController profileController;

	private ModbusConfigPanel configPanel;
	private ModbusAutoPollingPanel autoPollingPanel;
	private ModbusRawHexPanel rawHexControlPanel;
	private ModbusTopBarPanel topBarPanel;
	private ModbusErrorHistoryDialog errorHistoryDialog;
	private ModbusLogPanel logPanel;

	private JTable meterTable;
	private DefaultTableModel meterModel;

	// ✅ MeterRowIndex — (slaveId, fc, address) 3개 복합키 캡슐화
	private final MeterRowIndex rowIndex = new MeterRowIndex();

	private JButton clearSelectionBtn, selectAllBtn, addRowBtn;
	private JButton btnSaveMap, btnImportMap, btnDeleteMap, btnNewMap, btnExportMap, btnDeleteSelected;
	private JComboBox<String> mapDropDown;

	private int nextPredictionAddr = 0;

	private boolean isUpdatingAddress = false;
	private boolean hasSelection = false;

	private final ScheduledExecutorService autoScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "AutoPolling");
		t.setDaemon(true);
		return t;
	});
	private ScheduledFuture<?> autoTaskFuture;
	// ✅ [수정] volatile 추가 — autoScheduler와 EDT 사이의 가시성 보장
	private volatile int currentCycle = 0;

	// ✅ [신규] 에러 발생 행 추적 — EDT 전용 (SafeEventDispatcher가 EDT 보장)
	// updateMeterValue()에서 add/remove, prepareRenderer에서 배경색 결정에 사용.
	// 자동 검침 중 행 삭제는 isAutoPolling 가드로 차단되어 있으므로
	// 인덱스 불일치 위험은 낮지만, 삭제 시 clear()로 방어한다.
	private final java.util.Set<Integer> errorRowSet = new java.util.HashSet<>();

	private final Color zebraEvenColor = new Color(248, 249, 251);
	private final Color zebraOddColor = Color.WHITE;
	private final Font tableBaseFont = new Font("맑은 고딕", Font.PLAIN, 12);

	private JDialog activeBulkEditDialog;

	public ModbusTerminal() {
		serialManager = new ModbusManager(this);
		this.controller = new ModbusController(this, this.serialManager);
		this.configPanel = new ModbusConfigPanel(this, this.controller);
		this.errorHistoryDialog = new ModbusErrorHistoryDialog(serialManager);
		logPanel = new ModbusLogPanel(this);

		setTitle("Modbus Metering Program");

		// =============== 구형 모니터 크기 최소 방어선 =============== 
		setSize(1024, 768);
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
		// 0.5초(500ms)마다 테이블 화면을 다시 그리도록 신호를 주는 타이머 (CPU 지분 거의 안 먹음) 요청중 용 UI 타이머
		new javax.swing.Timer(500, e -> meterTable.repaint()).start();

		Font boldTitleFont = new Font("맑은 고딕", Font.BOLD, 12);
		Color softBorderColor = new Color(225, 230, 235);

		((JPanel) getContentPane()).setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
		((JPanel) getContentPane()).setBackground(Color.WHITE);

		// =============== 왼쪽 패널 크기 ===============
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
		btnImportMap = new JButton("맵 가져오기");
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

		// ==================== 우측 Meter Table ==================== 
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

		// ✅ [수정] prepareRenderer 오버라이드 — 에러 행 전체 배경색 일괄 적용
		// [Q2 fix] row >= 0 가드: 셀 편집 취소(ESC) 등 엣지케이스에서 row=-1로
		// 호출될 수 있다. HashSet.contains(-1)은 false라 크래시는 없지만,
		// 명시적 가드로 의도를 명확히 한다.
		meterTable = new JTable(meterModel) {
			@Override
			public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
				Component c = super.prepareRenderer(renderer, row, column);

				// row < 0 가드 — 셀 편집 취소(ESC) 등 엣지케이스
				if (row < 0)
					return c;

				// 선택 행은 Look&Feel 기본 선택 색상 유지 (건드리지 않음)
				if (isRowSelected(row))
					return c;

				// ✅ [핵심 수정] 에러 여부와 관계없이 항상 배경색을 명시적으로 지정한다.
				//
				// [버그 원인]
				// JTable의 렌더러 컴포넌트(JCheckBox 포함)는 모든 행이 하나를 공유한다.
				// 에러 행에서 setBackground(빨강)를 호출하면, 다음 비에러 행 렌더링 시
				// 컴포넌트가 여전히 빨간 배경을 가진 채로 그려진다.
				//
				// [fix]
				// 조건 분기와 관계없이 모든 경로에서 반드시 배경색을 지정하여
				// 이전 렌더링의 상태가 이어지는 것을 차단한다.
				if (errorRowSet.contains(row)) {
					c.setBackground(new Color(255, 210, 210)); // 에러 행: 연한 빨강
				} else {
					c.setBackground(row % 2 == 0 ? zebraEvenColor : zebraOddColor); // 정상 행: 기본 배경
				}

				// JCheckBox는 L&F에 따라 기본적으로 불투명(opaque)이 아닐 수 있다.
				// setOpaque(true) 없이는 setBackground가 실제로 표시되지 않는 경우가 있다.
				if (c instanceof javax.swing.JComponent jc) {
					jc.setOpaque(true);
				}
				return c;
			}
		};

		JTextField addressField = new JTextField();
		addressField.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
		meterTable.getColumnModel().getColumn(3).setCellEditor(new DefaultCellEditor(addressField));

		fileManager = new MapFileManager();
		profileController = new ProfileController(this, fileManager, meterModel, rowIndex);

		JTableHeader header = meterTable.getTableHeader();
		header.addMouseListener(new java.awt.event.MouseAdapter() {
			private void tryShow(java.awt.event.MouseEvent e) {
				if (!e.isPopupTrigger())
					return;
				int viewCol = header.columnAtPoint(e.getPoint());
				if (viewCol < 0)
					return;
				int modelCol = meterTable.convertColumnIndexToModel(viewCol);
				if (modelCol == 2 || modelCol == 5 || modelCol == 6 || modelCol == 7)
					showBulkColumnEditPopup(modelCol, header, e.getX(), e.getY());
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

		meterTable.getColumnModel().getColumn(0).setPreferredWidth(45); // No.
		meterTable.getColumnModel().getColumn(1).setPreferredWidth(45); // Check
		meterTable.getColumnModel().getColumn(2).setPreferredWidth(65); // Slave Id
		meterTable.getColumnModel().getColumn(3).setPreferredWidth(60); // Address
		meterTable.getColumnModel().getColumn(4).setPreferredWidth(140); // Name
		meterTable.getColumnModel().getColumn(5).setPreferredWidth(140); // FC
		meterTable.getColumnModel().getColumn(6).setPreferredWidth(85); // Data Type
		meterTable.getColumnModel().getColumn(7).setPreferredWidth(140); // Endian
		meterTable.getColumnModel().getColumn(8).setPreferredWidth(80); // Value
		meterTable.getColumnModel().getColumn(9).setPreferredWidth(60); // Unit
		meterTable.getColumnModel().getColumn(10).setPreferredWidth(60); // State
		meterTable.getColumnModel().getColumn(11).setPreferredWidth(50); // Scale

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
						int frame = (int) ((System.currentTimeMillis() / 500) % 4);
						String dots = "";
						for (int i = 0; i < frame; i++) {
							dots += ".";
						}
						((JLabel) c).setText(String.format("요청중%-3s", dots));
						break;

					default:
						c.setForeground(new Color(130, 140, 150));
						break;
					}
				}
				return c;
			}
		};
		meterTable.getColumnModel().getColumn(10).setCellRenderer(stateRenderer);

		DefaultTableCellRenderer valueRenderer = new DefaultTableCellRenderer() {
			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				Component c = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
				((JLabel) c).setHorizontalAlignment(JLabel.RIGHT);
				c.setFont(new Font("D2Coding", Font.BOLD, 14));
				if (!isSelected) {
					c.setBackground(row % 2 == 0 ? zebraEvenColor : zebraOddColor);
					c.setForeground(Color.BLACK);
				}
				return c;
			}
		};
		meterTable.getColumnModel().getColumn(8).setCellRenderer(valueRenderer);

		TableColumn dtoColumn = meterTable.getColumnModel().getColumn(12);
		dtoColumn.setMinWidth(0);
		dtoColumn.setMaxWidth(0);
		dtoColumn.setPreferredWidth(0);
		dtoColumn.setResizable(false);

		JScrollPane meterScroll = new JScrollPane(meterTable);
		meterScroll.getViewport().setBackground(Color.WHITE);
		meterScroll.setBorder(BorderFactory.createLineBorder(softBorderColor));
		rightPanel.add(meterScroll, BorderLayout.CENTER);

		// ── 하단 로그 패널 (logPanel으로 분리) ────────────────────────────────

		rightPanel.add(logPanel, BorderLayout.SOUTH);

		topBarPanel = new ModbusTopBarPanel();

		this.addRowBtn = topBarPanel.getAddRowBtn();
		this.clearSelectionBtn = topBarPanel.getClearSelectionBtn();
		this.selectAllBtn = topBarPanel.getSelectAllBtn();
		this.btnDeleteSelected = topBarPanel.getBtnDeleteSelected();

		final JTextField txtStartAddr = topBarPanel.getTxtStartAddr();
		final JTextField txtSlaveId = topBarPanel.getTxtSlaveId();
		final JTextField txtAddRowCount = topBarPanel.getTxtAddRowCount();
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

		// 1. 왼쪽 컨테이너 전용 (gbcLeft)
		GridBagConstraints gbcLeft = new GridBagConstraints();
		gbcLeft.fill = GridBagConstraints.BOTH;
		gbcLeft.gridx = 0;
		gbcLeft.gridy = 0;
		gbcLeft.weightx = 0.0;
		gbcLeft.weighty = 1.0;
		gbcLeft.insets = new Insets(0, 0, 0, 10);
		masterContentPanel.add(leftScrollContainer, gbcLeft); // 왼쪽 추가 완료

		// 2. 오른쪽 패널 전용 (gbcRight) - 객체를 새로 생성해서 독립시킵니다.
		GridBagConstraints gbcRight = new GridBagConstraints();
		gbcRight.fill = GridBagConstraints.BOTH;
		gbcRight.gridx = 1; // 1번째 열 (오른쪽)
		gbcRight.gridy = 0; // 0번째 행
		gbcRight.weightx = 1.0; // 가로 공간을 꽉 채우도록 설정
		gbcRight.weighty = 1.0;
		gbcRight.insets = new Insets(0, 0, 0, 0);
		masterContentPanel.add(rightPanel, gbcRight); // 오른쪽 추가 완료

		JScrollPane globalMainScrollPane = new JScrollPane(masterContentPanel);
		globalMainScrollPane.setBorder(null);
		globalMainScrollPane.setBackground(Color.WHITE);
		globalMainScrollPane.getViewport().setBackground(Color.WHITE);
		globalMainScrollPane.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED);
		globalMainScrollPane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
		add(globalMainScrollPane, BorderLayout.CENTER);

		meterTable.setDragEnabled(true);
		meterTable.setDropMode(javax.swing.DropMode.INSERT_ROWS);
		meterTable.setTransferHandler(new TableTransferHandler(this, meterModel, rowIndex));
		meterTable.setDefaultRenderer(Object.class, rowColorRenderer);
		meterTable.setDefaultRenderer(Number.class, rowColorRenderer);

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

		topBarPanel.getRdoHex().addActionListener(e -> {
			if (meterTable.getRowCount() == 0) {
				topBarPanel.getTxtStartAddr().setText("0x0000");
			} else {
				txtStartAddr.setText(String.format("0x%04X", this.nextPredictionAddr));
			}
		});

		topBarPanel.getRdoDec().addActionListener(e -> {
			if (meterTable.getRowCount() == 0) {
				topBarPanel.getTxtStartAddr().setText("0");
			} else {
				txtStartAddr.setText(String.valueOf(this.nextPredictionAddr));
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
					rowIndex.remove(dto);
				meterModel.removeRow(targetRow);
			}
			rowIndex.clear();
			for (int i = 0; i < meterModel.getRowCount(); i++) {
				MeterRowDto dto = (MeterRowDto) meterModel.getValueAt(i, 12);
				if (dto != null)
					rowIndex.put(dto, i);
				meterModel.setValueAt(i + 1, i, 0);
			}

			if (meterTable.getRowCount() == 0) {
				if (topBarPanel.getRdoDec().isSelected()) {
					topBarPanel.getTxtStartAddr().setText("0");
				} else {
					topBarPanel.getTxtStartAddr().setText("0x0000");
				}
			}

			// ✅ [Q3 fix] 행 삭제 후 errorRowSet 초기화
			// 행 삭제 시 인덱스가 재편되어 errorRowSet의 기존 인덱스가 꼬인다.
			// 자동검침 중 삭제는 isAutoPolling 가드로 막혀있지만, 방어적으로 clear한다.
			errorRowSet.clear();
			meterTable.repaint();
			selectAllorRefresh(false);
		});

		btnNewMap.addActionListener(e -> {
			profileController.createNewProfile();
			clearErrorHighlights();
		});
		btnSaveMap.addActionListener(e -> profileController.saveProfile());
		btnDeleteMap.addActionListener(e -> {
			profileController.deleteProfile((String) mapDropDown.getSelectedItem());
			refreshMapDropDown();
			clearErrorHighlights();
		});
		btnImportMap.addActionListener(e -> {
			profileController.importProfileFromDisk();
			clearErrorHighlights();
		});
		btnExportMap.addActionListener(e -> profileController.exportProfileFromTable());
		mapDropDown.addActionListener(e -> {
			String selected = (String) mapDropDown.getSelectedItem();
			// ============ 맵 파일 불러오기 ============ 
			if (selected != null && !selected.trim().isEmpty()) {
				profileController.loadProfile(selected);
				clearErrorHighlights();
				if (selectAllBtn.getText().equals("전체 해제")) {
					selectAllBtn.setText("전체 선택");
					selectAllBtn.setForeground(Color.BLACK);
				}
			}
		});

		final boolean isFileManageChange[] = { false };
		mapFileCombo.addActionListener(e -> {
			if (isFileManageChange[0])
				return;
			@SuppressWarnings("unchecked")
			JComboBox<String> combo = (JComboBox<String>) e.getSource();
			int selectedIndex = combo.getSelectedIndex();
			switch (selectedIndex) {
			case 0: // 저장
				btnSaveMap.doClick();
				break;
			case 1: // 다른 이름으로 저장
				promptAndSaveProfileAs();
				isFileManageChange[0] = true;
				combo.setSelectedIndex(0);
				isFileManageChange[0] = false;
				break;
			case 2: // 새 파일 생성
				btnNewMap.doClick();
				isFileManageChange[0] = true;
				combo.setSelectedIndex(0);
				isFileManageChange[0] = false;
				break;
			case 3: // 파일 내보내기
				btnExportMap.doClick();
				isFileManageChange[0] = true;
				combo.setSelectedIndex(0);
				isFileManageChange[0] = false;
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
			int addRowCount = 1; // 기본값 선언
			try {
				slaveId = Integer.parseInt(txtSlaveId.getText().trim());
			} catch (NumberFormatException nfe) {
				JOptionPane.showMessageDialog(this, "Slave Id 는 정수만 입력 가능합니다.", "입력 오류", JOptionPane.WARNING_MESSAGE);
				return;
			}
			try {
				String countText = txtAddRowCount.getText().trim();
				if (countText.isEmpty() || countText.equals("1")) {
					addRowCount = 1;
				} else {
					addRowCount = Integer.parseInt(countText);
					if (addRowCount <= 0) {
						JOptionPane.showMessageDialog(this, "추가 행 개수는 1 이상의 양의 정수만 입력 가능합니다.", "입력 오류",
								JOptionPane.WARNING_MESSAGE);
						return;
					}
				}
			} catch (NumberFormatException nfe) {
				JOptionPane.showMessageDialog(this, "올바른 숫자 형식을 입력해 주세요.", "입력 오류", JOptionPane.WARNING_MESSAGE);
				return;
			}

			// ── 여기서부터는 안전하게 검증된 addRowCount(양의 정수)로 행 추가 로직 수행 ──
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
			// ✅ [수정] 중복 검사 — 새 행의 기본 FC(READ_INPUT_REGISTERS=0x04) 포함 3개 키로 비교
			int currentAddress = parsedAddress;

			for (int i = 0; i < addRowCount; i++) {
				// ✅ [질문자님 원본 로직 유지] 중복 검사 — 새 행의 기본 FC 포함 3개 키로 비교
				int defaultFcCode = FunctionCode.READ_INPUT_REGISTERS.getCode();
				if (rowIndex.contains(slaveId, defaultFcCode, currentAddress)) {
					JOptionPane.showMessageDialog(this,
							String.format(
									"이미 등록된 항목입니다. (Slave %d, FC 0x%02X, 주소 0x%04X)\n\n" + "같은 주소에 다른 기능코드를 추가하려면:\n"
											+ "① 기존 행의 FC를 원하는 값으로 먼저 변경\n" + "② 이후 새 행을 추가하면 키 충돌 없이 등록 가능",
									slaveId, defaultFcCode, currentAddress),
							"중복 항목 — 추가 불가", JOptionPane.WARNING_MESSAGE);
					return; // 중복 발견 시 즉시 전체 중단 (기존 로직 유지)
				}

				MeterRowDto newDto = new MeterRowDto(currentAddress, slaveId, "새 검침 항목",
						FunctionCode.READ_INPUT_REGISTERS, targetDataType, targetEndianType, "V", 1.0);

				int nextRowNum = meterModel.getRowCount() + 1;
				newDto.setHexAddress(rdoHex.isSelected()); // ✅ 행 추가 시 현재 Hex/Dec 포맷 저장

				meterModel.addRow(new Object[] { nextRowNum, newDto.isSelected(), newDto.getSlaveId(),
						newDto.getFormattedAddress(), newDto.getItemName(), newDto.getFunctionCode(),
						newDto.getDataType(), newDto.getEndianType(), "-", newDto.getUnit(), "대기", newDto.getScale(),
						newDto });

				rowIndex.put(newDto, nextRowNum - 1);

				currentAddress += getRegisterIncrement(newDto.getDataType());
				//				currentAddress += getRegisterIncrement(targetDataType);
			}

			selectAllorRefresh(hasSelection);
			topBarPanel.setTextAddRowCount();

			// UI 예측 텍스트필드 업데이트
			this.nextPredictionAddr = currentAddress;
			txtStartAddr.setText(
					rdoHex.isSelected() ? String.format("0x%04X", currentAddress) : String.valueOf(currentAddress));
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

		rawHexControlPanel.getSendRawBtn().addActionListener(e -> {
			rawHexControlPanel.getRawResponseArea().setText("");
			sendRawData();
		});
		rawHexControlPanel.getRawHexField().addActionListener(e -> sendRawData());
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

		// =========================================================================
		// ModbusLogPanel
		// =========================================================================
		// logPanel이 필터/비우기 내부 처리, Terminal은 비즈니스 액션만 주입
		logPanel.addSendAction(e -> sendSelectedData());
		logPanel.addViewHistoryAction(e -> showErrorHistory());
		logPanel.addSaveTerminalAction(e -> saveTerminalLog());

		// TODO: 시연후 삭제 
		// [DEMO ONLY] 시연 후 삭제
		logPanel.getChkDemoCrc()
				.addActionListener(e -> serialManager.setSimulateCrcError(logPanel.getChkDemoCrc().isSelected()));
		logPanel.getChkDemoException().addActionListener(
				e -> serialManager.setSimulateException(logPanel.getChkDemoException().isSelected())); // =========================================================================
		// TableModelListener – 셀 편집 DTO 동기화
		// =========================================================================
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

				case 2: { // Slave Id 변경
					String slaveIdInput = val.toString().trim();
					int newSlaveId;
					try {
						newSlaveId = Integer.parseInt(slaveIdInput);
					} catch (NumberFormatException nfe) {
						log.warn("SlaveId 파싱 실패: {}", val);
						isUpdatingAddress = true;
						meterModel.setValueAt(String.valueOf(dto.getSlaveId()), row, col);
						isUpdatingAddress = false;
						break;
					}

					int oldSlaveId = dto.getSlaveId();
					// ✅ [수정] FC 포함 3개 복합키로 중복 검사
					int fcCode = dto.getFunctionCode() != null ? dto.getFunctionCode().getCode() : 0x04;
					boolean conflict = newSlaveId != oldSlaveId
							&& rowIndex.contains(newSlaveId, fcCode, dto.getAddress());

					if (conflict) {
						JOptionPane.showMessageDialog(this,
								String.format("이미 등록된 항목입니다. (Slave %d, FC 0x%02X, 주소 0x%04X)", newSlaveId, fcCode,
										dto.getAddress()));
						isUpdatingAddress = true;
						meterModel.setValueAt(String.valueOf(oldSlaveId), row, col);
						isUpdatingAddress = false;
						break;
					}

					// ✅ [수정] long oldKey 캡처 후 dto 변경, rekey(oldKey, dto, row) 호출
					long oldKey = dto.getRowKey();
					dto.setSlaveId(newSlaveId);
					rowIndex.rekey(oldKey, dto, row);
					break;
				}

				case 3: { // Address 변경
					String input = val.toString().trim();
					int newAddr;

					// ✅ [수정] 사용자 입력 포맷 감지 — 이후 hexAddress 갱신 + 표시에 사용
					boolean inputIsHex = input.startsWith("0x") || input.startsWith("0X");

					try {
						if (inputIsHex) {
							String hexStr = input.substring(2).trim();
							newAddr = hexStr.isEmpty() ? 0 : Integer.parseInt(hexStr, 16);
						} else {
							newAddr = Integer.parseInt(input);
						}
					} catch (NumberFormatException ex) {
						// 파싱 실패 → 원래 포맷 그대로 복원
						isUpdatingAddress = true;
						meterModel.setValueAt(dto.getFormattedAddress(), row, col); // ✅ [수정] Hex 하드코딩 제거
						isUpdatingAddress = false;
						break;
					}

					if (newAddr < 0 || newAddr > 65535) {
						JOptionPane.showMessageDialog(this, "모드버스 주소 범위를 초과했습니다. (0 ~ 65535)");
						isUpdatingAddress = true;
						meterModel.setValueAt(dto.getFormattedAddress(), row, col); // ✅ [수정] 원래 포맷으로 복원
						isUpdatingAddress = false;
						break;
					}

					int oldAddr = dto.getAddress();
					int fcCode = dto.getFunctionCode() != null ? dto.getFunctionCode().getCode() : 0x04;
					boolean conflict = newAddr != oldAddr && rowIndex.contains(dto.getSlaveId(), fcCode, newAddr);

					if (conflict) {
						JOptionPane.showMessageDialog(this, String.format(
								"이미 등록된 항목입니다. (Slave %d, FC 0x%02X, 주소 0x%04X)", dto.getSlaveId(), fcCode, newAddr));
						isUpdatingAddress = true;
						meterModel.setValueAt(dto.getFormattedAddress(), row, col); // ✅ [수정] 원래 포맷으로 복원
						isUpdatingAddress = false;
						break;
					}

					// 정상 업데이트
					long oldKey = dto.getRowKey();
					dto.setAddress(newAddr);
					dto.setHexAddress(inputIsHex); // ✅ [추가] 핵심 — 편집 시 포맷 갱신
					rowIndex.rekey(oldKey, dto, row);

					isUpdatingAddress = true;
					meterModel.setValueAt(dto.getFormattedAddress(), row, col); // ✅ [수정] 갱신된 포맷으로 표시
					isUpdatingAddress = false;
					break;
				}
				case 4:
					dto.setItemName(val.toString());
					break;

				case 5: {
					FunctionCode newFc = val instanceof FunctionCode ? (FunctionCode) val
							: FunctionCode.valueOf(val.toString());
					int newFcCode = newFc.getCode();
					int oldFcCode = dto.getFunctionCode() != null ? dto.getFunctionCode().getCode() : 0x04;

					// FC가 실제로 달라질 때만 충돌 검사
					if (newFcCode != oldFcCode && rowIndex.contains(dto.getSlaveId(), newFcCode, dto.getAddress())) {
						JOptionPane.showMessageDialog(this,
								String.format(
										"FC 변경 불가 — 이미 등록된 항목입니다.\n" + "(Slave %d, FC 0x%02X, 주소 0x%04X)\n\n"
												+ "해당 FC를 가진 행의 주소 또는 SlaveID를 먼저 변경하세요.",
										dto.getSlaveId(), newFcCode, dto.getAddress()),
								"FC 변경 충돌", JOptionPane.WARNING_MESSAGE);
						// 셀 값을 원래 FC로 되돌림
						isUpdatingAddress = true;
						meterModel.setValueAt(dto.getFunctionCode(), row, col);
						isUpdatingAddress = false;
						break;
					}

					long oldKey = dto.getRowKey(); // 변경 전 키 캡처
					dto.setFunctionCode(newFc); // DTO 값 변경
					rowIndex.rekey(oldKey, dto, row); // 새 키로 재등록
					break;
				}

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

	// =========================================================================
	// 칼럼 헤더 우클릭 일괄 변경
	// =========================================================================
	private void showBulkColumnEditPopup(int column, Component invoker, int x, int y) {
		if (isAutoPolling) {
			JOptionPane.showMessageDialog(this, "자동 검침 중에는 칼럼 값을 변경할 수 없습니다.", "변경 불가", JOptionPane.WARNING_MESSAGE);
			return;
		}
		if (meterModel.getRowCount() == 0)
			return;
		if (activeBulkEditDialog != null && activeBulkEditDialog.isVisible())
			activeBulkEditDialog.dispose();

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
		Runnable applyAttributesForAllRows;

		switch (column) {
		case 2: {
			JTextField field = new JTextField("1", 6);
			field.setHorizontalAlignment(JTextField.CENTER);
			editor = field;
			applyAttributesForAllRows = () -> {
				try {
					applyBulkColumnValue(2, Integer.parseInt(field.getText().trim()));
				} catch (NumberFormatException nfe) {
					JOptionPane.showMessageDialog(this, "정수만 입력하세요.", "입력 오류", JOptionPane.WARNING_MESSAGE);
				}
			};
			break;
		}
		case 5: {
			JComboBox<FunctionCode> combo = new JComboBox<>(FunctionCode.values());
			editor = combo;
			applyAttributesForAllRows = () -> applyBulkColumnValue(5, combo.getSelectedItem());
			break;
		}
		case 6: {
			JComboBox<DataType> combo = new JComboBox<>(DataType.values());
			editor = combo;
			applyAttributesForAllRows = () -> applyBulkColumnValue(6, combo.getSelectedItem());
			break;
		}
		case 7: {
			JComboBox<EndianType> combo = new JComboBox<>(EndianType.values());
			editor = combo;
			applyAttributesForAllRows = () -> applyBulkColumnValue(7, combo.getSelectedItem());
			break;
		}
		default:
			return;
		}
		JButton btnApplyAttributesForAllRows = new JButton("전체 적용");
		btnApplyAttributesForAllRows.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		btnApplyAttributesForAllRows.addActionListener(ev -> {
			applyAttributesForAllRows.run();
			dialog.dispose();
		});

		JPanel editorRow = new JPanel(new BorderLayout(4, 0));
		editorRow.setBackground(Color.WHITE);
		editorRow.add(editor, BorderLayout.CENTER);
		editorRow.add(btnApplyAttributesForAllRows, BorderLayout.EAST);

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
		for (int row = 0; row < rowCount; row++)
			meterModel.setValueAt(value, row, column);
		log.info("칼럼 {} 일괄 변경 완료 – 적용값: {}, 대상 행: {}", column, value, rowCount);
		appendSystemLog(new LogDto("SYSTEM",
				String.format("%s – 적용값: {}, 대상 행개수: {}", getBulkEditTitle(column), value, rowCount)));
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
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(this, "저장 중 오류가 발생했습니다: " + ex.getMessage(), "저장 오류",
					JOptionPane.ERROR_MESSAGE);
			log.error("다른 이름으로 저장 실패", ex);
		}
	}

	// =========================================================================
	// 수동 / 자동 검침
	// =========================================================================
	public void sendSelectedData() {
		if (!controller.isConnected()) {
			JOptionPane.showMessageDialog(this, "활성 개방 상태인 컴포트 대상이 확인되지 않습니다.");
			return;
		}
		if (!enqueueSelectedMeters()) {
			JOptionPane.showMessageDialog(this, "검침 항목을 최소 한 개 이상 선택하세요.");
			return;
		}
		resetAllMeterTable(); //TODO
		serialManager.clearAllErrorStatus();
		serialManager.startSend();
	}

	// =========================================================================
	// ✅ [전면 수정] sendSelectedDataAuto
	//
	// [변경 이유 1 — 타이밍]
	// 기존: startSend() 직후 updateCycleDisplay() 호출
	//       → 사이클이 완료되기도 전에 진행률이 올라가는 문제
	// 수정: 다음 pollingTask 실행 시 isCycleComplete()=true 확인 후
	//       이전 사이클의 오류 여부를 판별하고 onCycleCompleted() 호출
	//
	// [변경 이유 2 — 사이클 오염 판별]
	// errSnapshot[]: 사이클 시작 직전의 errorMap 누적 합계를 저장
	// 다음 사이클 시작 시 현재 합계와 비교 → diff > 0 이면 해당 사이클 "오염"
	// =========================================================================
	public void sendSelectedDataAuto(int scanRate, int targetCycle, int retryCount) {
		if (!controller.isConnected()) {
			JOptionPane.showMessageDialog(this, "활성 개방 상태인 컴포트 대상이 확인되지 않습니다.");
			return;
		}
		// ✅ [수정] stopAutoPolling() → resetPollingState()
		// stopAutoPolling()은 applyStopUi() → onStopComplete() → setStatus(false)를 호출해
		// 패널 버튼을 "정지" 상태로 되돌려버린다.
		// resetPollingState()는 스케줄러/큐만 정리하고 UI는 건드리지 않는다.
		resetPollingState();

		currentCycle = 0;
		// ✅ [신규] 요청 건 단위 진행률 추적 초기화
		storedTargetCycle = targetCycle;
		totalCompletedReqs = 0;
		rowsPerCycle = 0;
		// ✅ [신규] 새 검침 세션 시작 시 에러 하이라이트 전체 초기화
		errorRowSet.clear();
		meterTable.repaint();
		serialManager.clearAllErrorStatus();
		serialManager.setRetryCount(retryCount);
		if (meterTable.isEditing())
			meterTable.getCellEditor().stopCellEditing();
		isAutoPolling = true;
		setRowControlButtonsEnabled(false);
		rawHexControlPanel.getSendRawBtn().setEnabled(false);

		// 사이클별 오류 스냅샷 — int[]로 선언해야 람다 안에서 값을 변경할 수 있다
		final int[] errSnapshot = { 0 };
		final boolean[] isFirstRun = { true };

		Runnable pollingTask = () -> {
			try {
				if (!serialManager.isCycleComplete()) {
					log.warn("이전 사이클 처리 중 – 현재 주기 스킵");
					return;
				}

				// ── 이전 사이클 결과 평가 ─────────────────────────────────────
				// 첫 번째 실행은 이전 사이클이 없으므로 건너뛴다.
				if (!isFirstRun[0]) {
					int currentErrSum = serialManager.getErrorMap().values().stream()
							.mapToInt(com.DSK.modbus.model.ErrorStatusDto::getTotalErrCount).sum();
					boolean hadErrors = currentErrSum > errSnapshot[0];
					errSnapshot[0] = currentErrSum;

					final int completedCycle = currentCycle;
					final boolean cycleHadErrors = hadErrors;
					SwingUtilities.invokeLater(
							() -> autoPollingPanel.onCycleCompleted(completedCycle, cycleHadErrors, targetCycle));
				} else {
					isFirstRun[0] = false;
					errSnapshot[0] = 0;
				}

				// ── 종료 조건 확인 ────────────────────────────────────────────
				if (targetCycle > 0 && currentCycle >= targetCycle) {
					// ✅ pollingTask는 autoScheduler 스레드 — stopAutoPolling()이
					// cancelPendingRequests()(synchronized OK) + applyStopUi()(EDT 위임)
					// 패턴이므로 여기서 직접 호출해도 안전하다.
					stopAutoPolling();
					SwingUtilities.invokeLater(() -> {
						autoPollingPanel.getPollingIndicator().stopAnimation();
						JOptionPane.showMessageDialog(ModbusTerminal.this, "자동 검침 목표 사이클 완료했습니다.");
					});
					return;
				}

				// ── 새 사이클 시작 ────────────────────────────────────────────
				if (!enqueueSelectedMeters()) {
					log.warn("선택된 항목 없음 – 자동 검침 중단");
					stopAutoPolling();
					return;
				}
				serialManager.startSend();
				currentCycle++;
				log.info("자동 검침 {}회/{} 시작", currentCycle, targetCycle == 0 ? "∞" : String.valueOf(targetCycle));

			} catch (Exception ex) {
				log.error("자동 검침 스케줄러 예외", ex);
			}
		};
		autoTaskFuture = autoScheduler.scheduleAtFixedRate(pollingTask, 0, scanRate, TimeUnit.MILLISECONDS);
	}

	// =========================================================================
	// ✅ [수정] stopAutoPolling — 즉각 정지 + EDT 분리
	//
	// 변경 전: autoTaskFuture.cancel()만 하고 requestQueue는 방치
	//   → 큐에 남은 행들이 ModbusManager에서 계속 처리됨 (사이클 끝까지 돌다 정지)
	//
	// 변경 후: cancelPendingRequests()로 큐 즉시 비움
	//   → 현재 TX된 요청만 자연 완료(응답 or 타임아웃), 나머지는 즉각 취소
	//   → UI 조작은 EDT에서만 (applyStopUi)
	// =========================================================================
	public void stopAutoPolling() {
		if (autoTaskFuture != null && !autoTaskFuture.isCancelled()) {
			autoTaskFuture.cancel(false);
			log.info("자동 검침 중지 (완료: {}회)", currentCycle);
			appendSystemLog(new LogDto("SYSTEM", String.format("자동 검침 중지 (완료: %d회)\n", currentCycle)));
		}
		// ✅ 큐 즉시 비움 — 현재 TX된 1개 요청 완료 후 다음 행으로 넘어가지 않음
		serialManager.cancelPendingRequests();
		isAutoPolling = false; // volatile — 즉시 가시성 보장

		if (SwingUtilities.isEventDispatchThread()) {
			applyStopUi();
		} else {
			SwingUtilities.invokeLater(this::applyStopUi);
		}
	}

	// ✅ [수정] applyStopUi — pollStop() 대신 onStopComplete() 호출
	// pollStop()은 내부에서 terminal.stopAutoPolling()을 역호출하므로
	// applyStopUi() → pollStop() → stopAutoPolling() → applyStopUi() 순환이 발생했다.
	// onStopComplete()는 패널 UI만 정리하고 Terminal을 역호출하지 않으므로 순환이 끊린다.
	private void applyStopUi() {
		setRowControlButtonsEnabled(true);
		if (autoPollingPanel != null)
			autoPollingPanel.onStopComplete(); // pollStop() 아님
	}

	private void setRowControlButtonsEnabled(boolean enabled) {
		addRowBtn.setEnabled(enabled);
		clearSelectionBtn.setEnabled(enabled);
		selectAllBtn.setEnabled(enabled);
		btnDeleteSelected.setEnabled(enabled);
		rawHexControlPanel.getSendRawBtn().setEnabled(enabled);
	}

	// =========================================================================
	// 선택된 항목들 enqueue 
	// =========================================================================
	private boolean enqueueSelectedMeters() {
		boolean hasSelection = false;
		int enqueueCount = 0; // ✅ [신규] 이번 사이클 실제 큐에 들어간 수 추적
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
			PendingRequest request = serialManager.buildRequest(info);
			final int currentRow = row;
			if (request == null) {
				SwingUtilities.invokeLater(() -> meterModel.setValueAt("오류", currentRow, 10));
				continue;
			}
			serialManager.enqueue(request);
			enqueueCount++;
			SwingUtilities.invokeLater(() -> meterModel.setValueAt("요청중", currentRow, 10));
		}
		// ✅ [신규] rowsPerCycle: 첫 사이클에서 확정, 이후 동일하므로 매번 갱신해도 무해
		if (enqueueCount > 0)
			rowsPerCycle = enqueueCount;
		return hasSelection;
	}

	// =========================================================================
	// ✅ [수정] updateMeterValue — progress 추적 + 오류 즉시 알림 추가
	// =========================================================================

	// 요청 건 단위 진행률 추적 필드 (sendSelectedDataAuto에서 초기화됨)
	// ✅ volatile — enqueueSelectedMeters(autoScheduler)와 updateMeterValue(EDT) 사이 가시성
	private volatile int rowsPerCycle = 0; // 한 사이클당 검침 항목 수
	private int totalCompletedReqs = 0; // EDT 전용 (SafeEventDispatcher 보장)
	private int storedTargetCycle = 0; // EDT 전용

	public void updateMeterValue(int slaveId, int address, int fc, String value, String status) {
		Integer row = rowIndex.find(slaveId, fc, address);
		if (row == null) {
			log.warn("Slave {} FC 0x{} 주소 0x{} 에 해당하는 행 없음", slaveId, String.format("%02X", fc),
					String.format("%04X", address));
			return;
		}
		meterModel.setValueAt(value, row, 8);
		meterModel.setValueAt(status, row, 10);

		// ✅ [신규] 에러 행 배경색 추적
		// isErrorStatus=true  → errorRowSet.add(row)    → prepareRenderer가 빨간 배경 적용
		// isErrorStatus=false → errorRowSet.remove(row)  → 다음 사이클 정상 수신 시 원상복구
		if (isErrorStatus(status)) {
			errorRowSet.add(row);
		} else {
			errorRowSet.remove(row);
		}
		meterTable.repaint(); // prepareRenderer 재실행 트리거 (EDT 위에서 호출되므로 안전)

		// 요청 건 단위 진행률 갱신
		if (isAutoPolling && rowsPerCycle > 0) {
			totalCompletedReqs++;
			autoPollingPanel.updateRequestProgress(totalCompletedReqs, rowsPerCycle, storedTargetCycle);
		}

		// 오류 발생 즉시 Health Monitor 갱신
		if (isAutoPolling && isErrorStatus(status)) {
			String itemName = "Unknown";
			try {
				MeterRowDto dto = (row < meterModel.getRowCount()) ? (MeterRowDto) meterModel.getValueAt(row, 12)
						: null;
				if (dto != null)
					itemName = dto.getItemName();
			} catch (Exception ignored) {
			}
			autoPollingPanel.onImmediateError(slaveId, itemName, address, status);
		}
	}

	/** 에러 상태 문자열 판별 */
	private static boolean isErrorStatus(String status) {
		return "타임아웃".equals(status) || "CRC오류".equals(status) || "장비거부".equals(status) || "데이터오류".equals(status);
	}

	// =========================================================================
	// 오류 이력 조회
	// =========================================================================
	private void showErrorHistory() {
		errorHistoryDialog.show(this);
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
		String logContent = logPanel.getTxRxText();

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
			this.hasSelection = hasAny;
		}

	}

	// =========================================================================
	// ✅ [신규] resetPollingState — 내부 세션 정리 전용 (UI 건드리지 않음)
	//
	// sendSelectedDataAuto() 시작 시 이전 세션을 정리할 때 사용.
	// stopAutoPolling()과 달리 패널 버튼 상태를 건드리지 않는다.
	//
	// stopAutoPolling() : 사용자 명시 정지 → 스케줄 취소 + 큐 비움 + UI 정지 상태로 변경
	// resetPollingState(): 내부 세션 리셋  → 스케줄 취소 + 큐 비움만 (UI 무변경)
	// =========================================================================
	private void resetPollingState() {
		if (autoTaskFuture != null && !autoTaskFuture.isCancelled()) {
			autoTaskFuture.cancel(false);
		}
		serialManager.cancelPendingRequests();
		isAutoPolling = false;
	}

	public void resetAllMeterTable() {
		SwingUtilities.invokeLater(() -> {
			for (int row = 0; row < meterModel.getRowCount(); row++) {
				meterModel.setValueAt(false, row, 1);
				meterModel.setValueAt("-", row, 8);
				meterModel.setValueAt("대기", row, 10);
			}
			// ✅ [신규] 테이블 초기화 시 에러 하이라이트도 초기화
			errorRowSet.clear();
			meterTable.repaint();
		});
	}

	// appendTxRxTerminal → logPanel 위임
	public void appendTxRxTerminal(String hex) {
		logPanel.appendTxRx(hex);
	}

	// appendSystemLog(String, String) → logPanel 위임
	public void appendSystemLog(String level, String message) {
		logPanel.appendSystemLog(level, message);
	}

	// appendSystemLog(LogDto) → logPanel 위임
	public void appendSystemLog(LogDto dto) {
		logPanel.appendSystemLog(dto);
	}

	public void clearRaw() {
		rawHexControlPanel.getRawHexField().setText("");
		rawHexControlPanel.getRawResponseArea().setText("");
		rawHexControlPanel.getCrcPreviewLabel().setText("송신 예정: -");
	}

	public void updateRawResponseArea(String hex) {
		rawHexControlPanel.getRawResponseArea().append(hex);
	}

	private void clearErrorHighlights() {
		errorRowSet.clear();
		meterTable.repaint();
	}

	// =========================================================================
	// ✅ [수정] handlePhysicalDisconnect — 단선 시 즉각 검침 정지
	//
	// 변경 전: 알림 다이얼로그만 표시, 폴링 계속
	//   → 타임아웃 반복 → sendBytes() 예외(포트 없음) → 재귀 sendNextPacket() 루프 위험
	//
	// 변경 후:
	//   ① handlePortDisconnect() — 타임아웃 취소 + 큐 비움 + waitingResponse=false (통신 레이어 차단)
	//   ② isAutoPolling이면 스케줄러도 중단 + applyStopUi() (EDT 위임)
	//   ③ 다이얼로그는 통신 정리 완료 후 표시 (블로킹 전 정리 필수)
	// =========================================================================
	public void handlePhysicalDisconnect() {
		// ① 통신 레이어 즉각 초기화
		serialManager.handlePortDisconnect();

		// ② 자동 검침 중이면 스케줄러 중단 + UI 정리
		if (isAutoPolling) {
			if (autoTaskFuture != null && !autoTaskFuture.isCancelled()) {
				autoTaskFuture.cancel(false);
			}
			isAutoPolling = false;
			log.warn("물리 단선 감지 — 자동 검침 즉시 정지");
			appendSystemLog(new LogDto("SYSTEM", "단선이 감지되어 자동 검침을 종료합니다."));
			autoPollingPanel.getPollingIndicator().stopAnimation();
			SwingUtilities.invokeLater(this::applyStopUi);
		}

		// ③ 사용자 알림 (통신 정리 완료 후 표시)
		JOptionPane.showMessageDialog(null, "시리얼 연결이 해제 되었습니다. 물리 단선 상태를 확인하세요.");
		configPanel.updateConnectionState(false);
		appendSystemLog(new LogDto("SYSTEM", "시리얼 통신 단선이 감지되었습니다.\n"));
		if (autoPollingPanel != null)
			autoPollingPanel.incrementPhysicalDisconnect();
	}

	public void setConnected(boolean connected) {
		this.isConnected = connected;
		rawHexControlPanel.getSendRawBtn().setEnabled(connected);
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

	public boolean getIsAutoPolling() {
		return this.isAutoPolling;
	}

	// =========================================================================
	// ModbusEventListener 구현 — SafeEventDispatcher 가 EDT 라우팅 후 여기 도착
	// =========================================================================
	@Override
	public void onSystemLog(LogDto logEntry) {
		appendSystemLog(logEntry);
	}

	@Override
	public void onTxRx(String hex) {
		appendTxRxTerminal(hex);
	}

	// ✅ [수정] fc 파라미터 추가 — 인터페이스 변경에 맞춰 시그니처 동기화
	@Override
	public void onMeterValueUpdated(int slaveId, int address, int fc, String value, String status,
			int avgPacketResponseTime) {
		updateMeterValue(slaveId, address, fc, value, status);
		autoPollingPanel.getLblAvgResponseTime().setText(String.format("%d ms", avgPacketResponseTime));
	}

	@Override
	public void onRawResponse(String hex) {
		updateRawResponseArea(hex);
	}

	@Override
	public void onPhysicalDisconnect() {
		handlePhysicalDisconnect();
	}

	public static void main(String[] args) {
		com.formdev.flatlaf.FlatLightLaf.setup();
		SwingUtilities.invokeLater(() -> new ModbusTerminal().setVisible(true));
	}

}