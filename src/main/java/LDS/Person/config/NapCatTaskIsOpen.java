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
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * 任务开关配置管理
 * 
 * 性能优化：
 * 1. 使用读写锁保证线程安全的同时提高并发读取性能
 * 2. 缓存配置减少数据库访问频率
 */
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
    
    // 使用读写锁提高并发性能，读多写少的场景更高效
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    
    // 缓存上次加载时间，用于判断是否需要重新加载
    private volatile long lastLoadTime = 0;
    
    // 缓存有效期（毫秒），默认5分钟，减少数据库访问频率
    private static final long CACHE_VALIDITY_MS = 5 * 60 * 1000;

    @PostConstruct
    public void initializeTaskStates() {
        System.out.println("[NapCatTaskIsOpen] 开始初始化任务状态...");
        lock.writeLock().lock();
        try {
            Map<String, Integer> taskStates = readTaskStatesFromDatabase();
            
            updateTaskStates(taskStates);
            lastLoadTime = System.currentTimeMillis();
            
            log.info("NapCatTaskIsOpen 初始化完成:\n {}", taskStates);
        } catch (Exception e) {
            System.err.println("[NapCatTaskIsOpen] Failed to load task states from database: " + e.getMessage());
            e.printStackTrace();
            log.error("[NapCatTaskIsOpen] 初始化失败", e);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 运行时刷新配置 - 重新从数据库读取任务状态
     * 添加缓存检查，避免频繁访问数据库
     */
    public void refreshTaskStates() {
        // 检查缓存是否仍然有效
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastLoadTime < CACHE_VALIDITY_MS) {
            log.debug("使用缓存的任务状态配置，缓存仍然有效");
            return;
        }
        
        System.out.println("[NapCatTaskIsOpen] 开始刷新任务状态...");
        lock.writeLock().lock();
        try {
            Map<String, Integer> taskStates = readTaskStatesFromDatabase();
            
            System.out.println("[NapCatTaskIsOpen] 从数据库读取到的原始数据: " + taskStates);
            
            updateTaskStates(taskStates);
            lastLoadTime = currentTime;

            log.info("NapCatTaskIsOpen 刷新完成:\n {}", taskStates);
        } catch (Exception e) {
            System.err.println("[NapCatTaskIsOpen] Failed to refresh task states from database: " + e.getMessage());
            e.printStackTrace();
            log.error("[NapCatTaskIsOpen] 刷新失败", e);
        } finally {
            lock.writeLock().unlock();
        }
    }
    
    /**
     * 更新任务状态的内部方法
     */
    private void updateTaskStates(Map<String, Integer> taskStates) {
        isMsgLisATTask = getStateOrDefault(taskStates, "isMsgLisATTask", true);
        isMsgLisKeyWordTask = getStateOrDefault(taskStates, "isMsgLisKeyWordTask", true);
        isMsgLisUserCmdTask = getStateOrDefault(taskStates, "isMsgLisUserCmdTask", true);
        isMsgLisVipCmdTask = getStateOrDefault(taskStates, "isMsgLisVipCmdTask", true);
        isMsgSchTask = getStateOrDefault(taskStates, "isMsgSchTask", true);
        isDailyGreetingCreateTask = getStateOrDefault(taskStates, "isDailyGreetingCreateTask", true);
        isDailyGreetingMorningTask = getStateOrDefault(taskStates, "isDailyGreetingMorningTask", true);
        isDailyGreetingEveningTask = getStateOrDefault(taskStates, "isDailyGreetingEveningTask", true);
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
