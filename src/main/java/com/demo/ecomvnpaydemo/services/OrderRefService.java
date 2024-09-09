package com.demo.ecomvnpaydemo.services;

import org.springframework.stereotype.Service;

import java.util.Random;

@Service
public class OrderRefService {

    public String genRef(int n) {
        String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
        Random random = new Random();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) {
            int index = random.nextInt(characters.length());
            sb.append(characters.charAt(index));
        }
        return sb.toString();
    }

}
