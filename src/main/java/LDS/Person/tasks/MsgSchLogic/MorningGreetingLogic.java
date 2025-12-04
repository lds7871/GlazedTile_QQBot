package LDS.Person.tasks.MsgSchLogic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import LDS.Person.dto.request.SendGroupMessageRequest;
import LDS.Person.dto.request.SendGroupImageRequest;
import LDS.Person.util.ImgToUri;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 每日早安问候逻辑处理器
 * 查询需要发送早安问候的群组
 * 获取最新早安问候文本
 * 依次发送到各群聊
 */
@Component
@Slf4j
public class MorningGreetingLogic {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTemplate restTemplate;

    private static final Random random = new Random();

    /**
     * 执行早安问候发送逻辑
     */
    public void executeMorningGreeting() {
        try {
            log.info("[MorningGreetingLogic] 开始发送每日早安问候");

            // 1. 查询需要发送早安问候的群组（greeting=1）
            List<String> groupIds = queryGroupIdsWithMorningGreeting();
            if (groupIds == null || groupIds.isEmpty()) {
                log.warn("[MorningGreetingLogic] 没有需要发送早安问候的群组");
                return;
            }

            log.info("[MorningGreetingLogic] 查询到 {} 个需要发送早安问候的群组", groupIds.size());

            // 2. 获取最新的早安问候文本
            String morningText = queryLatestMorningText();
            if (morningText == null || morningText.isEmpty()) {
                log.warn("[MorningGreetingLogic] 没有早安问候文本");
                return;
            }

            log.info("[MorningGreetingLogic] 获取早安问候文本，长度: {}", morningText.length());

            // 3. 提前获取图片 Data URI（避免每次发送时重复获取）
            String imageDataUri = null;
            try {
                imageDataUri = fetchAndConvertImageToDataUri();
                log.info("[MorningGreetingLogic] 图片 Data URI 获取成功");
            } catch (Exception e) {
                log.warn("[MorningGreetingLogic] 图片获取或转码失败，将继续发送文本消息: {}", e.getMessage());
            }

            // 4. 依次向每个群组发送问候，间隔 1~3 秒
            for (int i = 0; i < groupIds.size(); i++) {
                String groupId = groupIds.get(i);
                try {
                    // 先发送文本消息
                    sendMessageToGroup(groupId, morningText);
                    log.info("[MorningGreetingLogic] 早安问候文本已发送到群组: {}", groupId);

                    // 再发送图片消息（如果获取成功）
                    if (imageDataUri != null && !imageDataUri.isEmpty()) {
                        try {
                            sendImageToGroup(groupId, imageDataUri);
                            log.info("[MorningGreetingLogic] 早安问候图片已发送到群组: {}", groupId);
                        } catch (Exception e) {
                            log.warn("[MorningGreetingLogic] 图片发送到群组 {} 失败，但继续执行", groupId);
                        }
                    }

                    // 如果不是最后一个群组，则随机等待 1~3 秒
                    if (i < groupIds.size() - 1) {
                        long delayMillis = 1000 + random.nextInt(2001); // 1000~3000 毫秒
                        log.debug("[MorningGreetingLogic] 等待 {} 毫秒后发送下一个群组的问候", delayMillis);
                        Thread.sleep(delayMillis);
                    }
                } catch (Exception e) {
                    log.error("[MorningGreetingLogic] 发送早安问候到群组 {} 失败", groupId, e);
                }
            }

            log.info("[MorningGreetingLogic] 所有早安问候发送完成");

        } catch (Exception e) {
            log.error("[MorningGreetingLogic] 发送每日早安问候异常", e);
        }
    }

    /**
     * 查询 group_task 表中 greeting=1 的所有 group_id
     * 
     * @return group_id 列表
     */
    private List<String> queryGroupIdsWithMorningGreeting() {
        try {
            String sql = "SELECT group_id FROM group_task WHERE greeting = 1";
            List<String> groupIds = jdbcTemplate.queryForList(sql, String.class);
            log.info("[MorningGreetingLogic] 查询到 {} 个群组", groupIds.size());
            return groupIds;
        } catch (Exception e) {
            log.error("[MorningGreetingLogic] 查询群组列表异常", e);
            return null;
        }
    }

    /**
     * 查询 daliy_greeting 表最新一条数据的 morning_text
     * 
     * @return morning_text 文本
     */
    private String queryLatestMorningText() {
        try {
            String sql = "SELECT morning_text FROM daliy_greeting ORDER BY id DESC LIMIT 1";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            if (results != null && !results.isEmpty()) {
                Object morningTextObj = results.get(0).get("morning_text");
                if (morningTextObj != null) {
                    return morningTextObj.toString();
                }
            }
            
            log.warn("[MorningGreetingLogic] 没有找到早安问候文本");
            return null;
        } catch (Exception e) {
            log.error("[MorningGreetingLogic] 查询早安问候文本异常", e);
            return null;
        }
    }

    /**
     * 发送消息到指定群组
     * 调用本地 API 接口 POST /api/ncat/send/group-message
     * 
     * @param groupId 群组ID
     * @param text 消息文本
     */
    private void sendMessageToGroup(String groupId, String text) {
        try {
            SendGroupMessageRequest request = new SendGroupMessageRequest();
            request.setGroupId(Long.parseLong(groupId));
            request.setText(text);

            // 调用本地接口发送消息
            String url = "http://localhost:8090/api/ncat/send/group-message";
            Object response = restTemplate.postForObject(url, request, Object.class);

            log.debug("[MorningGreetingLogic] 发送消息到群组 {} 的响应: {}", groupId, response);

        } catch (Exception e) {
            log.error("[MorningGreetingLogic] 发送消息到群组 {} 失败", groupId, e);
        }
    }

    /**
     * 从 https://t.alcy.cc/moez 获取图片 URL 并转码为 Data URI
     * URL 会重定向到 .webp 格式的图片
     * 
     * @return Data URI 字符串
     */
    private String fetchAndConvertImageToDataUri() throws Exception {
        log.info("[MorningGreetingLogic] 开始获取并转码图片");

        String shortUrl = "https://t.alcy.cc/moez";
        
        // 1. 获取重定向后的真实 URL（.webp 图片）
        String realImageUrl = getRedirectUrl(shortUrl);
        if (realImageUrl == null || realImageUrl.isEmpty()) {
            throw new Exception("无法获取图片重定向 URL");
        }

        log.info("[MorningGreetingLogic] 获取到图片 URL: {}", realImageUrl);

        // 2. 下载图片内容
        byte[] imageBytes = downloadImage(realImageUrl);
        if (imageBytes == null || imageBytes.length == 0) {
            throw new Exception("图片下载失败或内容为空");
        }

        log.info("[MorningGreetingLogic] 图片下载成功，大小: {} 字节", imageBytes.length);

        // 3. 转码为 Base64
        String base64String = Base64.getEncoder().encodeToString(imageBytes);

        // 4. 构建 Data URI
        String dataUri = "data:image/webp;base64," + base64String;

        log.info("[MorningGreetingLogic] 图片转码为 Data URI 成功，长度: {} 字符", dataUri.length());

        return dataUri;
    }

    /**
     * 获取 URL 重定向后的真实 URL
     * 用于处理短链接到真实图片 URL 的重定向
     * 
     * @param shortUrl 短链接 URL
     * @return 重定向后的真实 URL
     */
    private String getRedirectUrl(String shortUrl) throws Exception {
        try {
            URL url = new URL(shortUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int statusCode = connection.getResponseCode();
            log.debug("[MorningGreetingLogic] HTTP 响应码: {}", statusCode);

            if (statusCode >= 300 && statusCode < 400) {
                String location = connection.getHeaderField("Location");
                if (location != null && !location.isEmpty()) {
                    log.info("[MorningGreetingLogic] 获取到重定向 URL: {}", location);
                    return location;
                }
            } else if (statusCode == 200) {
                // 如果直接返回 200，则原 URL 就是图片 URL
                return shortUrl;
            }

            throw new Exception("重定向失败，HTTP 状态码: " + statusCode);

        } catch (Exception e) {
            log.error("[MorningGreetingLogic] 获取重定向 URL 异常", e);
            throw e;
        }
    }

    /**
     * 下载图片内容为字节数组
     * 
     * @param imageUrl 图片 URL
     * @return 图片字节数组
     */
    private byte[] downloadImage(String imageUrl) throws Exception {
        try {
            URL url = new URL(imageUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                throw new Exception("图片下载失败，HTTP 状态码: " + statusCode);
            }

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (BufferedInputStream bis = new BufferedInputStream(connection.getInputStream())) {
                byte[] data = new byte[1024];
                int nRead;
                while ((nRead = bis.read(data, 0, data.length)) != -1) {
                    buffer.write(data, 0, nRead);
                }
            }

            return buffer.toByteArray();

        } catch (Exception e) {
            log.error("[MorningGreetingLogic] 下载图片异常", e);
            throw e;
        }
    }

    /**
     * 发送图片到指定群组
     * 调用本地 API 接口 POST /api/ncat/send/group-image
     * 
     * @param groupId 群组ID
     * @param dataUri 图片 Data URI（Base64 编码）
     */
    private void sendImageToGroup(String groupId, String dataUri) {
        try {
            SendGroupImageRequest request = new SendGroupImageRequest();
            request.setGroupId(Long.parseLong(groupId));
            request.setFile(dataUri);

            // 调用本地接口发送图片
            String url = "http://localhost:8090/api/ncat/send/group-image";
            Object response = restTemplate.postForObject(url, request, Object.class);

            log.debug("[MorningGreetingLogic] 发送图片到群组 {} 的响应: {}", groupId, response);

        } catch (Exception e) {
            log.error("[MorningGreetingLogic] 发送图片到群组 {} 失败", groupId, e);
            throw new RuntimeException(e);
        }
    }
}
