package com.DSK.modbus.view;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.Highlighter;

import com.DSK.modbus.model.ErrorMarkerDto;
import com.DSK.modbus.model.LogDto;
import com.DSK.modbus.view.component.ErrorMarkerScrollBar;

import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

public class ModbusLogPanel extends JPanel {

	private static final long serialVersionUID = 1L;

	// ── 로그 데이터 ─────────────────────────────────────────────────────────
	private static final int MAX_LOG_COUNT = 2000;
	private final List<LogDto> totalLogList = Collections.synchronizedList(new ArrayList<>());
	private boolean isShowSystemLog = true;
	private boolean isShowErrorLog = true;

	// ── UI ──────────────────────────────────────────────────────────────────
	private final JTextPane systemLogArea;
	private final JTextPane txrxArea;

	// ── 스크롤용 ──────────────────────────────────────────────────────────────────
	private JScrollPane txrxScroll;
	private ErrorMarkerScrollBar markerScrollBar;
	private final List<ErrorMarkerDto> errorMarkerList = Collections.synchronizedList(new ArrayList<>());

	// 비즈니스 액션이 필요한 버튼 — Terminal이 addXxxAction()으로 바인딩
	private final JButton sendSelectedOnceBtn;
	private final JButton sendSelectedOnceBtnShared;
	private final JButton viewHistoryBtn;
	private final JButton viewHistoryBtnShared;
	private final JButton btnSaveTerminal;
	private final JButton clearRxTxBtn;

	private final SimpleAttributeSet normalStyle = new SimpleAttributeSet();
	private final SimpleAttributeSet errorStyle = new SimpleAttributeSet();
	private final SimpleAttributeSet boldStyle = new SimpleAttributeSet();

	private ModbusTerminal terminal;

	// ===== [DEMO ONLY] 시연 후 삭제 =====
	// TODO: 시연 후 삭제
	private final JCheckBox chkDemoCrc;
	private final JCheckBox chkDemoException;
	// ===== [DEMO ONLY] END =====

	// =========================================================================
	// 생성자
	// =========================================================================
	public ModbusLogPanel(ModbusTerminal termianl) {
		this.terminal = termianl;

		Font boldFont = new Font("맑은 고딕", Font.BOLD, 12);
		StyleConstants.setForeground(normalStyle, Color.BLACK);
		StyleConstants.setBold(normalStyle, false);

		StyleConstants.setForeground(errorStyle, Color.RED);
		StyleConstants.setBold(errorStyle, true);

		StyleConstants.setFontFamily(boldStyle, "맑은 고딕");
		StyleConstants.setFontSize(boldStyle, 12);
		StyleConstants.setBold(boldStyle, true);
		StyleConstants.setForeground(boldStyle, Color.BLACK);

		setBackground(Color.WHITE);
		setLayout(new BorderLayout());

		// ── 텍스트 영역 ──────────────────────────────────────────────────────
		systemLogArea = buildTextArea();
		txrxArea = buildTextArea();

		// ── 오른쪽 액션 버튼 (탭별로 각각) ──────────────────────────────────
		Dimension rightBtnSize = new Dimension(160, 80);
		sendSelectedOnceBtn = buildRightBtn("체크 항목 검침", rightBtnSize, boldFont, true);
		viewHistoryBtn = buildRightBtn("오류 이력 조회", rightBtnSize, boldFont, true);
		sendSelectedOnceBtnShared = buildRightBtn("체크 항목", rightBtnSize, boldFont, true);
		viewHistoryBtnShared = buildRightBtn("오류 이력 조회", rightBtnSize, boldFont, true);
		sendSelectedOnceBtn.addPropertyChangeListener("enabled",
				evt -> sendSelectedOnceBtnShared.setEnabled((Boolean) evt.getNewValue()));

		// ── 시스템 로그 탭 구성 ───────────────────────────────────────────────
		JPanel sysRight = new JPanel(new GridLayout(2, 1, 0, 6));
		sysRight.setBackground(Color.WHITE);
		sysRight.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		sysRight.add(sendSelectedOnceBtn);
		sysRight.add(viewHistoryBtn);

		JPanel sysTab = new JPanel(new BorderLayout(5, 0));
		sysTab.setBackground(Color.WHITE);
		sysTab.add(buildScroll(systemLogArea), BorderLayout.CENTER);
		sysTab.add(sysRight, BorderLayout.EAST);

		// ── TX/RX 터미널 탭 구성 ──────────────────────────────────────────────
		JPanel rxTxRight = new JPanel(new GridLayout(2, 1, 0, 6));
		rxTxRight.setBackground(Color.WHITE);
		rxTxRight.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
		rxTxRight.add(sendSelectedOnceBtnShared);
		rxTxRight.add(viewHistoryBtnShared);

		JPanel rxTxTab = new JPanel(new BorderLayout(5, 0));
		rxTxTab.setBackground(Color.WHITE);

		txrxScroll = buildScroll(txrxArea);

		markerScrollBar = new ErrorMarkerScrollBar(txrxArea, errorMarkerList);

		txrxScroll.setVerticalScrollBar(markerScrollBar);

		rxTxTab.add(txrxScroll, BorderLayout.CENTER);
		rxTxTab.add(rxTxRight, BorderLayout.EAST);

		// ── JTabbedPane ───────────────────────────────────────────────────────
		JTabbedPane logTab = new JTabbedPane();
		logTab.setBackground(Color.WHITE);
		logTab.setForeground(Color.BLACK);
		logTab.addTab("시스템 상태 로그", sysTab);
		logTab.addTab("TX / RX 터미널", rxTxTab);

		// ── 필터 카드 패널 (탭에 겹쳐 표시) ─────────────────────────────────
		// 시스템 로그 필터 라인
		JCheckBox chkSys = buildCheckBox("시스템 로그", true);
		JCheckBox chkErr = buildCheckBox("에러 로그", true);
		JButton clearSysBtn = buildSmallBtn("로그 비우기", 120);

		// ===== [DEMO ONLY] 시연 후 삭제 =====
		chkDemoCrc = new JCheckBox("CRC 오류 주입 → 시연 후 삭제!!");
		chkDemoCrc.setForeground(Color.RED);

		chkDemoException = new JCheckBox("장비거부 주입 → 시연 후 삭제!!");
		chkDemoException.setForeground(Color.RED);
		// ===== [DEMO ONLY] END =====

		JPanel sysFilterLine = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
		sysFilterLine.setBackground(Color.WHITE);
		sysFilterLine.add(chkSys);
		sysFilterLine.add(chkErr);
		sysFilterLine.add(clearSysBtn);
		sysFilterLine.add(chkDemoCrc); // TODO: [DEMO ONLY] 시연 후 삭제

		// TX/RX 필터 라인
		btnSaveTerminal = buildSmallBtn("로그 기록 저장", 120);
		clearRxTxBtn = buildSmallBtn("로그 비우기", 120);

		JPanel rxTxFilterLine = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
		rxTxFilterLine.setBackground(Color.WHITE);
		rxTxFilterLine.add(Box.createHorizontalStrut(2));
		rxTxFilterLine.add(btnSaveTerminal);
		rxTxFilterLine.add(clearRxTxBtn);
		rxTxFilterLine.add(chkDemoException);

		CardLayout filterCard = new CardLayout();
		JPanel filterCardPanel = new JPanel(filterCard);
		filterCardPanel.setBackground(Color.WHITE);
		filterCardPanel.add(sysFilterLine, "SYS");
		filterCardPanel.add(rxTxFilterLine, "RXTX");

		logTab.addChangeListener(
				e -> filterCard.show(filterCardPanel, logTab.getSelectedIndex() == 0 ? "SYS" : "RXTX"));

		// ── 필터 패널을 탭 헤더에 겹쳐 표시하는 컨테이너 ────────────────────
		JPanel container = new JPanel(null) {
			@Override
			public void doLayout() {
				logTab.setBounds(0, 0, getWidth(), getHeight());
				int fw = filterCardPanel.getPreferredSize().width;
				int fh = filterCardPanel.getPreferredSize().height;
				filterCardPanel.setBounds(getWidth() - fw - 175, 5, fw, fh);
				super.doLayout();
			}
		};
		container.setBackground(Color.WHITE);
		container.add(filterCardPanel);
		container.add(logTab);
		container.setPreferredSize(new Dimension(0, 230));

		add(container, BorderLayout.CENTER);

		// ── 내부 이벤트 바인딩 (데이터/표시 담당 — 비즈니스 로직 제외) ────────
		chkSys.addActionListener(e -> {
			isShowSystemLog = chkSys.isSelected();
			refreshSystemLogView();
		});
		chkErr.addActionListener(e -> {
			isShowErrorLog = chkErr.isSelected();
			refreshSystemLogView();
		});
		clearSysBtn.addActionListener(e -> clearSystemLog());
		clearRxTxBtn.addActionListener(e -> clearTxRx());
	}

	// =========================================================================
	// 공개 API — ModbusTerminal이 위임 호출하는 메서드
	// =========================================================================

	/**
	 * TX/RX 터미널에 텍스트를 추가한다.
	 * EDT 안전 처리 내장 — 어느 스레드에서 호출해도 무방하다.
	 */
	public void appendTxRx(String hex) {
		StyledDocument doc = txrxArea.getStyledDocument();

		// 스크롤 마커용 저장
		int startOffset = doc.getLength();
		try {
			// RX 또는 ERROR는 빨간색 + Bold
			if (hex.contains("TIMEOUT") || (hex.contains("CRC ERROR") || (hex.contains("[EXCEPTION]")))) {
				doc.insertString(doc.getLength(), hex, boldStyle);
			} else if (hex.contains("[RETRY_EXHAUSTED]")
					|| (hex.contains("MODBUS EXCEPTION") || (hex.contains("[ERROR][PORT DISCONNECTION]")))) {
				doc.insertString(doc.getLength(), hex, errorStyle);
				doc.insertString(doc.getLength(), "\n", null);
			} else if (hex.contains("RX")) {
				doc.insertString(doc.getLength(), hex + "\n", normalStyle);
			} else {
				doc.insertString(doc.getLength(), hex, normalStyle);
			}

			// ERROR 마커 등록
			if (hex.contains("[RETRY_EXHAUSTED]") || (hex.contains("MODBUS EXCEPTION"))) {
				errorMarkerList.add(new ErrorMarkerDto(startOffset, hex));
				markerScrollBar.refreshMarkers();
			}

		} catch (

		BadLocationException e) {
			e.printStackTrace();
		}

		txrxArea.setCaretPosition(doc.getLength());
	}

	/**
	 * 시스템 로그를 추가한다.
	 * MAX_LOG_COUNT 초과 시 가장 오래된 항목 제거 (FIFO).
	 */
	public void appendSystemLog(LogDto dto) {
		if (dto == null)
			return;
		synchronized (totalLogList) {
			if (totalLogList.size() >= MAX_LOG_COUNT)
				totalLogList.remove(0);
			totalLogList.add(dto);
		}
		refreshSystemLogView();
	}

	/** appendSystemLog 오버로드 — level + message 단순 버전 */
	public void appendSystemLog(String level, String message) {
		appendSystemLog(new LogDto(level, message));
	}

	/** TX/RX 터미널 내용 전체 반환 — saveTerminalLog()에서 사용 */
	public String getTxRxText() {
		return txrxArea.getText().trim();
	}

	/** TX/RX 터미널 비우기 (확인 다이얼로그 포함) */
	public void clearTxRx() {
		if (JOptionPane.showConfirmDialog(this.terminal, "현재까지 기록된 TX/RX 로그를 삭제 하시겠습니까?", "TX / RX 로그 삭제",
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			SwingUtilities.invokeLater(() -> txrxArea.setText(""));
			errorMarkerList.clear();
			markerScrollBar.refreshMarkers();
		}
	}

	/** 시스템 로그 비우기 */
	public void clearSystemLog() {
		if (JOptionPane.showConfirmDialog(this.terminal, "현재까지 기록된 시스템 로그를 삭제 하시겠습니까?", "시스템 로그 삭제",
				JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
			synchronized (totalLogList) {
				totalLogList.clear();
			}
			SwingUtilities.invokeLater(() -> systemLogArea.setText(""));
		}
	}

	/**
	 * 검침 요청 버튼 활성화 상태 설정.
	 * sendSelectedOnceBtn만 설정하면 PropertyChangeListener가 sendSelectedOnceBtnShared를 자동 동기화.
	 */
	public void setSendButtonEnabled(boolean enabled) {
		if (sendSelectedOnceBtn != null) {
			sendSelectedOnceBtn.setEnabled(enabled);
			sendSelectedOnceBtnShared.setEnabled(enabled);
		}
	}

	// =========================================================================
	// 액션 바인딩 메서드 — ModbusTerminal의 bindAllListeners에서 호출
	// =========================================================================

	/**
	 * 검침 요청 버튼(양 탭 공통)에 액션을 등록한다.
	 * sendSelectedOnceBtnShared는 활성화 동기화만 할 뿐 액션은 따로 필요하다.
	 */
	public void addSendAction(ActionListener l) {
		sendSelectedOnceBtn.addActionListener(l);
		sendSelectedOnceBtnShared.addActionListener(l);
	}

	/**
	 * 오류 이력 조회 버튼(양 탭 공통)에 액션을 등록한다.
	 */
	public void addViewHistoryAction(ActionListener l) {
		viewHistoryBtn.addActionListener(l);
		viewHistoryBtnShared.addActionListener(l);
	}

	/** 로그 기록 저장 버튼 액션 등록 */
	public void addSaveTerminalAction(ActionListener l) {
		btnSaveTerminal.addActionListener(l);
	}

	// ===== [DEMO ONLY] 시연 후 삭제 =====
	/** CRC 오류 주입 체크박스 반환 — Terminal에서 serialManager와 바인딩 */
	public JCheckBox getChkDemoCrc() {
		return chkDemoCrc;
	}

	public JCheckBox getChkDemoException() {
		return chkDemoException;
	}
	// ===== [DEMO ONLY] END =====

	// =========================================================================
	// 내부 유틸
	// =========================================================================

	private void refreshSystemLogView() {
		SwingUtilities.invokeLater(() -> {
			StringBuilder sb = new StringBuilder();
			synchronized (totalLogList) {
				for (LogDto entry : totalLogList) {
					String type = entry.getType();
					if ("SYSTEM".equalsIgnoreCase(type) && !isShowSystemLog)
						continue;
					if ("ERROR".equalsIgnoreCase(type) && !isShowErrorLog)
						continue;
					sb.append(entry.toString()).append("\n");
				}
			}
			systemLogArea.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
			systemLogArea.setText(sb.toString());
			systemLogArea.setCaretPosition(systemLogArea.getDocument().getLength());
		});
	}

	private static JTextPane buildTextArea() {

		JTextPane pane = new JTextPane();
		pane.setEditable(false);
		pane.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
		pane.setBorder(BorderFactory.createEmptyBorder(5, 8, 5, 8));
		pane.setBackground(Color.WHITE);

		return pane;
	}

	private static JScrollPane buildScroll(JTextPane area) {
		JScrollPane sp = new JScrollPane(area);
		sp.getViewport().setBackground(Color.WHITE);
		sp.setBorder(BorderFactory.createLineBorder(new Color(240, 240, 240)));
		return sp;
	}

	private static JButton buildRightBtn(String label, Dimension size, Font font, boolean enabled) {
		JButton b = new JButton(label);
		b.setFont(font != null ? font : new Font("맑은 고딕", Font.BOLD, 12));
		b.setBackground(Color.WHITE);
		b.setForeground(Color.BLACK);
		b.setPreferredSize(size);
		b.setEnabled(enabled);
		return b;
	}

	private static JButton buildSmallBtn(String label, int width) {
		JButton b = new JButton(label);
		b.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		b.setBackground(Color.WHITE);
		b.setForeground(Color.BLACK);
		b.setPreferredSize(new Dimension(width, 23));
		return b;
	}

	private static JCheckBox buildCheckBox(String label, boolean selected) {
		JCheckBox c = new JCheckBox(label, selected);
		c.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		c.setBackground(Color.WHITE);
		return c;
	}

	public void moveToKeyword(String keyword) {
		String text = systemLogArea.getText();
		int index = text.indexOf(keyword);
		if (index < 0)
			return;
		systemLogArea.setCaretPosition(index);
	}

	public void highlightKeyword(String keyword) {
		String text = systemLogArea.getText();
		int index = text.indexOf(keyword);
		if (index < 0)
			return;

		Highlighter highlighter = systemLogArea.getHighlighter();
		highlighter.removeAllHighlights();

		try {
			highlighter.addHighlight(index, index + keyword.length(),
					new DefaultHighlighter.DefaultHighlightPainter(Color.YELLOW));
		} catch (BadLocationException e) {
			e.printStackTrace();
		}
		systemLogArea.setCaretPosition(index);
	}
}