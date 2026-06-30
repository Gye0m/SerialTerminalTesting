package com.DSK.serial.manager;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.DSK.model.dto.common.MeterRowDto;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class MapFileManager {
	private final ObjectMapper mapper = new ObjectMapper();
	private final File configDir = new File("./config/address_tables");

	public MapFileManager() {
		if (!configDir.exists()) {
			configDir.mkdirs();
		}
	}

	// 1. 디스크에서 텍스트(.txt) 파일 목록만 읽어서 반환
	public List<String> getProfileList() {
		List<String> profiles = new ArrayList<>();
		if (configDir.exists() && configDir.isDirectory()) {
			// 🎯 [.json -> .txt 변경] 확장자 필터를 .txt로 수정합니다.
			File[] files = configDir.listFiles((dir, name) -> name.endsWith(".txt"));
			if (files != null) {
				for (File file : files) {
					// 드롭다운 리스트에 띄울 때는 뒤의 .txt를 떼고 순수 이름만 추출
					profiles.add(file.getName().replace(".txt", ""));
				}
			}
		}
		return profiles;
	}

	// 2. 파일 저장 로직 독립 (확장자 우회 적용)
	public void saveProfile(String profileName, List<MeterRowDto> dtoList) throws Exception {
		// 🎯 파일명이 들어올 때 외형만 .txt로 붙여서 저장합니다.
		// 내부 데이터는 여전히 깔끔하게 줄바꿈 및 정렬된 예쁜 JSON 데이터(PrettyPrinter)가 들어갑니다.
		File targetFile = new File(configDir, profileName + ".txt");
		mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, dtoList);
	}

	// 3. 파일 로드 로직 독립 (텍스트 파일 읽기)
	public List<MeterRowDto> loadProfile(String profileName) throws Exception {
		// 🎯 읽어올 때도 해당 디렉토리의 .txt 파일을 매핑합니다.
		File file = new File(configDir, profileName + ".txt");
		if (!file.exists())
			return new ArrayList<>();

		return mapper.readValue(file, new TypeReference<List<MeterRowDto>>() {
		});
	}

	// 4. 파일 삭제 로직 독립 (.txt 대상)
	public boolean deleteProfile(String profileName) {
		// 🎯 삭제 타겟도 .txt로 동기화합니다.
		File file = new File(configDir, profileName + ".txt");
		return file.exists() && file.delete();
	}

	// 5. 외부에서 공유받은 .txt 파일을 프로그램 내부 폴더로 가져오는 기능
	public String importExternalProfile(File externalFile) throws Exception {
		if (externalFile == null || !externalFile.exists()) {
			throw new IllegalArgumentException("올바르지 않은 파일입니다.");
		}

		// 파일명 추출 (예: "A동_메터.txt")
		String fileName = externalFile.getName();

		// 내부 저장소에 저장될 경로 설정 (./config/address_tables/A동_메터.txt)
		File targetFile = new File(configDir, fileName);

		// 안전하게 파일 복사 진행 (Jackson을 이용해 올바른 구조의 맵 데이터인지 검증하며 복사)
		List<MeterRowDto> verifiedData = mapper.readValue(externalFile, new TypeReference<List<MeterRowDto>>() {
		});
		mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, verifiedData);

		// 드롭다운에 등록할 수 있도록 확장자를 뗀 순수 프로필명 반환 ("A동_메터")
		return fileName.replace(".txt", "");
	}

	// 6. 현재 가공된 데이터를 원하는 경로의 텍스트 파일로 저장(내보내기)
	public void exportProfileToExternal(File targetFile, List<MeterRowDto> dtoList) throws Exception {
		if (targetFile == null) {
			throw new IllegalArgumentException("저장할 파일 경로가 올바르지 않습니다.");
		}

		// 사용자가 확장자를 안 붙였을 경우를 대비해 .txt 강제 락킹
		String path = targetFile.getAbsolutePath();
		if (!path.toLowerCase().endsWith(".txt")) {
			targetFile = new File(path + ".txt");
		}

		// 구조화된 JSON 데이터 형식을 그대로 유지한 채 이쁘게 텍스트 파일로 출력
		mapper.writerWithDefaultPrettyPrinter().writeValue(targetFile, dtoList);
	}
}