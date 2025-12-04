package LDS.Person.websocket;

import java.net.URI;

import LDS.Person.tasks.MsgLisATTask;
import LDS.Person.tasks.MsgLisKeyWordTask;
import LDS.Person.tasks.MsgLisVipCmdTask;
import LDS.Person.tasks.MsgLisUserCmdTask;
import org.java_websocket.handshake.ServerHandshake;

import com.alibaba.fastjson2.JSONObject;

import LDS.Person.util.OneBotMessageFormatter;
import LDS.Person.websocket.base.BaseWebSocketClientHandler;
import LDS.Person.websocket.config.WebSocketConstants;

/**
 * 远程 WebSocket 客户端处理器 - 连接到远程 NapCat 服务器
 * 接收消息并转发给 Spring 服务器的 OneBotWebSocketHandler
 * 同时监听特定消息并触发自动回复任务
 */
public class RemoteWebSocketClientHandler extends BaseWebSocketClientHandler {

    private static MsgLisATTask msgLisATTask;
    private static MsgLisKeyWordTask msgLisKeyWordTask;
    private static MsgLisVipCmdTask msgLisVipCmdTask;
    private static MsgLisUserCmdTask msgLisUserCmdTask;

    public RemoteWebSocketClientHandler(URI uri) {
        super(uri);
    }

    /**
     * 设置消息监听任务（由 Spring 容器注入）
     */
    public static void setMessageListenerTask(MsgLisATTask task) {
        msgLisATTask = task;
    }

    /**
     * 设置关键词监听任务（由 Spring 容器注入）
     */
    public static void setMessageListenerKeyWordTask(MsgLisKeyWordTask task) {
        msgLisKeyWordTask = task;
    }

    /**
     * 设置 VIP 白名单消息监听任务（由 Spring 容器注入）
     */
    public static void setMsgLisVipATTask(MsgLisVipCmdTask task) {
        msgLisVipCmdTask = task;
    }

    /**
     * 设置用户公共指令监听任务（由 Spring 容器注入）
     */
    public static void setMsgLisUserCmdTask(MsgLisUserCmdTask task) {
        msgLisUserCmdTask = task;
    }

    @Override
    public void onMessage(String message) {
        try {
            // 解析消息
            JSONObject json = JSONObject.parseObject(message);
            String postType = json.getString("post_type");
            String metaEventType = json.getString("meta_event_type");

            // 屏蔽 heartbeat 消息（心跳事件）
            boolean isHeartbeat = WebSocketConstants.POST_TYPE_META_EVENT.equals(postType)
                    && WebSocketConstants.META_EVENT_TYPE_HEARTBEAT.equals(metaEventType);

            // 屏蔽 get_status 响应消息（echo 字段存在且 status/retcode 字段存在表示是 API 响应）
            boolean isStatusResponse = json.containsKey("status") && json.containsKey("retcode")
                    && json.containsKey("echo");

            if (!isHeartbeat && !isStatusResponse) {
                // 使用消息格式化工具来简化消息显示
                String formattedMessage = OneBotMessageFormatter.formatMessage(json);
                System.out.println(WebSocketConstants.LOG_PREFIX_REMOTE + " " + formattedMessage);

                // 触发消息监听任务（自动回复处理）
                if (msgLisATTask != null) {
                    msgLisATTask.handleMessage(json);
                }

                // 触发关键词监听任务
                if (msgLisKeyWordTask != null) {
                    msgLisKeyWordTask.handleMessage(json);
                }

                // 触发 VIP 白名单消息监听任务
                if (msgLisVipCmdTask != null) {
                    msgLisVipCmdTask.handleMessage(json);
                }

                // 触发用户公共指令监听任务
                if (msgLisUserCmdTask != null) {
                    msgLisUserCmdTask.handleMessage(json);
                }
            }

            // 将消息转发到 Spring 服务器的客户端
            OneBotWebSocketHandler.broadcastToClients(message);

        } catch (Exception e) {
            logError("处理消息出错: " + e.getMessage());
        }
    }

    @Override
    protected void logConnectionOpened(ServerHandshake handshakeData) {
        System.out.println(WebSocketConstants.LOG_PREFIX_REMOTE + " 远程连接已打开，状态码: " + handshakeData.getHttpStatus());
    }

    @Override
    protected void logConnectionClosed(int code, String reason, boolean remote) {
        System.out.println(WebSocketConstants.LOG_PREFIX_REMOTE + " 远程连接已关闭，代码: " + code + "，原因: " + reason);
    }

    @Override
    protected void logError(String msg) {
        System.err.println(WebSocketConstants.LOG_PREFIX_REMOTE + " " + msg);
    }
}
