package com.example.webstore.service;

import com.example.webstore.dto.CustomerDtos.*;
import com.example.webstore.dto.OrderDtos.OrderSummaryResponse;
import com.example.webstore.dto.PageResponse;
import com.example.webstore.entity.Customer;
import com.example.webstore.exception.ApiException;
import com.example.webstore.repository.CustomerAddressRepository;
import com.example.webstore.repository.CustomerOrderRepository;
import com.example.webstore.repository.CustomerRepository;
import com.example.webstore.repository.ReportQueries;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository addressRepository;
    private final CustomerOrderRepository orderRepository;
    private final ReportQueries reportQueries;

    public CustomerService(CustomerRepository customerRepository,
                           CustomerAddressRepository addressRepository,
                           CustomerOrderRepository orderRepository,
                           ReportQueries reportQueries) {
        this.customerRepository = customerRepository;
        this.addressRepository = addressRepository;
        this.orderRepository = orderRepository;
        this.reportQueries = reportQueries;
    }

    @Transactional(readOnly = true)
    public PageResponse<CustomerResponse> findAll(int page, int size) {
        return PageResponse.from(customerRepository.findAll(PageResponse.request(page, size, Sort.by("id"))),
                CustomerResponse::from);
    }

    @Transactional(readOnly = true)
    public CustomerDetailResponse findById(Integer id) {
        Customer c = getCustomer(id);

        List<AddressResponse> addresses = addressRepository.findByCustomerIdOrderById(id).stream()
                .map(AddressResponse::from)
                .toList();

        List<OrderSummaryResponse> orders = reportQueries.customerOrders(id).stream()
                .filter(o -> !"CANCELLED".equals(o.status()))
                .toList();
        BigDecimal totalSpent = orders.stream()
                .map(OrderSummaryResponse::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new CustomerDetailResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(),
                c.getPhone(), addresses, orders.size(), totalSpent);
    }

    @Transactional(readOnly = true)
    public List<OrderSummaryResponse> findOrders(Integer id) {
        getCustomer(id);
        return reportQueries.customerOrders(id);
    }

    public CustomerResponse create(CustomerRequest request) {
        if (customerRepository.existsByEmail(request.email())) {
            throw ApiException.conflict("E-mail " + request.email() + " is already in use");
        }
        Customer customer = new Customer();
        apply(customer, request);
        return CustomerResponse.from(customerRepository.save(customer));
    }

    public CustomerResponse update(Integer id, CustomerRequest request) {
        Customer customer = getCustomer(id);
        if (customerRepository.existsByEmailAndIdNot(request.email(), id)) {
            throw ApiException.conflict("E-mail " + request.email() + " is already in use");
        }
        apply(customer, request);
        return CustomerResponse.from(customer);
    }

    public void delete(Integer id) {
        Customer customer = getCustomer(id);
        if (orderRepository.existsByCustomerId(id)) {
            throw ApiException.conflict("Customer " + id + " has orders and cannot be deleted");
        }
        customerRepository.delete(customer);
    }

    private Customer getCustomer(Integer id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> ApiException.notFound("Customer", id));
    }

    private static void apply(Customer customer, CustomerRequest request) {
        customer.setFirstName(request.firstName());
        customer.setLastName(request.lastName());
        customer.setEmail(request.email());
        customer.setPhone(request.phone());
    }
}