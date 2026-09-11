package com.qxt.yuexiang.favorite.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qxt.yuexiang.common.mq.MqConstants;
import com.qxt.yuexiang.favorite.entity.FavoriteSong;
import com.qxt.yuexiang.favorite.feign.SingerClient;
import com.qxt.yuexiang.favorite.feign.SongClient;
import com.qxt.yuexiang.favorite.mapper.FavoriteSongMapper;
import com.rabbitmq.client.Channel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 歌曲上架事件消费端（favorite 服务）：
 * 找出收藏过该歌手歌曲的用户（粉丝语义：收藏过该歌手任意作品）+ 关注该歌手的用户，
 * 两路名单合并去重后与歌曲信息打包转发回同交换机的 message 队列，由 message 服务发通知。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SongPublishedConsumer {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final FavoriteSongMapper favoriteSongMapper;
    private final SongClient songClient;
    private final SingerClient singerClient;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = MqConstants.SONG_PUBLISHED_QUEUE_FAVORITE,
            containerFactory = "manualAckContainerFactory")
    public void onSongPublished(Message message, Channel channel) throws IOException {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        try {
            Map<?, ?> event = JSON.readValue(body, Map.class);
            Long singerId = Long.valueOf(event.get(MqConstants.FIELD_SINGER_ID).toString());
            Long songId = Long.valueOf(event.get(MqConstants.FIELD_SONG_ID).toString());
            String songName = String.valueOf(event.get(MqConstants.FIELD_SONG_NAME));

            // 粉丝两步查：歌手全部歌曲ID → 收藏过这些歌曲的用户（去重）
            List<Long> singerSongIds = songClient.idsBySinger(singerId);
            Set<Long> fanIds = new LinkedHashSet<>(singerSongIds.isEmpty() ? List.of()
                    : favoriteSongMapper.selectObjs(new LambdaQueryWrapper<FavoriteSong>()
                            .select(FavoriteSong::getUserId)
                            .in(FavoriteSong::getSongId, singerSongIds)
                            .groupBy(FavoriteSong::getUserId))
                    .stream().map(o -> Long.valueOf(o.toString())).toList());
            // 合并关注该歌手的用户（关注即粉丝；singer 服务不可用时仅通知收藏粉丝）
            try {
                fanIds.addAll(singerClient.followerIds(singerId));
            } catch (Exception e) {
                log.warn("查询歌手关注者失败（降级为仅收藏粉丝）: singerId={}, {}", singerId, e.getMessage());
            }
            if (fanIds.isEmpty()) {
                channel.basicAck(deliveryTag, false);
                return;
            }

            // 转发通知事件到 message 队列（同交换机，message 服务消费自己的队列）
            Map<String, Object> notify = new HashMap<>();
            notify.put(MqConstants.FIELD_TYPE, MqConstants.TYPE_FAN_NOTIFY);
            notify.put(MqConstants.FIELD_SONG_ID, songId);
            notify.put(MqConstants.FIELD_SONG_NAME, songName);
            notify.put("fanIds", List.copyOf(fanIds));
            rabbitTemplate.convertAndSend(MqConstants.SONG_PUBLISHED_EXCHANGE,
                    MqConstants.SONG_PUBLISHED_ROUTING_KEY, JSON.writeValueAsString(notify));
            log.info("歌曲上架粉丝通知转发完成: songId={}, 粉丝数={}", songId, fanIds.size());
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            log.warn("歌曲上架事件处理失败（重回队列）: {}, {}", truncate(body), e.getMessage());
            channel.basicNack(deliveryTag, false, true);
        }
    }

    private String truncate(String s) {
        return s == null ? "" : s.substring(0, Math.min(120, s.length()));
    }
}
