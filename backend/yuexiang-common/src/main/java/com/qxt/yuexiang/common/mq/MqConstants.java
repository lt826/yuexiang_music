package com.qxt.yuexiang.common.mq;

/**
 * RabbitMQ 通道命名与 JSON 事件结构约定（各服务保持一致）。
 *
 * <p>拓扑：交换机 + 每个消费服务独立队列，路由键绑定。消费者端声明自己的队列
 * （声明操作幂等），发布者只声明交换机，跨服务零 Feign 依赖。</p>
 */
public final class MqConstants {

    private MqConstants() {}

    // ===== 播放事件（song 发布 → song/favorite 各自消费） =====

    /** 播放事件交换机（直连） */
    public static final String PLAY_EXCHANGE = "yuexiang.play.exchange";

    /** 路由键 */
    public static final String PLAY_ROUTING_KEY = "play.report";

    /** song 服务消费队列：写 Redis 计数缓冲与热榜 */
    public static final String PLAY_QUEUE_SONG = "yuexiang.play.queue.song";

    /** favorite 服务消费队列：异步落库收听历史 */
    public static final String PLAY_QUEUE_FAVORITE = "yuexiang.play.queue.favorite";

    // ===== 歌曲发布事件（song 审核 → favorite 查粉丝 → message 发通知） =====

    /** 歌曲上架事件交换机 */
    public static final String SONG_PUBLISHED_EXCHANGE = "yuexiang.song.published.exchange";

    /** 路由键 */
    public static final String SONG_PUBLISHED_ROUTING_KEY = "song.published";

    /** favorite 服务消费队列：查收藏该歌手歌曲的用户（粉丝） */
    public static final String SONG_PUBLISHED_QUEUE_FAVORITE = "yuexiang.song.published.queue.favorite";

    /** message 服务消费队列：按粉丝名单发通知 */
    public static final String SONG_PUBLISHED_QUEUE_MESSAGE = "yuexiang.song.published.queue.message";

    // ===== 事件 JSON 字段 =====

    /** 事件类型字段（区分同一队列内的多事件） */
    public static final String FIELD_TYPE = "type";
    /** 用户ID */
    public static final String FIELD_USER_ID = "userId";
    /** 歌曲ID */
    public static final String FIELD_SONG_ID = "songId";
    /** 歌手ID */
    public static final String FIELD_SINGER_ID = "singerId";
    /** 歌名 */
    public static final String FIELD_SONG_NAME = "songName";
    /** 歌手名 */
    public static final String FIELD_SINGER_NAME = "singerName";

    /** 事件类型：播放上报 */
    public static final String TYPE_PLAY = "play";
    /** 事件类型：粉丝通知（favorite → message 转发） */
    public static final String TYPE_FAN_NOTIFY = "fanNotify";
}
