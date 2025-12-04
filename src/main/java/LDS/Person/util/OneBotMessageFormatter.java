package LDS.Person.util;

import com.alibaba.fastjson2.JSONObject;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * OneBot 消息简化工具
 * 将复杂的 WebSocket 消息简化为易读的字符串格式
 */
public class OneBotMessageFormatter {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * 将 JSON 消息转换为简化的字符串格式
     * 
     * @param jsonMessage JSON 格式的消息
     * @return 简化后的消息字符串
     */
    public static String formatMessage(String jsonMessage) {
        try {
            JSONObject json = JSONObject.parseObject(jsonMessage);
            return formatMessage(json);
        } catch (Exception e) {
            return "消息解析失败: " + e.getMessage();
        }
    }

    /**
     * 将 JSONObject 消息转换为简化的字符串格式
     * 
     * @param json JSONObject 格式的消息
     * @return 简化后的消息字符串
     */
    public static String formatMessage(JSONObject json) {
        try {
            String postType = json.getString("post_type");
            
            // 根据消息类型进行不同的格式化
            if ("message".equals(postType)) {
                return formatChatMessage(json);
            } else if ("message_sent".equals(postType)) {
                return formatMessageSent(json);
            } else if ("notice".equals(postType)) {
                return formatNoticeMessage(json);
            } else if ("request".equals(postType)) {
                return formatRequestMessage(json);
            } else {
                return formatUnknownMessage(json);
            }
        } catch (Exception e) {
            return "消息格式化失败: " + e.getMessage();
        }
    }

    /**
     * 格式化聊天消息
     */
    private static String formatChatMessage(JSONObject json) {
        StringBuilder sb = new StringBuilder();
        
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String messageType = json.getString("message_type");
        Long messageId = json.getLong("message_id");
        Long userId = json.getLong("user_id");
        String rawMessage = json.getString("raw_message");
        
        sb.append("[").append(timestamp).append("] ");
        
        if ("group".equals(messageType)) {
            // 群聊消息
            Long groupId = json.getLong("group_id");
            String groupName = json.getString("group_name");
            
            JSONObject sender = json.getJSONObject("sender");
            String nickname = sender != null ? sender.getString("nickname") : "未知用户";
            String card = sender != null ? sender.getString("card") : "";
            String role = sender != null ? sender.getString("role") : "";
            
            sb.append("[群聊] ");
            sb.append("群ID:").append(groupId).append(" ");
            sb.append("群名:").append(groupName).append(" | ");
            
            if (card != null && !card.isEmpty()) {
                sb.append("昵称:").append(card).append(" ");
            } else {
                sb.append("昵称:").append(nickname).append(" ");
            }
            
            if (role != null && !role.isEmpty()) {
                sb.append("[").append(role).append("] ");
            }
            
            sb.append("| 消息ID:").append(messageId).append(" ");
            sb.append("用户ID:").append(userId).append(" ");
            sb.append("| 内容:").append(rawMessage);
            
        } else if ("private".equals(messageType)) {
            // 私聊消息
            JSONObject sender = json.getJSONObject("sender");
            String nickname = sender != null ? sender.getString("nickname") : "未知用户";
            
            sb.append("[私聊] ");
            sb.append("用户:").append(nickname).append("(").append(userId).append(") ");
            sb.append("| 消息ID:").append(messageId).append(" ");
            sb.append("| 内容:").append(rawMessage);
        }
        
        return sb.toString()+"\n";
    }

    /**
     * 格式化机器人自身发出的消息（message_sent 事件）
     */
    private static String formatMessageSent(JSONObject json) {
        StringBuilder sb = new StringBuilder();
        
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String messageType = json.getString("message_type");
        Long messageId = json.getLong("message_id");
        Long userId = json.getLong("user_id");
        String rawMessage = json.getString("raw_message");
        
        sb.append("[").append(timestamp).append("] ");
        sb.append("[自己发送] ");
        
        if ("group".equals(messageType)) {
            // 群聊消息
            Long groupId = json.getLong("group_id");
            String groupName = json.getString("group_name");
            
            JSONObject sender = json.getJSONObject("sender");
            String nickname = sender != null ? sender.getString("nickname") : "未知用户";
            String card = sender != null ? sender.getString("card") : "";
            String role = sender != null ? sender.getString("role") : "";
            
            sb.append("群ID:").append(groupId).append(" ");
            sb.append("群名:").append(groupName).append(" | ");
            
            if (card != null && !card.isEmpty()) {
                sb.append("昵称:").append(card).append(" ");
            } else {
                sb.append("昵称:").append(nickname).append(" ");
            }
            
            if (role != null && !role.isEmpty()) {
                sb.append("[").append(role).append("] ");
            }
            
            sb.append("| 消息ID:").append(messageId).append(" ");
            sb.append("| 内容:").append(rawMessage);
            
        } else if ("private".equals(messageType)) {
            // 私聊消息
            JSONObject sender = json.getJSONObject("sender");
            String nickname = sender != null ? sender.getString("nickname") : "未知用户";
            
            sb.append("用户:").append(nickname).append("(").append(userId).append(") ");
            sb.append("| 消息ID:").append(messageId).append(" ");
            sb.append("| 内容:").append(rawMessage);
        }
        
        return sb.toString()+"\n";
    }

    /**
     * 格式化通知消息
     */
    private static String formatNoticeMessage(JSONObject json) {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String noticeType = json.getString("notice_type");
        
        sb.append("[").append(timestamp).append("] ");
        sb.append("[通知] ").append(noticeType).append(" | ");
        
        if ("group_upload".equals(noticeType)) {
            Long groupId = json.getLong("group_id");
            Long userId = json.getLong("user_id");
            sb.append("群ID:").append(groupId).append(" 用户:").append(userId).append(" 上传了文件");
        } else if ("group_admin".equals(noticeType)) {
            Long groupId = json.getLong("group_id");
            Long userId = json.getLong("user_id");
            String subType = json.getString("sub_type");
            sb.append("群ID:").append(groupId).append(" 用户:").append(userId)
                    .append(" ").append("set".equals(subType) ? "被设为" : "被取消").append("管理员");
        } else {
            sb.append(json.toJSONString());
        }
        
        return sb.toString();
    }

    /**
     * 格式化请求消息
     */
    private static String formatRequestMessage(JSONObject json) {
        StringBuilder sb = new StringBuilder();
        String timestamp = LocalDateTime.now().format(FORMATTER);
        String requestType = json.getString("request_type");
        
        sb.append("[").append(timestamp).append("] ");
        sb.append("[请求] ").append(requestType);
        
        if ("friend".equals(requestType)) {
            Long userId = json.getLong("user_id");
            String comment = json.getString("comment");
            sb.append(" | 用户:").append(userId).append(" 请求添加好友");
            if (comment != null && !comment.isEmpty()) {
                sb.append(" 备注:").append(comment);
            }
        } else if ("group".equals(requestType)) {
            Long groupId = json.getLong("group_id");
            Long userId = json.getLong("user_id");
            String subType = json.getString("sub_type");
            String comment = json.getString("comment");
            
            sb.append(" | 群ID:").append(groupId).append(" 用户:").append(userId);
            if ("add".equals(subType)) {
                sb.append(" 申请加入群聊");
            } else if ("invite".equals(subType)) {
                sb.append(" 邀请进入群聊");
            }
            if (comment != null && !comment.isEmpty()) {
                sb.append(" 备注:").append(comment);
            }
        }
        
        return sb.toString();
    }

    /**
     * 格式化未知消息
     */
    private static String formatUnknownMessage(JSONObject json) {
        return "[未知消息] " + json.toJSONString();
    }

    /**
     * 生成简化的消息摘要（一行显示）
     * 包含: group_id, message_id, message_type, user_id, nickname, card, raw_message
     * 
     * @param json JSONObject 格式的消息
     * @return 简化的消息摘要字符串
     */
    public static String formatMessageSummary(JSONObject json) {
        try {
            String postType = json.getString("post_type");
            
            if (!"message".equals(postType)) {
                return null; // 只处理聊天消息
            }
            
            Long groupId = json.getLong("group_id");
            Long messageId = json.getLong("message_id");
            String messageType = json.getString("message_type");
            Long userId = json.getLong("user_id");
            String rawMessage = json.getString("raw_message");
            
            JSONObject sender = json.getJSONObject("sender");
            String nickname = sender != null ? sender.getString("nickname") : "";
            String card = sender != null ? sender.getString("card") : "";
            
            // 构建简化的摘要字符串
            StringBuilder sb = new StringBuilder();
            sb.append("GroupID=").append(groupId);
            sb.append(" | MsgID=").append(messageId);
            sb.append(" | Type=").append(messageType);
            sb.append(" | UserID=").append(userId);
            sb.append(" | Nickname=").append(nickname);
            if (card != null && !card.isEmpty()) {
                sb.append(" | Card=").append(card);
            }
            sb.append(" | Message=").append(rawMessage);
            
            return sb.toString();
        } catch (Exception e) {
            return "摘要生成失败: " + e.getMessage();
        }
    }
}
