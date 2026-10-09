package com.example.webstore.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "customeraddresses")
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "street_address", nullable = false)
    private String streetAddress;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(nullable = false, length = 100)
    private String city;

    @Column(length = 100)
    private String country;

    public CustomerAddress() {
    }

    public Integer getId() { return id; }

    public Customer getCustomer() { return customer; }

    public String getStreetAddress() { return streetAddress; }

    public String getPostalCode() { return postalCode; }

    public String getCity() { return city; }

    public String getCountry() { return country; }
}