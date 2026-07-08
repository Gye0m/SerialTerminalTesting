package com.DSK.modbus.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * [LogDto]
 * 역할: 시스템 로그 패널에 표시되는 로그 1건의 모델(DTO)
 * 주요 기능:
 * - 기능 1: 로그 유형 구분 — "SYSTEM"(정상 이벤트) / "ERROR"(오류 이벤트)
 * - 기능 2: 생성 시점 타임스탬프 자동 기록 (생성자에서 현재 시각 포맷팅)
 * - 기능 3: toString() — "[시각] 메시지" 형태로 로그 영역 출력 포맷 제공
 */
public class LogDto {
	private String timestamp; // 발생 시간 (yyyy-MM-dd HH:mm:ss)
	private String type; // ("SYSTEM" 또는 "ERROR")
	private String message; // 로그 본문 내용

	public LogDto(String type, String message) {
		this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss:ms"));
		this.type = type;
		this.message = message;
	}

	public String getTimestamp() {
		return timestamp;
	}

	public void setTimestamp(String timestamp) {
		this.timestamp = timestamp;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	@Override
	public String toString() {
		return String.format("[%s] %s", timestamp, message);
	}
}