package LDS.Person.tasks.MsgLisLogic;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import LDS.Person.dto.request.SendGroupMessageRequest;
import LDS.Person.dto.request.GroupTaskUpdateRequest;
import LDS.Person.dto.response.GroupTaskUpdateResponse;

import java.util.Map;

/**
 * VIP 群组任务修改逻辑处理器
 * 处理 "指令修改群组任务->字段名=值" 指令，修改群组配置并发送更新结果到群聊
 */
@Component
@Slf4j
public class VIPGroupTaskUpLogic {

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 处理"指令修改群组任务->字段名=值"指令
     * 根据 group_id 和字段名/值修改群组任务配置，并发送更新后的数据到群聊
     *
     * @param groupId 群聊ID
     * @param rawMessage 原始消息文本，格式: "指令修改群组任务->字段名=值"
     */
    public void handleGroupTaskUpdate(Long groupId, String rawMessage) {
        try {
            log.info("[VIPGroupTaskUpLogic] 处理修改群组任务指令，群组ID: {}，消息: {}", groupId, rawMessage);

            // 1. 提取字段名和值
            String[] fieldAndValue = extractFieldAndValue(rawMessage);
            if (fieldAndValue == null) {
                log.warn("[VIPGroupTaskUpLogic] 无法提取字段名和值");
                sendMessage(groupId, "指令格式错误，正确格式: 指令修改群组任务->字段名=true/false");
                return;
            }

            String taskName = fieldAndValue[0];
            String taskValue = fieldAndValue[1];

            //log.info("[VIPGroupTaskUpLogic] 提取到字段名: {}，值: {}", taskName, taskValue);

            // 2. 验证值是否为 true 或 false
            boolean taskStatus;
            if ("true".equalsIgnoreCase(taskValue)) {
                taskStatus = true;
            } else if ("false".equalsIgnoreCase(taskValue)) {
                taskStatus = false;
            } else {
                log.warn("[VIPGroupTaskUpLogic] 值不是 true 或 false: {}", taskValue);
                sendMessage(groupId, "值必须为 true 或 false");
                return;
            }

            // 3. 构建更新请求
            GroupTaskUpdateRequest updateRequest = new GroupTaskUpdateRequest();
            updateRequest.setGroupId(String.valueOf(groupId));
            updateRequest.setTaskName(taskName);
            updateRequest.setTaskStatus(taskStatus);

            //log.info("[VIPGroupTaskUpLogic] 构建更新请求: groupId={}, taskName={}, taskStatus={}", 
            //        groupId, taskName, taskStatus);

            // 4. 调用更新接口
            String url = "http://localhost:8090/api/group-task/update";
            GroupTaskUpdateResponse updateResponse = restTemplate.postForObject(
                    url, 
                    updateRequest, 
                    GroupTaskUpdateResponse.class
            );

            if (updateResponse == null) {
                log.warn("[VIPGroupTaskUpLogic] 更新接口返回 null");
                sendMessage(groupId, "修改群组任务配置失败：接口返回异常");
                return;
            }

            log.info("[VIPGroupTaskUpLogic] 更新接口响应码: {}，消息: {}", updateResponse.getCode(), updateResponse.getMessage());

            // 5. 检查更新结果
            if (updateResponse.getCode() != 0) {
                log.warn("[VIPGroupTaskUpLogic] 更新失败: {}", updateResponse.getMessage());
                sendMessage(groupId, "修改群组任务配置失败: " + updateResponse.getMessage());
                return;
            }

            // 6. 获取更新后的 data 字段
            Map<String, Object> updatedData = updateResponse.getData();
            if (updatedData == null || updatedData.isEmpty()) {
                log.warn("[VIPGroupTaskUpLogic] 更新后的数据为空");
                sendMessage(groupId, "修改成功，但返回数据为空");
                return;
            }

            log.info("[VIPGroupTaskUpLogic] 成功更新群组任务数据: {}", updatedData);

            // 7. 格式化更新后的数据为可读的文本
            String formattedMessage = formatTaskData(updatedData);

            // 8. 发送到群聊
            sendMessage(groupId, formattedMessage);

            //log.info("[VIPGroupTaskUpLogic] 群组任务修改信息发送完成");

        } catch (Exception e) {
            log.error("[VIPGroupTaskUpLogic] 处理修改群组任务指令异常", e);
            try {
                sendMessage(groupId, "处理修改群组任务指令失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("[VIPGroupTaskUpLogic] 发送错误消息失败", ex);
            }
        }
    }

    /**
     * 从原始消息中提取字段名和值
     * 格式: "指令修改群组任务->字段名=值"
     *
     * @param rawMessage 原始消息文本
     * @return 包含 [字段名, 值] 的数组，如果解析失败返回 null
     */
    private String[] extractFieldAndValue(String rawMessage) {
        try {
            // 移除 "指令修改群组任务->" 前缀
            String prefix = "指令修改群组任务->";
            int prefixIndex = rawMessage.indexOf(prefix);
            if (prefixIndex == -1) {
                return null;
            }

            String content = rawMessage.substring(prefixIndex + prefix.length()).trim();
            
            // 查找 "=" 符号
            int equalIndex = content.indexOf("=");
            if (equalIndex == -1) {
                return null;
            }

            String fieldName = content.substring(0, equalIndex).trim();
            String fieldValue = content.substring(equalIndex + 1).trim();

            if (fieldName.isEmpty() || fieldValue.isEmpty()) {
                return null;
            }

            //log.debug("[VIPGroupTaskUpLogic] 提取字段: {}, 值: {}", fieldName, fieldValue);
            return new String[]{fieldName, fieldValue};

        } catch (Exception e) {
            log.error("[VIPGroupTaskUpLogic] 提取字段名和值异常", e);
            return null;
        }
    }

    /**
     * 格式化任务数据为可读的文本
     *
     * @param taskData 任务数据 Map
     * @return 格式化后的文本
     */
    private String formatTaskData(Map<String, Object> taskData) {
        StringBuilder sb = new StringBuilder();
        sb.append("【群组任务配置】").append(System.lineSeparator());
        
        for (Map.Entry<String, Object> entry : taskData.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            
            // 字段名翻译
            String displayKey = translateFieldName(key);
            
            // 值转换（1 转为是，0 转为否）
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
     * 对 tinyint 类型的值转换 1/0 为 是/否，其他值保持原样
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

         //   log.info("[VIPGroupTaskUpLogic] 消息发送响应: {}", response);

        } catch (Exception e) {
            log.error("[VIPGroupTaskUpLogic] 发送消息异常", e);
        }
    }
}
