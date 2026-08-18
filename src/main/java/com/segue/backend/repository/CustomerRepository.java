package com.segue.backend.repository;

import com.segue.backend.domain.Customer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByPhoneNumber(String phoneNumber);

    /**
     * 표시용 포맷(010-1234-5678)은 그대로 두고 비교할 때만 하이픈/공백을 제거해 매칭한다.
     * 태블릿에서 CA가 직접 입력하는 값이라 01012345678, 010 1234 5678 같은 변형이 실제로 들어온다.
     */
    @Query("select c from Customer c "
            + "where function('replace', function('replace', c.phoneNumber, '-', ''), ' ', '') = :digits")
    Optional<Customer> findByPhoneNumberDigits(@Param("digits") String digits);
}
