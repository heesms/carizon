package com.carizon.api.service;

import com.carizon.api.dto.CarListItemDto;
import com.carizon.api.entity.CarMaster;
import com.carizon.api.entity.PlatformCar;
import com.carizon.api.repository.CarMasterRepository;
import com.carizon.api.repository.PlatformCarRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CarQueryService {

    private final CarMasterRepository carMasterRepository;
    private final PlatformCarRepository platformCarRepository;

    /**
     * Search cars with filters and pagination.
     * TODO: Future integration with Meilisearch for full-text search
     */
    public Page<CarListItemDto> search(String maker, String modelGroup, String model, 
                                       String trim, String grade, String query, 
                                       int page, int size, String sort) {
        
        Specification<CarMaster> spec = (root, criteriaQuery, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            
            if (maker != null && !maker.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("makerCode"), maker));
            }
            if (modelGroup != null && !modelGroup.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("modelGroupCode"), modelGroup));
            }
            if (model != null && !model.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("modelCode"), model));
            }
            if (trim != null && !trim.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("trimCode"), trim));
            }
            if (grade != null && !grade.isEmpty()) {
                predicates.add(criteriaBuilder.equal(root.get("gradeCode"), grade));
            }
            if (query != null && !query.isEmpty()) {
                // Simple text search on model name for now
                predicates.add(criteriaBuilder.or(
                    criteriaBuilder.like(root.get("modelName"), "%" + query + "%"),
                    criteriaBuilder.like(root.get("makerName"), "%" + query + "%"),
                    criteriaBuilder.like(root.get("carNo"), "%" + query + "%")
                ));
            }
            
            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };

        Sort sortObj = Sort.by(Sort.Direction.DESC, "updatedAt");
        if (sort != null) {
            String[] parts = sort.split(",");
            if (parts.length == 2) {
                sortObj = Sort.by(
                    "desc".equalsIgnoreCase(parts[1]) ? Sort.Direction.DESC : Sort.Direction.ASC,
                    parts[0]
                );
            }
        }

        Pageable pageable = PageRequest.of(page, size, sortObj);
        Page<CarMaster> carPage = carMasterRepository.findAll(spec, pageable);

        // Get representative prices from platform_car
        List<Long> carIds = carPage.getContent().stream()
            .map(CarMaster::getCarId)
            .collect(Collectors.toList());
        
        Map<Long, Integer> representativePrices = platformCarRepository.findAll().stream()
            .filter(pc -> carIds.contains(pc.getCarId()) && pc.getPrice() != null)
            .collect(Collectors.groupingBy(
                PlatformCar::getCarId,
                Collectors.collectingAndThen(
                    Collectors.averagingInt(PlatformCar::getPrice),
                    Double::intValue
                )
            ));

        List<CarListItemDto> items = carPage.getContent().stream()
            .map(car -> CarListItemDto.builder()
                .carId(car.getCarId())
                .carNo(car.getCarNo())
                .makerName(car.getMakerName())
                .modelGroupName(car.getModelGroupName())
                .modelName(car.getModelName())
                .trimName(car.getTrimName())
                .year(car.getYear())
                .mileage(car.getMileage())
                .color(car.getColor())
                .transmission(car.getTransmission())
                .fuel(car.getFuel())
                .region(car.getRegion())
                .advStatus(car.getAdvStatus())
                .representativePrice(representativePrices.get(car.getCarId()))
                .representativeImageUrl("https://cdn.jsdelivr.net/gh/twitter/twemoji@latest/assets/72x72/1f697.png")
                .build())
            .collect(Collectors.toList());

        return new PageImpl<>(items, pageable, carPage.getTotalElements());
    }
}
