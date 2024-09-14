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

    @Column(name = "status", columnDefinition = "enum('PENDING','SUCCESS','FAILED','CANCELLED','DISPUTE') default 'CANCELLED'")
    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @Column(name = "message")
    private String message;

    @Column(name = "resultCode")
    private String resultCode;

    @Column(name = "ref", nullable = false)
    private String ref;

    @Column(name = "ip_address", nullable = false)
    private String ipAddress;

    @Column(name = "pay_url", columnDefinition = "TEXT")
    private String payUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "pay_type")
    private PayType payType;

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

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }

    public PayType getPayType() {
        return payType;
    }

    public void setPayType(PayType payType) {
        this.payType = payType;
    }
}
