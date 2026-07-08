package com.DSK.modbus.model;

public class ErrorMarkerDto {

	private final int offset;
	private final String message;

	public ErrorMarkerDto(int offset, String message) {
		this.offset = offset;
		this.message = message;
	}

	public int getOffset() {
		return offset;
	}

	public String getMessage() {
		return message;
	}
}