package com.DSK.model.dto.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class LogDto {
	private String timestamp; // 발생 시간 (yyyy-MM-dd HH:mm:ss)
	private String type; // ("SYSTEM" 또는 "ERROR")
	private String message; // 로그 본문 내용

	public LogDto(String type, String message) {
		this.timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd HH:mm:ss:ms"));
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