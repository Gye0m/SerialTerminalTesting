package com.DSK.model.dto.common;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public class ErrorStatusDto {
	private final int address; // 레지스터 주소
	private final String itemName; // 항목명
	private int totalErrCount; // 최종 누적 오류 횟수
	private int crcErrCount; // CRC 에러 카운트
	private int timeoutCount; // 타임아웃 카운트
	private int totalRetryCount; // 재시도 총합 카운트 (성공 전 튀는 노이즈 포착용)
	private int consecutiveFails; // 연속 실패 횟수 (현재 장비 단절 여부)
	private String lastErrTime; // 최근 발생 시간

	public ErrorStatusDto(int address, String itemName) {
		this.address = address;
		this.itemName = itemName;
		this.totalErrCount = 0;
		this.crcErrCount = 0;
		this.timeoutCount = 0;
		this.totalRetryCount = 0;
		this.consecutiveFails = 0;
		this.lastErrTime = "-";
	}

	public void incrementRetry() {
		this.totalRetryCount++;
		updateTime();
	}

	public void triggerTimeout() {
		this.totalErrCount++;
		this.timeoutCount++;
		this.consecutiveFails++;
		updateTime();
	}

	public void triggerCrcError() {
		this.totalErrCount++;
		this.crcErrCount++;
		this.consecutiveFails++;
		updateTime();
	}

	public void resetConsecutiveFails() {
		this.consecutiveFails = 0;
	}

	private void updateTime() {
		this.lastErrTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
	}

	// =========================================================================
	// Getter 메서드 리스트
	// =========================================================================
	public int getAddress() {
		return address;
	}

	public String getItemName() {
		return itemName;
	}

	public int getTotalErrCount() {
		return totalErrCount;
	}

	public int getCrcErrCount() {
		return crcErrCount;
	}

	public int getTimeoutCount() {
		return timeoutCount;
	}

	public int getTotalRetryCount() {
		return totalRetryCount;
	}

	public int getConsecutiveFails() {
		return consecutiveFails;
	}

	public String getLastErrTime() {
		return lastErrTime;
	}
}