package LDS.Person.tasks.MsgLisLogic;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import LDS.Person.dto.request.SendGroupMessageRequest;
import LDS.Person.dto.request.GroupTaskCreateRequest;
import LDS.Person.dto.response.GroupTaskResponse;

import java.util.Map;

/**
 * VIP 群组任务创建逻辑处理器
 * 处理 "指令新增群组任务->" 指令，创建新的群组任务配置并发送结果到群聊
 */
@Component
@Slf4j
public class VIPGroupTaskCreateLogic {

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 处理"指令新增群组任务->"指令
     * 为当前群聊创建新的群组任务配置，并将结果发送到群聊
     *
     * @param groupId 群聊ID
     * @param rawMessage 原始消息文本（未使用，保留用于兼容性）
     */
    public void handleGroupTaskCreate(Long groupId, String rawMessage) {
        try {
            log.info("[VIPGroupTaskCreateLogic] 处理创建群组任务指令，群组ID: {}", groupId);

            // 使用当前群聊ID作为目标群组ID
            String targetGroupId = String.valueOf(groupId);
        //    log.info("[VIPGroupTaskCreateLogic] 目标群组ID: {}", targetGroupId);

            // 1. 构建创建请求
            GroupTaskCreateRequest request = new GroupTaskCreateRequest();
            request.setGroupId(targetGroupId);

            // 2. 调用创建接口
            String url = "http://localhost:8090/api/group-task/create";
            GroupTaskResponse response = restTemplate.postForObject(
                    url, 
                    request, 
                    GroupTaskResponse.class
            );

            if (response == null) {
                log.warn("[VIPGroupTaskCreateLogic] 创建接口返回 null");
                sendMessage(groupId, "创建群组任务配置失败：接口返回异常");
                return;
            }

        //    log.info("[VIPGroupTaskCreateLogic] 创建接口响应码: {}，消息: {}", response.getCode(), response.getMessage());

            // 3. 检查创建结果
            if (response.getCode() != 0) {
                log.warn("[VIPGroupTaskCreateLogic] 创建失败: {}", response.getMessage());
                sendMessage(groupId, "创建群组任务配置失败: " + response.getMessage());
                return;
            }

            // 4. 获取 data 字段
            Map<String, Object> createdData = response.getData();
            if (createdData == null || createdData.isEmpty()) {
                log.warn("[VIPGroupTaskCreateLogic] 创建后的数据为空");
                sendMessage(groupId, "创建成功，但返回数据为空");
                return;
            }

        //    log.info("[VIPGroupTaskCreateLogic] 成功创建群组任务数据: {}", createdData);

            // 5. 格式化创建后的数据为可读的文本
            String formattedMessage = formatTaskData(createdData);

            // 6. 发送到群聊
            sendMessage(groupId, formattedMessage);

        //    log.info("[VIPGroupTaskCreateLogic] 群组任务创建信息发送完成");

        } catch (Exception e) {
            log.error("[VIPGroupTaskCreateLogic] 处理创建群组任务指令异常", e);
            try {
                sendMessage(groupId, "处理创建群组任务指令失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("[VIPGroupTaskCreateLogic] 发送错误消息失败", ex);
            }
        }
    }

    /**
     * 格式化创建后的任务数据为可读的文本
     *
     * @param taskData 任务数据 Map
     * @return 格式化后的文本
     */
    private String formatTaskData(Map<String, Object> taskData) {
        StringBuilder sb = new StringBuilder();
        sb.append("【群组任务创建成功】").append(System.lineSeparator());
        
        for (Map.Entry<String, Object> entry : taskData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 字段名翻译
            String displayKey = translateFieldName(key);
            
            // 值转换
            String displayValue = translateFieldValue(key, value);
            
            sb.append("• ").append(displayKey).append("=").append(displayValue)
                    .append(System.lineSeparator());
        }
        
        return sb.toString();
    }

    /**
     * 翻译字段名称为用户友好的名称
     * 直接返回原字段名，不进行翻译
     *
     * @param fieldName 字段名称
     * @return 字段名称
     */
    private String translateFieldName(String fieldName) {
        return fieldName;
    }

    /**
     * 翻译字段值为用户友好的显示
     * 对数值保持原样
     *
     * @param fieldName 字段名称
     * @param value 字段值
     * @return 转换后的值
     */
    private String translateFieldValue(String fieldName, Object value) {
        if (value == null) {
            return "N/A";
        }
        return value.toString();
    }

    /**
     * 发送文本消息到群聊
     * 调用 NCatSendMessageController 的 /api/ncat/send/group-message 接口
     *
     * @param groupId 群聊ID
     * @param message 消息内容
     */
    private void sendMessage(Long groupId, String message) {
        try {
            SendGroupMessageRequest request = new SendGroupMessageRequest();
            request.setGroupId(groupId);
            request.setText(message);

            // 调用本地接口发送消息
            String url = "http://localhost:8090/api/ncat/send/group-message";
            JSONObject response = restTemplate.postForObject(url, request, JSONObject.class);

        //    log.info("[VIPGroupTaskCreateLogic] 消息发送响应: {}", response);

        } catch (Exception e) {
            log.error("[VIPGroupTaskCreateLogic] 发送消息异常", e);
        }
    }
}
