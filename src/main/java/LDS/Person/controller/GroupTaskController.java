package LDS.Person.controller;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import LDS.Person.dto.request.GroupTaskRequest;
import LDS.Person.dto.request.GroupTaskUpdateRequest;
import LDS.Person.dto.request.GroupTaskCreateRequest;
import LDS.Person.dto.response.GroupTaskResponse;
import LDS.Person.dto.response.GroupTaskListResponse;
import LDS.Person.dto.response.GroupTaskUpdateResponse;
import LDS.Person.service.GroupTaskService;

import java.util.List;
import java.util.Map;

/**
 * 群组定时任务控制器
 * 用于查询和管理群组的定时任务配置
 */
@RestController
@RequestMapping("/api/group-task")
@Api(tags = "群组定时任务", description = "群组定时任务管理接口")
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class GroupTaskController {

    @Autowired
    private GroupTaskService groupTaskService;

    /**
     * 查询群组任务配置
     * 根据 group_id 查询群组的所有任务配置字段
     *
     * @param request 包含 group_id 的请求体
     * @return 群组任务的所有字段和数据
     */
    @PostMapping("/get-by-group")
    @ApiOperation(value = "查询群组任务配置", notes = "根据群组ID查询该群组的所有任务配置字段")
    public ResponseEntity<GroupTaskResponse> getGroupTaskByGroupId(@RequestBody GroupTaskRequest request) {
        try {
            // 参数校验
            if (request.getGroupId() == null || request.getGroupId().trim().isEmpty()) {
                log.warn("[GroupTaskController] group_id 为空");
                return ResponseEntity.badRequest()
                        .body(GroupTaskResponse.error("group_id 不能为空"));
            }

            String groupId = request.getGroupId().trim();
            log.info("[GroupTaskController] 查询群组任务配置，group_id: {}", groupId);

            // 调用服务层
            Map<String, Object> taskData = groupTaskService.getGroupTaskByGroupId(groupId);

            if (taskData == null) {
                log.info("[GroupTaskController] 没有找到 group_id: {} 的记录", groupId);
                return ResponseEntity.ok(GroupTaskResponse.notFound("未找到该群组的任务配置"));
            }

            log.info("[GroupTaskController] 成功查询到群组任务配置，字段数: {}", taskData.size());
            return ResponseEntity.ok(GroupTaskResponse.success(taskData));

        } catch (Exception e) {
            log.error("[GroupTaskController] 查询群组任务配置异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GroupTaskResponse.error("服务器错误: " + e.getMessage()));
        }
    }

    /**
     * 查询所有群组任务配置
     * 返回group_task表中的所有记录
     *
     * @return 所有群组的任务配置
     */
    @GetMapping("/get-all")
    @ApiOperation(value = "查询所有群组任务配置", notes = "查询 group_task 表中的所有记录")
    public ResponseEntity<GroupTaskListResponse> getAllGroupTasks() {
        try {
            log.info("[GroupTaskController] 查询所有群组任务配置");

            // 调用服务层
            List<Map<String, Object>> results = groupTaskService.getAllGroupTasks();

            log.info("[GroupTaskController] 查询到 {} 条群组任务配置记录", results.size());
            return ResponseEntity.ok(GroupTaskListResponse.success(results));

        } catch (Exception e) {
            log.error("[GroupTaskController] 查询所有群组任务配置异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GroupTaskListResponse.error("服务器错误: " + e.getMessage()));
        }
    }

    /**
     * 创建新的群组任务配置行
     * 仅需传入 group_id，如果该 group_id 已存在则返回错误
     *
     * @param request 包含 group_id 的请求体
     * @return 创建结果（新创建的群组任务配置数据）
     */
    @PostMapping("/create")
    @ApiOperation(value = "创建群组任务配置", notes = "创建新的群组任务配置行（greeting 和 temp 默认为 0），若 group_id 已存在则失败")
    public ResponseEntity<GroupTaskResponse> createGroupTask(@RequestBody GroupTaskCreateRequest request) {
        try {
            // 参数校验
            if (request.getGroupId() == null || request.getGroupId().trim().isEmpty()) {
                log.warn("[GroupTaskController] group_id 为空");
                return ResponseEntity.badRequest()
                        .body(GroupTaskResponse.error("group_id 不能为空"));
            }

            String groupId = request.getGroupId().trim();
            log.info("[GroupTaskController] 创建群组任务配置，group_id: {}", groupId);

            // 调用服务层
            Map<String, Object> createdData = groupTaskService.createGroupTask(groupId);

            if (createdData == null) {
                log.warn("[GroupTaskController] 创建后未找到数据");
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(GroupTaskResponse.error("创建失败：无法获取创建后的数据"));
            }

            log.info("[GroupTaskController] 成功创建群组任务配置，group_id: {}", groupId);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(GroupTaskResponse.success(createdData));

        } catch (RuntimeException e) {
            log.warn("[GroupTaskController] 创建群组任务配置业务异常: {}", e.getMessage());
            // 重复创建返回 409 Conflict
            if (e.getMessage().contains("已存在")) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(GroupTaskResponse.error(e.getMessage()));
            }
            return ResponseEntity.badRequest()
                    .body(GroupTaskResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[GroupTaskController] 创建群组任务配置异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GroupTaskResponse.error("服务器错误: " + e.getMessage()));
        }
    }

    /**
     * 更新群组任务配置
     * 根据 group_id 更新指定字段的值
     *
     * @param request 包含 group_id、taskName 和 taskStatus 的请求体
     * @return 更新结果
     */
    @PostMapping("/update")
    @ApiOperation(value = "更新群组任务配置", notes = "根据群组ID更新指定字段的值（true 转为 1，false 转为 0）")
    public ResponseEntity<GroupTaskUpdateResponse> updateGroupTask(@RequestBody GroupTaskUpdateRequest request) {
        try {
            // 参数校验
            if (request.getGroupId() == null || request.getGroupId().trim().isEmpty()) {
                log.warn("[GroupTaskController] group_id 为空");
                return ResponseEntity.badRequest()
                        .body(GroupTaskUpdateResponse.error("group_id 不能为空"));
            }

            if (request.getTaskName() == null || request.getTaskName().trim().isEmpty()) {
                log.warn("[GroupTaskController] taskName 为空");
                return ResponseEntity.badRequest()
                        .body(GroupTaskUpdateResponse.error("taskName 不能为空"));
            }

            if (request.getTaskStatus() == null) {
                log.warn("[GroupTaskController] taskStatus 为空");
                return ResponseEntity.badRequest()
                        .body(GroupTaskUpdateResponse.error("taskStatus 不能为空"));
            }

            String groupId = request.getGroupId().trim();
            String taskName = request.getTaskName().trim();
            boolean taskStatus = request.getTaskStatus();

            log.info("[GroupTaskController] 更新群组任务配置，group_id: {}，taskName: {}，taskStatus: {}", 
                    groupId, taskName, taskStatus);

            // 调用服务层
            Map<String, Object> updatedData = groupTaskService.updateGroupTask(groupId, taskName, taskStatus);

            if (updatedData == null) {
                log.info("[GroupTaskController] 没有找到 group_id: {} 的记录", groupId);
                return ResponseEntity.ok(GroupTaskUpdateResponse.notFound("未找到该群组的任务配置"));
            }

            log.info("[GroupTaskController] 成功更新群组任务配置，group_id: {}", groupId);
            return ResponseEntity.ok(GroupTaskUpdateResponse.success(updatedData));

        } catch (IllegalArgumentException e) {
            log.warn("[GroupTaskController] 参数验证失败: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(GroupTaskUpdateResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("[GroupTaskController] 更新群组任务配置异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(GroupTaskUpdateResponse.error("服务器错误: " + e.getMessage()));
        }
    }
}
