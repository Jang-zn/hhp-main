package kr.hhplus.be.server.domain.port.messaging;

public interface MessagingPort {
    /**
 * Publishes an event to the specified topic synchronously.
 *
 * @param topic the name of the topic to which the event will be published
 * @param event the event object to publish
 */
void publish(String topic, Object event);
    /**
 * Publishes an event to the specified topic asynchronously.
 *
 * @param topic the name of the topic to which the event will be published
 * @param event the event object to publish
 */
void publishAsync(String topic, Object event);
} 