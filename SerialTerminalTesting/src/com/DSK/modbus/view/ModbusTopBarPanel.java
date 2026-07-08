package com.DSK.modbus.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Image;
import java.awt.Insets;
import java.text.SimpleDateFormat;
import java.util.Date;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;

import com.DSK.modbus.model.constant.DataType;
import com.DSK.modbus.model.constant.EndianType;

public class ModbusTopBarPanel extends JPanel {

	private static final long serialVersionUID = 1L;
	// ──────────────── 좌측 시계 ────────────────
	private JLabel liveClockLabel;

	// ──────────────── 주소 설정 그룹 ────────────────
	private JTextField txtStartAddr;
	private JTextField txtSlaveId;
	private JTextField txtAddRowCount;
	private JRadioButton rdoHex;
	private JRadioButton rdoDec;
	private JComboBox<DataType> defaultDataTypeDropDown;
	private JComboBox<EndianType> defaultEndianDropDown;

	// ──────────────── 행 제어 그룹 ────────────────
	private JButton addRowBtn;
	private JButton clearSelectionBtn;
	private JButton selectAllBtn;
	private JButton btnDeleteSelected;

	// ──────────────── 맵 파일 관리 그룹 ────────────────
	private JComboBox<String> mapFileCombo;

	public ModbusTopBarPanel() {
		super(new BorderLayout());
		initComponents();
		startLiveClock();

		// 🚀 창이 화면에 완전히 나타나는 시점을 감지해서 텍스트 필드의 포커스를 뺏음
		this.addHierarchyListener(e -> {
			// 컴포넌트가 화면에 보여지는 상태(SHOWING)로 변경되었을 때
			if ((e.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0 && isShowing()) {
				javax.swing.SwingUtilities.invokeLater(() -> {
					// 패널 자체가 포커스를 받도록 설정
					this.setFocusable(true);
					this.requestFocusInWindow();

					// 텍스트 필드의 드래그(선택) 영역을 확실하게 초기화
					txtStartAddr.setSelectionStart(0);
					txtStartAddr.setSelectionEnd(0);
				});
			}
		});
	}

	private void initComponents() {
		Color softBorderColor = new Color(225, 230, 235);
		setBackground(Color.WHITE);

		// 위/아래 2줄 구성을 위해 BorderLayout 사용
		setLayout(new BorderLayout(0, 4));
		setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, softBorderColor),
				BorderFactory.createEmptyBorder(4, 8, 4, 8)));

		// ── [1층 - 시계, 로고] ───────────────────────────────
		JPanel topRowPanel = new JPanel(new BorderLayout());
		topRowPanel.setBackground(Color.WHITE);

		ImageIcon icon = new ImageIcon(getClass().getResource("/images/DSK Logo.png"));

		Image scaled = icon.getImage().getScaledInstance(90, 40, Image.SCALE_SMOOTH);

		JLabel logoLabel = new JLabel(new ImageIcon(scaled));

		liveClockLabel = new JLabel(getCurrentTime());
		liveClockLabel.setFont(new Font("JetBrains Mono", Font.BOLD, 18));
		liveClockLabel.setForeground(Color.BLACK);

		topRowPanel.add(logoLabel, BorderLayout.WEST);
		topRowPanel.add(liveClockLabel, BorderLayout.EAST);

		// ── [2층 - 하단] 복합 정렬 영역 (맵 파일: 좌측 / 설정·제어: 우측) ────────────────
		JPanel bottomRowPanel = new JPanel(new BorderLayout());
		bottomRowPanel.setBackground(Color.WHITE);

		// 각 그룹 패널 빌드
		JPanel addrGroupPanel = buildAddressGroupPanel();
		JPanel rowControlGroup = buildRowControlGroup();
		JPanel mapFileGroup = buildMapFileGroup();

		// 1. [하단 좌측 배치] 맵 파일 관리 그룹용 래퍼 (왼쪽 정렬)
		JPanel leftControlsWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
		leftControlsWrapper.setBackground(Color.WHITE);
		leftControlsWrapper.add(mapFileGroup);

		// 2. [하단 우측 배치] 행 추가 및 행 제어 그룹용 래퍼 (오른쪽 정렬)
		JPanel rightControlsWrapper = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 2));
		rightControlsWrapper.setBackground(Color.WHITE);
		rightControlsWrapper.add(addrGroupPanel);
		rightControlsWrapper.add(Box.createHorizontalStrut(8));
		rightControlsWrapper.add(rowControlGroup);

		// 하단 메인 패널에 각각 좌측(WEST)과 우측(EAST)으로 바인딩
		bottomRowPanel.add(leftControlsWrapper, BorderLayout.WEST);
		bottomRowPanel.add(rightControlsWrapper, BorderLayout.EAST);

		// ── 메인 패널에 1층(상단)과 2층(하단)을 최종 조립 ───────────────────────────
		// =========================================================================
		// 🚀 [수정된 코드] 창이 좁아질 때를 대비한 가로 스크롤 래퍼 적용
		// =========================================================================	
		JScrollPane bottomScroll = new JScrollPane(bottomRowPanel);
		bottomScroll.setBorder(null); // 스크롤바 컴포넌트의 지저분한 기본 테두리 제거
		bottomScroll.getViewport().setBackground(Color.WHITE); // 배경색 일치 (하얗게 붕 뜨는 현상 방지)
		bottomScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER); // 세로 스크롤은 절대 안 생기게 막음
		bottomScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED); // 가로폭이 모자랄 때만 스크롤 생성

		// ── 메인 패널에 1층(상단)과 2층(하단 래퍼)을 최종 조립 ───────────────────────────
		add(topRowPanel, BorderLayout.NORTH);
		add(bottomScroll, BorderLayout.SOUTH);
	}

	// =========================================================================
	// [주소 설정 그룹] – 맨 오른쪽에 [행 추가 (+)] 버튼 추가
	// =========================================================================
	private JPanel buildAddressGroupPanel() {
		Font groupTitleFont = new Font("맑은 고딕", Font.BOLD, 10);
		Color groupBorderColor = new Color(210, 215, 225);

		Font labelFont = new Font("맑은 고딕", Font.PLAIN, 11);
		Color labelColor = new Color(80, 90, 100);

		JPanel addrGroupPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2));
		addrGroupPanel.setBackground(Color.WHITE);

		addrGroupPanel.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(groupBorderColor),
				"행 추가 기본 설정", TitledBorder.LEFT, TitledBorder.TOP, groupTitleFont));

		// 1. 시작 주소
		JLabel lblStartAddr = new JLabel("시작 주소:");
		lblStartAddr.setFont(labelFont);
		lblStartAddr.setForeground(labelColor);

		txtStartAddr = new JTextField("0x0000");
		txtStartAddr.setPreferredSize(new Dimension(65, 20));
		txtStartAddr.setFont(new Font("D2Coding", Font.PLAIN, 11));
		txtStartAddr.setBorder(BorderFactory.createLineBorder(new Color(160, 180, 205), 1));
		txtStartAddr.setHorizontalAlignment(JTextField.CENTER);

		rdoHex = new JRadioButton("HEX", true);
		rdoDec = new JRadioButton("DEC");
		for (JRadioButton rdo : new JRadioButton[] { rdoHex, rdoDec }) {
			rdo.setFont(new Font("맑은 고딕", Font.PLAIN, 10));
			rdo.setBackground(Color.WHITE);
			rdo.setForeground(Color.BLACK);
			rdo.setMargin(new Insets(0, 0, 0, 0));
		}
		ButtonGroup addrTypeGroup = new ButtonGroup();
		addrTypeGroup.add(rdoHex);
		addrTypeGroup.add(rdoDec);

		// 2. Slave ID
		JLabel lblSlaveId = new JLabel("Slave ID:");
		lblSlaveId.setFont(labelFont);
		lblSlaveId.setForeground(labelColor);

		txtSlaveId = new JTextField("1");
		txtSlaveId.setPreferredSize(new Dimension(40, 20));
		txtSlaveId.setFont(new Font("D2Coding", Font.PLAIN, 11));
		txtSlaveId.setBorder(BorderFactory.createLineBorder(new Color(160, 180, 205), 1));
		txtSlaveId.setHorizontalAlignment(JTextField.CENTER);

		// 3. 타입
		JLabel lblDataType = new JLabel("Data Type:");
		lblDataType.setFont(labelFont);
		lblDataType.setForeground(labelColor);

		defaultDataTypeDropDown = new JComboBox<>(DataType.values());
		defaultDataTypeDropDown.setPreferredSize(new Dimension(100, 20));
		defaultDataTypeDropDown.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		defaultDataTypeDropDown.setBackground(Color.WHITE);

		// 4. 엔디안
		JLabel lblEndian = new JLabel("Endian Type:");
		lblEndian.setFont(labelFont);
		lblEndian.setForeground(labelColor);

		defaultEndianDropDown = new JComboBox<>(EndianType.values());
		defaultEndianDropDown.setPreferredSize(new Dimension(180, 20));
		defaultEndianDropDown.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		defaultEndianDropDown.setBackground(Color.WHITE);

		// 5. 몇 개의 행 추가
		txtAddRowCount = new JTextField("1");
		txtAddRowCount.setPreferredSize(new Dimension(35, 20)); // 💡 20은 글자가 잘릴 수 있으니 가로를 35 정도로 살짝 늘리는 걸 추천합니다!
		txtAddRowCount.setFont(new Font("D2Coding", Font.PLAIN, 11));
		txtAddRowCount.setBackground(Color.WHITE);
		txtAddRowCount.setForeground(Color.GRAY);
		txtAddRowCount.setHorizontalAlignment(JTextField.CENTER);

		txtAddRowCount.addFocusListener(new java.awt.event.FocusListener() {
			@Override
			public void focusGained(java.awt.event.FocusEvent e) {
				if (txtAddRowCount.getText().equals("1")) {
					txtAddRowCount.setText("");
					txtAddRowCount.setForeground(Color.BLACK);
				}
			}

			@Override
			public void focusLost(java.awt.event.FocusEvent e) {
				if (txtAddRowCount.getText().trim().isEmpty()) {
					txtAddRowCount.setText("1");
					txtAddRowCount.setForeground(Color.GRAY);
				}
			}
		});

		// 6. 행 추가 버튼 (여기로 이동)
		addRowBtn = new JButton("행 추가 (+)");
		addRowBtn.setPreferredSize(new Dimension(90, 20)); // 높이를 입력창들과 맞춰 20으로 설정
		addRowBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		addRowBtn.setBackground(Color.WHITE);
		addRowBtn.setForeground(Color.BLACK);
		addRowBtn.setMargin(new Insets(1, 2, 1, 2));

		// 컴포넌트 순차 등록
		addrGroupPanel.add(lblStartAddr);
		addrGroupPanel.add(txtStartAddr);
		addrGroupPanel.add(rdoHex);
		addrGroupPanel.add(rdoDec);
		addrGroupPanel.add(Box.createHorizontalStrut(8));

		addrGroupPanel.add(lblSlaveId);
		addrGroupPanel.add(txtSlaveId);
		addrGroupPanel.add(Box.createHorizontalStrut(8));

		addrGroupPanel.add(lblDataType);
		addrGroupPanel.add(defaultDataTypeDropDown);
		addrGroupPanel.add(Box.createHorizontalStrut(6));

		addrGroupPanel.add(lblEndian);
		addrGroupPanel.add(defaultEndianDropDown);

		// 맨 오른쪽에 버튼 결합
		addrGroupPanel.add(Box.createHorizontalStrut(10));
		addrGroupPanel.add(txtAddRowCount);
		addrGroupPanel.add(addRowBtn);

		return addrGroupPanel;
	}

	// =========================================================================
	// [행 제어 그룹] – 행 추가 버튼이 제거됨
	// =========================================================================
	private JPanel buildRowControlGroup() {
		Font groupTitleFont = new Font("맑은 고딕", Font.BOLD, 10);
		Color groupBorderColor = new Color(210, 215, 225);
		Dimension slimBtnSize = new Dimension(85, 22);
		Font compactFont = new Font("맑은 고딕", Font.PLAIN, 11);

		// 아일랜드(addRowBtn) 제거
		clearSelectionBtn = new JButton("값 초기화");
		clearSelectionBtn.setPreferredSize(slimBtnSize);
		selectAllBtn = new JButton("전체 선택");
		selectAllBtn.setPreferredSize(slimBtnSize);
		btnDeleteSelected = new JButton("선택 삭제");
		btnDeleteSelected.setPreferredSize(slimBtnSize);

		for (JButton btn : new JButton[] { clearSelectionBtn, selectAllBtn, btnDeleteSelected }) {
			btn.setFont(compactFont);
			btn.setBackground(Color.WHITE);
			btn.setForeground(Color.BLACK);
			btn.setMargin(new Insets(1, 2, 1, 2));
		}

		JPanel rowControlGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		rowControlGroup.setBackground(Color.WHITE);
		rowControlGroup.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(groupBorderColor),
				"테이블 조작", TitledBorder.LEFT, TitledBorder.TOP, groupTitleFont));

		// 나머지 버튼들만 추가
		rowControlGroup.add(selectAllBtn);
		rowControlGroup.add(clearSelectionBtn);
		rowControlGroup.add(btnDeleteSelected);

		return rowControlGroup;
	}

	// =========================================================================
	// [맵 파일 관리 그룹] – 콤보박스만 노출. 실행 로직은 ModbusTerminal 이 리스너로 바인딩.
	// =========================================================================
	private JPanel buildMapFileGroup() {
		Font groupTitleFont = new Font("맑은 고딕", Font.BOLD, 10);
		Color groupBorderColor = new Color(210, 215, 225);

		JPanel mapFileGroup = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 2));
		mapFileGroup.setBackground(Color.WHITE);
		mapFileGroup.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(groupBorderColor),
				"파일 관리", TitledBorder.LEFT, TitledBorder.TOP, groupTitleFont));

		String[] mapActions = { "저장", "다른 이름으로 저장", "새 파일 생성", "파일 내보내기" };
		mapFileCombo = new JComboBox<>(mapActions);
		mapFileCombo.setBackground(Color.WHITE);
		mapFileCombo.setFont(new Font("맑은 고딕", Font.PLAIN, 12));

		mapFileGroup.add(mapFileCombo);
		return mapFileGroup;
	}

	// =========================================================================
	// 자체 캡슐화된 시계 – 외부 호출 없이 패널 스스로 1초마다 갱신
	// =========================================================================
	private void startLiveClock() {
		new Timer(1000, e -> liveClockLabel.setText(getCurrentTime())).start();
	}

	private String getCurrentTime() {
		return new SimpleDateFormat("yyyy/MM/dd HH:mm:ss").format(new Date());
	}

	// =========================================================================
	// Getter – ModbusTerminal 이 리스너를 붙이기 위해 필요한 컴포넌트만 노출
	// =========================================================================
	public JTextField getTxtStartAddr() {
		return txtStartAddr;
	}

	public JTextField getTxtSlaveId() {
		return txtSlaveId;
	}

	public JTextField getTxtAddRowCount() {
		return txtAddRowCount;
	}

	public void setTextAddRowCount() {
		txtAddRowCount.setText("1");
	}

	public JRadioButton getRdoHex() {
		return rdoHex;
	}

	public JRadioButton getRdoDec() {
		return rdoDec;
	}

	public JComboBox<DataType> getDefaultDataTypeDropDown() {
		return defaultDataTypeDropDown;
	}

	public JComboBox<EndianType> getDefaultEndianDropDown() {
		return defaultEndianDropDown;
	}

	public JButton getAddRowBtn() {
		return addRowBtn;
	}

	public JButton getClearSelectionBtn() {
		return clearSelectionBtn;
	}

	public JButton getSelectAllBtn() {
		return selectAllBtn;
	}

	public JButton getBtnDeleteSelected() {
		return btnDeleteSelected;
	}

	public JComboBox<String> getMapFileCombo() {
		return mapFileCombo;
	}
}