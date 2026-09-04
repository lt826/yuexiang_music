package com.qxt.yuexiang.favorite.config;

import com.qxt.yuexiang.common.mq.MqConstants;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 拓扑声明（favorite 服务）：
 * 消费播放事件队列（异步落库收听历史）+ 歌曲发布事件队列（查粉丝）。
 * 声明操作幂等，服务启动时自动补齐拓扑。
 */
@Configuration
public class FavoriteMqConfig {

    @Bean
    public DirectExchange playExchange() {
        return new DirectExchange(MqConstants.PLAY_EXCHANGE, true, false);
    }

    @Bean
    public Queue playQueueFavorite() {
        return QueueBuilder.durable(MqConstants.PLAY_QUEUE_FAVORITE).build();
    }

    @Bean
    public Binding playQueueFavoriteBinding() {
        return BindingBuilder.bind(playQueueFavorite()).to(playExchange())
                .with(MqConstants.PLAY_ROUTING_KEY);
    }

    @Bean
    public DirectExchange songPublishedExchange() {
        return new DirectExchange(MqConstants.SONG_PUBLISHED_EXCHANGE, true, false);
    }

    @Bean
    public Queue songPublishedQueueFavorite() {
        return QueueBuilder.durable(MqConstants.SONG_PUBLISHED_QUEUE_FAVORITE).build();
    }

    @Bean
    public Binding songPublishedQueueFavoriteBinding() {
        return BindingBuilder.bind(songPublishedQueueFavorite()).to(songPublishedExchange())
                .with(MqConstants.SONG_PUBLISHED_ROUTING_KEY);
    }
}
