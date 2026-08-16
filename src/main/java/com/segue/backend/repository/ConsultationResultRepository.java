package com.segue.backend.repository;

import com.segue.backend.domain.ConsultationResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConsultationResultRepository extends JpaRepository<ConsultationResult, Long> {

    List<ConsultationResult> findByCustomerIdOrderByConsultedAtDesc(Long customerId);
}
