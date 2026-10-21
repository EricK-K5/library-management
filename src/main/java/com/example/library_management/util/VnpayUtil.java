package com.example.library_management.util;

import jakarta.servlet.http.HttpServletRequest;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

public final class VnpayUtil {

    private VnpayUtil() {
    }

    public static String hmacSHA512(String key, String data) {
        try {
            Mac hmac = Mac.getInstance("HmacSHA512");
            hmac.init(new SecretKeySpec(
                    key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] hash = hmac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (byte b : hash) {
                // %02x: hex, toi thieu 2 ki tu (1 byte = 8 bit)
                result.append(String.format("%02x", b));
            }
            return result.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot generate VNPAY signature", e);
        }
    }

    // Chuoi de ky: sort theo key, bo gia tri rong, bo vnp_SecureHash*, value URL-encode
    public static String buildHashData(Map<String, String> params) {
        StringBuilder hashData = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            if (!isSignable(entry)) {
                continue;
            }
            if (!hashData.isEmpty()) {
                hashData.append('&');
            }
            hashData.append(entry.getKey())
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }
        return hashData.toString();
    }

    public static String buildQueryString(Map<String, String> params) {
        StringBuilder query = new StringBuilder();
        for (Map.Entry<String, String> entry : new TreeMap<>(params).entrySet()) {
            if (!isSignable(entry)) {
                continue;
            }
            if (!query.isEmpty()) {
                query.append('&');
            }
            query.append(URLEncoder.encode(entry.getKey(), StandardCharsets.US_ASCII))
                    .append('=')
                    .append(URLEncoder.encode(entry.getValue(), StandardCharsets.US_ASCII));
        }
        return query.toString();
    }

    // Kiem tra chu ky VNPay gui ve (Return URL / IPN). So sanh constant-time.
    public static boolean verifySignature(String secret, Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null || received.isBlank()) {
            return false;
        }
        String expected = hmacSHA512(secret, buildHashData(params));
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                received.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }

    // Local dev: getRemoteAddr tra ve "0:0:0:0:0:0:0:1" (IPv6), VNPay khong nhan.
    // Qua ngrok/proxy thi IP that nam trong X-Forwarded-For.
    public static String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isBlank()) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        if (ip == null || ip.isBlank() || ip.contains(":")) {
            return "127.0.0.1";
        }
        return ip;
    }

    private static boolean isSignable(Map.Entry<String, String> entry) {
        String key = entry.getKey();
        String value = entry.getValue();
        return value != null && !value.isEmpty()
                && !key.equals("vnp_SecureHash")
                && !key.equals("vnp_SecureHashType");
    }
}
