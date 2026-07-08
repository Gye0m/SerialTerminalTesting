package com.DSK.modbus.model.constant;

public enum FunctionCode {
	READ_COILS(0x01, "Read Coils (0xxxx)", true), READ_DISCRETE_INPUTS(0x02, "Read Discrete Inputs (1xxxx)", true),
	READ_HOLDING_REGISTERS(0x03, "Read Holding Registers (4xxxx)", true),
	READ_INPUT_REGISTERS(0x04, "Read Input Registers (3xxxx)", true),

	WRITE_SINGLE_COIL(0x05, "Write Single Coil", false), WRITE_SINGLE_REGISTER(0x06, "Write Single Register", false),

	// ✅ [수정 유지] 0x15(21)→0x0F(15), 0x16(22)→0x10(16). 표준 Modbus 함수코드로 교정.
	WRITE_MULTIPLE_COILS(0x0F, "Write Multiple Coils", false),
	WRITE_MULTIPLE_REGISTERS(0x10, "Write Multiple Registers", false);

	private final int code;
	private final String description;
	private final boolean isReadOp; // 읽기모드 or 쓰기모드

	FunctionCode(int code, String description, boolean isReadOp) {
		this.code = code;
		this.description = description;
		this.isReadOp = isReadOp;
	}

	public int getCode() {
		return code;
	}

	public String getDescription() {
		return description;
	}

	public boolean isReadOp() {
		return isReadOp;
	}

	public static FunctionCode fromCode(int code) {
		int targetCode = (code & 0x80) != 0 ? (code & 0x7F) : code;

		for (FunctionCode fc : values()) {
			if (fc.getCode() == targetCode) {
				return fc;
			}
		}
		return null; // 없는 코드일 경우
	}
}
