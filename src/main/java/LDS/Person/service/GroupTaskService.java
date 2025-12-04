package LDS.Person.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 群组任务服务层
 * 负责群组任务的业务逻辑处理
 */
@Service
@Slf4j
public class GroupTaskService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 根据 group_id 查询群组任务配置
     *
     * @param groupId 群组ID
     * @return 群组任务配置数据
     */
    public Map<String, Object> getGroupTaskByGroupId(String groupId) {
        try {
            log.info("[GroupTaskService] 查询群组任务配置，group_id: {}", groupId);

            String sql = "SELECT * FROM group_task WHERE group_id = ?";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql, groupId);

            if (results.isEmpty()) {
                log.info("[GroupTaskService] 没有找到 group_id: {} 的记录", groupId);
                return null;
            }

            Map<String, Object> taskData = results.get(0);
            log.info("[GroupTaskService] 成功查询到群组任务配置，字段数: {}", taskData.size());
            return taskData;

        } catch (Exception e) {
            log.error("[GroupTaskService] 查询群组任务配置异常", e);
            throw new RuntimeException("查询群组任务配置失败: " + e.getMessage());
        }
    }

    /**
     * 查询所有群组任务配置
     *
     * @return 所有群组任务配置列表
     */
    public List<Map<String, Object>> getAllGroupTasks() {
        try {
            log.info("[GroupTaskService] 查询所有群组任务配置");

            String sql = "SELECT * FROM group_task";
            List<Map<String, Object>> results = jdbcTemplate.queryForList(sql);

            log.info("[GroupTaskService] 查询到 {} 条群组任务配置记录", results.size());
            return results;

        } catch (Exception e) {
            log.error("[GroupTaskService] 查询所有群组任务配置异常", e);
            throw new RuntimeException("查询所有群组任务配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新群组任务配置
     *
     * @param groupId 群组ID
     * @param taskName 任务字段名称
     * @param taskStatus 任务状态（boolean）
     * @return 更新后的完整数据
     */
    public Map<String, Object> updateGroupTask(String groupId, String taskName, Boolean taskStatus) {
        try {
            log.info("[GroupTaskService] 更新群组任务配置，group_id: {}，taskName: {}，taskStatus: {}", 
                    groupId, taskName, taskStatus);

            // 验证字段名称是否安全（防止SQL注入）
            if (!isValidFieldName(taskName)) {
                log.warn("[GroupTaskService] 字段名称不合法: {}", taskName);
                throw new IllegalArgumentException("字段名称不合法");
            }

            // 检查记录是否存在
            String checkSql = "SELECT 1 FROM group_task WHERE group_id = ?";
            List<Map<String, Object>> checkResults = jdbcTemplate.queryForList(checkSql, groupId);
            
            if (checkResults.isEmpty()) {
                log.info("[GroupTaskService] 没有找到 group_id: {} 的记录", groupId);
                return null;
            }

            // 将 boolean 转换为 0 或 1
            int statusValue = taskStatus ? 1 : 0;

            // 动态构建 UPDATE 语句
            String updateSql = "UPDATE group_task SET " + taskName + " = ? WHERE group_id = ?";
            
            int updatedRows = jdbcTemplate.update(updateSql, statusValue, groupId);

            if (updatedRows > 0) {
                log.info("[GroupTaskService] 成功更新群组任务配置，group_id: {}，字段: {} = {}", 
                        groupId, taskName, statusValue);
                
                // 返回更新后的完整数据
                String selectSql = "SELECT * FROM group_task WHERE group_id = ?";
                List<Map<String, Object>> results = jdbcTemplate.queryForList(selectSql, groupId);
                return results.isEmpty() ? null : results.get(0);
            } else {
                log.warn("[GroupTaskService] 更新失败，没有行被更新");
                throw new RuntimeException("更新失败，没有行被更新");
            }

        } catch (Exception e) {
            log.error("[GroupTaskService] 更新群组任务配置异常", e);
            throw new RuntimeException("更新群组任务配置失败: " + e.getMessage());
        }
    }

    /**
     * 验证字段名称是否安全（防止SQL注入）
     * 仅允许字母、数字和下划线
     *
     * @param fieldName 字段名称
     * @return 如果安全返回 true，否则返回 false
     */
    private boolean isValidFieldName(String fieldName) {
        return fieldName.matches("^[a-zA-Z_][a-zA-Z0-9_]*$");
    }

    /**
     * 创建新的群组任务配置行
     * 如果该 group_id 已存在则抛出异常，否则插入新行
     *
     * @param groupId 群组ID
     * @return 新创建的群组任务配置数据
     */
    public Map<String, Object> createGroupTask(String groupId) {
        try {
            log.info("[GroupTaskService] 创建群组任务配置，group_id: {}", groupId);

            // 检查 group_id 是否已存在
            String checkSql = "SELECT COUNT(*) FROM group_task WHERE group_id = ?";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, groupId);

            if (count != null && count > 0) {
                log.warn("[GroupTaskService] group_id: {} 已存在，不允许重复创建", groupId);
                throw new RuntimeException("该群组ID已存在，不允许重复创建");
            }

            // 插入新行（除去 group_id 和 id 外，其他所有字段均默认为 0）
            String insertSql = "INSERT INTO group_task (group_id, greeting) VALUES (?, 0)";
            int insertedRows = jdbcTemplate.update(insertSql, groupId);

            if (insertedRows > 0) {
                log.info("[GroupTaskService] 成功创建群组任务配置，group_id: {}", groupId);

                // 返回新创建的完整数据
                String selectSql = "SELECT * FROM group_task WHERE group_id = ?";
                List<Map<String, Object>> results = jdbcTemplate.queryForList(selectSql, groupId);
                return results.isEmpty() ? null : results.get(0);
            } else {
                log.warn("[GroupTaskService] 创建失败，没有行被插入");
                throw new RuntimeException("创建失败，没有行被插入");
            }

        } catch (Exception e) {
            log.error("[GroupTaskService] 创建群组任务配置异常", e);
            throw new RuntimeException("创建群组任务配置失败: " + e.getMessage());
        }
    }
}
