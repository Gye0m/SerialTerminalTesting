package com.DSK.model.dto.addressMap;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import com.DSK.model.dto.common.MeterAddressMap;
import com.DSK.model.dto.common.MeterInfo;
import com.DSK.serial.converter.DataType;
import com.DSK.serial.converter.EndianType;

public class Omni implements MeterAddressMap {

	private final Map<Integer, MeterInfo> map = new HashMap<>();

	public Omni() {

		// ================= 1. Signed long 계열 (DataType.INT32 / LITTLE)
		// =================
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

		map.put(0x0018, new MeterInfo("[전기] 현재 Voltage 1상", "mV", DataType.INT32, EndianType.LITTLE));
		map.put(0x001A, new MeterInfo("[전기] 현재 Voltage 2상", "mV", DataType.INT32, EndianType.LITTLE));
		map.put(0x001C, new MeterInfo("[전기] 현재 Voltage 3상", "mV", DataType.INT32, EndianType.LITTLE));

		map.put(0x001E, new MeterInfo("[전기] 현재 Current 1상", "mA", DataType.INT32, EndianType.LITTLE));
		map.put(0x0020, new MeterInfo("[전기] 현재 Current 2상", "mA", DataType.INT32, EndianType.LITTLE));
		map.put(0x0022, new MeterInfo("[전기] 현재 Current 3상", "mA", DataType.INT32, EndianType.LITTLE));

		map.put(0x0024, new MeterInfo("[전기] 현재 Phase 1상 (CosØ: 역률1)", "0.001", DataType.INT32, EndianType.LITTLE));
		map.put(0x0026, new MeterInfo("[전기] 현재 Phase 2상 (CosØ: 역률2)", "0.001", DataType.INT32, EndianType.LITTLE));
		map.put(0x0028, new MeterInfo("[전기] 현재 Phase 3상 (CosØ: 역률3)", "0.001", DataType.INT32, EndianType.LITTLE));

		map.put(0x002A, new MeterInfo("[전기] 현재주파수", "0.1 Hz", DataType.INT32, EndianType.LITTLE));
		map.put(0x002C,
				new MeterInfo("[전기] 현재 Power Relay (0:Off /Others:On)", "Off/On", DataType.INT32, EndianType.LITTLE));

		map.put(0x002E, new MeterInfo("[수도] 적산 검침 량", "m3", DataType.INT32, EndianType.LITTLE));
		map.put(0x0030, new MeterInfo("[수도] 순시치", "m3/h", DataType.INT32, EndianType.LITTLE));

		map.put(0x0032, new MeterInfo("[온수] 적산 검침 량", "m3", DataType.INT32, EndianType.LITTLE));
		map.put(0x0034, new MeterInfo("[온수] 순시치", "m3/h", DataType.INT32, EndianType.LITTLE));

		map.put(0x0036, new MeterInfo("[가스] 적산 검침 량", "m3", DataType.INT32, EndianType.LITTLE));
		map.put(0x0038, new MeterInfo("[가스] 순시치", "m3/h", DataType.INT32, EndianType.LITTLE));

		map.put(0x003A, new MeterInfo("[난방] 적산 검침 량", "MWh", DataType.INT32, EndianType.LITTLE));
		map.put(0x003C, new MeterInfo("[난방] 순시치", "kW", DataType.INT32, EndianType.LITTLE));

		map.put(0x003E, new MeterInfo("[난방] 유량 누적 량", "m3", DataType.INT32, EndianType.LITTLE));
		map.put(0x0040, new MeterInfo("[난방] 유량 순시치", "m3/h", DataType.INT32, EndianType.LITTLE));

		map.put(0x0042, new MeterInfo("[난방] 송류온도+환류온도", "℃", DataType.INT32, EndianType.LITTLE));
		map.put(0x0044, new MeterInfo("[기타] 적산 검침 량", "MWh", DataType.INT32, EndianType.LITTLE));
		map.put(0x0046, new MeterInfo("[기타] 순시치", "kW", DataType.INT32, EndianType.LITTLE));

		map.put(0x0048, new MeterInfo("[기타] 유량 누적 량", "m3", DataType.INT32, EndianType.LITTLE));
		map.put(0x004A, new MeterInfo("[기타] 유량 순시치", "m3/h", DataType.INT32, EndianType.LITTLE));

		map.put(0x004C, new MeterInfo("[기타] 송류온도+환류온도", "℃", DataType.INT32, EndianType.LITTLE));

		map.put(0x0052, new MeterInfo("[산업용열량계] 순시치", "kW", DataType.INT32, EndianType.LITTLE));
		map.put(0x0058, new MeterInfo("[산업용열량계] 유량 순시치", "m3/h", DataType.INT32, EndianType.LITTLE));
		map.put(0x005A, new MeterInfo("[산업용열량계] 온도(송/환)", "℃", DataType.INT32, EndianType.LITTLE));

		// ================= 2. Signed double 계열 (DataType.DOUBLE64 / LITTLE)
		// =================
		map.put(0x004E, new MeterInfo("[산업용열량계] 적산 검침 량", "MWh", DataType.DOUBLE64, EndianType.LITTLE));
		map.put(0x0054, new MeterInfo("[산업용열량계] 유량 누적 량", "m3", DataType.DOUBLE64, EndianType.LITTLE));
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