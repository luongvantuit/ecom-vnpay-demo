package com.demo.ecomvnpaydemo.rest.controllers;

import com.demo.ecomvnpaydemo.domain.models.Order;
import com.demo.ecomvnpaydemo.repositories.OrderRepository;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

import java.util.List;

@RestController
@RequestMapping(path = "/orders")
public class OrderController {

    private final OrderRepository orderRepository;


    public OrderController(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    @RequestMapping(path = "", method = RequestMethod.GET)
    public ModelAndView index(Model model) {
        List<Order> orders = orderRepository.findAll();
        model.addAttribute("orders", orders);
        return new ModelAndView("orders");
    }

}
