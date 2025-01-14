package com.demo.ecomvnpaydemo.repositories;

import com.demo.ecomvnpaydemo.domain.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {
    Order findByRef(String ref);
}
