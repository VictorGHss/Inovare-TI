package br.dev.ctrls.inovareti.domain.appointment;

import java.time.Duration;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@ConditionalOnBean(StringRedisTemplate.class)
public class AppointmentSendIdempotencyService {

    private final StringRedisTemplate redis;
    private final AppointmentMotorProperties properties;

    public boolean registerIfFirstSend(String appointmentId) {
        Boolean inserted = redis.opsForValue().setIfAbsent(
                "appointment:send:idempotency:" + appointmentId,
                "1",
                Duration.ofHours(properties.getSendIdempotencyHours()));

        return Boolean.TRUE.equals(inserted);
    }
}
