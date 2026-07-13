package com.hotel.repositories;

import com.hotel.entities.SoftwareContract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SoftwareContractRepository extends JpaRepository<SoftwareContract, Long> {}
