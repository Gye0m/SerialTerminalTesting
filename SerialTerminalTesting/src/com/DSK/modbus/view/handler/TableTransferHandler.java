package com.DSK.modbus.view.handler;

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableModel;

import com.DSK.modbus.model.MeterRowDto;
import com.DSK.modbus.model.MeterRowIndex;
import com.DSK.modbus.view.ModbusTerminal;

public class TableTransferHandler extends TransferHandler {
	private static final long serialVersionUID = 1L;

	// DTO 가 들어있는 실제 컬럼 인덱스. 매직 넘버를 여기저기 흩어두지 않고 한 곳에 모은다.
	private static final int COL_DTO = 12;
	private static final int COL_NO = 0;

	private int[] rows = null;

	private final ModbusTerminal terminal;
	private final DefaultTableModel model;

	// ✅ [수정] Map<Integer,Integer> 직접 참조 → MeterRowIndex 로 전환.
	private final MeterRowIndex rowIndex;

	public TableTransferHandler(ModbusTerminal terminal, DefaultTableModel model, MeterRowIndex rowIndex) {
		this.terminal = terminal;
		this.model = model;
		this.rowIndex = rowIndex;
	}

	@Override
	protected Transferable createTransferable(JComponent c) {
		JTable table = (JTable) c;
		rows = table.getSelectedRows();

		// 선택된 행이 없는 상태에서 드래그가 시작되면 rows.length == 0 인데
		// rows[0] 을 바로 참조하면 ArrayIndexOutOfBoundsException 이 난다.
		if (rows == null || rows.length == 0) {
			return new StringSelection("");
		}
		return new StringSelection(String.valueOf(rows[0]));
	}

	@Override
	public int getSourceActions(JComponent c) {
		return MOVE;
	}

	@Override
	public boolean canImport(TransferSupport info) {
		return info.isDrop() && info.isDataFlavorSupported(DataFlavor.stringFlavor);
	}

	@Override
	public boolean importData(TransferSupport info) {
		if (!canImport(info))
			return false;

		if (rows == null || rows.length == 0)
			return false;

		JTable table = (JTable) info.getComponent();
		JTable.DropLocation dl = (JTable.DropLocation) info.getDropLocation();
		int targetRow = dl.getRow();
		int maxRows = model.getRowCount();

		if (targetRow < 0)
			targetRow = 0;
		if (targetRow > maxRows)
			targetRow = maxRows;

		int sourceRow = rows[0];
		if (sourceRow == targetRow || sourceRow == targetRow - 1)
			return false;

		if (sourceRow < 0 || sourceRow >= maxRows)
			return false;

		// 1. 데이터 추출
		Object[] rowData = new Object[model.getColumnCount()];
		for (int i = 0; i < model.getColumnCount(); i++) {
			rowData[i] = model.getValueAt(sourceRow, i);
		}

		terminal.setUpdatingAddress(true);
		try {
			model.removeRow(sourceRow);
			if (targetRow > sourceRow) {
				targetRow--;
			}
			model.insertRow(targetRow, rowData);

			// 2. 전체 순번 및 내부 캐시 인덱스 리싱크
			rowIndex.clear();
			for (int i = 0; i < model.getRowCount(); i++) {
				model.setValueAt(i + 1, i, COL_NO); // 순번(No.) 재정렬

				MeterRowDto remainingDto = (MeterRowDto) model.getValueAt(i, COL_DTO);
				if (remainingDto != null) {
					rowIndex.put(remainingDto, i);
				}
			}

			table.setRowSelectionInterval(targetRow, targetRow);
			return true;
		} finally {
			// try-finally 로 보장 — 블록 내부 어디서 예외가 나도 isUpdatingAddress 가
			// true 로 영구 고정되어 테이블 전체 편집 기능이 마비되는 사태를 막는다.
			terminal.setUpdatingAddress(false);
		}
	}
}
