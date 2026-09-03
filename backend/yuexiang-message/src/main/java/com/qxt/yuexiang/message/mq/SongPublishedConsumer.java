package com.qxt.yuexiang.message.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qxt.yuexiang.common.mq.MqConstants;
import com.qxt.yuexiang.message.service.MessageService;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 歌曲上架事件消费端（message 服务）：按 favorite 服务给的粉丝名单逐个发通知。
 * 单个粉丝发送失败不影响其余粉丝（逐条 try-catch），整体处理完才 ACK。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SongPublishedConsumer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MessageService messageService;

    @RabbitListener(queues = MqConstants.SONG_PUBLISHED_QUEUE_MESSAGE,
            containerFactory = "manualAckContainerFactory")
    public void onSongPublished(Message message, Channel channel) throws IOException {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            Map<?, ?> event = JSON.readValue(body, Map.class);
            @SuppressWarnings("unchecked")
            List<Number> fanIds = (List<Number>) event.get("fanIds");
            Long songId = Long.valueOf(event.get(MqConstants.FIELD_SONG_ID).toString());
            String songName = String.valueOf(event.get(MqConstants.FIELD_SONG_NAME));
            String title = "关注歌手新歌上架";
            String content = "你收藏过的歌手发布了新歌《" + songName + "》，快来听听吧！";

            int sent = 0;
            for (Number fanId : fanIds) {
                try {
                    messageService.send(fanId.longValue(), title, content, "SYSTEM");
                    sent++;
                } catch (Exception e) {
                    // 单点失败容忍：继续给其余粉丝发
                    log.warn("粉丝通知单点失败: fanId={}, {}", fanId, e.getMessage());
                }
            }
            log.info("歌曲上架粉丝通知完成: songId={}, 粉丝={}, 成功={}", songId, fanIds.size(), sent);
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.warn("粉丝通知事件处理失败（重回队列）: {}, {}", body, e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }
}
