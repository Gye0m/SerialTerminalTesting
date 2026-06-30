package com.DSK.serial.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;

import com.DSK.model.dto.common.MeterRowDto;
import com.DSK.serial.manager.MapFileManager;
import com.DSK.ui.modbus.ModbusTerminal;

public class ProfileController {
	private final ModbusTerminal terminal;
	private final MapFileManager fileManager;
	private final DefaultTableModel meterModel;
	private final Map<Integer, Integer> rowMap;

	private String currentProfileName = null;

	public ProfileController(ModbusTerminal terminal, MapFileManager fileManager, DefaultTableModel meterModel,
			Map<Integer, Integer> rowMap) {
		this.terminal = terminal;
		this.fileManager = fileManager;
		this.meterModel = meterModel;
		this.rowMap = rowMap;
	}

	public void saveProfile() {
		if (currentProfileName != null && !currentProfileName.trim().isEmpty()) {
			executeSave(currentProfileName);
		} else {
			saveProfileAs();
		}
	}

	public void saveProfileAs() {
		String name = JOptionPane.showInputDialog(terminal, "새 주소 맵 이름을 입력하세요:", currentProfileName);
		if (name == null || name.trim().isEmpty())
			return;

		executeSave(name.trim());
	}

	// 📦 디스크 쓰기 처리를 담당하는 내부 공통 메소드
	private void executeSave(String profileName) {
		try {
			List<MeterRowDto> list = new ArrayList<>();
			for (int i = 0; i < meterModel.getRowCount(); i++) {
				// 🎯 [교정 1] 10번방(State)이 아니라 DTO 본체가 들은 12번방에서 꺼냅니다.
				MeterRowDto dto = (MeterRowDto) meterModel.getValueAt(i, 12);
				if (dto != null) {
					list.add(dto);
				}
			}
			fileManager.saveProfile(profileName, list);

			this.currentProfileName = profileName;

			JOptionPane.showMessageDialog(terminal, "[" + profileName + "] 저장 완료!");
			terminal.refreshMapDropDown();

			if (terminal.getMapDropDown() != null) {
				terminal.getMapDropDown().setSelectedItem(profileName);
			}
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(terminal, "저장 실패: " + ex.getMessage());
		}
	}

	// 📂 3. 프로필 로드 (불러올 때 데이터가 밀리지 않도록 순서 완벽 고정)
	public void loadProfile(String selectedProfile) {
		if (selectedProfile == null)
			return;

		try {
			List<MeterRowDto> list = fileManager.loadProfile(selectedProfile);
			meterModel.setRowCount(0);
			rowMap.clear();

			int no = 1;
			for (MeterRowDto d : list) {
				// 🎯 [교정 2] Unit에 "대기"가 찍히던 대참사를 해결하기 위해 13칸 주입 배열의 순서를 칼같이 맞췄습니다.
				meterModel.addRow(new Object[] { no++, // 0: No.
						d.isSelected(), // 1: Check
						d.getSlaveId(), // 2: Slave Id
						String.format("0x%04X", d.getAddress()), // 3: Address
						d.getItemName(), // 4: Name
						d.getFunctionCode(), // 5: FC
						d.getDataType(), // 6: Data Type
						d.getEndianType(), // 7: Endian
						"-", // 8: Value
						String.valueOf(d.getUnit()), // 9: Unit (이제 제자리에 정상 출력됩니다)
						"대기", // 10: State
						String.valueOf(d.getScale()), // 11: Scale
						d // 12: DTO 객체 본체 (가려짐)
				});
				rowMap.put(d.getAddress(), no - 2);
			}

			this.currentProfileName = selectedProfile;
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(terminal, "로드 실패: " + ex.getMessage());
		}
	}

	public void deleteProfile(String selectedProfile) {
		if (selectedProfile == null)
			return;

		int res = JOptionPane.showConfirmDialog(terminal, selectedProfile + "를 삭제할까요?", "삭제",
				JOptionPane.YES_NO_OPTION);
		if (res == JOptionPane.YES_OPTION) {
			if (fileManager.deleteProfile(selectedProfile)) {

				if (selectedProfile.equals(currentProfileName)) {
					currentProfileName = null;
				}

				terminal.refreshMapDropDown();
				meterModel.setRowCount(0);
				rowMap.clear();
			} else {
				JOptionPane.showMessageDialog(terminal, "파일 삭제에 실패했습니다.");
			}
		}
	}

	public void createNewProfile() {
		int res = JOptionPane.showConfirmDialog(terminal, "현재까지 작성한 내용이 초기화됩니다. 새 주소 맵을 만드시겠습니까?", "새 파일 추가",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

		if (res == JOptionPane.YES_OPTION) {
			meterModel.setRowCount(0);
			rowMap.clear();

			this.currentProfileName = null;

			if (terminal.getMapDropDown() != null) {
				terminal.getMapDropDown().setSelectedIndex(-1);
			}
		}
	}

	public void importProfileFromDisk() {
		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("공유받은 검침 맵 파일(.txt) 선택");

		javax.swing.filechooser.FileNameExtensionFilter filter = new javax.swing.filechooser.FileNameExtensionFilter(
				"검침 맵 파일 (*.txt)", "txt");
		fileChooser.setFileFilter(filter);

		int userSelection = fileChooser.showOpenDialog(terminal);

		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File selectedFile = fileChooser.getSelectedFile();
			try {
				String importedProfileName = fileManager.importExternalProfile(selectedFile);

				JOptionPane.showMessageDialog(terminal, "[" + importedProfileName + "] 맵 파일 가져오기 성공!");

				terminal.refreshMapDropDown();

				if (terminal.getMapDropDown() != null) {
					terminal.getMapDropDown().setSelectedItem(importedProfileName);
				}

				loadProfile(importedProfileName);

			} catch (Exception ex) {
				JOptionPane.showMessageDialog(terminal, "가져오기 실패 (올바른 맵 파일 형식이 아닙니다): " + ex.getMessage(), "오류",
						JOptionPane.ERROR_MESSAGE);
			}
		}
	}

	// 7. 테이블 데이터를 외부 텍스트 파일로 추출하여 내보내기
	public void exportProfileFromTable() {
		if (meterModel.getRowCount() == 0) {
			JOptionPane.showMessageDialog(terminal, "내보낼 데이터가 없습니다. 테이블을 먼저 채워주세요.", "안내", JOptionPane.WARNING_MESSAGE);
			return;
		}

		JFileChooser fileChooser = new JFileChooser();
		fileChooser.setDialogTitle("검침 맵 파일(.txt) 내보내기 경로 선택");

		String defaultName = (currentProfileName != null) ? currentProfileName : "새검침맵";
		fileChooser.setSelectedFile(new File(defaultName + ".txt"));

		javax.swing.filechooser.FileNameExtensionFilter filter = new javax.swing.filechooser.FileNameExtensionFilter(
				"검침 맵 파일 (*.txt)", "txt");
		fileChooser.setFileFilter(filter);

		int userSelection = fileChooser.showSaveDialog(terminal);

		if (userSelection == JFileChooser.APPROVE_OPTION) {
			File fileToSave = fileChooser.getSelectedFile();
			try {
				List<MeterRowDto> list = new ArrayList<>();
				for (int i = 0; i < meterModel.getRowCount(); i++) {
					MeterRowDto dto = (MeterRowDto) meterModel.getValueAt(i, 12);
					if (dto != null) {
						list.add(dto);
					}
				}

				fileManager.exportProfileToExternal(fileToSave, list);

				JOptionPane.showMessageDialog(terminal, "파일 내보내기 성공!\n주소: " + fileToSave.getAbsolutePath());

			} catch (Exception ex) {
				JOptionPane.showMessageDialog(terminal, "내보내기 실패: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
			}
		}
	}
}