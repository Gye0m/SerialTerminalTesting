package com.DSK.ui;

import com.DSK.model.dto.*;
import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;

public class CheckListPanel extends JPanel {
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	private final MeterAddressMap meterAddressMap = new MeterAddressMap();
	private final Map<JCheckBox, Integer> selectedMap = new HashMap<>();

	private JPanel checkBoxPanel;

	public CheckListPanel() {
		setLayout(new BorderLayout());
		initComponent();
		showCategory("전기"); // 초기 카테고리 기본 지정
	}

	public void clearAllSelection() {
		if (selectedMap != null) {
			for (JCheckBox cb : selectedMap.keySet()) {
				cb.setSelected(false);
			}
		}
	}

	private void initComponent() {
		// 카테고리 버튼 영역
		JPanel categoryPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
		String[] categories = { "전기", "수도", "온수", "가스", "난방", "기타", "산업용열량계" };
		for (String category : categories) {
			JButton btn = new JButton(category);
			btn.addActionListener(e -> showCategory(category));
			categoryPanel.add(btn);
		}
		add(categoryPanel, BorderLayout.NORTH);

		// 체크박스 리스트 영역
		checkBoxPanel = new JPanel();
		checkBoxPanel.setLayout(new BoxLayout(checkBoxPanel, BoxLayout.Y_AXIS));
		JScrollPane checkScroll = new JScrollPane(checkBoxPanel);
		checkScroll.setBorder(BorderFactory.createEmptyBorder());

		// 전체 컨테이너와 제어 버튼 그룹
		JPanel totalCheckContainer = new JPanel(new BorderLayout());
		totalCheckContainer.setBorder(BorderFactory.createTitledBorder("검침 항목 선택"));

		JPanel selectBtnGroup = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 2));
		JButton btnSelectAll = new JButton("전체 선택");
		JButton btnDeselectAll = new JButton("전체 해제");

		btnSelectAll.setPreferredSize(new Dimension(85, 23));
		btnDeselectAll.setPreferredSize(new Dimension(85, 23));
		btnSelectAll.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		btnDeselectAll.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

		selectBtnGroup.add(btnSelectAll);
		selectBtnGroup.add(btnDeselectAll);

		totalCheckContainer.add(selectBtnGroup, BorderLayout.NORTH);
		totalCheckContainer.add(checkScroll, BorderLayout.CENTER);
		add(totalCheckContainer, BorderLayout.CENTER);

		// 이벤트 바인딩
		btnSelectAll.addActionListener(e -> setSelectedAll(true));
		btnDeselectAll.addActionListener(e -> setSelectedAll(false));
	}

	public void setSelectedAll(boolean select) {
		for (JCheckBox cb : selectedMap.keySet()) {
			cb.setSelected(select);
		}
	}

	private void showCategory(String category) {
		checkBoxPanel.removeAll();
		selectedMap.clear();
		List<Integer> addresses = getAddressesByCategory(category);

		for (Integer address : addresses) {
			MeterInfo info = meterAddressMap.get(address);
			if (info == null)
				continue;

			JCheckBox checkBox = new JCheckBox(info.getName() + " (" + info.getUnit() + ")");
			selectedMap.put(checkBox, address);
			checkBoxPanel.add(checkBox);
		}
		checkBoxPanel.revalidate();
		checkBoxPanel.repaint();
	}

	private List<Integer> getAddressesByCategory(String category) {
		switch (category) {
		case "전기":
			return Arrays.asList(0x0000, 0x0002, 0x0004, 0x0006, 0x0008, 0x000A, 0x000C, 0x000E, 0x0010, 0x0012, 0x0014,
					0x0016, 0x0018, 0x001A, 0x001C, 0x001E, 0x0020, 0x0022, 0x0024, 0x0026, 0x0028, 0x002A, 0x002C);
		case "수도":
			return Arrays.asList(0x002E, 0x0030);
		case "온수":
			return Arrays.asList(0x0032, 0x0034);
		case "가스":
			return Arrays.asList(0x0036, 0x0038);
		case "난방":
			return Arrays.asList(0x003A, 0x003C, 0x003E, 0x0040, 0x0042);
		case "기타":
			return Arrays.asList(0x0044, 0x0046, 0x0048, 0x004A, 0x004C);
		case "산업용열량계":
			return Arrays.asList(0x004E, 0x0052, 0x0054, 0x0058, 0x005A);
		default:
			return new ArrayList<>();
		}
	}

	public Map<JCheckBox, Integer> getSelectedMap() {
		return selectedMap;
	}

	public MeterAddressMap getMeterAddressMap() {
		return meterAddressMap;
	}
}