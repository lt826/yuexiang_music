package com.qxt.yuexiang.message.config;

import com.qxt.yuexiang.common.mq.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明（message 服务）：消费歌曲上架事件队列，按粉丝名单发通知。
 */
@Configuration
public class MessageMqConfig {

    @Bean
    public DirectExchange songPublishedExchange() {
        return new DirectExchange(MqConstants.SONG_PUBLISHED_EXCHANGE, true, false);
    }

    @Bean
    public Queue songPublishedQueueMessage() {
        return QueueBuilder.durable(MqConstants.SONG_PUBLISHED_QUEUE_MESSAGE).build();
    }

    @Bean
    public Binding songPublishedQueueMessageBinding() {
        return BindingBuilder.bind(songPublishedQueueMessage()).to(songPublishedExchange())
                .with(MqConstants.SONG_PUBLISHED_ROUTING_KEY);
    }
}
