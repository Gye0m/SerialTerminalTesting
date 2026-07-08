package com.DSK.modbus.service;

import com.DSK.modbus.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.net.Socket;
import java.util.Map;

/**
 * Modbus TCP 통신 매니저 스켈레톤.
 *
 * RTU와의 차이:
 *   요청: CRC 제거 + MBAP 헤더(6B) 추가 [TxID(2)+ProtoID(2)+Length(2)+UnitID(1)]
 *   응답: CRC 없음, MBAP Length 기준 프레임 확정
 *   연결: Socket(ip, 502) — Half-Duplex 제약 없음, 다중 요청 가능(Transaction ID)
 */
public class TcpConnectionManager implements IModbusConnectionManager {

	private static final Logger log = LoggerFactory.getLogger(TcpConnectionManager.class);

	// ── 연결 설정
	private final String host;
	private final int port; // Modbus TCP 기본: 502
	private final int timeoutMs;

	// ── 소켓 자원
	private volatile Socket socket;
	private volatile boolean connected = false;

	// ── ModbusManager 위임 (RTU와 동일한 큐·오류 처리 재사용)
	private ModbusManager modbusManager;

	public TcpConnectionManager(String host, int port, int timeoutMs) {
		this.host = host;
		this.port = port;
		this.timeoutMs = timeoutMs;
	}

	@Override
	public boolean connect(ModbusManager modbusManager) {
		this.modbusManager = modbusManager;
		try {
			socket = new Socket(host, port);
			socket.setSoTimeout(timeoutMs);
			socket.getOutputStream();
			socket.getInputStream();
			connected = true;

			// TODO: TcpPacketReader(inputStream, modbusManager) 시작
			//       InputStream.read() 루프로 MBAP Length 기준 프레임 조립
			//       → modbusManager.onModbusPacket(pduBytes) 호출
			//       RTU의 PacketReceiver.attach()와 동일한 역할

			log.info("TCP 연결 성공: {}:{}", host, port);
			return true;
		} catch (Exception e) {
			log.error("TCP 연결 실패: {}:{}", host, port, e);
			connected = false;
			return false;
		}
	}

	@Override
	public void disconnect() {
		connected = false;
		try {
			if (socket != null && !socket.isClosed())
				socket.close();
		} catch (Exception e) {
			log.error("TCP 소켓 종료 오류", e);
		} finally {
			socket = null;
		}
	}

	@Override
	public boolean isOpen() {
		return connected && socket != null && socket.isConnected() && !socket.isClosed();
	}

	/**
	 * TCP용 패킷 빌드.
	 * RTU: [SlaveID][FC][Addr H][Addr L][Qty H][Qty L][CRC L][CRC H]
	 * TCP: [TxID H][TxID L][0x00][0x00][Len H][Len L][UnitID][FC][Addr H][Addr L][Qty H][Qty L]
	 */
	@Override
	public PendingRequest buildRequest(MeterRowDto info) {
		// TODO: MBAP 헤더 조립 + CRC 없는 PDU 빌드
		//       modbusManager.buildRequest(info)의 TCP 변형판
		//       transactionId 증가 및 패킷 헤더 삽입
		throw new UnsupportedOperationException("TCP buildRequest — 미구현");
	}

	// ── 아래는 ModbusManager에 그대로 위임 (큐·오류 처리 RTU와 동일)
	@Override
	public void enqueue(PendingRequest request) {
		modbusManager.enqueue(request);
	}

	@Override
	public void startSend() {
		modbusManager.startSend();
	}

	@Override
	public Map<Long, ErrorStatusDto> getErrorMap() {
		return modbusManager.getErrorMap();
	}

	@Override
	public void clearAllErrorStatus() {
		modbusManager.clearAllErrorStatus();
	}

	@Override
	public boolean isCycleComplete() {
		return modbusManager.isCycleComplete();
	}

	// ── TCP 전용 설정 조회
	public String getHost() {
		return host;
	}

	public int getPort() {
		return port;
	}

	public int getTimeoutMs() {
		return timeoutMs;
	}
}