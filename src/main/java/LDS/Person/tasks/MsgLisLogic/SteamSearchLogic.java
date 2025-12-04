package LDS.Person.tasks.MsgLisLogic;

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

import LDS.Person.util.SteamGameSearcher;

import java.io.InputStream;
import java.util.Properties;

/**
 * Steam 游戏搜索逻辑处理器
 * 处理 Steam 游戏信息搜索和发送
 */
@Component
@Slf4j
public class SteamSearchLogic {

    @Autowired
    private RestTemplate restTemplate;

    private static String NCAT_API_BASE = "http://0.0.0.0:3000";
    private static String NCAT_AUTH_TOKEN = "";

    // 静态初始化块：从 config.properties 读取配置
    static {
        Properties props = new Properties();
        try (InputStream input = SteamSearchLogic.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                NCAT_API_BASE = props.getProperty("NapCatApiBase", NCAT_API_BASE);
                NCAT_AUTH_TOKEN = props.getProperty("NapCatAuthToken", NCAT_AUTH_TOKEN);
            } else {
                System.out.println("[WARN] config.properties 没有找到, 使用默认值");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] 无法读取 config.properties: " + e.getMessage());
        }
    }

    /**
     * 处理 Steam 游戏搜索指令
     *
     * @param groupId  群组 ID
     * @param gameName 游戏名称
     */
    public void handleSteamSearch(Long groupId, String gameName) {
    //    log.info("处理 Steam 搜索指令 - 群ID: {}, 游戏名: {}", groupId, gameName);

        try {
            // 调用 SteamGameSearcher 搜索游戏信息
            String[] result = SteamGameSearcher.searchAndGetGameInfoWithImage(gameName);
            String gameInfo = result[0];
            String jsonResponse = result[1];
            String appId = result[2];

            // 如果有游戏信息，先发送头图
            if (!jsonResponse.isEmpty() && !appId.isEmpty()) {
                String imageUrl = SteamGameSearcher.getGameHeaderImage(jsonResponse, appId);
                if (!imageUrl.isEmpty()) {
            //        log.info("发送 Steam 游戏头图: {}", imageUrl);
                    sendGroupImage(groupId, imageUrl);

                    // 等待一小段时间，确保图片先发送
                    Thread.sleep(500);
                }
            }

            // 发送游戏信息文本
        //    log.info("发送 Steam 游戏信息");
            sendGroupMessage(groupId, gameInfo);

        } catch (Exception e) {
            log.error("Steam 搜索处理失败: {}", e.getMessage());
            sendGroupMessage(groupId, "Steam 搜索失败: " + e.getMessage());
        }
    }

    /**
     * 发送群聊文本消息
     *
     * @param groupId 群组 ID
     * @param text    消息文本
     */
    private void sendGroupMessage(Long groupId, String text) {
        try {
            String url = NCAT_API_BASE + "/send_group_msg";

            // 构建请求体
            JSONObject requestBody = new JSONObject();
            requestBody.put("group_id", groupId);
            requestBody.put("message", text);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
             //   log.info(" 群聊消息发送成功 - 群ID: {}", groupId);
            } else {
                log.error("群聊消息发送失败 - 状态码: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("发送群聊消息异常: {}", e.getMessage());
        }
    }

    /**
     * 发送群聊图片消息
     *
     * @param groupId  群组 ID
     * @param imageUrl 图片 URL
     */
    private void sendGroupImage(Long groupId, String imageUrl) {
        try {
            String url = NCAT_API_BASE + "/send_group_msg";

            // 构建 CQ 码图片消息
            JSONObject imageSegment = new JSONObject();
            imageSegment.put("type", "image");
            JSONObject imageData = new JSONObject();
            imageData.put("file", imageUrl);
            imageSegment.put("data", imageData);

            com.alibaba.fastjson2.JSONArray messageArray = new com.alibaba.fastjson2.JSONArray();
            messageArray.add(imageSegment);

            JSONObject requestBody = new JSONObject();
            requestBody.put("group_id", groupId);
            requestBody.put("message", messageArray);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (response.getStatusCode() == HttpStatus.OK) {
            //    log.info(" 群聊图片发送成功 - 群ID: {}", groupId);
            } else {
                log.error("群聊图片发送失败 - 状态码: {}", response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("发送群聊图片异常: {}", e.getMessage());
        }
    }
}
