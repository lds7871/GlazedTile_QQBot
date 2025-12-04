package LDS.Person.controller;

import LDS.Person.util.OldGameGetTask;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import LDS.Person.websocket.OneBotWebSocketHandler;
import LDS.Person.util.DSchatNcatQQ;
import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.tasks.MsgLisVipCmdTask;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.databind.node.ObjectNode;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

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

    @Autowired
    private DataSource dataSource;

    @Autowired
    private NapCatTaskIsOpen napCatTaskIsOpen;

    @Autowired
    private MsgLisVipCmdTask msgLisVipCmdTask;

    @Autowired
    private OldGameGetTask oldGameGetTask;

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

    /**
     * 获取所有任务的启用状态
     */
    @GetMapping("/task-status")
    @ApiOperation(value = "获取所有任务的启用状态", notes = "返回数据库中所有任务的当前启用/禁用状态")
    public ResponseEntity<?> getTaskStatus() {
        try {
            JSONObject allTaskStatus = readAllTaskStatesFromDatabase();
            
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("data", allTaskStatus);
            response.put("message", "任务状态已获取");
            response.put("total", allTaskStatus.size());
            
            log.info("获取任务状态成功，共 {} 个任务", allTaskStatus.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取任务状态异常", e);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取任务状态失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 从数据库读取所有任务状态
     */
    private JSONObject readAllTaskStatesFromDatabase() throws Exception {
        JSONObject allTaskStatus = new JSONObject();
        
        String sql = "SELECT param_name, state FROM task_open";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                String paramName = rs.getString("param_name");
                int state = rs.getInt("state");
                // 将 state 转换为 boolean：0 为 false，非 0 为 true
                allTaskStatus.put(paramName, state != 0);
            }
        }
        
        return allTaskStatus;
    }

    /**
     * 从数据库读取所有 VIP 用户
     */
    private JSONObject readAllVIPUsersFromDatabase() throws Exception {
        JSONObject result = new JSONObject();
        JSONObject vipUsers = new JSONObject();
        com.alibaba.fastjson2.JSONArray vipIds = new com.alibaba.fastjson2.JSONArray();
        
        String sql = "SELECT id, qq_id, create_time FROM vip_id";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            int count = 0;
            while (rs.next()) {
                int id = rs.getInt("id");
                String qqId = rs.getString("qq_id");
                java.sql.Timestamp createTime = rs.getTimestamp("create_time");
                
                JSONObject user = new JSONObject();
                user.put("id", id);
                user.put("qq_id", qqId);
                user.put("create_time", createTime != null ? createTime.toString() : null);
                
                vipUsers.put(String.valueOf(id), user);
                vipIds.add(qqId);
                count++;
            }
            
            result.put("vip_ids", vipIds);
            result.put("vip_users", vipUsers);
            result.put("total", count);
        }
        
        return result;
    }

    /**
     * 刷新任务配置 - 重新从数据库加载所有任务状态
     */
    @PostMapping("/refresh-task-config")
    @ApiOperation(value = "刷新任务配置", notes = "重新从数据库读取任务状态，无需重启应用")
    public ResponseEntity<?> refreshTaskConfig() {
        try {
            napCatTaskIsOpen.refreshTaskStates();
            
            // 刷新后读取最新的配置数据
            JSONObject allTaskStatus = readAllTaskStatesFromDatabase();
            
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("message", "任务配置已刷新");
            response.put("data", allTaskStatus);
            response.put("total", allTaskStatus.size());
            
            log.info("任务配置刷新成功，共 {} 个任务", allTaskStatus.size());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("刷新任务配置异常", e);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("status", "error");
            errorResponse.put("message", "刷新任务配置失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 获取 VIP 用户白名单
     */
    @GetMapping("/vip-users")
    @ApiOperation(value = "获取 VIP 用户白名单", notes = "返回数据库中所有 VIP 用户的 QQ ID")
    public ResponseEntity<?> getVIPUsers() {
        try {
            JSONObject allVIPUsers = readAllVIPUsersFromDatabase();
            
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("data", allVIPUsers.getJSONArray("vip_ids"));
            response.put("message", "VIP 用户白名单已获取");
            response.put("total", allVIPUsers.getInteger("total"));
            
            log.info("获取 VIP 用户白名单成功，共 {} 个用户", allVIPUsers.getInteger("total"));
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("获取 VIP 用户白名单异常", e);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("status", "error");
            errorResponse.put("message", "获取 VIP 用户白名单失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
    /**
     * 刷新 VIP 用户白名单 - 重新从数据库加载
     */
    @PostMapping("/refresh-vip-users")
    @ApiOperation(value = "刷新 VIP 用户白名单", notes = "重新从数据库读取 VIP 用户列表，无需重启应用")
    public ResponseEntity<?> refreshVIPUsers() {
        try {
            msgLisVipCmdTask.refreshVIPUserIds();
            
            // 刷新后读取最新的数据
            JSONObject allVIPUsers = readAllVIPUsersFromDatabase();
            
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("message", "VIP 用户白名单已刷新");
            response.put("data", allVIPUsers.getJSONArray("vip_ids"));
            response.put("total", allVIPUsers.getInteger("total"));
            
            log.info("VIP 用户白名单刷新成功，共 {} 个用户", allVIPUsers.getInteger("total"));
            
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("刷新 VIP 用户白名单异常", e);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("status", "error");
            errorResponse.put("message", "刷新 VIP 用户白名单失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * 手动触发 OldGameTask（用于在非定时执行时强制保存）
     */
    @PostMapping("/oldgame/trigger")
    @ApiOperation(value = "手动触发 OldGameTask", notes = "立即执行 OldGameTask 的保存逻辑")
    public ResponseEntity<?> triggerOldGameTask() {
        try {
            oldGameGetTask.executeScheduledTask();
            JSONObject response = new JSONObject();
            response.put("status", "success");
            response.put("message", "OldGameTask 已触发并保存数据（如果成功）");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("手动触发 OldGameTask 失败", e);
            JSONObject errorResponse = new JSONObject();
            errorResponse.put("status", "error");
            errorResponse.put("message", "触发失败: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}