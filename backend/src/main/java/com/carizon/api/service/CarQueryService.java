package com.carizon.api.service;

import com.carizon.api.dto.CarListItem;
import com.carizon.api.dto.CarListItemDto;
import com.carizon.api.mapper.CarsMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarQueryService {

    private final CarsMapper carsMapper;

    /**
     * Search cars with filters and pagination using MyBatis.
     */
    public Page<CarListItemDto> search(String maker, String modelGroup, String model, 
                                       String trim, String grade, String query, 
                                       int page, int size, String sort) {
        
        int offset = page * size;
        
        // Get cars from MyBatis mapper
        List<CarListItem> cars = carsMapper.searchCars(maker, modelGroup, model, trim, grade, query, offset, size);
        int total = carsMapper.countCars(maker, modelGroup, model, trim, grade, query);
        
        // Convert CarListItem to CarListItemDto for backward compatibility
        List<CarListItemDto> items = cars.stream()
            .map(car -> CarListItemDto.builder()
                .carId(car.getCarId())
                .carNo(car.getCarNo())
                .makerName(car.getMakerName())
                .modelGroupName(car.getModelGroupName())
                .modelName(car.getModelName())
                .trimName(car.getTrimName())
                .year(car.getYymm() != null && car.getYymm().length() >= 2 ? 
                      Short.valueOf("20" + car.getYymm().substring(0, 2)) : null)
                .mileage(car.getKm())
                .color(car.getColor())
                .transmission(car.getTransmission())
                .fuel(car.getFuel())
                .region(car.getRegion())
                .advStatus(car.getStatus())
                .representativePrice(car.getPrice())
                .representativeImageUrl("https://cdn.jsdelivr.net/gh/twitter/twemoji@latest/assets/72x72/1f697.png")
                .build())
            .collect(Collectors.toList());
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        return new PageImpl<>(items, pageable, total);
    }
}
