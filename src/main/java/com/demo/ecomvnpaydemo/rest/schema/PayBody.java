package com.demo.ecomvnpaydemo.rest.schema;

public class PayBody {

    private float amount;

    private String content;

    public PayBody() {
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
}
