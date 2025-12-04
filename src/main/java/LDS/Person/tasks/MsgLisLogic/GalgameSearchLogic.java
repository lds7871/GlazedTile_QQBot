package LDS.Person.tasks.MsgLisLogic;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONArray;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;

import LDS.Person.util.GalgameProcessor;
import LDS.Person.dto.request.SendGroupImageRequest;

import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;

/**
 * Galgame 搜索逻辑处理类
 * 处理 Galgame 游戏搜索请求，包括获取数据、生成截图、转换为 Data URI、发送图片等
 * 
 * 流程：
 * 1. 调用 GalgameProcessor 获取游戏数据并生成截图
 * 2. 使用 ImgToUri 将截图转换为 Data URI
 * 3. 调用 /group-image 接口发送图片到群组
 */
@Component
@Slf4j
public class GalgameSearchLogic {

    @Autowired
    private RestTemplate restTemplate;

    // 本地 API 端点（推荐使用）
    private static final String LOCAL_SEND_IMAGE_URL = "http://localhost:8090/api/ncat/send/group-image";

    private static String NCAT_API_BASE = "00";
    private static String NCAT_AUTH_TOKEN = "0000";

    // 静态初始化块：从 config.properties 读取配置
    static {
        Properties props = new Properties();

        try (InputStream input = GalgameSearchLogic.class.getClassLoader()
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
     * 处理 Galgame 搜索请求
     * 异步执行以避免阻塞主线程
     *
     * @param groupId 群组 ID
     */
    public void handleGalgameSearch(Long groupId) {
        // 使用异步处理以避免阻塞主线程
        CompletableFuture.runAsync(() -> {
            try {
                log.info("开始处理 Galgame 搜索请求，群组ID: {}", groupId);

                // 第一步：调用 GalgameProcessor 获取截图
                //log.info("调用 GalgameProcessor 获取 Galgame 数据...");
                GalgameProcessor.process();

                // 第二步：等待一下确保文件已生成
                Thread.sleep(2000);

                // 第三步：使用 GalgameProcessor 直接获取裁剪后图片的 Data URI
                //log.info("获取裁剪后图片的 Data URI...");
                String dataUri = null;
                try {
                    dataUri = GalgameProcessor.getGalgameCroppedImageAsDataUri();
                } catch (Exception ex) {
                    log.error("从 GalgameProcessor 获取 Data URI 失败: {}", ex.getMessage());
                }

                if (dataUri == null || dataUri.isEmpty()) {
                    log.error("Failed to convert image to Data URI");
                    sendErrorMessage(groupId, "无法转换图片为 Data URI");
                    return;
                }

                //log.info("Data URI 转换成功，长度: {} 字符", dataUri.length());

                // 第四步：调用 /group-image 接口发送图片
                //log.info("准备通过 /group-image 接口发送图片到群组...");
                sendGalgameImageToGroup(groupId, dataUri);

                //log.info("Galgame 搜索请求处理完成");

            } catch (Exception e) {
                log.error("处理 Galgame 搜索异常", e);
                sendErrorMessage(groupId, "处理 Galgame 搜索出错: " + e.getMessage());
            }
        });
    }

    /**
     * 调用本地 /group-image 接口发送 Data URI 图片
     *
     * @param groupId 群组 ID
     * @param dataUri Data URI 字符串
     */
    private void sendGalgameImageToGroup(Long groupId, String dataUri) {
        try {
            // 使用本地 API 接口（推荐）
            SendGroupImageRequest request = new SendGroupImageRequest();
            request.setGroupId(groupId);
            request.setFile(dataUri);

            log.debug("准备通过本地 /group-image 接口发送图片到群组: {}", groupId);
            ResponseEntity<?> response = restTemplate.postForEntity(
                LOCAL_SEND_IMAGE_URL,
                request,
                Object.class
            );

            if (response.getStatusCode().is2xxSuccessful()) {
                // log.info("✅ Galgame 图片发送成功，群组ID: {}", groupId);
            } else {
                log.warn(" Galgame 图片发送失败，群组ID: {}，状态码: {}", groupId, response.getStatusCode());
            }

        } catch (Exception e) {
            log.error("发送 Galgame 图片异常，群组ID: {}", groupId, e);
        }
    }

    /**
     * 发送错误消息到群组
     *
     * @param groupId 群组 ID
     * @param errorMessage 错误消息
     */
    private void sendErrorMessage(Long groupId, String errorMessage) {
        try {
            String url = NCAT_API_BASE + "/send_group_msg";

            JSONObject requestBody = new JSONObject();
            requestBody.put("group_id", groupId);

            JSONArray messageArray = new JSONArray();
            JSONObject messageItem = new JSONObject();
            messageItem.put("type", "text");
            JSONObject dataObj = new JSONObject();
            dataObj.put("text", errorMessage);
            messageItem.put("data", dataObj);
            messageArray.add(messageItem);

            requestBody.put("message", messageArray);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            restTemplate.postForEntity(url, entity, String.class);

            log.debug("错误消息已发送到群组: {}", groupId);

        } catch (Exception e) {
            log.error("发送错误消息异常", e);
        }
    }
}
