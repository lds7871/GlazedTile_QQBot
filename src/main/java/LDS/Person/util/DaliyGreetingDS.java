package LDS.Person.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import LDS.Person.config.NapCatTaskIsOpen;

import java.text.SimpleDateFormat;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;

/**
 * 每日问候 DeepSeek 任务处理器
 * 集成定时任务和业务逻辑
 * 使用 DeepSeek API 生成每日问候语
 * 
 * 定时任务：
 * - 00:01:04 创建每日问候记录
 * - 00:01:05 生成早安问候
 * - 00:01:06 生成晚安问候
 */
@Component
@Slf4j
public class DaliyGreetingDS {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * 定时任务：每天 00:01:04 执行
     * 在 daliy_greeting 表中插入新记录，today 字段为当前日期（yyyy-MM-dd 格式）
     */
    @Scheduled(cron = "4 1 0 * * ?")
    public void createDailyGreeting() {
        // 检查任务是否启用
        if (!NapCatTaskIsOpen.isDailyGreetingCreateTask) {
            log.debug("[DaliyGreetingDS] 创建任务未启用");
            return;
        }

        try {
            //log.info("[DaliyGreetingDS] 开始创建每日问候记录");
            createDailyGreetingRecord();
            log.info("[DaliyGreetingDS] 每日问候记录创建成功");
        } catch (Exception e) {
            log.error("[DaliyGreetingDS] 创建每日问候记录异常", e);
        }
    }

    /**
     * 定时任务：每天 00:01:05 执行
     * 调用 DeepSeek API 生成早安问候，更新 daliy_greeting 表中最新一行的 morning_text 字段
     */
    @Scheduled(cron = "5 1 0 * * ?")
    public void generateMorningGreetingTask() {
        // 检查任务是否启用
        if (!NapCatTaskIsOpen.isDailyGreetingMorningTask) {
            log.debug("[DaliyGreetingDS] 早安问候任务未启用");
            return;
        }

        try {
            //log.info("[DaliyGreetingDS] 开始生成早安问候");
            generateMorningGreeting();
            log.info("[DaliyGreetingDS] 早安问候生成成功");
        } catch (Exception e) {
            log.error("[DaliyGreetingDS] 生成早安问候异常", e);
        }
    }

    /**
     * 定时任务：每天 00:01:06 执行
     * 调用 DeepSeek API 生成晚安问候，更新 daliy_greeting 表中最新一行的 evening_text 字段
     */
    @Scheduled(cron = "6 1 0 * * ?")
    public void generateEveningGreetingTask() {
        // 检查任务是否启用
        if (!NapCatTaskIsOpen.isDailyGreetingEveningTask) {
            log.debug("[DaliyGreetingDS] 晚安问候任务未启用");
            return;
        }

        try {
            //log.info("[DaliyGreetingDS] 开始生成晚安问候");
            generateEveningGreeting();
            log.info("[DaliyGreetingDS] 晚安问候生成成功");
        } catch (Exception e) {
            log.error("[DaliyGreetingDS] 生成晚安问候异常", e);
        }
    }

    /**
     * 创建新的每日问候记录
     * 在 daliy_greeting 表中插入新一行，today 字段为当前日期（yyyy-MM-dd 格式）
     */
    private void createDailyGreetingRecord() {
        try {
            // 获取当前日期，格式为 yyyy-MM-dd
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String today = sdf.format(new Date());

            log.info("[DaliyGreetingDS] 创建新的每日问候记录，日期: {}", today);

            // 插入新记录，morning_text 和 evening_text 暂时为空
            String sql = "INSERT INTO daliy_greeting (today, morning_text, evening_text) VALUES (?, ?, ?)";
            jdbcTemplate.update(sql, today, "", "");

            log.info("[DaliyGreetingDS] 新记录创建成功，日期: {}", today);

        } catch (Exception e) {
            log.error("[DaliyGreetingDS] 创建每日问候记录异常", e);
        }
    }

    /**
     * 生成早安问候语并更新数据库
     * 调用 DeepSeek API 生成早安问候，更新表中最新一行的 morning_text 字段
     */
    private void generateMorningGreeting() {
        try {
            log.info("[DaliyGreetingDS] 生成早安问候语");

            // 获取当前日期和星期
            LocalDate today = LocalDate.now();
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            
            String todayStr = today.format(dateFormatter);
            DayOfWeek dayOfWeek = today.getDayOfWeek();
            String dayStr = convertDayOfWeek(dayOfWeek);

            String prompt = "今天是" + todayStr + " 星期" + dayStr + "，编辑一段用于早安问候的语言，只写一段话";

            log.info("[DaliyGreetingDS] 调用 DeepSeek API，提示词: {}", prompt);

            // 调用 DeepSeek API
            String apiKey = System.getenv("DEEPSEEK_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                log.error("[DaliyGreetingDS] DEEPSEEK_API_KEY 环境变量未设置");
                return;
            }

            DSchatNcatQQ deepseekClient = new DSchatNcatQQ(apiKey);
            String morningText = deepseekClient.UsedeepseekMorning(prompt);

            log.info("[DaliyGreetingDS] 生成的早安问候: {}", morningText);

            // 更新最新一行的 morning_text
            String sql = "UPDATE daliy_greeting SET morning_text = ? WHERE today = ? ORDER BY id DESC LIMIT 1";
            int updated = jdbcTemplate.update(sql, morningText, todayStr);

            log.info("[DaliyGreetingDS] 早安问候更新完成，受影响行数: {}", updated);

        } catch (Exception e) {
            log.error("[DaliyGreetingDS] 生成早安问候异常", e);
        }
    }

    /**
     * 生成晚安问候语并更新数据库
     * 调用 DeepSeek API 生成晚安问候，更新表中最新一行的 evening_text 字段
     */
    private void generateEveningGreeting() {
        try {
            log.info("[DaliyGreetingDS] 生成晚安问候语");

            String prompt = "写一段向大家晚安的话语";

            log.info("[DaliyGreetingDS] 调用 DeepSeek API，提示词: {}", prompt);

            // 调用 DeepSeek API
            String apiKey = System.getenv("DEEPSEEK_API_KEY");
            if (apiKey == null || apiKey.isEmpty()) {
                log.error("[DaliyGreetingDS] DEEPSEEK_API_KEY 环境变量未设置");
                return;
            }

            DSchatNcatQQ deepseekClient = new DSchatNcatQQ(apiKey);
            String eveningText = deepseekClient.UsedeepseekMorning(prompt);

            log.info("[DaliyGreetingDS] 生成的晚安问候: {}", eveningText);

            // 获取当前日期
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
            String today = sdf.format(new Date());

            // 更新最新一行的 evening_text
            String sql = "UPDATE daliy_greeting SET evening_text = ? WHERE today = ? ORDER BY id DESC LIMIT 1";
            int updated = jdbcTemplate.update(sql, eveningText, today);

            log.info("[DaliyGreetingDS] 晚安问候更新完成，受影响行数: {}", updated);

        } catch (Exception e) {
            log.error("[DaliyGreetingDS] 生成晚安问候异常", e);
        }
    }

    /**
     * 转换 DayOfWeek 为中文星期表示
     */
    private String convertDayOfWeek(DayOfWeek dayOfWeek) {
        switch (dayOfWeek) {
            case MONDAY:
                return "一";
            case TUESDAY:
                return "二";
            case WEDNESDAY:
                return "三";
            case THURSDAY:
                return "四";
            case FRIDAY:
                return "五";
            case SATURDAY:
                return "六";
            case SUNDAY:
                return "日";
            default:
                return "未知";
        }
    }
}
