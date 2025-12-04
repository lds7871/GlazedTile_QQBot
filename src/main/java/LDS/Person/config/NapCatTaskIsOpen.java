package LDS.Person.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

@Component
public class NapCatTaskIsOpen {
    public static boolean isMsgLisATTask = true;
    public static boolean isMsgLisKeyWordTask = true;
    public static boolean isMsgLisUserCmdTask = true;
    public static boolean isMsgLisVipCmdTask = true;
    public static boolean isMsgSchTask = true;
    public static boolean isDailyGreetingCreateTask = true;
    public static boolean isDailyGreetingMorningTask = true;
    public static boolean isDailyGreetingEveningTask = true;

    @Autowired
    private DataSource dataSource;

    private static final Logger log = LoggerFactory.getLogger(NapCatTaskIsOpen.class);

    @PostConstruct
    public void initializeTaskStates() {
        System.out.println("[NapCatTaskIsOpen] 开始初始化任务状态...");
        try {
            Map<String, Integer> taskStates = readTaskStatesFromDatabase();
            
            isMsgLisATTask = getStateOrDefault(taskStates, "isMsgLisATTask", true);
            isMsgLisKeyWordTask = getStateOrDefault(taskStates, "isMsgLisKeyWordTask", true);
            isMsgLisUserCmdTask = getStateOrDefault(taskStates, "isMsgLisUserCmdTask", true);
            isMsgLisVipCmdTask = getStateOrDefault(taskStates, "isMsgLisVipCmdTask", true);
            isMsgSchTask = getStateOrDefault(taskStates, "isMsgSchTask", true);
            isDailyGreetingCreateTask = getStateOrDefault(taskStates, "isDailyGreetingCreateTask", true);
            isDailyGreetingMorningTask = getStateOrDefault(taskStates, "isDailyGreetingMorningTask", true);
            isDailyGreetingEveningTask = getStateOrDefault(taskStates, "isDailyGreetingEveningTask", true);
            
            
            log.info("NapCatTaskIsOpen 初始化完成:\n {}", taskStates);
        } catch (Exception e) {
            System.err.println("[NapCatTaskIsOpen] Failed to load task states from database: " + e.getMessage());
            e.printStackTrace();
            log.error("[NapCatTaskIsOpen] 初始化失败", e);
        }
    }

    /**
     * 运行时刷新配置 - 重新从数据库读取任务状态
     */
    public void refreshTaskStates() {
        System.out.println("[NapCatTaskIsOpen] 开始刷新任务状态...");
        try {
            Map<String, Integer> taskStates = readTaskStatesFromDatabase();
            
            System.out.println("[NapCatTaskIsOpen] 从数据库读取到的原始数据: " + taskStates);
            
            isMsgLisATTask = getStateOrDefault(taskStates, "isMsgLisATTask", true);
            isMsgLisKeyWordTask = getStateOrDefault(taskStates, "isMsgLisKeyWordTask", true);
            isMsgLisUserCmdTask = getStateOrDefault(taskStates, "isMsgLisUserCmdTask", true);
            isMsgLisVipCmdTask = getStateOrDefault(taskStates, "isMsgLisVipCmdTask", true);
            isMsgSchTask = getStateOrDefault(taskStates, "isMsgSchTask", true);
            isDailyGreetingCreateTask = getStateOrDefault(taskStates, "isDailyGreetingCreateTask", true);
            isDailyGreetingMorningTask = getStateOrDefault(taskStates, "isDailyGreetingMorningTask", true);
            isDailyGreetingEveningTask = getStateOrDefault(taskStates, "isDailyGreetingEveningTask", true);

            log.info("NapCatTaskIsOpen 刷新完成:\n {}", taskStates);
        } catch (Exception e) {
            System.err.println("[NapCatTaskIsOpen] Failed to refresh task states from database: " + e.getMessage());
            e.printStackTrace();
            log.error("[NapCatTaskIsOpen] 刷新失败", e);
        }
    }

    /**
     * 从数据库读取任务状态
     */
    private Map<String, Integer> readTaskStatesFromDatabase() throws Exception {
        Map<String, Integer> taskStates = new HashMap<>();
        
        String sql = "SELECT param_name, state FROM task_open";
        
        try (Connection conn = dataSource.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {
            
            while (rs.next()) {
                String paramName = rs.getString("param_name");
                int state = rs.getInt("state");
                taskStates.put(paramName, state);
            }
        }
        
        return taskStates;
    }

    /**
     * 获取任务状态，如果数据库中不存在则返回默认值true
     */
    private static boolean getStateOrDefault(Map<String, Integer> taskStates, String paramName, boolean defaultValue) {
        if (taskStates.containsKey(paramName)) {
            return taskStates.get(paramName) != 0;
        }
        return defaultValue;
    }
}
