package com.study.llm;

import com.study.util.Json;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class JevModel {

    /**
     * 一次 choice 判断的结果：选中项 + 置信度 + 概率分布。
     * probabilities 按选项名归一化，可用于展示"它对每个选项各有多确定"。
     */
    public record Decision(String choice, double confidence, Map<String, Double> probabilities) {
    }

    private final String url;
    private final String apiKey;
    private final String model;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    public JevModel(String url, String apiKey, String model) {
        this.url = url.replaceAll("/+$", "");
        this.apiKey = apiKey;
        this.model = model;
    }

    public Map<String, Object> evaluate(String state, Map<String, Object> questions) {
        Map<String, Object> body = Json.obj();
        body.put("model", model);
        body.put("state", state);
        body.put("questions", questions);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(Json.write(body)))
                .build();

        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new IllegalStateException("Jev 决策调用失败 HTTP " + response.statusCode() + ": "
                        + snippet(response.body()));
            }
            Object root = Json.parse(response.body());
            Object answers = ((Map<?, ?>) root).get("answers");
            if (!(answers instanceof Map<?, ?> a)) {
                throw new IllegalStateException("Jev 响应缺少 answers 字段");
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) a;
            return result;
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Jev 决策调用异常: " + e.getMessage(), e);
        }
    }

    /**
     * choice 题型的便捷方法：一次判断"N 选一"。
     * <p>
     * criteria 即 {选项名: 选项说明} 的闭集——说明就是判据，改描述即可
     * 改善判断质量（"描述即路由"），零代码。返回带置信度与概率分布的
     * {@link Decision}，交由上层做置信度门控。
     */
    public Decision choice(String state, String questionName, String instructions,
                           Map<String, Object> criteria) {
        Map<String, Object> question = Json.obj();
        question.put("type", "choice");
        question.put("instructions", instructions);
        question.put("criteria", criteria);

        Map<String, Object> questions = Json.obj();
        questions.put(questionName, question);

        Object answer = evaluate(state, questions).get(questionName);
        if (!(answer instanceof Map<?, ?> a)) {
            throw new IllegalStateException("Jev 响应缺少问题 " + questionName + " 的答案");
        }
        String choice = Json.str(answer, "choice");
        if (choice == null) {
            throw new IllegalStateException("answers." + questionName + " 缺少 choice");
        }
        double confidence = num(a.get("confidence"));

        Map<String, Double> probabilities = new LinkedHashMap<>();
        Object probs = a.get("probabilities");
        if (probs instanceof Map<?, ?> p) {
            for (Map.Entry<?, ?> e : p.entrySet()) {
                probabilities.put(String.valueOf(e.getKey()), num(e.getValue()));
            }
        }
        return new Decision(choice, confidence, probabilities);
    }

    private static double num(Object v) {
        return v instanceof Number n ? n.doubleValue() : 0.0;
    }

    private String snippet(String s) {
        if (s == null) return "";
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
