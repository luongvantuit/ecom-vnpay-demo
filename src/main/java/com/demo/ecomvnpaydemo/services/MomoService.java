package com.demo.ecomvnpaydemo.services;

import com.demo.ecomvnpaydemo.domain.models.Transaction;
import com.demo.ecomvnpaydemo.rest.schema.MomoPayPayload;
import com.demo.ecomvnpaydemo.rest.schema.MomoPayResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Objects;
import java.util.UUID;

@Service
public class MomoService {

    private static final Logger log = LoggerFactory.getLogger(MomoService.class);

    @Value("${payment.momo.partner-code}")
    private String partnerCode;

    @Value("${payment.momo.pay.return-url}")
    private String redirectUrl;

    @Value("${payment.momo.pay.webhook-url}")
    private String webhookUrl;

    @Value("${payment.momo.access-key}")
    private String accessKey;

    @Value("${payment.momo.secret-key}")
    private String secretKey;

    @Value("${payment.momo.pay.url}")
    private String momoPayUrl;

    private final CryptoService cryptoService;

    public MomoService(CryptoService cryptoService) {
        this.cryptoService = cryptoService;
    }

    public String payUrl(Transaction transaction) throws NoSuchAlgorithmException, InvalidKeyException {
        String requestId = UUID.randomUUID().toString();
        long amount = (long) transaction.getAmount();
        // Extra data
        String extraData = "";
        String requestType = "captureWallet";

        String rawSignatureData = String.format(
                // @formatter:off
                "accessKey=%s" +
                        "&amount=%d" +
                        "&extraData=%s" +
                        "&ipnUrl=%s" +
                        "&orderId=%s" +
                        "&orderInfo=%s" +
                        "&partnerCode=%s" +
                        "&redirectUrl=%s" +
                        "&requestId=%s" +
                        "&requestType=%s",
                accessKey,
                amount,
                extraData,
                webhookUrl,
                transaction.getRef(),
                transaction.getOrder().getContent(),
                partnerCode,
                redirectUrl,
                requestId,
                requestType
        );
        log.info("Raw signature data {}", rawSignatureData);
        // @formatter:on
        String signature = cryptoService.hmacSHA256(secretKey, rawSignatureData);

        log.info("Signature {}", signature);

        MomoPayPayload payPayload = new MomoPayPayload(
                // @formatter:off
                partnerCode,
                accessKey,
                requestId,
                amount,
                transaction.getRef(),
                transaction.getOrder().getContent(),
                redirectUrl,
                webhookUrl,
                extraData,
                requestType,
                signature
                // @formatter:on
        );
        HttpHeaders headers = new HttpHeaders();
        headers.set("Content-Type", "application/json");

        HttpEntity<MomoPayPayload> request = new HttpEntity<>(payPayload, headers);

        RestTemplate restTemplate = new RestTemplate();

        ResponseEntity<MomoPayResponse> response = restTemplate.exchange(momoPayUrl, HttpMethod.POST, request, MomoPayResponse.class);
        if (response.getStatusCode() != HttpStatus.OK) {
            return "/?message=Đã có lỗi xảy ra&success=false";
        }
        return Objects.requireNonNull(response.getBody()).getPayUrl();
    }
}
