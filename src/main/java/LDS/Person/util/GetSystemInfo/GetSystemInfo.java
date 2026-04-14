package LDS.Person.util.GetSystemInfo;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

/**
 * 系统监控应用
 * 包含主函数、数据显示和图表生成功能
 */
public class GetSystemInfo {

  private static final int CHART_WIDTH = 1000;
  private static final int CHART_HEIGHT = 900;
  private static final int VALUE_START_X = 200; // 值部分的起始位置
  private static final Font TITLE_FONT = new Font("微软雅黑", Font.BOLD, 28);
  private static final Font LABEL_FONT = new Font("微软雅黑", Font.PLAIN, 13);
  private static final Font VALUE_FONT = new Font("微软雅黑", Font.PLAIN, 12);
  private static final Color BG_COLOR = Color.WHITE;
  private static final Color TEXT_COLOR = new Color(51, 51, 51);

  public static void main(String[] args) {

    try {
      // 步骤1：收集系统信息
      System.out.println("正在收集系统信息...");
      GetSystemInfoDataCollector systemInfo = new GetSystemInfoDataCollector().collect();
      // System.out.println("✓ 系统信息收集完成\n");

      // // 步骤2：显示收集到的信息
      // System.out.println("[步骤2] 系统信息摘要:");
      displaySummary(systemInfo);
      // System.out.println();

      // 步骤3：生成图表
      // System.out.println("[步骤3] 正在生成图表...");
      generateAndSaveChart(systemInfo, "img");
      System.out.println();

      // 步骤4：完成
      // System.out.println("========================================");
      // // System.out.println("✓ 所有操作完成！");
      // System.out.println(" 图表文件已保存到 'img' 文件夹");
      // System.out.println("========================================");

    } catch (Exception e) {
      System.err.println("✗ 发生错误: " + e.getMessage());
      e.printStackTrace();
    }
  }

  /**
   * 生成系统信息图表并返回图片路径（供外部调用）
   * 
   * @param imgFolder 输出文件夹
   * @return 生成的图片完整路径，失败返回 null
   */
  public static String generateSystemInfoChart(String imgFolder) {
    try {
      GetSystemInfoDataCollector systemInfo = new GetSystemInfoDataCollector().collect();
      return generateAndSaveChart(systemInfo, imgFolder);
    } catch (Exception e) {
      System.err.println("✗ 生成系统信息图表失败: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  /**
   * 生成系统信息图表并保存为图片
   * 
   * @param data      系统信息数据收集器
   * @param imgFolder 输出文件夹
   * @return 生成的图片完整路径
   */
  private static String generateAndSaveChart(GetSystemInfoDataCollector data, String imgFolder) {
    File imgDir = new File(imgFolder);
    if (!imgDir.exists()) {
      imgDir.mkdirs();
    }

    try {
      BufferedImage image = createChartImage(data);
      String imagePath = imgFolder + File.separator + "system_info.png";
      ImageIO.write(image, "png", new File(imagePath));
      System.out.println("✓ 系统信息图表已生成: " + imagePath);
      return new File(imagePath).getAbsolutePath();
    } catch (IOException e) {
      System.err.println("✗ 生成图表失败: " + e.getMessage());
      e.printStackTrace();
      return null;
    }
  }

  /**
   * 创建图表图像
   */
  private static BufferedImage createChartImage(GetSystemInfoDataCollector data) {
    BufferedImage image = new BufferedImage(CHART_WIDTH, CHART_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g2d = image.createGraphics();
    g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

    // 背景
    g2d.setColor(BG_COLOR);
    g2d.fillRect(0, 0, CHART_WIDTH, CHART_HEIGHT);

    // 标题
    g2d.setColor(TEXT_COLOR);
    g2d.setFont(TITLE_FONT);
    FontMetrics fm = g2d.getFontMetrics();
    String title = "/负载  数据表";
    int titleX = (CHART_WIDTH - fm.stringWidth(title)) / 2;
    g2d.drawString(title, titleX, 50);

    // 分隔线
    g2d.setColor(new Color(200, 200, 200));
    g2d.setStroke(new BasicStroke(1));
    g2d.drawLine(30, 70, CHART_WIDTH - 30, 70);

    // 绘制内容
    int startY = 100;
    int lineHeight = 32;
    int leftX = 50;
    int rightX = 480;
    int currentY = startY;

    // === 操作系统 ===
    currentY += 10;
    currentY = drawSection(g2d, "▶ 操作系统", new Color(52, 152, 219), leftX, currentY, lineHeight);
    g2d.setColor(TEXT_COLOR);
    g2d.setFont(VALUE_FONT);
    g2d.drawString(data.getOsName(), leftX + 20, currentY);
    currentY += lineHeight;

    // === 性能指标 ===
    currentY = drawSection(g2d, "▶ 实时性能指标", new Color(231, 76, 60), leftX, currentY, lineHeight);
    currentY = drawMetricLine(g2d, "CPU 使用率:",
        String.format("%.1f%% (%s)", data.getCpuUsage(), data.getProcessorName()) + " " + drawBar(data.getCpuUsage()),
        leftX, rightX, currentY,
        lineHeight);
    long totalGB = data.getTotalMemory() / (1024L * 1024L * 1024L);
    long usedGB = data.getUsedMemory() / (1024L * 1024L * 1024L);
    currentY = drawMetricLine(g2d, "内存使用率:",
        String.format("%.1f%% (%d/%d GB)", data.getMemoryUsagePercent(), usedGB, totalGB) + " "
            + drawBar(data.getMemoryUsagePercent()),
        leftX, rightX - 100, currentY, lineHeight);

    // === 处理器 ===
    currentY += 10;
    currentY = drawSection(g2d, "▶ 处理器信息", new Color(46, 204, 113), leftX, currentY, lineHeight);

    currentY = drawMetricLine(g2d, "物理核心数:", String.valueOf(data.getPhysicalCoreCount()), leftX, rightX, currentY,
        lineHeight);
    currentY = drawMetricLine(g2d, "逻辑核心数:", String.valueOf(data.getLogicalCoreCount()), leftX, rightX, currentY,
        lineHeight);
    currentY = drawMetricLine(g2d, "当前频率:", String.format("%.2f GHz", data.getCurrentFreq()), leftX, rightX, currentY,
        lineHeight);
    currentY = drawMetricLine(g2d, "最大频率:", String.format("%.2f GHz", data.getMaxFreq()), leftX, rightX, currentY,
        lineHeight);

    // === 电池 ===
    currentY += 10;
    currentY = drawSection(g2d, "▶ 电池信息", new Color(155, 89, 182), leftX, currentY, lineHeight);
    currentY = drawMetricLine(g2d, "电池名称:", data.getBatteryName(), leftX, rightX, currentY, lineHeight);
    currentY = drawMetricLine(g2d, "制造商:", data.getManufacturer(), leftX, rightX, currentY, lineHeight);
    currentY = drawMetricLine(g2d, "化学类型:", data.getChemistry(), leftX, rightX, currentY, lineHeight);
    currentY = drawMetricLine(g2d, "电压:", String.format("%.2f V", data.getVoltage()), leftX, rightX, currentY,
        lineHeight);
    currentY = drawMetricLine(g2d, "充电状态:", data.getChargeStatus(), leftX, rightX, currentY, lineHeight);
    currentY = drawMetricLine(g2d, "外电源:", data.isPowerOnLine() ? "已连接" : "未连接", leftX, rightX, currentY, lineHeight);

    // === 项目数据 ===
    currentY += 10;
    currentY = drawSection(g2d, "▶ 项目数据", new Color(26, 188, 156), leftX, currentY, lineHeight);
    currentY = drawMetricLine(g2d, "@消息监听:", data.isIsMsgLisATTask() ? "启用" : "禁用", leftX, rightX, currentY,
        lineHeight);
    currentY = drawMetricLine(g2d, "随机模拟任务:", data.isIsMsgSchHumanTask() ? "启用" : "禁用", leftX, rightX, currentY,
        lineHeight);
    currentY = drawMetricLine(g2d, "指令监听任务:", data.isIsMsgLisCmdTask() ? "启用" : "禁用", leftX, rightX, currentY,
        lineHeight);
    currentY = drawMetricLine(g2d, "最近活动群聊:", data.getLastActiveGroupName(), leftX, rightX, currentY,
        lineHeight);

    // 底部分隔线
    g2d.setColor(new Color(200, 200, 200));
    g2d.drawLine(30, currentY + 10, CHART_WIDTH - 30, currentY + 10);

    // 时间戳
    g2d.setColor(new Color(150, 150, 150));
    g2d.setFont(new Font("微软雅黑", Font.PLAIN, 10));
    String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
    g2d.drawString("生成时间: " + timestamp, leftX, currentY + 35);

    g2d.dispose();
    return image;
  }

  /**
   * 绘制小节标题
   */
  private static int drawSection(Graphics2D g2d, String title, Color color, int x, int y, int lineHeight) {
    g2d.setColor(color);
    g2d.setFont(new Font("微软雅黑", Font.BOLD, 16));
    g2d.drawString(title, x, y);
    return y + lineHeight + 5;
  }

  /**
   * 绘制指标行
   */
  private static int drawMetricLine(Graphics2D g2d, String label, String value, int leftX, int rightX, int y,
      int lineHeight) {
    g2d.setColor(TEXT_COLOR);
    g2d.setFont(LABEL_FONT);
    g2d.drawString(label, leftX + 20, y);
    g2d.setFont(VALUE_FONT);
    g2d.drawString(value, leftX + VALUE_START_X, y);
    return y + lineHeight;
  }

  /**
   * 绘制进度条
   */
  private static String drawBar(double percentage) {
    if (percentage < 0)
      percentage = 0;
    if (percentage > 100)
      percentage = 100;

    int total = 20;
    int filled = (int) (total * percentage / 100.0);

    StringBuilder bar = new StringBuilder("[");
    for (int i = 0; i < filled; i++) {
      bar.append("█");
    }
    for (int i = filled; i < total; i++) {
      bar.append("░");
    }
    bar.append("]");

    return bar.toString();
  }

  /**
   * 显示系统信息摘要
   */
  private static void displaySummary(GetSystemInfoDataCollector data) {
    System.out.println("  操作系统: " + data.getOsName());
    System.out.println("  处理器: " + data.getProcessorName());
    System.out.println("    - 物理核心: " + data.getPhysicalCoreCount());
    System.out.println("    - 逻辑核心: " + data.getLogicalCoreCount());
    System.out.println("    - 当前频率: " + String.format("%.2f GHz", data.getCurrentFreq()));
    System.out.println("    - 最大频率: " + String.format("%.2f GHz", data.getMaxFreq()));

    System.out.println("  性能指标:");
    System.out.println("    - CPU 使用率: " + String.format("%.1f%%", data.getCpuUsage()));
    System.out.println("    - 内存使用率: " + String.format("%.1f%% (%d/%d GB)",
        data.getMemoryUsagePercent(),
        data.getUsedMemory() / (1024L * 1024L * 1024L),
        data.getTotalMemory() / (1024L * 1024L * 1024L)));
    System.out.println("    - 磁盘使用率: " + String.format("%.1f%% (%s)",
        data.getDisk0UsagePercent(), data.getDisk0Name()));

    System.out.println("  电池信息: " + data.getBatteryName() + " (" + data.getManufacturer() + ")");
    System.out.println("    - 剩余: " + String.format("%.2f%%", data.getRemainingCapacityPercent() * 100));
    System.out.println("    - 电压: " + String.format("%.2f V", data.getVoltage()));
    System.out.println("    - 状态: " + data.getChargeStatus() + " (" + (data.isPowerOnLine() ? "外电源已连接" : "使用电池") + ")");

    System.out.println("  项目数据:");
    System.out.println("    - @消息监听: " + (data.isIsMsgLisATTask() ? "启用" : "禁用"));
    System.out.println("    - 模拟人类任务: " + (data.isIsMsgSchHumanTask() ? "启用" : "禁用"));
    System.out.println("    - 指令监听任务: " + (data.isIsMsgLisCmdTask() ? "启用" : "禁用"));
    System.out.println("    - 最近活动群聊: " + data.getLastActiveGroupName());
  }
}
