package LDS.Person.tasks.MsgLisLogic;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * VIP 单防指令逻辑处理器
 * 处理单防指令的解析和回复
 */
@Component
@Slf4j
public class VIPSingleDefenseLogic {

  @Autowired
  private RestTemplate restTemplate;

  private static String NCAT_API_BASE = "00";
  private static String NCAT_AUTH_TOKEN = "0000";

  // 静态初始化块：从 config.properties 读取配置
  static {
    Properties props = new Properties();

    try (InputStream input = VIPSingleDefenseLogic.class.getClassLoader()
        .getResourceAsStream("config.properties")) {
      if (input != null) {
        props.load(input);
        NCAT_API_BASE = props.getProperty("NapCatApiBase", NCAT_API_BASE);
        NCAT_AUTH_TOKEN = props.getProperty("NapCatAuthToken", NCAT_AUTH_TOKEN);
      } else {
        System.out.println("[WARN] config.properties 没有找到, 会使用不可用的默认值");
      }
    } catch (Exception e) {
      System.err.println("[ERROR] 无法读取 config.properties: " + e.getMessage());
    }
  }

  /**
   * 从消息中提取单防昵称
   * 格式: "指令单防->(昵称)"
   * 
   * @param rawMessage 原始消息
   * @return 提取的昵称，如果格式不正确则返回 null
   */
  public String extractDefenseNickname(String rawMessage) {
    try {
      // 使用前向断言，支持昵称中包含空格
      Pattern pattern = Pattern.compile("指令单防->(.+?)(?=\\s*\\[|\\s*$)");
      Matcher matcher = pattern.matcher(rawMessage);

      if (matcher.find()) {
        String nickname = matcher.group(1).trim();
        if (!nickname.isEmpty()) {
          return nickname;
        }
      }
    } catch (Exception e) {
      log.error("提取昵称异常", e);
    }

    return null;
  }

  /**
   * 回复群组消息（用于单防触发时回复）
   * 
   * @param groupId   群组 ID
   * @param messageId 消息 ID
   * @param replyText 回复文本
   * @return 是否发送成功
   */
  public boolean replyToMessage(Long groupId, Long messageId, String replyText) {
    try {
      String url = NCAT_API_BASE + "/send_group_msg";

      JSONObject requestBody = buildReplyMessageRequest(groupId, String.valueOf(messageId), replyText);

      HttpHeaders headers = new HttpHeaders();
      headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
      headers.set("Content-Type", "application/json");

      HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
      ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

      if (apiResponse.getStatusCode() == HttpStatus.OK) {
        JSONObject jsonResponse = JSONObject.parseObject(apiResponse.getBody());
        if (jsonResponse.containsKey("status") && "ok".equals(jsonResponse.getString("status"))) {
          log.info("单防回复消息发送成功 - 群ID: {}", groupId);
          return true;
        }
      }

      log.error("单防回复消息发送失败 - 群ID: {}，HTTP状态: {}", groupId, apiResponse.getStatusCode());

    } catch (Exception e) {
      log.error("发送单防回复消息异常 - 群ID: {}", groupId, e);
    }

    return false;
  }

  /**
   * 构建回复消息请求体
   */
  private JSONObject buildReplyMessageRequest(Long groupId, String messageId, String replyText) {
    JSONObject request = new JSONObject();
    request.put("group_id", groupId);

    com.alibaba.fastjson2.JSONArray messageArray = new com.alibaba.fastjson2.JSONArray();

    // 第一条：回复引用
    JSONObject replyItem = new JSONObject();
    replyItem.put("type", "reply");
    JSONObject replyData = new JSONObject();
    replyData.put("id", messageId);
    replyItem.put("data", replyData);
    messageArray.add(replyItem);

    // 第二条：回复文本
    JSONObject textItem = new JSONObject();
    textItem.put("type", "text");
    JSONObject textData = new JSONObject();
    textData.put("text", replyText);
    textItem.put("data", textData);
    messageArray.add(textItem);

    request.put("message", messageArray);

    return request;
  }
}
