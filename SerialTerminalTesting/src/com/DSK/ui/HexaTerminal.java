package com.DSK.ui;

import com.DSK.Metering.Analyzer;
import com.DSK.model.dto.MeterInfo;
import com.DSK.model.dto.MeterReading;
import com.DSK.serial.HexaManager;
import com.DSK.serial.ProtocolConverter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;

import java.awt.*;
import java.util.Map;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class HexaTerminal extends JFrame {
	private static final long serialVersionUID = 1L;
	private static final Logger log = LoggerFactory.getLogger(HexaTerminal.class);

	private final HexaManager serialManager;

	// 분할 추출한 서브 패널 부품들
	private ConfigPanel configPanel;
	private CheckListPanel checkListPanel;

	private JTextArea rxTextArea;
	private JButton sendHexaBtn;

	private int count = 0;

	public HexaTerminal() {
		serialManager = new HexaManager(this);

		setTitle("옴니 Modbus RTU 시리얼 포트 테스팅");
		setSize(1000, 950);
		setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout(10, 10));

		initUI();
	}

	public void resetCheckList() {
		if (checkListPanel != null) {
			checkListPanel.clearAllSelection();
		}
	}

	private void initUI() {
		// 1. 상단 포트 설정 패널 조립
		configPanel = new ConfigPanel(this, serialManager);
		add(configPanel, BorderLayout.NORTH);

		// 2. 중앙 좌측 검침 항목 선택 패널 조립
		checkListPanel = new CheckListPanel();

		// 3. 중앙 우측 수신 로그 영역 조립
		rxTextArea = new JTextArea();
		rxTextArea.setEditable(false);
		rxTextArea.setBackground(new Color(30, 30, 30));
		rxTextArea.setForeground(Color.GREEN);
		rxTextArea.setFont(new Font("Malgun Gothic", Font.PLAIN, 16));
		JScrollPane logScroll = new JScrollPane(rxTextArea);
		logScroll.setBorder(BorderFactory.createTitledBorder("수신 로그"));

		// 4. 중앙 스플릿 윈도우 조립
		JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, checkListPanel, logScroll);
		splitPane.setDividerLocation(350);
		add(splitPane, BorderLayout.CENTER);

		// 5. 하단 발신 버튼 구역 조립
		JPanel southPanel = new JPanel(new GridLayout(2, 1, 0, 5));
		southPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10)); // 패널 자체 외곽 여백 추가

		// ① 첫 번째 행: 검침 요청 버튼 구역
		// FlowLayout.CENTER를 주면 버튼이 강제로 늘어나지 않고 원래 설정한 크기를 유지합니다.
		JPanel hexaPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		sendHexaBtn = new JButton("검침 요청");
		sendHexaBtn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
		sendHexaBtn.setPreferredSize(new Dimension(300, 33)); // ◀ 실무에서 딱 보기 좋은 슬림하고 널찍한 크기
		sendHexaBtn.setEnabled(false);
		sendHexaBtn.addActionListener(e -> sendHexData());
		hexaPanel.add(sendHexaBtn);
		southPanel.add(hexaPanel);

		// ② 두 번째 행: 검침 내역 조회 버튼 구역
		JPanel historyPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 0, 0));
		JButton viewHistoryBtn = new JButton("검침 데이터 내역 조회");
		viewHistoryBtn.setFont(new Font("맑은 고딕", Font.BOLD, 13));
		viewHistoryBtn.setPreferredSize(new Dimension(300, 33)); // ◀ 위 버튼과 가로/세로 크기 통일
		viewHistoryBtn.addActionListener(e -> openHistoryDialog());
		historyPanel.add(viewHistoryBtn);
		southPanel.add(historyPanel);

		add(southPanel, BorderLayout.SOUTH);
	}

	// ============== 검침 내역 조회 ==============
	private void openHistoryDialog() {
		JDialog dialog = new JDialog(this, "검침 데이터 내역 조회", true);
		dialog.setSize(850, 500); // 상단 조건창 공간 확보를 위해 크기 넉넉히 설정
		dialog.setLocationRelativeTo(this);
		dialog.setLayout(new BorderLayout(10, 10));

		// ---------------------------------------------------------------------
		// 1. 상단 검색 조건 패널 (GridBagLayout으로 유연하고 완벽한 정렬)
		// ---------------------------------------------------------------------
		JPanel filterPanel = new JPanel(new GridBagLayout());
		filterPanel.setBorder(BorderFactory.createTitledBorder("필터 선택 조건"));

		// GridBagLayout 설정을 위한 제어 객체 생성
		GridBagConstraints gbc = new GridBagConstraints();
		gbc.insets = new Insets(5, 10, 5, 10); // 컴포넌트 간의 여백 (상, 좌, 하, 우)
		gbc.fill = GridBagConstraints.HORIZONTAL; // 빈 공간을 가로로 채우기
		gbc.anchor = GridBagConstraints.CENTER;
		// ① 장치 선택 레이블 & 콤보박스
		gbc.gridx = 0;
		gbc.gridy = 0;
		gbc.weightx = 0.1;
		filterPanel.add(new JLabel("장치 선택:"), gbc);

		gbc.gridx = 1;
		gbc.weightx = 0.1; // 적당한 너비 배분
		String[] devices = { "1번", "2번", "3번" };
		JComboBox<String> deviceCombo = new JComboBox<>(devices);
		filterPanel.add(deviceCombo, gbc);

		// ② 검침 항목명 레이블 & 콤보박스 (★ 핵심: 항목명이 길므로 weightx를 크게 줌)
		gbc.gridx = 2;
		gbc.weightx = 0.0;
		filterPanel.add(new JLabel("검침 항목명:"), gbc);

		gbc.gridx = 3;
		gbc.weightx = 0.6; // ◀ 항목명 콤보박스가 가장 넓은 공간을 차지하도록 설정!
		JComboBox<String> itemCombo = new JComboBox<>();
		if (checkListPanel != null && checkListPanel.getSelectedMap() != null) {
			itemCombo.removeAllItems();
			checkListPanel.getSelectedMap().keySet().stream().map(JCheckBox::getText).sorted(Comparator.reverseOrder())
					.forEach(item -> itemCombo.addItem(item));
		}
		filterPanel.add(itemCombo, gbc);

		// ③ 조회 기간 레이블 & 콤보박스
		gbc.gridx = 4;
		gbc.weightx = 0.0;
		filterPanel.add(new JLabel("조회 기간:"), gbc);

		gbc.gridx = 5;
		gbc.weightx = 0.1;
		String[] dateRanges = { "전체 기간", "오늘", "최근 3일", "최근 1주일" };
		JComboBox<String> dateCombo = new JComboBox<>(dateRanges);
		filterPanel.add(dateCombo, gbc);

		// ④ 조회 버튼
		gbc.gridx = 6;
		gbc.weightx = 0.0;
		JButton searchBtn = new JButton("조회");
		searchBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		filterPanel.add(searchBtn, gbc);

		// ⑤ 데이터 분석 버튼
		gbc.gridx = 7;
		gbc.weightx = 0.0;
		JButton meterDataAnalyzer = new JButton("데이터 분석");
		meterDataAnalyzer.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		filterPanel.add(meterDataAnalyzer, gbc);
		meterDataAnalyzer.setEnabled(false);

		// ⑥ 오류 데이터 조회 버튼
		gbc.gridx = 8;
		gbc.weightx = 0.0;
		JButton errorSearchBtn = new JButton("오류 데이터");
		errorSearchBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
		// FlatLaf 스타일 테두리나 배경을 약간 주황/빨간 톤으로 힌트를 주면 UI가 더 직관적입니다.
		errorSearchBtn.setForeground(new Color(220, 53, 69));
		filterPanel.add(errorSearchBtn, gbc);

		// 다이얼로그 북쪽에 패널 부착
		dialog.add(filterPanel, BorderLayout.NORTH);

		
		// ---------------------------------------------------------------------
		// 2. 중앙 JTable 구성 (데이터 결과를 뿌려줄 표)
		// ---------------------------------------------------------------------
		String[] columns = { "비고", "검침 시간", "항목명", "계측값", "검침 ID" };
		DefaultTableModel model = new DefaultTableModel(columns, 0) {
			private static final long serialVersionUID = 1L;

			@Override
			public boolean isCellEditable(int row, int column) {
				return false; // 테이블 값 임의 수정 불가 처리
			}
		};
		JTable table = new JTable(model);
		dialog.add(new JScrollPane(table), BorderLayout.CENTER);

		// ---------------------------------------------------------------------
		// 3. [조회] 버튼 클릭 시 필터링 처리 이벤트 기능 정의
		// ---------------------------------------------------------------------
		final List<MeterReading> activeList = new ArrayList<>();

		
		
		// 데이터 분석 버튼 누르면 조회된 데이터 기반으로 제미나이 툴 통한 데이터 관련 팁 암시
		meterDataAnalyzer.addActionListener(e -> {
			// 1. 로딩 팝업창(JDialog) 즉시 생성 및 표시
			JDialog loadingDialog = new JDialog(dialog, "AI 분석 중", false); // false로 주어 메인창 안 막히게 설정
			loadingDialog.setSize(300, 120);
			loadingDialog.setLocationRelativeTo(dialog);
			loadingDialog.setLayout(new BorderLayout(10, 10));
			loadingDialog.setUndecorated(true); // 테두리·타이틀바를 없애서 팝업처럼 깔끔하게 만듦

			// 패널 내부 정돈 (여백 및 테두리 부여)
			JPanel loadingPanel = new JPanel(new BorderLayout(10, 10));
			loadingPanel.setBorder(BorderFactory.createLineBorder(java.awt.Color.GRAY, 1));

			// Swing 내장 무한 로딩 애니메이션 바 (모래시계 역할)
			JProgressBar progressBar = new JProgressBar();
			progressBar.setIndeterminate(true);

			JLabel loadingLabel = new JLabel("AI가 데이터를 분석하고 있습니다...", JLabel.CENTER);
			loadingLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));

			loadingPanel.add(loadingLabel, BorderLayout.CENTER);
			loadingPanel.add(progressBar, BorderLayout.SOUTH);
			loadingDialog.add(loadingPanel);

			// 로딩창을 화면에 먼저 즉시 노출!
			loadingDialog.setVisible(true);

			// -----------------------------------------------------------------
			// 2. [핵심] 백엔드 비동기 스레드 작동 (네트워크 통신으로 인한 메인화면 멈춤 방지)
			// -----------------------------------------------------------------
			new Thread(() -> {
				try {
					// 구글 AI 서버와 통신 실행 (시간이 오래 걸리는 파트)
					String meterAnalyzerResult = Analyzer.analyzeMeterData(activeList);

					// 3. 통신이 끝났으므로 UI 업데이트는 메인 스레드(EDT)에 안전하게 요청
					javax.swing.SwingUtilities.invokeLater(() -> {
						// 로딩창을 닫아줍니다.
						loadingDialog.dispose();

						if (meterAnalyzerResult.equals("분석할 데이터가 없습니다.") || meterAnalyzerResult.isEmpty()) {
							JOptionPane.showMessageDialog(dialog, "AI 분석 결과를 가져오지 못했습니다.", "오류",
									JOptionPane.ERROR_MESSAGE);
							return;
						}

						// 결과 화면 텍스트 영역 세팅 및 팝업 출력
						JTextArea textArea = new JTextArea(20, 50);
						textArea.setText(meterAnalyzerResult);
						textArea.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
						textArea.setEditable(false);
						textArea.setLineWrap(true);
						textArea.setWrapStyleWord(true);

						JScrollPane scrollPane = new JScrollPane(textArea);
						JOptionPane.showMessageDialog(dialog, scrollPane, "💡AI 검침 데이터 분석 리포트",
								JOptionPane.INFORMATION_MESSAGE);
					});

				} catch (Exception ex) {
					// 예외 발생 시 로딩창 닫고 에러 출력
					javax.swing.SwingUtilities.invokeLater(() -> {
						loadingDialog.dispose();
						JOptionPane.showMessageDialog(dialog, "분석 중 오류 발생: " + ex.getMessage(), "오류",
								JOptionPane.ERROR_MESSAGE);
					});
				}
			}).start(); // 백그라운드 스레드 가동 시작!
		});

		// 🎯 [오류 데이터 조회] 버튼 클릭 이벤트 리스너 기본 구조
				errorSearchBtn.addActionListener(e -> {
					model.setRowCount(0); // 새로 조회할 때마다 기존 표 내용을 싹 비움

					// 1. 선택된 조건 추출 (기존과 동일)
					String selectedDevice = (String) deviceCombo.getSelectedItem();
					String selectedItemName = (String) itemCombo.getSelectedItem();

					int targetAddress = -1;
					String dbAddressMap = "0x0000";

					// 선택된 문자열 일치하는 주소값 매핑 (기존과 동일)
					if (selectedItemName != null && checkListPanel != null) {
						if (checkListPanel.getSelectedMap() != null) {
							for (Integer startAddress : checkListPanel.getSelectedMap().values()) {
								MeterInfo info = checkListPanel.getMeterAddressMap().get(startAddress);
								if (info != null) {
									String mapItemName = info.getName();
									if (selectedItemName.contains(mapItemName)) {
										targetAddress = startAddress; 
										break;
									}
								}
							}
						}
					}

					// 정수 주소를 DB용 String 변환 (기존과 동일)
					if (targetAddress != -1) {
						dbAddressMap = "0x" + String.format("%04X", targetAddress);
					} else {
						JOptionPane.showMessageDialog(dialog, "데이터베이스 수신 간에 오류 발생");
						return;
					}

					int selectedItemDate = dateCombo.getSelectedIndex();
					int reqDeviceId = Integer.parseInt(selectedDevice.replaceAll("[^0-9]", ""));

					// 2. 전체 데이터 내역 수신 (기존과 동일)
					final List<MeterReading> dbList = serialManager.getMeterHistory(reqDeviceId, dbAddressMap, selectedItemDate);

					if (dbList == null || dbList.isEmpty()) {
						JOptionPane.showMessageDialog(dialog, "조회된 검침 데이터가 없습니다.");
						meterDataAnalyzer.setEnabled(false);
						return;
					}

					// 기존 활성화 리스트 초기화
					activeList.clear();

					// 3. ⭐️ [질문자님 전용 추가 로직 구간] 오류 데이터 필터링 ⭐️
					for (MeterReading r : dbList) {
						
						// ========================================================
						// 💡 이 구역에 원하시는 오류 판단 조건식을 채워 넣으시면 됩니다!
						// 예시: 
						// boolean isError = r.getReadingValue().equals("0") || r.getReadingValue() == null;
						// = "여기에 질문자님만의 비즈니스 로직을 구현해보세요!"
						// ========================================================
						boolean isError = true; // ◀ 임시로 전부 에러로 판단하게 세팅해 둠 (수정 필요)

						// 필터링 결과 에러 데이터로 판명된 녀석들만 activeList에 수집
						if (isError) {
							activeList.add(r);
						}
					}

					// 오류 필터링을 다 돌렸는데 결과가 하나도 없는 경우 처리
					if (activeList.isEmpty()) {
						JOptionPane.showMessageDialog(dialog, "조회 조건에 해당하는 오류 데이터가 존재하지 않습니다.");
						meterDataAnalyzer.setEnabled(false);
						return;
					}

					// 4. 필터링된 데이터만 JTable 표에 순차 출력
					int rowNum = 1;
					for (MeterReading r : activeList) {
						model.addRow(new Object[] { 
							"⚠️ 오류 데이터", // ◀ 일반 조회와 구분하기 쉽게 비고란 고정 마킹
							r.getReadingTime(), 
							r.getReadingName(),
							(r.getReadingValue() + " " + (r.getUnit() != null ? r.getUnit() : "")), 
							r.getReadingId() 
						});
						rowNum++;
					}
					
					// 오류 목록만 모아둔 채로 AI 연동할 수 있게 활성화
					meterDataAnalyzer.setEnabled(true); 
				});
		
		// 데이터 조회 버튼 누르면
		searchBtn.addActionListener(e -> {
			model.setRowCount(0); // 새로 조회할 때마다 기존 표 내용을 싹 비움

			// 선택된 스크롤바 아이템 텍스트 추출
			String selectedDevice = (String) deviceCombo.getSelectedItem();
			String selectedItemName = (String) itemCombo.getSelectedItem();

			int targetAddress = -1;
			String dbAddressMap = "0x0000";

			// 선택된 문자열 일치하는 주소값 받아서 출력
			if (selectedItemName != null && checkListPanel != null) {
				if (checkListPanel.getSelectedMap() != null) {
					for (Integer startAddress : checkListPanel.getSelectedMap().values()) {
						MeterInfo info = checkListPanel.getMeterAddressMap().get(startAddress);

						if (info != null) {
							String mapItemName = info.getName();

							if (selectedItemName.contains(mapItemName)) {
								targetAddress = startAddress; // 정수값 찾음 (예: 8)
								break;
							}
						}
					}
				}
			}

			// 정수 주소를 DB용 String 변환
			if (targetAddress != -1) {
				dbAddressMap = "0x" + String.format("%04X", targetAddress);
			} else {
				JOptionPane.showMessageDialog(dialog, "데이터베이스 수신 간에 오류 발생");
			}

			int selectedItemDate = dateCombo.getSelectedIndex();
			int reqDeviceId = Integer.parseInt(selectedDevice.replaceAll("[^0-9]", ""));

			// 장치 번호, 검침 항목 주소, 조회 기간 넘겨서 데이터 바인딩
			final List<MeterReading> dbList = serialManager.getMeterHistory(reqDeviceId, dbAddressMap,
					selectedItemDate);

			if (dbList == null || dbList.isEmpty()) {
				JOptionPane.showMessageDialog(dialog, "조회된 검침 데이터가 없습니다.");
				meterDataAnalyzer.setEnabled(false);
				return;
			}

			// 기존 팝업 리스트 비우고 새로운 데이터 교체
			activeList.clear();
			activeList.addAll(dbList);

			// 가져온 데이터를 콤보박스 조건 맞춰서 필터링 후 출력
			int rowNum = 1;
			for (MeterReading r : activeList) {
				model.addRow(new Object[] { rowNum, r.getReadingTime(), r.getReadingName(),
						(r.getReadingValue() + " " + (r.getUnit() != null ? r.getUnit() : "")), r.getReadingId() });
				rowNum++;
			}
			meterDataAnalyzer.setEnabled(true);
		});

		// 팝업창 최종 화면 표시
		dialog.setVisible(true);
	}

	private void sendHexData() {
		if (serialManager == null || !serialManager.isOpen()) {
			JOptionPane.showMessageDialog(this, "시리얼 포트가 열려있지 않습니다.");
			return;
		}

		boolean hasSelection = false;
		Map<JCheckBox, Integer> selectedMap = checkListPanel.getSelectedMap();

		for (Map.Entry<JCheckBox, Integer> entry : selectedMap.entrySet()) {
			JCheckBox checkBox = entry.getKey();
			if (!checkBox.isSelected())
				continue;

			hasSelection = true;
			int startAddress = entry.getValue();

			try {
				byte[] rawBytes = { 0x01, 0x04, (byte) ((startAddress >> 8) & 0xFF), (byte) (startAddress & 0xFF), 0x00,
						0x02 };
				int crc = ProtocolConverter.calculateCRC16(rawBytes, rawBytes.length);

				byte[] modbusPacket = new byte[rawBytes.length + 2];
				System.arraycopy(rawBytes, 0, modbusPacket, 0, rawBytes.length);
				modbusPacket[6] = (byte) (crc & 0xFF);
				modbusPacket[7] = (byte) ((crc >> 8) & 0xFF);

				serialManager.enqueue(modbusPacket);

				String hex = ProtocolConverter.convertBytesToHex(modbusPacket, modbusPacket.length);
				MeterInfo info = checkListPanel.getMeterAddressMap().get(startAddress);

				count++;
				rxTextArea.append("[" + info.getName() + "]\n");
				log.info("[검침 요청] = {}, [송신 HEX 데이터] = {}", info.getName(), hex);

			} catch (Exception e) {
				log.error("검침 요청 실패 주소: {}", startAddress, e);
				JOptionPane.showMessageDialog(this, "검침 요청 실패 : " + startAddress);
			}
		}

		if (!hasSelection) {
			JOptionPane.showMessageDialog(this, "검침할 항목을 선택하세요.");
			return;
		}

		rxTextArea.append("\n총 " + count + "개의 데이터 검침 요청중입니다...\n\n");
		serialManager.startSend();
		count = 0;
	}

	public void appendRxText(String text) {
		SwingUtilities.invokeLater(() -> rxTextArea.append(text));
	}

	public void clearLogArea() {
		rxTextArea.setText("");
	}

	public void setSendButtonEnabled(boolean enabled) {
		sendHexaBtn.setEnabled(enabled);
	}

	public void handlePhysicalDisconnect() {
		SwingUtilities.invokeLater(() -> {
			JOptionPane.showMessageDialog(null, "물리적 연결이 끊어졌습니다!\n케이블 상태를 확인하세요.");
			rxTextArea.append("[SYSTEM MESSAGE] Physical Connection is disconnected.\n");
			configPanel.updateConnectionState(false);
		});
	}

	public static void main(String[] args) {
		com.formdev.flatlaf.FlatLightLaf.setup();
		SwingUtilities.invokeLater(() -> new HexaTerminal().setVisible(true));
	}
}