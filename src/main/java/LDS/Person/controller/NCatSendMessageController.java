package LDS.Person.controller;

import LDS.Person.dto.request.SendGroupMessageRequest;
import LDS.Person.dto.request.ReplyGroupMessageRequest;
import LDS.Person.dto.request.SendGroupImageRequest;
import LDS.Person.dto.response.SendGroupMessageResponse;
import LDS.Person.tasks.MsgLisATTask;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.Random;
import java.util.Properties;

/**
 * NapCat 群聊消息发送控制器
 * 用于调用 NapCat 服务的消息发送接口
 */
@RestController
@RequestMapping("/api/ncat/send")
@Api(tags = "NapCat 消息发送", description = "NapCat 消息发送相关接口")
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class NCatSendMessageController {

    @Autowired
    private RestTemplate restTemplate;

    private static String NCAT_API_BASE = "00";
    private static String NCAT_AUTH_TOKEN = "0000";
    private static String FIXED_IMAGE_URL = "https://example.com/image.jpg"; // 固定图片URL

    // 静态初始化块：从 config.properties 读取配置
    static {
        Properties props = new Properties();

        try (InputStream input = MsgLisATTask.class.getClassLoader()
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
     * 发送群聊文本消息=======================================================================================================================
     */
    @PostMapping("/group-message")
    @ApiOperation(value = "发送群聊文本消息", notes = "向指定群组发送文本消息")
    public ResponseEntity<SendGroupMessageResponse> sendGroupMessage(
            @RequestBody SendGroupMessageRequest request) {
        try {
            // 参数校验
            if (request.getGroupId() == null || request.getGroupId() <= 0) {
                log.warn("群组 ID 无效: {}", request.getGroupId());
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("群组 ID 无效"));
            }

            if (request.getText() == null || request.getText().trim().isEmpty()) {
                log.warn("消息文本为空");
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("消息文本不能为空"));
            }

           // log.info("准备发送群聊消息，群组ID: {}，消息: {}", request.getGroupId(), request.getText());

            // 构建请求体
            String messageText = request.getText();
            JSONObject requestBody = buildSendGroupMessageRequest(request.getGroupId(), messageText);

           // log.debug("请求体: {}", requestBody.toJSONString());

            // 调用 NapCat API
            String url = NCAT_API_BASE + "/send_group_msg";
           // log.debug("调用 NapCat API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (apiResponse.getStatusCode() != HttpStatus.OK) {
                log.error("NapCat API 返回错误状态: {}", apiResponse.getStatusCode());
                return ResponseEntity.status(apiResponse.getStatusCode())
                        .body(SendGroupMessageResponse.error("API 请求失败，状态码: " + apiResponse.getStatusCode()));
            }

            // 解析响应
            JSONObject jsonResponse = JSON.parseObject(apiResponse.getBody());
           // log.debug("API 响应: {}", jsonResponse.toJSONString());

            // 检查是否成功
            if (jsonResponse.containsKey("status") && !jsonResponse.getString("status").equals("ok")) {
                String errorMsg = jsonResponse.getString("message");
                log.error("NapCat API 返回错误: {}", errorMsg);
                SendGroupMessageResponse response = SendGroupMessageResponse.error(errorMsg);
                response.setRawResponse(jsonResponse);
                return ResponseEntity.badRequest().body(response);
            }

            // 提取消息 ID
            Integer messageId = null;
            if (jsonResponse.containsKey("data")) {
                JSONObject data = jsonResponse.getJSONObject("data");
                messageId = data.getInteger("message_id");
            }

           // log.info("✅ 群聊消息发送成功，消息ID: {}", messageId);

            SendGroupMessageResponse response = SendGroupMessageResponse.success(messageId);
            response.setRawResponse(jsonResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("发送群聊消息异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SendGroupMessageResponse.error("服务器错误: " + e.getMessage()));
        }
    }

    /**
     * 构建发送群聊消息请求体
     */
    private JSONObject buildSendGroupMessageRequest(Long groupId, String messageText) {
        JSONObject request = new JSONObject();
        request.put("group_id", groupId);

        // 构建消息数组
        JSONArray messageArray = new JSONArray();
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
     * 回复群聊消息=======================================================================================================================
     */
    @PostMapping("/reply-message")
    @ApiOperation(value = "回复群聊消息", notes = "回复指定群组中的指定消息")
    public ResponseEntity<SendGroupMessageResponse> replyGroupMessage(
            @RequestBody ReplyGroupMessageRequest request) {
        try {
            // 参数校验
            if (request.getGroupId() == null || request.getGroupId() <= 0) {
                log.warn("群组 ID 无效: {}", request.getGroupId());
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("群组 ID 无效"));
            }

            if (request.getMessageId() == null || request.getMessageId().trim().isEmpty()) {
                log.warn("被回复消息 ID 无效");
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("被回复消息 ID 不能为空"));
            }

            if (request.getText() == null || request.getText().trim().isEmpty()) {
                log.warn("回复消息文本为空");
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("回复消息文本不能为空"));
            }

            //log.info("准备回复群聊消息，群组ID: {}，被回复消息ID: {}，回复内容: {}",
            //        request.getGroupId(), request.getMessageId(), request.getText());

            // 构建请求体
            JSONObject requestBody = buildReplyMessageRequest(request.getGroupId(),
                    request.getMessageId(), request.getText());

            //log.debug("请求体: {}", requestBody.toJSONString());

            // 调用 NapCat API
            String url = NCAT_API_BASE + "/send_group_msg";
            //log.debug("调用 NapCat API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (apiResponse.getStatusCode() != HttpStatus.OK) {
                log.error("NapCat API 返回错误状态: {}", apiResponse.getStatusCode());
                return ResponseEntity.status(apiResponse.getStatusCode())
                        .body(SendGroupMessageResponse.error("API 请求失败，状态码: " + apiResponse.getStatusCode()));
            }

            // 解析响应
            JSONObject jsonResponse = JSON.parseObject(apiResponse.getBody());
            //log.debug("API 响应: {}", jsonResponse.toJSONString());

            // 检查是否成功
            if (jsonResponse.containsKey("status") && !jsonResponse.getString("status").equals("ok")) {
                String errorMsg = jsonResponse.getString("message");
                log.error("NapCat API 返回错误: {}", errorMsg);
                SendGroupMessageResponse response = SendGroupMessageResponse.error(errorMsg);
                response.setRawResponse(jsonResponse);
                return ResponseEntity.badRequest().body(response);
            }

            // 提取消息 ID
            Integer messageId = null;
            if (jsonResponse.containsKey("data")) {
                JSONObject data = jsonResponse.getJSONObject("data");
                messageId = data.getInteger("message_id");
            }

            //log.info("✅ 群聊回复消息发送成功，消息ID: {}", messageId);

            SendGroupMessageResponse response = SendGroupMessageResponse.success(messageId);
            response.setRawResponse(jsonResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("回复群聊消息异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SendGroupMessageResponse.error("服务器错误: " + e.getMessage()));
        }
    }

    /**
     * 构建回复消息请求体
     */
    private JSONObject buildReplyMessageRequest(Long groupId, String repliedMessageId, String replyText) {
        JSONObject request = new JSONObject();
        request.put("group_id", groupId);

        // 构建消息数组
        JSONArray messageArray = new JSONArray();

        // 第一条：回复引用
        JSONObject replyItem = new JSONObject();
        replyItem.put("type", "reply");
        JSONObject replyData = new JSONObject();
        replyData.put("id", repliedMessageId);
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

    /**
     * 发送群聊图片消息=======================================================================================================================
     */
    @PostMapping("/group-image")
    @ApiOperation(value = "发送群聊图片消息", notes = "向指定群组发送图片消息")
    public ResponseEntity<SendGroupMessageResponse> sendGroupImage(
            @RequestBody SendGroupImageRequest request) {
        try {
            // 参数校验
            if (request.getGroupId() == null || request.getGroupId() <= 0) {
                log.warn("群组 ID 无效: {}", request.getGroupId());
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("群组 ID 无效"));
            }

            if (request.getFile() == null || request.getFile().trim().isEmpty()) {
                log.warn("图片文件路径为空");
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("图片文件路径不能为空"));
            }

            //log.info("准备发送群聊图片消息，群组ID: {}", request.getGroupId());

            // 构建请求体
            JSONObject requestBody = buildSendGroupImageRequest(request.getGroupId(), request.getFile());

            //log.debug("请求体: {}", requestBody.toJSONString().length() > 50 ? requestBody.toJSONString().substring(0, 50)
            //        : requestBody.toJSONString());

            // 调用 NapCat API
            String url = NCAT_API_BASE + "/send_group_msg";
            //log.debug("调用 NapCat API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (apiResponse.getStatusCode() != HttpStatus.OK) {
                log.error("NapCat API 返回错误状态: {}", apiResponse.getStatusCode());
                return ResponseEntity.status(apiResponse.getStatusCode())
                        .body(SendGroupMessageResponse.error("API 请求失败，状态码: " + apiResponse.getStatusCode()));
            }

            // 解析响应
            JSONObject jsonResponse = JSON.parseObject(apiResponse.getBody());
            //log.debug("API 响应: {}", jsonResponse.toJSONString());

            // 检查是否成功
            if (jsonResponse.containsKey("status") && !jsonResponse.getString("status").equals("ok")) {
                String errorMsg = jsonResponse.getString("message");
                log.error("NapCat API 返回错误: {}", errorMsg);
                SendGroupMessageResponse response = SendGroupMessageResponse.error(errorMsg);
                response.setRawResponse(jsonResponse);
                return ResponseEntity.badRequest().body(response);
            }

            // 提取消息 ID
            Integer messageId = null;
            if (jsonResponse.containsKey("data")) {
                JSONObject data = jsonResponse.getJSONObject("data");
                messageId = data.getInteger("message_id");
            }

            //log.info("✅ 群聊图片消息发送成功，消息ID: {}", messageId);

            SendGroupMessageResponse response = SendGroupMessageResponse.success(messageId);
            response.setRawResponse(jsonResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("发送群聊图片消息异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SendGroupMessageResponse.error("服务器错误: " + e.getMessage()));
        }
    }

    /**
     * 构建发送群聊图片请求体
     */
    private JSONObject buildSendGroupImageRequest(Long groupId, String imageFile) {
        JSONObject request = new JSONObject();
        request.put("group_id", groupId);

        // 构建消息数组
        JSONArray messageArray = new JSONArray();
        JSONObject imageItem = new JSONObject();
        imageItem.put("type", "image");

        JSONObject dataObj = new JSONObject();
        dataObj.put("file", imageFile);
        imageItem.put("data", dataObj);

        messageArray.add(imageItem);
        request.put("message", messageArray);

        return request;
    }

    /**
     * 发送群聊语音消息=======================================================================================================================
     */
    @PostMapping("/group-sound")
    @ApiOperation(value = "发送群聊语音消息", notes = "向指定群组发送语音消息")
    public ResponseEntity<SendGroupMessageResponse> sendGroupSound(
            @RequestBody SendGroupImageRequest request) {
        try {
            // 参数校验
            if (request.getGroupId() == null || request.getGroupId() <= 0) {
                log.warn("群组 ID 无效: {}", request.getGroupId());
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("群组 ID 无效"));
            }

            if (request.getFile() == null || request.getFile().trim().isEmpty()) {
                log.warn("语音文件路径为空");
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("语音文件路径不能为空"));
            }

            // log.info("准备发送群聊语音消息，群组ID: {}", request.getGroupId());

            // 构建请求体
            JSONObject requestBody = buildSendGroupSoundRequest(request.getGroupId(), request.getFile());

            // log.debug("请求体: {}", requestBody.toJSONString().length() > 50 ?
            // requestBody.toJSONString().substring(0, 50)
            // : requestBody.toJSONString());

            // 调用 NapCat API
            String url = NCAT_API_BASE + "/send_group_msg";
            log.debug("调用 NapCat API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (apiResponse.getStatusCode() != HttpStatus.OK) {
                log.error("NapCat API 返回错误状态: {}", apiResponse.getStatusCode());
                return ResponseEntity.status(apiResponse.getStatusCode())
                        .body(SendGroupMessageResponse.error("API 请求失败，状态码: " + apiResponse.getStatusCode()));
            }

            // 解析响应
            JSONObject jsonResponse = JSON.parseObject(apiResponse.getBody());
            // log.debug("API 响应: {}", jsonResponse.toJSONString());

            // 检查是否成功
            if (jsonResponse.containsKey("status") && !jsonResponse.getString("status").equals("ok")) {
                String errorMsg = jsonResponse.getString("message");
                log.error("NapCat API 返回错误: {}", errorMsg);
                SendGroupMessageResponse response = SendGroupMessageResponse.error(errorMsg);
                response.setRawResponse(jsonResponse);
                return ResponseEntity.badRequest().body(response);
            }

            // 提取消息 ID
            Integer messageId = null;
            if (jsonResponse.containsKey("data")) {
                JSONObject data = jsonResponse.getJSONObject("data");
                messageId = data.getInteger("message_id");
            }

            // log.info("✅ 群聊语音消息发送成功，消息ID: {}", messageId);

            SendGroupMessageResponse response = SendGroupMessageResponse.success(messageId);
            response.setRawResponse(jsonResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("发送群聊语音消息异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SendGroupMessageResponse.error("服务器错误: " + e.getMessage()));
        }
    }

    /**
     * 构建发送群聊语音请求体
     */
    private JSONObject buildSendGroupSoundRequest(Long groupId, String soundFile) {
        JSONObject request = new JSONObject();
        request.put("group_id", groupId);

        // 构建消息数组
        JSONArray messageArray = new JSONArray();
        JSONObject soundItem = new JSONObject();
        soundItem.put("type", "record");

        JSONObject dataObj = new JSONObject();
        dataObj.put("file", soundFile);
        soundItem.put("data", dataObj);

        messageArray.add(soundItem);
        request.put("message", messageArray);

        return request;
    }

    /**
     * 发送固定随机URL的群聊图片消息=======================================================================================================================
     */
    @PostMapping("/group-fixed-image")
    @ApiOperation(value = "发送固定URL的群聊图片", notes = "向指定群组发送预设URL的图片消息（需输入groupId和keyWord）")
    public ResponseEntity<SendGroupMessageResponse> sendGroupFixedImage(
            @RequestParam(value = "groupId") Long groupId,
            @RequestParam(value = "keyWord") String keyWord) {
        try {
            // 参数校验
            if (groupId == null || groupId <= 0) {
                log.warn("群组 ID 无效: {}", groupId);
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("群组 ID 无效"));
            }

            if (keyWord == null || keyWord.trim().isEmpty()) {
                log.warn("关键字为空");
                return ResponseEntity.badRequest()
                        .body(SendGroupMessageResponse.error("关键字不能为空"));
            }

            // 固定URL逻辑，拿取图片随机数，然后拼接地址
            Random random = new Random();
            int Imgcode = 1;
            if (keyWord.equals("BIGHead")) {
                Imgcode = random.nextInt(141) + 1;
            } // Gal图库的大小
            if (keyWord.equals("MoCai")) {
                Imgcode = random.nextInt(124) + 1;
            } // 魔法少女的魔法裁判图库的大小

            // https://gitee.com/LDS7871/LDS_Memes_Hub/raw/master/BIGHead/1.png
            FIXED_IMAGE_URL = "https://gitee.com/LDS7871/LDS_Memes_Hub/raw/master/" + keyWord + "/" + Imgcode + ".png";

            log.info("准备发送固定URL群聊图片消息，群组ID: {}，关键字: {}，图片URL: {}，抽取号: {}", groupId, keyWord, FIXED_IMAGE_URL, Imgcode);

            // 构建请求体（使用固定URL）
            JSONObject requestBody = buildSendGroupImageFixedRequest(groupId, keyWord, FIXED_IMAGE_URL);

            // log.debug("请求体: {}", requestBody.toJSONString());

            // 调用 NapCat API
            String url = NCAT_API_BASE + "/send_group_msg";
            // log.debug("调用 NapCat API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<String> entity = new HttpEntity<>(requestBody.toJSONString(), headers);
            ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.POST, entity, String.class);

            if (apiResponse.getStatusCode() != HttpStatus.OK) {
                log.error("NapCat API 返回错误状态: {}", apiResponse.getStatusCode());
                return ResponseEntity.status(apiResponse.getStatusCode())
                        .body(SendGroupMessageResponse.error("API 请求失败，状态码: " + apiResponse.getStatusCode()));
            }

            // 解析响应
            JSONObject jsonResponse = JSON.parseObject(apiResponse.getBody());
            // log.debug("API 响应: {}", jsonResponse.toJSONString());

            // 检查是否成功
            if (jsonResponse.containsKey("status") && !jsonResponse.getString("status").equals("ok")) {
                String errorMsg = jsonResponse.getString("message");
                log.error("NapCat API 返回错误: {}", errorMsg);
                SendGroupMessageResponse response = SendGroupMessageResponse.error(errorMsg);
                response.setRawResponse(jsonResponse);
                return ResponseEntity.badRequest().body(response);
            }

            // 提取消息 ID
            Integer messageId = null;
            if (jsonResponse.containsKey("data")) {
                JSONObject data = jsonResponse.getJSONObject("data");
                messageId = data.getInteger("message_id");
            }

            // log.info("✅ 固定URL群聊图片消息发送成功，消息ID: {}", messageId);

            SendGroupMessageResponse response = SendGroupMessageResponse.success(messageId);
            response.setRawResponse(jsonResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("发送固定URL群聊图片消息异常", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(SendGroupMessageResponse.error("服务器错误: " + e.getMessage()));
        }
    }

    /**
     * 构建发送固定URL群聊图片请求体
     */
    private JSONObject buildSendGroupImageFixedRequest(Long groupId, String keyWord, String imageUrl) {
        JSONObject request = new JSONObject();
        request.put("group_id", groupId);

        // 构建消息数组
        JSONArray messageArray = new JSONArray();
        JSONObject imageItem = new JSONObject();
        imageItem.put("type", "image");

        JSONObject dataObj = new JSONObject();
        dataObj.put("file", imageUrl);
        imageItem.put("data", dataObj);

        messageArray.add(imageItem);
        request.put("message", messageArray);

        return request;
    }
}
