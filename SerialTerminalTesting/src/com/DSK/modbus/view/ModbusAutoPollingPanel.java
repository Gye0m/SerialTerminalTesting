package com.DSK.modbus.view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.TitledBorder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.modbus.model.ErrorStatusDto;
import com.DSK.modbus.model.LogDto;
import com.DSK.modbus.service.ModbusManager;
import com.DSK.modbus.view.component.GradientProgressBar;
import com.DSK.modbus.view.component.PollingIndicator;

public class ModbusAutoPollingPanel extends JPanel {
	@SuppressWarnings("unused")
	private static final Logger log = LoggerFactory.getLogger(ModbusAutoPollingPanel.class);
	private static final long serialVersionUID = 1L;

	private final ModbusTerminal terminal;
	private final ModbusManager serialManager;

	// ── 자동 검침 UI ────────────────────────────────────────────────────────
	private JLabel lblPollingStatus;
	private JLabel lblPercent;
	private GradientProgressBar pollingProgress;
	private JTextField txtScanRate;
	private JTextField txtRetryCount;
	private JTextField txtTargetCycle;

	// ── Health Monitor ───────────────────────────────────────────────────────
	private java.awt.CardLayout healthCardLayout;
	private JPanel healthCardContainer;
	private JLabel lblTotalReq, lblContinuousFail, lblSuccessResp;
	private JLabel lblLastErr, lblErrAddr, lblErrTime, lblErrRateVal, lblAvgResponseTime;

	private JButton btnPollStart, btnPollStop, btnPollRefresh;

	// ── 물리 단선 전역 카운터 ───────────────────────────────────────────────
	private int globalPhysicalDisconnects = 0;

	// ── 사이클 단위 오염 추적 ───────────────────────────────────────────────
	private int cyclesCompleted = 0;
	private int cleanCycles = 0;
	private int contamCycles = 0;

	private boolean status = false;

	// 오류 정보 카드
	private CardLayout errInfoCardLayout;
	private JPanel errInfoCardPanel;

	private CardLayout cycleInfoCardLayout;
	private JPanel cycleInfoCardPanel;

	private PollingIndicator pollingIndicator;

	// =========================================================================
	// 생성자
	// =========================================================================
	public ModbusAutoPollingPanel(ModbusTerminal terminal, ModbusManager serialManager) {
		super();
		this.terminal = terminal;
		this.serialManager = serialManager;
		initAutoPollingComponent();
	}

	// =========================================================================
	// UI 초기화
	// =========================================================================
	private void initAutoPollingComponent() {
		Font boldTitleFont = new Font("맑은 고딕", Font.BOLD, 12);
		Color softBorderColor = new Color(220, 225, 230);

		this.setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		this.setBackground(Color.WHITE);
		this.setMaximumSize(new Dimension(Short.MAX_VALUE, 430));
		this.setAlignmentX(Component.CENTER_ALIGNMENT);

		// ── SECTION 1. 자동 검침 제어 ────────────────────────────────────
		JPanel autoPollingWrapper = new JPanel();
		autoPollingWrapper.setLayout(new BoxLayout(autoPollingWrapper, BoxLayout.Y_AXIS));
		autoPollingWrapper.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(softBorderColor),
				"자동 검침 스펙 설정 (Auto Polling)", TitledBorder.LEFT, TitledBorder.TOP, boldTitleFont));
		autoPollingWrapper.setBackground(Color.WHITE);
		autoPollingWrapper.setAlignmentX(Component.CENTER_ALIGNMENT);

		JPanel pollingGridRow = new JPanel(new GridLayout(4, 1, 0, 6));
		pollingGridRow.setBackground(Color.WHITE);
		pollingGridRow.setBorder(BorderFactory.createEmptyBorder(5, 15, 5, 15));

		Dimension labelSize = new Dimension(85, 22);
		Font defaultLabelFont = new Font("맑은 고딕", Font.BOLD, 12);

		// [1] 상태 행
		JPanel statusRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		statusRow.setBackground(Color.WHITE);
		lblPollingStatus = new JLabel("WAITING");
		lblPollingStatus.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		lblPollingStatus.setForeground(Color.BLUE);
		lblPollingStatus.setPreferredSize(new Dimension(65, 22));
		lblPollingStatus.setHorizontalAlignment(SwingConstants.LEFT);
		pollingIndicator = new PollingIndicator();
		statusRow.add(lblPollingStatus);
		statusRow.add(pollingIndicator);

		// [2] 스캔 주기 행
		JPanel scanRateWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		scanRateWrapper.setBackground(Color.WHITE);
		JLabel lblScanRateTitle = new JLabel("스캔 주기");
		lblScanRateTitle.setFont(defaultLabelFont);
		lblScanRateTitle.setPreferredSize(labelSize);
		txtScanRate = new JTextField("1000");
		txtScanRate.setFont(new Font("D2Coding", Font.PLAIN, 12));
		txtScanRate.setPreferredSize(new Dimension(65, 22));
		txtScanRate.setHorizontalAlignment(JTextField.CENTER);
		JLabel lblScanRateUnit = new JLabel(" ms");
		lblScanRateUnit.setFont(defaultLabelFont);
		scanRateWrapper.add(lblScanRateTitle);
		scanRateWrapper.add(txtScanRate);
		scanRateWrapper.add(lblScanRateUnit);

		// [3] 재시도 횟수 행
		JPanel retryWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		retryWrapper.setBackground(Color.WHITE);
		JLabel lblRetryTitle = new JLabel("재시도 횟수");
		lblRetryTitle.setFont(defaultLabelFont);
		lblRetryTitle.setPreferredSize(labelSize);
		txtRetryCount = new JTextField("3");
		txtRetryCount.setFont(new Font("D2Coding", Font.PLAIN, 12));
		txtRetryCount.setPreferredSize(new Dimension(65, 22));
		txtRetryCount.setHorizontalAlignment(JTextField.CENTER);
		JLabel lblRetryUnit = new JLabel(" 회");
		lblRetryUnit.setFont(defaultLabelFont);
		retryWrapper.add(lblRetryTitle);
		retryWrapper.add(txtRetryCount);
		retryWrapper.add(lblRetryUnit);

		// [4] Cycle 설정 행
		JPanel cycleSetWrapper = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		cycleSetWrapper.setBackground(Color.WHITE);
		JLabel lblCycleSetTitle = new JLabel("Cycle 설정");
		lblCycleSetTitle.setFont(defaultLabelFont);
		lblCycleSetTitle.setPreferredSize(labelSize);
		txtTargetCycle = new JTextField("10");
		txtTargetCycle.setFont(new Font("D2Coding", Font.PLAIN, 12));
		txtTargetCycle.setPreferredSize(new Dimension(65, 22));
		txtTargetCycle.setHorizontalAlignment(JTextField.CENTER);
		JLabel lblCycleSetUnit = new JLabel(" 회");
		lblCycleSetUnit.setFont(defaultLabelFont);
		cycleSetWrapper.add(lblCycleSetTitle);
		cycleSetWrapper.add(txtTargetCycle);
		cycleSetWrapper.add(lblCycleSetUnit);

		pollingGridRow.add(statusRow);
		pollingGridRow.add(scanRateWrapper);
		pollingGridRow.add(retryWrapper);
		pollingGridRow.add(cycleSetWrapper);
		autoPollingWrapper.add(pollingGridRow);

		// 진행률 행
		JPanel progressRow = new JPanel(new BorderLayout(6, 0));
		progressRow.setBackground(Color.WHITE);
		progressRow.setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));

		JLabel lblProgressTitle = new JLabel("진행률");
		lblProgressTitle.setFont(new Font("맑은 고딕", Font.BOLD, 11));
		lblProgressTitle.setPreferredSize(new Dimension(38, 16));

		pollingProgress = new GradientProgressBar(0, 100);
		pollingProgress.setValue(0);
		pollingProgress.setStringPainted(false);
		pollingProgress.setBackground(new Color(240, 240, 240));
		pollingProgress.setForeground(new Color(40, 167, 69));
		pollingProgress.setPreferredSize(new Dimension(400, 20));

		lblPercent = new JLabel("대기 중", SwingConstants.CENTER);
		lblPercent.setFont(new Font("맑은 고딕", Font.BOLD, 11));
		lblPercent.setPreferredSize(new Dimension(100, 16));

		progressRow.add(lblProgressTitle, BorderLayout.WEST);
		progressRow.add(pollingProgress, BorderLayout.CENTER);
		progressRow.add(lblPercent, BorderLayout.EAST);

		autoPollingWrapper.add(progressRow);
		autoPollingWrapper.add(Box.createVerticalStrut(6));

		// 제어 버튼
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

		// ── SECTION 2. Health Monitor ────────────────────────────────────
		JPanel healthWrapper = new JPanel(new BorderLayout());

		healthWrapper.setBackground(Color.WHITE);

		healthWrapper.setBorder(BorderFactory.createTitledBorder(BorderFactory.createLineBorder(softBorderColor),
				"통신 상태 (Health Monitor)", TitledBorder.LEFT, TitledBorder.TOP, boldTitleFont));

		JPanel healthPanel = new JPanel();
		healthPanel.setLayout(new BoxLayout(healthPanel, BoxLayout.Y_AXIS));
		healthPanel.setBackground(Color.WHITE);
		healthPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

		healthCardLayout = new java.awt.CardLayout();
		healthCardContainer = new JPanel(healthCardLayout);
		healthCardContainer.setBackground(Color.WHITE);

		// [수정 1] 전체 높이를 170에서 220으로 대폭 늘려 박스가 세로로 뻗을 수 있는 공간 확보
		healthCardContainer.setPreferredSize(new Dimension(0, 220));

		JPanel preInspectCard = new JPanel(new java.awt.GridBagLayout());
		preInspectCard.setBackground(Color.WHITE);
		JLabel lblPreMsg = new JLabel("자동검침 전입니다.");
		lblPreMsg.setFont(new Font("맑은 고딕", Font.BOLD, 14));
		lblPreMsg.setForeground(new Color(130, 140, 150));
		preInspectCard.add(lblPreMsg);

		JPanel inspectingCard = new JPanel();
		inspectingCard.setLayout(new BoxLayout(inspectingCard, BoxLayout.Y_AXIS));
		inspectingCard.setBackground(Color.WHITE);

		// 라벨 초기화 (기존 변수명 유지)
		lblTotalReq = new JLabel("완료: ");
		lblSuccessResp = new JLabel("정상: ");
		lblContinuousFail = new JLabel("오류: / 단선: 0회");
		lblLastErr = new JLabel("정보: ");
		lblErrAddr = new JLabel("주소: ");
		lblErrTime = new JLabel("마지막 발생: ");

		for (JLabel lbl : new JLabel[] { lblTotalReq, lblSuccessResp, lblContinuousFail, lblLastErr, lblErrAddr,
				lblErrTime }) {
			lbl.setFont(new Font("맑은 고딕", Font.BOLD, 12));
			lbl.setForeground(Color.BLACK);
		}

		// 좌/우 구분을 위한 메인 컨테이너 (1행 2열)
		JPanel healthSplitPanel = new JPanel(new GridLayout(1, 2, 15, 0));
		healthSplitPanel.setBackground(Color.WHITE);
		// 메인 컨테이너의 위아래 여백도 15 -> 20으로 증가
		healthSplitPanel.setBorder(BorderFactory.createEmptyBorder(15, 12, 20, 12));

		Font subGroupFont = new Font("맑은 고딕", Font.BOLD, 11);

		// 1. 좌측: 사이클 정보 그룹
		// [수정 2] 라벨 간의 세로 간격을 12 -> 18로 증가
		// 검침 완료 사이클 CardLayout
		cycleInfoCardLayout = new CardLayout();
		cycleInfoCardPanel = new JPanel(cycleInfoCardLayout);
		cycleInfoCardPanel.setBackground(Color.WHITE);

		(cycleInfoCardPanel).setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(220, 225, 230)), "검침 완료 사이클",
						TitledBorder.LEFT, TitledBorder.TOP, subGroupFont, new Color(70, 70, 70)),
				BorderFactory.createEmptyBorder(10, 10, 10, 10)));

		// 대기 화면
		JPanel cycleWaitingPanel = new JPanel(new BorderLayout());
		cycleWaitingPanel.setBackground(Color.WHITE);

		JLabel lblCycleWaiting = new JLabel("완료 사이클 대기중입니다.", SwingConstants.CENTER);

		lblCycleWaiting.setFont(new Font("맑은 고딕", Font.BOLD, 11));
		lblCycleWaiting.setForeground(Color.GRAY);

		cycleWaitingPanel.add(lblCycleWaiting, BorderLayout.CENTER);

		// 데이터 표시 화면
		JPanel cycleDataPanel = new JPanel(new GridLayout(3, 1, 0, 12));
		cycleDataPanel.setBackground(Color.WHITE);

		cycleDataPanel.add(lblTotalReq);
		cycleDataPanel.add(lblSuccessResp);
		cycleDataPanel.add(lblContinuousFail);

		// Card 등록
		cycleInfoCardPanel.add(cycleWaitingPanel, "WAITING");
		cycleInfoCardPanel.add(cycleDataPanel, "DATA");

		// 초기 상태
		cycleInfoCardLayout.show(cycleInfoCardPanel, "WAITING");

		// 2. 우측: 오류 정보 그룹
		errInfoCardLayout = new CardLayout();
		errInfoCardPanel = new JPanel(errInfoCardLayout);
		errInfoCardPanel.setBackground(Color.WHITE);

		// 공통 Border
		errInfoCardPanel.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createTitledBorder(BorderFactory.createLineBorder(new Color(255, 200, 200)),
						"오류 발생 최다 항목", TitledBorder.LEFT, TitledBorder.TOP, subGroupFont, new Color(180, 40, 40)),
				BorderFactory.createEmptyBorder(15, 10, 15, 10)));

		// ==============================
		// 오류 없음 화면
		// ==============================
		JPanel noErrorPanel = new JPanel(new BorderLayout());
		noErrorPanel.setBackground(Color.WHITE);

		JLabel lblNoError = new JLabel("오류 항목이 없습니다.", SwingConstants.CENTER);
		lblNoError.setFont(new Font("맑은 고딕", Font.BOLD, 11));
		lblNoError.setForeground(Color.GRAY);

		noErrorPanel.add(lblNoError, BorderLayout.CENTER);

		// ==============================
		// 오류 정보 화면
		// ==============================
		JPanel errInfoPanel = new JPanel(new GridLayout(3, 1, 0, 18));
		errInfoPanel.setBackground(Color.WHITE);

		errInfoPanel.add(lblLastErr);
		errInfoPanel.add(lblErrAddr);
		errInfoPanel.add(lblErrTime);

		// Card 등록
		errInfoCardPanel.add(noErrorPanel, "NO_ERROR");
		errInfoCardPanel.add(errInfoPanel, "ERROR");

		// 시작 시에는 오류가 없다고 표시
		errInfoCardLayout.show(errInfoCardPanel, "NO_ERROR");

		// 좌측 + 우측 패널 추가
		healthSplitPanel.add(cycleInfoCardPanel);
		healthSplitPanel.add(errInfoCardPanel);

		inspectingCard.add(healthSplitPanel);

		// 하단 성공률 영역
		JPanel healthScoreRow = new JPanel();
		healthScoreRow.setLayout(new BoxLayout(healthScoreRow, BoxLayout.Y_AXIS));
		healthScoreRow.setBackground(Color.WHITE);
		healthScoreRow.setBorder(BorderFactory.createCompoundBorder(
				BorderFactory.createMatteBorder(1, 0, 0, 0, new Color(240, 240, 240)),
				BorderFactory.createEmptyBorder(12, 0, 12, 0)));

		JPanel innerScoreRow2 = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 2));
		innerScoreRow2.setBackground(Color.WHITE);

		// ── Cycle 성공률 / 패킷 평균 응답시간 ─────────────────────────────

		JLabel lblErrRateTitle = new JLabel("Cycle 성공률");
		lblErrRateTitle.setFont(new Font("맑은 고딕", Font.BOLD, 12));

		lblErrRateVal = new JLabel("0%");
		lblErrRateVal.setFont(new Font("D2Coding", Font.BOLD, 13));
		lblErrRateVal.setForeground(new Color(180, 40, 40));

		JLabel lblAvgTitle = new JLabel("패킷 평균 응답시간");
		lblAvgTitle.setFont(new Font("맑은 고딕", Font.BOLD, 12));

		lblAvgResponseTime = new JLabel("0 ms");
		lblAvgResponseTime.setFont(new Font("D2Coding", Font.BOLD, 13));
		lblAvgResponseTime.setForeground(new Color(40, 100, 180));

		// 추가 순서
		innerScoreRow2.add(lblErrRateTitle);
		innerScoreRow2.add(lblErrRateVal);

		innerScoreRow2.add(Box.createHorizontalStrut(40));

		innerScoreRow2.add(lblAvgTitle);
		innerScoreRow2.add(lblAvgResponseTime);
		healthScoreRow.add(innerScoreRow2);
		inspectingCard.add(healthScoreRow);

		healthCardContainer.add(preInspectCard, "PRE_INSPECT");
		healthCardContainer.add(inspectingCard, "INSPECTING");

		healthPanel.add(healthCardContainer);
		healthWrapper.add(healthPanel, BorderLayout.CENTER);
		healthCardLayout.show(healthCardContainer, "PRE_INSPECT");
		errInfoCardLayout.show(errInfoCardPanel, "NO_ERROR");

		this.add(healthWrapper);
		// ── 이벤트 바인딩 ────────────────────────────────────────────────
		btnPollStart.addActionListener(e -> {
			if (!terminal.isConnected()) {
				JOptionPane.showMessageDialog(this.terminal, "통신 미연결 상태입니다.");
				return;
			}
			if (!terminal.getHasSelection()) {
				JOptionPane.showMessageDialog(this.terminal, "검침 항목을 최소 한 개 이상 선택하세요.");
				return;
			}

			int retryCount, scanRate, targetCycle;
			try {
				retryCount = Integer.parseInt(txtRetryCount.getText().trim());
				scanRate = Integer.parseInt(txtScanRate.getText().trim());
				targetCycle = Integer.parseInt(txtTargetCycle.getText().trim());
				if (retryCount <= 0 || scanRate <= 0 || targetCycle < 0) {
					JOptionPane.showMessageDialog(this.terminal, "스캔주기/재시도횟수는 1 이상, Cycle은 0 이상의 정수여야 합니다.", "입력 오류",
							JOptionPane.WARNING_MESSAGE);
					return;
				}
			} catch (NumberFormatException ex) {
				JOptionPane.showMessageDialog(this.terminal, "설정 입력 형식이 올바르지 않습니다.\n숫자만 입력해 주세요.", "파싱 오류",
						JOptionPane.ERROR_MESSAGE);
				return;
			}

			terminal.appendSystemLog(new LogDto("SYSTEM",
					String.format("\n자동 검침 시작\n   ├─ Scan Rate : %d ms\n   └─ Retry : %d 회\n   └─ Cycle : %s\n",
							scanRate, retryCount, targetCycle == 0 ? "무한" : targetCycle + " 회")));

			resetCycleCounters();
			resetHealthDisplayLabels();

			// 좌측: 검침 완료 사이클 대기 화면으로 전환
			cycleInfoCardLayout.show(cycleInfoCardPanel, "WAITING");

			// 💡 [추가] 우측: 오류 발생 최다 항목을 "오류 항목이 없습니다." 화면으로 리셋
			errInfoCardLayout.show(errInfoCardPanel, "NO_ERROR");

			// 전체 메인 컨테이너를 모니터링 화면으로 전환
			healthCardLayout.show(healthCardContainer, "INSPECTING");

			pollingIndicator.startAnimation();
			pollingProgress.startStripeAnimation();
			setStatus(true);
			this.status = true;

			terminal.sendSelectedDataAuto(scanRate, targetCycle, retryCount);
		});

		btnPollStop.addActionListener(e -> {
			pollingIndicator.stopAnimation();
			pollStop();
		});

		btnPollRefresh.addActionListener(e -> {
			if (this.status) {
				JOptionPane.showMessageDialog(this.terminal, "Auto Polling 정지 후 초기화 하십시오.");
				return;
			}
			setErrorInfoVisible(false);
			healthCardLayout.show(healthCardContainer, "PRE_INSPECT");
			pollingProgress.setValue(0);
			lblPercent.setText("대기 중");
			resetCycleCounters();
			resetHealthDisplayLabels();
			pollingIndicator.stopAnimation();
			globalPhysicalDisconnects = 0;
			terminal.resetAllMeterTable();
		});
	}

	// =========================================================================
	// 사이클 완료 콜백
	// =========================================================================
	public void onCycleCompleted(int completedCycles, boolean hadErrors, int targetCycle) {
		cyclesCompleted = completedCycles;
		if (hadErrors)
			contamCycles++;
		else
			cleanCycles++;
		updateHealthMonitor();
	}

	// =========================================================================
	// Health Monitor 갱신
	// ✅ [수정] 오류 정보 선정 기준 변경: 연속 실패 최다 → "누적 오류 최다" 행
	//
	// 이전: getConsecutiveFails() 최다 = "지금 연속으로 죽어있는 행"
	//       (정상 응답 1회면 0으로 리셋되어 회복 시 대상이 계속 바뀜)
	// 변경: getTotalErrCount() 최다 = "세션 전체에서 가장 문제였던 행"
	//       (누적값이라 리셋 없음 — 표시 대상이 안정적으로 유지됨)
	//
	// 라벨 갱신은 이 메서드 한 곳으로 단일화 (onImmediateError 라벨 갱신 제거).
	// 갱신 시점: onCycleCompleted() → 사이클 완료마다 스냅샷.
	// =========================================================================
	private void updateHealthMonitor() {
		if (serialManager == null)
			return;

		// 1. 좌측 사이클 텍스트 업데이트
		cycleInfoCardLayout.show(cycleInfoCardPanel, "DATA");

		lblTotalReq.setText("완료:  " + cyclesCompleted);
		lblSuccessResp.setText("정상:  " + cleanCycles);
		lblContinuousFail.setText("오류:  " + contamCycles + "  /  단선: " + globalPhysicalDisconnects + "회");

		// 2. 🎯 정확도(성공률) 계산 로직으로 전환
		// 초기 상태(진행된 사이클이 0일 때)는 기본 100.0%로 표현하는 것이 자연스럽습니다.
		double accuracyRate = 0.0;
		if (cyclesCompleted > 0) {
			accuracyRate = (cleanCycles * 100.0) / cyclesCompleted;
		}
		//		int avgResponseTime = serialManager.getavgPacketResponseTime();

		// 3. UI 반영 및 데이터 상태에 따른 색상 강조 (Optional)
		lblErrRateVal.setText(String.format("%.2f %%", accuracyRate));
		//		lblAvgResponseTime.setText(String.format("%d ms", avgResponseTime));

		// [팁] 정확도가 100%면 초록색, 떨어지면 붉은색 계열로 동적 색상 변경을 주면 피드백이 확실합니다.
		if (accuracyRate >= 97.5) {
			lblErrRateVal.setForeground(new Color(40, 167, 69)); // 정상 초록색
		} else if (accuracyRate > 90.0) {
			lblErrRateVal.setForeground(Color.ORANGE); // 경고 주황색
		} else {
			lblErrRateVal.setForeground(Color.RED); // 위험 빨간색
		}
	}

	// =========================================================================
	// 즉시 오류 알림 (사이클 완료 대기 없이 Health Monitor 즉시 갱신)
	//
	// ✅ [수정] 동률 처리 추가: 누적 오류 건수가 같으면 발생시간(lastErrTime)이
	//    최신인 행을 우선한다.
	//
	// 선정 기준 (우선순위 순):
	//   1차 — getTotalErrCount() 최다  : 세션에서 가장 문제였던 행
	//   2차 — getLastErrTime() 최신    : 동률이면 방금 오류난 쪽
	//
	// 이전 '>' 단독 비교는 동률 시 ConcurrentHashMap 순회 순서(해시 버킷 배치,
	// 비결정적)에 따라 승자가 세션/resize마다 바뀌는 표시 비일관성이 있었다.
	// worst와 직접 비교하는 방식으로 전환 — 첫 항목을 무조건 흡수하므로
	// 기존의 "전부 count 0 → lastErrTime fallback" 분기도 자연 대체된다.
	// =========================================================================
	public void onImmediateError(int slaveId, String itemName, int address, String errorType) {
		@SuppressWarnings("unchecked")
		Map<Long, ErrorStatusDto> errorMap = (Map<Long, ErrorStatusDto>) (Map<?, ?>) serialManager.getErrorMap();

		// ── 누적 오류 최다 행 선정 (동률 시 발생시간 최신 우선) ─────────────
		ErrorStatusDto worst = null;
		for (ErrorStatusDto e : errorMap.values()) {
			if (worst == null || e.getTotalErrCount() > worst.getTotalErrCount()
					|| (e.getTotalErrCount() == worst.getTotalErrCount()
							&& e.getLastErrTime().compareTo(worst.getLastErrTime()) > 0)) {
				worst = e;
			}
		}

		if (worst != null) {
			setErrorInfoVisible(true);
			lblLastErr.setText("정보: Slave " + worst.getSlaveId() + "   (누적 " + worst.getTotalErrCount() + "건)");
			lblErrAddr.setText("주소: 0x" + String.format("%04X", worst.getAddress()));
			//			lblErrTime.setText("마지막 발생: " + worst.getLastErrTime());
			// "yyyy/MM/dd HH:mm:ss.SSS" -> 앞의 11글자를 제외하고 "HH:mm:ss.SSS"만 추출
			lblErrTime.setText("마지막 발생: " + worst.getLastErrTime().substring(11));
		}
	}

	// =========================================================================
	// 진행률 갱신 (요청 건 단위)
	// =========================================================================
	public void updateRequestProgress(int completedReqs, int rowsPerCycle, int targetCycles) {
		if (rowsPerCycle <= 0)
			return;
		pollingProgress.setIndeterminate(false);
		if (targetCycles > 0) {
			int totalPlanned = targetCycles * rowsPerCycle;
			int pct = (int) Math.min(100, (completedReqs * 100L) / totalPlanned);
			int finishedCycles = completedReqs / rowsPerCycle;
			pollingProgress.setValue(pct);
			lblPercent.setText(String.format("%d / %d (%d%%)", finishedCycles, targetCycles, pct));
		} else {
			int inCycle = completedReqs % rowsPerCycle;
			int pct = (inCycle == 0) ? 100 : (inCycle * 100 / rowsPerCycle);
			pollingProgress.setValue(pct);
			lblPercent.setText(String.format("처리: %d건 / ∞", completedReqs));
		}
	}

	// =========================================================================
	// 물리 단선 카운트
	// =========================================================================
	public void incrementPhysicalDisconnect() {
		globalPhysicalDisconnects++;
		lblContinuousFail.setText("오류:  " + contamCycles + "  /  단선: " + globalPhysicalDisconnects + "회");
	}

	// =========================================================================
	// 정지 콜백 (Terminal → Panel 단방향, 역호출 없음)
	// =========================================================================
	public void onStopComplete() {
		setStatus(false);
		this.status = false;
		btnPollStop.setEnabled(false);
	}

	public void pollStop() {
		setStatus(false);
		this.status = false;
		terminal.stopAutoPolling();
	}

	// =========================================================================
	// 내부 유틸
	// =========================================================================
	private void resetCycleCounters() {
		cyclesCompleted = 0;
		cleanCycles = 0;
		contamCycles = 0;
	}

	private void resetHealthDisplayLabels() {
		lblTotalReq.setText("완료: ");
		lblSuccessResp.setText("정상: ");
		lblContinuousFail.setText("오류: ");
		lblLastErr.setText("정보: ");
		lblErrAddr.setText("주소: ");
		lblErrTime.setText("마지막 발생: ");
		lblErrRateVal.setText("");
		lblAvgResponseTime.setText("");
	}

	// =========================================================================
	// 외부 API
	// =========================================================================
	public PollingIndicator getPollingIndicator() {
		return this.pollingIndicator;
	}

	public GradientProgressBar getPollingProgress() {
		return this.pollingProgress;
	}

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
			return 10;
		}
	}

	@Deprecated
	public void updateCycleDisplay(int currentCycle, int targetCycle) {
		SwingUtilities.invokeLater(() -> onCycleCompleted(currentCycle, false, targetCycle));
	}

	public void setStatus(boolean isAutoPollingStarted) {
		SwingUtilities.invokeLater(() -> {
			Color disabledBg = new Color(240, 242, 245);
			Color enabledBg = Color.WHITE;
			if (isAutoPollingStarted) {
				lblPollingStatus.setText("<html><font color='green'><b>RUNNING</b></font></html>");
				for (JTextField f : new JTextField[] { txtScanRate, txtRetryCount, txtTargetCycle }) {
					f.setEnabled(false);
					f.setEditable(false);
					f.setBackground(disabledBg);
				}
				btnPollStart.setEnabled(false);
				btnPollStop.setEnabled(true);
			} else {
				lblPollingStatus.setText("<html><font color='blue'><b>WAITING</b></font></html>");
				for (JTextField f : new JTextField[] { txtScanRate, txtRetryCount, txtTargetCycle }) {
					f.setEnabled(true);
					f.setEditable(true);
					f.setBackground(enabledBg);
				}
				btnPollStart.setEnabled(true);
				btnPollStop.setEnabled(false);
			}
		});
	}

	/**
	 * 오류 정보 패널 표시 전환
	 *
	 * @param hasError true : 오류 정보 표시
	 *                 false : 오류 없음 표시
	 */
	public void setErrorInfoVisible(boolean hasError) {
		if (hasError) {
			errInfoCardLayout.show(errInfoCardPanel, "ERROR");
		} else {
			errInfoCardLayout.show(errInfoCardPanel, "NO_ERROR");
		}
	}

	public JLabel getLblAvgResponseTime() {
		return this.lblAvgResponseTime;
	}
}