package com.DSK.modbus.view;

import javax.swing.*;
import java.awt.*;

public class ModbusRawHexPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private JButton btnToggleHex;
	private JPanel rawHexPanel;
	private JTextField rawHexField;
	private JButton sendRawBtn;
	private JButton clearRawBtn;
	private JCheckBox crcAutoCheck;
	private JLabel crcPreviewLabel;
	private JTextArea rawResponseArea;

	public ModbusRawHexPanel(int contentWidth, Font boldTitleFont, Color softBorderColor) {
		setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
		setBackground(Color.WHITE);
		setAlignmentX(Component.CENTER_ALIGNMENT);

		btnToggleHex = new JButton("▼ Raw HEX 송신/분석 접기");
		btnToggleHex.setFont(boldTitleFont);
		btnToggleHex.setBackground(new Color(245, 247, 250));
		btnToggleHex.setForeground(new Color(70, 80, 95));
		btnToggleHex.setFocusPainted(false);
		btnToggleHex.setAlignmentX(Component.CENTER_ALIGNMENT);
		btnToggleHex.setMaximumSize(new Dimension(contentWidth, 26));

		// 메인 타이틀 패널
		rawHexPanel = new JPanel();
		rawHexPanel.setLayout(new BoxLayout(rawHexPanel, BoxLayout.Y_AXIS));
		rawHexPanel.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(softBorderColor),
				BorderFactory.createEmptyBorder(6, 6, 6, 6)));
		rawHexPanel.setBackground(Color.WHITE);
		rawHexPanel.setMaximumSize(new Dimension(contentWidth, 120));
		rawHexPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

		// =========================================================================
		// [HEX 1열] 입력 및 제어 라인
		// =========================================================================
		JPanel inputRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
		inputRow.setBackground(Color.WHITE);

		rawHexField = new JTextField();
		rawHexField.setPreferredSize(new Dimension(contentWidth - 210, 24));
		rawHexField.setFont(new Font("D2Coding", Font.PLAIN, 12));
		rawHexField.setBackground(Color.WHITE);
		rawHexField.setForeground(Color.BLACK);

		sendRawBtn = new JButton("전송");
		sendRawBtn.setPreferredSize(new Dimension(85, 24));
		sendRawBtn.setBackground(Color.WHITE);
		sendRawBtn.setForeground(Color.BLACK);
		sendRawBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

		clearRawBtn = new JButton("비우기");
		clearRawBtn.setPreferredSize(new Dimension(85, 24));
		clearRawBtn.setBackground(Color.WHITE);
		clearRawBtn.setForeground(Color.BLACK);
		clearRawBtn.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

		inputRow.add(rawHexField);
		inputRow.add(Box.createHorizontalStrut(4));
		inputRow.add(sendRawBtn);
		inputRow.add(Box.createHorizontalStrut(4));
		inputRow.add(clearRawBtn);

		rawHexPanel.add(inputRow);

		// =========================================================================
		// [HEX 2열] 옵션 라인
		// =========================================================================
		JPanel optionRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 2));
		optionRow.setBackground(Color.WHITE);

		crcAutoCheck = new JCheckBox("CRC 자동 계산", true);
		crcAutoCheck.setBackground(Color.WHITE);
		crcAutoCheck.setForeground(Color.BLACK);
		crcAutoCheck.setFont(new Font("맑은 고딕", Font.PLAIN, 11));

		crcPreviewLabel = new JLabel("송신 예정: -");
		crcPreviewLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 11));
		crcPreviewLabel.setForeground(Color.BLUE);

		optionRow.add(crcAutoCheck);
		optionRow.add(Box.createHorizontalStrut(10));
		optionRow.add(crcPreviewLabel);
		rawHexPanel.add(optionRow);

		// =========================================================================
		// [HEX 3열] 응답 영역
		// =========================================================================
		rawResponseArea = new JTextArea();
		rawResponseArea.setEditable(false);
		rawResponseArea.setFont(new Font("D2Coding", Font.PLAIN, 12));
		rawResponseArea.setBorder(BorderFactory.createLineBorder(new Color(230, 235, 240)));

		// 높이 직접 지정
		FontMetrics fm = rawResponseArea.getFontMetrics(rawResponseArea.getFont());
		int h = fm.getHeight() + 6;

		rawResponseArea.setPreferredSize(new Dimension(contentWidth - 15, h));
		rawResponseArea.setMinimumSize(new Dimension(contentWidth - 15, 28));
		rawResponseArea.setMaximumSize(new Dimension(Short.MAX_VALUE, 32));

		JPanel scrollWrapper = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		scrollWrapper.setBackground(Color.WHITE);
		scrollWrapper.setMaximumSize(new Dimension(Short.MAX_VALUE, 32));

		scrollWrapper.add(rawResponseArea);
		rawHexPanel.add(scrollWrapper);

		// =========================================================================
		// ⚡ [토글 액션 리스너] 자기 자식들의 레이아웃 및 부모 갱신 연산
		// =========================================================================
		btnToggleHex.addActionListener(e -> {
			boolean isVisible = rawHexPanel.isVisible();
			rawHexPanel.setVisible(!isVisible);

			if (isVisible) {
				btnToggleHex.setText("▶ Raw HEX 송신/분석 펼치기");
			} else {
				btnToggleHex.setText("▼ Raw HEX 송신/분석 접기");
			}

			Container parent = getParent();
			if (parent != null) {
				parent.revalidate();
				parent.repaint();
			}
		});

		// 패널에 조립 완료
		add(btnToggleHex);
		add(Box.createVerticalStrut(3));
		add(rawHexPanel);

		// 초기값 설정 (접힌 상태로 시작)
		rawHexPanel.setVisible(false);
		btnToggleHex.setText("▶ Raw HEX 송신/분석 펼치기");
	}
	

	// =========================================================================
	// 🎯 외부(Controller나 Terminal)에서 데이터를 가져오거나 이벤트를 달 수 있도록 Getter 개방
	// =========================================================================
	public JTextField getRawHexField() {
		return rawHexField;
	}

	public JButton getSendRawBtn() {
		return sendRawBtn;
	}

	public JButton getClearRawBtn() {
		return clearRawBtn;
	}

	public JCheckBox getCrcAutoCheck() {
		return crcAutoCheck;
	}

	public JLabel getCrcPreviewLabel() {
		return crcPreviewLabel;
	}

	public JTextArea getRawResponseArea() {
		return rawResponseArea;
	}
}