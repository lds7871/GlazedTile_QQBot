package LDS.Person.websocket;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * WebSocket 客户端基类 - 提供通用的连接、收发消息功能
 */
@Slf4j
public abstract class BaseWebSocketClientHandler extends WebSocketClient {

    protected CountDownLatch connectionLatch;
    protected volatile boolean isConnected = false;

    protected BaseWebSocketClientHandler(URI uri) {
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
        log.info("WebSocket 连接已打开，状态码: {}", handshakeData.getHttpStatus());
        connectionLatch.countDown();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        isConnected = false;
        log.info("WebSocket 连接已关闭，代码: {}，原因: {}，远程: {}", code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        log.error("WebSocket 错误: {}", ex.getMessage(), ex);
    }

    /**
     * 发送消息
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
