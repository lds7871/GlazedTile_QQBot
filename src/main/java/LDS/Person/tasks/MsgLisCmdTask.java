package LDS.Person.tasks;

import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.config.ConfigManager;
import LDS.Person.tasks.MsgLisCmdLogic.GetSystemInfoLogic;
import LDS.Person.tasks.MsgLisCmdLogic.GetSystemInfoLogic.CmdExecutionResult;
import LDS.Person.tasks.MsgLisCmdLogic.GetPUBGLogic;
import LDS.Person.tasks.MsgLisCmdLogic.CreateSoundLogic;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 消息命令处理任务
 * 监听群聊消息，根据关键词触发相应的命令逻辑
 * 
 * 支持的关键词：
 * - "-负载": 获取系统负载信息并生成图表
 * - 易于扩展：添加新关键词只需在 initLogicHandlers() 中注册新的处理器
 * 
 * 可拓展架构说明：
 * 1. 每个关键词对应一个逻辑处理器（实现 IMsgLisCmdLogic 接口）
 * 2. 处理器在 initLogicHandlers() 中注册
 * 3. 新增关键词时：在 MsgLisCmdLogic/ 目录下创建新的 Logic 类，然后在此处注册即可
 */
@Component
@Slf4j
public class MsgLisCmdTask {

  private static final Pattern PUBG_COMMAND_PATTERN = Pattern.compile("PUBG-([^\\s]+)", Pattern.CASE_INSENSITIVE);

  @Autowired
  private RestTemplate restTemplate;

  private static final ConfigManager configManager = ConfigManager.getInstance();
  private static final String NCAT_API_BASE = configManager.getNapCatApiBase();
  private static final String NCAT_AUTH_TOKEN = configManager.getNapCatAuthToken();

  /**
   * 指令描述符：封装指令的匹配逻辑和执行逻辑
   * <p>
   * name 指令名称（用于日志）<br>
   * extractor 从消息中提取指令参数，不匹配时返回 null<br>
   * executor 执行指令 (groupId, extractedArg)
   */
  private record CommandEntry(
      String name,
      Function<String, String> extractor,
      BiConsumer<Long, String> executor) {
  }

  // ==================== 指令注册区 ====================
  // 新增指令：在此列表中添加 CommandEntry，无需修改其他任何方法
  private List<CommandEntry> commands;

  // 重试相关常量
  private static final long RETRY_BASE_DELAY_MS = 1000L;
  private static final long SERVER_ERROR_RETRY_DELAY_MS = 2000L;

  /**
   * PUBG 命令专用单线程执行器
   * 使用单线程保证 PUBG 查询按提交顺序依次执行，
   * 同时不阻塞 WebSocket 消息处理线程，确保其它任务可正常调度。
   */
  private final ExecutorService pubgExecutor = Executors.newSingleThreadExecutor(r -> {
    Thread t = new Thread(r, "pubg-query-worker");
    return t;
  });

  /**
   * 应用关闭时优雅停止 PUBG 执行器
   */
  @PreDestroy
  public void shutdown() {
    log.info("正在关闭 PUBG 命令执行器...");
    pubgExecutor.shutdown();
    try {
      if (!pubgExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
        pubgExecutor.shutdownNow();
      }
    } catch (InterruptedException e) {
      pubgExecutor.shutdownNow();
      Thread.currentThread().interrupt();
    }
    log.info("PUBG 命令执行器已关闭");
  }

  /**
   * 初始化指令注册列表
   * 所有指令集中在此处配置和管理，新增指令只需在此处添加 CommandEntry 即可
   */
  @PostConstruct
  private void initCommands() {
    commands = List.of(

        // ── 系统负载查询 ──────────────────────────────────────
        // 触发关键词: 消息中包含 "负载-"
        new CommandEntry(
            "系统负载",
            msg -> msg.contains("负载-") ? "负载-" : null,
            (groupId, arg) -> {
              GetSystemInfoLogic logic = new GetSystemInfoLogic(restTemplate);
              Object result = logic.execute(arg);
              if (result instanceof CmdExecutionResult r)
                handleCommandResult(groupId, r);
            }),

        // ── PUBG 战绩查询 ─────────────────────────────────────
        // 触发格式: PUBG-{用户名}（不区分大小写）
        new CommandEntry(
            "PUBG战绩",
            this::extractPubgCommand,
            (groupId, pubgCommand) -> {
              log.info("PUBG 查询已提交到异步队列: {} (群ID: {})", pubgCommand, groupId);
              try {
                pubgExecutor.submit(() -> executePubgCommand(groupId, pubgCommand));
              } catch (RejectedExecutionException e) {
                log.warn("PUBG 执行器已关闭，无法处理查询: {}", pubgCommand);
                sendErrorMessage(groupId, "服务正在关闭，无法处理 PUBG 查询");
              }
            }),

        // ── 语音合成 ──────────────────────────────────────────
        // 触发格式: 语音_{说话人}-{文本内容}
        new CommandEntry(
            "语音合成",
            msg -> msg.startsWith("语音_") ? msg : null,
            (groupId, msg) -> {
              String[] voiceCmd = extractVoiceCommand(msg);
              if (voiceCmd == null) {
                sendErrorMessage(groupId, "命令格式错误，请使用: 语音_{说话人}-{文本内容}");
                return;
              }
              log.info("语音合成已提交 - 说话人: [{}]，群ID: {}", voiceCmd[0], groupId);
              try {
                pubgExecutor.submit(() -> executeVoiceCommand(groupId, voiceCmd[0], voiceCmd[1]));
              } catch (RejectedExecutionException e) {
                log.warn("执行器已关闭，无法处理语音合成");
                sendErrorMessage(groupId, "服务正在关闭，无法处理语音合成");
              }
            })

    );
  }

  /**
   * 处理接收到的 WebSocket 消息
   * 当群聊消息包含命令关键词时，触发相应的处理逻辑
   * 
   * @param message WebSocket 消息的 JSON 对象
   */
  public void handleMessage(JSONObject message) {
    // 检查该任务是否启用
    if (!NapCatTaskIsOpen.isMsgLisCmdTask) {
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
      String rawMessage = message.getString("raw_message");

      // 遍历指令注册列表，找到匹配的指令并执行
      if (rawMessage != null && !rawMessage.isEmpty()) {
        for (CommandEntry entry : commands) {
          String arg = entry.extractor().apply(rawMessage);
          if (arg != null) {
            log.info("检测到指令 [{}] 在群ID: {} 中", entry.name(), groupId);
            try {
              entry.executor().accept(groupId, arg);
            } catch (Exception e) {
              log.error("执行指令 [{}] 异常 - 群ID: {}", entry.name(), groupId, e);
              sendErrorMessage(groupId, "处理命令异常: " + e.getMessage());
            }
            break;
          }
        }
      }

    } catch (Exception e) {
      log.error("处理消息异常", e);
    }
  }

  /**
   * 在专用线程中执行 PUBG 查询并发送结果
   *
   * @param groupId     群组 ID
   * @param pubgCommand PUBG 命令（如 "PUBG-username"）
   */
  private void executePubgCommand(Long groupId, String pubgCommand) {
    try {
      GetPUBGLogic logic = new GetPUBGLogic();
      Object result = logic.execute(pubgCommand);

      if (result instanceof CmdExecutionResult) {
        handleCommandResult(groupId, (CmdExecutionResult) result);
      } else if (result != null) {
        log.warn("PUBG 查询返回未知结果类型: {}", result.getClass().getName());
      }
    } catch (Exception e) {
      log.error("PUBG 异步查询异常 - 命令: {}, 群ID: {}", pubgCommand, groupId, e);
      sendErrorMessage(groupId, "PUBG 查询异常: " + e.getMessage());
    }
  }

  private String extractPubgCommand(String message) {
    if (message == null || message.isEmpty()) {
      return null;
    }

    Matcher matcher = PUBG_COMMAND_PATTERN.matcher(message);
    if (matcher.find()) {
      String username = matcher.group(1).trim();
      if (!username.isEmpty()) {
        return "PUBG-" + username;
      }
    }

    return null;
  }

  /**
   * 处理命令执行结果
   * 根据结果发送相应的回复、图片或语音
   *
   * @param groupId 群组 ID
   * @param result  命令执行结果
   */
  private void handleCommandResult(Long groupId, CmdExecutionResult result) {
    try {
      if (result.isSuccess()) {
        // 优先判断语音
        if (result.getSoundFile() != null && !result.getSoundFile().isEmpty()) {
          sendGroupSound(groupId, result.getSoundFile());
        } else if (result.getImagePath() != null && !result.getImagePath().isEmpty()) {
          sendGroupImage(groupId, result.getImagePath());
        } else {
          sendGroupMessage(groupId, result.getMessage());
        }
      } else {
        sendErrorMessage(groupId, result.getMessage());
      }
    } catch (Exception e) {
      log.error("处理执行结果异常", e);
    }
  }

  /**
   * 发送图片消息到群聊
   * 
   * @param groupId   群组 ID
   * @param imagePath 图片文件路径
   */
  private void sendGroupImage(Long groupId, String imagePath) {
    try {
      log.info("准备向群ID: {} 发送图片: {}", groupId, imagePath);

      // 构建图片消息请求体
      JSONObject requestBody = buildImageMessageRequest(groupId, imagePath);

      // 发送消息（带重试）
      boolean success = sendMessageWithRetry(groupId, requestBody, 2);

      if (success) {
        // log.info("图片消息发送成功 - 群ID: {}", groupId);
      } else {
        log.error("图片消息发送失败 - 群ID: {}", groupId);
      }

    } catch (Exception e) {
      log.error("发送图片消息异常 - 群ID: {}", groupId, e);
    }
  }

  /**
   * 在专用线程中执行语音合成并发送结果
   *
   * @param groupId 群组 ID
   * @param speaker 说话人
   * @param text    文本内容
   */
  private void executeVoiceCommand(Long groupId, String speaker, String text) {
    try {
      CreateSoundLogic logic = new CreateSoundLogic(speaker, text);
      Object result = logic.execute("语音_");
      if (result instanceof CmdExecutionResult) {
        handleCommandResult(groupId, (CmdExecutionResult) result);
      }
    } catch (Exception e) {
      log.error("语音合成异步执行异常 - 说话人: {}，群ID: {}", speaker, groupId, e);
      sendErrorMessage(groupId, "语音合成异常: " + e.getMessage());
    }
  }

  /**
   * 从消息中解析语音指令
   * 格式: 语音_{说话人}-{文本内容}
   *
   * @param message 消息内容
   * @return [speaker, text] 数组，格式不匹配时返回 null
   */
  private String[] extractVoiceCommand(String message) {
    if (message == null || !message.startsWith("语音_")) {
      return null;
    }
    String content = message.substring("语音_".length());
    int dashIndex = content.indexOf('-');
    if (dashIndex <= 0 || dashIndex >= content.length() - 1) {
      return null;
    }
    String speaker = content.substring(0, dashIndex).trim();
    String text = content.substring(dashIndex + 1).trim();
    if (speaker.isEmpty() || text.isEmpty()) {
      return null;
    }
    return new String[] { speaker, text };
  }

  /**
   * 发送语音消息到群聊
   *
   * @param groupId   群组 ID
   * @param soundFile base64:// 格式的语音数据
   */
  private void sendGroupSound(Long groupId, String soundFile) {
    try {
      log.info("准备向群ID: {} 发送语音消息", groupId);

      JSONObject requestBody = buildSoundMessageRequest(groupId, soundFile);
      boolean success = sendMessageWithRetry(groupId, requestBody, 2);

      if (!success) {
        log.error("语音消息发送失败 - 群ID: {}", groupId);
      }
    } catch (Exception e) {
      log.error("发送语音消息异常 - 群ID: {}", groupId, e);
    }
  }

  /**
   * 构建语音消息请求体
   *
   * @param groupId   群组 ID
   * @param soundFile base64:// 格式的语音数据
   * @return 请求 JSON
   */
  private JSONObject buildSoundMessageRequest(Long groupId, String soundFile) {
    JSONObject request = new JSONObject();
    request.put("group_id", groupId);

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
   * 发送文本消息到群聊
   * 
   * @param groupId     群组 ID
   * @param messageText 文本内容
   */
  private void sendGroupMessage(Long groupId, String messageText) {
    try {
      log.info("准备向群ID: {} 发送文本消息: {}", groupId, messageText);

      // 构建文本消息请求体
      JSONObject requestBody = buildTextMessageRequest(groupId, messageText);

      // 发送消息（带重试）
      boolean success = sendMessageWithRetry(groupId, requestBody, 2);

      if (success) {
        log.info("文本消息发送成功 - 群ID: {}", groupId);
      } else {
        log.error("文本消息发送失败 - 群ID: {}", groupId);
      }

    } catch (Exception e) {
      log.error("发送文本消息异常 - 群ID: {}", groupId, e);
    }
  }

  /**
   * 发送错误消息到群聊
   * 
   * @param groupId      群组 ID
   * @param errorMessage 错误消息
   */
  private void sendErrorMessage(Long groupId, String errorMessage) {
    try {
      String fullMessage = "命令执行出错: " + errorMessage;
      sendGroupMessage(groupId, fullMessage);
    } catch (Exception e) {
      log.error("发送错误消息异常", e);
    }
  }

  /**
   * 构建图片消息请求体
   * 
   * @param groupId   群组 ID
   * @param imagePath 图片文件路径
   * @return 请求 JSON
   */
  private JSONObject buildImageMessageRequest(Long groupId, String imagePath) {
    JSONObject request = new JSONObject();
    request.put("group_id", groupId);

    JSONArray messageArray = new JSONArray();
    JSONObject imageItem = new JSONObject();
    imageItem.put("type", "image");

    JSONObject dataObj = new JSONObject();
    // 使用文件路径格式发送本地文件
    dataObj.put("file", "file://" + new File(imagePath).getAbsolutePath());
    imageItem.put("data", dataObj);

    messageArray.add(imageItem);
    request.put("message", messageArray);

    return request;
  }

  /**
   * 构建文本消息请求体
   * 
   * @param groupId     群组 ID
   * @param messageText 文本内容
   * @return 请求 JSON
   */
  private JSONObject buildTextMessageRequest(Long groupId, String messageText) {
    JSONObject request = new JSONObject();
    request.put("group_id", groupId);

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
          if ("ok".equals(jsonResponse.getString("status"))) {
            return true;
          } else {
            log.error("第 {} 次发送失败 - 群ID: {}，错误: {}", i, groupId, jsonResponse.getString("message"));
          }
        } else {
          log.error("第 {} 次发送失败 - 群ID: {}，HTTP状态: {}", i, groupId, apiResponse.getStatusCode());
        }

        // 重试前等待
        if (i < retryCount) {
          long waitTime = RETRY_BASE_DELAY_MS * i;
          log.info("等待 {} 毫秒后进行第 {} 次重试...", waitTime, i + 1);
          try {
            Thread.sleep(waitTime);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("重试等待被中断");
            break;
          }
        }

      } catch (HttpServerErrorException e) {
        log.error("第 {} 次发送异常 - 群ID: {}，HTTP错误: {} {}", i, groupId, e.getRawStatusCode(), e.getStatusText());

        if (i < retryCount && (e.getRawStatusCode() >= 500)) {
          try {
            long waitTime = SERVER_ERROR_RETRY_DELAY_MS * i;
            log.info("服务器错误，等待 {} 毫秒后进行第 {} 次重试...", waitTime, i + 1);
            Thread.sleep(waitTime);
          } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("重试等待被中断");
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
}
