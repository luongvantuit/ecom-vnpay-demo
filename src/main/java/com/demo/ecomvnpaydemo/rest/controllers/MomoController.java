package com.demo.ecomvnpaydemo.rest.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/momo")
public class MomoController {

    @RequestMapping(path = "/pay",method = RequestMethod.POST)
    public void pay() {
    }

    @RequestMapping(path = "/return-url",method = RequestMethod.GET)
    public void returnUrl() {
    }

}
