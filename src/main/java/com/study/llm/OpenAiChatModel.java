package com.study.llm;

import com.study.util.Json;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
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
            ///     {
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
}
