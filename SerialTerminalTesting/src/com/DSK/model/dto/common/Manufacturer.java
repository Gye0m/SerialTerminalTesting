package com.DSK.model.dto.common;

public enum Manufacturer {
	OMNI("옴니 (OMNI)"), 
	WIZIT("위지트 (WIZIT)");

	private final String name;

	Manufacturer(String name) {
		this.name = name;
	}

	@Override
	public String toString() {
		return name; // 콤보박스에 이 이름으로 표시됩니다.
	}
}