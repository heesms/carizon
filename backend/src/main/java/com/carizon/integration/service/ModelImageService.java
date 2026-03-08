package com.carizon.integration.service;

import com.carizon.integration.dto.CarPick;
import com.carizon.integration.mapper.CarizonMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.BiFunction;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelImageService {

    private final CarizonMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String IMG_BASE = "https://img.kbchachacha.com/IMG/carimg/l/";
    private static final String STATIC_MODEL_IMG_BASE = "https://img.kbchachacha.com/IMG/statics/carimg/";
    private static final String STATIC_MAKER_IMG_BASE = "https://img.kbchachacha.com/IMG/statics/maker/o/";

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
        downloadFromPicks(targetDir, picks, false, (pick, index) -> pick.getCarCode() + (index == 0 ? "_first.jpg" : "_second.jpg"), false);
    }

    public void downloadRepresentativeImagesForModelCodes(Path targetDir, List<String> modelCodes, Integer limit, boolean skipExisting) throws Exception {
        if (targetDir == null) targetDir = Path.of("C:\\carizon\\model_image");
        Files.createDirectories(targetDir);
        if (modelCodes == null || modelCodes.isEmpty()) {
            return;
        }
        List<String> candidates = applyLimit(modelCodes, limit);
        downloadByCodes(
                targetDir,
                candidates,
                skipExisting,
                modelCode -> STATIC_MODEL_IMG_BASE + modelCode + ".png"
        );
    }

    public void downloadRepresentativeImagesForModelCodes(Path targetDir, List<String> modelCodes, Integer limit) throws Exception {
        downloadRepresentativeImagesForModelCodes(targetDir, modelCodes, limit, true);
    }

    public void downloadRepresentativeImagesForMakers(Path targetDir, List<String> makerCodes, Integer limit, boolean skipExisting) throws Exception {
        if (targetDir == null) targetDir = Path.of("C:\\carizon\\model_image");
        Files.createDirectories(targetDir);
        if (makerCodes == null || makerCodes.isEmpty()) {
            return;
        }
        List<String> candidates = applyLimit(makerCodes, limit);
        // 저장 파일명: maker{code}.png (프론트 /image/maker/maker{code}.png 경로와 일치)
        downloadByCodes(
                targetDir,
                candidates,
                skipExisting,
                makerCode -> STATIC_MAKER_IMG_BASE + "maker" + makerCode + ".png",
                makerCode -> "maker" + makerCode + ".png"
        );
    }

    private void downloadByCodes(Path targetDir, List<String> codes, boolean skipExisting, Function<String, String> urlResolver) throws Exception {
        downloadByCodes(targetDir, codes, skipExisting, urlResolver, code -> code + ".png");
    }

    private void downloadByCodes(Path targetDir, List<String> codes, boolean skipExisting, Function<String, String> urlResolver, Function<String, String> fileNameResolver) throws Exception {
        if (codes == null || codes.isEmpty()) {
            return;
        }
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        int ok = 0, fail = 0;
        for (String code : codes) {
            if (code == null || code.isBlank()) {
                continue;
            }
            String url = urlResolver.apply(code);
            Path out = targetDir.resolve(fileNameResolver.apply(code));
            boolean downloaded = download(http, url, out, skipExisting);
            log.info("{} {} <- {}", downloaded ? "[OK ]" : "[ERR]", out, url);
            ok += downloaded ? 1 : 0;
            fail += downloaded ? 0 : 1;
        }
        log.info("done: ok {} fail {}", ok, fail);
    }

    private List<String> applyLimit(List<String> codes, Integer limit) {
        if (codes == null) return List.of();
        if (limit == null || limit <= 0 || codes.size() <= limit) {
            return codes;
        }
        return codes.subList(0, limit);
    }

    private void downloadFromPicks(Path targetDir, List<CarPick> picks, boolean skipExisting, BiFunction<CarPick, Integer, String> fileNameSelector, boolean onlyFirstImage) throws Exception {
        log.info("representative count: {}", picks.size());

        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

        int ok = 0, fail = 0;
        for (CarPick pick : picks) {
            List<String> names = firstTwoFilenames(pick.getPayload());
            if (names.isEmpty()) {
                log.warn("[SKIP] fileNameArray empty: model={}, carSeq={}", pick.getModelCode(), pick.getCarSeq());
                fail++;
                continue;
            }
            String folderTwo = folderByFourthDigit(pick.getCarSeq()); // imgNN/
            String firstFour = firstFourDigits(pick.getCarSeq());     // img####/

            if (names.size() >= 1) {
                String url1 = buildUrl(folderTwo, firstFour, pick.getCarSeq(), names.get(0));
                Path out1 = targetDir.resolve(fileNameSelector.apply(pick, 0));
                boolean b1 = download(http, url1, out1, skipExisting);
                log.info("{} {} <- {}", b1 ? "[OK ]" : "[ERR]", out1, url1);
                ok += b1 ? 1 : 0;
                fail += b1 ? 0 : 1;
            }
            if (!onlyFirstImage && names.size() >= 2) {
                String url2 = buildUrl(folderTwo, firstFour, pick.getCarSeq(), names.get(1));
                Path out2 = targetDir.resolve(fileNameSelector.apply(pick, 1));
                boolean b2 = download(http, url2, out2, skipExisting);
                log.info("{} {} <- {}", b2 ? "[OK ]" : "[ERR]", out2, url2);
                ok += b2 ? 1 : 0;
                fail += b2 ? 0 : 1;
            }
        }
        log.info("done: ok {} fail {}", ok, fail);
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
            log.warn("payload JSON parse failed: {}", e.getMessage());
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

    private static final int MAX_IMAGE_DIM = 256;

    private boolean download(HttpClient http, String url, Path out, boolean skipExisting) {
        try {
            if (skipExisting && Files.exists(out) && Files.size(out) > 0) {
                log.info("[SKIP] {} exists", out);
                return true;
            }
        } catch (Exception e) {
            log.warn("file check fail: {} -> {}", out, e.getMessage());
        }

        try {
            HttpRequest req = HttpRequest.newBuilder().uri(URI.create(url)).timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<byte[]> resp = http.send(req, HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().length > 0) {
                byte[] data = resizeIfNeeded(resp.body(), MAX_IMAGE_DIM);
                Files.write(out, data);
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

    /** 이미지 최대 크기를 maxDim px로 리사이즈 (비율 유지). 이미 작으면 그대로 반환. */
    private byte[] resizeIfNeeded(byte[] original, int maxDim) {
        try {
            BufferedImage img = ImageIO.read(new ByteArrayInputStream(original));
            if (img == null) return original;
            int w = img.getWidth(), h = img.getHeight();
            if (Math.max(w, h) <= maxDim) return original;

            double ratio = (double) maxDim / Math.max(w, h);
            int nw = (int) (w * ratio), nh = (int) (h * ratio);

            BufferedImage resized = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(img.getScaledInstance(nw, nh, Image.SCALE_SMOOTH), 0, 0, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(resized, "PNG", baos);
            byte[] resizedBytes = baos.toByteArray();
            log.debug("resized {}x{} → {}x{}: {}KB → {}KB", w, h, nw, nh,
                    original.length / 1024, resizedBytes.length / 1024);
            return resizedBytes;
        } catch (Exception e) {
            log.warn("resize failed, using original: {}", e.getMessage());
            return original;
        }
    }

    public Set<String> filterMissingModelCodes(Collection<String> candidateModelCodes, Path targetDir) throws IOException {
        Set<String> missing = new LinkedHashSet<>();
        if (candidateModelCodes == null) return missing;
        for (String modelCode : new LinkedHashSet<>(candidateModelCodes)) {
            if (modelCode == null) continue;
            Path first = targetDir.resolve(modelCode + ".png");
            if (!Files.exists(first) || Files.size(first) == 0) {
                missing.add(modelCode);
            }
        }
        return missing;
    }

    public Set<String> filterMissingMakerCodes(Collection<String> candidateMakerCodes, Path targetDir) throws IOException {
        Set<String> missing = new LinkedHashSet<>();
        if (candidateMakerCodes == null) return missing;
        for (String makerCode : new LinkedHashSet<>(candidateMakerCodes)) {
            if (makerCode == null) continue;
            Path file = targetDir.resolve("maker" + makerCode + ".png");
            if (!Files.exists(file) || Files.size(file) == 0) {
                missing.add(makerCode);
            }
        }
        return missing;
    }
}
