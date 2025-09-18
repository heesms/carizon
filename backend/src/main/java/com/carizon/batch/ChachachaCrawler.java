package com.carizon.batch;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class ChachachaCrawler {

    private static final String BASE = "https://m.kbchachacha.com/public/web/search/infinitySearch.json";
    private static final String INCLUDE_FIELDS =
            "carSeq,carNo,firstAdDay,adDay,regiSiteGbn,shopNo,danjiNo,fileNameArray,ownerYn," +
                    "makerName,className,carName,modelName,gradeName,regiDay,yymm,km,cityCodeName2," +
                    "sellAmtGbn,sellAmt,sellAmtPrev,carMasterSpecialYn,monthLeaseAmt,directYn,carAccidentNo," +
                    "warrantyYn,kbLeaseYn,orderDate,certifiedShopYn,kbCertifiedYn,hasOverThreeFileNames,diagYn," +
                    "diagGbn,lineAdYn,carAccidentNo,colorCodeName,gasName,homeserviceYn2,labsDanjiNo2,premiumYn," +
                    "t34SellGbn,t34MonthAmt,t34DiscountAmt,adState,paymentPremiumYn,contractingYn";

    private final OkHttpClient http = new OkHttpClient();
    private final ObjectMapper mapper = new ObjectMapper();
    private final ListingWriter writer; // DB 업서트 담당(아래에 추가)

    @SuppressWarnings("unchecked")
    public void runOnce() {
        List<Object> searchAfter = null;
        int pageSize = 10000;
        int fetched = 0;

        while (true) {
            try {
                var urlBuilder = new StringBuilder(BASE)
                        .append("?sort=").append("-orderDate")
                        .append("&page=1")
                        .append("&pageSize=").append(pageSize)
                        .append("&includeFields=").append(INCLUDE_FIELDS)
                        .append("&displaySoldoutYn=Y")
                        .append("&v=").append(System.currentTimeMillis());

                if (searchAfter != null) {
                    for (Object v : searchAfter) {
                        urlBuilder.append("&searchAfter=").append(v);
                    }
                }

                Request req = new Request.Builder()
                        .url(urlBuilder.toString())
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0) Chrome/120 Safari/537.36")
                        .build();

                try (Response resp = http.newCall(req).execute()) {
                    if (!resp.isSuccessful()) throw new RuntimeException("HTTP " + resp.code());
                    var data = mapper.readValue(Objects.requireNonNull(resp.body()).bytes(),
                            new TypeReference<Map<String, Object>>() {});
                    Map<String, Object> result = (Map<String, Object>) data.getOrDefault("result", Map.of());
                    List<Map<String, Object>> list = (List<Map<String, Object>>) result.getOrDefault("list", List.of());
                    if (list.isEmpty()) break;

                    // DB 업서트
                    for (Map<String, Object> item : list) {
                        writer.upsertFromChachacha(item);
                    }

                    fetched += list.size();
                    Object nextSa = result.get("searchAfter");
                    if (!(nextSa instanceof List nextList) || nextList.isEmpty()) break;

                    searchAfter = nextList;
                    if (list.size() < pageSize) break;

                    Thread.sleep(800);
                }
            } catch (Exception e) {
                // 실패하면 루프 종료(간단 MVP). 필요하면 재시도 로직 추가
                e.printStackTrace();
                break;
            }
        }
        System.out.println("Crawl done. fetched=" + fetched);
    }
}
