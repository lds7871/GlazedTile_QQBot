package LDS.Person.tasks.MsgLisCmdLogic;

import LDS.Person.tasks.MsgLisCmdLogic.GetSystemInfoLogic.CmdExecutionResult;
import LDS.Person.util.CreateSound.CreateSound;
import lombok.extern.slf4j.Slf4j;

/**
 * 语音合成指令逻辑处理器
 * 处理关键词格式: 语音_{说话人}-{文本内容}
 * 示例: 语音_李白-白日依山尽，黄河入海流
 */
@Slf4j
public class CreateSoundLogic implements IMsgLisCmdLogic {

  private final String speaker;
  private final String text;

  /**
   * @param speaker 说话人名称
   * @param text    要合成的文本内容
   */
  public CreateSoundLogic(String speaker, String text) {
    this.speaker = speaker;
    this.text = text;
  }

  @Override
  public CmdExecutionResult execute(String keyword) {
    try {
      log.info("开始处理语音合成指令 - 说话人: [{}]，文本: {}", speaker, text);

      String soundBase64 = CreateSound.generateSound(text, speaker);

      if (soundBase64 == null) {
        return CmdExecutionResult.failure("语音合成失败，请稍后重试");
      }

      CmdExecutionResult result = CmdExecutionResult.success("语音合成成功");
      result.setSoundFile(soundBase64);
      return result;

    } catch (Exception e) {
      log.error("语音合成指令处理异常", e);
      return CmdExecutionResult.failure("语音合成异常: " + e.getMessage());
    }
  }

  @Override
  public String getName() {
    return "CreateSoundLogic";
  }

  @Override
  public String getDescription() {
    return "语音合成逻辑处理器，调用本地 TTS 引擎合成语音并发送至群聊";
  }
}
