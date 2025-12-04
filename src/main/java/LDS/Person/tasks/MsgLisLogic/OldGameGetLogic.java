package LDS.Person.tasks.MsgLisLogic;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import LDS.Person.dto.request.SendGroupImageRequest;
import LDS.Person.dto.request.SendGroupMessageRequest;

import java.util.List;
import java.util.Map;

/**
 * 今日怀旧游戏逻辑处理器
 * 从数据库读取最近添加的旧游戏记录，发送图片和内容到群聊
 */
@Component
@Slf4j
public class OldGameGetLogic {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 处理"今日怀旧->"指令
     * 从 old_game 表中读取最近的一条记录，发送图片和内容到群聊
     *
     * @param groupId 群聊ID
     */
    public void handleOldGameGet(Long groupId) {
        try {
            log.info("[OldGameGetLogic] 处理今日怀旧指令，群组ID: {}", groupId);

            // 1. 从数据库读取最近的一条记录
            String sql = "SELECT content, image_url FROM old_game ORDER BY created_at DESC LIMIT 1";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

            if (results.isEmpty()) {
                log.warn("[OldGameGetLogic] 数据库中没有找到怀旧游戏记录");
                // 发送提示消息
                sendMessage(groupId, "暂无怀旧游戏记录，请稍后再试");
                return;
            }

            Map<String, Object> row = results.get(0);
            String content = (String) row.get("content");
            String imageUrl = (String) row.get("image_url");

            log.info("[OldGameGetLogic] 读取到记录 - 内容长度: {}，图片URL: {}", 
                    content != null ? content.length() : 0, imageUrl);

            // 2. 先发送图片（如果存在）
            if (imageUrl != null && !imageUrl.isEmpty()) {
                log.info("[OldGameGetLogic] 发送图片，URL: {}", imageUrl);
                sendImage(groupId, imageUrl);
            } else {
                log.warn("[OldGameGetLogic] 图片URL为空，跳过发送图片");
            }

            // 3. 再发送内容
            if (content != null && !content.isEmpty()) {
                log.info("[OldGameGetLogic] 发送内容，长度: {}", content.length());
                sendMessage(groupId, content);
            } else {
                log.warn("[OldGameGetLogic] 内容为空");
            }

            log.info("[OldGameGetLogic] 怀旧游戏信息发送完成");

        } catch (Exception e) {
            log.error("[OldGameGetLogic] 处理今日怀旧指令异常", e);
            try {
                sendMessage(groupId, "处理怀旧游戏指令失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("[OldGameGetLogic] 发送错误消息失败", ex);
            }
        }
    }

    /**
     * 发送图片到群聊
     * 调用 NCatSendMessageController 的 /api/ncat/send/group-image 接口
     *
     * @param groupId 群聊ID
     * @param imageUrl 图片URL
     */
    private void sendImage(Long groupId, String imageUrl) {
        try {
            SendGroupImageRequest request = new SendGroupImageRequest();
            request.setGroupId(groupId);
            request.setFile(imageUrl);

            // 调用本地接口发送图片
            String url = "http://localhost:8090/api/ncat/send/group-image";
            JSONObject response = restTemplate.postForObject(url, request, JSONObject.class);

            log.info("[OldGameGetLogic] 图片发送响应: {}", response);

        } catch (Exception e) {
            log.error("[OldGameGetLogic] 发送图片异常", e);
        }
    }

    /**
     * 发送文本消息到群聊
     * 调用 NCatSendMessageController 的 /api/ncat/send/group-message 接口
     *
     * @param groupId 群聊ID
     * @param message 消息内容
     */
    private void sendMessage(Long groupId, String message) {
        try {
            SendGroupMessageRequest request = new SendGroupMessageRequest();
            request.setGroupId(groupId);
            request.setText(message);

            // 调用本地接口发送消息
            String url = "http://localhost:8090/api/ncat/send/group-message";
            JSONObject response = restTemplate.postForObject(url, request, JSONObject.class);

            log.info("[OldGameGetLogic] 消息发送响应: {}", response);

        } catch (Exception e) {
            log.error("[OldGameGetLogic] 发送消息异常", e);
        }
    }
}
