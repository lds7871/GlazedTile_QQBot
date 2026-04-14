package LDS.Person.tasks.MsgSchLogic;

import LDS.Person.config.ConfigManager;
import LDS.Person.util.DSchatNcatQQ;
import LDS.Person.tasks.TaskFactory;
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

    @Autowired
    private RestTemplate restTemplate;

    private static final ConfigManager CONFIG = ConfigManager.getInstance();
    private static final String NCAT_API_BASE = CONFIG.getNapCatApiBase();
    private static final String NCAT_AUTH_TOKEN = CONFIG.getNapCatAuthToken();

    /**
     * 调用 DeepSeek API 生成对话
     */
    private String callDeepSeekAPI(String prompt) {
        try {
            String apiKey = System.getenv("DEEPSEEK_API_KEY");
            System.out.println("[RandomChatLogic] DEEPSEEK_API_KEY 是否设置: " + (apiKey != null && !apiKey.isEmpty()));

            if (apiKey == null || apiKey.isEmpty()) {
                System.out.println("[RandomChatLogic]  DEEPSEEK_API_KEY 未设置");
                log.error("DEEPSEEK_API_KEY 未设置");
                return null;
            }

            System.out.println("[RandomChatLogic] 创建 DSchatNcatQQ 客户端...");
            DSchatNcatQQ chatClient = new DSchatNcatQQ(apiKey);
            System.out.println("[RandomChatLogic] 调用 Usedeepseek() 方法...");
            String result = chatClient.Usedeepseek(prompt);
            System.out.println("[RandomChatLogic] Usedeepseek() 返回: " + result);
            return result;

        } catch (Exception e) {
            System.out.println("[RandomChatLogic]  调用 DeepSeek API 异常: " + e.getMessage());
            e.printStackTrace();
            log.error("调用 DeepSeek API 异常: {}", e.getMessage(), e);
            return null;
        }
    }

    /**
     * 生成随机群聊消息并发送
     */
    public void generateAndSendRandomChat() {
        try {
            // 从 TaskFactory 获取最近一次活动的群聊ID
            String groupId = TaskFactory.getLastActiveGroupId();
            System.out.println("[RandomChatLogic] TaskFactory.getLastActiveGroupId() 返回: " + groupId);

            if (groupId == null || groupId.isEmpty()) {
                System.out.println("[RandomChatLogic]  未记录到群聊ID，跳过发送");
                log.warn("未记录到群聊ID，跳过发送");
                return;
            }

            System.out.println("[RandomChatLogic]  群聊ID: " + groupId);
            log.info("触发随机对话群ID: {}", groupId);

            String prompt = "生成一句无关紧要对话，不要带引号";
            System.out.println("[RandomChatLogic] 开始调用 DeepSeek API...");
            String randomMessage = callDeepSeekAPI(prompt);
            System.out.println("[RandomChatLogic] DeepSeek API 返回: " + randomMessage);

            if (randomMessage == null || randomMessage.isEmpty()) {
                System.out.println("[RandomChatLogic]  生成的随机对话为空，跳过发送");
                log.warn("生成的随机对话为空，跳过发送");
                return;
            }

            System.out.println("[RandomChatLogic] 开始发送消息: " + randomMessage);
            sendGroupMessage(groupId, randomMessage);
            System.out.println("[RandomChatLogic]  消息发送完成");

        } catch (Exception e) {
            System.out.println("[RandomChatLogic]  生成随机对话时出错: " + e.getMessage());
            e.printStackTrace();
            log.error("生成随机对话时出错: {}", e.getMessage(), e);
        }
    }

    /**
     * 发送群聊消息
     */
    private void sendGroupMessage(String groupId, String message) {
        try {
            String url = NCAT_API_BASE + "/send_group_msg";
            System.out.println("[RandomChatLogic.sendGroupMessage] 请求 URL: " + url);
            System.out.println("[RandomChatLogic.sendGroupMessage] 群组ID: " + groupId);
            System.out.println("[RandomChatLogic.sendGroupMessage] 消息内容: " + message);

            JSONObject requestBody = new JSONObject();
            requestBody.put("group_id", Long.parseLong(groupId));
            requestBody.put("message", message);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            System.out.println("[RandomChatLogic.sendGroupMessage] 请求体: " + requestBody.toJSONString());
            System.out.println("[RandomChatLogic.sendGroupMessage] 执行 HTTP POST 请求...");

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            System.out.println("[RandomChatLogic.sendGroupMessage] HTTP 响应状态码: " + response.getStatusCode());
            System.out.println("[RandomChatLogic.sendGroupMessage] HTTP 响应体: " + response.getBody());

            if (response.getStatusCode() != HttpStatus.OK) {
                System.out.println("[RandomChatLogic.sendGroupMessage]  群聊消息发送失败");
                log.error("群聊消息发送失败 - 状态码: {}", response.getStatusCode());
            } else {
                System.out.println("[RandomChatLogic.sendGroupMessage]  群聊消息发送成功");
            }

        } catch (Exception e) {
            System.out.println("[RandomChatLogic.sendGroupMessage]  发送群聊消息异常: " + e.getMessage());
            e.printStackTrace();
            log.error("发送群聊消息异常: {}", e.getMessage(), e);
        }
    }
}
