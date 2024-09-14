package com.demo.ecomvnpaydemo.rest.schema;

public class CreateOrderBody {

    private float amount;

    private String content;

    public CreateOrderBody() {
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
