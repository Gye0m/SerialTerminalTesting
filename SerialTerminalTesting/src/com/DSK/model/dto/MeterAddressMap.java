package com.DSK.model.dto;

import java.util.HashMap;
import java.util.Map;

public class MeterAddressMap {

	// 시작주소, 데이터 정보, 단위
	private final Map<Integer, MeterInfo> map = new HashMap<>();

	public MeterAddressMap() {
		// 01 ~ 04: 유효전력
		map.put(0x0000, new MeterInfo("수전 적산 유효 전력량", "Wh"));
		map.put(0x0002, new MeterInfo("송전 적산 유효 전력량", "Wh"));
		map.put(0x0004, new MeterInfo("수전 유효 전력", "W"));
		map.put(0x0006, new MeterInfo("송전 유효 전력", "W"));

		// 05 ~ 12: 무효전력
		map.put(0x0008, new MeterInfo("수전 적산 지상 무효 전력량", "Varh"));
		map.put(0x000A, new MeterInfo("수전 적산 진상 무효 전력량", "Varh"));
		map.put(0x000C, new MeterInfo("수전 지상 무효 전력", "Var"));
		map.put(0x000E, new MeterInfo("수전 진상 무효 전력", "Var"));
		map.put(0x0010, new MeterInfo("송전 적산 지상 무효 전력량", "Varh"));
		map.put(0x0012, new MeterInfo("송전 적산 진상 무효 전력량", "Varh"));
		map.put(0x0014, new MeterInfo("송전 지상 무효 전력", "Var"));
		map.put(0x0016, new MeterInfo("송전 진상 무효 전력", "Var"));

		// 13 ~ 15: 전압
		map.put(0x0018, new MeterInfo("현재 Voltage 1상", "mV"));
		map.put(0x001A, new MeterInfo("현재 Voltage 2상", "mV"));
		map.put(0x001C, new MeterInfo("현재 Voltage 3상", "mV"));

		// 16 ~ 18: 전류
		map.put(0x001E, new MeterInfo("현재 Current 1상", "mA"));
		map.put(0x0020, new MeterInfo("현재 Current 2상", "mA"));
		map.put(0x0022, new MeterInfo("현재 Current 3상", "mA"));

		// 19 ~ 21: 역률
		map.put(0x0024, new MeterInfo("현재 Phase 1상 (CosØ)", "0.001"));
		map.put(0x0026, new MeterInfo("현재 Phase 2상 (CosØ)", "0.001"));
		map.put(0x0028, new MeterInfo("현재 Phase 3상 (CosØ)", "0.001"));

		// 22: 주파수
		map.put(0x002A, new MeterInfo("현재 주파수", "0.1 Hz"));

		// 23: 릴레이 상태
		map.put(0x002C, new MeterInfo("Power Relay 상태", "On/Off"));

		// ==========================================

		// 24 ~ 29 : 수도 / 온수 / 가스
		map.put(0x002E, new MeterInfo("수도 적산 검침량", "m3"));
		map.put(0x0030, new MeterInfo("수도 순시치", "m3/h"));

		map.put(0x0032, new MeterInfo("온수 적산 검침량", "m3"));
		map.put(0x0034, new MeterInfo("온수 순시치", "m3/h"));

		map.put(0x0036, new MeterInfo("가스 적산 검침량", "m3"));
		map.put(0x0038, new MeterInfo("가스 순시치", "m3/h"));

		// 30 ~ 33 : 난방
		map.put(0x003A, new MeterInfo("난방 적산 검침량", "MWh"));
		map.put(0x003C, new MeterInfo("난방 순시치", "kW"));

		map.put(0x003E, new MeterInfo("난방 유량 누적량", "m3"));
		map.put(0x0040, new MeterInfo("난방 유량 순시치", "m3/h"));

		// 34 ~ 35 : 온도
		map.put(0x0042, new MeterInfo("난방 송류온도+환류온도", "℃"));
		map.put(0x004C, new MeterInfo("기타 송류온도+환류온도", "℃"));

		// 35 ~ 38 : 기타
		map.put(0x0044, new MeterInfo("기타 적산 검침량", "MWh"));
		map.put(0x0046, new MeterInfo("기타 순시치", "kW"));

		map.put(0x0048, new MeterInfo("기타 유량 누적량", "m3"));
		map.put(0x004A, new MeterInfo("기타 유량 순시치", "m3/h"));

		// 40 ~ 45 : 산업용 열량계 (중요: 4byte = double)
		map.put(0x004E, new MeterInfo("산업용열량계 적산 검침량", "MWh (double)"));
		map.put(0x0052, new MeterInfo("산업용열량계 순시치", "kW"));

		map.put(0x0054, new MeterInfo("산업용열량계 유량 누적량", "m3 (double)"));
		map.put(0x0058, new MeterInfo("산업용열량계 유량 순시치", "m3/h"));

		map.put(0x005A, new MeterInfo("산업용열량계 온도(송/환)", "℃"));
	}

	public MeterInfo get(int address) {
		return map.get(address);
	}
}