package com.study.agent.step01;

import com.study.agent.Agent;
import com.study.llm.ChatModel;
import com.study.llm.Message;
import com.study.llm.Models;
import com.study.llm.OpenAiChatModel;

import java.util.List;

public interface ChatAgent extends Agent {
    class Impl implements ChatAgent {
        private final ChatModel model;

        public Impl(ChatModel model) {
            this.model = model;
        }

        @Override
        public String name() {
            return "Step01 - 对话智能体";
        }

        @Override
        public String chat(String input) {
            return model.chat(List.of(Message.user(input)));
        }
    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();
        System.out.println(new Impl(model).chat("简单介绍一下agent"));
    }
}
