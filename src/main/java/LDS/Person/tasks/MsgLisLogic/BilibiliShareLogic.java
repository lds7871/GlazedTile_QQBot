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
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 哔哩哔哩小程序分享消息处理器
 * 监听群聊中的B站小程序分享，提取预览图和BV号并发送到群聊
 */
@Component
@Slf4j
public class BilibiliShareLogic {

  @Autowired
  private RestTemplate restTemplate;

  private static String NCAT_API_BASE = "00";
  private static String NCAT_AUTH_TOKEN = "0000";

  // 静态初始化块：从 config.properties 读取配置
  static {
    Properties props = new Properties();

    try (InputStream input = BilibiliShareLogic.class.getClassLoader()
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
   * 处理哔哩哔哩小程序分享消息
   * 
   * @param groupId    群组 ID
   * @param rawMessage 原始消息文本
   * @return true 表示消息被处理，false 表示不是B站分享消息
   */
  public boolean handleBilibiliShare(Long groupId, String rawMessage) {
    try {
      if (rawMessage == null || rawMessage.trim().isEmpty()) {
        return false;
      }

      // 检查是否包含 b23.tv 短链接
      if (rawMessage.contains("https://b23.tv/")) {
        log.info("检测到B站短链接 - 群ID: {}", groupId);
        return handleB23TvLink(groupId, rawMessage);
      }

      // 检查是否是 CQ:json 消息
      if (!rawMessage.contains("[CQ:json,data=")) {
        return false;
      }

      // 提取 JSON 数据
      String jsonData = extractJsonFromCQ(rawMessage);
      if (jsonData == null) {
        return false;
      }

      // 解析 JSON
      JSONObject jsonObject = JSONObject.parseObject(jsonData);
      if (jsonObject == null) {
        return false;
      }

      // 检查是否是哔哩哔哩小程序
      JSONObject meta = jsonObject.getJSONObject("meta");
      if (meta == null) {
        return false;
      }

      JSONObject detail1 = meta.getJSONObject("detail_1");
      if (detail1 == null) {
        return false;
      }

      String title = detail1.getString("title");
      if (!"哔哩哔哩".equals(title)) {
        return false;
      }

      log.info("检测到哔哩哔哩小程序分享 - 群ID: {}", groupId);

      // 提取短链接
      String qqdocurl = detail1.getString("qqdocurl");

      if (qqdocurl == null || qqdocurl.isEmpty()) {
        log.warn("qqdocurl 为空");
        return false;
      }

      // 处理 HTML 实体编码
      qqdocurl = decodeHtmlEntities(qqdocurl);

      // log.info("B站分享短链接: {}", qqdocurl);

      // 获取重定向后的 BV 号并发送
      String bvCode = extractBVFromRedirect(qqdocurl);
      if (bvCode != null && !bvCode.isEmpty()) {
        // log.info("提取到 BV 号: {}", bvCode);
        sendGroupMessage(groupId, bvCode);
      } else {
        log.warn("无法提取 BV 号");
      }

      return true;

    } catch (Exception e) {
      log.error("处理哔哩哔哩分享消息异常 - 群ID: {}", groupId, e);
      return false;
    }
  }

  /**
   * 处理包含 b23.tv 短链接的消息
   * 
   * @param groupId    群组 ID
   * @param rawMessage 原始消息文本
   * @return true 表示消息被处理
   */
  private boolean handleB23TvLink(Long groupId, String rawMessage) {
    try {
      // 使用正则提取 https://b23.tv/... 链接
      Pattern pattern = Pattern.compile("https://b23\\.tv/[^\\s]+");
      Matcher matcher = pattern.matcher(rawMessage);

      if (!matcher.find()) {
        log.warn("未能从消息中提取 b23.tv 链接");
        return false;
      }

      String shortUrl = matcher.group();
      // log.info("提取到短链接: {}", shortUrl);

      // 获取重定向后的 BV 号并发送
      String bvCode = extractBVFromRedirect(shortUrl);
      if (bvCode != null && !bvCode.isEmpty()) {
        log.info("提取到 BV 号: {}", bvCode);
        sendGroupMessage(groupId, bvCode);
        return true;
      } else {
        log.warn("无法提取 BV 号");
        return false;
      }

    } catch (Exception e) {
      log.error("处理 b23.tv 链接异常 - 群ID: {}", groupId, e);
      return false;
    }
  }

  /**
   * 从 CQ 码中提取 JSON 数据
   * 
   * @param rawMessage 原始消息
   * @return JSON 字符串
   */
  private String extractJsonFromCQ(String rawMessage) {
    try {
      // 匹配 [CQ:json,data=...] 格式
      int startIdx = rawMessage.indexOf("[CQ:json,data=");
      if (startIdx == -1) {
        return null;
      }

      int dataStart = startIdx + "[CQ:json,data=".length();
      int endIdx = rawMessage.lastIndexOf("]");
      if (endIdx <= dataStart) {
        return null;
      }

      String jsonData = rawMessage.substring(dataStart, endIdx);

      // 解码 CQ 码中的特殊字符
      jsonData = decodeCQSpecialChars(jsonData);

      return jsonData;
    } catch (Exception e) {
      log.error("提取 JSON 数据异常", e);
      return null;
    }
  }

  /**
   * 解码 CQ 码中的特殊字符
   * &#44; -> ,
   * &#91; -> [
   * &#93; -> ]
   * &amp; -> &
   * 
   * @param str 原始字符串
   * @return 解码后的字符串
   */
  private String decodeCQSpecialChars(String str) {
    if (str == null) {
      return null;
    }
    return str
        .replace("&#44;", ",")
        .replace("&#91;", "[")
        .replace("&#93;", "]")
        .replace("&amp;", "&");
  }

  /**
   * 解码 HTML 实体
   * 
   * @param str 原始字符串
   * @return 解码后的字符串
   */
  private String decodeHtmlEntities(String str) {
    if (str == null) {
      return null;
    }
    return str
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'");
  }

  /**
   * 从短链接重定向中提取 BV 号
   * 
   * @param shortUrl 短链接
   * @return BV 号
   */
  private String extractBVFromRedirect(String shortUrl) {
    HttpURLConnection connection = null;
    try {
      URL url = new URL(shortUrl);
      connection = (HttpURLConnection) url.openConnection();
      connection.setInstanceFollowRedirects(false); // 不自动跟随重定向
      connection.setRequestMethod("HEAD");
      connection.setConnectTimeout(5000);
      connection.setReadTimeout(5000);
      connection.setRequestProperty("User-Agent", "Mozilla/5.0");

      int responseCode = connection.getResponseCode();

      if (responseCode == HttpURLConnection.HTTP_MOVED_TEMP ||
          responseCode == HttpURLConnection.HTTP_MOVED_PERM ||
          responseCode == 302 || responseCode == 301) {

        String location = connection.getHeaderField("Location");
        log.info("重定向地址: {}", location);

        if (location != null && location.contains("video/")) {
          // 提取 video/ 之后 ? 之前的字符串
          Pattern pattern = Pattern.compile("video/([^?]+)");
          Matcher matcher = pattern.matcher(location);
          if (matcher.find()) {
            return matcher.group(1);
          }
        }
      }

      return null;

    } catch (Exception e) {
      log.error("获取重定向地址异常: {}", shortUrl, e);
      return null;
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  /**
   * 发送群组文本消息
   * 
   * @param groupId 群组 ID
   * @param text    消息文本
   */
  private void sendGroupMessage(Long groupId, String text) {
    try {
      JSONObject requestBody = new JSONObject();
      requestBody.put("group_id", groupId);

      // 构建消息数组（包含文本）
      com.alibaba.fastjson2.JSONArray messageArray = new com.alibaba.fastjson2.JSONArray();
      JSONObject messageItem = new JSONObject();
      messageItem.put("type", "text");

      JSONObject dataObj = new JSONObject();
      dataObj.put("text", text);
      messageItem.put("data", dataObj);

      messageArray.add(messageItem);
      requestBody.put("message", messageArray);

      // 调用 NapCat API 发送消息
      String url = NCAT_API_BASE + "/send_group_msg";

      HttpHeaders headers = new HttpHeaders();
      headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
      headers.set("Content-Type", "application/json");

      HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
      ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

      if (apiResponse.getStatusCode() == HttpStatus.OK) {
        JSONObject jsonResponse = JSONObject.parseObject(apiResponse.getBody());
        if (jsonResponse.containsKey("status") && "ok".equals(jsonResponse.getString("status"))) {
          // log.info("BV号消息发送成功 - 群ID: {}，BV: {}", groupId, text);
          return;
        }
      }

      log.error("BV号消息发送失败 - 群ID: {}，HTTP状态: {}", groupId, apiResponse.getStatusCode());

    } catch (Exception e) {
      log.error("发送BV号消息异常 - 群ID: {}", groupId, e);
    }
  }
}
