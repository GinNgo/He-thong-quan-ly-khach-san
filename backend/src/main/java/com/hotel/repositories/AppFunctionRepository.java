package com.hotel.repositories;

import com.hotel.entities.AppFunction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppFunctionRepository extends JpaRepository<AppFunction, Long> {
    AppFunction findByCode(String code);
}
