package com.carizon.api.service;

import com.carizon.api.dto.CarDetailDto;
import com.carizon.api.dto.PlatformListingDto;
import com.carizon.api.mapper.CarsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CarDetailService {

    private final CarsMapper carsMapper;

    /**
     * Get car detail with aggregated platform entries using MyBatis
     */
    public CarDetailDto getDetail(Long carId) {
        CarDetailDto detail = carsMapper.getCarDetailById(carId);
        if (detail == null) {
            throw new RuntimeException("Car not found: " + carId);
        }

        List<PlatformListingDto> listings = carsMapper.getPlatformCarsByCarId(carId);
        detail.setPlatformListings(listings);
        
        // Set representative image URL
        detail.setRepresentativeImageUrl("https://cdn.jsdelivr.net/gh/twitter/twemoji@latest/assets/72x72/1f697.png");

        return detail;
    }
}
