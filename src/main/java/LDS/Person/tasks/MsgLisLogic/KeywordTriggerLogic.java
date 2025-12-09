package LDS.Person.tasks.MsgLisLogic;

import com.alibaba.fastjson2.JSONObject;
import LDS.Person.config.ConfigManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * 关键字触发逻辑处理器
 * 监听特定关键字并发送对应的图片回复
 */
@Component
@Slf4j
public class KeywordTriggerLogic {

  @Autowired
  private RestTemplate restTemplate;

  /**
   * 关键字列表 - gal 系列
   */
  private static final List<String> KEYWORDS_gal = Arrays.asList(
      "旮旯", "gal", "GAL", "Gal");

  /**
   * 关键字列表 - 魔裁 系列
   */
  private static final List<String> KEYWORDS_MoCai = Arrays.asList(
      "魔裁", "少女", "魔法", "审判");

  // 使用ConfigManager获取配置，避免重复加载配置文件，提高性能
  private static final ConfigManager configManager = ConfigManager.getInstance();
  private static final String NCAT_API_BASE = configManager.getNapCatApiBase();
  private static final String NCAT_AUTH_TOKEN = configManager.getNapCatAuthToken();

  private static final Random RANDOM = new Random();
  
  // 预定义图片类型数组，避免每次调用时创建新的ArrayList，提高性能
  private static final String[] IMAGE_TYPES = {"BIGHead", "MoCai", "Memes"};

  /**
   * 触发关键字响应
   * 检查消息中是否包含关键字，如果包含则发送对应的图片
   * 若没有关键词触发，则有1/150的概率随机触发一项
   * 
   * @param groupId    群组 ID
   * @param rawMessage 原始消息文本
   */
  public void triggerKeywordResponse(Long groupId, String rawMessage) {
    try {
      if (rawMessage == null || rawMessage.trim().isEmpty()) {
        return;
      }

      // 检查是否包含 gal 关键字
      if (checkKeyword(rawMessage, KEYWORDS_gal)) {
        log.info("触发->BIGHead<- 关键字 - 群ID: {}", groupId);
        sendGroupImageMessage(groupId, "BIGHead");
        return;
      }

      // 检查是否包含 魔裁 关键字
      if (checkKeyword(rawMessage, KEYWORDS_MoCai)) {
        log.info("触发 ->MoCai<- 关键字 - 群ID: {}", groupId);
        sendGroupImageMessage(groupId, "MoCai");
        return;
      }

      // 若没有关键词触发，则有1/110的概率随机触发一项
      if (shouldRandomTrigger()) {
        String randomImageType = getRandomImageType();
        log.info("随机触发 ->{}<<- 图片 - 群ID: {}", randomImageType, groupId);
        sendGroupImageMessage(groupId, randomImageType);
      }

    } catch (Exception e) {
      log.error("触发关键字响应异常 - 群ID: {}", groupId, e);
    }
  }

  /**
   * 公开方法：触发随机图片发送
   * 
   * @param groupId 群组ID
   */
  public void triggerRandomImage(Long groupId) {
    try {
      String randomImageType = getRandomImageType();
      log.info("触发随机图片 ->{}<<- 群ID: {}", randomImageType, groupId);
      sendGroupImageMessage(groupId, randomImageType);
    } catch (Exception e) {
      log.error("触发随机图片异常 - 群ID: {}", groupId, e);
    }
  }

  /**
   * 判断是否应该随机触发（1/110 概率）
   * 
   * @return true 表示触发，false 表示不触发
   */
  private boolean shouldRandomTrigger() {
    return RANDOM.nextInt(110) == 0;
  }

  /**
   * 检查消息中是否包含指定的关键字列表中的任意一个
   * 
   * @param message  消息文本
   * @param keywords 关键字列表
   * @return true 表示包含关键字，false 表示不包含
   */
  private boolean checkKeyword(String message, List<String> keywords) {
    if (message == null || message.trim().isEmpty()) {
      return false;
    }

    for (String keyword : keywords) {
      if (message.contains(keyword)) {
        return true;
      }
    }

    return false;
  }

  /**
   * 发送群组固定图片消息
   * 直接调用 NapCat API 发送图片，而不是仅生成 URL
   * 
   * @param groupId   群组 ID
   * @param imageType 图片类型 ("BIGHead" 或 "MoCai")
   */
  private void sendGroupImageMessage(Long groupId, String imageType) {
    try {
      // 生成随机图片 URL
      String imageUrl = selectImageUrl(imageType);

      // 构建请求体
      JSONObject requestBody = buildGroupImageRequest(groupId, imageUrl);

      // 调用 NapCat API 发送图片
      String url = NCAT_API_BASE + "/send_group_msg";

      HttpHeaders headers = new HttpHeaders();
      headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
      headers.set("Content-Type", "application/json");

      HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
      ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

      if (apiResponse.getStatusCode() == HttpStatus.OK) {
        JSONObject jsonResponse = JSONObject.parseObject(apiResponse.getBody());
        if (jsonResponse.containsKey("status") && "ok".equals(jsonResponse.getString("status"))) {
          // log.info("关键字图片消息发送成功 - 群ID: {}，类型: {}，URL: {}", groupId, imageType,
          // imageUrl);
          return;
        }
      }

      log.error("关键字图片消息发送失败 - 群ID: {}，HTTP状态: {}", groupId, apiResponse.getStatusCode());

    } catch (Exception e) {
      log.error("发送关键字图片消息异常 - 群ID: {}", groupId, e);
    }
  }

  /**
   * 构建发送群组图片的请求体
   * 
   * @param groupId  群组 ID
   * @param imageUrl 图片 URL
   * @return 请求的 JSON 对象
   */
  private JSONObject buildGroupImageRequest(Long groupId, String imageUrl) {
    JSONObject request = new JSONObject();
    request.put("group_id", groupId);

    // 构建消息数组（包含图片）
    com.alibaba.fastjson2.JSONArray messageArray = new com.alibaba.fastjson2.JSONArray();
    JSONObject messageItem = new JSONObject();
    messageItem.put("type", "image");

    JSONObject dataObj = new JSONObject();
    dataObj.put("file", imageUrl);
    messageItem.put("data", dataObj);

    messageArray.add(messageItem);
    request.put("message", messageArray);

    return request;
  }

  /**
   * 随机选择一个图片类型
   * 
   * @return "BIGHead" 或 "MoCai" 或 "Memes"
   */
  private String getRandomImageType() {
    // 直接从预定义数组中随机选择，避免每次创建ArrayList
    return IMAGE_TYPES[RANDOM.nextInt(IMAGE_TYPES.length)];
  }

  /**
   * 根据图片类型选择随机图片 URL
   * 
   * @param imageType 图片类型 ("BIGHead" 或 "MoCai")
   * @return 图片 URL
   */
  private String selectImageUrl(String imageType) {
    String baseUrl = "https://gitee.com/LDS7871/LDS_Memes_Hub/raw/master/";

    if ("BIGHead".equals(imageType)) {
      // BIGHead
      int randomNum = RANDOM.nextInt(151) + 1;
      return baseUrl + "BIGHead/" + randomNum + ".png";
    } else if ("MoCai".equals(imageType)) {
      // MoCai
      int randomNum = RANDOM.nextInt(130) + 1;
      return baseUrl + "MoCai/" + randomNum + ".png";
    } else if ("Memes".equals(imageType)) {
      // Memes
      int randomNum = RANDOM.nextInt(746) + 1;
      return baseUrl + "Memes/" + randomNum + ".png";
    }

    // 默认返回
    int randomNum = RANDOM.nextInt(746) + 1;
    return baseUrl + "Memes/" + randomNum + ".png";
  }
}
