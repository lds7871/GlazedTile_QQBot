package LDS.Person.util.CreateSound;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Base64;

/**
 * TTS 语音合成工具类
 * 调用 E:\SAVE\LLM_To_TTS\run.cmd 生成语音，并返回 base64 编码
 */
@Slf4j
public class CreateSound {

  private static final String TTS_DIR = "E:\\SAVE\\LLM_To_TTS";
  private static final String OUTPUT_WAV = TTS_DIR + "\\audio\\output.wav";

  /**
   * 执行 TTS 合成，返回 base64:// 格式的音频数据
   *
   * @param text    要合成的文本（自动格式化为单行）
   * @param speaker 说话人名称
   * @return "base64://..." 格式字符串，失败返回 null
   */
  public static String generateSound(String text, String speaker) {
    try {
      // 格式化文本：合并为单行，去除换行、null 字符和首尾空白
      String formattedText = text.replaceAll("[\\r\\n\u0000]+", "").trim();

      // 基础安全过滤：防止命令注入，同时移除 null 字符
      String safeText = formattedText.replace("\"", "'").replace("\0", "");
      String safeSpeaker = speaker.replaceAll("[\"&|<>^;\\r\\n\u0000]", "").trim();

      if (safeText.isEmpty()) {
        log.warn("格式化后文本为空，取消 TTS 合成");
        return null;
      }
      if (safeSpeaker.isEmpty()) {
        log.warn("说话人为空，取消 TTS 合成");
        return null;
      }

      log.info("开始 TTS 合成 - 说话人: [{}]，文本长度: {} 字符", safeSpeaker, safeText.length());

      // 构建 cmd 命令：cmd.exe /c .\run.cmd --text "..." --speaker "..."
      ProcessBuilder pb = new ProcessBuilder(
          "cmd.exe", "/c",
          ".\\run.cmd",
          "--text", safeText,
          "--speaker", safeSpeaker);
      pb.directory(new File(TTS_DIR));
      pb.redirectErrorStream(true);

      Process process = pb.start();

      // 实时读取并记录进程输出
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(process.getInputStream(), "GBK"))) {
        String line;
        while ((line = reader.readLine()) != null) {
          log.debug("[TTS] {}", line);
        }
      }

      int exitCode = process.waitFor();
      if (exitCode != 0) {
        log.error("TTS 进程异常退出，退出码: {}", exitCode);
        return null;
      }

      // 读取输出 wav 并转 base64
      File wavFile = new File(OUTPUT_WAV);
      if (!wavFile.exists()) {
        log.error("TTS 输出文件不存在: {}", OUTPUT_WAV);
        return null;
      }

      byte[] audioBytes;
      try (FileInputStream fis = new FileInputStream(wavFile)) {
        audioBytes = fis.readAllBytes();
      }

      String base64Audio = Base64.getEncoder().encodeToString(audioBytes);
      log.info("TTS 合成完成，音频大小: {} bytes，base64 长度: {}", audioBytes.length, base64Audio.length());

      return "base64://" + base64Audio;

    } catch (Exception e) {
      log.error("TTS 合成异常", e);
      return null;
    }
  }
}
