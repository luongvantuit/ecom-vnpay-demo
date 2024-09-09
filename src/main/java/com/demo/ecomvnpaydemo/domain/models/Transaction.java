package com.demo.ecomvnpaydemo.domain.models;

import jakarta.persistence.*;

@Entity
@Table(name = "transactions")
public class Transaction extends Base {

    @Column(name = "amount", nullable = false)
    private float amount;

    @Column(name = "pay_method", nullable = false)
    @Enumerated(EnumType.STRING)
    private PayMethod payMethod;

    @ManyToOne(optional = false)
    @JoinColumn(name = "order_id", referencedColumnName = "id")
    private Order order;

    @Column(name = "status", columnDefinition = "enum('SUCCESS','FAILED','CANCELLED','DISPUTE') default 'CANCELLED'")
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    public Transaction() {
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }

    public PayMethod getPayMethod() {
        return payMethod;
    }

    public void setPayMethod(PayMethod payMethod) {
        this.payMethod = payMethod;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }
}
