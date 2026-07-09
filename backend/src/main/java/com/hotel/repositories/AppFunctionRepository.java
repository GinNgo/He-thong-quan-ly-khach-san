package com.hotel.repositories;

import com.hotel.entities.AppFunction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface AppFunctionRepository extends JpaRepository<AppFunction, Long> {
    AppFunction findByCode(String code);
    List<AppFunction> findByModuleId(Long moduleId);
    List<AppFunction> findByModuleIdOrderBySortOrderAsc(Long moduleId);
}
