package com.jingxuan.auth.service;

import com.jingxuan.config.MailConfig;
import com.jingxuan.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RegistrationServiceMailContextTest {

    @Test
    void usesSecureSmtpDefaultsWhenCredentialsAreConfigured() {
        new ApplicationContextRunner()
                .withUserConfiguration(MailConfig.class)
                .withPropertyValues(
                        "MAIL_HOST=smtp.example.test",
                        "MAIL_PORT=587",
                        "MAIL_USERNAME=sender@example.test",
                        "MAIL_PASSWORD=example-authorization-code")
                .run(context -> {
                    JavaMailSender javaMailSender = context.getBeanProvider(JavaMailSender.class).getIfAvailable();
                    assertThat(javaMailSender).isInstanceOf(JavaMailSenderImpl.class);

                    JavaMailSenderImpl sender = (JavaMailSenderImpl) javaMailSender;
                    assertThat(sender.getJavaMailProperties())
                            .containsEntry("mail.smtp.auth", "true")
                            .containsEntry("mail.smtp.starttls.enable", "true")
                            .containsEntry("mail.smtp.starttls.required", "true");
                });
    }

    @Test
    void createsUnauthenticatedSenderForLocalMailCapture() {
        new ApplicationContextRunner()
                .withUserConfiguration(MailConfig.class)
                .withPropertyValues(
                        "MAIL_HOST=mailpit",
                        "MAIL_PORT=1025",
                        "MAIL_USERNAME=",
                        "MAIL_PASSWORD=",
                        "MAIL_SMTP_AUTH=false",
                        "MAIL_SMTP_STARTTLS_ENABLE=false",
                        "MAIL_SMTP_STARTTLS_REQUIRED=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    JavaMailSender javaMailSender = context.getBeanProvider(JavaMailSender.class).getIfAvailable();
                    assertThat(javaMailSender).isInstanceOf(JavaMailSenderImpl.class);

                    JavaMailSenderImpl sender = (JavaMailSenderImpl) javaMailSender;
                    assertThat(sender.getHost()).isEqualTo("mailpit");
                    assertThat(sender.getPort()).isEqualTo(1025);
                    assertThat(sender.getJavaMailProperties())
                            .containsEntry("mail.smtp.auth", "false")
                            .containsEntry("mail.smtp.starttls.enable", "false")
                            .containsEntry("mail.smtp.starttls.required", "false");
                });
    }

    @Test
    void startsWithoutMailCredentialsAndKeepsRegistrationAvailable() {
        new ApplicationContextRunner()
                .withUserConfiguration(MailConfig.class, RegistrationService.class)
                .withBean(SysUserMapper.class, () -> mock(SysUserMapper.class))
                .withBean(PasswordEncoder.class, () -> mock(PasswordEncoder.class))
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .withPropertyValues("MAIL_USERNAME=", "MAIL_PASSWORD=")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RegistrationService.class);
                    assertThat(context.getBeanProvider(JavaMailSender.class).getIfAvailable()).isNull();
                });
    }
}
