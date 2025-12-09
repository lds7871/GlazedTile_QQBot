package LDS.Person.tasks;

import com.alibaba.fastjson2.JSONObject;

import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.util.DSchatNcatQQ;
import LDS.Person.config.ConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;

import java.util.Random;

/**
 * 消息监听和自动回复处理器
 * 监听群聊消息，当消息中包含 @机器人 时自动回复
 */
@Component
@Slf4j
public class MsgLisATTask {

    @Autowired
    private RestTemplate restTemplate;

    // 使用ConfigManager获取配置，避免重复加载配置文件，提高性能
    private static final ConfigManager configManager = ConfigManager.getInstance();
    private static final String NCAT_API_BASE = configManager.getNapCatApiBase();
    private static final String NCAT_AUTH_TOKEN = configManager.getNapCatAuthToken();
    private static final String BOT_QQ_ID = configManager.getNapcatQQID();
    
    // 重试相关常量
    private static final long RETRY_BASE_DELAY_MS = 1000L;          // 基础重试延迟（毫秒）
    private static final long SERVER_ERROR_RETRY_DELAY_MS = 2000L;  // 服务器错误重试延迟（毫秒）

    /**
     * 处理接收到的 WebSocket 消息
     * 当任何群聊中的消息包含 @机器人 时，自动回复
     * 
     * @param message WebSocket 消息的 JSON 对象
     */
    public void handleMessage(JSONObject message) {
        // 检查该任务是否启用
        if (!NapCatTaskIsOpen.isMsgLisATTask) {
          //  System.out.println("isMsgLisATTask未启用");
            return;
        }

        try {
            String postType = message.getString("post_type");

            // 只处理聊天消息
            if (!"message".equals(postType)) {
                return;
            }

            String messageType = message.getString("message_type");

            // 只处理群聊消息（不区分是否为目标群）
            if (!"group".equals(messageType)) {
                return;
            }

            Long groupId = message.getLong("group_id");
            Long userId = message.getLong("user_id");
            String rawMessage = message.getString("raw_message");
            String groupName = message.getString("group_name");

            JSONObject sender = message.getJSONObject("sender");
            String nickname = sender != null ? sender.getString("nickname") : "未知用户";
            String card = sender != null ? sender.getString("card") : "";
            String displayName = (card != null && !card.isEmpty()) ? card : nickname;

            // 生成 @ 机器人的标记
            String atBotTag = "[CQ:at,qq=" + BOT_QQ_ID + "]";

            // 检查消息是否包含 @ 机器人
            if (rawMessage != null && rawMessage.contains(atBotTag)) {
                log.info("检测到 @机器人消息");

                // 发送自动回复
                sendAutoReply(groupId, userId, rawMessage, displayName);
            }

        } catch (Exception e) {
            log.error("处理消息异常", e);
        }
    }

    /**
     * 发送自动回复消息
     * 
     * @param groupId         群组 ID
     * @param userId          用户 ID
     * @param originalMessage 原始消息
     * @param nickname        用户昵称
     */
    private void sendAutoReply(Long groupId, Long userId, String originalMessage, String nickname) {
        try {
            // log.info("准备向群ID: {} 发送自动回复", groupId);

            // 调用 DeepSeek API 获取 AI 回复
            String replyText = null;
            try {
                // 记录用户昵称
                DSchatNcatQQ.setUserNickname(String.valueOf(userId), nickname);

                // 构建带昵称的消息
                String contextMessage = nickname + ": " + originalMessage;

                System.out.println("\n" + contextMessage + "\n");

                DSchatNcatQQ dSchatNcatQQ = new DSchatNcatQQ(System.getenv("DEEPSEEK_API_KEY"));
                // 使用 userId 作为会话ID，保持用户上下文
                replyText = dSchatNcatQQ.Usedeepseek(contextMessage, String.valueOf(userId));

                // System.out.println("\n[用户消息] " + contextMessage + "\n[AI回复] " + replyText +
                // "\n");

                // 检查回复是否为空
                if (replyText == null || replyText.isEmpty()) {
                    log.warn("DeepSeek 返回空回复，使用默认回复");
                    replyText = "收到消息，但AI没有生成有效回复";
                }

            } catch (Exception e) {
                log.error("调用 DeepSeek API 异常，使用默认回复: {}", e.getMessage());
                replyText = "收到消息，API调用异常";
            }

            // 使用已有的方法构建消息请求体
            JSONObject requestBody = buildGroupMessageRequest(groupId, replyText);

            // log.debug("发送回复消息: {}", requestBody.toJSONString());

            // 调用 NapCat API 发送消息（带重试机制）
            boolean success = sendMessageWithRetry(groupId, requestBody, 2);

            // // 如果发送成功，有五分之一的概率发送固定图片
            // if (success) {
            //     if (shouldSendImage()) {
            //         sendGroupFixedImage(groupId);
            //     }
            // }

        } catch (Exception e) {
            log.error("发送自动回复异常 - 群ID: {}", groupId, e);
        }
    }

    /**
     * 发送消息并支持重试
     * 
     * @param groupId     群组 ID
     * @param requestBody 请求体
     * @param retryCount  重试次数
     * @return 是否发送成功
     */
    private boolean sendMessageWithRetry(Long groupId, JSONObject requestBody, int retryCount) {
        String url = NCAT_API_BASE + "/send_group_msg";

        for (int i = 1; i <= retryCount; i++) {
            try {
                // log.debug("第 {} 次尝试发送消息到群ID: {}", i, groupId);

                HttpHeaders headers = new HttpHeaders();
                headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
                headers.set("Content-Type", "application/json");

                HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
                ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

                if (apiResponse.getStatusCode() == HttpStatus.OK) {
                    JSONObject jsonResponse = JSONObject.parseObject(apiResponse.getBody());
                    if (jsonResponse.containsKey("status") && "ok".equals(jsonResponse.getString("status"))) {
                        Integer messageId = null;
                        if (jsonResponse.containsKey("data")) {
                            JSONObject data = jsonResponse.getJSONObject("data");
                            messageId = data.getInteger("message_id");
                        }
                        // log.info("✅ 自动回复消息发送成功，群ID: {}，消息ID: {}", groupId, messageId);
                        return true;
                    } else {
                        log.error("❌ 第 {} 次发送失败 - 群ID: {}，错误: {}", i, groupId, jsonResponse.getString("message"));
                    }
                } else {
                    log.error("❌ 第 {} 次发送失败 - 群ID: {}，HTTP状态: {}", i, groupId, apiResponse.getStatusCode());
                }

                // 重试前等待一段时间，使用指数退避策略
                if (i < retryCount) {
                    long waitTime = RETRY_BASE_DELAY_MS * i; // 等待时间递增：1秒、2秒
                    log.info("等待 {} 毫秒后进行第 {} 次重试...", waitTime, i + 1);
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("重试等待被中断");
                        break;
                    }
                }

            } catch (HttpServerErrorException e) {
                log.error("❌ 第 {} 次发送异常 - 群ID: {}，HTTP错误: {} {}", i, groupId, e.getRawStatusCode(), e.getStatusText());

                // 如果是 502/503/504 等服务器错误，进行重试，使用指数退避策略
                if (i < retryCount && (e.getRawStatusCode() >= 500)) {
                    try {
                        long waitTime = SERVER_ERROR_RETRY_DELAY_MS * i; // 等待时间：2秒、4秒
                        log.info("服务器错误，等待 {} 毫秒后进行第 {} 次重试...", waitTime, i + 1);
                        Thread.sleep(waitTime);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        log.warn("重试等待被中断");
                        break;
                    }
                } else {
                    break;
                }
            } catch (Exception e) {
                log.error("❌ 第 {} 次发送异常 - 群ID: {}", i, groupId, e);
                if (i < retryCount) {
                    try {
                        Thread.sleep(1000 * i);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }

        return false;
    }

    /**
     * 构建群聊消息请求体
     */
    private JSONObject buildGroupMessageRequest(Long groupId, String messageText) {
        JSONObject request = new JSONObject();
        request.put("group_id", groupId);

        // 构建消息数组
        com.alibaba.fastjson2.JSONArray messageArray = new com.alibaba.fastjson2.JSONArray();
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
     * 判断是否应该发送图片（五分之一概率）
     */
    private boolean shouldSendImage() {
        Random random = new Random();
        return random.nextInt(10) == 0; // 20%的概率（1/5）
    }

    /**
     * 调用 NCatSendMessageController 的 sendGroupFixedImage 接口
     */
    private void sendGroupFixedImage(Long groupId) {
        try {
            String keyWord = "BIGHead"; // 默认关键字
            String url = "http://localhost:8090/api/ncat/send/group-fixed-image?groupId=" + groupId + "&keyWord=" + keyWord;
            // log.info("调用固定图片接口，群ID: {}，关键字: {}", groupId, keyWord);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode().is2xxSuccessful()) {
                log.info(" 抽中随机调用图片方法");
            } else {
                log.warn(" 随机图片发送失败，群ID: {}，状态码: {}", groupId, response.getStatusCode());
            }
        } catch (Exception e) {
            log.error("随机调用固定图片接口异常，群ID: {}", groupId, e);
        }
    }

}
