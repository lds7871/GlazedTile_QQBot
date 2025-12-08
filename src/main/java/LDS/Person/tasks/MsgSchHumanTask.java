package LDS.Person.tasks;

import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.tasks.MsgSchLogic.RandomChatLogic;
import LDS.Person.tasks.MsgLisLogic.KeywordTriggerLogic;
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
     * 每分钟执行一次，检查是否需要触发随机对话
     * 执行时间：每小时内随机选择一个时间（9点到22点）
     */
    @Scheduled(cron = "0 * 9-21 * * *")
    public void scheduleRandomChat() {
        // 检查该任务是否启用
        if (!NapCatTaskIsOpen.isMsgSchTask) {
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

                // 50% 概率触发随机对话，50% 概率触发随机图片
                if (random.nextBoolean()) {
                    // 触发随机对话
                    randomChatLogic.generateAndSendRandomChat();
                } else {
                    // 触发随机图片
                    String groupId = RandomChatLogic.getLastGroupId();
                    if (groupId != null && !groupId.isEmpty()) {
                        keywordTriggerLogic.triggerRandomImage(Long.parseLong(groupId));
                    }
                }
            }
            
        } catch (Exception e) {
            System.err.println("[MsgSchTask] 定时任务执行出错: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
