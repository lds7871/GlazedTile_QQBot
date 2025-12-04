package LDS.Person.tasks;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.service.SingleDefenseService;
import LDS.Person.tasks.MsgLisLogic.KeywordTriggerLogic;
import LDS.Person.tasks.MsgLisLogic.BilibiliShareLogic;
import LDS.Person.tasks.MsgSchLogic.RandomChatLogic;

/**
 * 关键词消息监听处理器
 * 监听群聊消息，当消息中包含指定关键词时触发
 * 
 * 优先级：VIP指令 > 用户指令 > 关键词触发 > B站分享
 */
@Component
@Slf4j
public class MsgLisKeyWordTask {

  @Autowired
  private SingleDefenseService singleDefenseService;

  @Autowired
  private KeywordTriggerLogic keywordTriggerLogic;

  @Autowired
  private BilibiliShareLogic bilibiliShareLogic;

  @Autowired
  private MsgLisVipCmdTask msgLisVipCmdTask;

  @Autowired
  private MsgLisUserCmdTask msgLisUserCmdTask;

  /**
   * 处理接收到的 WebSocket 消息
   * 当群聊消息中包含关键词时，自动发送图片
   * 
   * @param message WebSocket 消息的 JSON 对象
   */
  public void handleMessage(JSONObject message) {
    // 检查该任务是否启用
    if (!NapCatTaskIsOpen.isMsgLisKeyWordTask) {
     // System.out.println("isMsgLisKeyWordTask未启用");
     
     // 记录最近一次的群聊ID（用于定时任务）
     Long groupId = message.getLong("group_id");
     RandomChatLogic.recordLastGroupId(groupId.toString());
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

      Long groupId = message.getLong("group_id");
      Long userId = message.getLong("user_id");
      String rawMessage = message.getString("raw_message");

      // 记录最近一次的群聊ID（用于定时任务）
      RandomChatLogic.recordLastGroupId(groupId.toString());

      // 检查用户是否被单防（如果被单防则忽略此消息）
      String displayName = SingleDefenseService.extractDisplayName(message);
      if (singleDefenseService.isUnderDefense(groupId, displayName)) {
        log.debug("用户被单防，忽略关键词触发 - 群ID: {}，昵称: {}", groupId, displayName);
        return;
      }

      // 优先处理 VIP 指令
      if (rawMessage != null && msgLisVipCmdTask.isVipCommandTriggered(userId, rawMessage)) {
      //  log.debug("VIP 指令已触发，跳过关键词处理");
        return;
      }

      // 其次处理用户公共指令
      if (rawMessage != null && msgLisUserCmdTask.isUserCommandTriggered(rawMessage)) {
      //  log.debug("用户指令已触发，跳过关键词处理");
        return;
      }

      // 优先处理哔哩哔哩小程序分享
      if (rawMessage != null && bilibiliShareLogic.handleBilibiliShare(groupId, rawMessage)) {
        return; // 已处理B站分享，不再进行关键词触发
      }

      // 使用逻辑类处理关键词触发
      if (rawMessage != null) {
        keywordTriggerLogic.triggerKeywordResponse(groupId, rawMessage);
      }

    } catch (Exception e) {
      log.error("处理消息异常", e);
    }
  }
}
