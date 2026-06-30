package com.DSK.ui.handler; // 유저님의 패키지 경로에 맞게 수정하세요.

import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.util.Map;

import javax.swing.JComponent;
import javax.swing.JTable;
import javax.swing.TransferHandler;
import javax.swing.table.DefaultTableModel;

import com.DSK.model.dto.common.MeterRowDto;
import com.DSK.ui.modbus.ModbusTerminal;

public class TableTransferHandler extends TransferHandler {
	private static final long serialVersionUID = 1L;

	private int[] rows = null;

	// 외부 파일로 분리되면서 제어해야 할 대상들을 담는 private 변수
	private final ModbusTerminal terminal;
	private final DefaultTableModel model;
	private final Map<Integer, Integer> rowMap;

	// 🎯 생성자를 통해 터미널과 모델, 주소 캐시 맵을 주입받습니다.
	public TableTransferHandler(ModbusTerminal terminal, DefaultTableModel model, Map<Integer, Integer> rowMap) {
		this.terminal = terminal;
		this.model = model;
		this.rowMap = rowMap;
	}

	@Override
	protected Transferable createTransferable(JComponent c) {
		JTable table = (JTable) c;
		rows = table.getSelectedRows();
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

		JTable table = (JTable) info.getComponent();
		JTable.DropLocation dl = (JTable.DropLocation) info.getDropLocation();
		int targetRow = dl.getRow();
		int maxRows = model.getRowCount();

		if (targetRow < 0)
			targetRow = 0;
		if (targetRow > maxRows)
			targetRow = maxRows;

		if (rows != null && rows.length > 0) {
			int sourceRow = rows[0];
			if (sourceRow == targetRow || sourceRow == targetRow - 1)
				return false;

			// 1. 데이터 추출
			Object[] rowData = new Object[model.getColumnCount()];
			for (int i = 0; i < model.getColumnCount(); i++) {
				rowData[i] = model.getValueAt(sourceRow, i);
			}

			terminal.setUpdatingAddress(true);

			model.removeRow(sourceRow);
			if (targetRow > sourceRow) {
				targetRow--;
			}
			model.insertRow(targetRow, rowData);

			// 2. 전체 순번 및 내부 캐시 맵 리싱크 정렬
			rowMap.clear();
			for (int i = 0; i < model.getRowCount(); i++) {
				model.setValueAt(i + 1, i, 0); // 순번(No.) 재정렬

				MeterRowDto remainingDto = (MeterRowDto) model.getValueAt(i, 10);
				if (remainingDto != null) {
					rowMap.put(remainingDto.getAddress(), i);
				}
			}

			terminal.setUpdatingAddress(false);

			table.setRowSelectionInterval(targetRow, targetRow);
			return true;
		}
		return false;
	}
}