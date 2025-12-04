package LDS.Person.tasks.MsgLisLogic;

import com.alibaba.fastjson2.JSONObject;
import LDS.Person.util.DSchatNcatQQ;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpServerErrorException;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;
import java.util.Random;

/**
 * @机器人 AI 回复逻辑处理器
 *      处理 @机器人 消息的自动回复
 */
@Component
@Slf4j
public class ATBotReplyLogic {

  @Autowired
  private RestTemplate restTemplate;

  private static String NCAT_API_BASE = "00";
  private static String NCAT_AUTH_TOKEN = "0000";
  private static Long[] VIP_USER_IDS = {};

  // 静态初始化块：从 config.properties 读取配置
  static {
    Properties props = new Properties();

    try (InputStream input = ATBotReplyLogic.class.getClassLoader()
        .getResourceAsStream("config.properties")) {
      if (input != null) {
        props.load(input);
        NCAT_API_BASE = props.getProperty("NapCatApiBase", NCAT_API_BASE);
        NCAT_AUTH_TOKEN = props.getProperty("NapCatAuthToken", NCAT_AUTH_TOKEN);

        // 读取 VIP 用户 ID 列表
        String vipUserIdsStr = props.getProperty("VIPUserIds", "");
        if (vipUserIdsStr != null && !vipUserIdsStr.trim().isEmpty()) {
          String[] idStrings = vipUserIdsStr.split(",");
          VIP_USER_IDS = new Long[idStrings.length];
          for (int i = 0; i < idStrings.length; i++) {
            try {
              VIP_USER_IDS[i] = Long.parseLong(idStrings[i].trim());
            } catch (NumberFormatException e) {
              System.err.println("[ERROR] VIP用户ID格式错误: " + idStrings[i]);
            }
          }
        }
      } else {
        System.out.println("[WARN] config.properties 没有找到, 会使用不可用的默认值");
      }
    } catch (Exception e) {
      System.err.println("[ERROR] 无法读取 config.properties: " + e.getMessage());
    }
  }

  /**
   * 发送自动回复消息
   * 
   * @param groupId         群组 ID
   * @param userId          用户 ID
   * @param originalMessage 原始消息
   * @param nickname        用户昵称
   */
  public void sendAutoReply(Long groupId, Long userId, String originalMessage, String nickname) {
    try {
      // 调用 DeepSeek API 获取 AI 回复
      String replyText = null;
      try {
        // 构建带昵称的消息
        String contextMessage = nickname + ": " + originalMessage;

        System.out.println("\n" + contextMessage + "\n");

        
          //使用 DSchatNcatQQ（保存上下文）
          DSchatNcatQQ.setUserNickname(String.valueOf(userId), nickname);
          DSchatNcatQQ normalClient = new DSchatNcatQQ(System.getenv("DEEPSEEK_API_KEY"));
          replyText = normalClient.Usedeepseek(contextMessage, String.valueOf(userId));
        

        // 检查回复是否为空
        if (replyText == null || replyText.isEmpty()) {
          log.warn("DeepSeek 返回空回复，使用默认回复");
          replyText = "收到消息，但AI没有生成有效回复";
        }

      } catch (Exception e) {
        log.error("调用 DeepSeek API 异常，使用默认回复: {}", e.getMessage());
        replyText = "收到消息，API调用异常";
      }

      // 使用已有的方法构建消息请求体
      JSONObject requestBody = buildGroupMessageRequest(groupId, replyText);

      // 调用 NapCat API 发送消息（带重试机制）
      boolean success = sendMessageWithRetry(groupId, requestBody, 2);

      // 如果发送成功，有五分之一的概率发送固定图片
      if (success) {
        if (shouldSendImage()) {
          sendGroupFixedImage(groupId);
        }
      }

    } catch (Exception e) {
      log.error("发送自动回复异常 - 群ID: {}", groupId, e);
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
    return Arrays.asList(VIP_USER_IDS).contains(userId);
  }

  /**
   * 发送消息并支持重试
   * 
   * @param groupId     群组 ID
   * @param requestBody 请求体
   * @param retryCount  重试次数
   * @return 是否发送成功
   */
  private boolean sendMessageWithRetry(Long groupId, JSONObject requestBody, int retryCount) {
    String url = NCAT_API_BASE + "/send_group_msg";

    for (int i = 1; i <= retryCount; i++) {
      try {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
        headers.set("Content-Type", "application/json");

        HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
        ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

        if (apiResponse.getStatusCode() == HttpStatus.OK) {
          JSONObject jsonResponse = JSONObject.parseObject(apiResponse.getBody());
          if (jsonResponse.containsKey("status") && "ok".equals(jsonResponse.getString("status"))) {
            log.info("✅ 自动回复消息发送成功，群ID: {}", groupId);
            return true;
          } else {
            log.error("第 {} 次发送失败 - 群ID: {}，错误: {}", i, groupId, jsonResponse.getString("message"));
          }
        } else {
          log.error("第 {} 次发送失败 - 群ID: {}，HTTP状态: {}", i, groupId, apiResponse.getStatusCode());
        }

        // 重试前等待一段时间
        if (i < retryCount) {
          long waitTime = 1000 * i; // 等待时间递增：1秒、2秒、3秒
          log.info("等待 {} 毫秒后进行第 {} 次重试...", waitTime, i + 1);
          Thread.sleep(waitTime);
        }

      } catch (HttpServerErrorException e) {
        log.error("第 {} 次发送异常 - 群ID: {}，HTTP错误: {} {}", i, groupId, e.getStatusCode());

        // 如果是 502/503/504 等服务器错误，进行重试
        if (i < retryCount && (e.getStatusCode().value() >= 500)) {
          try {
            long waitTime = 2000 * i; // 等待时间：2秒、4秒、6秒
            log.info("服务器错误，等待 {} 毫秒后进行第 {} 次重试...", waitTime, i + 1);
            Thread.sleep(waitTime);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        } else {
          break;
        }
      } catch (Exception e) {
        log.error("第 {} 次发送异常 - 群ID: {}", i, groupId, e);
        if (i < retryCount) {
          try {
            Thread.sleep(1000 * i);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            break;
          }
        }
      }
    }

    return false;
  }

  /**
   * 构建群聊消息请求体
   */
  private JSONObject buildGroupMessageRequest(Long groupId, String messageText) {
    JSONObject request = new JSONObject();
    request.put("group_id", groupId);

    // 构建消息数组
    com.alibaba.fastjson2.JSONArray messageArray = new com.alibaba.fastjson2.JSONArray();
    JSONObject messageItem = new JSONObject();
    messageItem.put("type", "text");

    JSONObject dataObj = new JSONObject();
    dataObj.put("text", messageText);
    messageItem.put("data", dataObj);

    messageArray.add(messageItem);
    request.put("message", messageArray);

    return request;
  }

  /**
   * 判断是否应该发送图片（五分之一概率）
   */
  private boolean shouldSendImage() {
    Random random = new Random();
    return random.nextInt(5) == 0; // 20%的概率（1/5）
  }

  /**
   * 调用 NCatSendMessageController 的 sendGroupFixedImage 接口
   */
  private void sendGroupFixedImage(Long groupId) {
    try {
      String keyWord = "BIGHead"; // 默认关键字
      String url = "http://localhost:8090/api/ncat/send/group-fixed-image?groupId=" + groupId + "&keyWord="
          + keyWord;

      HttpHeaders headers = new HttpHeaders();
      headers.set("Content-Type", "application/json");

      HttpEntity<String> entity = new HttpEntity<>(headers);
      ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

      if (response.getStatusCode().is2xxSuccessful()) {
        log.info("抽中随机调用图片方法");
      } else {
        log.warn("❌ 随机图片发送失败，群ID: {}，状态码: {}", groupId, response.getStatusCode());
      }
    } catch (Exception e) {
      log.error("❌ 随机调用固定图片接口异常，群ID: {}", groupId, e);
    }
  }
}
