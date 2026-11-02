package com.study.llm;


import java.util.List;

public record Message(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

    public Message(String user, String content) {
        this(user, content, null, null);
    }

    public static Message system(String content) {
        return new Message("system", content);
    }

    public static Message user(String content) {
        return new Message("user", content);
    }

    public static Message assistant(String content) {
        return new Message("assistant", content);
    }

    //assistant消息携带工具调用请求(模型说:请帮我执行这些工具)
    public static Message assistantToolCalls(List<ToolCall> toolCalls) {
        return new Message("assistant", "", toolCalls, null);
    }

    //工具结果回填消息：按tool_call_id对号入座
    public static Message tool(String toolCallId, String content) {
        return new Message("tool", content, null, toolCallId);
    }
}
