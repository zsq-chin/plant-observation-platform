package com.jingxuan.auth.service;

import com.jingxuan.entity.SysUser;
import com.jingxuan.enums.UserStatusEnum;
import com.jingxuan.exception.BusinessException;
import com.jingxuan.mapper.SysUserMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegistrationServiceTest {

    @Test
    void sendsVerificationCodeWithConfiguredFromAddress() {
        SysUserMapper users = mock(SysUserMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        JavaMailSender mailSender = mock(JavaMailSender.class);
        @SuppressWarnings("unchecked")
        ObjectProvider<JavaMailSender> mailSenderProvider = mock(ObjectProvider.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(
                "jingxuan:verify:cooldown:preview@example.test:2",
                "1",
                Duration.ofSeconds(60))).thenReturn(true);
        when(mailSenderProvider.getIfAvailable()).thenReturn(mailSender);
        RegistrationService service = new RegistrationService(
                users,
                mock(PasswordEncoder.class),
                redis,
                mailSenderProvider);
        ReflectionTestUtils.setField(service, "mailFrom", "noreply@jingxuan.test");

        service.sendVerificationCode(Map.of(
                "email", "preview@example.test",
                "roleId", 2));

        ArgumentCaptor<SimpleMailMessage> messageCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(messageCaptor.capture());
        SimpleMailMessage message = messageCaptor.getValue();
        assertEquals("noreply@jingxuan.test", message.getFrom());
        assertThat(message.getTo()).containsExactly("preview@example.test");
        assertEquals("菁选注册验证码", message.getSubject());
        assertThat(message.getText()).containsPattern("(?<!\\d)\\d{6}(?!\\d)");
        verify(values).set(
                eq("jingxuan:verify:preview@example.test:2"),
                argThat(code -> code != null && code.matches("\\d{6}")),
                eq(Duration.ofMinutes(5)));
    }

    @Test
    void doesNotAcknowledgeVerificationCodeWhenMailSenderIsUnavailable() {
        SysUserMapper users = mock(SysUserMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(any(), any(), any(java.time.Duration.class))).thenReturn(true);
        RegistrationService service = new RegistrationService(
                users,
                mock(PasswordEncoder.class),
                redis,
                emptyMailSenderProvider());

        BusinessException error = assertThrows(BusinessException.class, () -> service.sendVerificationCode(Map.of(
                "email", "preview@example.test",
                "roleId", 1)));

        assertThat(error.getMessage()).contains("邮箱服务未配置");
        verify(redis, never()).opsForValue();
    }

    @Test
    void teacherRegistrationCreatesPendingApprovalAccount() {
        SysUserMapper users = mock(SysUserMapper.class);
        PasswordEncoder passwords = mock(PasswordEncoder.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("jingxuan:verify:teacher@example.edu:2")).thenReturn("123456");
        when(users.countByUsername("teacher001")).thenReturn(0);
        when(users.countByEmailAndRole("teacher@example.edu", 2)).thenReturn(0);
        when(passwords.encode("ExamplePass2026")).thenReturn("bcrypt12");
        RegistrationService service = new RegistrationService(users, passwords, redis, emptyMailSenderProvider());

        service.register(Map.of(
                "username", "teacher001",
                "password", "ExamplePass2026",
                "realName", "教师",
                "email", "teacher@example.edu",
                "verifyCode", "123456",
                "roleId", 2));

        ArgumentCaptor<SysUser> captured = ArgumentCaptor.forClass(SysUser.class);
        verify(users).insert(captured.capture());
        assertEquals(UserStatusEnum.PENDING_APPROVAL, captured.getValue().getStatus());
    }

    @Test
    void rejectsAlreadyConsumedOrWrongVerificationCodeBeforeInsertingAUser() {
        SysUserMapper users = mock(SysUserMapper.class);
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(users.countByUsername("teacher001")).thenReturn(0);
        when(users.countByEmailAndRole("teacher@example.edu", 2)).thenReturn(0);

        RegistrationService service = new RegistrationService(users, mock(PasswordEncoder.class), redis,
                emptyMailSenderProvider());

        assertThrows(com.jingxuan.exception.BusinessException.class, () -> service.register(Map.of(
                "username", "teacher001", "password", "ExamplePass2026", "realName", "教师",
                "email", "teacher@example.edu", "verifyCode", "123456", "roleId", 2)));

        verify(users, never()).insert(any(SysUser.class));
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<JavaMailSender> emptyMailSenderProvider() {
        return mock(ObjectProvider.class);
    }
}
