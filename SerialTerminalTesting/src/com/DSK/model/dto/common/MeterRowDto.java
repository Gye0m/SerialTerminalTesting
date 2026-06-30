package com.DSK.model.dto.common;

import com.DSK.serial.constant.DataType;
import com.DSK.serial.constant.EndianType;
import com.DSK.serial.constant.FunctionCode;

/**
 * 사용자 정의 검침 테이블의 한 행(프로토콜 메타 정보)을 담당하는 DTO
 */
public class MeterRowDto {
	private boolean selected; // 체크박스 선택 여부
	private int address; // 레지스터 시작 주소 (예: 0x4403)
	private int slaveId; // 장치 번호
	private String name; // 항목명 (예: "A상 전압")
	private FunctionCode functionCode; // Modbus Function Code (0x03 또는 0x04)
	private DataType dataType; // 데이터 타입 (INT32, FLOAT32 등)
	private EndianType endianType; // 엔디안 타입 (BIG, LITTLE 등)
	private String unit = "V"; // 단위
	private double scale = 1.0; // 최종 측정값 : scale x value, 1로 우선 초기화;

	public MeterRowDto() {
	}

	public MeterRowDto(int address, int slaveId, String itemName, FunctionCode functionCode, DataType dataType,
			EndianType endianType, String unit, double scale) {
		this.selected = false;
		this.address = address;
		this.slaveId = slaveId;
		this.name = itemName;
		this.functionCode = functionCode;
		this.dataType = dataType;
		this.endianType = endianType;
		this.unit = unit;
		this.scale = scale;
	}

	public String getName() {
		return name;
	}

	public int getSlaveId() {
		return slaveId;
	}

	public void setSlaveId(int slaveId) {
		this.slaveId = slaveId;
	}

	public void setName(String name) {
		this.name = name;
	}

	public double getScale() {
		return scale;
	}

	public void setScale(double scale) {
		this.scale = scale;
	}

	public boolean isSelected() {
		return selected;
	}

	public void setSelected(boolean selected) {
		this.selected = selected;
	}

	public int getAddress() {
		return address;
	}

	public void setAddress(int address) {
		this.address = address;
	}

	public String getItemName() {
		return name;
	}

	public void setItemName(String itemName) {
		this.name = itemName;
	}

	public FunctionCode getFunctionCode() {
		return functionCode;
	}

	public void setFunctionCode(FunctionCode functionCode) {
		this.functionCode = functionCode;
	}

	public DataType getDataType() {
		return dataType;
	}

	public void setDataType(DataType dataType) {
		this.dataType = dataType;
	}

	public EndianType getEndianType() {
		return endianType;
	}

	public void setEndianType(EndianType endianType) {
		this.endianType = endianType;
	}

	public String getUnit() {
		return unit;
	}

	public void setUnit(String unit) {
		this.unit = unit;
	}
}