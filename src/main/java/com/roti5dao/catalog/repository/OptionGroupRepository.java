package com.roti5dao.catalog.repository;

import com.roti5dao.catalog.entity.OptionGroup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {

    @Query("select distinct g from OptionGroup g left join fetch g.items order by g.sortOrder, g.id")
    List<OptionGroup> findAllWithItems();
}
