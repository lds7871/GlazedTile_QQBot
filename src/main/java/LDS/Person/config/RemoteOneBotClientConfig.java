package LDS.Person.config;

import LDS.Person.tasks.MsgLisKeyWordTask;
import LDS.Person.tasks.MsgLisVipCmdTask;
import LDS.Person.tasks.MsgLisUserCmdTask;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.ApplicationContext;
import org.springframework.beans.factory.annotation.Autowired;

import LDS.Person.tasks.MsgLisATTask;
import LDS.Person.websocket.RemoteOneBotClient;
import LDS.Person.websocket.RemoteWebSocketClientHandler;

import java.io.InputStream;
import java.util.Properties;

/**
 * 远程 OneBot 客户端启动配置
 * 在 Spring 应用启动后自动连接到远程 NapCat 服务器
 */
@Configuration
public class RemoteOneBotClientConfig {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private MsgLisATTask msgLisATTask;

    @Autowired
    private MsgLisKeyWordTask msgLisKeyWordTask;

    @Autowired
    private MsgLisVipCmdTask msgLisVipCmdTask;

    @Autowired
    private MsgLisUserCmdTask msgLisUserCmdTask;

    private static boolean NCAT_IS_OPEN;

    // 静态初始化块：从 config.properties 读取配置
    static {
        Properties props = new Properties();
        try (InputStream input = RemoteOneBotClientConfig.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                String napCatIsOpenStr = props.getProperty("NapCatIsOpen", "true");
                NCAT_IS_OPEN = Boolean.parseBoolean(napCatIsOpenStr);
                System.out.println("[CONFIG] NapCatIsOpen: " + NCAT_IS_OPEN);
            } else {
                System.out.println("[WARN] config.properties not found, NapCat will be enabled by default");
                NCAT_IS_OPEN = true;
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to load config.properties: " + e.getMessage());
            NCAT_IS_OPEN = true;
        }
    }

    /**
     * 创建 RemoteOneBotClient Bean
     */
    @Bean
    @ConditionalOnMissingBean
    public RemoteOneBotClient remoteOneBotClient() {
        return new RemoteOneBotClient();
    }

    /**
     * Spring 容器启动完毕后，自动启动远程客户端连接
     */
    @EventListener(ContextRefreshedEvent.class)
    public void startRemoteClient() {
        // 检查 NapCat 是否启用
        if (!NCAT_IS_OPEN) {
            System.out.println("[CONFIG] NapCat 已禁用 (NapCatIsOpen=false)，跳过启动远程 OneBot 客户端");
            return;
        }

        RemoteOneBotClient client = applicationContext.getBean(RemoteOneBotClient.class);
        if (client == null) {
            return;
        }
        try {
            System.out.println("[CONFIG] 开始启动远程 OneBot 客户端...");

            // 注入消息监听任务到 WebSocket 处理器
            RemoteWebSocketClientHandler.setMessageListenerTask(msgLisATTask);
            RemoteWebSocketClientHandler.setMessageListenerKeyWordTask(msgLisKeyWordTask);
            RemoteWebSocketClientHandler.setMsgLisVipATTask(msgLisVipCmdTask);
            RemoteWebSocketClientHandler.setMsgLisUserCmdTask(msgLisUserCmdTask);
            System.out.println("[CONFIG] 消息监听任务已注入");

            if (!client.isConnected()) {
                client.start();
            }
            System.out.println("[CONFIG] 远程 OneBot 客户端已启动");
        } catch (Exception e) {
            System.err.println("[CONFIG] 启动远程客户端失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
