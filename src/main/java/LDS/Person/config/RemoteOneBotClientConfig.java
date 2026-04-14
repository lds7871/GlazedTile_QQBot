package LDS.Person.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;

import LDS.Person.tasks.MsgLisATTask;
import LDS.Person.tasks.MsgLisCmdTask;
import LDS.Person.tasks.MsgSchHumanTask;
import LDS.Person.websocket.RemoteOneBotClient;
import lombok.extern.slf4j.Slf4j;

/**
 * 远程 OneBot 客户端启动配置
 * 在 Spring 应用启动后自动连接到远程 NapCat 服务器
 */
@Configuration
@Slf4j
public class RemoteOneBotClientConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MsgLisATTask msgLisATTask;

    @Autowired
    private MsgLisCmdTask msgLisCmdTask;

    @Autowired
    private MsgSchHumanTask msgSchHumanTask;

    private static final boolean NCAT_IS_OPEN;

    static {
        NCAT_IS_OPEN = ConfigManager.getInstance().getBoolean("NapCatIsOpen", true);
        log.info("NapCatIsOpen: {}", NCAT_IS_OPEN);
    }

    @Bean
    @ConditionalOnMissingBean
    public RemoteOneBotClient remoteOneBotClient() {
        return new RemoteOneBotClient();
    }

    @EventListener(ContextRefreshedEvent.class)
    public void startRemoteClient() {
        if (!NCAT_IS_OPEN) {
            log.info("NapCat 已禁用，跳过启动远程 OneBot 客户端");
            return;
        }

        RemoteOneBotClient client = applicationContext.getBean(RemoteOneBotClient.class);
        try {
            log.info("开始启动远程 OneBot 客户端...");
            RemoteOneBotClient.setMessageListenerTask(msgLisATTask);
            RemoteOneBotClient.setMessageScheduleTask(msgSchHumanTask);
            log.info("消息调度任务已注入");
            log.info("消息监听任务已注入");
            RemoteOneBotClient.setMessageCmdTask(msgLisCmdTask);
            log.info("消息命令任务已注入");

            if (!client.isConnected()) {
                client.start();
            }
            log.info("远程 OneBot 客户端已启动");
        } catch (Exception e) {
            log.error("启动远程客户端失败: {}", e.getMessage(), e);
        }
    }
}
