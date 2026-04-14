package LDS.Person.config;

import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 任务开关配置管理
 * 
 * 简化版本：使用静态布尔值控制任务开关，无数据库依赖
 */
@Component
public class NapCatTaskIsOpen {
    public static boolean isMsgLisATTask = true;
    public static boolean isMsgSchHumanTask = true;
    public static boolean isMsgLisCmdTask = true;

    private static final Logger log = LoggerFactory.getLogger(NapCatTaskIsOpen.class);

    public NapCatTaskIsOpen() {
        log.info("NapCatTaskIsOpen 初始化完成: @监听任务={}, 随机模拟任务={}, 指令监听任务={}",
                isMsgLisATTask, isMsgSchHumanTask, isMsgLisCmdTask);

    }
}
