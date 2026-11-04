package com.study.llm;


import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Models {
    private static String baseUrl;
    private static String apiKey;
    private static String modelName;

    // Jev 决策模型（System One）配置：意图路由等结构化判断专用，见 JevModel
    private static String jevUrl;
    private static String jevApiKey;
    private static String jevModel;
    static {
        load();
    }

    private static void load() {
        Properties properties = new Properties();
        Path confPath = Path.of("config.properties");
        try(InputStream in = Files.newInputStream(confPath)) {
            properties.load(in);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        baseUrl = properties.getProperty("base-url");
        apiKey = properties.getProperty("api-key");
        modelName = properties.getProperty("model");
    }

    public static OpenAiChatModel openAiOrNull() {
        return new OpenAiChatModel(apiKey, baseUrl, modelName);
    }

    /** 有真实 Key 则返回 Jev 决策模型，否则返回 null（上层回落 LLM 判断）。 */
    public static JevModel jevOrNull() {
        if (jevApiKey == null || jevApiKey.isBlank()) return null;
        return new JevModel(jevUrl, jevApiKey, jevModel);
    }
}
