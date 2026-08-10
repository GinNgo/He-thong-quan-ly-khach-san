package com.hotel.operations;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationalTaskHistoryRepository extends JpaRepository<OperationalTaskHistory, Long> {
}

