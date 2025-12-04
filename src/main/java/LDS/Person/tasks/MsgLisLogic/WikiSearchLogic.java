package LDS.Person.tasks.MsgLisLogic;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import LDS.Person.util.WikipediaSearcher;

import java.io.InputStream;
import java.util.Properties;

/**
 * Wiki 搜索逻辑处理类
 * 处理 Wikipedia 搜索请求和消息发送
 */
@Component
@Slf4j
public class WikiSearchLogic {

    @Autowired
    private RestTemplate restTemplate;

    private static String NCAT_API_BASE = "00";
    private static String NCAT_AUTH_TOKEN = "0000";

    // 静态初始化块：从 config.properties 读取配置
    static {
        Properties props = new Properties();

        try (InputStream input = WikiSearchLogic.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                NCAT_API_BASE = props.getProperty("NapCatApiBase", NCAT_API_BASE);
                NCAT_AUTH_TOKEN = props.getProperty("NapCatAuthToken", NCAT_AUTH_TOKEN);
            } else {
                System.out.println("[WARN] config.properties 没有找到, 会使用不可用的默认值");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] 无法读取 config.properties: " + e.getMessage());
        }
    }

    /**
     * 处理 Wiki 搜索请求
     * 
     * @param groupId 群组 ID
     * @param wikiContent Wiki 搜索内容
     */
    public void handleWikiSearch(Long groupId, String wikiContent) {
        try {
            log.info("处理 Wiki 搜索请求 - 群ID: {}，搜索内容: {}", groupId, wikiContent);
            
            // 调用 WikipediaSearcher 执行搜索
            String searchResult = WikipediaSearcher.executeSearch(wikiContent, 5);
            
            if (searchResult == null || searchResult.isEmpty()) {
                log.warn("Wiki 搜索返回空结果: {}", wikiContent);
                sendMessageToGroup(groupId, "Wiki 搜索失败: 没有获取到结果");
                return;
            }

            // 检查是否是错误信息
            if (searchResult.startsWith("Error:") || searchResult.startsWith("提取失败:") || searchResult.startsWith("无效的响应格式")) {
                log.warn("Wiki 搜索返回错误: {}", searchResult);
                sendMessageToGroup(groupId, "Wiki 搜索失败: " + searchResult);
                return;
            }

            log.debug("Wiki 搜索结果长度: {}", searchResult.length());
            
            // 将搜索结果分段发送（考虑消息长度限制）
            sendLongMessageToGroup(groupId, searchResult);
            
            log.info(" Wiki 搜索完成 - 群ID: {}", groupId);
            
        } catch (Exception e) {
            log.error("处理 Wiki 搜索异常 - 群ID: {}，异常信息: {}", groupId, e.getMessage());
            log.error("详细堆栈追踪:", e);
            sendMessageToGroup(groupId, "Wiki 搜索异常: " + e.getMessage());
        }
    }

    /**
     * 发送单条消息到群组
     * 
     * @param groupId 群组 ID
     * @param message 消息内容
     */
    private void sendMessageToGroup(Long groupId, String message) {
        try {
            // 构建请求头
            org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("group_id", groupId);
            
            com.alibaba.fastjson2.JSONArray messageArray = new com.alibaba.fastjson2.JSONArray();
            JSONObject messageItem = new JSONObject();
            messageItem.put("type", "text");
            JSONObject dataObj = new JSONObject();
            dataObj.put("text", message);
            messageItem.put("data", dataObj);
            messageArray.add(messageItem);
            requestBody.put("message", messageArray);

            // 发送请求
            String url = NCAT_API_BASE + "/send_group_msg";
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(requestBody.toJSONString(), headers);
            
            restTemplate.exchange(url, org.springframework.http.HttpMethod.POST, entity, String.class);
            
            // log.debug("消息已发送到群组: {}", groupId);
        } catch (Exception e) {
            log.error("发送消息到群组异常 - 群ID: {}", groupId, e);
        }
    }

    /**
     * 发送长消息到群组（自动分段）
     * 考虑 QQ 消息长度限制，超过限制则分多条发送
     * 
     * @param groupId 群组 ID
     * @param message 消息内容
     */
    private void sendLongMessageToGroup(Long groupId, String message) {
        try {
            // QQ 群聊消息长度限制通常为 4096 字符
            int maxLength = 4000;
            
            if (message.length() <= maxLength) {
                // 消息长度足够短，直接发送
                sendMessageToGroup(groupId, message);
            } else {
                // 消息过长，按照段落或行分割
                String[] lines = message.split("\n");
                StringBuilder currentMessage = new StringBuilder();
                
                for (String line : lines) {
                    // 如果加上当前行会超过限制，先发送当前消息
                    if (currentMessage.length() + line.length() + 1 > maxLength && currentMessage.length() > 0) {
                        sendMessageToGroup(groupId, currentMessage.toString());
                        currentMessage = new StringBuilder();
                    }
                    
                    currentMessage.append(line).append("\n");
                }
                
                // 发送剩余的消息
                if (currentMessage.length() > 0) {
                    sendMessageToGroup(groupId, currentMessage.toString());
                }
            }
        } catch (Exception e) {
            log.error("发送长消息到群组异常 - 群ID: {}", groupId, e);
            sendMessageToGroup(groupId, "消息发送异常: " + e.getMessage());
        }
    }
}
