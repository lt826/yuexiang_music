package com.qxt.yuexiang.song.config;

import com.qxt.yuexiang.common.mq.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明（song 服务）：
 * 发布播放事件 + 消费本服务的播放计数队列。
 * 声明操作幂等，服务启动时自动补齐拓扑。
 */
@Configuration
public class SongMqConfig {

    @Bean
    public DirectExchange playExchange() {
        // 持久化交换机，不自动删除
        return new DirectExchange(MqConstants.PLAY_EXCHANGE, true, false);
    }

    /** song 服务自己的播放队列（写计数缓冲与热榜） */
    @Bean
    public Queue playQueueSong() {
        return QueueBuilder.durable(MqConstants.PLAY_QUEUE_SONG).build();
    }

    @Bean
    public Binding playQueueSongBinding() {
        return BindingBuilder.bind(playQueueSong()).to(playExchange())
                .with(MqConstants.PLAY_ROUTING_KEY);
    }
}
