package com.demo.ecomvnpaydemo.rest.controllers;

import com.demo.ecomvnpaydemo.domain.models.Order;
import com.demo.ecomvnpaydemo.domain.models.PayMethod;
import com.demo.ecomvnpaydemo.domain.models.Transaction;
import com.demo.ecomvnpaydemo.domain.models.TransactionStatus;
import com.demo.ecomvnpaydemo.repositories.OrderRepository;
import com.demo.ecomvnpaydemo.repositories.TransactionRepository;
import com.demo.ecomvnpaydemo.services.CryptoService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
@RequestMapping(path = "/vnpay")
public class VnpayController {

    private static final Logger log = LoggerFactory.getLogger(VnpayController.class);

    private final CryptoService cryptoService;

    @Value("${payment.vnpay.hash-secret}")
    private String hashSecret;

    private final OrderRepository orderRepository;

    private final TransactionRepository transactionRepository;

    public VnpayController(CryptoService cryptoService, OrderRepository orderRepository, TransactionRepository transactionRepository) {
        this.cryptoService = cryptoService;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
    }

    @RequestMapping(path = {"/return","/return/"}, method = RequestMethod.GET)
    public ModelAndView _return(HttpServletRequest httpServletRequest) {
        log.info("VnPay return url received");
        // Find order
        String ref = httpServletRequest.getParameter("vnp_TxnRef");
        Transaction transaction = transactionRepository.findByRef(ref);
        if (transaction == null) {
            return new ModelAndView(String.format("redirect:/?message=%s&success=false", URLEncoder.encode("Không tìm thấy thông tin giao dịch", StandardCharsets.US_ASCII)));
        }
        Order order = transaction.getOrder();
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
        orderRepository.save(order);
        return new ModelAndView(String.format("redirect:/transactions/%s?message=%s&success=%b",transaction.getId().toString(), URLEncoder.encode(message, StandardCharsets.UTF_8), success));
    }


}
