package com.carizon.core.domain.service;

import com.carizon.core.domain.entity.MyCarMaster;
import com.carizon.core.domain.entity.PlatformListing;
import com.carizon.core.domain.repo.MyCarMasterRepo;
import com.carizon.core.domain.repo.PlatformListingRepo;
import com.carizon.core.domain.web.dto.CarDetailDto;
import com.carizon.core.domain.web.dto.CarListItemDto;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class CarQueryService {

    private final MyCarMasterRepo masterRepo;
    private final PlatformListingRepo listingRepo;
    private final JdbcTemplate jdbc;

    public Page<CarListItemDto> search(
            String maker, String group, String model, String trim, String grade, String q,
            int page, int size, String sort) {

        Pageable pageable = PageRequest.of(page, size, Sort.by("lastSeenDate").descending());
        Page<MyCarMaster> masters = masterRepo.search(maker, group, model, trim, grade, q, pageable);

        Map<String, Map<String,Object>> priceSummary = new HashMap<>();
        if (!masters.isEmpty()) {
            String inKeys = String.join(",", Collections.nCopies(masters.getContent().size(), "?"));
            String sql = "SELECT my_car_key, min_price, max_price, cheapest_platform " +
                    "FROM v_my_car_price_summary WHERE my_car_key IN (" + inKeys + ")";
            List<Object> params = new ArrayList<>();
            masters.forEach(m -> params.add(m.getMyCarKey()));
            jdbc.query(sql, params.toArray(), rs -> {
                Map<String,Object> row = new HashMap<>();
                row.put("min_price", rs.getInt("min_price"));
                row.put("max_price", rs.getInt("max_price"));
                row.put("cheapest_platform", rs.getString("cheapest_platform"));
                priceSummary.put(rs.getString("my_car_key"), row);
            });
        }

        return masters.map(m -> {
            Map<String,Object> ps = priceSummary.getOrDefault(m.getMyCarKey(), Collections.emptyMap());
            return CarListItemDto.builder()
                    .myCarKey(m.getMyCarKey())
                    .numberPlate(m.getNumberPlate())
                    .makerName(m.getMakerName())
                    .modelGroupName(m.getModelGroupName())
                    .modelName(m.getModelName())
                    .trimName(m.getTrimName())
                    .gradeName(m.getGradeName())
                    .advertStatus(m.getAdvertStatus().name())
                    .minPrice((Integer) ps.get("min_price"))
                    .maxPrice((Integer) ps.get("max_price"))
                    .cheapestPlatform((String) ps.get("cheapest_platform"))
                    .build();
        });
    }

    public CarDetailDto detail(String myCarKey) {
        MyCarMaster m = masterRepo.findById(myCarKey).orElseThrow();
        List<PlatformListing> actives =
                listingRepo.findByMyCarKeyAndStatusNowOrderByPriceNowAsc(myCarKey, PlatformListing.Status.ACTIVE);

        Integer min = null, max = null;
        if (!actives.isEmpty()) {
            min = actives.get(0).getPriceNow();
            max = actives.stream().filter(p -> p.getPriceNow()!=null)
                    .map(PlatformListing::getPriceNow).max(Integer::compareTo).orElse(null);
        }

        List<CarDetailDto.PlatformPrice> platforms = new ArrayList<>();
        for (PlatformListing pl : actives) {
            String url = toPlatformUrl(pl);
            platforms.add(CarDetailDto.PlatformPrice.builder()
                    .source(pl.getSource().name())
                    .priceNow(pl.getPriceNow())
                    .detailUrl(url)
                    .build());
        }

        return CarDetailDto.builder()
                .myCarKey(m.getMyCarKey())
                .numberPlate(m.getNumberPlate())
                .makerName(m.getMakerName())
                .modelGroupName(m.getModelGroupName())
                .modelName(m.getModelName())
                .trimName(m.getTrimName())
                .gradeName(m.getGradeName())
                .advertStatus(m.getAdvertStatus().name())
                .minPrice(min)
                .maxPrice(max)
                .platforms(platforms)
                .build();
    }

    private String toPlatformUrl(PlatformListing pl) {
        switch (pl.getSource()) {
            case KCAR:
                return "https://m.kcar.com/bc/detail/carInfoDtl?i_sCarCd=" + pl.getSourceKey();
            case CHACHACHA:
                return "https://www.kbchachacha.com/public/car/detail.kbc?carSeq=" + pl.getSourceKey();
            case ENCAR:
                return "https://fem.encar.com/cars/detail/" + pl.getSourceKey();
            case CHUTCHA:
                return "https://web.chutcha.net/bmc/detail/" + pl.getSourceKey();
            default:
                return "#";
        }
    }
}
