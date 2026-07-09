package com.DSK.modbus.controller;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.table.DefaultTableModel;

import com.DSK.modbus.model.MeterRowDto;
import com.DSK.modbus.model.MeterRowIndex;
import com.DSK.modbus.service.MapFileManager;
import com.DSK.modbus.view.ModbusTerminal;

/**
 * [ProfileController]
 * 역할: 검침 맵(주소 테이블) 프로필의 생성/저장/로드/삭제/가져오기·내보내기 흐름을 담당하는 컨트롤러
 * 주요 기능:
 * - 기능 1: 프로필 CRUD — MapFileManager(파일 I/O)에 위임하고, 성공/실패를 UI 다이얼로그로 안내
 * - 기능 2: 로드 시 테이블 재구성 — meterModel 행 재적재 + MeterRowIndex 재등록 (키 정합성 유지)
 * - 기능 3: 외부 파일 가져오기/내보내기 — JFileChooser 기반, 역직렬화 검증 후 내부 폴더로 복사
 *
 * 스레드 모델: 전 메서드 EDT 전용 (버튼 리스너에서 호출, 파일 I/O가 짧아 워커 분리 생략)
 */
public class ProfileController {
	private final ModbusTerminal terminal;
	private final MapFileManager fileManager;
	private final DefaultTableModel meterModel;

	// ✅ [수정] Map<Integer,Integer> 직접 참조 → MeterRowIndex 로 전환.
	// 키를 어떻게 만드는지(MeterRowDto.rowKey)를 이 클래스가 더 이상 알 필요가
	// 없다. dto 만 넘기면 MeterRowIndex 내부에서 항상 올바른 복합키로 등록한다.
	private final MeterRowIndex rowIndex;

	private String currentProfileName = null;

	public ProfileController(ModbusTerminal terminal, MapFileManager fileManager, DefaultTableModel meterModel,
			MeterRowIndex rowIndex) {
		this.terminal = terminal;
		this.fileManager = fileManager;
		this.meterModel = meterModel;
		this.rowIndex = rowIndex;
	}

	public void saveProfile() {
		if (currentProfileName != null && !currentProfileName.trim().isEmpty()) {
			executeSave(currentProfileName);
		} else {
			saveProfileAs();
		}
	}

	public void saveProfileAs() {
		String targetName = currentProfileName + "_복사본";

		int option = JOptionPane.showConfirmDialog(terminal, "'" + targetName + "'(으)로 다른 이름으로 저장하시겠습니까?", "다른 이름으로 저장",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

		if (option == JOptionPane.YES_OPTION) {
			executeSave(targetName);
		} else
			return;
	}

	public void changeProfileName() {
		if (currentProfileName == null || currentProfileName.trim().isEmpty()) {
			JOptionPane.showMessageDialog(terminal, "저장된 프로필이 없습니다. 먼저 저장해 주세요.");
			return;
		}

		String newName = (String) JOptionPane.showInputDialog(terminal, "변경할 파일 이름을 입력하세요.", "파일 이름 변경",
				JOptionPane.PLAIN_MESSAGE, null, null, currentProfileName);

		if (newName == null || newName.trim().isEmpty())
			return;
		newName = newName.trim();

		if (newName.equals(currentProfileName))
			return;

		if (newName.matches(".*[\\\\/:*?\"<>|].*")) {
			JOptionPane.showMessageDialog(terminal, "파일명에 사용할 수 없는 문자가 포함되어 있습니다.", "입력 오류",
					JOptionPane.WARNING_MESSAGE);
			return;
		}

		// 실제 파일 이름 변경
		if (fileManager.renameProfile(currentProfileName, newName)) {
			this.currentProfileName = newName;
			terminal.refreshMapDropDown();

			if (terminal.getMapDropDown() != null) {
				terminal.getMapDropDown().setSelectedItem(newName);
			}
			JOptionPane.showMessageDialog(terminal, "프로필 이름이 '" + newName + "'(으)로 변경되었습니다.");
		} else {
			JOptionPane.showMessageDialog(terminal, "이름 변경에 실패했습니다. 동일한 이름이 이미 존재하거나 파일 접근이 제한되었습니다.", "오류",
					JOptionPane.ERROR_MESSAGE);
		}
	}

	// 📦 디스크 쓰기 처리를 담당하는 내부 공통 메소드
	private void executeSave(String profileName) {
		try {
			List<MeterRowDto> list = new ArrayList<>();
			for (int i = 0; i < meterModel.getRowCount(); i++) {
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
		if (terminal.getIsAutoPolling()) {
			JOptionPane.showMessageDialog(this.terminal, "검침 도중에는 맵 변경이 불가합니다.");
			return;
		}
		try {
			List<MeterRowDto> list = fileManager.loadProfile(selectedProfile);
			meterModel.setRowCount(0);
			rowIndex.clear();

			int no = 1;
			for (MeterRowDto d : list) {
				meterModel.addRow(new Object[] { no++, // 0: No.
						d.isSelected(), // 1: Check
						d.getSlaveId(), // 2: Slave Id
						d.getFormattedAddress(), // 3: Address ✅ [수정] 항상 Hex 하드코딩 → DTO 포맷 그대로 복원
						d.getItemName(), // 4: Name
						d.getFunctionCode(), // 5: FC
						d.getDataType(), // 6: Data Type
						d.getEndianType(), // 7: Endian
						"-", // 8: Value
						String.valueOf(d.getUnit()), // 9: Unit
						"대기", // 10: State
						String.valueOf(d.getScale()), // 11: Scale
						d // 12: DTO 객체 본체 (가려짐)
				});

				rowIndex.put(d, no - 2);
			}

			this.currentProfileName = selectedProfile;
		} catch (Exception ex) {
			JOptionPane.showMessageDialog(terminal, "로드 실패: " + ex.getMessage());
		}
	}

	public boolean deleteProfile(String selectedProfile) {
		if (selectedProfile == null)
			return false;

		int res = JOptionPane.showConfirmDialog(terminal, selectedProfile + "를 삭제할까요?", "삭제",
				JOptionPane.YES_NO_OPTION);
		if (res == JOptionPane.YES_OPTION) {
			if (fileManager.deleteProfile(selectedProfile)) {

				if (selectedProfile.equals(currentProfileName)) {
					currentProfileName = null;
				}

				terminal.refreshMapDropDown();
				meterModel.setRowCount(0);
				rowIndex.clear();
				JOptionPane.showMessageDialog(this.terminal, "\"" + selectedProfile + "\" 맵 테이블을 삭제하였습니다.");

				return true;
			} else {
				JOptionPane.showMessageDialog(terminal, "파일 삭제에 실패했습니다.");
				return false;
			}
		} else
			return false;
	}

	public void createNewProfile() {
		int res = JOptionPane.showConfirmDialog(terminal, "현재까지 작성한 내용이 초기화됩니다. 새 주소 맵을 만드시겠습니까?", "새 파일 추가",
				JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);

		if (res == JOptionPane.YES_OPTION) {
			meterModel.setRowCount(0);
			rowIndex.clear();

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
				JOptionPane.showMessageDialog(terminal, "가져오기 실패 (올바른 맵 파일 형식이 아닙니다)\n " + ex.getMessage(), "오류",
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