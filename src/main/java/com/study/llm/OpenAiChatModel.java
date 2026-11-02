package com.study.llm;

import com.study.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class OpenAiChatModel implements ChatModel {
    private final String apiKey;
    private final String baseUrl;
    private final String model;
    //发请求的客户端，长期存在可复用
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15)) //从发请求，到连接服务器的时间；针对的故障：连不上
            .build();

    // http://127.0.0.1:8777/，http://127.0.0.1:8777
    // 后面的斜线不一定有
    public OpenAiChatModel(String apiKey, String baseUrl, String model) {
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.replaceAll("/+$", "");
        this.model = model;
    }

    @Override
    public String chat(List<Message> messages) {
        // openAI文档：https://openai.apifox.cn/api-67883981，查看body参数
        // LinkedHashMap会保持你 put 的顺序，而HashMap会打乱顺序
        LinkedHashMap<Object, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages.stream()
                //逻辑是把 List<Message> 转成 List<Map>
                //因为手写 JSON 工具不认识Message，只知道Map/List
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList()
        );
        body.put("temperature", 0.7);
        //请求，一次性
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                //从发请求，到收到完整响应体的时间；针对的故障：连得上但响应慢
                //所以connectTimeout是timeout的子集
                .timeout(Duration.ofSeconds(100))
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();
        try {
            //把响应体当作字符串来接收
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("模型调用失败：" + response.statusCode() + ":" + response.body());
            }
            /// {
            ///   "id": "chatcmpl-123",
            ///   "model": "kilo-auto/free",
            ///   "choices": [
            ///{
            ///       "message": {
            ///         "role": "assistant",
            ///         "content": "Agent 是一种能自主完成任务的人工智能..."
            ///       }
            ///     }
            ///   ]
            /// }

            //解析响应体，解析成Map集合
            Object root = Json.parse(response.body());
            Object choices = ((Map<?, ?>) root).get("choices");
            //choices 是数组，取第1个
            Object first = ((List<?>) choices).get(0);
            Object message = ((Map<?, ?>) first).get("message");
            return String.valueOf(((Map<?, ?>) message).get("content"));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public ChatResponse chat(List<Message> messages, List<Map<String, Object>> tools) {
        //组装请求体
        LinkedHashMap<Object, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        /*body.put("messages", messages.stream()
                .map(m -> Map.of("role", m.role(), "content", m.content()))
                .toList()
        );
        以前 Message 只有 role + content，转 JSON 一行就够；
        现在加上了带 tool_calls 的 assistant 消息、多输出一个 tool_call_id 的 tool 消息
        改用toWire方法
        */
        body.put("messages", messages.stream().map(OpenAiChatModel::toWire).toList());
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", tools);
        }
        body.put("temperature", 0.7);
        //请求
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/chat/completions"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                //从发请求，到收到完整响应体的时间；针对的故障：连得上但响应慢
                //所以connectTimeout是timeout的子集
                .timeout(Duration.ofSeconds(100))
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();
        try {
            //把响应体当作字符串来接收
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalArgumentException("模型调用失败：" + response.statusCode() + ":" + response.body());
            }
            //解析响应体，解析成Map集合
            Object root = Json.parse(response.body());
            Object message = ((Map<?, ?>) ((List<?>) ((Map<?, ?>) root).get("choices")).get(0)).get("message");
            Object contentObj = ((Map<?, ?>) message).get("content");
            String content = contentObj == null ? "" : contentObj.toString();
            ArrayList<ToolCall> calls = new ArrayList<>();

            Object rawCalls = ((Map<?, ?>) message).get("tool_calls");
            if (rawCalls instanceof List<?> list) {
                for (Object item : list) {
                    Map<?, ?> call = (Map<?, ?>) item;
                    Map<?, ?> function = (Map<?, ?>) call.get("function");
                    calls.add(new ToolCall(
                            String.valueOf(call.get("id")),
                            Json.str(function, "name"),
                            Json.str(function, "arguments")
                    ));
                }
            }
            //把可变的 ArrayList 转成不可变列表返回
            return new ChatResponse(content, List.copyOf(calls));
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    //把 Message 转成协议JSON 的序列化器
    private static Map<String, Object> toWire(Message m) {
        Map<String, Object> wire = Json.obj();
        wire.put("role", m.role());
        // 是带工具申请的 assistant？
        if (m.toolCalls() != null && !m.toolCalls().isEmpty()) {
            wire.put("tool_calls", m.toolCalls().stream().map(tc -> {
                Map<String, Object> fn = Json.obj();
                fn.put("name", tc.name());
                fn.put("arguments", tc.argumentsJson());

                Map<String, Object> call = Json.obj();
                call.put("id", tc.id());
                call.put("type", "function");
                call.put("function", fn);

                return call;
            }).toList());

            if (m.content() != null && !m.content().isBlank()) {
                wire.put("content", m.content());
            }
        } else { // 普通消息 或 tool 消息
            wire.put("content", m.content() == null ? "" : m.content());

            if ("tool".equals(m.role())) { // 是 tool 消息？
                // 加 tool_call_id 指向某次调用
                wire.put("tool_call_id", m.toolCallId());
            }
        }
        return wire;
    }

}
