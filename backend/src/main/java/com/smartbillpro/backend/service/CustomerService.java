package com.smartbillpro.backend.service;

import com.smartbillpro.backend.dto.customer.CustomerRequest;
import com.smartbillpro.backend.dto.customer.CustomerResponse;
import com.smartbillpro.backend.entity.Customer;
import com.smartbillpro.backend.exception.ResourceNotFoundException;
import com.smartbillpro.backend.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customerRepository;

    @Transactional
    public CustomerResponse createCustomer(CustomerRequest request, Long createdByUserId) {
        Customer customer = Customer.builder()
                .customerName(request.getCustomerName())
                .mobileNumber(request.getMobileNumber())
                .email(request.getEmail())
                .address(request.getAddress())
                .gstNumber(request.getGstNumber())
                .createdBy(createdByUserId)
                .build();
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public CustomerResponse updateCustomer(Long id, CustomerRequest request) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        customer.setCustomerName(request.getCustomerName());
        customer.setMobileNumber(request.getMobileNumber());
        customer.setEmail(request.getEmail());
        customer.setAddress(request.getAddress());
        customer.setGstNumber(request.getGstNumber());
        return toResponse(customerRepository.save(customer));
    }

    @Transactional
    public void deleteCustomer(Long id) {
        Customer customer = customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id));
        customer.setIsActive(false);
        customerRepository.save(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(Long id) {
        return toResponse(customerRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Customer", "id", id)));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> searchCustomers(String keyword, Pageable pageable) {
        Page<Customer> page = (keyword == null || keyword.isBlank())
                ? customerRepository.findAll(pageable)
                : customerRepository.search(keyword, pageable);
        return page.map(this::toResponse);
    }

    private CustomerResponse toResponse(Customer c) {
        return CustomerResponse.builder()
                .id(c.getId())
                .customerName(c.getCustomerName())
                .mobileNumber(c.getMobileNumber())
                .email(c.getEmail())
                .address(c.getAddress())
                .gstNumber(c.getGstNumber())
                .outstandingBalance(c.getOutstandingBalance())
                .active(Boolean.TRUE.equals(c.getIsActive()))
                .build();
    }
}
