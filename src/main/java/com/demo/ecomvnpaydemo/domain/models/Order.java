package com.demo.ecomvnpaydemo.domain.models;

import jakarta.persistence.*;

import java.util.Set;

@Entity
@Table(name = "orders")
public class Order extends Base {

    @Column(name = "amount", nullable = false)
    private float amount;

    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "ref", nullable = false)
    private String ref;

    @Column(name = "payed", nullable = false)
    private boolean payed;

    @OneToMany(mappedBy = "order")
    private Set<Transaction> transactions;

    public Order() {
    }

    public Order(float amount, String content, String ref, boolean payed) {
        this.amount = amount;
        this.content = content;
        this.ref = ref;
        this.payed = payed;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRef() {
        return ref;
    }

    public void setRef(String ref) {
        this.ref = ref;
    }

    public Set<Transaction> getTransactions() {
        return transactions;
    }

    public void setTransactions(Set<Transaction> transactions) {
        this.transactions = transactions;
    }

    public boolean isPayed() {
        return payed;
    }

    public void setPayed(boolean payed) {
        this.payed = payed;
    }
}
