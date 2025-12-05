package LDS.Person.tasks.MsgSchLogic;

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

import java.io.InputStream;
import java.util.Properties;

/**
 * 随机群聊逻辑处理器
 * 定时生成无关紧要的对话并发送到最近一次的群聊
 */
@Component
@Slf4j
public class RandomChatLogic {
    
    // 存储最近一次的群聊ID
    private static volatile String lastGroupId = null;
    
    @Autowired
    private RestTemplate restTemplate;
    
    private static String NCAT_API_BASE;
    private static String NCAT_AUTH_TOKEN;
    
    // 静态初始化块：从 config.properties 读取配置
    static {
        Properties props = new Properties();
        try (InputStream input = RandomChatLogic.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                NCAT_API_BASE = props.getProperty("NapCatApiBase", "http://0.0.0.0:3000");
                NCAT_AUTH_TOKEN = props.getProperty("NapCatAuthToken", "");
            } else {
                log.warn("[RandomChatLogic] config.properties not found, using default values");
                NCAT_API_BASE = "http://0.0.0.0:3000";
                NCAT_AUTH_TOKEN = "";
            }
        } catch (Exception e) {
            log.error("[RandomChatLogic] Failed to load config.properties: {}", e.getMessage());
            NCAT_API_BASE = "http://0.0.0.0:3000";
            NCAT_AUTH_TOKEN = "";
        }
    }
    
    /**
     * 记录最近一次的群聊ID
     */
    public static void recordLastGroupId(String groupId) {
        lastGroupId = groupId;
    }
    
    /**
     * 获取最近一次的群聊ID
     */
    public static String getLastGroupId() {
        return lastGroupId;
    }

    /**
     * 调用 DeepSeek API 生成对话
     * 
     * @param prompt 提示词
     * @return 生成的对话内容
     */
    private String callDeepSeekAPI(String prompt) {
        try {
            String apiKey = System.getenv("DEEPSEEK_API_KEY");
            
            if (apiKey == null || apiKey.isEmpty()) {
                log.error("[RandomChatLogic] DEEPSEEK_API_KEY 未设置");
                return null;
            }
            
            DSchatNcatQQ chatClient = new DSchatNcatQQ(apiKey);
            String response = chatClient.Usedeepseek(prompt);
            
            // log.info("[RandomChatLogic] DeepSeek API 生成结果: {}", response);
            return response;
            
        } catch (Exception e) {
            log.error("[RandomChatLogic] 调用 DeepSeek API 异常: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * 生成随机群聊消息并发送
     */
    public void generateAndSendRandomChat() {
        try {
            // 获取最近的群聊ID
            String groupId = getLastGroupId();
            if (groupId == null || groupId.isEmpty()) {
                log.warn("[RandomChatLogic] 未记录到群聊ID，跳过发送");
                return;
            }
            log.info("触发随机对话群ID: {}", groupId);
            // 调用 DeepSeek 生成对话
            String prompt = "生成一句无关紧要但又有点无语的对话，不要带引号";
            String randomMessage = callDeepSeekAPI(prompt);
            
            if (randomMessage == null || randomMessage.isEmpty()) {
                log.warn("[RandomChatLogic] 生成的随机对话为空，跳过发送");
                return;
            }
            
            // 发送到群聊
            sendGroupMessage(groupId, randomMessage);
            
        } catch (Exception e) {
            log.error("[RandomChatLogic] 生成随机对话时出错: {}", e.getMessage(), e);
        }
    }
    
    /**
     * 发送群聊消息
     */
    private void sendGroupMessage(String groupId, String message) {
        try {
            String url = NCAT_API_BASE + "/send_group_msg";
            
            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("group_id", Long.parseLong(groupId));
            requestBody.put("message", message);
            
            
            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");
            
            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);
            
            if (response.getStatusCode() == HttpStatus.OK) {
                // log.info("[RandomChatLogic] 成功发送随机对话到群 {}", groupId);
            } else {
                log.error("[RandomChatLogic] 群聊消息发送失败 - 状态码: {}", response.getStatusCode());
            }
            
        } catch (Exception e) {
            log.error("[RandomChatLogic] 发送群聊消息异常: {}", e.getMessage(), e);
        }
    }
}
