package com.demo.ecomvnpaydemo.rest.controllers;

import com.demo.ecomvnpaydemo.domain.models.Order;
import com.demo.ecomvnpaydemo.domain.models.PayMethod;
import com.demo.ecomvnpaydemo.domain.models.Transaction;
import com.demo.ecomvnpaydemo.domain.models.TransactionStatus;
import com.demo.ecomvnpaydemo.repositories.OrderRepository;
import com.demo.ecomvnpaydemo.repositories.TransactionRepository;
import com.demo.ecomvnpaydemo.rest.dto.PayBody;
import com.demo.ecomvnpaydemo.services.VnpayService;
import com.demo.ecomvnpaydemo.services.OrderRefService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
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


    private final VnpayService vnpayService;

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

    public VnpayController(VnpayService vnpayService, OrderRefService orderRefService, OrderRepository orderRepository, TransactionRepository transactionRepository) {
        this.vnpayService = vnpayService;
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

        String secretHash = vnpayService.hmacSHA512(hashSecret, hashData.toString());
        String queryUrl = query.toString();
        queryUrl += "&vnp_SecureHash=" + secretHash;
        String payUrl = vnPayPayUrl + "?" + queryUrl;

        log.info("Pay url {}", payUrl);
        httpServletResponse.sendRedirect(payUrl);
    }


    @RequestMapping(path = "/return-url", method = RequestMethod.GET)
    public ModelAndView returnUrl(HttpServletRequest httpServletRequest) {
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
        String signValue = vnpayService.hashAllFields(hashSecret, fields);
        log.info("Sign value {}", signValue);
        String vnp_SecureHash = httpServletRequest.getParameter("vnp_SecureHash");
        if (!signValue.equals(vnp_SecureHash)) {
            return new ModelAndView("redirect:/?errorMessage=Invalid transaction");
        }
        String responseCode = httpServletRequest.getParameter("vnp_ResponseCode");
        log.info("Response code {}", responseCode);
        if (!responseCode.equals("00")) {
            if (responseCode.equals("24")) {
                return new ModelAndView("redirect:/?errorMessage=Transaction cancelled");
            } else {
                return new ModelAndView("redirect:/?errorMessage=Pay failed");
            }
        }
        String ref = httpServletRequest.getParameter("vnp_TxnRef");
        Order order = orderRepository.findByRef(ref);
        if (order == null) {
            return new ModelAndView("redirect:/?errorMessage=Order not found");
        }
        if (order.isPayed()) {
            return new ModelAndView("redirect:/?errorMessage=Order already payed");
        }
        String rawAmount = httpServletRequest.getParameter("vnp_Amount");
        float amount = Float.parseFloat(rawAmount) / 100f;
        Transaction transaction = new Transaction();
        transaction.setAmount(amount);
        transaction.setPayMethod(PayMethod.VNPAY);
        transaction.setStatus(TransactionStatus.SUCCESS);
        transaction.setOrder(order);
        transactionRepository.save(transaction);
        log.info("VnPay return url received");
        return new ModelAndView(String.format("redirect:/transactions/%s", transaction.getId().toString()));
    }

}
