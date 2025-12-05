package LDS.Person.tasks.MsgSchLogic;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import LDS.Person.dto.request.SendGroupMessageRequest;

import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * 每日晚安问候逻辑处理器
 * 查询需要发送晚安问候的群组
 * 获取最新晚安问候文本
 * 依次发送到各群聊
 */
@Component
@Slf4j
public class EveningGreetingLogic {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RestTemplate restTemplate;

    private static final Random random = new Random();

    /**
     * 执行晚安问候发送逻辑
     */
    public void executeEveningGreeting() {
        try {
            //log.info("[EveningGreetingLogic] 开始发送每日晚安问候");

            // 1. 查询需要发送晚安问候的群组（greeting=1）
            List<String> groupIds = queryGroupIdsWithMorningGreeting();
            if (groupIds == null || groupIds.isEmpty()) {
                log.warn("[EveningGreetingLogic] 没有需要发送晚安问候的群组");
                return;
            }

            log.info("[EveningGreetingLogic] 查询到 {} 个需要发送晚安问候的群组", groupIds.size());

            // 2. 获取最新的晚安问候文本
            String eveningText = queryLatestEveningText();
            if (eveningText == null || eveningText.isEmpty()) {
                log.warn("[EveningGreetingLogic] 没有晚安问候文本");
                return;
            }

            //log.info("[EveningGreetingLogic] 获取晚安问候文本，长度: {}", eveningText.length());

            // 3. 依次向每个群组发送问候，间隔 1~3 秒
            for (int i = 0; i < groupIds.size(); i++) {
                String groupId = groupIds.get(i);
                try {
                    sendMessageToGroup(groupId, eveningText);
                    //log.info("[EveningGreetingLogic] 晚安问候已发送到群组: {}", groupId);

                    // 如果不是最后一个群组，则随机等待 1~3 秒
                    if (i < groupIds.size() - 1) {
                        long delayMillis = 1000 + random.nextInt(2001); // 1000~3000 毫秒
                        log.debug("[EveningGreetingLogic] 等待 {} 毫秒后发送下一个群组的晚安问候", delayMillis);
                        Thread.sleep(delayMillis);
                    }
                } catch (Exception e) {
                    log.error("[EveningGreetingLogic] 发送晚安问候到群组 {} 失败", groupId, e);
                }
            }

            log.info("[EveningGreetingLogic] 所有晚安问候发送完成");

        } catch (Exception e) {
            log.error("[EveningGreetingLogic] 发送每日晚安问候异常", e);
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
            //log.info("[EveningGreetingLogic] 查询到 {} 个群组", groupIds.size());
            return groupIds;
        } catch (Exception e) {
            log.error("[EveningGreetingLogic] 查询群组列表异常", e);
            return null;
        }
    }

    /**
     * 查询 daliy_greeting 表最新一条数据的 evening_text
     * 
     * @return evening_text 文本
     */
    private String queryLatestEveningText() {
        try {
            String sql = "SELECT evening_text FROM daliy_greeting ORDER BY id DESC LIMIT 1";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);
            
            if (results != null && !results.isEmpty()) {
                Object eveningTextObj = results.get(0).get("evening_text");
                if (eveningTextObj != null) {
                    return eveningTextObj.toString();
                }
            }
            
            log.warn("[EveningGreetingLogic] 没有找到晚安问候文本");
            return null;
        } catch (Exception e) {
            log.error("[EveningGreetingLogic] 查询晚安问候文本异常", e);
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

           // log.debug("[EveningGreetingLogic] 发送消息到群组 {} 的响应: {}", groupId, response);

        } catch (Exception e) {
            log.error("[EveningGreetingLogic] 发送消息到群组 {} 失败", groupId, e);
        }
    }
}
