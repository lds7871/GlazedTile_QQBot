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
    public static boolean isMsgSchTask = true;

    private static final Logger log = LoggerFactory.getLogger(NapCatTaskIsOpen.class);

    public NapCatTaskIsOpen() {
        log.info("NapCatTaskIsOpen 初始化完成: isMsgLisATTask={}, isMsgSchTask={}", 
                isMsgLisATTask, isMsgSchTask);
    }
}
