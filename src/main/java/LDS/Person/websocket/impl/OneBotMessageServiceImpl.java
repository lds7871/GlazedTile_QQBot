package LDS.Person.websocket.impl;

import com.alibaba.fastjson2.JSONObject;
import org.springframework.stereotype.Service;

import LDS.Person.websocket.OneBotMessageService;

/**
 * OneBot 消息处理服务实现 - 处理来自 NapCat 的消息
 */
@Service
public class OneBotMessageServiceImpl implements OneBotMessageService {

    @Override
    public void processMessage(JSONObject message) {
        try {
            String action = message.getString("action");
            Object data = message.get("data");
            long echo = message.getLongValue("echo");

            System.out.println("[ONEBOT-SERVICE] 处理消息 - Action: " + action + ", Echo: " + echo);

            // 根据 action 类型进行业务处理
            if ("get_status".equals(action)) {
                System.out.println("[ONEBOT-SERVICE] 处理状态查询请求");
            } else if ("get_version".equals(action)) {
                System.out.println("[ONEBOT-SERVICE] 处理版本查询请求");
            } else if ("get_login_info".equals(action)) {
                System.out.println("[ONEBOT-SERVICE] 处理登录信息查询请求");
            } else {
                System.out.println("[ONEBOT-SERVICE] 处理其他类型消息: " + action);
            }

        } catch (Exception e) {
            System.err.println("[ONEBOT-SERVICE] 处理消息时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void handleEvent(JSONObject event) {
        try {
            String eventType = event.getString("event");
            System.out.println("[ONEBOT-SERVICE] 处理事件: " + eventType);

            // 根据事件类型进行业务处理
            switch (eventType) {
                case "message":
                    handleMessageEvent(event);
                    break;
                case "notice":
                    handleNoticeEvent(event);
                    break;
                case "request":
                    handleRequestEvent(event);
                    break;
                case "meta_event":
                    handleMetaEvent(event);
                    break;
                default:
                    System.out.println("[ONEBOT-SERVICE] 未知事件类型: " + eventType);
            }

        } catch (Exception e) {
            System.err.println("[ONEBOT-SERVICE] 处理事件时出错: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 处理消息事件
     */
    private void handleMessageEvent(JSONObject event) {
        String messageType = event.getString("message_type");
        String message = event.getString("message");
        System.out.println("[ONEBOT-SERVICE] 消息事件 - 类型: " + messageType + ", 内容: " + message);
    }

    /**
     * 处理通知事件
     */
    private void handleNoticeEvent(JSONObject event) {
        String noticeType = event.getString("notice_type");
        System.out.println("[ONEBOT-SERVICE] 通知事件 - 类型: " + noticeType);
    }

    /**
     * 处理请求事件
     */
    private void handleRequestEvent(JSONObject event) {
        String requestType = event.getString("request_type");
        System.out.println("[ONEBOT-SERVICE] 请求事件 - 类型: " + requestType);
    }

    /**
     * 处理元事件 (心跳响应等)
     */
    private void handleMetaEvent(JSONObject event) {
        String metaEventType = event.getString("meta_event_type");
        System.out.println("[ONEBOT-SERVICE] 元事件 - 类型: " + metaEventType);
    }
}
