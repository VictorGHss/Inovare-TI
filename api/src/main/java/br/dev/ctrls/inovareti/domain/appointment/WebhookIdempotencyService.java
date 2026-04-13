package br.dev.ctrls.inovareti.domain.appointment;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class WebhookIdempotencyService {

    private final StringRedisTemplate redis;
    private final AppointmentMotorProperties properties;

    public boolean registerIfFirstTime(String messageId) {
        Boolean inserted = redis.opsForValue().setIfAbsent(
                "appointment:webhook:message:" + messageId,
                "1",
                Duration.ofHours(properties.getWebhookIdempotencyHours()));

        return Boolean.TRUE.equals(inserted);
    }
}
