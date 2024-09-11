package com.demo.ecomvnpaydemo.repositories;

import com.demo.ecomvnpaydemo.domain.models.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    List<Transaction> findAllByOrderId(UUID orderId);
}
