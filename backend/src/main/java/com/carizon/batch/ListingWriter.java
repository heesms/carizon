package com.carizon.batch;

import com.carizon.repository.ListingMapper;
import com.carizon.repository.VehicleMapper;
import com.carizon.repository.model.ListingSource;
import com.carizon.repository.model.PriceHistory;
import com.carizon.repository.model.Vehicle;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ListingWriter {

    private final VehicleMapper vehicleMapper;
    private final ListingMapper listingMapper;

    public void upsertFromChachacha(Map<String, Object> item) {
        String carNo = str(item.get("carNo"));
        if (carNo == null || carNo.isBlank()) return;

        var vehicle = new Vehicle();
        vehicle.setCarNumber(carNo);
        vehicle.setModel(buildModel(item));
        vehicle.setYear(parseYear(item));
        vehicle.setMileageKm(intOrNull(item.get("km")));
        vehicle.setFuel(str(item.get("gasName")));
        vehicle.setTransmission(null); // 없음
        vehicle.setColor(str(item.get("colorCodeName")));

        vehicleMapper.upsertVehicle(vehicle); // ON DUP KEY UPDATE

        Integer vehicleId = Optional.ofNullable(vehicleMapper.selectIdByCarNumber(carNo))
                .orElseThrow();

        var listing = new ListingSource();
        listing.setVehicleId(vehicleId);
        listing.setPlatform("차차차");
        listing.setDetailUrl(buildDetailUrl(item));
        listing.setPrice(intOrNull(item.get("sellAmt")));
        listingMapper.upsertListingSource(listing);

        var ph = new PriceHistory();
        ph.setVehicleId(vehicleId);
        ph.setPlatform("차차차");
        ph.setPrice(listing.getPrice());
        listingMapper.insertPriceHistory(ph);
    }

    private static String str(Object v){ return v==null?null:String.valueOf(v).trim(); }
    private static Integer intOrNull(Object v){
        try { return v==null?null:Integer.valueOf(String.valueOf(v)); } catch (Exception e){ return null; }
    }
    private static String buildModel(Map<String,Object> m){
        String[] k = {"makerName","className","carName","modelName","gradeName"};
        StringBuilder sb = new StringBuilder();
        for (String key: k){ String s=str(m.get(key)); if(s!=null&&!s.isEmpty()){ if(sb.length()>0) sb.append(' '); sb.append(s);} }
        return sb.toString();
    }
    private static Integer parseYear(Map<String,Object> m){
        String yymm = str(m.get("yymm"));
        if (yymm!=null && yymm.length()>=4) try { return Integer.parseInt(yymm.substring(0,4)); } catch(Exception ignored){}
        return null;
    }
    private static String buildDetailUrl(Map<String,Object> m){
        Object carSeq = m.get("carSeq");
        if (carSeq==null) return "";
        return "https://m.kbchachacha.com/public/detail/main.kbc?carseq=" + carSeq;
    }
}
