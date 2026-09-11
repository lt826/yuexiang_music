package com.qxt.yuexiang.ai.dto;

import lombok.Data;

import java.util.List;

/**
 * AI 聊天请求：带最近对话上下文，支持多轮追问
 */
@Data
public class AiChatRequest {

    /** 本轮用户提问 */
    private String message;

    /** 历史对话（按时间正序，服务端截取最近 N 轮防超长） */
    private List<HistoryMessage> history;

    /** 当前登录用户ID（网关透传身份，听歌周报等个人数据工具用） */
    private Long userId;

    @Data
    public static class HistoryMessage {
        /** "user" / "assistant" */
        private String role;
        private String content;
    }
}
