package com.DSK.modbus.service;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.DSK.modbus.model.LogDto;

/**
 * ModbusEventListener 안전 래퍼 — EDT 강제 라우팅 + 호출 추적 + 예외 방어.
 *
 * 역할:
 *   1. 모든 콜백을 EDT로 라우팅 (이미 EDT면 직접 실행, 아니면 invokeLater)
 *   2. 이벤트별 enqueued/delivered 카운터로 EDT 백로그 감지
 *   3. 리스너 예외가 timeoutScheduler를 죽이지 않도록 격리
 *
 * 백로그 감지:
 *   enqueued - delivered 값이 임계값을 초과하면 logStats()가 경고 로그를 남긴다.
 *   이 값이 지속 증가한다면 EDT Heavy Rendering 또는 리스너 예외를 의심하라.
 *   logStats()는 사이클 완료 콜백 또는 UI 타이머에서 주기적으로 호출을 권장한다.
 */
public class SafeEventDispatcher implements ModbusEventListener {

	private static final Logger log = LoggerFactory.getLogger(SafeEventDispatcher.class);

	private final ModbusEventListener delegate;

	// ── 임계값 상수 ───────────────────────────────────────────────────────────
	private static final int BACKLOG_WARN_SYSTEM_LOG = 5;
	private static final int BACKLOG_WARN_TX_RX = 10;
	private static final int BACKLOG_WARN_METER_UPDATE = 5;

	// ── 이벤트별 카운터 ───────────────────────────────────────────────────────
	// enqueued: invokeLater로 큐에 넣은 수
	// delivered: EDT에서 실제 실행 완료된 수
	// enqueued - delivered = 현재 EDT 큐 대기 수
	private final AtomicLong systemLogEnqueued = new AtomicLong();
	private final AtomicLong systemLogDelivered = new AtomicLong();
	private final AtomicLong txRxEnqueued = new AtomicLong();
	private final AtomicLong txRxDelivered = new AtomicLong();
	private final AtomicLong meterUpdateEnqueued = new AtomicLong();
	private final AtomicLong meterUpdateDelivered = new AtomicLong();
	private final AtomicLong rawResponseEnqueued = new AtomicLong();
	private final AtomicLong rawResponseDelivered = new AtomicLong();

	// [신규] 물리 단선 이벤트 카운터 — 단선 빈도 진단용
	private final AtomicLong disconnectEnqueued = new AtomicLong();
	private final AtomicLong disconnectDelivered = new AtomicLong();

	// ── 마지막 처리 시각 ──────────────────────────────────────────────────────
	private volatile long lastSystemLogMs = 0;
	private volatile long lastMeterUpdateMs = 0;

	public SafeEventDispatcher(ModbusEventListener delegate) {
		this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
	}

	// =========================================================================
	// ModbusEventListener 구현
	// 공통 3단계: ① enqueued 증가 → ② EDT 라우팅 → ③ delivered 증가 + 타임스탬프
	// =========================================================================

	@Override
	public void onSystemLog(LogDto logDto) {
		long seq = systemLogEnqueued.incrementAndGet();
		dispatchToEdt("onSystemLog#" + seq, () -> {
			delegate.onSystemLog(logDto);
			systemLogDelivered.incrementAndGet();
			lastSystemLogMs = System.currentTimeMillis();
		});
	}

	@Override
	public void onTxRx(String hex) {
		long seq = txRxEnqueued.incrementAndGet();
		dispatchToEdt("onTxRx#" + seq, () -> {
			delegate.onTxRx(hex);
			txRxDelivered.incrementAndGet();
		});
	}

	@Override
	public void onMeterValueUpdated(int slaveId, int address, int fc, String value, String status,
			int avgPacketResponseTime) {
		long seq = meterUpdateEnqueued.incrementAndGet();
		dispatchToEdt("onMeterValueUpdated#" + seq + "[" + slaveId + ":0x" + String.format("%02X", fc) + ":0x"
				+ String.format("%04X", address) + "]", () -> {
					delegate.onMeterValueUpdated(slaveId, address, fc, value, status, avgPacketResponseTime);
					meterUpdateDelivered.incrementAndGet();
					lastMeterUpdateMs = System.currentTimeMillis();
				});
	}

	@Override
	public void onRawResponse(String hex) {
		long seq = rawResponseEnqueued.incrementAndGet();
		dispatchToEdt("onRawResponse#" + seq, () -> {
			delegate.onRawResponse(hex);
			rawResponseDelivered.incrementAndGet();
		});
	}

	@Override
	public void onPhysicalDisconnect() {
		// [수정] 단선 이벤트도 카운터 관리 (이전엔 추적 불가)
		long seq = disconnectEnqueued.incrementAndGet();
		dispatchToEdt("onPhysicalDisconnect#" + seq, () -> {
			delegate.onPhysicalDisconnect();
			disconnectDelivered.incrementAndGet();
			log.warn("[SafeEventDispatcher] 물리 단선 이벤트 전달 완료 (누적 {}회)", disconnectDelivered.get());
		});
	}

	// =========================================================================
	// EDT 라우팅 공통 메서드
	// =========================================================================

	/**
	 * 이미 EDT면 직접 실행, 아니면 invokeLater.
	 * 직접 실행 분기 필요 이유: invokeLater는 다음 EDT 사이클에 실행된다.
	 * 버튼 핸들러·Timer 콜백처럼 이미 EDT 위에 있을 때는 즉시 실행이 더 자연스럽다.
	 */
	private void dispatchToEdt(String eventLabel, Runnable action) {
		if (SwingUtilities.isEventDispatchThread()) {
			runSafely(eventLabel, action);
		} else {
			SwingUtilities.invokeLater(() -> runSafely(eventLabel, action));
		}
	}

	/**
	 * 예외 방어 실행.
	 * 리스너 구현체 예외가 timeoutScheduler로 전파되어 통신 스케줄러를 죽이는 것을 막는다.
	 */
	private void runSafely(String eventLabel, Runnable action) {
		try {
			action.run();
		} catch (Exception e) {
			log.error("[SafeEventDispatcher] {} 처리 중 예외 발생: {}", eventLabel, e.getMessage(), e);
		}
	}

	// =========================================================================
	// 진단 및 통계
	// =========================================================================

	/**
	 * 이벤트 처리 통계를 로그에 출력한다.
	 * 사이클 완료 콜백 또는 UI Timer에서 주기적으로 호출 권장.
	 */
	public void logStats() {
		long logPending = systemLogEnqueued.get() - systemLogDelivered.get();
		long txRxPending = txRxEnqueued.get() - txRxDelivered.get();
		long meterPending = meterUpdateEnqueued.get() - meterUpdateDelivered.get();
		long disconnects = disconnectDelivered.get();

		boolean hasBacklog = logPending > BACKLOG_WARN_SYSTEM_LOG || txRxPending > BACKLOG_WARN_TX_RX
				|| meterPending > BACKLOG_WARN_METER_UPDATE;

		if (hasBacklog) {
			log.warn("[EventDispatcher ⚠️ 백로그] SystemLog(대기:{}) TxRx(대기:{}) Meter(대기:{}) | 단선누적:{}회", logPending,
					txRxPending, meterPending, disconnects);
		} else {
			log.debug("[EventDispatcher ✅ 정상] SysLog(큐:{}/완:{}) TxRx(큐:{}/완:{}) Meter(큐:{}/완:{}) | 단선:{}회",
					systemLogEnqueued.get(), systemLogDelivered.get(), txRxEnqueued.get(), txRxDelivered.get(),
					meterUpdateEnqueued.get(), meterUpdateDelivered.get(), disconnects);
		}
	}

	/** 요약 문자열 반환 (UI 상태바 또는 헬스체크용) */
	public String getStatsSummary() {
		return String.format("SysLog[큐:%d/완:%d] TxRx[큐:%d/완:%d] Meter[큐:%d/완:%d] 단선:%d회 | 마지막로그:%dms전 마지막검침:%dms전",
				systemLogEnqueued.get(), systemLogDelivered.get(), txRxEnqueued.get(), txRxDelivered.get(),
				meterUpdateEnqueued.get(), meterUpdateDelivered.get(), disconnectDelivered.get(),
				lastSystemLogMs > 0 ? System.currentTimeMillis() - lastSystemLogMs : -1,
				lastMeterUpdateMs > 0 ? System.currentTimeMillis() - lastMeterUpdateMs : -1);
	}

	/** EDT 큐 백로그 여부 빠른 확인 */
	public boolean hasBacklog() {
		return (systemLogEnqueued.get() - systemLogDelivered.get() > BACKLOG_WARN_SYSTEM_LOG)
				|| (txRxEnqueued.get() - txRxDelivered.get() > BACKLOG_WARN_TX_RX)
				|| (meterUpdateEnqueued.get() - meterUpdateDelivered.get() > BACKLOG_WARN_METER_UPDATE);
	}

	/** 카운터 전체 초기화 (새 검침 세션 시작 시 호출) */
	public void resetCounters() {
		systemLogEnqueued.set(0);
		systemLogDelivered.set(0);
		txRxEnqueued.set(0);
		txRxDelivered.set(0);
		meterUpdateEnqueued.set(0);
		meterUpdateDelivered.set(0);
		rawResponseEnqueued.set(0);
		rawResponseDelivered.set(0);
		disconnectEnqueued.set(0);
		disconnectDelivered.set(0);
		log.info("[SafeEventDispatcher] 카운터 초기화 완료");
	}
}