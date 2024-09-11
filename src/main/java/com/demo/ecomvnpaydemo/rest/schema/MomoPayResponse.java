package com.demo.ecomvnpaydemo.rest.schema;

public class MomoPayResponse {

    private String partnerCode;

    private String payUrl;

    public MomoPayResponse() {
    }

    public String getPartnerCode() {
        return partnerCode;
    }

    public void setPartnerCode(String partnerCode) {
        this.partnerCode = partnerCode;
    }

    public String getPayUrl() {
        return payUrl;
    }

    public void setPayUrl(String payUrl) {
        this.payUrl = payUrl;
    }
}
