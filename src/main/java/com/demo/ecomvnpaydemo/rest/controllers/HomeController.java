package com.demo.ecomvnpaydemo.rest.controllers;

import com.demo.ecomvnpaydemo.rest.schema.CreateOrderBody;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.ModelAndView;

@RestController
public class HomeController {

    @RequestMapping(path = {"","/"}, method = RequestMethod.GET)
    public ModelAndView home(HttpServletRequest httpServletRequest, Model model) {
        CreateOrderBody createOrderBody = new CreateOrderBody();
        createOrderBody.setAmount(10000);
        createOrderBody.setContent("Mua sản phẩm");
        model.addAttribute("pay", createOrderBody);
        model.addAttribute("message", httpServletRequest.getParameter("message"));
        model.addAttribute("success", httpServletRequest.getParameter("success"));
        return new ModelAndView("index");
    }
}
