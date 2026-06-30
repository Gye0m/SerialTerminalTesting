package com.DSK.serial.manager;

import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.serial.converter.ProtocolConverter;
import com.DSK.serial.core.PacketReceiver;
import com.DSK.serial.core.SerialConnectionManager;
import com.DSK.ui.modbus.ModbusTerminal;

/**
 * Raw HEX 송수신 전용 매니저.
 *
 * 수동/자동 검침과는 완전히 별개의 "부가 기능"이다. 통신이 연결되어 있을 때
 * 임의의 HEX 바이트를 직접 보내보고 응답을 그대로 화면에 띄워주는, 어디까지나
 * 임시 점검용 도구다.
 */
public class RawHexManager {

	private static final Logger log = LoggerFactory.getLogger(RawHexManager.class);

	private final ModbusTerminal terminal;
	private final SerialConnectionManager connectionManager;

	private OutputStream outputStream;

	// ✅ [신규] 송신 직전 "다음 응답은 이 Slave ID 의 것" 이라고 PacketReceiver 에
	// 알려주기 위한 참조. null 이면(연결 전 호출 등) 그냥 건너뛴다 — 필수 의존성이
	// 아니라 정확도를 높여주는 보조 연결이기 때문.
	private PacketReceiver packetReceiver;

	private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread t = new Thread(r, "RawHex-Timeout");
		t.setDaemon(true);
		return t;
	});

	private ScheduledFuture<?> timeoutTask;

	private volatile boolean waitingResponse = false;

	private long requestTime;

	private int TIMEOUT_MS = 1000;

	public RawHexManager(ModbusTerminal terminal, SerialConnectionManager connectionManager) {
		this.terminal = terminal;
		this.connectionManager = connectionManager;
	}

	/**
	 * ✅ [신규] PacketReceiver 연결. setExpectedSlaveId() 로 송신 직전 목적지
	 * Slave ID 를 알려주기 위해 필요하다. setRawHexManager() 와 같은 시점에
	 * 짝으로 호출해주면 된다 (연결부 양방향 와이어링).
	 */
	public void setPacketReceiver(PacketReceiver packetReceiver) {
		this.packetReceiver = packetReceiver;
	}

	// =========================================================================
	// 연결 바인딩 – outputStream 을 실제로 받아온다.
	// SerialConnectionManager.connect() 가 성공해서 포트가 열린 직후,
	// PacketReceiver 를 생성/attach 하는 지점에서 함께 호출해야 한다.
	// =========================================================================
	public void bind() {
		try {
			this.outputStream = connectionManager.getOutputStream();
			log.info("RawHexManager 바인딩 완료");
		} catch (Exception e) {
			log.error("RawHex bind 실패", e);
		}
	}

	// =========================================================================
	// TX (HEX 전송)
	// =========================================================================
	public synchronized void sendRawHex(String hex, boolean autoCrc) throws Exception {

		if (!connectionManager.isOpen()) {
			throw new IllegalStateException("Serial not connected");
		}
		if (outputStream == null) {
			throw new IllegalStateException("RawHexManager 가 아직 바인딩되지 않았습니다. (bind() 호출 필요)");
		}

		byte[] inputBytes = ProtocolConverter.convertHexToBytes(hex);

		if (inputBytes == null || inputBytes.length == 0) {
			throw new IllegalArgumentException("HEX 데이터가 비어있습니다.");
		}

		byte[] packet;

		// ================= CRC 처리 =================
		if (autoCrc) {
			int crc = ProtocolConverter.calculateCRC16(inputBytes, inputBytes.length);

			packet = new byte[inputBytes.length + 2];

			System.arraycopy(inputBytes, 0, packet, 0, inputBytes.length);

			packet[packet.length - 2] = (byte) (crc & 0xFF);
			packet[packet.length - 1] = (byte) ((crc >> 8) & 0xFF);
		} else {
			packet = inputBytes;
		}

		// ✅ [신규] 송신 직전, 패킷 첫 바이트(목적지 Slave ID)를 PacketReceiver 에 알려준다.
		// 이게 없으면 PacketReceiver 가 여전히 이전 Slave ID 를 기다리고 있어서,
		// Slave ID 1이 아닌 장비로 Raw HEX 테스트를 보내면 응답이 첫 바이트 필터에서
		// 통째로 걸러져 사라진다.
		if (packetReceiver != null) {
			packetReceiver.setExpectedSlaveId(packet[0] & 0xFF);
		}

		this.waitingResponse = true;
		this.requestTime = System.currentTimeMillis();

		// ================= TX 로그 =================
		String txHex = ProtocolConverter.convertBytesToHex(packet, packet.length);
		String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS"));

		SwingUtilities.invokeLater(() -> terminal.appendTxRxTerminal("[" + time + "][TX] " + txHex + "\n"));

		// ================= TIMEOUT =================
		if (timeoutTask != null && !timeoutTask.isDone()) {
			timeoutTask.cancel(true);
		}

		timeoutTask = scheduler.schedule(() -> {
			synchronized (RawHexManager.this) {
				if (!waitingResponse)
					return;
				waitingResponse = false;
			}
			SwingUtilities.invokeLater(() -> terminal.appendTxRxTerminal("[TIMEOUT] 응답 없음\n"));
			log.error("RawHex TIMEOUT 발생");
		}, TIMEOUT_MS, TimeUnit.MILLISECONDS);

		// ================= SEND =================
		outputStream.write(packet);
		outputStream.flush();
	}

	// =========================================================================
	// RX 처리 – PacketReceiver 가 완성된 패킷을 받을 때마다 호출
	// =========================================================================
	public synchronized void onReceive(byte[] packet) {

		if (!waitingResponse)
			return;

		waitingResponse = false;

		if (timeoutTask != null) {
			timeoutTask.cancel(true);
		}

		long elapsed = System.currentTimeMillis() - requestTime;

		String hex = ProtocolConverter.convertBytesToHex(packet, packet.length);

		String time = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss:SSS"));

		SwingUtilities
				.invokeLater(() -> terminal.appendTxRxTerminal("[" + time + "][RX] " + hex + " (" + elapsed + "ms)\n"));

		SwingUtilities.invokeLater(() -> terminal.updateRawResponseArea(hex + "\n"));

		log.info("RawHex 응답 완료 : {}", hex);
	}

	// =========================================================================
	// 상태
	// =========================================================================
	public boolean isWaitingResponse() {
		return waitingResponse;
	}

	public void setTimeout(int timeoutMs) {
		this.TIMEOUT_MS = timeoutMs;
	}
}