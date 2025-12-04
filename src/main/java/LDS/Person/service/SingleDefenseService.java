package LDS.Person.service;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

/**
 * 单防服务 - 统一管理全局防御状态
 * 用于防止被单防的用户触发其他任务
 * 
 * 作用域：
 * 1. MsgLisVipATTask: 设置单防
 * 2. MsgLisATTask: 检查是否被单防，如果被单防则忽略
 * 3. MsgLisKeyWordTask: 检查是否被单防，如果被单防则忽略
 */
@Service
@Slf4j
public class SingleDefenseService {

  /**
   * 单防昵称映射: 群ID -> 被单防的昵称
   * 使用 ConcurrentHashMap 支持并发操作
   */
  private static final ConcurrentHashMap<Long, String> SINGLE_DEFENSE_NICKNAMES = new ConcurrentHashMap<>();

  /**
   * 启用单防
   * 
   * @param groupId  群组 ID
   * @param nickname 被单防的昵称
   */
  public void enableDefense(Long groupId, String nickname) {
    if (groupId == null || nickname == null || nickname.trim().isEmpty()) {
      log.warn("无效的单防参数: groupId={}, nickname={}", groupId, nickname);
      return;
    }

    SINGLE_DEFENSE_NICKNAMES.put(groupId, nickname.trim());
    log.info(" 启用单防 - 群ID: {}，目标昵称: {}", groupId, nickname.trim());
  }

  /**
   * 取消单防
   * 
   * @param groupId 群组 ID
   */
  public void disableDefense(Long groupId) {
    if (groupId == null) {
      return;
    }

    if (SINGLE_DEFENSE_NICKNAMES.containsKey(groupId)) {
      String removedNickname = SINGLE_DEFENSE_NICKNAMES.remove(groupId);
      log.info("取消单防 - 群ID: {}，之前的目标昵称: {}", groupId, removedNickname);
    } else {
      log.info("没有活跃的单防设置 - 群ID: {}", groupId);
    }
  }

  /**
   * 检查用户是否被单防
   * 用于其他任务判断是否应该忽略此消息
   * 
   * @param groupId     群组 ID
   * @param displayName 用户显示名称（昵称或群名片）
   * @return true 表示被单防，false 表示没有被单防
   */
  public boolean isUnderDefense(Long groupId, String displayName) {
    if (groupId == null || displayName == null) {
      return false;
    }

    String targetNickname = SINGLE_DEFENSE_NICKNAMES.get(groupId);
    if (targetNickname == null) {
      return false;
    }

    return displayName.equals(targetNickname);
  }

  /**
   * 获取群组的单防目标昵称
   * 
   * @param groupId 群组 ID
   * @return 单防目标昵称，如果没有设置则返回 null
   */
  public String getDefenseNickname(Long groupId) {
    return SINGLE_DEFENSE_NICKNAMES.get(groupId);
  }

  /**
   * 检查群组是否有活跃的单防设置
   * 
   * @param groupId 群组 ID
   * @return true 表示有活跃的单防设置，false 表示没有
   */
  public boolean hasActiveDefense(Long groupId) {
    return groupId != null && SINGLE_DEFENSE_NICKNAMES.containsKey(groupId);
  }

  /**
   * 获取所有活跃的单防设置
   * 
   * @return 单防映射副本
   */
  public ConcurrentHashMap<Long, String> getAllDefenseSettings() {
    return new ConcurrentHashMap<>(SINGLE_DEFENSE_NICKNAMES);
  }

  /**
   * 清除所有单防设置（仅用于测试或管理）
   */
  public void clearAllDefense() {
    int clearedCount = SINGLE_DEFENSE_NICKNAMES.size();
    SINGLE_DEFENSE_NICKNAMES.clear();
    log.info(" 已清除所有单防设置，共 {} 个", clearedCount);
  }

  /**
   * 清除特定群组的单防（别名方法）
   * 
   * @param groupId 群组 ID
   */
  public void clearDefense(Long groupId) {
    disableDefense(groupId);
  }

  /**
   * 从 WebSocket 消息中提取显示名称
   * 优先级：群名片 > 昵称
   * 
   * @param message WebSocket 消息 JSON
   * @return 显示名称
   */
  public static String extractDisplayName(JSONObject message) {
    JSONObject sender = message.getJSONObject("sender");
    if (sender == null) {
      return "未知用户";
    }

    String card = sender.getString("card");
    if (card != null && !card.isEmpty()) {
      return card;
    }

    String nickname = sender.getString("nickname");
    return nickname != null ? nickname : "未知用户";
  }

  /**
   * 记录检查结果（用于日志）
   * 
   * @param groupId     群组 ID
   * @param displayName 用户显示名称
   * @param isDefense   是否被单防
   */
  public void logDefenseCheckResult(Long groupId, String displayName, boolean isDefense) {
    if (isDefense) {
      log.debug(" 用户被单防，忽略消息 - 群ID: {}，昵称: {}", groupId, displayName);
    }
  }
}
