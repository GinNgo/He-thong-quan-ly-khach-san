package com.hotel.repositories;

import com.hotel.entities.AppModule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AppModuleRepository extends JpaRepository<AppModule, Long> {
    AppModule findByCode(String code);
}
