package com.DSK.modbus.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.DSK.modbus.model.MeterRowDto;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * [MapFileManager]
 * 역할: 검침 맵 프로필의 파일 시스템 영속화(JSON-in-txt)를 담당하는 서비스
 * 주요 기능:
 * - 기능 1: 프로필 저장/로드 — Jackson으로 List&lt;MeterRowDto&gt; ↔ .txt(JSON) 직렬화,
 *           저장 경로는 {실행경로}/config/address_tables/ 고정
 * - 기능 2: 프로필 목록 조회/삭제 — .txt 확장자 파일만 스캔
 * - 기능 3: 외부 파일 가져오기/내보내기 — 가져오기 시 역직렬화 검증을 먼저 수행해
 *           손상 파일이 내부 폴더에 복사되는 것을 차단
 *
 * 방어 설정: FAIL_ON_UNKNOWN_PROPERTIES=false — DTO 스키마 변경(필드 추가/제거) 시에도
 *           기존 저장 파일 로드가 깨지지 않도록 미지 필드를 무시
 */
public class MapFileManager {
	private final ObjectMapper mapper = new ObjectMapper();
	String baseDir = System.getProperty("user.dir");
	private final File configDir = new File(baseDir, "config/address_tables");
	//	private final File configDir = new File("./config/address_tables");

	public MapFileManager() {
		// ✅ [추가] 역직렬화 시 모르는 JSON 필드를 만나도 예외를 던지지 않고 그냥
		// 무시하도록 한다. MeterRowDto 에 @JsonIgnore 가 빠진 계산 프로퍼티가
		// 실수로 하나라도 노출되면(예: getRowKey()), 예전엔 저장은 되는데 다음
		// 로드가 100% 실패하는 사고로 이어졌다. 이 설정은 그런 종류의 스키마
		// 변경에 대한 일반적인 방어막이다.
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		if (!configDir.exists()) {
			configDir.mkdirs();
		}
	}

	// 1. 디스크에서 텍스트(.txt) 파일 목록만 읽어서 반환
	public List<String> getProfileList() {
		List<String> profiles = new ArrayList<>();
		if (configDir.exists() && configDir.isDirectory()) {
			File[] files = configDir.listFiles((dir, name) -> name.endsWith(".txt"));
			if (files != null) {
				for (File file : files) {
					profiles.add(file.getName().replace(".txt", ""));
				}
			}
		}
		return profiles;
	}

	// 2. 파일 저장 로직 독립 (확장자 우회 적용)
	public void saveProfile(String profileName, List<MeterRowDto> dtoList) throws Exception {
		File targetFile = new File(configDir, profileName + ".txt");
		mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, dtoList);
	}

	// 3. 파일 로드 로직 독립 (텍스트 파일 읽기)
	public List<MeterRowDto> loadProfile(String profileName) throws Exception {
		File file = new File(configDir, profileName + ".txt");
		if (!file.exists())
			return new ArrayList<>();

		return mapper.readValue(file, new TypeReference<List<MeterRowDto>>() {
		});
	}

	// 4. 파일 삭제 로직 독립 (.txt 대상)
	public boolean deleteProfile(String profileName) {
		File file = new File(configDir, profileName + ".txt");
		return file.exists() && file.delete();
	}

	// 5. 외부에서 공유받은 .txt 파일을 프로그램 내부 폴더로 가져오는 기능
	public String importExternalProfile(File externalFile) throws Exception {
		if (externalFile == null || !externalFile.exists()) {
			throw new IllegalArgumentException("올바르지 않은 파일입니다.");
		}

		String fileName = externalFile.getName();
		File targetFile = new File(configDir, fileName);

		List<MeterRowDto> verifiedData = mapper.readValue(externalFile, new TypeReference<List<MeterRowDto>>() {
		});
		mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, verifiedData);

		return fileName.replace(".txt", "");
	}

	// 6. 현재 가공된 데이터를 원하는 경로의 텍스트 파일로 저장(내보내기)
	public void exportProfileToExternal(File targetFile, List<MeterRowDto> dtoList) throws Exception {
		if (targetFile == null) {
			throw new IllegalArgumentException("저장할 파일 경로가 올바르지 않습니다.");
		}

		String path = targetFile.getAbsolutePath();
		if (!path.toLowerCase().endsWith(".txt")) {
			targetFile = new File(path + ".txt");
		}

		mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, dtoList);
	}
}