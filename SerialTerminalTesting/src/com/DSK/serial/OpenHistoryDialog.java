package com.DSK.serial;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;

import com.DSK.Metering.Analyzer;
import com.DSK.model.dto.MeterReading;

public class OpenHistoryDialog {
    
    private final JFrame parent;
    private final HexaManager serialManager;
    private JDialog dialog;

    public OpenHistoryDialog(JFrame parent, HexaManager serialManager) {
        this.parent = parent;
        this.serialManager = serialManager;
        
        // UI 초기화 및 조회를 담당하는 메서드 호출
        initDialog();
    }

    private void initDialog() {
        dialog = new JDialog(parent, "검침 데이터 내역 조회", true);
        dialog.setSize(850, 500); 
        dialog.setLocationRelativeTo(parent);
        dialog.setLayout(new BorderLayout(10, 10));

        // ---------------------------------------------------------------------
        // 1. 상단 검색 조건 패널
        // ---------------------------------------------------------------------
        JPanel filterPanel = new JPanel(new GridBagLayout());
        filterPanel.setBorder(BorderFactory.createTitledBorder("필터 선택 조건"));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 10, 5, 10); 
        gbc.fill = GridBagConstraints.HORIZONTAL; 
        gbc.anchor = GridBagConstraints.CENTER;
        
        // ① 장치 선택
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0.1;
        filterPanel.add(new JLabel("장치 선택:"), gbc);

        gbc.gridx = 1; gbc.weightx = 0.1; 
        String[] devices = { "1번", "2번", "3번" };
        JComboBox<String> deviceCombo = new JComboBox<>(devices);
        filterPanel.add(deviceCombo, gbc);

        // ② 검침 항목명
        gbc.gridx = 2; gbc.weightx = 0.0;
        filterPanel.add(new JLabel("검침 항목명:"), gbc);

        gbc.gridx = 3; gbc.weightx = 0.6; 
        JComboBox<String> itemCombo = new JComboBox<>();
        filterPanel.add(itemCombo, gbc);

        // ③ 조회 기간
        gbc.gridx = 4; gbc.weightx = 0.0;
        filterPanel.add(new JLabel("조회 기간:"), gbc);

        gbc.gridx = 5; gbc.weightx = 0.1;
        String[] dateRanges = { "전체 기간", "오늘", "최근 3일", "최근 1주일" };
        JComboBox<String> dateCombo = new JComboBox<>(dateRanges);
        filterPanel.add(dateCombo, gbc);

        // ④ 조회 버튼
        gbc.gridx = 6; gbc.weightx = 0.0;
        JButton searchBtn = new JButton("조회");
        searchBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        filterPanel.add(searchBtn, gbc);

        // ⑤ 오류 데이터 조회 버튼
        gbc.gridx = 7; gbc.weightx = 0.0;
        JButton errorSearchBtn = new JButton("오류 데이터");
        errorSearchBtn.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        errorSearchBtn.setForeground(new Color(220, 53, 69));
        filterPanel.add(errorSearchBtn, gbc);

        // ⑥ 데이터 분석 버튼
        gbc.gridx = 8; gbc.weightx = 0.0;
        JButton meterDataAnalyzer = new JButton("데이터 분석");
        meterDataAnalyzer.setFont(new Font("맑은 고딕", Font.BOLD, 12));
        filterPanel.add(meterDataAnalyzer, gbc);
        meterDataAnalyzer.setEnabled(false);

        dialog.add(filterPanel, BorderLayout.NORTH);

        // ---------------------------------------------------------------------
        // 2. 중앙 JTable 구성
        // ---------------------------------------------------------------------
        String[] columns = { "비고", "검침 시간", "항목명", "계측값", "검침 ID" };
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            private static final long serialVersionUID = 1L;
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; 
            }
        };
        JTable table = new JTable(model);
        dialog.add(new JScrollPane(table), BorderLayout.CENTER);

        // ---------------------------------------------------------------------
        // 3. 이벤트 리스너 기능 정의
        // ---------------------------------------------------------------------
        final List<MeterReading> activeList = new ArrayList<>();

        // AI 데이터 분석 기능
        meterDataAnalyzer.addActionListener(e -> {
            JDialog loadingDialog = new JDialog(dialog, "AI 분석 중", false); 
            loadingDialog.setSize(300, 120);
            loadingDialog.setLocationRelativeTo(dialog);
            loadingDialog.setLayout(new BorderLayout(10, 10));
            loadingDialog.setUndecorated(true); 

            JPanel loadingPanel = new JPanel(new BorderLayout(10, 10));
            loadingPanel.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

            JProgressBar progressBar = new JProgressBar();
            progressBar.setIndeterminate(true);

            JLabel loadingLabel = new JLabel("AI가 데이터를 분석하고 있습니다...", JLabel.CENTER);
            loadingLabel.setFont(new Font("맑은 고딕", Font.PLAIN, 12));

            loadingPanel.add(loadingLabel, BorderLayout.CENTER);
            loadingPanel.add(progressBar, BorderLayout.SOUTH);
            loadingDialog.add(loadingPanel);

            loadingDialog.setVisible(true);

            new Thread(() -> {
                try {
                    String meterAnalyzerResult = Analyzer.analyzeMeterData(activeList);

                    javax.swing.SwingUtilities.invokeLater(() -> {
                        loadingDialog.dispose();

                        if (meterAnalyzerResult == null || meterAnalyzerResult.equals("분석할 데이터가 없습니다.") || meterAnalyzerResult.isEmpty()) {
                            JOptionPane.showMessageDialog(dialog, "AI 분석 결과를 가져오지 못했습니다.", "오류", JOptionPane.ERROR_MESSAGE);
                            return;
                        }

                        JTextArea textArea = new JTextArea(20, 50);
                        textArea.setText(meterAnalyzerResult);
                        textArea.setFont(new Font("맑은 고딕", Font.PLAIN, 13));
                        textArea.setEditable(false);
                        textArea.setLineWrap(true);
                        textArea.setWrapStyleWord(true);

                        JScrollPane scrollPane = new JScrollPane(textArea);
                        JOptionPane.showMessageDialog(dialog, scrollPane, "💡AI 검침 데이터 분석 리포트", JOptionPane.INFORMATION_MESSAGE);
                    });

                } catch (Exception ex) {
                    javax.swing.SwingUtilities.invokeLater(() -> {
                        loadingDialog.dispose();
                        JOptionPane.showMessageDialog(dialog, "분석 중 오류 발생: " + ex.getMessage(), "오류", JOptionPane.ERROR_MESSAGE);
                    });
                }
            }).start(); 
        });

        // 🎯 오류 데이터 조회 버튼 클릭
        errorSearchBtn.addActionListener(e -> {
            model.setRowCount(0); 

            String selectedDevice = (String) deviceCombo.getSelectedItem();
            int targetAddress = -1;
            String dbAddressMap = "0x0000";

            if (targetAddress != -1) {
                dbAddressMap = "0x" + String.format("%04X", targetAddress);
            } else {
                // 💥 수정 포인트: 다이얼로그 가동 직후 -1 상태일 때 튕기지 않게 하려면 우선 테스트용 임시 맵핑 주소를 강제로 쥐여줍니다.
                dbAddressMap = "0x0001"; 
            }

            int selectedItemDate = dateCombo.getSelectedIndex();
            int reqDeviceId = Integer.parseInt(selectedDevice.replaceAll("[^0-9]", ""));

            final List<MeterReading> dbList = serialManager.getErrorMeterHistory(reqDeviceId, dbAddressMap, selectedItemDate);

            if (dbList == null || dbList.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "조회된 검침 오류 데이터가 없습니다.");
                meterDataAnalyzer.setEnabled(false);
                return;
            }

            activeList.clear();

            for (MeterReading r : dbList) {
                boolean isError = true; 
                if (isError) {
                    activeList.add(r);
                }
            }

            if (activeList.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "조회 조건에 해당하는 오류 데이터가 존재하지 않습니다.");
                meterDataAnalyzer.setEnabled(false);
                return;
            }

            for (MeterReading r : activeList) {
                model.addRow(new Object[] { 
                    "⚠️ 오류 데이터", 
                    r.getReadingTime(), 
                    r.getReadingName(),
                    (r.getReadingValue() + " " + (r.getUnit() != null ? r.getUnit() : "")), 
                    r.getReadingId() 
                });
            }

            meterDataAnalyzer.setEnabled(true);
        });

        // 일반 데이터 조회 버튼 클릭
        searchBtn.addActionListener(e -> {
            model.setRowCount(0); 

            String selectedDevice = (String) deviceCombo.getSelectedItem();
            int targetAddress = -1;
            String dbAddressMap = "0x0000";

            if (targetAddress != -1) {
                dbAddressMap = "0x" + String.format("%04X", targetAddress);
            } else {
                // 💥 여기도 마찬가지로 주소가 고정 -1이라 조회 오류가 뜨므로 임시 테스트 주소를 넣고 돌립니다.
                dbAddressMap = "0x0001";
            }

            int selectedItemDate = dateCombo.getSelectedIndex();
            int reqDeviceId = Integer.parseInt(selectedDevice.replaceAll("[^0-9]", ""));

            final List<MeterReading> dbList = serialManager.getMeterHistory(reqDeviceId, dbAddressMap, selectedItemDate);

            if (dbList == null || dbList.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "조회된 검침 데이터가 없습니다.");
                meterDataAnalyzer.setEnabled(false);
                return;
            }

            activeList.clear();
            activeList.addAll(dbList);

            int rowNum = 1;
            for (MeterReading r : activeList) {
                model.addRow(new Object[] { 
                    rowNum, 
                    r.getReadingTime(), 
                    r.getReadingName(),
                    (r.getReadingValue() + " " + (r.getUnit() != null ? r.getUnit() : "")), 
                    r.getReadingId() 
                });
                rowNum++;
            }
            meterDataAnalyzer.setEnabled(true);
        });

        // 팝업창 최종 화면 표시
        dialog.setVisible(true);
    }
}