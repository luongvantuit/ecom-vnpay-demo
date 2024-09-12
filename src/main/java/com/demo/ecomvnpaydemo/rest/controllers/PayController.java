package com.demo.ecomvnpaydemo.rest.controllers;

import com.demo.ecomvnpaydemo.domain.models.Order;
import com.demo.ecomvnpaydemo.domain.models.PayMethod;
import com.demo.ecomvnpaydemo.domain.models.Transaction;
import com.demo.ecomvnpaydemo.domain.models.TransactionStatus;
import com.demo.ecomvnpaydemo.repositories.OrderRepository;
import com.demo.ecomvnpaydemo.repositories.TransactionRepository;
import com.demo.ecomvnpaydemo.services.MomoService;
import com.demo.ecomvnpaydemo.services.VnpayService;
import com.demo.ecomvnpaydemo.utils.Generate;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@RestController
@RequestMapping(path = "/pay")
public class PayController {

    private static final Logger log = LoggerFactory.getLogger(PayController.class);
    final
    VnpayService vnpayService;

    final
    MomoService momoService;

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;

    public PayController(VnpayService vnpayService, MomoService momoService, OrderRepository orderRepository, TransactionRepository transactionRepository) {
        this.vnpayService = vnpayService;
        this.momoService = momoService;
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
    }

    @RequestMapping(path = {"/{orderId}/{type}","/{orderId}/{type}/"},method = RequestMethod.POST)
    public void redirectUrl(@PathVariable(name = "orderId") String orderId, @PathVariable(name = "type") String type, HttpServletResponse httpServletResponse, HttpServletRequest httpServletRequest) throws IOException, NoSuchAlgorithmException, InvalidKeyException {
        Optional<Order> optionalOrder = orderRepository.findById(UUID.fromString(orderId));
        if (optionalOrder.isEmpty()) {
            httpServletResponse.sendRedirect("/?message=Không tìm thấy đơn hàng&success=false");
        } else {
            String ipAddr = httpServletRequest.getHeader("X-FORWARDED-FOR");
            Order order = optionalOrder.get();
            Transaction transaction = new Transaction();
            transaction.setAmount(order.getAmount());
            transaction.setStatus(TransactionStatus.PENDING);
            transaction.setOrder(order);
            transaction.setRef(Generate.str(15));
            transaction.setIpAddress(ipAddr);
            if ("momo".equals(type)) {
                transaction.setPayMethod(PayMethod.MOMO);
            } else {
                transaction.setPayMethod(PayMethod.VNPAY);
            }
            transactionRepository.save(transaction);
            Set<Transaction> transactions = order.getTransactions();
            if (transactions == null) {
                transactions = new HashSet<>();
            }
            transactions.add(transaction);
            order.setTransactions(transactions);
            orderRepository.save(order);
            if ("momo".equals(type)) {
                httpServletResponse.sendRedirect(momoService.payUrl(transaction));
            } else {
                httpServletResponse.sendRedirect(vnpayService.payUrl(transaction));
            }
        }
    }

}
