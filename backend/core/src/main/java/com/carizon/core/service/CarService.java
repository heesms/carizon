package com.carizon.core.service;

import com.carizon.core.dto.CarDetailDto;
import com.carizon.core.dto.CarSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;

public interface CarService {
    Page<CarSummaryDto> searchCars(String query, CarSearchFilters filters, Pageable pageable);
    Optional<CarDetailDto> getCarDetail(Long id);
    CarDetailDto getCarHistory(Long id);

    record CarSearchFilters(
            Integer priceMin,
            Integer priceMax,
            Integer kmMax,
            Integer yymmFrom,
            Integer yymmTo,
            String[] fuel,
            String[] transmission,
            String[] body,
            String[] region,
            String sort
    ) {}
}
