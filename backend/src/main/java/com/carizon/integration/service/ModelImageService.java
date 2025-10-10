package com.carizon.integration.service;

import com.carizon.integration.dto.CarPick;
import com.carizon.integration.mapper.CarizonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelImageService {

    private final CarizonMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String IMG_BASE = "https://img.kbchachacha.com/IMG/carimg/l/";

    /**
     * 모델코드별 대표 car_seq를 뽑아 1/2번 이미지를 다운로드합니다.
     *
     * @param targetDir 저장 폴더 (예: C:\\carizon\\model_image)
     * @param limit     처리할 모델 수 제한 (null이면 전체)
     */
    public void downloadRepresentativeImages(Path targetDir, Integer limit) throws Exception {
        if (targetDir == null) targetDir = Path.of("C:\\carizon\\model_image");
        Files.createDirectories(targetDir);

        List<CarPick> picks = mapper.selectRepresentativeCars(limit);
        log.info("대표 건수: {}", picks.size());

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        int ok = 0, fail = 0;
        for (CarPick pick : picks) {
            List<String> names = firstTwoFilenames(pick.getPayload());
            if (names.isEmpty()) {
                log.warn("[SKIP] fileNameArray 비어있음: model={}, carSeq={}", pick.getModelCode(), pick.getCarSeq());
                fail++;
                continue;
            }
            String folderTwo = folderByFourthDigit(pick.getCarSeq()); // imgNN/
            String firstFour = firstFourDigits(pick.getCarSeq());     // img####/

            if (names.size() >= 1) {
                String url1 = buildUrl(folderTwo, firstFour, pick.getCarSeq(), names.get(0));
                Path out1 = targetDir.resolve(pick.getCarCode() + "_first.jpg");
                boolean b1 = download(http, url1, out1);
                log.info("{} {} <- {}", b1 ? "[OK ]" : "[ERR]", out1, url1);
                ok += b1 ? 1 : 0;
                fail += b1 ? 0 : 1;
            }
            if (names.size() >= 2) {
                String url2 = buildUrl(folderTwo, firstFour, pick.getCarSeq(), names.get(1));
                Path out2 = targetDir.resolve(pick.getCarCode() + "_second.jpg");
                boolean b2 = download(http, url2, out2);
                log.info("{} {} <- {}", b2 ? "[OK ]" : "[ERR]", out2, url2);
                ok += b2 ? 1 : 0;
                fail += b2 ? 0 : 1;
            }
        }
        log.info("완료: 성공 {} 실패 {}", ok, fail);
    }

    private List<String> firstTwoFilenames(String payloadJson) {
        if (payloadJson == null || payloadJson.isBlank()) return List.of();
        try {
            JsonNode root = objectMapper.readTree(payloadJson);
            JsonNode arr = root.get("fileNameArray");
            List<String> out = new ArrayList<>(2);
            if (arr != null && arr.isArray()) {
                for (int i = 0; i < arr.size() && out.size() < 2; i++) {
                    JsonNode n = arr.get(i);
                    if (n.isTextual()) out.add(n.asText());
                    else if (n.isObject()) {
                        if (n.has("name")) out.add(n.get("name").asText());
                        else if (n.has("fileName")) out.add(n.get("fileName").asText());
                        else if (n.has("filename")) out.add(n.get("filename").asText());
                    }
                }
            }
            return out;
        } catch (IOException e) {
            log.warn("payload JSON 파싱 실패: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * car_seq의 네번째 자리수(왼쪽 기준): 0 -> "10", 그 외 "0"+digit
     */
    private String folderByFourthDigit(Long carSeq) {
        String s = String.valueOf(carSeq);
        if (s.length() < 4) throw new IllegalArgumentException("car_seq too short: " + carSeq);
        char d = s.charAt(3);
        return (d == '0') ? "10" : ("0" + d);
    }

    /**
     * car_seq의 앞 4자리
     */
    private String firstFourDigits(Long carSeq) {
        String s = String.valueOf(carSeq);
        if (s.length() < 4) throw new IllegalArgumentException("car_seq too short: " + carSeq);
        return s.substring(0, 4);
    }

    private String buildUrl(String folderTwoDigits, String firstFour, Long carSeq, String fileName) {
        return IMG_BASE + "img" + folderTwoDigits + "/img" + firstFour + "/" + fileName + "?width=720";
    }

    private boolean download(HttpClient http, String url, Path out) {
        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                Files.write(out, resp.body());
                return true;
            } else {
                log.warn("[HTTP {}] {}", resp.statusCode(), url);
                return false;
            }
        } catch (Exception e) {
            log.warn("download fail: {} -> {}", url, e.getMessage());
            return false;
        }
    }
}
