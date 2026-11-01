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
}
