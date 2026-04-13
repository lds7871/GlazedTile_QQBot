package LDS.Person.websocket;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;

/**
 * WebSocket 客户端基础类 - 提供心跳、连接管理等通用功能
 */
@Slf4j
public abstract class BaseWebSocketClient {

    protected BaseWebSocketClientHandler wsHandler;
    protected ScheduledExecutorService executorService;
    protected volatile boolean isConnected = false;

    protected BaseWebSocketClient() {
        this.executorService = Executors.newScheduledThreadPool(2, r -> {
            Thread t = new Thread(r);
            t.setName("WebSocketClient-" + getClientName());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * 启动连接
     */
    public synchronized void start() throws Exception {
        try {
            String url = getWebSocketUrl();
            log.info("[{}] 正在连接到 WebSocket: {}", getClientName(), url);

            URI uri = new URI(url);
            wsHandler = createWebSocketHandler(uri);
            wsHandler.connect();

            if (wsHandler.waitForConnection(WebSocketConstants.CONNECTION_TIMEOUT_MS)) {
                isConnected = true;
                log.info("[{}] WebSocket 连接成功!", getClientName());
                startHeartbeat();
            } else {
                log.error("[{}] 连接超时", getClientName());
                throw new Exception("WebSocket 连接超时");
            }

        } catch (Exception e) {
            log.error("[{}] 连接失败: {}", getClientName(), e.getMessage());
            throw e;
        }
    }

    /**
     * 启动心跳任务
     */
    protected void startHeartbeat() {
        executorService.scheduleAtFixedRate(() -> {
            try {
                if (isConnected && wsHandler.isConnected()) {
                    long echo = System.currentTimeMillis();
                    String heartbeatMsg = buildHeartbeatMessage(echo);
                    wsHandler.sendMessage(heartbeatMsg);
                }
            } catch (Exception e) {
                log.error("[{}] 发送心跳失败: {}", getClientName(), e.getMessage());
            }
        }, WebSocketConstants.HEARTBEAT_INTERVAL_MS, WebSocketConstants.HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 停止连接
     */
    public synchronized void stop() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (wsHandler != null) {
            try {
                wsHandler.close();
                isConnected = false;
                log.info("[{}] WebSocket 连接已关闭", getClientName());
            } catch (Exception e) {
                log.error("[{}] 关闭连接失败: {}", getClientName(), e.getMessage());
            }
        }
    }

    /**
     * 检查是否已连接
     */
    public boolean isConnected() {
        return isConnected && wsHandler != null && wsHandler.isConnected();
    }

    // ==================== 子类必须实现的方法 ====================

    protected abstract String getClientName();
    protected abstract String getWebSocketUrl();
    protected abstract BaseWebSocketClientHandler createWebSocketHandler(URI uri);

    /**
     * 构建心跳消息
     */
    protected String buildHeartbeatMessage(long echo) {
        return "{\"action\":\"" + WebSocketConstants.ACTION_GET_STATUS 
            + "\",\"params\":{},\"echo\":" + echo + "}";
    }
}
