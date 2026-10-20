package com.example.library_management.service;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.beans.factory.annotation.Value;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {
    private final JavaMailSender javaMailSender;

    @Value("${app.mail.enabled:false}")
    private boolean enabled;

    @Value("${app.mail.from}")
    private String from;

    public boolean send(String to, String subject, String body){
        if(!enabled){
            log.info("Mail dang tat");
            return false;
        }
        if(!StringUtils.hasText(to)){
            return false;
        }

//        Dat trong try catch de tranh truong hop mail hong khong anh huong den van de khac
        try {
            MimeMessage message = javaMailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message,false,"utf-8");
            if(StringUtils.hasText(from)){
                helper.setFrom(from);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body, false);
            javaMailSender.send(message);
            return true;
        } catch (Exception e) {
            log.error("Gui mail that bai toi {}: {}", to, e.getMessage());
            return false;
        }
    }
}
