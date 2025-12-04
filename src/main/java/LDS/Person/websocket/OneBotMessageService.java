package LDS.Person.websocket;

import com.alibaba.fastjson2.JSONObject;

/**
 * OneBot 消息业务处理服务接口
 */
public interface OneBotMessageService {

    /**
     * 处理来自 OneBot 客户端的消息
     * 
     * @param message 消息内容（JSON 格式）
     */
    void processMessage(JSONObject message);

    /**
     * 处理机器人事件
     * 
     * @param event 事件内容（JSON 格式）
     */
    void handleEvent(JSONObject event);
}
