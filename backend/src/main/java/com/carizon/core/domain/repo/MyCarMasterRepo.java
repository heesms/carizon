package com.carizon.core.domain.repo;

import com.carizon.core.domain.entity.MyCarMaster;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.stereotype.Repository;

@Repository
public interface MyCarMasterRepo extends JpaRepository<MyCarMaster, String> {

    @Query("""
        select m from MyCarMaster m
        where (:maker is null or m.makerName = :maker)
          and (:group is null or m.modelGroupName = :group)
          and (:model is null or m.modelName = :model)
          and (:trim is null or m.trimName = :trim)
          and (:grade is null or m.gradeName = :grade)
          and (:q is null or
               lower(concat(m.makerName,' ',m.modelGroupName,' ',m.modelName,' ',
                            coalesce(m.trimName,''),' ',coalesce(m.gradeName,''),' ',m.numberPlate))
               like lower(concat('%', :q, '%')))
    """)
    Page<MyCarMaster> search(String maker, String group, String model, String trim, String grade, String q, Pageable pageable);
}
