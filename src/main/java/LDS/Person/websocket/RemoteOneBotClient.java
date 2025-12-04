package LDS.Person.websocket;

import java.net.URI;

import LDS.Person.websocket.base.BaseWebSocketClient;
import LDS.Person.websocket.base.BaseWebSocketClientHandler;
import LDS.Person.websocket.config.WebSocketConstants;

/**
 * OneBot 远程客户端 - 连接到远程 NapCat 服务器并转发消息到 Spring WebSocket
 * 架构：NapCat (ws://115.190.170.56:3001) -> RemoteOneBotClient -> Spring Server (ws://localhost:8090/onebot)
 */
public class RemoteOneBotClient extends BaseWebSocketClient {

    @Override
    protected String getClientName() {
        return "NapCat-Remote";
    }

    @Override
    protected String getWebSocketUrl() {
        return WebSocketConstants.REMOTE_NAPCAT_URL;
    }

    @Override
    protected BaseWebSocketClientHandler createWebSocketHandler(URI uri) {
        return new RemoteWebSocketClientHandler(uri);
    }

    /**
     * 主函数 - 用于独立运行
     */
    public static void main(String[] args) {
        RemoteOneBotClient client = new RemoteOneBotClient();
        try {
            // 启动客户端
            client.start();

            // 保持连接
            System.out.println(WebSocketConstants.LOG_PREFIX_INFO + " 按 Ctrl+C 退出程序...");
            Thread.currentThread().join();

        } catch (Exception e) {
            System.err.println(WebSocketConstants.LOG_PREFIX_ERROR + " 程序运行出错: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.stop();
        }
    }
}

