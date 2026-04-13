package LDS.Person.tasks.MsgSchLogic;

import LDS.Person.config.ConfigManager;
import LDS.Person.util.DSchatNcatQQ;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 随机群聊逻辑处理器
 * 定时生成无关紧要的对话并发送到最近一次的群聊
 */
@Component
@Slf4j
public class RandomChatLogic {

    private static volatile String lastGroupId = null;

    @Autowired
    private RestTemplate restTemplate;

    private static final ConfigManager CONFIG = ConfigManager.getInstance();
    private static final String NCAT_API_BASE = CONFIG.getNapCatApiBase();
    private static final String NCAT_AUTH_TOKEN = CONFIG.getNapCatAuthToken();

    public static void recordLastGroupId(String groupId) {
        lastGroupId = groupId;
    }

    public static String getLastGroupId() {
        return lastGroupId;
    }

    /**
     * 调用 DeepSeek API 生成对话
     */
    private String callDeepSeekAPI(String prompt) {
        try {
            String apiKey = System.getenv("DEEPSEEK_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                log.error("DEEPSEEK_API_KEY 未设置");
                return null;
            }

            DSchatNcatQQ chatClient = new DSchatNcatQQ(apiKey);
            return chatClient.Usedeepseek(prompt);

        } catch (Exception e) {
            log.error("调用 DeepSeek API 异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 生成随机群聊消息并发送
     */
    public void generateAndSendRandomChat() {
        try {
            String groupId = getLastGroupId();
            if (groupId == null || groupId.isEmpty()) {
                log.warn("未记录到群聊ID，跳过发送");
                return;
            }
            log.info("触发随机对话群ID: {}", groupId);

            String prompt = "生成一句无关紧要的科普对话，不要带引号";
            String randomMessage = callDeepSeekAPI(prompt);

            if (randomMessage == null || randomMessage.isEmpty()) {
                log.warn("生成的随机对话为空，跳过发送");
                return;
            }

            sendGroupMessage(groupId, randomMessage);

        } catch (Exception e) {
            log.error("生成随机对话时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送群聊消息
     */
    private void sendGroupMessage(String groupId, String message) {
        try {
            String url = NCAT_API_BASE + "/send_group_msg";

            JSONObject requestBody = new JSONObject();
            requestBody.put("group_id", Long.parseLong(groupId));
            requestBody.put("message", message);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() != HttpStatus.OK) {
                log.error("群聊消息发送失败 - 状态码: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("发送群聊消息异常: {}", e.getMessage(), e);
        }
    }
}
