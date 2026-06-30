package com.DSK.Metering;

import com.DSK.model.dto.MeterReading;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class Analyzer {

	private static final String GEMINI_API_KEY = "AQ.Ab8RN6IkMUPChTwxgoK12BKSBJp1DtHHc3O6giTHbM80WE9Z-Q";
	private static final String GEMINI_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key="
			+ GEMINI_API_KEY;

	public static void main(String[] args) {
		// 함수 즉시 호출
	}

	public static String analyzeMeterData(List<MeterReading> list) {
		// 방어 코드: 혹시나 리스트가 비어있다면 바로 리턴
		if (list == null || list.isEmpty()) {
			System.out.println("분석할 데이터가 없습니다.");
			return "분석할 데이터가 없습니다.";
		}

		// Gemini에게 던질 전력 데이터를 저장할 문자열 변수
		StringBuilder dataSummary = new StringBuilder();
		String pureResult = "AI 분석에 실패했습니다.";

		for (MeterReading r : list) {
			dataSummary.append(String.format("ID: %s, 시간: %s, 항목: %s, 계측값: %s %s\n", r.getReadingId(),
					r.getReadingTime(), r.getReadingName(), r.getReadingValue(),
					(r.getUnit() != null ? r.getUnit() : "") // 단위가 있다면
																																																						// 단위까지
																																																						// 결합
			));
		}
		// AI 프롬프트 작성
		String prompt = "너는 전력 데이터 분석 전문가야. 아래 제공하는 전력 검침 데이터를 보고, " + "1) 시간 흐름에 따른 데이터 수치 변화 추이, "
				+ "2) 수치들 간의 특별한 관계나 이상 징후가 보인다면 요약해줘.\n그리고, 해당 내용 정리해서 줄때 가식성 좋게 **이런거 넣지말고 번호같은거 넣어서 만들어줘\n"
				+ "[검침 데이터 목록]\n" + dataSummary.toString();

		// JSON 형태로 포맷팅 및 Gemini 전송 파트 (아래는 기존 코드와 완전히 동일)
		try {
			ObjectMapper mapper = new ObjectMapper();
			ObjectNode rootNode = mapper.createObjectNode();
			ArrayNode contentsArray = rootNode.putArray("contents");
			ObjectNode contentNode = contentsArray.addObject();
			ArrayNode partsArray = contentNode.putArray("parts");
			ObjectNode partNode = partsArray.addObject();
			partNode.put("text", prompt);

			String jsonPayload = mapper.writeValueAsString(rootNode);

			HttpClient client = HttpClient.newHttpClient();
			HttpRequest request = HttpRequest.newBuilder().uri(URI.create(GEMINI_URL))
					.header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
					.build();

			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

			if (response.statusCode() == 200) {
				System.out.println("\n=== Gemini AI의 분석 결과 ===");
				ObjectMapper resMapper = new ObjectMapper();
				JsonNode root = resMapper.readTree(response.body());

				pureResult = root.path("candidates").path(0).path("content").path("parts").path(0).path("text")
						.asText();

			} else {
				System.err.println("Gemini API 전송 실패 코드: " + response.statusCode());
				System.err.println("에러 내용: " + response.body());
			}

		} catch (Exception e) {
			System.err.println("AI 전송 중 에러 발생: " + e.getMessage());
			e.printStackTrace();
		}
		return pureResult;
	}
}