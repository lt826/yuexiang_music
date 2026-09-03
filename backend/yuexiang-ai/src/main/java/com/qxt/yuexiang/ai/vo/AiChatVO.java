package com.qxt.yuexiang.ai.vo;

import lombok.Data;

import java.util.List;

/**
 * AI 聊天响应：自然语言回答 + 命中的歌曲列表（前端渲染可点击播放）
 */
@Data
public class AiChatVO {

    /** DeepSeek 生成的回答文本 */
    private String reply;

    /** 回答附带的歌曲列表（工具查询的真实库内数据，前端可直接播放） */
    private List<AiSongVO> songs;

    /** 本轮对话中 AI 新建的歌单 ID（create_playlist 工具成功时回填，前端渲染跳转卡片） */
    private Long createdPlaylistId;

    /** 新建歌单的名称（前端卡片展示用） */
    private String createdPlaylistTitle;
}
