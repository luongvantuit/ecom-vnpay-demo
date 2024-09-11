package com.demo.ecomvnpaydemo.services;

import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
public class CryptoService {

    public String hashAllFields(final String key, Map<String, String> fields) {
        List<String> fieldNames = new ArrayList<>(fields.keySet());
        Collections.sort(fieldNames);
        StringBuilder sb = new StringBuilder();
        Iterator<String> itr = fieldNames.iterator();
        while (itr.hasNext()) {
            String fieldName = itr.next();
            String fieldValue = fields.get(fieldName);
            if ((fieldValue != null) && (!fieldValue.isEmpty())) {
                sb.append(fieldName);
                sb.append("=");
                sb.append(fieldValue);
            }
            if (itr.hasNext()) {
                sb.append("&");
            }
        }
        return hmacSHA512(key, sb.toString());
    }

    public String hmacSHA512(final String key, final String data) {
        try {
            if (key == null || data == null) {
                throw new NullPointerException();
            }
            final Mac hmac512 = Mac.getInstance("HmacSHA512");
            byte[] hmacKeyBytes = key.getBytes();
            final SecretKeySpec secretKey = new SecretKeySpec(hmacKeyBytes, "HmacSHA512");
            hmac512.init(secretKey);
            byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
            byte[] result = hmac512.doFinal(dataBytes);
            StringBuilder sb = new StringBuilder(2 * result.length);
            for (byte b : result) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();

        } catch (Exception ex) {
            return "";
        }
    }

    public String hmacSHA256(final String key, final String data) throws NoSuchAlgorithmException, InvalidKeyException {
        // Tạo đối tượng Mac với thuật toán HMAC-SHA256
        Mac sha256Hmac = Mac.getInstance("HmacSHA256");

        // Tạo khóa bí mật từ secretKey
        SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA256");

        // Khởi tạo đối tượng Mac với khóa bí mật
        sha256Hmac.init(secretKeySpec);

        // Tính toán HMAC cho thông điệp
        byte[] hmacBytes = sha256Hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));

        // Chuyển đổi HMAC thành chuỗi Base64 để dễ đọc hơn (hoặc có thể dùng Hex)

        return toHexString(hmacBytes);
    }

    private static String toHexString(byte[] bytes) {
        Formatter formatter = new Formatter();
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

}
