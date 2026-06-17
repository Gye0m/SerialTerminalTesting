package com.DSK.model.dto.common;

import java.util.Map;

public interface MeterAddressMap {
	MeterInfo get(int address);

	Map<Integer, MeterInfo> getAll();
}