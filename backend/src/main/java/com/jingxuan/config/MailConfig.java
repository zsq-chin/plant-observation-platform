package com.jingxuan.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.util.Properties;

@Configuration
public class MailConfig {

    @Value("${MAIL_HOST:smtp.qq.com}")
    private String host;

    @Value("${MAIL_PORT:587}")
    private int port;

    @Value("${MAIL_USERNAME:}")
    private String username;

    @Value("${MAIL_PASSWORD:}")
    private String password;

    @Value("${MAIL_SMTP_AUTH:true}")
    private boolean smtpAuth;

    @Value("${MAIL_SMTP_STARTTLS_ENABLE:true}")
    private boolean smtpStarttlsEnable;

    @Value("${MAIL_SMTP_STARTTLS_REQUIRED:true}")
    private boolean smtpStarttlsRequired;

    @Bean
    public JavaMailSender javaMailSender() {
        boolean credentialsMissing = username == null || username.isBlank()
                || password == null || password.isBlank();
        if (smtpAuth && credentialsMissing) {
            return null;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (!credentialsMissing) {
            sender.setUsername(username);
            sender.setPassword(password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", Boolean.toString(smtpAuth));
        props.put("mail.smtp.starttls.enable", Boolean.toString(smtpStarttlsEnable));
        props.put("mail.smtp.starttls.required", Boolean.toString(smtpStarttlsRequired));
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        return sender;
    }
}
