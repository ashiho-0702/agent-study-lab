package com.study.llm;

import java.util.List;

public interface ChatModel {
    //为什么是List集合，因为要维护历史记录
    String chat(List<Message> messages);
}
