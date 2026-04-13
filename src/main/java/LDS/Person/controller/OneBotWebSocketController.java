package LDS.Person.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import LDS.Person.websocket.OneBotWebSocketHandler;
import LDS.Person.util.DSchatNcatQQ;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * OneBot WebSocket 管理控制器
 */
@RestController
@RequestMapping("/api/onebot")
@Api(tags = "OneBot状态与服务配置", description = "OneBot连接配置，端口config读取")
@Slf4j
public class OneBotWebSocketController {

    @Autowired
    private OneBotWebSocketHandler oneBotWebSocketHandler;

    /**
     * 获取 WebSocket 连接状态
     */
    @GetMapping("/status")
    @ApiOperation(value = "获取 WebSocket 连接状态")
    public ResponseEntity<?> getStatus() {
        JSONObject response = new JSONObject();
        response.put("connected_clients", oneBotWebSocketHandler.getConnectionCount());
        response.put("message", "WebSocket 服务运行正常");
        response.put("websocket_url", "ws://localhost:7090/onebot");
        return ResponseEntity.ok(response);
    }

    /**
     * 获取 WebSocket 配置信息
     */
    @GetMapping("/config")
    @ApiOperation(value = "获取 WebSocket 配置信息")
    public ResponseEntity<?> getConfig() {
        JSONObject response = new JSONObject();
        response.put("endpoint", "/onebot");
        response.put("protocol", "WebSocket");
        response.put("onebot_version", "11");
        response.put("description", "OneBot 消息接收端点");
        return ResponseEntity.ok(response);
    }

    /**
     * 广播消息给所有连接的客户端（用于测试）
     */
    @PostMapping("/broadcast")
    @ApiOperation(value = "广播消息给所有连接的客户端（用于测试）")
    public ResponseEntity<?> broadcastMessage(@RequestBody JSONObject message) {
        try {
            oneBotWebSocketHandler.broadcastMessage(message.toJSONString());
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("message", "消息已发送给所有连接的客户端");
            response.put("client_count", oneBotWebSocketHandler.getConnectionCount());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("status", "error");
            errorResponse.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 获取所有用户的AI对话历史（JSON格式）
     */
    @GetMapping("/conversation-history")
    @ApiOperation(value = "获取所有用户的对话历史", notes = "返回所有用户的DeepSeek对话历史记录（JSON格式）")
    public ResponseEntity<?> getConversationHistory() {
        try {
            ObjectNode allHistory = DSchatNcatQQ.getAllHistoryAsJson();
            
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("data", JSONObject.parse(allHistory.toString()));
            
            log.info("获取所有用户对话历史成功，总用户数: {}", allHistory.get("totalUsers"));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取对话历史异常", e);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取对话历史失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}