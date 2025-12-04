package LDS.Person.tasks;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.tasks.MsgSchLogic.MorningGreetingLogic;
import LDS.Person.tasks.MsgSchLogic.EveningGreetingLogic;

/**
 * 每日问候定时任务调度器
 * 负责定时触发早安和晚安问候的发送
 */
@Component
@Slf4j
public class MsgSchGreetingTask {

    @Autowired
    private MorningGreetingLogic morningGreetingLogic;

    @Autowired
    private EveningGreetingLogic eveningGreetingLogic;

    /**
     * 定时任务：每天 08:00:00 执行
     * cron 表达式：秒 分 小时 日 月 周几
     * "0 0 8 * * ?" 表示每天 08:00:00 执行
     */
    @Scheduled(cron = "0 0 8 * * ?")
    public void sendMorningGreeting() {
        // 检查任务是否启用
        if (!NapCatTaskIsOpen.isMsgSchTask) {
            log.debug("[MsgSchGreetingTask] 早安任务未启用");
            return;
        }

        try {
            log.info("[MsgSchGreetingTask] 触发每日早安问候定时任务");
            morningGreetingLogic.executeMorningGreeting();
        } catch (Exception e) {
            log.error("[MsgSchGreetingTask] 执行早安问候任务异常", e);
        }
    }

    /**
     * 定时任务：每天 23:00:00 执行
     * cron 表达式：秒 分 小时 日 月 周几
     * "0 0 23 * * ?" 表示每天 23:00:00 执行
     */
    @Scheduled(cron = "0 0 23 * * ?")
    public void sendEveningGreeting() {
        // 检查任务是否启用
        if (!NapCatTaskIsOpen.isMsgSchTask) {
            log.debug("[MsgSchGreetingTask] 晚安任务未启用");
            return;
        }

        try {
            log.info("[MsgSchGreetingTask] 触发每日晚安问候定时任务");
            eveningGreetingLogic.executeEveningGreeting();
        } catch (Exception e) {
            log.error("[MsgSchGreetingTask] 执行晚安问候任务异常", e);
        }
    }
}
