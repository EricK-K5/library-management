package com.example.library_management.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

//annotation dung lay tung gia tri trong file yaml tien hon khi dung value
@ConfigurationProperties(prefix = "app.vnpay")
public record VnpConfig(
        boolean enabled,
        String payUrl,
        String tmnCode,
        String hashSecret,
        // VNPay redirect trinh duyet cua user ve day (BE)
        String returnUrl,
        // Sau khi BE xu ly xong, redirect tiep ve trang ket qua cua FE
        String frontendResultUrl,
        String version,
        String command,
        String orderType
) {
}
