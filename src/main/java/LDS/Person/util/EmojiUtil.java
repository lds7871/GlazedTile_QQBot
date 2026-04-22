package LDS.Person.util;

import com.vdurmont.emoji.EmojiParser;

/**
 * Emoji工具类
 * 用于处理WebSocket中的emoji表情
 */
public class EmojiUtil {

  /**
   * 将emoji表情符转换为Unicode格式
   * 
   * @param text 包含emoji的文本
   * @return 转换后的Unicode文本
   */
  public static String parseToUnicode(String text) {
    if (text == null) {
      return null;
    }
    return EmojiParser.parseToUnicode(text);
  }

  /**
   * 将Unicode格式转换为emoji表情符
   * 
   * @param text Unicode格式的文本
   * @return 包含emoji的文本
   */
  public static String parseToHtmlDecimal(String text) {
    if (text == null) {
      return null;
    }
    return EmojiParser.parseToHtmlDecimal(text);
  }

  /**
   * 检测文本是否包含emoji
   * 
   * @param text 待检测的文本
   * @return true if contains emoji, false otherwise
   */
  public static boolean containsEmoji(String text) {
    if (text == null) {
      return false;
    }
    return !EmojiParser.removeAllEmojis(text).equals(text);
  }

  /**
   * 移除文本中的所有emoji
   * 
   * @param text 待处理的文本
   * @return 移除emoji后的文本
   */
  public static String removeEmojis(String text) {
    if (text == null) {
      return null;
    }
    return EmojiParser.removeAllEmojis(text);
  }

  /**
   * 提取文本中的所有emoji
   * 
   * @param text 待处理的文本
   * @return emoji列表
   */
  public static java.util.List<String> extractEmojis(String text) {
    if (text == null) {
      return new java.util.ArrayList<>();
    }
    return EmojiParser.extractEmojis(text);
  }
}
