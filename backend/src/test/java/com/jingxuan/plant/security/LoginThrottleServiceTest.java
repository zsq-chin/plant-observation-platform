package com.jingxuan.plant.security;

import com.jingxuan.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 登录防爆破测试（V4 §53）。 */
class LoginThrottleServiceTest {

    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> values = mock(ValueOperations.class);
    private final LoginThrottleService service = new LoginThrottleService(redis);

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
    }

    @Test
    void allowsLoginBelowThreshold() {
        when(values.get(anyString())).thenReturn("3");
        assertDoesNotThrow(() -> service.assertAllowed("stu1"));
    }

    @Test
    void blocksLoginAtThreshold() {
        when(values.get("jingxuan:login:fail:stu1")).thenReturn("5");
        assertThrows(BusinessException.class, () -> service.assertAllowed("stu1"));
    }

    @Test
    void recordsFailureAndSetsTtlOnFirst() {
        when(values.increment("jingxuan:login:fail:stu1")).thenReturn(1L);
        service.recordFailure("stu1");
        verify(redis).expire(eq("jingxuan:login:fail:stu1"), eq(Duration.ofMinutes(10)));
    }

    @Test
    void resetClearsKey() {
        service.reset("stu1");
        verify(redis).delete("jingxuan:login:fail:stu1");
    }
}
