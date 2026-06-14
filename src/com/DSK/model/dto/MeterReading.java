package com.DSK.model.dto;

// 검침 결과 저장 및 조회용 DTO
public class MeterReading {
	private int readingId; // 검침 결과 고유 아이디

	private int deviceId; // 검침 장치 아이디
	private String addressMap; // 검침 항목 시작 주소
	private String readingName; // 검침 항목 이름
	private long readingValue; // 검침 결과 값
	private String readingTime; // 검침 수행 시간
	private String unit; // 단위

	public MeterReading(int readingId, int deviceId, String addressMap, String readingName, long readingValue,
			String readingTime, String unit) {
		super();
		this.readingId = readingId;
		this.deviceId = deviceId;
		this.addressMap = addressMap;
		this.readingName = readingName;
		this.readingValue = readingValue;
		this.readingTime = readingTime;
		this.unit = unit;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getReadingName() {
		return readingName;
	}

	public void setReadingName(String readingName) {
		this.readingName = readingName;
	}

	public void setReadingValue(long readingValue) {
		this.readingValue = readingValue;
	}

	public int getReadingId() {
		return readingId;
	}

	public void setReadingId(int readingId) {
		this.readingId = readingId;
	}

	public int getDeviceId() {
		return deviceId;
	}

	public void setDeviceId(int deviceId) {
		this.deviceId = deviceId;
	}

	public String getAddressMap() {
		return addressMap;
	}

	public void setAddressMap(String addressMap) {
		this.addressMap = addressMap;
	}

	public long getReadingValue() {
		return readingValue;
	}

	public void setReadingValue(int readingValue) {
		this.readingValue = readingValue;
	}

	public String getReadingTime() {
		return readingTime;
	}

	public void setReadingTime(String readingTime) {
		this.readingTime = readingTime;
	}

}
