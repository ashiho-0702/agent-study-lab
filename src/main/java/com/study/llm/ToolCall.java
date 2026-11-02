package com.study.llm;

public record ToolCall(String id, String name, String argumentsJson) {
}
