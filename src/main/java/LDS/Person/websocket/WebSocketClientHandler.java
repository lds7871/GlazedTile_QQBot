package LDS.Person.websocket;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * WebSocket 处理器 - 处理连接、消息接收和发送
 */
public class WebSocketClientHandler extends WebSocketClient {

    private CountDownLatch connectionLatch;
    private volatile boolean isConnected = false;

    public WebSocketClientHandler(URI uri) {
        super(uri);
        this.connectionLatch = new CountDownLatch(1);
    }

    /**
     * 等待连接建立
     */
    public boolean waitForConnection(long timeoutMs) throws InterruptedException {
        return connectionLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onOpen(ServerHandshake handshakeData) {
        isConnected = true;
        System.out.println("[WEBSOCKET] 连接已打开，状态码: " + handshakeData.getHttpStatus());
        connectionLatch.countDown();
    }

    @Override
    public void onMessage(String message) {
        System.out.println("[WEBSOCKET] 收到消息: " + message);
        
        // 解析 OneBot 响应消息
        try {
            // 简单的 JSON 解析 - 查找关键字段
            if (message.contains("\"status\":\"ok\"")) {
                System.out.println("[WEBSOCKET] API 调用成功");
            } else if (message.contains("\"status\":\"failed\"")) {
                // 提取错误消息
                int msgStart = message.indexOf("\"message\":\"");
                if (msgStart != -1) {
                    int msgEnd = message.indexOf("\"", msgStart + 11);
                    if (msgEnd != -1) {
                        String errorMsg = message.substring(msgStart + 11, msgEnd);
                        System.out.println("[WEBSOCKET] API 调用失败: " + errorMsg);
                    }
                }
            }
            
            // 检查是否是 meta_event (心跳响应)
            // if (message.contains("\"meta_event_type\"")) {
            //     System.out.println("[WEBSOCKET] 收到元事件");
            // }
        } catch (Exception e) {
            System.out.println("[WEBSOCKET] 消息解析异常: " + e.getMessage());
        }
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        isConnected = false;
        System.out.println("[WEBSOCKET] 连接已关闭，代码: " + code + "，原因: " + reason + "，远程: " + remote);
    }

    @Override
    public void onError(Exception ex) {
        System.err.println("[WEBSOCKET] 错误: " + ex.getMessage());
        ex.printStackTrace();
    }

    /**
     * 发送文本消息
     */
    public void sendMessage(String message) throws Exception {
        if (isConnected && this.isOpen()) {
            this.send(message);
        } else {
            throw new Exception("WebSocket 未连接或已关闭");
        }
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return isConnected && this.isOpen();
    }
}
