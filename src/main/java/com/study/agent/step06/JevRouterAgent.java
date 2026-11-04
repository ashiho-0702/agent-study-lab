package com.study.agent.step06;

import com.study.agent.Agent;
import com.study.agent.step03.ReActAgent;
import com.study.llm.*;
import com.study.util.Json;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public interface JevRouterAgent extends Agent {
    class Impl implements JevRouterAgent {
        /**
         * 置信度门控阈值：Jev 的 confidence 达到它才"自动执行"，否则回落 LLM 判断。
         * 阈值不该拍脑袋——拿自己的真实输入标定后再锁死（教学演示用 0.75）。
         */
        double CONFIDENCE_GATE = 0.75;

            /** 兜底判断 + 闲聊对话仍走聊天大模型——Jev 管判断，LLM 管生成。 */
            private final ChatModel model;
            /** Jev 决策端口；null 表示未配置 key，全部判断回落 LLM。 */
            private final JevModel jev;

            /** LLM 兜底判断用的提示词——与 RouterAgent 相同口径，回落时判断结果口径一致。 */
            private static final String FALLBACK_PROMPT = """
                你是意图识别路由器。分析用户输入，只输出 JSON：
                {"intent": "weather|math|chat", "reason": "一句话理由"}
                - weather: 查询天气
                - math: 四则运算
                - chat: 其他一切对话
                """;

            public Impl(ChatModel model) {
                this(model, Models.jevOrNull());
            }

            Impl(ChatModel model, JevModel jev) {
                this.model = model;
                this.jev = jev;
            }

            @Override
            public String name() {
                return "Step06+ · Jev 决策模型路由（System One）";
            }

            @Override
            public String chat(String input) {
                // 1. 意图判断：优先走 Jev 决策端口，失败或低置信时回落 LLM
                JevModel.Decision decision = null;
                String jevError = null;
                if (jev == null) {
                    jevError = "未配置 jev-api-key";
                } else {
                    try {
                        decision = jevDecide(input);
                    } catch (Exception e) {
                        jevError = "Jev 调用失败：" + e.getMessage();
                    }
                }
                String intent;
                if (decision != null) {
                    // 2. 置信度门控：答案告诉你"是什么"，置信度告诉你"该不该动手"
                    if (decision.confidence() >= CONFIDENCE_GATE) {
                        // 置信度高，直接用 Jev 的判断
                        intent = decision.choice();
                    } else {
                        // 置信度低，回落 LLM 判断
                        intent = llmJudge(input);
                    }
                } else {
                    // 决策端口故障/未配置 → 回落原路径，不新增失败面
                    intent = llmJudge(input);
                }
                // 3. 按意图路由到处理器 —— 与 RouterAgent 相同的分发骨架
                return switch (intent) {
                    case "weather" -> {
                        String city = extractCity(input);
                        String result = ReActAgent.MockWeather.query(city);
                        yield "「" + city + "」现在" + result + "。";
                    }
                    case "math" -> {
                        yield "计算结果：" + calc(input) + "。";
                    }
                    default -> {
                        yield model.chat(List.of(
                                Message.system("你是一个友好的中文聊天助手。"),
                                Message.user(input)));
                    }
                };
            }

            /**
             * 调 Jev 决策端口做意图判断：choice 题型，闭集三选一。
             * <p>
             * criteria 就是判据——"描述即路由"，改描述即可改善判断质量，零代码。
             * 实际生产里还可以在同一次请求里打包"要不要写权限（noul）、难度分档（score）"
             * 等更多问题（见 {@link JevModel#evaluate}），加问题几乎不增加耗时。
             */
            private JevModel.Decision jevDecide(String input) {
                Map<String, Object> criteria = Json.obj();
                criteria.put("weather", "查询天气");
                criteria.put("math", "四则运算或计算");
                criteria.put("chat", "其他一切对话");
                return jev.choice(input, "intent", "用户想让助手做什么？", criteria);
            }

            /** LLM 兜底判断：口径与 RouterAgent 完全一致（猜 JSON + 解析失败落 chat）。 */
            private String llmJudge(String input) {
                String raw = model.chat(List.of(
                        Message.system(FALLBACK_PROMPT),
                        Message.user(input))).trim();
                String intent = null;
                try {
                    Object parsed = Json.parse(raw
                            .replaceFirst("^```\\w*\\s*", "")
                            .replaceFirst("```\\s*$", "")
                            .trim());
                    intent = Json.str(parsed, "intent");
                } catch (Exception ignored) {
                }

                return intent == null ? "chat" : intent;
            }

            // ------------------------------------------------------------- 处理器（与 RouterAgent 相同）

            private String extractCity(String text) {
                for (String city : List.of("北京", "上海", "广州", "深圳", "杭州", "成都")) {
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


    }

    /**
     * 本地直接测试入口：
     * java -cp target/classes cn.xiaofuge.ai.agent.step06.JevRouterAgent [你的问题]
     * <p>
     * Jev 配置来自 config.properties（jev-url / jev-api-key / jev-model），
     * 未配置 key 时自动回落 LLM 判断并打印提示。
     */
    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();
        Impl impl = new Impl(model);
        System.out.println(impl.chat("写一首诗"));
        System.out.println(impl.chat("北京天气"));
    }
}
