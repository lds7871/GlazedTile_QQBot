package LDS.Person.websocket;

import com.alibaba.fastjson2.JSONObject;
import com.alibaba.fastjson2.JSONException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * OneBot WebSocket 消息处理器 - 处理来自 OneBot 客户端的连接和消息
 * 参考: https://napneko.github.io/onebot/network
 */
@Component
@Slf4j
public class OneBotWebSocketHandler extends TextWebSocketHandler {

    private static final CopyOnWriteArraySet<WebSocketSession> sessions = new CopyOnWriteArraySet<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.add(session);
        log.info("客户端已连接: {}，当前连接数: {}", session.getId(), sessions.size());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();

        try {
            JSONObject json = JSONObject.parseObject(payload);
            String action = json.getString("action");
            long echo = json.getLongValue("echo");

            // 心跳消息不输出日志
            if (!WebSocketConstants.ACTION_GET_STATUS.equals(action)) {
                log.debug("WebSocket 消息接收 - 操作: {}, Echo: {}", action, echo);
            }

            // 构建响应
            JSONObject response = new JSONObject();
            response.put("status", "ok");
            response.put("retcode", 0);
            response.put("data", null);
            response.put("echo", echo);

            switch (action != null ? action : "") {
                case "get_status":
                    JSONObject statusData = new JSONObject();
                    statusData.put("online", true);
                    statusData.put("good", true);
                    response.put("data", statusData);
                    break;
                case "get_version":
                    JSONObject versionData = new JSONObject();
                    versionData.put("impl", "NapCat");
                    versionData.put("version", "1.0.0");
                    versionData.put("onebot_version", "11");
                    response.put("data", versionData);
                    break;
                case "get_login_info":
                    JSONObject loginData = new JSONObject();
                    loginData.put("user_id", 3050000000L);
                    loginData.put("nickname", "OneBot");
                    response.put("data", loginData);
                    break;
                default:
                    response.put("status", "failed");
                    response.put("retcode", 1404);
                    response.put("message", "不支持的API: " + action);
            }

            sendMessage(session, response.toJSONString());

        } catch (JSONException e) {
            log.error("JSON 解析错误: {}", e.getMessage());
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("status", "failed");
            errorResponse.put("retcode", 400);
            errorResponse.put("message", "JSON 解析错误");
            sendMessage(session, errorResponse.toJSONString());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
        log.info("客户端已断开: {}，当前连接数: {}", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输错误: {}", exception.getMessage(), exception);
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
            sendMessage(session, message);
        }
    }

    /**
     * 静态方法：广播消息给所有连接的客户端（用于远程客户端转发）
     */
    public static void broadcastToClients(String message) throws IOException {
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
