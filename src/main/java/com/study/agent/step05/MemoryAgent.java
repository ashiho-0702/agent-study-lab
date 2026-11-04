package com.study.agent.step05;

import com.study.agent.Agent;
import com.study.llm.ChatModel;
import com.study.llm.Message;
import com.study.llm.Models;
import com.study.llm.OpenAiChatModel;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public interface MemoryAgent extends Agent {
    class Impl implements MemoryAgent {
        private final ChatModel model;
        //短期记忆条数上限
        private static final int MAX_SHOT_MESSAGES = 4;
        //长期记忆存在哪个文件
        private final Path file;
        //短期记忆
        private final List<Message> shortMemory = new ArrayList<>();
        //长期记忆
        private final Map<String, String> database = new LinkedHashMap<>();
        //压缩摘要
        private String summary = "";

        public Impl(ChatModel model, Path file) {
            this.model = model;
            this.file = file;
            loadFile();
        }

        public Impl(ChatModel model) {
            this(model, Path.of("runtime", "step05-memory.properties"));
        }

        //把磁盘的记忆加载到内存里
        private void loadFile() {
            Properties properties = new Properties();
            if (Files.exists(file)) {
                try (InputStream in = Files.newInputStream(file)) {
                    properties.load(in);
                    for (String key : properties.stringPropertyNames()) {
                        if (key.startsWith("db.")) {
                            //去掉前缀，存回 database
                            database.put(key.substring(3), properties.getProperty(key));
                        }
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("读取长期记忆失败", e);
                }
            }
        }

        //把长期记忆持久化到磁盘
        private void saveFile() {
            try {
                //确保目录存在，不存在就创建
                Files.createDirectories(file.getParent());
                //Properties是键值对容器
                //为什么用Properties而不是用普通的Map去写入写出文件
                //因为它额外自带store()/load()方法，而Map没有
                Properties properties = new Properties();
                //加上前缀，存到 properties
                database.forEach((key, value) -> properties.setProperty("db." + key, value));
                try (OutputStream out = Files.newOutputStream(file)) {
                    //写进文件
                    properties.store(out, "Step05 long-term memory");
                }
            } catch (IOException e) {
                throw new IllegalStateException("写入长期记忆失败", e);
            }
        }

        @Override
        public String name() {
            return "Step05 - 上下文记忆";
        }

        @Override
        public String chat(String input) {
            //加入短期记忆
            shortMemory.add(Message.user(input));
            //超了就压缩老记忆
            if (shortMemory.size() > MAX_SHOT_MESSAGES) {
                compress();
            }
            //按关键词查长期记忆
            String databaseMemory = recallDatabase(input);
            //拼装完整上下文
            List<Message> context = buildContext(databaseMemory);
            //拿上下文问模型
            String answer = model.chat(context);
            //回答进短期记忆
            shortMemory.add(Message.assistant(answer));
            //长期记忆写回磁盘
            saveFile();
            return answer;
        }

        //拼接上下文
        private List<Message> buildContext(String databaseMemory) {
            List<Message> context = new ArrayList<>();
            context.add(Message.system("""
                    你是一个有记忆的助手。回答时优先使用“记忆区”里的事实。
                    如果记忆里没有，就明确说不知道，不要编造。
                    """));
            //文件记忆
            context.add(Message.system("【长期记忆/文件】\n" + readMemoryText()));
            //数据库召回
            context.add(Message.system("【结构记忆/数据库】\n" + databaseMemory));
            //摘要
            if (!summary.isEmpty()) {
                context.add(Message.system("【压缩摘要】\n" + summary));
            }
            //短期记忆
            context.addAll(shortMemory);
            return context;
        }

        private String readMemoryText() {
            if (database.isEmpty()) return "(空)";
            return String.join("\n", database.keySet().stream().map(key -> key + " = " + database.get(key)).toList());
        }

        //关键词召回
        private String recallDatabase(String input) {
            String key;
            if (input.contains("咖啡")) key = "coffee";
            else if (input.contains("项目")) key = "project";
            else if (input.contains("名字")) key = "name";
            else key = "";

            if (key.isBlank() || !database.containsKey(key)) {
                return "没有命中";
            }
            return key + " = " + database.get(key);
        }

        //压缩
        private void compress() {
            //把最老的一轮对话（一问一答）压缩成一行摘要存进 summary
            Message question = shortMemory.remove(0);
            Message answer = shortMemory.remove(0);
            //截断到 28 字符防止摘要过长
            String digest = clip(question.content()) + " -> " + clip(answer.content());
            summary = summary.isEmpty() ? digest : summary + ";" + digest;
        }

        private String clip(String text) {
            //多空白→单空格，去首尾
            String value = text.replaceAll("\\s+", " ").trim();
            return value.length() > 28 ? value.substring(0, 28) + "..." : value;
        }

        public void rememberInDatabase(String key, String value) {
            database.put(key, value);
            saveFile();
        }
    }

    public static void main(String[] args) {
        OpenAiChatModel model = Models.openAiOrNull();
        Impl agent = new Impl(model);
        agent.rememberInDatabase("name", "小傅哥");
        agent.rememberInDatabase("coffee", "冰拿铁");
        agent.rememberInDatabase("project", "ToyAgent");

        String[][] script = {
                {"我叫小傅哥，记住我的名字", "第一轮：名字写入数据库记忆"},
                {"我最近在做 ToyAgent ", "第二轮：项目事实进入短期记忆"},
                {"我想优化上下文压缩", "第三轮：触发滑动窗口压缩"},
                {"新会话测试：我平时喝什么咖啡", "第四轮：短期记忆已重置，仍查询长期记忆"},
                {"那我叫什么名字，在做什么项目？", "第五轮：一次回答引入结构记忆"},
        };

        for (int i = 0; i < script.length; i++) {
            if (i == 3) {
                agent.reset();
                System.out.println("\n[操作] 模拟用户重新打开一个会话（reset 短期记忆）");
            }

            String[] step = script[i];
            System.out.println("\n[输入] " + step[0]);
            System.out.println("[输出] " + agent.chat(step[0]));
            System.out.println("[说明] " + step[1]);
        }
    }
}
