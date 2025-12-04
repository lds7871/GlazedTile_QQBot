package LDS.Person.websocket.base;

import java.net.URI;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import LDS.Person.websocket.config.WebSocketConstants;

/**
 * WebSocket 客户端基础类 - 提供心跳、连接管理等通用功能
 */
public abstract class BaseWebSocketClient {

    protected BaseWebSocketClientHandler wsHandler;
    protected ScheduledExecutorService executorService;
    protected volatile boolean isConnected = false;

    public BaseWebSocketClient() {
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
            logInfo("正在连接到 WebSocket: " + url);

            URI uri = new URI(url);
            wsHandler = createWebSocketHandler(uri);
            wsHandler.connect();

            // 等待连接建立
            if (wsHandler.waitForConnection(WebSocketConstants.CONNECTION_TIMEOUT_MS)) {
                isConnected = true;
                logSuccess("WebSocket 连接成功!");
                startHeartbeat();
            } else {
                logError("连接超时");
                throw new Exception("WebSocket 连接超时");
            }

        } catch (Exception e) {
            logError("连接失败: " + e.getMessage());
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
                    // 心跳消息不输出
                }
            } catch (Exception e) {
                logError("发送心跳失败: " + e.getMessage());
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
                logInfo("WebSocket 连接已关闭");
            } catch (Exception e) {
                logError("关闭连接失败: " + e.getMessage());
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

    /**
     * 获取客户端名称
     */
    protected abstract String getClientName();

    /**
     * 获取 WebSocket URL
     */
    protected abstract String getWebSocketUrl();

    /**
     * 创建 WebSocket 处理器
     */
    protected abstract BaseWebSocketClientHandler createWebSocketHandler(URI uri);

    /**
     * 构建心跳消息
     */
    protected String buildHeartbeatMessage(long echo) {
        return "{\"action\":\"" + WebSocketConstants.ACTION_GET_STATUS 
            + "\",\"params\":{},\"echo\":" + echo + "}";
    }

    // ==================== 日志方法 ====================

    protected void logInfo(String msg) {
        System.out.println(WebSocketConstants.LOG_PREFIX_INFO + " " + msg);
    }

    protected void logSuccess(String msg) {
        System.out.println(WebSocketConstants.LOG_PREFIX_SUCCESS + " " + msg);
    }

    protected void logError(String msg) {
        System.err.println(WebSocketConstants.LOG_PREFIX_ERROR + " " + msg);
    }
}
