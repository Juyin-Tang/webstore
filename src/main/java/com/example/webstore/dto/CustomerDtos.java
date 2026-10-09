package com.example.webstore.dto;

import com.example.webstore.entity.Customer;
import com.example.webstore.entity.CustomerAddress;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.List;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerRequest(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 255) String email,
            @Size(max = 30) String phone) {
    }

    public record CustomerResponse(Integer id, String firstName, String lastName, String email, String phone) {

        public static CustomerResponse from(Customer c) {
            return new CustomerResponse(c.getId(), c.getFirstName(), c.getLastName(), c.getEmail(), c.getPhone());
        }
    }

    public record AddressResponse(Integer id, String streetAddress, String postalCode, String city, String country) {

        public static AddressResponse from(CustomerAddress a) {
            return new AddressResponse(a.getId(), a.getStreetAddress(), a.getPostalCode(), a.getCity(), a.getCountry());
        }
    }

    public record CustomerDetailResponse(Integer id, String firstName, String lastName, String email, String phone,
                                         List<AddressResponse> addresses,
                                         int orderCount, BigDecimal totalSpent) {
    }
}