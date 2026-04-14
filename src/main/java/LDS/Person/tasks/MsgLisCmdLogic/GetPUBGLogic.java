package LDS.Person.tasks.MsgLisCmdLogic;

import LDS.Person.tasks.MsgLisCmdLogic.GetSystemInfoLogic.CmdExecutionResult;
import LDS.Person.util.PubgGet.PubgUserInfoWithSelenium;
import lombok.extern.slf4j.Slf4j;

/**
 * PUBG 战绩查询逻辑处理器
 * 处理关键词格式: PUBG-{username}
 */
@Slf4j
public class GetPUBGLogic implements IMsgLisCmdLogic {

  public GetPUBGLogic() {
  }

  @Override
  public CmdExecutionResult execute(String keyword) {
    try {
      String username = extractUsername(keyword);
      if (username == null || username.isEmpty()) {
        return CmdExecutionResult.failure("命令格式错误，请使用: PUBG-{username}");
      }

      log.info("开始处理 PUBG 查询，用户名: {}", username);
      String imagePath = PubgUserInfoWithSelenium.fetchUserInfoWithSelenium(username);

      if (imagePath == null || imagePath.isEmpty()) {
        return CmdExecutionResult.failure("PUBG战绩图片生成失败，请稍后重试");
      }

      CmdExecutionResult result = CmdExecutionResult.success("PUBG战绩已生成: " + username);
      result.setImagePath(imagePath);
      return result;
    } catch (Exception e) {
      log.error("处理 PUBG 查询异常", e);
      return CmdExecutionResult.failure("处理 PUBG 查询异常: " + e.getMessage());
    }
  }

  private String extractUsername(String keyword) {
    if (keyword == null) {
      return null;
    }

    String prefix = "PUBG-";
    if (!keyword.startsWith(prefix)) {
      return null;
    }

    return keyword.substring(prefix.length()).trim();
  }

  @Override
  public String getName() {
    return "GetPUBGLogic";
  }

  @Override
  public String getDescription() {
    return "PUBG 战绩查询逻辑";
  }
}
