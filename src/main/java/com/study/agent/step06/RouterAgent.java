package com.study.agent.step06;

import com.study.agent.Agent;
import com.study.agent.step03.ReActAgent;
import com.study.llm.ChatModel;
import com.study.llm.Message;
import com.study.llm.Models;
import com.study.llm.OpenAiChatModel;
import com.study.util.Json;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface RouterAgent extends Agent {
    class Impl implements RouterAgent {
        private final ChatModel model;
        private static final String ROUTER_PROMPT = """
                你是意图识别路由器。分析用户输入，只输出 JSON：
                {"intent": "weather|math|chat", "reason": "一句话理由"}
                - weather: 查询天气
                - math: 四则运算
                - chat: 其他一切的对话
                """;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step06 - 意图识别";
        }

        @Override
        public String chat(String input) {
            String raw = model.chat(List.of(Message.system(ROUTER_PROMPT),
                    Message.user(input))).trim();
            Object decision = tryParse(raw);
            String intent = Json.str(decision, "intent");
            if (null == intent) intent = "chat";
            return switch (intent) {
                case "weather" -> {
                    String city = extractCity(input);
                    String result = ReActAgent.MockWeather.query(city);
                    //在switch表达式里，每个分支用yield返回一个值
                    yield " [" + city + " ] 现在" + result + "。";
                }
                case "math" -> {
                    yield "计算结果：" + calc(input) + "。";
                }
                default -> {
                    yield model.chat(List.of(Message.system("你是一个友好的中文聊天助手"), Message.user(input)));
                }
            };
        }

        private String extractCity(String text) {
            for (String city : List.of("湛江", "上海", "广州", "深圳", "杭州", "成都")) {
                if (text.contains(city)) return city;
            }
            return "北京";
        }

        private String calc(String expr) {
            Matcher m = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([+\\-*/])\\s*(\\d+(?:\\.\\d+)?)").matcher(expr);
            if (!m.find()) return "未识别的算式";
            double a = Double.parseDouble(m.group(1));
            double b = Double.parseDouble(m.group(3));
            double r = switch (m.group(2)) {
                case "+" -> a + b;
                case "-" -> a - b;
                case "*" -> a * b;
                case "/" -> b == 0 ? Double.NaN : a / b;
                default -> Double.NaN;
            };
            return Double.isNaN(r) ? "无法计算" : (r == Math.floor(r) ? String.valueOf((long) r) : String.valueOf(r));
        }

        private Object tryParse(String raw) {
            try {
                return Json.parse(raw.replaceFirst("^```\\w*\\s*", "").replaceFirst("```\\s*$", "").trim());
            } catch (Exception e) {
                return Json.obj();
            }
        }
    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();
        Impl impl = new Impl(model);
        System.out.println(impl.chat("写一首诗"));
        System.out.println(impl.chat("湛江天气"));
    }
}
