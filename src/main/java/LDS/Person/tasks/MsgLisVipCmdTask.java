package LDS.Person.tasks;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.service.SingleDefenseService;
import LDS.Person.tasks.MsgLisLogic.VIPSingleDefenseLogic;
import LDS.Person.tasks.MsgLisLogic.VIPScreenshotLogic;
import LDS.Person.tasks.MsgLisLogic.VIPGroupTaskGetLogic;
import LDS.Person.tasks.MsgLisLogic.VIPGroupTaskUpLogic;
import LDS.Person.tasks.MsgLisLogic.VIPGroupTaskCreateLogic;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * VIP 白名单消息监听和自动回复处理器
 * 监听群聊中白名单用户的消息，对特殊指令进行自动处理
 * 
 * 指令格式：
 * - "指令单防->(昵称)" -> 开启对指定昵称的自动回复"不要狗叫"
 * - "指令撤销" -> 取消所有单防设置
 */
@Component
@Slf4j
public class MsgLisVipCmdTask {

  @Autowired
  private SingleDefenseService singleDefenseService;

  @Autowired
  private VIPSingleDefenseLogic vipSingleDefenseLogic;

  @Autowired
  private VIPScreenshotLogic vipScreenshotLogic;

  @Autowired
  private VIPGroupTaskGetLogic vipGroupTaskGetLogic;

  @Autowired
  private VIPGroupTaskUpLogic vipGroupTaskUpLogic;

  @Autowired
  private VIPGroupTaskCreateLogic vipGroupTaskCreateLogic;

  @Autowired
  private DataSource dataSource;

  /**
   * VIP 白名单用户 ID 集合
   * 只有在此集合中的用户才能触发指令
   */
  private static Set<Long> VIP_USER_IDS = new HashSet<>();

  /**
   * 在 Spring 容器初始化后，从数据库加载 VIP 用户白名单
   */
  @PostConstruct
  public void loadVIPUserIds() {
    System.out.println("[MsgLisVipCmdTask] 开始加载 VIP 用户白名单...");
    try {
      List<Long> vipIds = readVIPUserIdsFromDatabase();
      VIP_USER_IDS = new HashSet<>(vipIds);
      log.info("VIP 用户白名单加载完成，共 {} 个用户: {}", VIP_USER_IDS.size(), VIP_USER_IDS);
      System.out.println("[MsgLisVipCmdTask] VIP 用户白名单加载完成，共 " + VIP_USER_IDS.size() + " 个用户");
    } catch (Exception e) {
      System.err.println("[MsgLisVipCmdTask] 从数据库加载 VIP 用户失败: " + e.getMessage());
      e.printStackTrace();
      log.error("[MsgLisVipCmdTask] 加载 VIP 用户失败", e);
    }
  }

  /**
   * 运行时刷新 VIP 用户白名单 - 重新从数据库读取
   */
  public void refreshVIPUserIds() {
    System.out.println("[MsgLisVipCmdTask] 开始刷新 VIP 用户白名单...");
    try {
      List<Long> vipIds = readVIPUserIdsFromDatabase();
      VIP_USER_IDS = new HashSet<>(vipIds);
      log.info("VIP 用户白名单刷新完成，共 {} 个用户: {}", VIP_USER_IDS.size(), VIP_USER_IDS);
      System.out.println("[MsgLisVipCmdTask] VIP 用户白名单刷新完成，共 " + VIP_USER_IDS.size() + " 个用户");
    } catch (Exception e) {
      System.err.println("[MsgLisVipCmdTask] 刷新 VIP 用户失败: " + e.getMessage());
      e.printStackTrace();
      log.error("[MsgLisVipCmdTask] 刷新 VIP 用户失败", e);
    }
  }

  /**
   * 从数据库读取 VIP 用户白名单
   * 
   * @return VIP 用户 ID 列表
   */
  private List<Long> readVIPUserIdsFromDatabase() throws Exception {
    List<Long> vipIds = new ArrayList<>();
    
    String sql = "SELECT qq_id FROM vip_id";
    
    try (Connection conn = dataSource.getConnection();
         PreparedStatement pstmt = conn.prepareStatement(sql);
         ResultSet rs = pstmt.executeQuery()) {
      
      while (rs.next()) {
        String qqIdStr = rs.getString("qq_id");
        try {
          long qqId = Long.parseLong(qqIdStr);
          vipIds.add(qqId);
        } catch (NumberFormatException e) {
          System.err.println("[ERROR] VIP 用户 ID 格式错误: " + qqIdStr);
          log.warn("VIP 用户 ID 格式错误: {}", qqIdStr);
        }
      }
    }
    
    return vipIds;
  }

  /**
   * 处理接收到的 WebSocket 消息
   * 检查用户是否在白名单中，如果是则处理特殊指令
   * 
   * @param message WebSocket 消息的 JSON 对象
   */
  public void handleMessage(JSONObject message) {
    // 检查该任务是否启用
    if (!NapCatTaskIsOpen.isMsgLisVipCmdTask) {
      //  System.out.println("isMsgLisVipCmdTask未启用");
      return;
    }

    try {
      String postType = message.getString("post_type");

      // 只处理聊天消息
      if (!"message".equals(postType)) {
        return;
      }

      String messageType = message.getString("message_type");

      // 只处理群聊消息
      if (!"group".equals(messageType)) {
        return;
      }

      Long userId = message.getLong("user_id");
      Long groupId = message.getLong("group_id");
      String rawMessage = message.getString("raw_message");

      String displayName = SingleDefenseService.extractDisplayName(message);

      // 检查白名单用户的指令
      if (isVipUser(userId)) {
        // 处理单防指令
        if (rawMessage != null && rawMessage.contains("指令单防->")) {
          String nickname = vipSingleDefenseLogic.extractDefenseNickname(rawMessage);
          if (nickname != null && !nickname.isEmpty()) {
            singleDefenseService.enableDefense(groupId, nickname);
            log.info("单防已启用 - 群ID: {}，昵称: {}", groupId, nickname);
          }
          return;
        }

        // 处理撤销指令
        if (rawMessage != null && rawMessage.contains("指令撤销")) {
          singleDefenseService.disableDefense(groupId);
          log.info("单防已撤销 - 群ID: {}", groupId);
          return;
        }

        // 处理截取指令
        if (rawMessage != null && rawMessage.contains("指令截取->")) {
          String windowTitle = vipScreenshotLogic.extractWindowTitle(rawMessage);
          if (windowTitle != null && !windowTitle.isEmpty()) {
            log.info("VIP 截取指令触发 - 群ID: {}，窗口标题: {}", groupId, windowTitle);
            vipScreenshotLogic.handleScreenshotCommand(groupId, windowTitle);
          }
          return;
        }

        // 处理查询群组任务指令
        if (rawMessage != null && rawMessage.contains("指令查询群组任务->")) {
          log.info("VIP 查询群组任务指令触发 - 群ID: {}", groupId);
          vipGroupTaskGetLogic.handleGroupTaskQuery(groupId);
          return;
        }

        // 处理修改群组任务指令
        if (rawMessage != null && rawMessage.contains("指令修改群组任务->")) {
          log.info("VIP 修改群组任务指令触发 - 群ID: {}", groupId);
          vipGroupTaskUpLogic.handleGroupTaskUpdate(groupId, rawMessage);
          return;
        }

        // 处理新增群组任务指令
        if (rawMessage != null && rawMessage.contains("指令新增群组任务->")) {
          log.info("VIP 新增群组任务指令触发 - 群ID: {}", groupId);
          vipGroupTaskCreateLogic.handleGroupTaskCreate(groupId, rawMessage);
          return;
        }
      }

      // 检查是否有针对此昵称的单防设置
      if (singleDefenseService.isUnderDefense(groupId, displayName)) {
        log.info("单防触发 - 群ID: {}，昵称: {}", groupId, displayName);
        // 使用逻辑类发送回复
        Long messageId = message.getLong("message_id");
        if (messageId != null) {
          vipSingleDefenseLogic.replyToMessage(groupId, messageId, "不要狗叫");
        }
      }

    } catch (Exception e) {
      log.error("处理 VIP 消息异常", e);
    }
  }

  /**
   * 检查用户是否在 VIP 白名单中
   * 
   * @param userId 用户ID
   * @return true 表示在白名单中，false 表示不在
   */
  private boolean isVipUser(Long userId) {
    if (userId == null) {
      return false;
    }
    return VIP_USER_IDS.contains(userId);
  }

  /**
   * 检查是否触发了 VIP 指令
   * 
   * @param userId 用户ID
   * @param rawMessage 原始消息文本
   * @return 如果触发了 VIP 指令返回 true，否则返回 false
   */
  public boolean isVipCommandTriggered(Long userId, String rawMessage) {
    if (rawMessage == null || rawMessage.isEmpty()) {
      return false;
    }

    // 检查用户是否在 VIP 白名单中
    if (!isVipUser(userId)) {
      return false;
    }

    // 检查是否包含 VIP 指令
    return rawMessage.contains("指令单防->") || 
           rawMessage.contains("指令撤销") || 
           rawMessage.contains("指令截取->") ||
           rawMessage.contains("指令查询群组任务->") ||
           rawMessage.contains("指令修改群组任务->") ||
           rawMessage.contains("指令新增群组任务->");
  }
}
