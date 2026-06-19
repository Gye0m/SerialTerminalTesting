package com.DSK.serial.constant;

public enum FunctionCode {
    READ_COILS(0x01, "Read Coils (0xxxx)", true),
    READ_DISCRETE_INPUTS(0x02, "Read Discrete Inputs (1xxxx)", true),
    READ_HOLDING_REGISTERS(0x03, "Read Holding Registers (4xxxx)", true),
    READ_INPUT_REGISTERS(0x04, "Read Input Registers (3xxxx)", true), 
    
    WRITE_SINGLE_COIL(0x05, "Write Single Coil", false),
    WRITE_SINGLE_REGISTER(0x06, "Write Single Register", false),
    WRITE_MULTIPLE_COILS(0x15, "Write Multiple Coils", false),
    WRITE_MULTIPLE_REGISTERS(0x16, "Write Multiple Registers", false);

    private final int code;
    private final String description;
    private final boolean isReadOp;

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

    /**
     * 🎯 [핵심 유틸] 들어온 바이트(int) 값을 기반으로 일치하는 Enum을 찾아주는 역추적 매퍼
     */
    public static FunctionCode fromCode(int code) {
        // 에러 응답(Exception: 예컨대 0x84 등)인 경우 맨 앞 비트(0x80)를 마스킹해서 원본 기능코드를 복원
        int targetCode = (code & 0x80) != 0 ? (code & 0x7F) : code;
        
        for (FunctionCode fc : values()) {
            if (fc.getCode() == targetCode) {
                return fc;
            }
        }
        return null; // 없는 코드일 경우
    }
}