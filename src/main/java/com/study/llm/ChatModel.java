package com.study.llm;

import java.util.List;
import java.util.Map;

public interface ChatModel {
    //为什么是List集合，因为要维护历史记录
    String chat(List<Message> messages);

    default ChatResponse chat(List<Message> messages, List<Map<String, Object>> tools) {
        return new ChatResponse(chat(messages), List.of());
    }
}
