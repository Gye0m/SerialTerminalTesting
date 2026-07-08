package com.DSK.modbus.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.DSK.modbus.model.constant.DataType;
import com.DSK.modbus.model.constant.EndianType;
import com.DSK.modbus.model.constant.FunctionCode;

/**
 * [MeterRowDto]
 * 역할: 검침 맵 테이블의 행(Row) 1개에 해당하는 정적 설정값 모델(DTO)
 * 주요 기능:
 * - 기능 1: 검침 항목 정의 보관 — 주소, SlaveID, FC, 데이터타입, 엔디안, 단위, 스케일
 * - 기능 2: JSON 직렬화/역직렬화 대상 — MapFileManager가 맵 파일(.txt) 저장/로드에 사용
 *           (rowKey는 계산 프로퍼티이므로 @JsonIgnoreProperties로 직렬화 제외)
 * - 기능 3: rowKey() 복합키 생성 — (slaveId, fc, address) → Long 단일 키.
 *           MeterRowIndex(행 조회)와 ModbusManager.errorMap(오류 통계)이
 *           동일 키 체계를 공유하는 단일 소스(Single Source of Truth)
 *
 * 관계: PendingRequest가 이 DTO를 참조로 보유하며 메타데이터 접근을 위임받는다.
 */
@JsonIgnoreProperties({ "rowKey" })
public class MeterRowDto {

	private boolean selected;
	private int address;
	private int slaveId;
	private String name;
	private FunctionCode functionCode;
	private DataType dataType;
	private EndianType endianType;
	private String unit = "V";
	private double scale = 1.0;

	private boolean hexAddress = true;

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

	// =========================================================================
	// ✅ [변경] rowKey — (slaveId, fc, address) 3개 복합키, long 반환.

	// [변경 후] (slaveId, fc, address) 3개 키 → long 반환
	//   비트 배치:
	//     bits 39-32 : slaveId (8비트, 0-247)
	//     bits 31-24 : fc      (8비트, 0x01-0x10)
	//     bits 23-0  : address (24비트로 여유 있게, 실제 사용 범위 0-65535)
	//
	//   long 을 사용하는 이유:
	//     int(32비트)에 slaveId(8) + fc(8) + address(16)를 욱여넣으면
	//     slaveId가 128 이상일 때 부호 비트를 건드려 음수 키가 생긴다.
	//     long 은 부호 문제 없이 넉넉하다.
	// =========================================================================
	public static long rowKey(int slaveId, int fc, int address) {
		return ((long) (slaveId & 0xFF) << 32) | ((long) (fc & 0xFF) << 16) | (address & 0xFFFF);
	}

	/**
	 * 이 DTO의 현재 (slaveId, fc, address) 기준 복합키.
	 *
	 * @JsonIgnore 유지 필수 — 직렬화 시 "rowKey" JSON 필드가 노출되면
	 * setRowKey() 가 없어서 역직렬화 시 UnrecognizedPropertyException 발생.
	 */
	@JsonIgnore
	public long getRowKey() {
		int fc = (functionCode != null) ? functionCode.getCode() : 0x04;
		return rowKey(this.slaveId, fc, this.address);
	}

	// =========================================================================
	// Getter / Setter
	// =========================================================================

	// =========================================================================
	// ✅ [추가] hexAddress getter / setter
	// Jackson이 직렬화할 때 "hexAddress": true/false 를 JSON에 기록한다.
	// 역직렬화 시에도 그대로 복원되어 포맷이 영구 보존된다.
	// =========================================================================
	public boolean isHexAddress() {
		return hexAddress;
	}

	public void setHexAddress(boolean hexAddress) {
		this.hexAddress = hexAddress;
	}

	// =========================================================================
	// ✅ [추가] 주소를 원래 포맷 문자열로 반환하는 편의 메서드.
	// ProfileController·ModbusTerminal 양쪽에서 동일하게 쓸 수 있도록
	// DTO 안에 포맷 로직을 단일 소스(Single Source)로 관리한다.
	// =========================================================================
	@JsonIgnore
	public String getFormattedAddress() {
		return hexAddress ? String.format("0x%04X", address) : String.valueOf(address);
	}

	// =========================================================================
	// Getter / Setter
	// =========================================================================
	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public int getSlaveId() {
		return slaveId;
	}

	public void setSlaveId(int slaveId) {
		this.slaveId = slaveId;
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

	public void setSelected(boolean s) {
		this.selected = s;
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

	public void setItemName(String n) {
		this.name = n;
	}

	public FunctionCode getFunctionCode() {
		return functionCode;
	}

	public void setFunctionCode(FunctionCode fc) {
		this.functionCode = fc;
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
