package com.DSK.modbus.model;

import java.util.HashMap;
import java.util.Map;

/**
 * [MeterRowIndex]
 * 역할: (SlaveID, FC, 주소) 복합키 → 테이블 행 번호 매핑을 관리하는 인덱스 모델
 * 주요 기능:
 * - 기능 1: 응답 라우팅 — 검침 응답 수신 시 어느 행에 값을 표시할지 O(1) 조회 (find)
 * - 기능 2: 행 추가 시 중복 검사 (contains) — 동일 (슬레이브, FC, 주소) 조합 이중 등록 방지
 * - 기능 3: 키 변경 대응 (rekey) — 사용자가 SlaveID/주소/FC를 편집하면 이전 키 제거 후 재등록
 *
 * 키 체계: MeterRowDto.rowKey()가 유일한 키 생성 소스 — 이 클래스는 키 생성 방법을 모른다.
 * 스레드 모델: EDT 전용 (테이블 편집·응답 반영 모두 SafeEventDispatcher 경유 EDT에서 수행)
 */
public class MeterRowIndex {

	// ✅ [변경] Map<Integer, Integer> → Map<Long, Integer>
	private final Map<Long, Integer> index = new HashMap<>();

	// ─────────────────────────────────────────────────────────────────────────
	// 등록
	// ─────────────────────────────────────────────────────────────────────────

	/** dto의 현재 (slaveId, fc, address) 기준으로 행 번호 등록. */
	public void put(MeterRowDto dto, int row) {
		index.put(dto.getRowKey(), row);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// 조회
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * (slaveId, fc, address) 3개로 행 번호 조회.
	 * 응답 수신 시 "어느 행에 결과를 꽂을지"를 찾는 주 경로.
	 */
	public Integer find(int slaveId, int fc, int address) {
		return index.get(MeterRowDto.rowKey(slaveId, fc, address));
	}

	/** dto의 현재 키 등록 여부 확인. */
	public boolean contains(MeterRowDto dto) {
		return index.containsKey(dto.getRowKey());
	}

	/**
	 * (slaveId, fc, address) 3개로 등록 여부 확인.
	 * 행 추가 시 중복 검사용.
	 */
	public boolean contains(int slaveId, int fc, int address) {
		return index.containsKey(MeterRowDto.rowKey(slaveId, fc, address));
	}

	// ─────────────────────────────────────────────────────────────────────────
	// 제거
	// ─────────────────────────────────────────────────────────────────────────

	/** dto의 현재 키 매핑 제거. */
	public void remove(MeterRowDto dto) {
		index.remove(dto.getRowKey());
	}

	// ─────────────────────────────────────────────────────────────────────────
	// 재등록 (키 변경 시)
	// ─────────────────────────────────────────────────────────────────────────

	/**
	 * SlaveId / Address / FC 중 하나가 변경됐을 때 키를 재등록한다.
	 *
	 * 사용법 (TableModelListener 안):
	 * <pre>
	 *   long oldKey = dto.getRowKey();   // 변경 전 키를 먼저 캡처
	 *   dto.setSlaveId(newSlaveId);      // DTO 값 변경
	 *   rowIndex.rekey(oldKey, dto, row); // 이전 키 → 새 키로 재등록
	 * </pre>
	 *
	 * @param oldKey 변경 직전의 rowKey (dto 필드를 바꾸기 전에 캡처해야 한다)
	 * @param dto    이미 새 값이 세팅된 dto
	 * @param row    행 번호
	 */
	public void rekey(long oldKey, MeterRowDto dto, int row) {
		index.remove(oldKey);
		index.put(dto.getRowKey(), row);
	}

	// ─────────────────────────────────────────────────────────────────────────
	// 초기화 / 조회
	// ─────────────────────────────────────────────────────────────────────────

	public void clear() {
		index.clear();
	}

	public int size() {
		return index.size();
	}
}