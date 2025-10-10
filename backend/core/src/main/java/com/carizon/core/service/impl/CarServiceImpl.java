package com.carizon.core.service.impl;

import com.carizon.core.dto.CarDetailDto;
import com.carizon.core.dto.CarSummaryDto;
import com.carizon.core.domain.car.CarMaster;
import com.carizon.core.domain.car.PlatformCar;
import com.carizon.core.repository.CarMasterRepository;
import com.carizon.core.repository.CarPriceHistoryRepository;
import com.carizon.core.repository.PlatformCarRepository;
import com.carizon.core.service.CarService;
import com.carizon.core.service.search.SearchProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class CarServiceImpl implements CarService {

    private final SearchProvider searchProvider;
    private final CarMasterRepository carMasterRepository;
    private final PlatformCarRepository platformCarRepository;
    private final CarPriceHistoryRepository carPriceHistoryRepository;

    public CarServiceImpl(SearchProvider searchProvider,
                          CarMasterRepository carMasterRepository,
                          PlatformCarRepository platformCarRepository,
                          CarPriceHistoryRepository carPriceHistoryRepository) {
        this.searchProvider = searchProvider;
        this.carMasterRepository = carMasterRepository;
        this.platformCarRepository = platformCarRepository;
        this.carPriceHistoryRepository = carPriceHistoryRepository;
    }

    @Override
    public Page<CarSummaryDto> searchCars(String query, CarSearchFilters filters, Pageable pageable) {
        return searchProvider.search(query, filters, pageable);
    }

    @Override
    public Optional<CarDetailDto> getCarDetail(Long id) {
        return carMasterRepository.findById(id).map(this::mapToDetail);
    }

    @Override
    public CarDetailDto getCarHistory(Long id) {
        return getCarDetail(id).orElseThrow();
    }

    private CarDetailDto mapToDetail(CarMaster car) {
        List<PlatformCar> offers = platformCarRepository.findByCarId(String.valueOf(car.getId()));
        List<CarDetailDto.PlatformOfferDto> offerDtos = offers.stream()
            .map(offer -> new CarDetailDto.PlatformOfferDto(
                offer.getPlatformName(),
                offer.getPrice(),
                offer.getMileage(),
                offer.getPcUrl()
            ))
            .toList();

        List<CarDetailDto.PriceSnapshotDto> history = offers.stream()
            .flatMap(offer -> carPriceHistoryRepository.findByPlatformCarIdOrderByCheckedAtAsc(offer.getId()).stream())
            .map(snapshot -> new CarDetailDto.PriceSnapshotDto(
                snapshot.getCheckedAt() != null ? snapshot.getCheckedAt().toString() : null,
                snapshot.getPrice() != null ? snapshot.getPrice().intValue() : null
            ))
            .toList();

        Integer bestPrice = offers.stream()
            .map(PlatformCar::getPrice)
            .filter(price -> price != null && price > 0)
            .min(Comparator.naturalOrder())
            .orElse(null);

        return new CarDetailDto(
            car.getId(),
            car.getCarNo(),
            car.getYear(),
            car.getMileage(),
            bestPrice,
            car.getFuel(),
            car.getTransmission(),
            car.getBodyType(),
            car.getRegion(),
            offerDtos,
            history
        );
    }
}
