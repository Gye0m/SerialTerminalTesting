package com.DSK.modbus.view;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Comparator;
import java.util.Map;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

import com.DSK.modbus.model.ErrorStatusDto;
import com.DSK.modbus.service.ModbusManager;

/**
 * 오류 이력 조회 다이얼로그 — 공용 컴포넌트 (정렬 필터 기능 고도화 버전)
 */
public class ModbusErrorHistoryDialog {

	private static final String[] COLUMNS = { "순위", "Slave ID", "주소", "FC", "항목명", "전체 오류", "CRC 오류", "타임아웃", "장비거부",
			"재시도", "연속 실패", "최근 발생" };
	private static final int[] COL_WIDTHS = { 40, 65, 70, 60, 130, 65, 65, 65, 65, 55, 65, 150 };

	private static final int ITEM_NAME_COL = 4;
	private static final int REFRESH_MS = 1000; // 1초 갱신

	private final ModbusManager serialManager;

	public ModbusErrorHistoryDialog(ModbusManager serialManager) {
		this.serialManager = serialManager;
	}

	public void show(java.awt.Component locationRelativeTo) {
		// ── 모델 ──────────────────────────────────────────────────────────
		DefaultTableModel model = new DefaultTableModel(COLUMNS, 0) {
			@Override
			public boolean isCellEditable(int r, int c) {
				return false;
			}
		};

		// ── 정렬 UI 및 콤보박스 초기화 ─────────────────────────────────────
		JLabel lblSortedBy = new JLabel("정렬 기준 : ");
		lblSortedBy.setFont(new Font("맑은 고딕", Font.PLAIN, 12));

		String[] filterOptions = { "최신순", "오류 개수순", "장비순" };
		JComboBox<String> cmbDeviceFilter = new JComboBox<>(filterOptions);
		cmbDeviceFilter.setFont(new Font("맑은 고딕", Font.PLAIN, 12));
		cmbDeviceFilter.setBackground(Color.WHITE);
		cmbDeviceFilter.setPreferredSize(new Dimension(130, 25));

		// ── 🎯 갱신 및 정렬 분기 Runnable ───────────────────────────
		Runnable fillData = () -> {
			@SuppressWarnings("unchecked")
			Map<Long, ErrorStatusDto> snap = new java.util.HashMap<>(
					(Map<Long, ErrorStatusDto>) (Map<?, ?>) serialManager.getErrorMap());
			model.setRowCount(0);

			// 콤보박스에서 현재 선택된 텍스트 추출 (멀티스레드 세이프 안정성 확보)
			String selectedFilter = (String) cmbDeviceFilter.getSelectedItem();
			if (selectedFilter == null)
				selectedFilter = "최신순";

			// 1. 드롭다운 선택 조건에 맞는 Comparator 매핑
			Comparator<ErrorStatusDto> comp;
			switch (selectedFilter) {
			case "오류 개수순":
				// 전체 오류 개수 내림차순 -> 같으면 최근 발생 시간 내림차순
				comp = Comparator.comparingInt(ErrorStatusDto::getTotalErrCount).reversed()
						.thenComparing(ErrorStatusDto::getLastErrTime, Comparator.nullsLast(Comparator.reverseOrder()));
				break;
			case "장비순":
				// Slave ID 오름차순 -> 같은 장비면 주소 오름차순 -> 오류 개수 내림차순
				comp = Comparator.comparingInt(ErrorStatusDto::getSlaveId).thenComparing(ErrorStatusDto::getAddress)
						.thenComparing(ErrorStatusDto::getTotalErrCount).reversed();
				break;
			case "최신순":
			default:
				// 최근 발생 시간 내림차순 (Null 데이터는 맨 뒤로 처리)
				comp = Comparator.comparing(ErrorStatusDto::getLastErrTime,
						Comparator.nullsLast(Comparator.reverseOrder()));
				break;
			}

			// 2. 스트림 정렬 및 테이블 행 삽입 순위 변수
			int[] rank = { 1 };
			snap.values().stream().sorted(comp).forEach(e -> {
				String fcStr = (e.getFunctionCode() != null) ? String.format("FC %02X", e.getFunctionCode().getCode())
						: "-";

				model.addRow(new Object[] { rank[0]++, e.getSlaveId(), String.format("0x%04X", e.getAddress()), fcStr,
						e.getItemName(), e.getTotalErrCount(), e.getCrcErrCount(), e.getTimeoutCount(),
						e.getModbusExceptionCount(), e.getTotalRetryCount(), e.getConsecutiveFails(),
						e.getLastErrTime() });
			});
		};

		// 💡 드롭다운 선택을 수동으로 변경했을 때 바로 테이블이 리프레시되도록 리스너 바인딩
		cmbDeviceFilter.addActionListener(e -> fillData.run());

		fillData.run(); // 최초 1회 즉시 실행

		// ── 테이블 및 탑 패널 구성 ────────────────────────────────────────
		JTable table = buildTable(model);
		JLabel lblStatus = buildStatusLabel(serialManager.getErrorMap().size());

		JPanel topPanel = new JPanel(new BorderLayout());
		topPanel.setBackground(Color.WHITE);
		topPanel.add(lblStatus, BorderLayout.WEST);

		JPanel sortPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
		sortPanel.setBackground(Color.WHITE);
		sortPanel.add(lblSortedBy);
		sortPanel.add(cmbDeviceFilter);
		topPanel.add(sortPanel, BorderLayout.EAST);

		// ── 버튼 패널 ─────────────────────────────────────────────────────
		JButton btnClear = new JButton("전체 초기화");
		JButton btnClose = new JButton("닫기");
		for (JButton btn : new JButton[] { btnClear, btnClose }) {
			btn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
			btn.setBackground(Color.WHITE);
		}
		JPanel btnPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
		btnPanel.setBackground(Color.WHITE);
		btnPanel.add(btnClear);
		btnPanel.add(btnClose);

		// ── 컨텐츠 패널 ───────────────────────────────────────────────────
		JScrollPane scroll = new JScrollPane(table);
		scroll.getViewport().setBackground(Color.WHITE);

		JPanel content = new JPanel(new BorderLayout(0, 4));
		content.setBackground(Color.WHITE);
		content.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		content.add(topPanel, BorderLayout.NORTH);
		content.add(scroll, BorderLayout.CENTER);
		content.add(btnPanel, BorderLayout.SOUTH);

		// ── 다이얼로그 생성 및 노출 ─────────────────────────────────────────
		java.awt.Window owner = (locationRelativeTo != null) ? SwingUtilities.getWindowAncestor(locationRelativeTo)
				: null;
		JDialog dialog = new JDialog(owner, "오류 이력 조회", Dialog.ModalityType.APPLICATION_MODAL);
		dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
		dialog.setContentPane(content);
		dialog.setSize(1200, 550);
		dialog.setLocationRelativeTo(locationRelativeTo);

		// ── Timer (1초마다 자동 데이터 동기화 및 렌더링) ────────────────────
		Timer timer = new Timer(REFRESH_MS, ev -> {
			fillData.run();
			lblStatus.setText(buildStatusText(serialManager.getErrorMap().size()));
		});

		dialog.addWindowListener(new java.awt.event.WindowAdapter() {
			@Override
			public void windowOpened(java.awt.event.WindowEvent e) {
				timer.start();
			}

			@Override
			public void windowClosed(java.awt.event.WindowEvent e) {
				timer.stop();
			}
		});

		btnClear.addActionListener(ev -> {
			if (JOptionPane.showConfirmDialog(dialog, "모든 오류 이력을 삭제하시겠습니까?", "확인",
					JOptionPane.YES_NO_OPTION) == JOptionPane.YES_OPTION) {
				serialManager.clearAllErrorStatus();
				model.setRowCount(0);
			}
		});
		btnClose.addActionListener(ev -> dialog.dispose());

		dialog.setVisible(true);
	}

	private JTable buildTable(DefaultTableModel model) {
		JTable table = new JTable(model);
		table.setFillsViewportHeight(true);
		table.setRowHeight(24);
		table.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		table.setShowVerticalLines(false);
		table.getTableHeader().setFont(new Font("맑은 고딕", Font.BOLD, 12));
		table.getTableHeader().setBackground(new Color(245, 247, 250));
		table.getTableHeader().setReorderingAllowed(false);

		for (int i = 0; i < COL_WIDTHS.length; i++) {
			table.getColumnModel().getColumn(i).setPreferredWidth(COL_WIDTHS[i]);
			DefaultTableCellRenderer r = new DefaultTableCellRenderer();
			r.setHorizontalAlignment(i == ITEM_NAME_COL ? JLabel.LEFT : JLabel.CENTER);
			table.getColumnModel().getColumn(i).setCellRenderer(r);
		}
		return table;
	}

	private JLabel buildStatusLabel(int count) {
		JLabel lbl = new JLabel(buildStatusText(count));
		lbl.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		lbl.setForeground(new Color(40, 160, 40));
		lbl.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		return lbl;
	}

	private String buildStatusText(int count) {
		return String.format(" ● 실시간 갱신 중 (%d초 간격)  |  총 %d개", REFRESH_MS / 1000, count);
	}
}