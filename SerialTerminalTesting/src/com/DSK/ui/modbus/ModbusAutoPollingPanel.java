package com.DSK.ui.modbus;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.model.dto.common.ErrorStatusDto;
import com.DSK.model.dto.common.LogDto;
import com.DSK.serial.manager.ModbusManager;

public class ModbusAutoPollingPanel extends JPanel {
	private static final Logger log = LoggerFactory.getLogger(ModbusAutoPollingPanel.class);
	private static final long serialVersionUID = 1L;

	private final ModbusTerminal terminal;

	// ✅ [수정] 이전엔 주석 처리되어 Health Monitor가 항상 더미 데이터만 보여주던 원인
	private final ModbusManager serialManager;

	// =========================================================================
	// 🔒 [멤버 변수 필드 구역 1] 자동 검침 (Auto Polling) 관련
	// =========================================================================
	private JLabel lblPollingStatus;
	private JLabel lblPercent;
	private JProgressBar pollingProgress;

	private JTextField txtScanRate;
	private JTextField txtRetryCount;
	private JTextField txtTargetCycle;

	// =========================================================================
	// 🔒 [멤버 변수 필드 구역 2] 통신 상태 (Health Monitor) 관련
	// =========================================================================
	private java.awt.CardLayout healthCardLayout;
	private JPanel healthCardContainer;

	private JLabel lblTotalReq;
	private JLabel lblContinuousFail;
	private JLabel lblSuccessResp;
	private JLabel lblLastErr;
	private JLabel lblErrAddr;
	private JLabel lblErrTime;

	private JLabel lblHealthVal;
	private JLabel lblErrRateVal;

	private JButton btnPollStart, btnPollStop, btnPollRefresh;

	// 팝업 관련 UI 제어 컴포넌트 변수
	private JDialog rankDialog;
	private DefaultTableModel rankingModel;

	private boolean status = false;

	// ✅ [신규] 진행률/통계 갱신 빈도 제한용 카운터
	// scanRate 가 50ms 처럼 짧을 경우 매 사이클 Health Monitor 풀스캔이 EDT 부담이 될 수 있어
	// 일정 사이클마다만 무거운 갱신(에러맵 풀스캔)을 수행한다.
	private static final int HEALTH_REFRESH_INTERVAL = 1; // 매 N 사이클마다 Health Monitor 갱신

	public ModbusAutoPollingPanel(ModbusTerminal terminal, ModbusManager serialManager) {
		super();
		this.terminal = terminal;
		this.serialManager = serialManager; // ✅ [수정] 누락된 할당 복구

		initAutoPollingComponent();
	}

	private void initAutoPollingComponent() {
		Font boldTitleFont = new Font("맑은 고딕", Font.BOLD, 12);
		Color softBorderColor = new Color(220, 225, 230);

		// 메인 베이스 컨테이너 레이아웃 (수직 적층)
		this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		this.setBackground(Color.WHITE);
		this.setMaximumSize(new Dimension(Short.MAX_VALUE, 410));
		this.setAlignmentX(Component.CENTER_ALIGNMENT);

		// =========================================================================
		// 📦 SECTION 1. 자동 검침 (Auto Polling) 제어 패널 구역
		// =========================================================================
		JPanel autoPollingWrapper = new JPanel();
		autoPollingWrapper.setLayout(new BoxLayout(autoPollingWrapper, BoxLayout.Y_AXIS));
		autoPollingWrapper.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(softBorderColor),
				"자동 검침 (Auto Polling)", TitledBorder.LEFT, TitledBorder.TOP, boldTitleFont));
		autoPollingWrapper.setBackground(Color.WHITE);
		autoPollingWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

		JPanel pollingGridRow = new JPanel(new GridLayout(4, 1, 0, 6));
		pollingGridRow.setBackground(Color.WHITE);
		pollingGridRow.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));

		lblPollingStatus = new JLabel("<html>상태: <font color='blue'><b>WAITING</b></font></html>");
		lblPollingStatus.setFont(new Font("맑은 고딕", Font.BOLD, 12));

		JPanel scanRateWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		scanRateWrapper.setBackground(Color.WHITE);
		JLabel lblScanRateTitle = new JLabel("스캔 주기: ");
		lblScanRateTitle.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		txtScanRate = new JTextField("1000");
		txtScanRate.setPreferredSize(new Dimension(65, 22));
		txtScanRate.setHorizontalAlignment(JTextField.CENTER);
		JLabel lblScanRateUnit = new JLabel(" ms");
		lblScanRateUnit.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		scanRateWrapper.add(lblScanRateTitle);
		scanRateWrapper.add(txtScanRate);
		scanRateWrapper.add(lblScanRateUnit);

		JPanel retryWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		retryWrapper.setBackground(Color.WHITE);
		JLabel lblRetryTitle = new JLabel("재시도 횟수: ");
		lblRetryTitle.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		txtRetryCount = new JTextField("3");
		txtRetryCount.setPreferredSize(new Dimension(65, 22));
		txtRetryCount.setHorizontalAlignment(JTextField.CENTER);
		JLabel lblRetryUnit = new JLabel(" 회");
		lblRetryUnit.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		retryWrapper.add(lblRetryTitle);
		retryWrapper.add(txtRetryCount);
		retryWrapper.add(lblRetryUnit);

		JPanel cycleSetWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		cycleSetWrapper.setBackground(Color.WHITE);
		JLabel lblCycleSetTitle = new JLabel("Cycle 설정: ");
		lblCycleSetTitle.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		txtTargetCycle = new JTextField("100");
		txtTargetCycle.setPreferredSize(new Dimension(65, 22));
		txtTargetCycle.setHorizontalAlignment(JTextField.CENTER);
		JLabel lblCycleSetUnit = new JLabel(" 회");
		lblCycleSetUnit.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		cycleSetWrapper.add(lblCycleSetTitle);
		cycleSetWrapper.add(txtTargetCycle);
		cycleSetWrapper.add(lblCycleSetUnit);

		pollingGridRow.add(lblPollingStatus);
		pollingGridRow.add(scanRateWrapper);
		pollingGridRow.add(retryWrapper);
		pollingGridRow.add(cycleSetWrapper);
		autoPollingWrapper.add(pollingGridRow);

		// [B] 진행률 프로그레스 바 영역
		JPanel progressRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 2));
		progressRow.setBackground(Color.WHITE);
		JLabel lblProgress = new JLabel("진행률");
		lblProgress.setFont(new Font("맑은 고딕", Font.BOLD, 11));

		pollingProgress = new JProgressBar(0, 100);
		pollingProgress.setValue(0);
		pollingProgress.setStringPainted(false);
		pollingProgress.setBackground(new Color(240, 240, 240));
		pollingProgress.setForeground(new Color(40, 167, 69));
		pollingProgress.setPreferredSize(new Dimension(280, 14));

		lblPercent = new JLabel("0 % (0/0)");
		lblPercent.setFont(new Font("맑은 고딕", Font.BOLD, 11));

		progressRow.add(lblProgress);
		progressRow.add(pollingProgress);
		progressRow.add(lblPercent);

		autoPollingWrapper.add(progressRow);
		autoPollingWrapper.add(Box.createVerticalStrut(6));

		// [C] 제어 버튼 인터페이스 (시작 / 정지 / 초기화)
		JPanel pollingBtnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 2));
		pollingBtnRow.setBackground(Color.WHITE);

		btnPollStart = new JButton("▶ 시작");
		btnPollStop = new JButton("■ 정지");
		btnPollRefresh = new JButton("초기화");

		for (JButton btn : new JButton[] { btnPollStart, btnPollStop, btnPollRefresh }) {
			btn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
			btn.setBackground(Color.WHITE);
			btn.setForeground(Color.BLACK);
			btn.setPreferredSize(new Dimension(110, 28));
		}
		btnPollStart.setForeground(new Color(40, 167, 69));
		btnPollStop.setForeground(new Color(200, 35, 51));
		btnPollRefresh.setForeground(Color.BLUE);
		btnPollStop.setEnabled(status);

		pollingBtnRow.add(btnPollStart);
		pollingBtnRow.add(btnPollStop);
		pollingBtnRow.add(btnPollRefresh);
		autoPollingWrapper.add(pollingBtnRow);

		this.add(autoPollingWrapper);
		this.add(Box.createVerticalStrut(8));

		// =========================================================================
		// 📦 SECTION 2. 통신 상태 (Health Monitor) 패널 구역
		// =========================================================================
		JPanel healthWrapper = new JPanel(new BorderLayout());
		healthWrapper.setBackground(Color.WHITE);
		healthWrapper.setBorder(BorderFactory.createLineBorder(softBorderColor));

		JPanel healthHeader = new JPanel(new BorderLayout());
		healthHeader.setBackground(Color.WHITE);
		healthHeader.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		JLabel healthTitle = new JLabel("통신 상태 (Health Monitor)");
		healthTitle.setFont(boldTitleFont);

		healthHeader.add(healthTitle, BorderLayout.WEST);

		JPanel healthPanel = new JPanel();
		healthPanel.setLayout(new BoxLayout(healthPanel, BoxLayout.Y_AXIS));
		healthPanel.setBackground(Color.WHITE);
		healthPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

		healthCardLayout = new java.awt.CardLayout();
		healthCardContainer = new JPanel(healthCardLayout);
		healthCardContainer.setBackground(Color.WHITE);

		// [화면 1] 검침 전 상태 화면
		JPanel preInspectCard = new JPanel(new java.awt.GridBagLayout());
		preInspectCard.setBackground(Color.WHITE);

		JLabel lblPreMsg = new JLabel("검침 전입니다.");
		lblPreMsg.setFont(new Font("맑은 고딕", Font.BOLD, 14));
		lblPreMsg.setForeground(new Color(130, 140, 150));
		preInspectCard.add(lblPreMsg);

		// [화면 2] 자동 검침 구동 중 가동 화면
		JPanel inspectingCard = new JPanel();
		inspectingCard.setLayout(new BoxLayout(inspectingCard, BoxLayout.Y_AXIS));
		inspectingCard.setBackground(Color.WHITE);

		JPanel healthGrid = new JPanel(new GridLayout(3, 2, 15, 5));
		healthGrid.setBackground(Color.WHITE);
		healthGrid.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

		lblTotalReq = new JLabel("요청 Cycle: ");
		lblSuccessResp = new JLabel("성공 응답 Cycle: ");
		lblContinuousFail = new JLabel("연속 실패: ");
		lblLastErr = new JLabel("마지막 오류: ");
		lblErrAddr = new JLabel("오류 주소: ");
		lblErrTime = new JLabel("에러 발생 시간: ");

		for (JLabel lbl : new JLabel[] { lblTotalReq, lblSuccessResp, lblContinuousFail, lblLastErr, lblErrAddr,
				lblErrTime }) {
			lbl.setFont(new Font("맑은 고딕", Font.BOLD, 12));
			lbl.setForeground(Color.BLACK);
		}

		lblLastErr.setForeground(new Color(180, 40, 40));

		healthGrid.add(lblTotalReq);
		healthGrid.add(lblErrAddr);
		healthGrid.add(lblSuccessResp);
		healthGrid.add(lblErrTime);
		healthGrid.add(lblContinuousFail);
		healthGrid.add(lblLastErr);

		inspectingCard.add(healthGrid);

		JPanel healthScoreRow = new JPanel();
		healthScoreRow.setLayout(new BoxLayout(healthScoreRow, BoxLayout.Y_AXIS));
		healthScoreRow.setBackground(Color.WHITE);
		healthScoreRow.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(240, 240, 240)));

		JPanel innerScoreRow1 = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 2));
		innerScoreRow1.setBackground(Color.WHITE);

		JLabel lblHealthTitle = new JLabel(); // 건강 상태
		lblHealthTitle.setFont(new Font("맑은 고딕", Font.BOLD, 12));

		lblHealthVal = new JLabel("");
		lblHealthVal.setFont(new Font("맑은 고딕", Font.BOLD, 13));
		lblHealthVal.setForeground(new Color(40, 167, 69));

		innerScoreRow1.add(lblHealthTitle);
		innerScoreRow1.add(lblHealthVal);

		JPanel innerScoreRow2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 2));
		innerScoreRow2.setBackground(Color.WHITE);

		JLabel lblErrRateTitle = new JLabel("오류 비율: ");
		lblErrRateTitle.setFont(new Font("맑은 고딕", Font.BOLD, 12));

		lblErrRateVal = new JLabel("");
		lblErrRateVal.setFont(new Font("맑은 고딕", Font.BOLD, 13));
		lblErrRateVal.setForeground(Color.BLUE);

		innerScoreRow2.add(lblErrRateTitle);
		innerScoreRow2.add(lblErrRateVal);

		healthScoreRow.add(innerScoreRow1);
		healthScoreRow.add(innerScoreRow2);

		inspectingCard.add(healthScoreRow);

		healthCardContainer.add(preInspectCard, "PRE_INSPECT");
		healthCardContainer.add(inspectingCard, "INSPECTING");

		healthPanel.add(healthCardContainer);

		healthWrapper.add(healthHeader, BorderLayout.NORTH);
		healthWrapper.add(healthPanel, BorderLayout.CENTER);

		healthCardLayout.show(healthCardContainer, "PRE_INSPECT");

		this.add(healthWrapper);

		// =========================================================================
		// 📦 오류 상세 모달창 생성
		// =========================================================================
		String[] columnNames = { "순위", "항목명", "주소", "오류 횟수", "CRC", "TIME OUT", "재시도", "연속 실패", "최근 발생 시간" };

		rankingModel = new DefaultTableModel(columnNames, 0) {
			@Override
			public boolean isCellEditable(int row, int column) {
				return false;
			}
		};

		JTable rankingTable = new JTable(rankingModel) {
			@Override
			public Component prepareRenderer(javax.swing.table.TableCellRenderer renderer, int row, int column) {
				Component c = super.prepareRenderer(renderer, row, column);
				if (!isRowSelected(row)) {
					c.setBackground(row % 2 == 0 ? Color.WHITE : new Color(248, 249, 250));
				}
				return c;
			}
		};

		rankingTable.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		rankingTable.setRowHeight(24);
		rankingTable.setShowVerticalLines(false);

		rankingTable.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 12));
		rankingTable.getTableHeader().setBackground(new Color(245, 247, 250));

		DefaultTableCellRenderer centerRender = new DefaultTableCellRenderer();
		centerRender.setHorizontalAlignment(JLabel.CENTER);

		for (int i : new int[] { 0, 2, 3, 4, 5, 6, 7, 8 }) {
			rankingTable.getColumnModel().getColumn(i).setCellRenderer(centerRender);
		}

		rankingTable.getColumnModel().getColumn(0).setPreferredWidth(45);
		rankingTable.getColumnModel().getColumn(8).setPreferredWidth(120);

		JScrollPane scrollPane = new JScrollPane(rankingTable);
		scrollPane.getViewport().setBackground(Color.WHITE);

		rankDialog = new JDialog(SwingUtilities.getWindowAncestor(this), "오류 상세 정보",
				Dialog.ModalityType.APPLICATION_MODAL);

		rankDialog.setLayout(new BorderLayout());
		rankDialog.add(scrollPane, BorderLayout.CENTER);
		rankDialog.setSize(900, 300);
		rankDialog.setLocationRelativeTo(null);

		// =========================================================================
		// [⚡ EVENT BUS LINKER] 이벤트 리스너 및 데이터 동기화 바인딩 파트
		// =========================================================================
		btnPollStart.addActionListener(e -> {
			if (!terminal.isConnected()) {
				JOptionPane.showMessageDialog(this.terminal, "통신 미연결 상태입니다.");
				return;
			}
			if (!terminal.getHasSelection()) {
				JOptionPane.showMessageDialog(this.terminal, "검침 항목을 최소 한 개 이상 선택하세요.");
				log.info("검침 항목을 최소 한 개 이상 선택하세요.");
				return;
			}

			int retryCount, scanRate, targetCycle;
			try {
				retryCount = Integer.parseInt(txtRetryCount.getText().trim());
				scanRate = Integer.parseInt(txtScanRate.getText().trim());
				targetCycle = Integer.parseInt(txtTargetCycle.getText().trim());

				if (retryCount <= 0 || scanRate <= 0 || targetCycle <= 0) {
					JOptionPane.showMessageDialog(this.terminal, "모든 설정값은 1 이상의 양의 정수여야 합니다.", "입력 오류",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this.terminal, "설정 입력 형식이 올바르지 않습니다.\n숫자만 입력해 주세요.", "파싱 오류",
						JOptionPane.ERROR_MESSAGE);
				return;
			}

			LogDto comSettingMsg = new LogDto("SYSTEM",
					(String.format("\n오토폴링 시작\n   • 스캔 주기 : %d\n   • 재시도 횟수 : %d\n   • Cycle 설정 : %d\n", scanRate,
							targetCycle, retryCount)));
			terminal.appendSystemLog(comSettingMsg);

			// ✅ [수정] 새 사이클 시작 전, 직전 검침의 에러 통계가 화면에 남아있지 않도록 초기화
			resetHealthDisplayLabels();

			healthCardLayout.show(healthCardContainer, "INSPECTING");
			setStatus(true);
			this.status = true;

			terminal.sendSelectedDataAuto(scanRate, targetCycle, retryCount);
		});

		btnPollStop.addActionListener(e -> {
			pollStop();
		});

		btnPollRefresh.addActionListener(e -> {
			if (this.status) {
				JOptionPane.showMessageDialog(this.terminal, "Auto Polling 정지 후 초기화 하십시오.");
				return;
			}
			healthCardLayout.show(healthCardContainer, "PRE_INSPECT");
			pollingProgress.setValue(0);
			lblPercent.setText("0 % (0/0)");
			rankingModel.setRowCount(0);
			resetHealthDisplayLabels();

			// ✅ [수정] 검침 카운터 초기화 시 에러 누적 통계도 함께 초기화
			// (실제 장비 통신 이력 전체를 지우는 것이므로 재확인 없이 즉시 수행하지 않고
			//  사용자가 명시적으로 "에러 정보 확인" 다이얼로그에서 별도로 초기화하도록 분리)
			terminal.resetAllMeterTable();
		});
	}

	// =========================================================================
	// 오토폴링 Getter 구역
	// =========================================================================
	public int getScanRate() {
		try {
			return Integer.parseInt(txtScanRate.getText().trim());
		} catch (Exception e) {
			return 1000;
		}
	}

	public int getRetryCount() {
		try {
			return Integer.parseInt(txtRetryCount.getText().trim());
		} catch (Exception e) {
			return 3;
		}
	}

	public int getTargetCycle() {
		try {
			return Integer.parseInt(txtTargetCycle.getText().trim());
		} catch (Exception e) {
			return 1000;
		}
	}

	public void setTotalReq(int count) {
		SwingUtilities.invokeLater(() -> lblTotalReq.setText("전체 요청:  " + count));
	}

	// =========================================================================
	// ✅ [신규 구현] updateCycleDisplay
	//
	// ModbusTerminal.sendSelectedDataAuto() 의 폴링 루프에서 매 사이클 완료 시 호출된다.
	//   SwingUtilities.invokeLater(() -> autoPollingPanel.updateCycleDisplay(cycle, targetCycle));
	//
	// 이전엔 이 메서드가 패널에 존재하지 않아 컴파일조차 되지 않았다.
	// 기존 updateCycleData(int, String, double) 와는 다른 책임을 가진다:
	//   - updateCycleData  : (레거시) 텍스트필드의 targetCycle 을 다시 파싱해서 사용 → 비활성화된
	//                        필드 값을 다시 읽는 구조라 부정확할 수 있음
	//   - updateCycleDisplay: Terminal 이 실제로 사용 중인 targetCycle 값을 인자로 직접 받아
	//                        파싱 단계 없이 정확한 진행률을 계산한다.
	//
	// 진행률 계산 외에도, 폴링이 진행 중인 매 사이클마다 Health Monitor 를
	// serialManager.getErrorMap() 의 실제 데이터로 갱신한다.
	// =========================================================================
	public void updateCycleDisplay(int currentCycle, int targetCycle) {
		SwingUtilities.invokeLater(() -> {
			// ── ① 진행률 갱신 ────────────────────────────────────────────────
			int safeTarget = (targetCycle > 0) ? targetCycle : 1;
			int progressPercent = (int) Math.min(100, (currentCycle * 100L) / safeTarget);

			pollingProgress.setValue(progressPercent);
			lblPercent.setText(
					String.format("%d %% (%d/%d)", progressPercent, currentCycle, targetCycle == 0 ? 0 : targetCycle));

			// ── ② Health Monitor 실데이터 갱신 ────────────────────────────
			if (currentCycle % HEALTH_REFRESH_INTERVAL == 0) {
				refreshHealthSnapshot(currentCycle);
			}
		});
	}

	/**
	 * serialManager.getErrorMap() 을 스캔해 Health Monitor 라벨을 채운다.
	 *
	 * [정확도에 대한 솔직한 한계]
	 * ErrorStatusDto 는 "에러 발생 시점"만 기록하므로 성공 응답 횟수를 직접 추적하지 않는다.
	 * 따라서 "전체 요청"은 currentCycle(완료된 폴링 사이클 수)을 그대로 쓰고,
	 * "성공 응답"은 (전체 요청 - 누적 에러 합계)로 근사한다.
	 * 행 단위 멀티 아이템 사이클(한 사이클에 N개 항목을 동시에 요청)인 경우
	 * 정확한 요청 건수는 currentCycle * 체크된 행 수 이지만, 이 패널은 체크 행 수를
	 * 알지 못하므로 사이클 수 단위로 표시한다. 정밀 카운팅이 필요하면
	 * ModbusManager 에 별도의 successCount AtomicLong 을 추가해야 한다.
	 */
	private void refreshHealthSnapshot(int currentCycle) {
		if (serialManager == null)
			return;

		Map<Integer, ErrorStatusDto> errorMap = serialManager.getErrorMap();

		if (errorMap.isEmpty()) {
			lblTotalReq.setText("전체 요청:  " + currentCycle);
			lblSuccessResp.setText("성공 응답:  " + currentCycle);
			lblContinuousFail.setText("연속 실패:  0");
			lblLastErr.setText("마지막 오류:  -");
			lblErrAddr.setText("오류 주소:  -");
			lblErrTime.setText("에러 발생 시간:  -");
			lblErrRateVal.setText("0.00 %");
			lblHealthVal.setText("GOOD");
			lblHealthVal.setForeground(new Color(40, 167, 69));
			return;
		}

		int totalErrSum = 0;
		int maxConsecutiveFails = 0;
		ErrorStatusDto worstEntry = null;

		for (ErrorStatusDto e : errorMap.values()) {
			totalErrSum += e.getTotalErrCount();
			// "마지막 오류" 표시는 연속 실패가 가장 심한(현재 가장 의심되는) 항목을 우선한다.
			if (e.getConsecutiveFails() > maxConsecutiveFails) {
				maxConsecutiveFails = e.getConsecutiveFails();
				worstEntry = e;
			}
		}
		// 연속 실패가 전부 0(전부 회복됨)이면, 최근 오류 시간이 가장 늦은 항목을 대표로 표시
		if (worstEntry == null) {
			worstEntry = errorMap.values().stream().max(Comparator.comparing(ErrorStatusDto::getLastErrTime))
					.orElse(null);
		}

		int successApprox = Math.max(0, currentCycle - totalErrSum);
		double errRate = (currentCycle > 0) ? (totalErrSum * 100.0 / currentCycle) : 0.0;

		lblTotalReq.setText("전체 요청:  " + currentCycle);
		lblSuccessResp.setText("성공 응답:  " + successApprox);
		lblContinuousFail.setText("연속 실패:  " + maxConsecutiveFails);

		if (worstEntry != null) {
			lblLastErr.setText("마지막 오류:  " + worstEntry.getItemName());
			lblErrAddr.setText("오류 주소:  " + String.format("0x%04X", worstEntry.getAddress()));
			lblErrTime.setText("에러 발생 시간:  " + worstEntry.getLastErrTime());
		}

		lblErrRateVal.setText(String.format("%.2f %%", errRate));

		if (errRate > 5.0 || maxConsecutiveFails >= 3) {
			lblHealthVal.setText("BAD");
			lblHealthVal.setForeground(Color.RED);
		} else {
			lblHealthVal.setText("GOOD");
			lblHealthVal.setForeground(new Color(40, 167, 69));
		}
	}

	/** Start 클릭 시 / Refresh 클릭 시 Health Monitor 라벨을 빈 상태로 되돌린다. */
	private void resetHealthDisplayLabels() {
		lblTotalReq.setText("전체 요청: ");
		lblSuccessResp.setText("성공 응답: ");
		lblContinuousFail.setText("연속 실패: ");
		lblLastErr.setText("마지막 오류: ");
		lblErrAddr.setText("오류 주소: ");
		lblErrTime.setText("에러 발생 시간: ");
		lblErrRateVal.setText("");
		lblHealthVal.setText("");
	}

	/**
	 * (레거시 유지) 텍스트필드의 targetCycle 을 직접 다시 파싱하는 기존 메서드.
	 * 외부에서 이 메서드를 더 이상 호출하지 않더라도, 하위호환을 위해 보존한다.
	 * 신규 호출부는 모두 updateCycleDisplay(int, int) 를 사용해야 한다.
	 */
	@Deprecated
	public void updateCycleData(int cycleCount, String currentName, double avgCycleTime) {
		SwingUtilities.invokeLater(() -> {
			int targetCycle = 1000;
			try {
				int inputCycle = Integer.parseInt(txtTargetCycle.getText().trim());
				if (inputCycle > 0)
					targetCycle = inputCycle;
			} catch (NumberFormatException e) {
				// 입력값 파싱 실패 시 기본값(1000) 유지
			}
			updateCycleDisplay(cycleCount, targetCycle);
		});
	}

	public void setStatus(boolean isAutoPollingStarted) {
		SwingUtilities.invokeLater(() -> {
			Color disabledBg = new Color(240, 242, 245);
			Color enabledBg = Color.WHITE;

			if (isAutoPollingStarted) {
				lblPollingStatus.setText("<html>상태: <font color='green'><b>RUNNING</b></font></html>");
				for (JTextField field : new JTextField[] { txtScanRate, txtRetryCount, txtTargetCycle }) {
					field.setEnabled(false);
					field.setEditable(false);
					field.setBackground(disabledBg);
				}
				btnPollStart.setEnabled(false);
				btnPollStop.setEnabled(true);
			} else {
				lblPollingStatus.setText("<html>상태: <font color='blue'><b>WAITING</b></font></html>");
				for (JTextField field : new JTextField[] { txtScanRate, txtRetryCount, txtTargetCycle }) {
					field.setEnabled(true);
					field.setEditable(true);
					field.setBackground(enabledBg);
				}
				btnPollStart.setEnabled(true);
				btnPollStop.setEnabled(false);
			}
		});
	}

	// =========================================================================
	// ⚡ 통신 상태 실시간 Setter 구역 (멀티스레드 안전성 확보 완료)
	// =========================================================================

	public void updateReqRespData(int totalReq, int successResp) {
		SwingUtilities.invokeLater(() -> {
			lblTotalReq.setText("전체 요청:  " + totalReq);
			lblSuccessResp.setText("성공 응답:  " + successResp);
		});
	}

	public void updateHealthData(int totalReq, int failCount, int successResp, String lastErr, int timeout,
			String errAddr, int crcErr, String errTime, int totalRetry, double errRate) {
		SwingUtilities.invokeLater(() -> {
			lblContinuousFail.setText("연속 실패:  " + failCount);
			lblLastErr.setText("마지막 오류:  " + lastErr);
			lblErrAddr.setText("오류 주소:  " + errAddr);
			lblErrTime.setText("에러 발생 시간:  " + errTime);
			lblErrRateVal.setText(String.format("%.2f %%", errRate));

			if (errRate > 5.0) {
				lblHealthVal.setText("BAD");
				lblHealthVal.setForeground(Color.RED);
			} else {
				lblHealthVal.setText("GOOD");
				lblHealthVal.setForeground(new Color(40, 167, 69));
			}
		});
	}

	// =========================================================================
	// ✅ [수정] 에러 랭킹 다이얼로그 – 더미 데이터 제거, 실제 ErrorStatusDto 기반
	// =========================================================================

	/**
	 * "에러 정보 확인" 버튼 클릭 시 호출.
	 * serialManager.getErrorMap() 에서 totalErrCount 내림차순 정렬 후 상위 10개를 표시한다.
	 * 이전 코드는 rankingModel.getRowCount()==0 일 때 하드코딩된 3개 행을 강제로 삽입했는데,
	 * 이는 실제 운영 환경에서 가짜 통계를 보여주는 치명적 결함이었다.
	 */
	public void refreshErrorRankingDialog() {
		if (serialManager == null) {
			JOptionPane.showMessageDialog(this.terminal, "통신 매니저가 초기화되지 않았습니다.");
			return;
		}

		Map<Integer, ErrorStatusDto> errorMap = serialManager.getErrorMap();

		rankingModel.setRowCount(0); // 이전 내용 클리어

		if (errorMap.isEmpty()) {
			JOptionPane.showMessageDialog(this.terminal, "기록된 오류 이력이 없습니다.", "오류 상세 정보",
					JOptionPane.INFORMATION_MESSAGE);
			return;
		}

		List<ErrorStatusDto> sorted = errorMap.values().stream()
				.sorted(Comparator.comparingInt(ErrorStatusDto::getTotalErrCount).reversed()).limit(10)
				.collect(Collectors.toList());

		int rank = 1;
		for (ErrorStatusDto e : sorted) {
			rankingModel.addRow(new Object[] { rank++, e.getItemName(), String.format("0x%04X", e.getAddress()),
					e.getTotalErrCount(), e.getCrcErrCount(), e.getTimeoutCount(), e.getTotalRetryCount(),
					e.getConsecutiveFails(), e.getLastErrTime() });
		}

		rankDialog.setLocationRelativeTo(this.terminal);
		rankDialog.setVisible(true);
	}

	// ================ 에러 테이블 갱신 (외부 호출용 – 유지) ================
	public void updateErrorTable(List<Object[]> errList) {
		SwingUtilities.invokeLater(() -> {
			rankingModel.setRowCount(0);

			if (errList == null || errList.isEmpty()) {
				return;
			}

			int rank = 1;
			for (Object[] rowData : errList) {
				Object[] finalRow = new Object[rowData.length + 1];
				finalRow[0] = rank++;
				System.arraycopy(rowData, 0, finalRow, 1, rowData.length);

				rankingModel.addRow(finalRow);

				if (rank > 10)
					break;
			}
		});
	}

	public void pollStop() {
		setStatus(false);
		this.status = false;
		terminal.stopAutoPolling();
		btnPollStop.setEnabled(status);
	}
}