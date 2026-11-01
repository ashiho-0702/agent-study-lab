package com.study.agent.step03;

import com.study.agent.Agent;
import com.study.llm.ChatModel;
import com.study.llm.Message;
import com.study.llm.Models;
import com.study.llm.OpenAiChatModel;
import com.study.util.Json;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public interface ReActAgent extends Agent {
    class Impl implements ReActAgent {
        private final ChatModel model;
        //它让模型每轮只输出一个 JSON（要么 "我要调工具"，要么 "我给出答案"）
        //ReAct的核心不是代码，而是这份 "协议"
        private static final String SYSTEM_PROMPT = """
                你是一个使用 ReAct 模式解决问题的智能体。
                每一轮指输出一个 JSON 对象，格式二选一:
                1. 继续行动: {"thought": "你的思考", "action":"工具名", "action_input": "参数"}
                2. 给出答案: {"thought": "你的思考", "final":"最终答案"}
                
                可用工具:
                - get_weather(city): 查询城市天气
                - get_time(): 获取当前时间
                不要输出 JSON 以外的内容。
                """;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step03 - ReAct循环";
        }

        @Override
        public String chat(String input) {
            //List.of(...) 不可变，add 直接崩溃
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(SYSTEM_PROMPT));
            messages.add(Message.user(input));
            for (int i = 0; i < 5; i++) {
                String raw = model.chat(messages).trim();
                raw = stripCodeFence(raw);
                Object parsed;
                try {
                    parsed = Json.parse(raw);
                } catch (Exception e) {
                    return raw;
                }
                String answer = Json.str(parsed, "final");
                if (answer != null) {
                    return answer;
                }

                String action = Json.str(parsed, "action");
                String actionInput = Json.str(parsed, "action_input");
                String observation = executeTool(action, actionInput);
                //模型本轮输出
                messages.add(Message.assistant(raw));
                //工具结果
                messages.add(Message.user("observation: " + observation));
                //下一轮模型就能看到自己刚做的行动和观察到的结果，从而继续推理
            }
            return "已达最大步数5，循环终止（生产系统必须有这个保险丝）。";
        }

        private String executeTool(String action, String input) {
            return switch (action == null ? "" : action) {
                case "get_weather" -> MockWeather.query(input);
                case "get_time" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                default -> "未知工具" + action;
            };
        }

        private String stripCodeFence(String s) {
            if (s.startsWith("```")) {
                s = s.replaceFirst("^```\\w*\\s*", "").replaceFirst("```\\s*$", "").trim();
            }
            return s;
        }
    }

    final class MockWeather {
        private final static Map<String, String> DATA = Map.of("湛江", "25~29℃", "肇庆", "22~31℃");

        public static String query(String city) {
            //很多模型在输出 JSON 时，习惯性地包一层 Markdown 代码块
            String key = city == null ? "" : city;
            return DATA.getOrDefault(key, "未收录城市" + city + "数据");
        }
    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();
        System.out.println(new Impl(model).chat("肇庆多少度，现在几点"));
    }

}
