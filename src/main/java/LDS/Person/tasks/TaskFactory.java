package LDS.Person.tasks;

import LDS.Person.config.ConfigManager;
import LDS.Person.config.NapCatTaskIsOpen;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

/**
 * Task 工厂工具类 - 新建 Task 的 SKILL
 * 提供创建各类任务所需的通用能力：消息构建、发送、重试、开关检查等
 * 
 * 使用方式：
 * 1. 通过 Spring 注入获取实例
 * 2. 调用 isTaskEnabled() 检查任务开关
 * 3. 调用 buildXxxMessage() 构建消息
 * 4. 调用 sendGroupMessage() 或 sendGroupMessageWithRetry() 发送消息
 * 
 * 新建 Task 步骤：
 * 1. 在 tasks/ 目录下创建 XxxTask.java，添加 @Component 注解
 * 2. 注入 TaskFactory，使用其提供的通用能力
 * 3. 监听型任务：实现 handleMessage(JSONObject) 方法
 * 4. 定时型任务：添加 @Scheduled(cron="...") 注解
 */
@Slf4j
public final class TaskFactory {

    private static final ConfigManager CONFIG = ConfigManager.getInstance();
    private static final String NCAT_API_BASE = CONFIG.getNapCatApiBase();
    private static final String NCAT_AUTH_TOKEN = CONFIG.getNapCatAuthToken();
    private static final String BOT_QQ_ID = CONFIG.getNapcatQQID();

    // 重试相关常量
    private static final long RETRY_BASE_DELAY_MS = 1000L;
    private static final long SERVER_ERROR_RETRY_DELAY_MS = 2000L;

    private TaskFactory() {
        // 工具类，禁止实例化
    }

    // ==================== 任务开关检查 ====================

    /**
     * 检查消息监听任务是否启用
     */
    public static boolean isListenerTaskEnabled() {
        return NapCatTaskIsOpen.isMsgLisATTask;
    }

    /**
     * 检查定时任务是否启用
     */
    public static boolean isScheduledTaskEnabled() {
        return NapCatTaskIsOpen.isMsgSchTask;
    }

    // ==================== 消息解析工具 ====================

    /**
     * 从 WebSocket 消息中提取群聊信息
     * 
     * @param message WebSocket 消息 JSON
     * @return 群聊信息，如果不是群聊消息返回 null
     */
    public static GroupMessageInfo extractGroupMessage(JSONObject message) {
        String postType = message.getString("post_type");
        if (!"message".equals(postType)) {
            return null;
        }

        String messageType = message.getString("message_type");
        if (!"group".equals(messageType)) {
            return null;
        }

        Long groupId = message.getLong("group_id");
        Long userId = message.getLong("user_id");
        String rawMessage = message.getString("raw_message");

        JSONObject sender = message.getJSONObject("sender");
        String nickname = sender != null ? sender.getString("nickname") : "未知用户";
        String card = sender != null ? sender.getString("card") : "";
        String displayName = (card != null && !card.isEmpty()) ? card : nickname;

        return new GroupMessageInfo(groupId, userId, rawMessage, displayName, nickname, card);
    }

    /**
     * 检查消息是否包含 @机器人
     * 
     * @param rawMessage 原始消息
     * @return 是否 @机器人
     */
    public static boolean isAtBot(String rawMessage) {
        if (rawMessage == null || BOT_QQ_ID == null || BOT_QQ_ID.isEmpty()) {
            return false;
        }
        String atBotTag = "[CQ:at,qq=" + BOT_QQ_ID + "]";
        return rawMessage.contains(atBotTag);
    }

    // ==================== 消息构建工具 ====================

    /**
     * 构建文本消息请求体
     * 
     * @param groupId     群组 ID
     * @param messageText 消息文本
     * @return 请求 JSON
     */
    public static JSONObject buildTextMessage(Long groupId, String messageText) {
        JSONObject request = new JSONObject();
        request.put("group_id", groupId);

        JSONArray messageArray = new JSONArray();
        JSONObject messageItem = new JSONObject();
        messageItem.put("type", "text");

        JSONObject dataObj = new JSONObject();
        dataObj.put("text", messageText);
        messageItem.put("data", dataObj);

        messageArray.add(messageItem);
        request.put("message", messageArray);

        return request;
    }

    /**
     * 构建图片消息请求体
     * 
     * @param groupId  群组 ID
     * @param imageUrl 图片 URL
     * @return 请求 JSON
     */
    public static JSONObject buildImageMessage(Long groupId, String imageUrl) {
        JSONObject request = new JSONObject();
        request.put("group_id", groupId);

        JSONArray messageArray = new JSONArray();
        JSONObject messageItem = new JSONObject();
        messageItem.put("type", "image");

        JSONObject dataObj = new JSONObject();
        dataObj.put("file", imageUrl);
        messageItem.put("data", dataObj);

        messageArray.add(messageItem);
        request.put("message", messageArray);

        return request;
    }

    // ==================== 消息发送工具 ====================

    /**
     * 发送群聊消息（无重试）
     * 
     * @param restTemplate Spring RestTemplate
     * @param groupId      群组 ID
     * @param requestBody  请求体
     * @return 是否发送成功
     */
    public static boolean sendGroupMessage(RestTemplate restTemplate, Long groupId, JSONObject requestBody) {
        return sendGroupMessageWithRetry(restTemplate, groupId, requestBody, 1);
    }

    /**
     * 发送群聊消息（带重试）
     * 
     * @param restTemplate Spring RestTemplate
     * @param groupId      群组 ID
     * @param requestBody  请求体
     * @param retryCount   最大尝试次数
     * @return 是否发送成功
     */
    public static boolean sendGroupMessageWithRetry(RestTemplate restTemplate, Long groupId,
            JSONObject requestBody, int retryCount) {
        String url = NCAT_API_BASE + "/send_group_msg";

        for (int i = 1; i <= retryCount; i++) {
            try {
                HttpHeaders headers = createAuthHeaders();
                HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
                ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                if (apiResponse.getStatusCode() == HttpStatus.OK) {
                    JSONObject jsonResponse = JSONObject.parseObject(apiResponse.getBody());
                    if ("ok".equals(jsonResponse.getString("status"))) {
                        return true;
                    }
                    log.error("第 {} 次发送失败 - 群ID: {}，错误: {}", i, groupId, jsonResponse.getString("message"));
                } else {
                    log.error("第 {} 次发送失败 - 群ID: {}，HTTP状态: {}", i, groupId, apiResponse.getStatusCode());
                }

                sleepBeforeRetry(i, retryCount, RETRY_BASE_DELAY_MS);

            } catch (HttpServerErrorException e) {
                log.error("第 {} 次发送异常 - 群ID: {}，HTTP错误: {} {}", i, groupId, e.getRawStatusCode(),
                        e.getStatusText());
                if (i < retryCount && e.getRawStatusCode() >= 500) {
                    sleepBeforeRetry(i, retryCount, SERVER_ERROR_RETRY_DELAY_MS);
                } else {
                    break;
                }
            } catch (Exception e) {
                log.error("第 {} 次发送异常 - 群ID: {}", i, groupId, e);
                sleepBeforeRetry(i, retryCount, RETRY_BASE_DELAY_MS);
            }
        }

        return false;
    }

    // ==================== 内部工具方法 ====================

    /**
     * 创建带认证的 HTTP 请求头（供外部 Task 复用）
     */
    public static HttpHeaders createAuthHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
        headers.set("Content-Type", "application/json");
        return headers;
    }

    /**
     * 获取 NapCat API 基础 URL
     */
    public static String getNcatApiBase() {
        return NCAT_API_BASE;
    }

    /**
     * 重试前的等待
     */
    private static void sleepBeforeRetry(int attempt, int maxAttempts, long baseDelayMs) {
        if (attempt < maxAttempts) {
            try {
                long waitTime = baseDelayMs * attempt;
                log.info("等待 {} 毫秒后进行第 {} 次重试...", waitTime, attempt + 1);
                Thread.sleep(waitTime);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("重试等待被中断");
            }
        }
    }

    // ==================== 群聊消息信息封装 ====================

    /**
     * 群聊消息信息数据类
     */
    @lombok.Getter
    @lombok.AllArgsConstructor
    public static class GroupMessageInfo {
        private final Long groupId;
        private final Long userId;
        private final String rawMessage;
        private final String displayName;
        private final String nickname;
        private final String card;
    }
}
