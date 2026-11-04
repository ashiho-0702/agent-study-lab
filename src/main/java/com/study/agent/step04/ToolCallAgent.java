package com.study.agent.step04;

import com.study.agent.Agent;
import com.study.agent.step03.ReActAgent;
import com.study.llm.*;
import com.study.util.Json;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface ToolCallAgent extends Agent {
    class Impl implements ToolCallAgent {

        private final ChatModel model;

        private static final String SYSTEM_PROMPT = """
                你是一个可以调用工具的智能体。需要实时数据或精确计算时调用工具，
                拿到工具结果后再用自然语言回答用户。
                """;

        private static Map<String, Object> tool(String name, String desc, Map<String, Object> params) {
            // 声明function
            Map<String, Object> fn = Json.obj();
            fn.put("name", name);
            fn.put("description", desc);
            fn.put("parameters", params);
            // 声明tool
            Map<String, Object> schema = Json.obj();
            schema.put("type", "function");
            schema.put("function", fn);

            return schema;
        }

        // 定义工具集
        private static final List<Map<String, Object>> TOOLS = List.of(
                tool("get_weather", "查询指定城市的实时天气",
                        Map.of("type", "object", "properties",
                                Map.of("city", Map.of("type", "string", "description", "城市名，如：湛江")),
                                "required", List.of("city"))),
                tool("get_time", "获取当前时间",
                        Map.of("type", "object", "properties", Map.of()))
        );

        private static final int MAX_ROUND = 5;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step04 - FunctionCalling";
        }

        @Override
        public String chat(String input) {
            List<Message> messages = new ArrayList<>();
            messages.add(Message.system(SYSTEM_PROMPT));
            messages.add(Message.user(input));
            for (int step = 1; step <= MAX_ROUND; step++) {
                ChatResponse reply = model.chat(messages, TOOLS);
                if (!reply.hasToolCalls()) {
                    return reply.content();
                }
                //这样下一轮模型能看到 我这一轮调了这些工具
                messages.add(Message.assistantToolCalls(reply.toolCalls()));//发起
                //模型一次可能要求调多个工具，所以用循环
                for (ToolCall call : reply.toolCalls()) {
                    //执行工具
                    String result = execute(call.name(), call.argumentsJson());
                    //循环里逐个对每个 call.id 回填对应的 tool 结果
                    messages.add(Message.tool(call.id(), result));//响应，前面有发起就要有响应
                }

            }
            return "已达最大工具轮数（" + MAX_ROUND + "），为防失控提前终止。";
        }

        private String execute(String tool, String argsJson) {
            try {
                Object args = Json.parse(argsJson == null ? "{}" : argsJson);
                return switch (tool) {
                    case "get_weather" -> ReActAgent.MockWeather.query(Json.str(args, "city"));
                    case "get_time" -> LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                    default -> "未知工具：" + tool;
                };
            } catch (Exception e) {
                return "工具执行失败：" + e.getMessage();
            }
        }

    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();
        System.out.println(new Impl(model).chat("帮我查一下湛江天气，然后推荐穿搭"));
    }
}

