package com.qxt.yuexiang.song.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qxt.yuexiang.common.mq.MqConstants;
import com.qxt.yuexiang.song.service.SongRedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 播放事件 MQ 层：发布端 + song 服务本地消费端。
 *
 * <p>reportPlay 由「同步写库」改为「发一条消息」，由各消费端自行处理：
 * song 队列写 Redis 计数/热榜，favorite 队列异步落库收听历史。
 * MQ 不可用时降级为直接写 Redis（播放计数不丢）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlayEventPublisher {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final RabbitTemplate rabbitTemplate;
    private final SongRedisService songRedisService;

    /**
     * 发布播放事件；MQ 故障时降级为直接写 Redis 计数（不阻塞播放主流程）。
     */
    public void publishPlay(Long songId, Long userId) {
        try {
            Map<String, Object> event = new HashMap<>();
            event.put(MqConstants.FIELD_TYPE, MqConstants.TYPE_PLAY);
            event.put(MqConstants.FIELD_SONG_ID, songId);
            event.put(MqConstants.FIELD_USER_ID, userId == null ? 0L : userId);
            rabbitTemplate.convertAndSend(
                    MqConstants.PLAY_EXCHANGE, MqConstants.PLAY_ROUTING_KEY, JSON.writeValueAsString(event));
        } catch (Exception e) {
            log.warn("播放事件发送失败，降级直写 Redis: songId={}, {}", songId, e.getMessage());
            songRedisService.recordPlay(songId);
        }
    }

    /** song 服务消费端：写 Redis 计数缓冲 + 实时/日榜 */
    @org.springframework.amqp.rabbit.annotation.RabbitListener(
            queues = MqConstants.PLAY_QUEUE_SONG,
            containerFactory = "manualAckContainerFactory")
    public void onPlay(org.springframework.amqp.core.Message raw, com.rabbitmq.client.Channel channel)
            throws java.io.IOException {
        String message = new String(raw.getBody(), java.nio.charset.StandardCharsets.UTF_8);
        long deliveryTag = raw.getMessageProperties().getDeliveryTag();
        try {
            Map<?, ?> event = JSON.readValue(message, Map.class);
            Long songId = Long.valueOf(event.get(MqConstants.FIELD_SONG_ID).toString());
            songRedisService.recordPlay(songId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // 解析/处理失败仅记日志（计数允许偏差），消息被确认丢弃，不无限重投
            log.warn("播放事件消费失败（丢弃）: {}, {}", truncate(message), e.getMessage());
            channel.basicAck(deliveryTag, false);
        }
    }

    /**
     * 歌曲上架事件：favorite 消费端查粉丝名单，message 消费端发通知。
     * 调用方已 try-catch，这里仅转换异常语义。
     */
    public void publishSongPublished(Long songId, Long singerId, String songName) throws Exception {
        Map<String, Object> event = new HashMap<>();
        event.put(MqConstants.FIELD_SONG_ID, songId);
        event.put(MqConstants.FIELD_SINGER_ID, singerId);
        event.put(MqConstants.FIELD_SONG_NAME, songName);
        rabbitTemplate.convertAndSend(MqConstants.SONG_PUBLISHED_EXCHANGE,
                MqConstants.SONG_PUBLISHED_ROUTING_KEY, JSON.writeValueAsString(event));
    }

    private String truncate(String s) {
        return s == null ? "" : s.substring(0, Math.min(120, s.length()));
    }
}
