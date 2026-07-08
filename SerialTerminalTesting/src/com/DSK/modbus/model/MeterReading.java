package com.DSK.modbus.model;

// 검침 결과 저장 및 조회용 DTO
public class MeterReading {
	private int readingId; // 검침 결과 고유 아이디

	private int deviceId; // 검침 장치 아이디
	private String addressMap; // 검침 항목 시작 주소
	private String readingName; // 검침 항목 이름
	private String readingValue; // 검침 결과 값
	private String readingTime; // 검침 수행 시간
	private String unit; // 단위
	private String type; // 통신 종류 구분

	public MeterReading(int readingId, int deviceId, String addressMap, String readingName, String readingValue,
			String readingTime, String unit, String type) {
		super();
		this.readingId = readingId;
		this.deviceId = deviceId;
		this.addressMap = addressMap;
		this.readingName = readingName;
		this.readingValue = readingValue;
		this.readingTime = readingTime;
		this.unit = unit;
		this.type = type;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	public String getReadingName() {
		return readingName;
	}

	public void setReadingName(String readingName) {
		this.readingName = readingName;
	}

	public void setReadingValue(String readingValue) {
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

	public String getReadingValue() {
		return readingValue;
	}

	public String getReadingTime() {
		return readingTime;
	}

	public void setReadingTime(String readingTime) {
		this.readingTime = readingTime;
	}

}
