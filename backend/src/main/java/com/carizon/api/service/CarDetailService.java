package com.carizon.api.service;

import com.carizon.api.dto.CarDetailDto;
import com.carizon.api.dto.PlatformListingDto;
import com.carizon.api.entity.CarMaster;
import com.carizon.api.entity.PlatformCar;
import com.carizon.api.repository.CarMasterRepository;
import com.carizon.api.repository.PlatformCarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarDetailService {

    private final CarMasterRepository carMasterRepository;
    private final PlatformCarRepository platformCarRepository;

    /**
     * Get car detail with aggregated platform entries
     */
    public CarDetailDto getDetail(Long carId) {
        CarMaster car = carMasterRepository.findById(carId)
            .orElseThrow(() -> new RuntimeException("Car not found: " + carId));

        List<PlatformCar> platformCars = platformCarRepository.findByCarId(carId);

        List<PlatformListingDto> listings = platformCars.stream()
            .map(pc -> PlatformListingDto.builder()
                .platformCarId(pc.getPlatformCarId())
                .platformName(pc.getPlatformName())
                .platformCarKey(pc.getPlatformCarKey())
                .price(pc.getPrice())
                .km(pc.getKm())
                .status(pc.getStatus())
                .mUrl(pc.getMUrl())
                .pcUrl(pc.getPcUrl())
                .firstAdDay(pc.getFirstAdDay())
                .lastSeenDate(pc.getLastSeenDate())
                .build())
            .collect(Collectors.toList());

        return CarDetailDto.builder()
            .carId(car.getCarId())
            .carNo(car.getCarNo())
            .makerCode(car.getMakerCode())
            .modelGroupCode(car.getModelGroupCode())
            .modelCode(car.getModelCode())
            .trimCode(car.getTrimCode())
            .gradeCode(car.getGradeCode())
            .makerName(car.getMakerName())
            .modelGroupName(car.getModelGroupName())
            .modelName(car.getModelName())
            .trimName(car.getTrimName())
            .year(car.getYear())
            .mileage(car.getMileage())
            .color(car.getColor())
            .transmission(car.getTransmission())
            .fuel(car.getFuel())
            .displacement(car.getDisplacement())
            .bodyType(car.getBodyType())
            .region(car.getRegion())
            .advStatus(car.getAdvStatus())
            .representativeImageUrl("https://cdn.jsdelivr.net/gh/twitter/twemoji@latest/assets/72x72/1f697.png")
            .platformListings(listings)
            .build();
    }
}
