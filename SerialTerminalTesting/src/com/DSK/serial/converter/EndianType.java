package com.DSK.serial.converter;

// 엔디안 타입
public enum EndianType {
	LITTLE, // 0: DCBA
	BIG, // 1: ABCD
	WORD_SWAP, // 2: CDAB
	BYTE_SWAP // 3: BADC
}