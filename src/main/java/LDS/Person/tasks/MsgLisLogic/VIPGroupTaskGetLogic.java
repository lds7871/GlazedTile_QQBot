package LDS.Person.tasks.MsgLisLogic;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import LDS.Person.dto.request.SendGroupMessageRequest;
import LDS.Person.dto.request.GroupTaskRequest;
import LDS.Person.dto.response.GroupTaskResponse;

import java.util.Map;

/**
 * VIP 群组任务查询逻辑处理器
 * 处理 "指令查询群组任务->" 指令，查询群组配置并发送到群聊
 */
@Component
@Slf4j
public class VIPGroupTaskGetLogic {

    @Autowired
    private RestTemplate restTemplate;

    /**
     * 处理"指令查询群组任务->"指令
     * 根据 group_id 查询群组任务配置，并发送到群聊
     *
     * @param groupId 群聊ID
     */
    public void handleGroupTaskQuery(Long groupId) {
        try {
            //log.info("[VIPGroupTaskGetLogic] 处理查询群组任务指令，群组ID: {}", groupId);

            // 1. 构建查询请求
            GroupTaskRequest request = new GroupTaskRequest();
            request.setGroupId(String.valueOf(groupId));

            // 2. 调用查询接口
            String url = "http://localhost:8090/api/group-task/get-by-group";
            GroupTaskResponse response = restTemplate.postForObject(
                    url, 
                    request, 
                    GroupTaskResponse.class
            );

            if (response == null) {
                log.warn("[VIPGroupTaskGetLogic] 查询接口返回 null");
                sendMessage(groupId, "查询群组任务配置失败：接口返回异常");
                return;
            }

            //log.info("[VIPGroupTaskGetLogic] 查询接口响应码: {}，消息: {}", response.getCode(), response.getMessage());

            // 3. 检查查询结果
            if (response.getCode() != 0) {
                log.warn("[VIPGroupTaskGetLogic] 查询失败: {}", response.getMessage());
                sendMessage(groupId, "查询群组任务配置失败: " + response.getMessage());
                return;
            }

            // 4. 获取 data 字段
            Map<String, Object> taskData = response.getData();
            if (taskData == null || taskData.isEmpty()) {
                log.warn("[VIPGroupTaskGetLogic] 任务数据为空");
                sendMessage(groupId, "暂无群组任务配置数据");
                return;
            }

           // log.info("[VIPGroupTaskGetLogic] 成功获取群组任务数据: {}", taskData);

            // 5. 格式化数据为可读的文本
            String formattedMessage = formatTaskData(taskData);

            // 6. 发送到群聊
            sendMessage(groupId, formattedMessage);

            //log.info("[VIPGroupTaskGetLogic] 群组任务信息发送完成");

        } catch (Exception e) {
            log.error("[VIPGroupTaskGetLogic] 处理查询群组任务指令异常", e);
            try {
                sendMessage(groupId, "处理查询群组任务指令失败: " + e.getMessage());
            } catch (Exception ex) {
                log.error("[VIPGroupTaskGetLogic] 发送错误消息失败", ex);
            }
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

         //   log.info("[VIPGroupTaskGetLogic] 消息发送响应: {}", response);

        } catch (Exception e) {
            log.error("[VIPGroupTaskGetLogic] 发送消息异常", e);
        }
    }
}
