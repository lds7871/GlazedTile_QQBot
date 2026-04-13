package LDS.Person.websocket;

import java.net.URI;

import LDS.Person.config.ConfigManager;
import LDS.Person.tasks.MsgLisATTask;
import LDS.Person.util.OneBotMessageFormatter;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.handshake.ServerHandshake;

/**
 * OneBot 远程客户端 - 连接到远程 NapCat 服务器并转发消息到 Spring WebSocket
 * 架构：NapCat (ws://remote:3001?access_token=xxx) -> RemoteOneBotClient
 * -> Spring Server (ws://localhost:8090/onebot)
 */
@Slf4j
public class RemoteOneBotClient extends BaseWebSocketClient {

    private static final String WS_URL_REMOTE;
    private static MsgLisATTask msgLisATTask;

    static {
        WS_URL_REMOTE = ConfigManager.getInstance().getWsUrlRemote();
        log.info("远程 WebSocket URL: {}", WS_URL_REMOTE);
    }

    /**
     * 设置消息监听任务（由 Spring 容器注入）
     */
    public static void setMessageListenerTask(MsgLisATTask task) {
        msgLisATTask = task;
    }

    @Override
    protected String getClientName() {
        return "NapCat-Remote";
    }

    @Override
    protected String getWebSocketUrl() {
        return WS_URL_REMOTE;
    }

    @Override
    protected BaseWebSocketClientHandler createWebSocketHandler(URI uri) {
        return new RemoteHandler(uri);
    }

    /**
     * 内部处理器 - 处理远程 NapCat 消息并转发
     */
    private static class RemoteHandler extends BaseWebSocketClientHandler {

        RemoteHandler(URI uri) {
            super(uri);
        }

        @Override
        public void onOpen(ServerHandshake handshakeData) {
            super.onOpen(handshakeData);
            log.info("[REMOTE] 远程连接已打开，状态码: {}", handshakeData.getHttpStatus());
        }

        @Override
        public void onMessage(String message) {
            try {
                JSONObject json = JSONObject.parseObject(message);
                String postType = json.getString("post_type");
                String metaEventType = json.getString("meta_event_type");

                // 屏蔽心跳消息
                boolean isHeartbeat = WebSocketConstants.POST_TYPE_META_EVENT.equals(postType)
                        && WebSocketConstants.META_EVENT_TYPE_HEARTBEAT.equals(metaEventType);

                // 屏蔽 API 响应消息
                boolean isStatusResponse = json.containsKey("status") && json.containsKey("retcode")
                        && json.containsKey("echo");

                if (!isHeartbeat && !isStatusResponse) {
                    String formattedMessage = OneBotMessageFormatter.formatMessage(json);
                    log.info("[REMOTE] {}", formattedMessage);

                    // 触发消息监听任务
                    if (msgLisATTask != null) {
                        msgLisATTask.handleMessage(json);
                    }
                }

                // 转发到 Spring WebSocket 客户端
                OneBotWebSocketHandler.broadcastToClients(message);

            } catch (Exception e) {
                log.error("[REMOTE] 处理消息出错: {}", e.getMessage(), e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            super.onClose(code, reason, remote);
            log.info("[REMOTE] 远程连接已关闭，代码: {}，原因: {}", code, reason);
        }
    }

    /**
     * 主函数 - 用于独立运行
     */
    public static void main(String[] args) {
        RemoteOneBotClient client = new RemoteOneBotClient();
        try {
            client.start();
            log.info("按 Ctrl+C 退出程序...");
            Thread.currentThread().join();
        } catch (Exception e) {
            log.error("程序运行出错: {}", e.getMessage(), e);
        } finally {
            client.stop();
        }
    }
}
