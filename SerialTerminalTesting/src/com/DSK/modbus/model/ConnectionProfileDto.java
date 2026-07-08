package com.DSK.modbus.model;

import lombok.Data;

/** 맵 파일에 함께 저장되는 연결 설정. null이면 RTU 전용 맵으로 간주. */
@Data
public class ConnectionProfileDto {

	private String mode = "RTU"; // "RTU" | "TCP"

	// RTU
	private String portName;
	private int baudRate;
	private int dataBits = 8;
	private int stopBits = 1;
	private int parity = 0;

	// TCP
	private String tcpHost;
	private int tcpPort = 502;
	private int tcpTimeoutMs = 1000;

	public String getMode() {
		return mode;
	}

	public void setMode(String mode) {
		this.mode = mode;
	}

	public String getPortName() {
		return portName;
	}

	public void setPortName(String portName) {
		this.portName = portName;
	}

	public int getBaudRate() {
		return baudRate;
	}

	public void setBaudRate(int baudRate) {
		this.baudRate = baudRate;
	}

	public int getDataBits() {
		return dataBits;
	}

	public void setDataBits(int dataBits) {
		this.dataBits = dataBits;
	}

	public int getStopBits() {
		return stopBits;
	}

	public void setStopBits(int stopBits) {
		this.stopBits = stopBits;
	}

	public int getParity() {
		return parity;
	}

	public void setParity(int parity) {
		this.parity = parity;
	}

	public String getTcpHost() {
		return tcpHost;
	}

	public void setTcpHost(String tcpHost) {
		this.tcpHost = tcpHost;
	}

	public int getTcpPort() {
		return tcpPort;
	}

	public void setTcpPort(int tcpPort) {
		this.tcpPort = tcpPort;
	}

	public int getTcpTimeoutMs() {
		return tcpTimeoutMs;
	}

	public void setTcpTimeoutMs(int tcpTimeoutMs) {
		this.tcpTimeoutMs = tcpTimeoutMs;
	}

	// getter/setter 생략 (Lombok or 직접 생성)
}