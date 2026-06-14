package com.DSK.model.dto;

public class MeterInfo {
	private String name; // 한글 이름
	private String unit; // 단위

	public MeterInfo(String name, String unit) {
		this.name = name;
		this.unit = unit;
	}

	public String getName() {
		return name;
	}

	public String getUnit() {
		return unit;
	}
}