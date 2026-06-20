package com.smartbillpro.backend.repository;

import com.smartbillpro.backend.entity.Customer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByMobileNumber(String mobileNumber);

    @Query("SELECT c FROM Customer c WHERE c.isActive = true AND " +
           "(LOWER(c.customerName) LIKE LOWER(CONCAT('%', :keyword, '%')) OR c.mobileNumber LIKE CONCAT('%', :keyword, '%'))")
    Page<Customer> search(@Param("keyword") String keyword, Pageable pageable);

    @Query("SELECT COUNT(c) FROM Customer c WHERE c.isActive = true")
    long countActive();
}
