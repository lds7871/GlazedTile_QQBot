package LDS.Person.tasks.MsgSchLogic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import LDS.Person.dto.request.SendGroupMessageRequest;
import LDS.Person.dto.request.SendGroupImageRequest;
import LDS.Person.util.AlcyWebpGet;
import LDS.Person.util.ImgToUri;

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
            //log.info("[MorningGreetingLogic] 开始发送每日早安问候");

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

            //log.info("[MorningGreetingLogic] 获取早安问候文本，长度: {}", morningText.length());

            // 3. 依次向每个群组发送问候，间隔 1~3 秒
            for (int i = 0; i < groupIds.size(); i++) {
                String groupId = groupIds.get(i);
                try {
                    // 先发送文本消息
                    sendMessageToGroup(groupId, morningText);
                    //log.info("[MorningGreetingLogic] 早安问候文本已发送到群组: {}", groupId);

                    // 再获取图片、转码、发送图片消息
                    try {
                        sendImageToGroup(groupId);
                        //log.info("[MorningGreetingLogic] 早安问候图片已发送到群组: {}", groupId);
                    } catch (Exception e) {
                        log.warn("[MorningGreetingLogic] 图片发送到群组 {} 失败，但继续执行", groupId, e);
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
            //log.info("[MorningGreetingLogic] 查询到 {} 个群组", groupIds.size());
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

            //log.debug("[MorningGreetingLogic] 发送消息到群组 {} 的响应: {}", groupId, response);

        } catch (Exception e) {
            log.error("[MorningGreetingLogic] 发送消息到群组 {} 失败", groupId, e);
        }
    }

    /**
     * 获取随机图片、转码并发送到指定群组
     * 1. 使用 AlcyWebpGet 获取图片并保存为每个群组独立的文件
     * 2. 使用 ImgToUri 转码为 Data URI
     * 3. 通过 /api/ncat/send/group-image 接口发送到群组
     * 
     * @param groupId 群组ID
     */
    private void sendImageToGroup(String groupId) throws Exception {
        try {
            //log.info("[MorningGreetingLogic] 开始获取并发送图片到群组: {}", groupId);

            // 1. 为每个群组生成独立的图片文件名（确保每个群的图片都不同）
            String imagePath = "img/早安图片.webp";
            
            //log.debug("[MorningGreetingLogic] 获取图片到: {}", imagePath);
            AlcyWebpGet.getAndSaveImage("http://t.alcy.cc/moez/", imagePath);

            // 2. 将图片转码为 Data URI
            String dataUri = ImgToUri.convertImageToDataUri(imagePath);
            if (dataUri == null || dataUri.isEmpty()) {
                throw new Exception("图片转码失败");
            }

            //log.debug("[MorningGreetingLogic] 图片转码成功，Data URI 长度: {}", dataUri.length());

            // 3. 通过接口发送图片到群组
            SendGroupImageRequest request = new SendGroupImageRequest();
            request.setGroupId(Long.parseLong(groupId));
            request.setFile(dataUri);

            String url = "http://localhost:8090/api/ncat/send/group-image";
            Object response = restTemplate.postForObject(url, request, Object.class);

            //log.debug("[MorningGreetingLogic] 发送图片到群组 {} 的响应: {}", groupId, response);

        } catch (Exception e) {
            log.error("[MorningGreetingLogic] 发送图片到群组 {} 失败", groupId, e);
            throw e;
        }
    }
}
