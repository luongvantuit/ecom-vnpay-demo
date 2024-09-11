package com.demo.ecomvnpaydemo.rest.controllers;

import com.demo.ecomvnpaydemo.domain.models.Order;
import com.demo.ecomvnpaydemo.domain.models.PayMethod;
import com.demo.ecomvnpaydemo.domain.models.Transaction;
import com.demo.ecomvnpaydemo.domain.models.TransactionStatus;
import com.demo.ecomvnpaydemo.repositories.OrderRepository;
import com.demo.ecomvnpaydemo.repositories.TransactionRepository;
import com.demo.ecomvnpaydemo.rest.schema.MomoPayPayload;
import com.demo.ecomvnpaydemo.rest.schema.MomoPayResponse;
import com.demo.ecomvnpaydemo.rest.schema.MomoPayWebhookPayload;
import com.demo.ecomvnpaydemo.rest.schema.PayBody;
import com.demo.ecomvnpaydemo.services.CryptoService;
import com.demo.ecomvnpaydemo.services.OrderRefService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@RestController
@RequestMapping("/momo")
public class MomoController {

    private static final Logger log = LoggerFactory.getLogger(MomoController.class);

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

    private final OrderRefService orderRefService;

    private final OrderRepository orderRepository;

    private final TransactionRepository transactionRepository;

    public MomoController(OrderRefService orderRefService, OrderRepository orderRepository, CryptoService cryptoService, TransactionRepository transactionRepository) {
        this.orderRefService = orderRefService;
        this.orderRepository = orderRepository;
        this.cryptoService = cryptoService;
        this.transactionRepository = transactionRepository;
    }

    @RequestMapping(path = "/pay", method = RequestMethod.POST, consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public void pay(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, @ModelAttribute PayBody body) throws NoSuchAlgorithmException, InvalidKeyException, IOException {
        // New order
        String content = body.getContent();
        String ref = orderRefService.genRef(10);
        log.info("Ref {}", ref);
        Order order = new Order(body.getAmount(), content, ref, false);
        orderRepository.save(order);
        String requestId = UUID.randomUUID().toString();
        long amount = (long) body.getAmount();
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
                ref,
                body.getContent(),
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
                ref,
                body.getContent(),
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
        httpServletResponse.sendRedirect(Objects.requireNonNull(response.getBody()).getPayUrl());
    }

    @RequestMapping(path = "/webhook", method = RequestMethod.POST, consumes = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<Object> webhook(@RequestBody MomoPayWebhookPayload payload) throws NoSuchAlgorithmException, InvalidKeyException {
        log.info("Payload {}", payload);

        String rawSignatureData = String.format(
                // @formatter:off
                "accessKey=%s" +
                        "&amount=%d" +
                        "&extraData=%s" +
                        "&message=%s" +
                        "&orderId=%s" +
                        "&orderInfo=%s" +
                        "&orderType=%s" +
                        "&partnerCode=%s" +
                        "&payType=%s" +
                        "&requestId=%s" +
                        "&responseTime=%d" +
                        "&resultCode=%d" +
                        "&transId=%d",
                accessKey,
                payload.getAmount(),
                payload.getExtraData(),
                payload.getMessage(),
                payload.getOrderId(),
                payload.getOrderInfo(),
                payload.getOrderType(),
                payload.getPartnerCode(),
                payload.getPayType(),
                payload.getRequestId(),
                payload.getResponseTime(),
                payload.getResultCode(),
                payload.getTransId()
                // @formatter:off
        );
        String ref = payload.getOrderId();
        Order order = orderRepository.findByRef(ref);
        if (order == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        // Transaction
        Transaction transaction = new Transaction();
        transaction.setPayMethod(PayMethod.MOMO);
        transaction.setOrder(order);
        // Defines
        TransactionStatus status = TransactionStatus.FAILED;
        String message = "Giao dịch thành công";
        // Check signature
        String signature = cryptoService.hmacSHA256(secretKey, rawSignatureData);
        if (!signature.equals(payload.getSignature())) {
            message = "Không thể xác thực chữ ký giao dịch";
        } else {
            transaction.setAmount(payload.getAmount());
            int resultCode = payload.getResultCode();
            transaction.setResultCode(String.format("%d",resultCode));
            switch (resultCode) {
                case 0:
                    message = "Thành công.";
                    if (!order.isPayed()) {
                        status = TransactionStatus.SUCCESS;
                    } else {
                        message = "Đơn hàng đã được thanh toán";
                    }
                    break;
                case 10:
                    message = "Hệ thống đang được bảo trì.";
                    break;
                case 11:
                    message = "Truy cập bị từ chối.";
                    break;
                case 12:
                    message = "Phiên bản API không được hỗ trợ cho yêu cầu này.";
                    break;
                case 13:
                    message = "Xác thực doanh nghiệp thất bại.";
                    break;
                case 20:
                    message = "Yêu cầu sai định dạng.";
                    break;
                case 21:
                    message = "Yêu cầu bị từ chối vì số tiền giao dịch không hợp lệ.";
                    break;
                case 22:
                    message = "Số tiền giao dịch không hợp lệ.";
                    break;
                case 40:
                    message = "RequestId bị trùng.";
                    break;
                case 41:
                    message = "OrderId bị trùng.";
                    break;
                case 42:
                    message = "OrderId không hợp lệ hoặc không được tìm thấy.";
                    break;
                case 43:
                    message = "Yêu cầu bị từ chối vì xung đột trong quá trình xử lý giao dịch.";
                    break;
                case 45:
                    message = "Trùng ItemId";
                    break;
                case 47:
                    message = "Yêu cầu bị từ chối vì thông tin không hợp lệ trong danh sách dữ liệu khả dụng";
                    break;
                case 98:
                    message = "QR Code tạo không thành công. Vui lòng thử lại sau.";
                    break;
                case 99:
                    message = "Lỗi không xác định.";
                    break;
                case 1000:
                    message = "Giao dịch đã được khởi tạo, chờ người dùng xác nhận thanh toán.";
                    break;
                case 1001:
                    message = "Giao dịch thanh toán thất bại do tài khoản người dùng không đủ tiền.";
                    break;
                case 1002:
                    message = "Giao dịch bị từ chối do nhà phát hành tài khoản thanh toán.";
                    break;
                case 1003:
                    message = "Giao dịch bị đã bị hủy.";
                    break;
                case 1004:
                    message = "Giao dịch thất bại do số tiền thanh toán vượt quá hạn mức thanh toán của người dùng.";
                    break;
                case 1005:
                    message = "Giao dịch thất bại do url hoặc QR code đã hết hạn.";
                    break;
                case 1006:
                    message = "Giao dịch thất bại do người dùng đã từ chối xác nhận thanh toán.";
                    break;
                case 1007:
                    message = "Giao dịch bị từ chối vì tài khoản không tồn tại hoặc đang ở trạng thái ngưng hoạt động.";
                    break;
                case 1017:
                    message = "Giao dịch bị hủy bởi đối tác.";
                    break;
                case 1026:
                    message = "Giao dịch bị hạn chế theo thể lệ chương trình khuyến mãi.";
                    break;
                case 1080:
                    message = "Giao dịch hoàn tiền thất bại trong quá trình xử lý. Vui lòng thử lại trong khoảng thời gian ngắn, tốt hơn là sau một giờ.";
                    break;
                case 1081:
                    message = "Giao dịch hoàn tiền bị từ chối. Giao dịch thanh toán ban đầu có thể đã được hoàn.";
                    break;
                case 1088:
                    message = "Giao dịch hoàn tiền bị từ chối. Giao dịch thanh toán ban đầu không được hỗ trợ hoàn tiền.";
                    break;
                case 2019:
                    message = "Yêu cầu bị từ chối vì orderGroupId không hợp lệ.";
                    break;
                case 4001:
                    message = "Giao dịch bị từ chối do tài khoản người dùng đang bị hạn chế.";
                    break;
                case 4100:
                    message = "Giao dịch thất bại do người dùng không đăng nhập thành công.";
                case 7000:
                    message = "Giao dịch đang được xử lý.";
                    break;
                case 7002:
                    message = "Giao dịch đang được xử lý bởi nhà cung cấp loại hình thanh toán.";
                    break;
                case 9000:
                    message = "Giao dịch đã được xác nhận thành công.";
                    break;
                default:
                    message = "Giao dịch thất bại";
                    break;
            }
        }
        transaction.setMessage(message);
        transaction.setStatus(status);
        transactionRepository.save(transaction);
        Set<Transaction> transactions = order.getTransactions();
        if (transactions == null) {
            transactions = new HashSet<>();
        }
        transactions.add(transaction);
        order.setTransactions(transactions);
        orderRepository.save(order);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    @RequestMapping(path = "/return", method = RequestMethod.GET)
    public void _return(HttpServletResponse httpServletResponse) throws IOException {
        log.info("MOMO return received");
        httpServletResponse.sendRedirect("/orders");
    }

}
