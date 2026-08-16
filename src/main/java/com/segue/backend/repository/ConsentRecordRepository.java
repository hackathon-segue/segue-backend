package com.segue.backend.repository;

import com.segue.backend.domain.ConsentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, Long> {

    Optional<ConsentRecord> findByCustomerId(Long customerId);
}
