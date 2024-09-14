package com.demo.ecomvnpaydemo.rest.schema;

import com.demo.ecomvnpaydemo.domain.models.PayType;

public class PayBody {
    private PayType payType;

    public PayBody() {
    }

    public PayType getPayType() {
        return payType;
    }

    public void setPayType(PayType payType) {
        this.payType = payType;
    }
}
