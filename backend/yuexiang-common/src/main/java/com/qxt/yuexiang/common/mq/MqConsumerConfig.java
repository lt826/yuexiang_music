package com.qxt.yuexiang.common.mq;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 公共 RabbitMQ 消费配置：手动 ACK 容器工厂。
 *
 * <p>手动 ACK 语义：处理成功才确认，消费端宕机消息自动重投（至少一次投递）；
 * 业务处理内部 try-catch 吞掉可容忍错误后正常 ack，避免毒消息无限重投。</p>
 *
 * <p>仅当 classpath 存在 AMQP 时生效（gateway/ai/file/admin 无 MQ 依赖，不加载）。</p>
 */
@Configuration
@ConditionalOnClass(SimpleRabbitListenerContainerFactory.class)
public class MqConsumerConfig {

    @Bean(name = "manualAckContainerFactory")
    public SimpleRabbitListenerContainerFactory manualAckContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        // 单条确认保证不丢；并发 2~5 平滑消费
        factory.setPrefetchCount(5);
        factory.setConcurrentConsumers(2);
        factory.setMaxConcurrentConsumers(5);
        return factory;
    }
}
