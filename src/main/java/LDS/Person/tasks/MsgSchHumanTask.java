package LDS.Person.tasks;

import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.tasks.MsgSchLogic.RandomChatLogic;
import LDS.Person.tasks.MsgLisLogic.KeywordTriggerLogic;
import com.alibaba.fastjson2.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Random;

/**
 * 自动发送处理器 用于降低人机查封的概率
 * 在这里QQ机器人的包含定时任务
 */
@Component
public class MsgSchHumanTask {

    @Autowired
    private RandomChatLogic randomChatLogic;

    @Autowired
    private KeywordTriggerLogic keywordTriggerLogic;

    private static final Random random = new Random();

    /**
     * 记录最近一次活动的群聊信息
     * 在接收到群聊消息时调用此方法
     * 
     * @param message WebSocket 消息的 JSON 对象
     */
    public static void recordActiveGroupInfo(JSONObject message) {
        try {
            String messageType = message.getString("message_type");

            // 只处理群聊消息
            if (!"group".equals(messageType)) {
                return;
            }

            Long groupId = message.getLong("group_id");
            String groupName = message.getString("group_name");

            // 记录到 TaskFactory
            TaskFactory.recordActiveGroupChat(String.valueOf(groupId), groupName);
        } catch (Exception e) {
            // 记录失败不影响其他功能
        }
    }

    /**
     * 每分钟执行一次，检查是否需要触发随机对话
     * 执行时间：每小时内随机选择一个时间（9点到22点）
     */
    @Scheduled(cron = "0 * 9-21 * * *")
    public void scheduleRandomChat() {
        // 检查该任务是否启用
        if (!NapCatTaskIsOpen.isMsgSchHumanTask) {
            // System.out.println("isMsgSchTask未启用");
            return;
        }

        try {
            LocalDateTime now = LocalDateTime.now();
            LocalTime currentTime = now.toLocalTime();

            // 检查时间是否在 9:00 - 22:00 范围内
            if (currentTime.isBefore(LocalTime.of(9, 0)) ||
                    currentTime.isAfter(LocalTime.of(22, 0))) {
                return;
            }

            // 每小时内随机决定是否发送（概率为当前分钟数/60）
            // 这样保证每小时大约有一定概率触发
            int currentMinute = currentTime.getMinute();
            int randomValue = random.nextInt(60);

            if (randomValue == currentMinute) {
                System.out.println("[MsgSchTask] ！！！触发随机对话任务，当前时间: " + currentTime);
                System.out.println("[MsgSchTask] lastGroupId = " + TaskFactory.getLastActiveGroupId());

                // 50% 概率触发随机对话，50% 概率触发随机图片
                boolean sendChat = random.nextBoolean();
                if (sendChat) {
                    // 触发随机对话
                    System.out.println("[MsgSchTask]  选择发送随机对话");
                    randomChatLogic.generateAndSendRandomChat();
                } else {
                    // 触发随机图片
                    System.out.println("[MsgSchTask]  选择发送随机图片");
                    String groupId = TaskFactory.getLastActiveGroupId();
                    System.out.println("[MsgSchTask] 准备发送图片，groupId = " + groupId);
                    if (groupId != null && !groupId.isEmpty()) {
                        keywordTriggerLogic.triggerRandomImage(Long.parseLong(groupId));
                    } else {
                        System.out.println("[MsgSchTask]  groupId 为空，跳过发送图片");
                    }
                }
            }

        } catch (Exception e) {
            System.err.println("[MsgSchTask] 定时任务执行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
