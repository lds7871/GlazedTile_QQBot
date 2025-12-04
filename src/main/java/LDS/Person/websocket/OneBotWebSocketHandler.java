package LDS.Person.websocket;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * OneBot WebSocket 消息处理器 - 处理来自 OneBot 客户端的连接和消息
 * 参考: https://napneko.github.io/onebot/network
 */
@Component
public class OneBotWebSocketHandler extends TextWebSocketHandler {

    // 存储所有连接的 WebSocket 会话
    private static final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Autowired(required = false)
    private OneBotMessageService oneBotMessageService;

    /**
     * 客户端连接时调用
     */
    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        System.out.println("[WEBSOCKET] 客户端已连接: " + session.getId());
        System.out.println("[WEBSOCKET] 当前连接数: " + sessions.size());
    }

    /**
     * 收到客户端消息时调用
     */
    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            // 解析 JSON 消息
            JSONObject json = JSONObject.parseObject(payload);
            
            // 处理不同类型的消息
            String action = json.getString("action");
            Object data = json.get("data");
            long echo = json.getLongValue("echo");

            // 只输出非心跳消息（get_status 是心跳）
            boolean isHeartbeat = "get_status".equals(action);
            if (!isHeartbeat) {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");
                String timestamp = LocalDateTime.now().format(formatter);
                System.out.println("\n========== WebSocket 消息接收 ==========");
                System.out.println("[时间] " + timestamp);
                System.out.println("[操作] " + action);
                System.out.println("[Echo] " + echo);
                System.out.println("[完整消息] " + payload);
                System.out.println("=======================================\n");
            }

            // 构建响应消息
            JSONObject response = new JSONObject();
            response.put("status", "ok");
            response.put("retcode", 0);
            response.put("data", null);
            response.put("echo", echo);

            // 根据 action 类型处理消息
            switch (action) {
                case "get_status":
                    handleGetStatus(response, echo);
                    break;
                case "get_version":
                    handleGetVersion(response, echo);
                    break;
                case "get_login_info":
                    handleGetLoginInfo(response, echo);
                    break;
                default:
                    response.put("status", "failed");
                    response.put("retcode", 1404);
                    response.put("message", "不支持的API: " + action);
            }

            // 发送响应消息
            sendMessage(session, response.toJSONString());

            // 如果有业务服务，传递消息给业务处理
            if (oneBotMessageService != null) {
                oneBotMessageService.processMessage(json);
            }

        } catch (JSONException e) {
            System.err.println("[WEBSOCKET] JSON 解析错误: " + e.getMessage());
            System.out.println("[WEBSOCKET] 原始消息: " + payload);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("status", "failed");
            errorResponse.put("retcode", 400);
            errorResponse.put("message", "JSON 解析错误");
            sendMessage(session, errorResponse.toJSONString());
        }
    }

    /**
     * 处理 get_status 请求 - 获取机器人状态
     */
    private void handleGetStatus(JSONObject response, long echo) {
        JSONObject data = new JSONObject();
        data.put("online", true);
        data.put("good", true);
        response.put("data", data);
        // 心跳消息不输出
    }

    /**
     * 处理 get_version 请求 - 获取版本信息
     */
    private void handleGetVersion(JSONObject response, long echo) {
        JSONObject data = new JSONObject();
        data.put("impl", "NapCat");
        data.put("version", "1.0.0");
        data.put("onebot_version", "11");
        response.put("data", data);
    }

    /**
     * 处理 get_login_info 请求 - 获取登录信息
     */
    private void handleGetLoginInfo(JSONObject response, long echo) {
        JSONObject data = new JSONObject();
        data.put("user_id", 3050000000L);
        data.put("nickname", "OneBot");
        response.put("data", data);
    }

    /**
     * 客户端连接关闭时调用
     */
    @Override
    public void afterConnectionClosed(WebSocketSession session, org.springframework.web.socket.CloseStatus status) {
        sessions.remove(session);
        System.out.println("[WEBSOCKET] 客户端已断开连接: " + session.getId());
        System.out.println("[WEBSOCKET] 当前连接数: " + sessions.size());
    }

    /**
     * 发生错误时调用
     */
    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        System.err.println("[WEBSOCKET] 传输错误: " + exception.getMessage());
        exception.printStackTrace();
    }

    /**
     * 发送消息给特定客户端
     */
    public void sendMessage(WebSocketSession session, String message) throws IOException {
        if (session != null && session.isOpen()) {
            synchronized (session) {
                session.sendMessage(new TextMessage(message));
            }
        }
    }

    /**
     * 广播消息给所有连接的客户端
     */
    public void broadcastMessage(String message) throws IOException {
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        }
    }

    /**
     * 静态方法：广播消息给所有连接的客户端（用于远程客户端转发）
     */
    public static void broadcastToClients(String message) throws IOException {
        // 获取所有会话，将消息转发给所有连接的客户端
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                synchronized (session) {
                    session.sendMessage(new TextMessage(message));
                }
            }
        }
    }

    /**
     * 获取当前连接数
     */
    public int getConnectionCount() {
        return sessions.size();
    }
}
