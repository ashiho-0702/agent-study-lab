package com.study.llm;

import java.util.List;

// 结果对象
public record ChatResponse(String content, List<ToolCall> toolCalls) {
    // content（最终答案）和 toolCalls（要调用的工具列表）
    public boolean hasToolCalls() {
        // 此方法是告诉你 这轮模型到底是想说话，还是想调工具
        return (toolCalls != null && !toolCalls.isEmpty());
    }

}
