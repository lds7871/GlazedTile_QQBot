package LDS.Person.websocket.base;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

/**
 * WebSocket 客户端基类 - 提供通用的连接、收发消息功能
 */
public abstract class BaseWebSocketClientHandler extends WebSocketClient {

    protected CountDownLatch connectionLatch;
    protected volatile boolean isConnected = false;

    public BaseWebSocketClientHandler(URI uri) {
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
        logConnectionOpened(handshakeData);
        connectionLatch.countDown();
    }

    @Override
    public void onClose(int code, String reason, boolean remote) {
        isConnected = false;
        logConnectionClosed(code, reason, remote);
    }

    @Override
    public void onError(Exception ex) {
        logError(ex);
        ex.printStackTrace();
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

    // ==================== 子类实现的日志方法 ====================

    /**
     * 连接打开时的日志
     */
    protected abstract void logConnectionOpened(ServerHandshake handshakeData);

    /**
     * 连接关闭时的日志
     */
    protected abstract void logConnectionClosed(int code, String reason, boolean remote);

    /**
     * 错误日志 (String)
     */
    protected void logError(String msg) {
        System.err.println("[WEBSOCKET] " + msg);
    }

    /**
     * 错误日志 (Exception)
     */
    protected void logError(Exception ex) {
        System.err.println("[WEBSOCKET] 错误: " + ex.getMessage());
    }
}
