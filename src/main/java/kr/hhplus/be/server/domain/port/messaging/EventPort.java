package kr.hhplus.be.server.domain.port.messaging;

public interface EventPort {
    void publish(String topic, Object event);
} 