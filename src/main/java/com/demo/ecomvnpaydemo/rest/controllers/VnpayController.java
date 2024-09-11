package com.demo.ecomvnpaydemo.rest.controllers;

import com.demo.ecomvnpaydemo.domain.models.Order;
import com.demo.ecomvnpaydemo.domain.models.PayMethod;
import com.demo.ecomvnpaydemo.domain.models.Transaction;
import com.demo.ecomvnpaydemo.domain.models.TransactionStatus;
import com.demo.ecomvnpaydemo.repositories.OrderRepository;
import com.demo.ecomvnpaydemo.repositories.TransactionRepository;
import com.demo.ecomvnpaydemo.rest.schema.PayBody;
import com.demo.ecomvnpaydemo.services.CryptoService;
import com.demo.ecomvnpaydemo.services.OrderRefService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping(path = "/vnpay")
public class VnpayController {

    private static final Logger log = LoggerFactory.getLogger(VnpayController.class);


    private final CryptoService cryptoService;

    @Value("${payment.vnpay.pay.url}")
    private String vnPayPayUrl;

    @Value("${payment.vnpay.pay.return-url}")
    private String vnPayReturnUrl;

    @Value("${payment.vnpay.tmn-code}")
    private String vnPayTmnCode;

    @Value("${payment.vnpay.hash-secret}")
    private String hashSecret;


    private final OrderRepository orderRepository;

    private final TransactionRepository transactionRepository;

    private final OrderRefService orderRefService;

    public VnpayController(CryptoService cryptoService, OrderRefService orderRefService, OrderRepository orderRepository, TransactionRepository transactionRepository) {
        this.cryptoService = cryptoService;
        this.orderRefService = orderRefService;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
    }

    @RequestMapping(path = "/pay", method = RequestMethod.POST, consumes = {MediaType.APPLICATION_FORM_URLENCODED_VALUE})
    public void pay(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, @ModelAttribute PayBody body) throws IOException, URISyntaxException {
        log.info("VnPay pay request received");
        // New order
        String content = body.getContent();
        float amount = body.getAmount();

        String ref = orderRefService.genRef(10);

        log.info("Ref {}", ref);

        Order order = new Order(amount, content, ref, false);
        orderRepository.save(order);

        String ipaddr = httpServletRequest.getHeader("x-forwarded-for");

        Calendar cld = Calendar.getInstance(TimeZone.getTimeZone("Etc/GMT+7"));
        SimpleDateFormat formatter = new SimpleDateFormat("yyyyMMddHHmmss");
        String createDate = formatter.format(cld.getTime());

        cld.add(Calendar.MINUTE, 15);
        String expireDate = formatter.format(cld.getTime());

        Map<String, String> params = new HashMap<>();

        params.put("vnp_Version", "2.1.0");
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", vnPayTmnCode);
        params.put("vnp_Amount", String.format("%d", (int) body.getAmount() * 100));
        params.put("vnp_CreateDate", createDate);
        params.put("vnp_IpAddr", ipaddr);
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_Locale", "vn");
        params.put("vnp_OrderInfo", body.getContent());
        params.put("vnp_ReturnUrl", vnPayReturnUrl);
        params.put("vnp_TxnRef", ref);
        params.put("vnp_OrderType", "other");
        params.put("vnp_ExpireDate", expireDate);

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
        String payUrl = vnPayPayUrl + "?" + queryUrl;

        log.info("Pay url {}", payUrl);
        httpServletResponse.sendRedirect(payUrl);
    }


    @RequestMapping(path = "/return", method = RequestMethod.GET)
    public ModelAndView _return(HttpServletRequest httpServletRequest) {
        log.info("VnPay return url received");
        // Find order
        String ref = httpServletRequest.getParameter("vnp_TxnRef");
        Order order = orderRepository.findByRef(ref);
        if (order == null) {
            return new ModelAndView(String.format("redirect:/?message=%s&success=false", URLEncoder.encode("Không tìm thấy đơn hàng", StandardCharsets.US_ASCII)));
        }
        // Verify signature
        Map<String, String> fields = new HashMap<>();
        for (Enumeration<String> params = httpServletRequest.getParameterNames(); params.hasMoreElements(); ) {
            String fieldName = URLEncoder.encode(params.nextElement(), StandardCharsets.US_ASCII);
            String fieldValue = URLEncoder.encode(httpServletRequest.getParameter(fieldName), StandardCharsets.US_ASCII);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
                fields.put(fieldName, fieldValue);
            }
        }
        fields.remove("vnp_SecureHashType");
        fields.remove("vnp_SecureHash");
        String signValue = cryptoService.hashAllFields(hashSecret, fields);
        log.info("Sign value {}", signValue);
        String vnp_SecureHash = httpServletRequest.getParameter("vnp_SecureHash");

        // Transaction
        Transaction transaction = new Transaction();
        transaction.setPayMethod(PayMethod.VNPAY);
        transaction.setOrder(order);
        // Defines
        TransactionStatus status = TransactionStatus.FAILED;
        String message = "Giao dịch thành công";
        boolean success = false;
        // Check signature
        if (!signValue.equals(vnp_SecureHash)) {
            message = "Không thể xác thực chữ ký giao dịch";
        } else {
            String rawAmount = httpServletRequest.getParameter("vnp_Amount");
            float amount = Float.parseFloat(rawAmount) / 100f;
            transaction.setAmount(amount);
            // Response code
            String responseCode = httpServletRequest.getParameter("vnp_ResponseCode");
            transaction.setResultCode(responseCode);
            //
            if (!responseCode.equals("00")) {
                message = switch (responseCode) {
                    case "01" -> "Giao dịch chưa hoàn tất";
                    case "02" -> "Giao dịch bị lỗi";
                    case "04" ->
                            "Giao dịch đảo (Khách hàng đã bị trừ tiền tại Ngân hàng nhưng GD chưa thành công ở VNPAY)";
                    case "05" -> "VNPAY đang xử lý giao dịch này (GD hoàn tiền)";
                    case "06" -> "VNPAY đã gửi yêu cầu hoàn tiền sang Ngân hàng (GD hoàn tiền)";
                    case "07" ->
                            "Trừ tiền thành công. Giao dịch bị nghi ngờ (liên quan tới lừa đảo, giao dịch bất thường).";
                    case "09" ->
                            "Giao dịch không thành công do: Thẻ/Tài khoản của khách hàng chưa đăng ký dịch vụ InternetBanking tại ngân hàng.";
                    case "10" ->
                            "Giao dịch không thành công do: Khách hàng xác thực thông tin thẻ/tài khoản không đúng quá 3 lần";
                    case "11" ->
                            "Giao dịch không thành công do: Đã hết hạn chờ thanh toán. Xin quý khách vui lòng thực hiện lại giao dịch.";
                    case "12" -> "Giao dịch không thành công do: Thẻ/Tài khoản của khách hàng bị khóa.";
                    case "13" ->
                            "Giao dịch không thành công do Quý khách nhập sai mật khẩu xác thực giao dịch (OTP). Xin quý khách vui lòng thực hiện lại giao dịch.";
                    case "24" -> "Giao dịch không thành công do: Khách hàng hủy giao dịch";
                    case "51" ->
                            "Giao dịch không thành công do: Tài khoản của quý khách không đủ số dư để thực hiện giao dịch.";
                    case "65" ->
                            "Giao dịch không thành công do: Tài khoản của Quý khách đã vượt quá hạn mức giao dịch trong ngày.";
                    case "75" -> "Ngân hàng thanh toán đang bảo trì.";
                    case "79" ->
                            "Giao dịch không thành công do: KH nhập sai mật khẩu thanh toán quá số lần quy định. Xin quý khách vui lòng thực hiện lại giao dịch";
                    case "99" -> "Các lỗi khác (lỗi còn lại, không có trong danh sách mã lỗi đã liệt kê)";
                    default -> "Giao dịch thất bại";
                };
                if (responseCode.equals("07")) {
                    status = TransactionStatus.DISPUTE;
                }
            } else {
                if (order.isPayed()) {
                    message = "Đơn hàng đã được thanh toán";
                } else {
                    status = TransactionStatus.SUCCESS;
                    order.setPayed(true);
                    success = true;
                }
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
        return new ModelAndView(String.format("redirect:/?message=%s&success=%b", URLEncoder.encode(message, StandardCharsets.UTF_8), success));
    }


}
