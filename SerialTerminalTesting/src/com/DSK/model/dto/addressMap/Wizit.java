package com.DSK.model.dto.addressMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.DSK.model.dto.common.MeterAddressMap;
import com.DSK.model.dto.common.MeterInfo; // 공통으로 분리한 MeterInfo 임포트
import com.DSK.serial.constant.DataType;
import com.DSK.serial.constant.EndianType;

public class Wizit implements MeterAddressMap {

	private final Map<Integer, MeterInfo> map = new HashMap<>();

	public Wizit() {

		// ================= 1. Signed long 계열 (DataType.INT32) =================
		map.put(0x0000, new MeterInfo("[전기] 수전 적산 유효 전력량", "Wh", DataType.INT32, EndianType.LITTLE));
		map.put(0x0002, new MeterInfo("[전기] 송전 적산 유효 전력량", "Wh", DataType.INT32, EndianType.LITTLE));
		map.put(0x0004, new MeterInfo("[전기] 수전 유효 전력", "W", DataType.INT32, EndianType.LITTLE));
		map.put(0x0006, new MeterInfo("[전기] 송전 유효 전력", "W", DataType.INT32, EndianType.LITTLE));

		map.put(0x0008, new MeterInfo("[전기] 수전 적산 지상 무효 전력량", "Varh", DataType.INT32, EndianType.LITTLE));
		map.put(0x000A, new MeterInfo("[전기] 수전 적산 진상 무효 전력량", "Varh", DataType.INT32, EndianType.LITTLE));
		map.put(0x000C, new MeterInfo("[전기] 수전 지상 무효 전력", "Var", DataType.INT32, EndianType.LITTLE));
		map.put(0x000E, new MeterInfo("[전기] 수전 진상 무효 전력", "Var", DataType.INT32, EndianType.LITTLE));

		map.put(0x0010, new MeterInfo("[전기] 송전 적산 지상 무효 전력량", "Varh", DataType.INT32, EndianType.LITTLE));
		map.put(0x0012, new MeterInfo("[전기] 송전 적산 진상 무효 전력량", "Varh", DataType.INT32, EndianType.LITTLE));
		map.put(0x0014, new MeterInfo("[전기] 송전 지상 무효 전력", "Var", DataType.INT32, EndianType.LITTLE));
		map.put(0x0016, new MeterInfo("[전기] 송전 진상 무효 전력", "Var", DataType.INT32, EndianType.LITTLE));

		// ================= 2. Float 계열 (DataType.FLOAT32) =================
		map.put(0x0018, new MeterInfo("[전기] 현재 Voltage 1상", "V", DataType.FLOAT32, EndianType.LITTLE));
		map.put(0x001A, new MeterInfo("[전기] 현재 Voltage 2상", "V", DataType.FLOAT32, EndianType.LITTLE));
		map.put(0x001C, new MeterInfo("[전기] 현재 Voltage 3상", "V", DataType.FLOAT32, EndianType.LITTLE));

		map.put(0x001E, new MeterInfo("[전기] 현재 Current 1상", "A", DataType.FLOAT32, EndianType.LITTLE));
		map.put(0x0020, new MeterInfo("[전기] 현재 Current 2상", "A", DataType.FLOAT32, EndianType.LITTLE));
		map.put(0x0022, new MeterInfo("[전기] 현재 Current 3상", "A", DataType.FLOAT32, EndianType.LITTLE));

		map.put(0x0024, new MeterInfo("[전기] 현재 Phase 1상 (CosØ: 역률1)", "단위없음", DataType.FLOAT32, EndianType.LITTLE));
		map.put(0x0026, new MeterInfo("[전기] 현재 Phase 2상 (CosØ: 역률2)", "단위없음", DataType.FLOAT32, EndianType.LITTLE));
		map.put(0x0028, new MeterInfo("[전기] 현재 Phase 3상 (CosØ: 역률3)", "단위없음", DataType.FLOAT32, EndianType.LITTLE));

		// ================= 3. Int (long) 및 하단 주황색 영역 =================
		map.put(0x002A, new MeterInfo("[전기] 현재주파수", "Hz", DataType.INT32, EndianType.LITTLE));
		map.put(0x002C, new MeterInfo("[전기] 평균역률", "단위없음", DataType.FLOAT32, EndianType.LITTLE));
	}

	@Override
	public MeterInfo get(int address) {
		return map.get(address);
	}

	@Override
	public Map<Integer, MeterInfo> getAll() {
		return Collections.unmodifiableMap(map);
	}
}