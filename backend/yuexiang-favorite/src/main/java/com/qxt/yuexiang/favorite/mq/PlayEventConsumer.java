package com.qxt.yuexiang.favorite.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qxt.yuexiang.common.mq.MqConstants;
import com.qxt.yuexiang.favorite.entity.ListenHistory;
import com.qxt.yuexiang.favorite.mapper.ListenHistoryMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;

/**
 * 播放事件消费端（favorite 服务）：收听历史异步落库。
 *
 * <p>手动 ACK：落库成功确认；DB 短暂故障时 nack 重回队列稍后重投（削峰填谷），
 * 避免播放高峰期同步写库拖垮接口。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayEventConsumer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final ListenHistoryMapper listenHistoryMapper;

    @RabbitListener(queues = MqConstants.PLAY_QUEUE_FAVORITE,
            containerFactory = "manualAckContainerFactory")
    public void onPlay(org.springframework.amqp.core.Message message, Channel channel)
            throws IOException {
        String messageBody = new String(message.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            Map<?, ?> event = JSON.readValue(messageBody, Map.class);
            Long songId = Long.valueOf(event.get(MqConstants.FIELD_SONG_ID).toString());
            Long userId = Long.valueOf(event.get(MqConstants.FIELD_USER_ID).toString());
            if (userId > 0) {
                // 幂等兜底：与旧同步接口等价（重复播放本就各记一条，无需去重）
                ListenHistory history = new ListenHistory();
                history.setUserId(userId);
                history.setSongId(songId);
                listenHistoryMapper.insert(history);
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 解析失败为毒消息，直接丢弃；DB 异常重回队列延迟重投
            boolean isParseError = e instanceof com.fasterxml.jackson.core.JacksonException
                    || e instanceof NullPointerException
                    || e instanceof NumberFormatException;
            if (isParseError) {
                log.warn("播放事件解析失败（丢弃）: {}, {}", truncate(messageBody), e.getMessage());
                channel.basicAck(deliveryTag, false);
            } else {
                log.warn("收听历史落库失败（重回队列）: {}", e.getMessage());
                channel.basicNack(deliveryTag, false, true);
            }
        }
    }

    private String truncate(String s) {
        return s == null ? "" : s.substring(0, Math.min(120, s.length()));
    }
}
