package com.qxt.yuexiang.ai.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * DeepSeek chat/completions 请求体（OpenAI 兼容格式）
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeepseekChatRequest {

    private String model;

    private List<Message> messages;

    /** OpenAI 兼容 Function Calling 工具定义 */
    private List<Tool> tools;

    /** true：由模型自行决定是否调工具 */
    @JsonProperty("tool_choice")
    private String toolChoice;

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Message {
        /** system / user / assistant / tool */
        private String role;
        /** 文本内容；tool 消息为工具执行结果 JSON 字符串，assistant 发起调用时可为 null */
        private String content;
        /** assistant 发起工具调用时的调用列表 */
        @JsonProperty("tool_calls")
        private List<ToolCall> toolCalls;
        /** tool 角色消息必填：对应的 tool_call.id */
        @JsonProperty("tool_call_id")
        private String toolCallId;

        public static Message of(String role, String content) {
            Message m = new Message();
            m.setRole(role);
            m.setContent(content);
            return m;
        }

        public static Message toolResult(String toolCallId, String content) {
            Message m = new Message();
            m.setRole("tool");
            m.setToolCallId(toolCallId);
            m.setContent(content);
            return m;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Tool {
        private String type = "function";
        private Function function;

        public static Tool of(String name, String description, Map<String, Object> parameters) {
            Tool t = new Tool();
            t.function = new Function();
            t.function.setName(name);
            t.function.setDescription(description);
            t.function.setParameters(parameters);
            return t;
        }
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class Function {
        private String name;
        private String description;
        /** JSON Schema 形式的参数定义 */
        private Map<String, Object> parameters;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ToolCall {
        private String id;
        /** 固定 "function" */
        private String type = "function";
        private FunctionCall function;
    }

    @Data
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class FunctionCall {
        private String name;
        /** 参数 JSON 字符串（协议要求 string 而非对象） */
        private String arguments;
    }
}
