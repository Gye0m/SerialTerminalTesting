package com.DSK.model.dto.common;

import com.DSK.serial.constant.*;

public class MeterInfo {

	private final String name;
	private final String unit;
	private final DataType dataType;
	private final EndianType endianType;

	public MeterInfo(String name, String unit, DataType dataType, EndianType endianType) {
		this.name = name;
		this.unit = unit;
		this.dataType = dataType;
		this.endianType = endianType;
	}

	public String getName() {
		return name;
	}

	public String getUnit() {
		return unit;
	}

	public DataType getDataType() {
		return dataType;
	}

	public EndianType getEndianType() {
		return endianType;
	}
}