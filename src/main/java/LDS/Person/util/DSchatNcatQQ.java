package LDS.Person.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.HashMap;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * DeepSeek API Java 客户端模板（非流式 chat/completions）
 * 使用 Java 11+ HttpClient 和 Jackson
 * 支持本地上下文管理：每个用户最多保存5条消息
 *
 * 环境变量：DEEPSEEK_API_KEY
 */

public class DSchatNcatQQ {
    private static final String BASE_URL = "https://api.deepseek.com";//  https://api.deepseek.com/v3.2_speciale_expires_on_20251215
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final int MAX_HISTORY = 15; // 最多保存15条消息（节省token）
    private static final Map<String, ArrayNode> conversationHistory = new ConcurrentHashMap<>();
    // 用于存储用户昵称的映射：userId -> nickname
    private static final Map<String, String> userNicknameMap = new ConcurrentHashMap<>();

    private final HttpClient client;
    private final String apiKey;

    public DSchatNcatQQ(String apiKey) {
        this.apiKey = apiKey;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .build();
    }

    /**
     * 获取或创建用户的对话历史
     */
    private static ArrayNode getOrCreateHistory(String userId) {
        return conversationHistory.computeIfAbsent(userId, k -> mapper.createArrayNode());
    }

    /**
     * 添加消息到用户历史，保持最多 MAX_HISTORY 条消息
     */
    private static void addMessageToHistory(String userId, String role, String content) {
        ArrayNode history = getOrCreateHistory(userId);
        ObjectNode message = mapper.createObjectNode();
        message.put("role", role);
        message.put("content", content);
        history.add(message);

        // 保持历史记录不超过 MAX_HISTORY 条
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }

    /**
     * 记录用户昵称
     * 
     * @param userId   用户ID
     * @param nickname 用户昵称
     */
    public static void setUserNickname(String userId, String nickname) {
        userNicknameMap.put(userId, nickname);
    }

    /**
     * 获取用户昵称
     */
    public static String getUserNickname(String userId) {
        return userNicknameMap.getOrDefault(userId, "未知用户");
    }

    /**
     * 获取用户的消息历史
     */
    public static ArrayNode getHistory(String userId) {
        return getOrCreateHistory(userId);
    }

    /**
     * 清除用户的对话历史
     */
    public static void clearHistory(String userId) {
        conversationHistory.remove(userId);
    }

    /**
     * 获取所有用户的对话历史
     * 
     * @return Map<userId, 对话消息列表>
     */
    public static Map<String, Object> getAllHistory() {
        Map<String, Object> allHistory = new HashMap<>();

        for (Map.Entry<String, ArrayNode> entry : conversationHistory.entrySet()) {
            String userId = entry.getKey();
            ArrayNode messages = entry.getValue();

            // 构建用户的消息信息
            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("userId", userId);
            userInfo.put("messageCount", messages.size());
            userInfo.put("messages", messages);

            allHistory.put(userId, userInfo);
        }

        return allHistory;
    }

    /**
     * 获取所有用户的对话历史（JSON格式）
     * 
     * @return JSONObject 包含所有用户的对话历史，使用昵称作为 key
     */
    public static ObjectNode getAllHistoryAsJson() {
        ObjectNode result = mapper.createObjectNode();
        result.put("totalUsers", conversationHistory.size());

        ObjectNode users = mapper.createObjectNode();

        for (Map.Entry<String, ArrayNode> entry : conversationHistory.entrySet()) {
            String userId = entry.getKey();
            ArrayNode messages = entry.getValue();

            // 获取用户昵称，如果没有记录则使用 userId
            String nickname = getUserNickname(userId);

            ObjectNode userInfo = mapper.createObjectNode();
            userInfo.put("userId", userId);
            userInfo.put("nickname", nickname);
            userInfo.put("messageCount", messages.size());
            userInfo.set("messages", messages);

            // 使用昵称作为 key
            users.set(nickname, userInfo);
        }

        result.set("users", users);
        return result;
    }

    /**
     * 获取特定用户的对话消息数量
     */
    public static int getHistoryCount(String userId) {
        return getOrCreateHistory(userId).size();
    }

    /**
     * 清除所有用户的对话历史
     */
    public static void clearAllHistory() {
        conversationHistory.clear();
    }

    /**
     * 发送一个非流式的对话请求（chat/completions）并返回原始响应字符串
     */
    public String createChatCompletion(String model, ArrayNode messages) throws Exception {
        String url = BASE_URL + "/chat/completions";

        ObjectNode payload = mapper.createObjectNode();
        payload.put("model", model);
        payload.set("messages", messages);
        payload.put("stream", false);
        payload.put("temperature", 1.4);

        String body = mapper.writeValueAsString(payload);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response = client.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));

        int code = response.statusCode();
        if (code >= 200 && code < 300) {
            // 解析 JSON 并提取 content 字段
            ObjectNode root = (ObjectNode) mapper.readTree(response.body());
            ArrayNode choices = (ArrayNode) root.get("choices");
            if (choices != null && choices.size() > 0) {
                ObjectNode message = (ObjectNode) choices.get(0).get("message");
                return message.get("content").asText();
            } else {
                return "未找到有效的响应内容";
            }
        } else {
            throw new RuntimeException("DeepSeek API 返回错误: HTTP " + code + " - " + response.body());
        }
    }

    /**
     * 使用 DeepSeek API 进行多轮对话，使用共享上下文
     * 
     * @param 输入文本   用户输入的消息内容
     * @param userId 用户唯一标识（此参数保留但不用于区分上下文）
     * @return AI 的回复内容
     */
    public String Usedeepseek(String 输入文本, String userId) throws Exception {
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isEmpty()) {
            System.err.println("请先在环境变量中设置 DEEPSEEK_API_KEY");
            return "请先在环境变量中设置 DEEPSEEK_API_KEY";
        }

        // 使用固定的 key 作为共享上下文标识
        String sharedContextKey = "shared_context";

        // 添加用户消息到共享历史
        addMessageToHistory(sharedContextKey, "user", 输入文本);

        // 构建消息数组（多轮对话格式）
        ArrayNode messages = mapper.createArrayNode();
        
        // 添加 System 角色（可选）
        ObjectNode sys = mapper.createObjectNode();
        sys.put("role", "system");
        sys.put("content", " ");//你的设定是胆小但又贴心的学妹兼助手。名字是\"科罗娜\"。接收消息格式是\"用户昵称：内容\"。回复时只输出对话内容，不要添加\"用户\"、昵称或任何前缀。
        messages.add(sys);

        // 添加共享的完整对话历史（包括之前的所有对话）
        ArrayNode history = getHistory(sharedContextKey);
        for (int i = 0; i < history.size(); i++) {
            messages.add(history.get(i));
        }

        // 在控制台输出传入的消息 JSON
        System.out.println("[DeepSeek 多轮对话] 用户ID: " + userId);
        System.out.println("[DeepSeek 多轮对话] 发送的消息 JSON:");
        System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(messages));

        // 调用 DeepSeek API
        DSchatNcatQQ client = new DSchatNcatQQ(key);
        String resp = client.createChatCompletion("deepseek-chat", messages);

        // 添加 AI 回复到共享历史
        addMessageToHistory(sharedContextKey, "assistant", resp);

        System.out.println("[DeepSeek 多轮对话] AI 回复: " + resp);
        System.out.println("[DeepSeek 多轮对话] 当前消息历史条数: " + history.size());
        System.out.println("---");

        return resp;
    }

    /**
     * 简单方式调用 （随机自然语言）
     */
    public String Usedeepseek(String 输入文本) throws Exception {
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isEmpty()) {
            System.err.println("请先在环境变量中设置 DEEPSEEK_API_KEY");
            return "请先在环境变量中设置 DEEPSEEK_API_KEY";
        }

        DSchatNcatQQ client = new DSchatNcatQQ(key);

        ArrayNode messages = mapper.createArrayNode();
        ObjectNode sys = mapper.createObjectNode();
        sys.put("role", "system");
        sys.put("content", "你是qq群友");
        messages.add(sys);

        ObjectNode user = mapper.createObjectNode();
        user.put("role", "user");
        user.put("content", 输入文本);
        messages.add(user);

        String resp = client.createChatCompletion("deepseek-chat", messages);
        return resp;
    }

    /**
     * 简单方式调用（问候）
     */
    public String UsedeepseekMorning(String 输入文本) throws Exception {
        String key = System.getenv("DEEPSEEK_API_KEY");
        if (key == null || key.isEmpty()) {
            System.err.println("请先在环境变量中设置 DEEPSEEK_API_KEY");
            return "请先在环境变量中设置 DEEPSEEK_API_KEY";
        }

        DSchatNcatQQ client = new DSchatNcatQQ(key);

        ArrayNode messages = mapper.createArrayNode();
        ObjectNode sys = mapper.createObjectNode();
        sys.put("role", "system");
        sys.put("content", "你是一个温柔的人");
        messages.add(sys);

        ObjectNode user = mapper.createObjectNode();
        user.put("role", "user");
        user.put("content", 输入文本);
        messages.add(user);

        String resp = client.createChatCompletion("deepseek-chat", messages);
        return resp;
    }



}
