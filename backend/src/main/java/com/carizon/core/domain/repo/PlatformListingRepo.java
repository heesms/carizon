package com.carizon.core.domain.repo;

import com.carizon.core.domain.entity.PlatformListing;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PlatformListingRepo extends JpaRepository<PlatformListing, Long> {
    List<PlatformListing> findByMyCarKeyAndStatusNowOrderByPriceNowAsc(
            String myCarKey, PlatformListing.Status status);
}
