package LDS.Person.tasks.MsgLisCmdLogic;

import LDS.Person.util.GetSystemInfo.GetSystemInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestTemplate;

/**
 * 系统信息查询逻辑处理器
 * 负责处理 "-负载" 关键词触发的系统信息查询业务逻辑
 * 
 * 功能：
 * 1. 生成系统信息图表
 * 2. 获取生成的图片路径
 * 3. 构建返回消息
 */
@Slf4j
public class GetSystemInfoLogic implements IMsgLisCmdLogic {

  private RestTemplate restTemplate;

  /**
   * 构造函数
   * 
   * @param restTemplate Spring RestTemplate 实例
   */
  public GetSystemInfoLogic(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  /**
   * 处理系统负载查询请求
   * 
   * @param keyword 触发关键词
   * @return 处理结果，包含消息内容和图片路径
   */
  @Override
  public CmdExecutionResult execute(String keyword) {
    try {
      log.info("开始处理 '{}' 关键词，生成系统负载信息...", keyword);

      // 生成系统信息图表并获取图片路径
      String imagePath = generateSystemInfoChart();

      if (imagePath == null || imagePath.isEmpty()) {
        log.error("生成系统信息图表失败");
        return CmdExecutionResult.failure("生成系统信息图表失败，请稍后重试");
      }

      // log.info("系统信息图表生成成功: {}", imagePath);

      // 构建成功的执行结果
      CmdExecutionResult result = CmdExecutionResult.success("系统负载信息已生成");
      result.setImagePath(imagePath);

      return result;

    } catch (Exception e) {
      log.error("处理系统负载查询异常", e);
      return CmdExecutionResult.failure("处理查询异常: " + e.getMessage());
    }
  }

  /**
   * 生成系统信息图表
   * 调用 GetSystemInfo 工具类生成图表
   * 
   * @return 图片文件的完整路径，失败返回 null
   */
  private String generateSystemInfoChart() {
    try {
      // 调用 GetSystemInfo 的静态方法生成系统信息图表
      String imagePath = GetSystemInfo.generateSystemInfoChart("img");
      return imagePath;
    } catch (Exception e) {
      log.error("调用 GetSystemInfo 生成图表异常", e);
      return null;
    }
  }

  /**
   * 名称
   */
  @Override
  public String getName() {
    return "GetSystemInfoLogic";
  }

  /**
   * 描述
   */
  @Override
  public String getDescription() {
    return "系统负载信息查询逻辑";
  }

  /**
   * 命令执行结果数据类
   */
  public static class CmdExecutionResult {
    private boolean success;
    private String message;
    private String imagePath;

    public CmdExecutionResult(boolean success, String message) {
      this.success = success;
      this.message = message;
    }

    /**
     * 创建成功结果
     */
    public static CmdExecutionResult success(String message) {
      return new CmdExecutionResult(true, message);
    }

    /**
     * 创建失败结果
     */
    public static CmdExecutionResult failure(String message) {
      return new CmdExecutionResult(false, message);
    }

    // Getters and Setters
    public boolean isSuccess() {
      return success;
    }

    public void setSuccess(boolean success) {
      this.success = success;
    }

    public String getMessage() {
      return message;
    }

    public void setMessage(String message) {
      this.message = message;
    }

    public String getImagePath() {
      return imagePath;
    }

    public void setImagePath(String imagePath) {
      this.imagePath = imagePath;
    }
  }
}
