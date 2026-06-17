package com.DSK.model.dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import com.DSK.Metering.DBConnectionConfig;
import com.DSK.model.dto.MeterReading;

public class MeterReadingDAO {

	// 계측값들 불러오기 (검침기 번호(port), 데이터 시작주소) 기준으로
	// 0 : 전체기간, 1 : 오늘, 2 : 최근 3일, 3 : 최근 일주일
	public List<MeterReading> getErrorMetersByAddress(int deviceId, String addressMap, int dateSelected) {

		List<MeterReading> list = new ArrayList<>();

		// 1. METER_READING_ERROR 테이블 기준 SQL 작성 (조인 없이 에러 테이블 직접 조회)
		StringBuilder sql = new StringBuilder(
				"SELECT * FROM ( " + "    SELECT ERROR_ID, DEVICE_ID, ADDRESS_MAP, ERROR_CODE, CREATED_AT "
						+ "    FROM METER_READING_ERROR " + "    WHERE ADDRESS_MAP = ? AND DEVICE_ID = ? ");

		String targetDateStr = null;

		// 2. dateSelected 값에 따라 날짜 조건 동적 추가 (CREATED_AT 컬럼 기준)
		if (dateSelected > 0) {
			java.time.LocalDateTime now = java.time.LocalDateTime.now();
			java.time.LocalDateTime targetDateTime = now;

			switch (dateSelected) {
			case 1: // 오늘 (오늘 00시 00분 00초부터)
				targetDateTime = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
				break;
			case 2: // 최근 3일 전부터
				targetDateTime = now.minusDays(3);
				break;
			case 3: // 최근 일주일(7일) 전부터
				targetDateTime = now.minusDays(7);
				break;
			}

			java.time.format.DateTimeFormatter formatter = java.time.format.DateTimeFormatter
					.ofPattern("yyyy-MM-dd HH:mm:ss");
			targetDateStr = targetDateTime.format(formatter);

			System.out.println("[에러날짜체크] 계산된 조건 시간: " + targetDateStr);

			sql.append("      AND CREATED_AT >= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS') ");
		}

		// 오라클 인라인 뷰 밖에서 ROWNUM을 체크하기 위해 ORDER BY 후 닫아줌
		sql.append("    ORDER BY CREATED_AT DESC " + ") WHERE ROWNUM <= 20");

		// 3. JDBC 실행
		try (Connection conn = DBConnectionConfig.getConnection();
				PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

			// 필수 파라미터 세팅
			pstmt.setString(1, addressMap);
			pstmt.setInt(2, deviceId);

			if (dateSelected > 0 && targetDateStr != null) {
				pstmt.setString(3, targetDateStr);
			}

			// 4. 쿼리문 실행 및 데이터 객체 바인딩
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {

					// 💡 [핵심 변환] 에러 테이블에는 값이 없으므로, UI 가독성을 위해 매핑을 커스텀합니다.
					int readingId = rs.getInt("ERROR_ID"); // READING_ID 대용으로 에러 ID 세팅
					int devId = rs.getInt("DEVICE_ID");
					String addrMap = rs.getString("ADDRESS_MAP");

					String errorCode = rs.getString("ERROR_CODE"); // 예: TIMEOUT, CRC_ERROR 등
					String itemName = "⚠️ [" + errorCode + "] 발생"; // UI 항목명 칸에 에러 코드 조합해서 노출

					String readingValue = ""; // 에러이므로 계측값은 0으로 고정
					String errorTime = rs.getString("CREATED_AT"); // 에러 발생 시간 매핑
					String unit = ""; // 단위 없음
					String type = ""; // 통신 종류

					// 생성자 파라미터 순서에 맞게 세팅하여 객체 생성
					MeterReading reading = new MeterReading(readingId, devId, addrMap, itemName, readingValue,
							errorTime, unit, type);

					list.add(reading);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return list;
	}

	// 오류 데이터 처리
	public int insertErrorData(int deviceId, String addressMap, int errorCode) {
//		final String sql = "INSERT INTO METER_READING_ERROR "
//				+ "(ERROR_ID, DEVICE_ID, ADDRESS_MAP, ERROR_CODE, CREATED_AT) "
//				+ "VALUES (SEQ_METER_READING_ERROR.NEXTVAL, ?, ?, ?, SYSDATE)";
//
//		try (Connection conn = DBConnectionConfig.getConnection();
//				PreparedStatement pstmt = conn.prepareStatement(sql)) {
//			pstmt.setInt(1, deviceId);
//			pstmt.setString(2, addressMap);
//
//			switch (errorCode) {
//			case 1:
//				pstmt.setString(3, "TIMEOUT");
//				break;
//			case 2:
//				pstmt.setString(3, "CRC_ERROR");
//				break;
//			case 3:
//				pstmt.setString(3, "DISCONNECTION");
//				break;
//			default:
//				pstmt.setString(3, "ERRORTYPE_UNDETECTED");
//				break;
//			}
//			return pstmt.executeUpdate();
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
		return 0;
	}

	// 검침 결과 삽입
	public int insert(MeterReading dto) {
		final String sql = "INSERT INTO METER_READING " + "(READING_ID, DEVICE_ID, ADDRESS_MAP, READING_VALUE)"
				+ "VALUES (SEQ_METER_READING.NEXTVAL, ?, ?, ?)";

		try (Connection conn = DBConnectionConfig.getConnection();
				PreparedStatement pstmt = conn.prepareStatement(sql)) {
			pstmt.setInt(1, dto.getDeviceId());
			pstmt.setString(2, dto.getAddressMap());
			pstmt.setString(3, dto.getReadingValue());

			return pstmt.executeUpdate();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}

	// 계측값들 불러오기 (검침기 번호(port), 데이터 시작주소) 기준으로
	// 0 : 전체기간, 1 : 오늘, 2 : 최근 3일, 3 : 최근 일주일
	public List<MeterReading> getMetersByAddress(int deviceId, String addressMap, int dateSelected) {

		List<MeterReading> list = new ArrayList<>();

		// 우선 검침기 번호, 데이터 시작 주소에 맞게 SQL 작성
		StringBuilder sql = new StringBuilder("SELECT * FROM (SELECT * FROM " + "METER_READING R JOIN MAP_TABLE M "
				+ "ON R.ADDRESS_MAP = M.ADDRESS_MAP " + "WHERE R.ADDRESS_MAP = ? AND R.DEVICE_ID = ? ");

		String targetDateStr = null;

		// 2. dateSelected 값에 따라 날짜 조건 동적 추가
		if (dateSelected > 0) {
			LocalDateTime now = LocalDateTime.now();
			LocalDateTime targetDateTime = now;

			switch (dateSelected) {
			case 1: // 오늘 (오늘 00시 00분 00초부터)
				targetDateTime = now.withHour(0).withMinute(0).withSecond(0).withNano(0);
				break;
			case 2: // 최근 3일 전부터
				targetDateTime = now.minusDays(3);
				break;
			case 3: // 최근 일주일(7일) 전부터
				targetDateTime = now.minusDays(7);
				break;
			}

			DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
			targetDateStr = targetDateTime.format(formatter);

			System.out.println("[날짜체크] 계산된 조건 시간: " + targetDateStr);

			sql.append("AND R.READING_TIME >= TO_DATE(?, 'YYYY-MM-DD HH24:MI:SS') ");
		}
		sql.append("ORDER BY R.READING_TIME DESC) WHERE ROWNUM <= 20");

		// 3. JDBC 실행
		try (Connection conn = DBConnectionConfig.getConnection();
				PreparedStatement pstmt = conn.prepareStatement(sql.toString())) {

			// 필수 파라미터 세팅
			pstmt.setString(1, addressMap);
			pstmt.setInt(2, deviceId);

			if (dateSelected > 0 && targetDateStr != null) {
				pstmt.setString(3, targetDateStr);
			}

			// 쿼리문 실행 후 맞는 객체들 리스트에 담아 반환
			try (ResultSet rs = pstmt.executeQuery()) {
				while (rs.next()) {
					// DB 컬럼명과 매칭되도록 파라미터 수정
					MeterReading reading = new MeterReading(rs.getInt("READING_ID"), rs.getInt("DEVICE_ID"),
							rs.getString("ADDRESS_MAP"), rs.getString("ITEM_NAME"), rs.getString("READING_VALUE"),
							rs.getString("READING_TIME"), rs.getString("UNIT"), rs.getString("TYPE"));
					list.add(reading);
				}
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return list;
	}

}