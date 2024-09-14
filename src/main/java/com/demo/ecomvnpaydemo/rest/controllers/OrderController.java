package com.demo.ecomvnpaydemo.rest.controllers;

import com.demo.ecomvnpaydemo.domain.models.Order;
import com.demo.ecomvnpaydemo.domain.models.PayType;
import com.demo.ecomvnpaydemo.domain.models.Transaction;
import com.demo.ecomvnpaydemo.repositories.OrderRepository;
import com.demo.ecomvnpaydemo.repositories.TransactionRepository;
import com.demo.ecomvnpaydemo.rest.schema.CreateOrderBody;
import com.demo.ecomvnpaydemo.rest.schema.PayBody;
import com.demo.ecomvnpaydemo.utils.Generate;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping(path = "/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    private final OrderRepository orderRepository;
    private final TransactionRepository transactionRepository;

    public OrderController(OrderRepository orderRepository, TransactionRepository transactionRepository) {
        this.orderRepository = orderRepository;
        this.transactionRepository = transactionRepository;
    }

    @RequestMapping(path = {"/{orderId}", "/{orderId}/"}, method = RequestMethod.GET)
    public ModelAndView show(Model model, @PathVariable String orderId) {
        Optional<Order> optionalOrder = orderRepository.findById(UUID.fromString(orderId));
        if (optionalOrder.isEmpty()) {
            return new ModelAndView("redirect:/orders?message=Không tìm thấy đơn hàng&success=false");
        }
        model.addAttribute("order", optionalOrder.get());
        List<Transaction> transactions = transactionRepository.findAllByOrderId(UUID.fromString(orderId), Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("transactions", transactions);
        PayBody payBody = new PayBody();
        payBody.setPayType(PayType.INTERNAL_BANK);
        model.addAttribute("payBody", payBody);
        return new ModelAndView("order");
    }


    @RequestMapping(path = {"", "/"}, method = RequestMethod.GET)
    public ModelAndView index(Model model, HttpServletRequest httpServletRequest) {
        List<Order> orders = orderRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        model.addAttribute("orders", orders);
        model.addAttribute("message", httpServletRequest.getParameter("message"));
        model.addAttribute("success", httpServletRequest.getParameter("success"));
        return new ModelAndView("orders");
    }

    @RequestMapping(path = {"", "/"}, method = RequestMethod.POST)
    public ModelAndView create(@ModelAttribute CreateOrderBody body) {
        // New order
        String content = body.getContent();
        float amount = body.getAmount();
        String ref = Generate.str(15);
        log.info("Ref {}", ref);
        Order order = new Order(amount, content, ref, false);
        orderRepository.save(order);
        return new ModelAndView(String.format("redirect:/orders/%s", order.getId().toString()));
    }

}
