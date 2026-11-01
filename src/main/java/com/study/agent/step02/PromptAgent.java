package com.study.agent.step02;

import com.study.agent.Agent;
import com.study.llm.ChatModel;
import com.study.llm.Message;
import com.study.llm.Models;
import com.study.llm.OpenAiChatModel;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public interface PromptAgent extends Agent {
    class Impl implements PromptAgent {
        private final ChatModel model;
        List<Map<String, Object>> lastTrace = new CopyOnWriteArrayList<>();
        private static final String SYSTEM_PROMPT = """
                # 角色
                你是技术助理，专注讲清楚ai agent的原理和工程实践。
                
                # 背景
                读者是正在学习智能体开发的Java工程师,熟悉后端技术,但对AI领域的新手。
                
                # 任务与约束
                1.回答使用简体中文，口语化但专业。
                2.先给结论，再展开解释；涉及概念时给出生活化类比。
                3.若用户要求“用一句话”，必须严格只回答一句话。
                4.不确定的内容要如实说明，禁止编造。
                """;


        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step02 - 提示词工程";
        }

        @Override
        public String chat(String input) {
            lastTrace.clear();
            return model.chat(List.of(
                    Message.system(SYSTEM_PROMPT),
                    Message.user(input)
            ));
        }

        public static void main(String[] args) {
            OpenAiChatModel model = Models.openAiOrNull();
            System.out.println(new Impl(model).chat("介绍一下提示词工程"));
        }
    }
}
