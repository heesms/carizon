package com.carizon.core.service.search.impl;

import com.carizon.core.dto.CarSummaryDto;
import com.carizon.core.domain.car.CarMaster;
import com.carizon.core.domain.car.PlatformCar;
import com.carizon.core.repository.CarMasterRepository;
import com.carizon.core.repository.PlatformCarRepository;
import com.carizon.core.service.CarService;
import com.carizon.core.service.search.SearchProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component("dbSearchProvider")
public class DbSearchProvider implements SearchProvider {

    private final CarMasterRepository carMasterRepository;
    private final PlatformCarRepository platformCarRepository;

    public DbSearchProvider(CarMasterRepository carMasterRepository,
                            PlatformCarRepository platformCarRepository) {
        this.carMasterRepository = carMasterRepository;
        this.platformCarRepository = platformCarRepository;
    }

    @Override
    public Page<CarSummaryDto> search(String query, CarService.CarSearchFilters filters, Pageable pageable) {
        Page<CarMaster> page = carMasterRepository.findAll(pageable);
        List<CarSummaryDto> summaries = page.getContent().stream()
            .map(car -> {
                List<PlatformCar> offers = platformCarRepository.findByCarId(String.valueOf(car.getId()));
                Integer bestPrice = offers.stream()
                    .map(PlatformCar::getPrice)
                    .filter(price -> price != null && price > 0)
                    .min(Comparator.naturalOrder())
                    .orElse(null);
                List<String> platforms = offers.stream()
                    .map(PlatformCar::getPlatformName)
                    .toList();
                return new CarSummaryDto(
                    car.getId(),
                    car.getCarNo(),
                    car.getYear(),
                    car.getMileage(),
                    bestPrice,
                    car.getFuel(),
                    car.getTransmission(),
                    platforms
                );
            })
            .toList();
        return new PageImpl<>(summaries, pageable, page.getTotalElements());
    }
}
