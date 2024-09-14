package com.demo.ecomvnpaydemo.services;

import com.demo.ecomvnpaydemo.domain.models.Transaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@Service
public class VnpayService {

    private final CryptoService cryptoService;

    @Value("${payment.vnpay.pay.url}")
    private String vnPayPayUrl;

    @Value("${payment.vnpay.pay.return-url}")
    private String vnPayReturnUrl;

    @Value("${payment.vnpay.tmn-code}")
    private String vnPayTmnCode;

    @Value("${payment.vnpay.hash-secret}")
    private String hashSecret;


    public VnpayService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public String payUrl(Transaction transaction) {

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String createDate = formatter.format(cld.getTime());

        cld.add(Calendar.MINUTE, 15);
        String expireDate = formatter.format(cld.getTime());

        String bankCode = switch (transaction.getPayType()) {
            case QR_CODE -> "VNPAYQR";
            case INTERNATIONAL_CARD -> "INTCARD";
            default -> "VNBANK";
        };

        Map<String, String> params = new HashMap<>();

        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnPayTmnCode);
        params.put("vnp_Amount", String.format("%d", (int) transaction.getAmount() * 100));
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_IpAddr", transaction.getIpAddress());
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_Locale", "vn");
        params.put("vnp_OrderInfo", transaction.getOrder().getContent());
        params.put("vnp_ReturnUrl", vnPayReturnUrl);
        params.put("vnp_TxnRef", transaction.getRef());
        params.put("vnp_OrderType", "other");
        params.put("vnp_ExpireDate", expireDate);
        params.put("vnp_BankCode", bankCode);

        List<String> keys = new ArrayList<>(params.keySet());
        Collections.sort(keys);
        StringBuilder hashData = new StringBuilder();
        StringBuilder query = new StringBuilder();

        Iterator<String> itr = keys.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = params.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
                //Build hash data
                hashData.append(fieldName);
                hashData.append('=');
                hashData.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                //Build query
                query.append(URLEncoder.encode(fieldName, StandardCharsets.US_ASCII));
                query.append('=');
                query.append(URLEncoder.encode(fieldValue, StandardCharsets.US_ASCII));
                if (itr.hasNext()) {
                    query.append('&');
                    hashData.append('&');
                }
            }
        }

        String secretHash = cryptoService.hmacSHA512(hashSecret, hashData.toString());
        String queryUrl = query.toString();
        queryUrl += "&vnp_SecureHash=" + secretHash;
        return vnPayPayUrl + "?" + queryUrl;
    }
}
