package LDS.Person.util.PubgGet;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.By;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 使用Selenium处理op.gg的PUBG用户信息获取
 * 能够处理AWS WAF验证和JavaScript渲染
 */
public class PubgUserInfoWithSelenium {

    private static final String BASE_URL = "https://op.gg/zh-cn/pubg/user/";
    private static final int CHART_WIDTH = 1600;
    private static final int CHART_HEIGHT = 3000; // 调大初始高度以免被截断
    private static final Font TITLE_FONT = new Font("微软雅黑", Font.BOLD, 36);
    private static final Font SECTION_FONT = new Font("微软雅黑", Font.BOLD, 22);
    private static final Font LABEL_FONT = new Font("微软雅黑", Font.PLAIN, 16);
    private static final Font VALUE_FONT = new Font("微软雅黑", Font.PLAIN, 15);
    private static final Font CARD_TITLE_FONT = new Font("微软雅黑", Font.BOLD, 14);
    private static final Font CARD_VALUE_FONT = new Font("微软雅黑", Font.PLAIN, 13);
    private static final Color BG_COLOR = new Color(248, 249, 250);
    private static final Color TEXT_COLOR = new Color(33, 37, 41);
    private static final Color CARD_BG_COLOR = Color.WHITE;

    // 存储提取的数据
    private static class PubgUserStats {
        String username;
        String rankingAvg;
        List<String> rankingData = new ArrayList<>();
        Map<String, String> avgStats = new HashMap<>();
        List<String[]> matches = new ArrayList<>();
    }

    /**
     * 提取PUBG用户信息
     */
    private static PubgUserStats extractUserStats(WebDriver driver, String username) {
        PubgUserStats stats = new PubgUserStats();
        stats.username = username;

        try {
            // 查找class="recent-matches__card"中的第一张卡片（平均排名）
            List<WebElement> recentCards = driver.findElements(By.className("recent-matches__card"));
            if (!recentCards.isEmpty()) {
                WebElement firstCard = recentCards.get(0);
                String cardTitle = firstCard.findElement(By.className("recent-matches__title")).getText();

                if (cardTitle.contains("平均排名")) {
                    WebElement content = firstCard.findElement(By.className("recent-matches__content"));
                    String contentText = content.getText();
                    String[] lines = contentText.split("\n");

                    if (lines.length > 0) {
                        stats.rankingAvg = lines[0];
                    }

                    for (int i = 1; i < lines.length; i++) {
                        String line = lines[i].trim();
                        // 包含"W"或者数字都保留
                        if (line.matches("\\d+") || line.equalsIgnoreCase("W")) {
                            stats.rankingData.add(line);
                        }
                    }
                }
            }

            // 查找平均统计信息
            if (recentCards.size() > 1) {
                WebElement secondCard = recentCards.get(1);
                String cardTitle = secondCard.findElement(By.className("recent-matches__title")).getText();

                if (cardTitle.contains("统计")) {
                    WebElement content = secondCard.findElement(By.className("recent-matches__content"));
                    String contentText = content.getText();
                    String[] lines = contentText.split("\n");

                    for (int i = 0; i < lines.length - 1; i += 2) {
                        String label = lines[i].trim();
                        String value = lines[i + 1].trim();
                        if (!label.isEmpty() && !value.isEmpty()) {
                            stats.avgStats.put(label, value);
                        }
                    }
                }
            }

            // 提取比赛列表
            List<WebElement> matchItems = driver
                    .findElements(By.cssSelector("[data-selector='total-played-game-item']"));

            for (WebElement item : matchItems) {
                try {
                    WebElement summary = item.findElement(By.className("matches-item__summary"));
                    String summaryText = summary.getText();
                    String[] lines = summaryText.split("\n");

                    String mode = "";
                    String timeAgo = "";
                    String duration = "";
                    String rank = "";
                    String kills = "";
                    String damage = "";
                    String distance = "";

                    for (String line : lines) {
                        line = line.trim();
                        if (line.contains("排")) {
                            mode = line;
                        } else if (line.contains("小时前") || line.contains("天前") || line.contains("分钟前")) {
                            timeAgo = line;
                        } else if (line.matches("\\d{1,2}:\\d{2}")) {
                            duration = line;
                        } else if (line.startsWith("#")) {
                            rank = line;
                        } else if (line.matches("\\d+") && !kills.isEmpty() && damage.isEmpty()) {
                            damage = line;
                        } else if (line.matches("\\d+") && kills.isEmpty() && !line.equals(mode)) {
                            kills = line;
                        } else if (line.contains("km")) {
                            distance = line;
                        }
                    }

                    if (!mode.isEmpty() && !duration.isEmpty()) {
                        stats.matches.add(new String[] { mode, timeAgo, duration, rank, kills, damage, distance });
                    }
                } catch (Exception e) {
                    continue;
                }
            }
        } catch (Exception e) {
            System.err.println("提取数据异常: " + e.getMessage());
        }

        return stats;
    }

    /**
     * 页面打开后每秒轮询查找 renewBtn，找到后点击并等待页面刷新。
     */
    private static void pollAndClickRenewButton(WebDriver driver, int maxPollingSeconds) {
        System.out.println("开始轮询查找 renewBtn 按钮...");

        for (int i = 0; i < maxPollingSeconds; i++) {
            try {
                List<WebElement> renewButtons = driver.findElements(By.id("renewBtn"));
                if (!renewButtons.isEmpty()) {
                    WebElement renewBtn = renewButtons.get(0);
                    if (renewBtn.isDisplayed() && renewBtn.isEnabled()) {
                        renewBtn.click();
                        System.out.println("已点击 renewBtn，等待页面刷新...");
                        Thread.sleep(2500);
                        return;
                    }
                }

                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.out.println("轮询 renewBtn 被中断，继续后续流程");
                return;
            } catch (Exception e) {
                System.out.println("轮询 renewBtn 时出现异常，继续轮询: " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }

        System.out.println("未检测到 renewBtn，继续后续流程");
    }

    /**
     * 生成PUBG用户信息图表
     */
    private static String generateAndSavePubgChart(PubgUserStats stats, String imgFolder) {
        File imgDir = new File(imgFolder);
        if (!imgDir.exists()) {
            imgDir.mkdirs();
        }

        try {
            BufferedImage image = createPubgChartImage(stats);
            String safeUsername = sanitizeFileName(stats.username);
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String imagePath = imgFolder + File.separator + "pubg" + ".png";
            ImageIO.write(image, "png", new File(imagePath));
            System.out.println("✓ PUBG用户信息图表已生成: " + imagePath);
            return imagePath;
        } catch (IOException e) {
            System.err.println("✗ 生成图表失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private static String sanitizeFileName(String input) {
        if (input == null || input.trim().isEmpty()) {
            return "unknown";
        }
        return input.replaceAll("[\\\\/:*?\"<>|\\s]+", "_");
    }

    /**
     * 创建PUBG图表图像 - 卡片设计版本
     */
    private static BufferedImage createPubgChartImage(PubgUserStats stats) {
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
        String title = "PUBG 玩家战绩 - " + stats.username;
        int titleX = (CHART_WIDTH - fm.stringWidth(title)) / 2;
        g2d.drawString(title, titleX, 50);

        // 分隔线
        g2d.setColor(new Color(220, 220, 220));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(40, 70, CHART_WIDTH - 40, 70);

        int currentY = 110;

        // === 最近20场游戏平均排名 ===
        currentY = drawSection(g2d, "▶ 最近20场游戏平均排名", new Color(41, 128, 185), 50, currentY, 35);
        g2d.setColor(TEXT_COLOR);
        g2d.setFont(VALUE_FONT);
        g2d.drawString("平均排名: " + (stats.rankingAvg != null ? stats.rankingAvg : "N/A"), 70, currentY);
        currentY += 40;

        // 排名数据 - 美化的徽章标签形式展示
        if (!stats.rankingData.isEmpty()) {
            g2d.drawString("历史排名: ", 70, currentY);
            int startX = 160;
            int rankY = currentY - 18;
            int marginX = 8;

            for (String rank : stats.rankingData) {
                int rankWidth = g2d.getFontMetrics().stringWidth(rank) + 20; // x-padding: 10 + 10

                // 根据排名决定背景色
                Color tagBgColor = new Color(223, 230, 233); // 默认灰色
                Color tagTextColor = new Color(52, 73, 94);

                String cleanRank = rank.replace("#", "").trim();
                if (cleanRank.equalsIgnoreCase("w") || cleanRank.equals("1")) {
                    tagBgColor = new Color(255, 193, 7); // 金色
                    tagTextColor = Color.WHITE;
                } else {
                    try {
                        int r = Integer.parseInt(cleanRank);
                        if (r >= 2 && r <= 5) {
                            tagBgColor = new Color(52, 152, 219); // 蓝色
                            tagTextColor = Color.WHITE;
                        } else if (r >= 6 && r <= 10) {
                            tagBgColor = new Color(46, 204, 113); // 绿色
                            tagTextColor = Color.WHITE;
                        }
                    } catch (NumberFormatException e) {
                        // 忽略解析错误，保持灰色默认
                    }
                }

                // 绘制圆角标签背景
                g2d.setColor(tagBgColor);
                g2d.fillRoundRect(startX, rankY, rankWidth, 26, 12, 12);

                // 绘制排名文本
                g2d.setColor(tagTextColor);
                g2d.drawString(rank, startX + 10, currentY);

                startX += rankWidth + marginX;

                // 换行逻辑
                if (startX > CHART_WIDTH - 80) {
                    startX = 160;
                    currentY += 36;
                    rankY += 36;
                }
            }
            currentY += 15;
        }

        currentY += 25;

        // === 最近20场游戏平均统计 ===
        currentY = drawSection(g2d, "▶ 最近20场游戏平均统计", new Color(231, 76, 60), 50, currentY, 35);
        currentY = drawAvgStatsCard(g2d, stats.avgStats, 50, currentY, CHART_WIDTH - 100, 110);
        currentY += 30;

        // === 最近比赛记录 - 卡片模式 ===
        currentY = drawSection(g2d, "▶ 最近比赛记录", new Color(46, 204, 113), 50, currentY, 35);

        // 卡片参数
        int cardsPerRow = 4;
        int cardWidth = (CHART_WIDTH - 120) / cardsPerRow;
        int cardHeight = 145;
        int cardPadding = 15;
        int spacing = (CHART_WIDTH - 100 - cardWidth * cardsPerRow) / (cardsPerRow - 1);

        currentY += 20;
        int cardX = 50;
        int cardY = currentY;
        int cardCount = 0;

        for (String[] match : stats.matches) {
            if (cardCount >= 20)
                break; // 最多显示20场

            // 计算卡片位置
            int col = cardCount % cardsPerRow;
            int row = cardCount / cardsPerRow;

            int x = cardX + col * (cardWidth + spacing);
            int y = cardY + row * (cardHeight + 25);

            // 绘制卡片
            drawMatchCard(g2d, x, y, cardWidth, cardHeight, match, cardPadding);

            cardCount++;
        }

        int rows = (cardCount + cardsPerRow - 1) / cardsPerRow;
        if (rows > 0) {
            currentY = cardY + rows * (cardHeight + 25);
        }
        currentY += 20;

        // === 最近比赛走势折线图 ===
        if (!stats.matches.isEmpty()) {
            currentY = drawSection(g2d, "▶ 最近比赛走势 (伤害、击杀与排名)", new Color(155, 89, 182), 50, currentY, 35);
            currentY = drawTrendChart(g2d, stats.matches, 50, currentY, CHART_WIDTH - 100, 280);
        }

        currentY += 20;

        // 底部分隔线
        g2d.setColor(new Color(220, 220, 220));
        g2d.drawLine(40, currentY, CHART_WIDTH - 40, currentY);

        currentY += 30;

        // 时间戳
        g2d.setColor(new Color(150, 150, 150));
        g2d.setFont(new Font("微软雅黑", Font.PLAIN, 11));
        String timestamp = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new java.util.Date());
        g2d.drawString("生成时间: " + timestamp, 50, currentY);

        currentY += 30; // 底部留白

        g2d.dispose();

        // 截取实际生成的画面内容（去除下方空白）
        return image.getSubimage(0, 0, CHART_WIDTH, Math.min(currentY, CHART_HEIGHT));
    }

    /**
     * 绘制比赛卡片
     */
    private static void drawMatchCard(Graphics2D g2d, int x, int y, int width, int height, String[] match,
            int padding) {
        // 增加阴影效果
        g2d.setColor(new Color(0, 0, 0, 15));
        g2d.fillRoundRect(x + 4, y + 4, width, height, 12, 12);

        // 卡片背景
        g2d.setColor(CARD_BG_COLOR);
        g2d.fillRoundRect(x, y, width, height, 12, 12);

        // 卡片边框
        g2d.setColor(new Color(220, 224, 228));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(x, y, width, height, 12, 12);

        // 竖条装饰 - 根据排名颜色变化
        Color rankColor = new Color(223, 230, 233); // 默认灰色
        Color rankTextColor = new Color(149, 165, 166); // 默认偏灰字体

        String cleanRankMatch = match[3].replace("#", "").trim();
        // 比赛列表截取真实排名，去掉" / 总队伍数"的部分（比如将 "1/28" 截取为 "1"）
        if (cleanRankMatch.contains("/")) {
            cleanRankMatch = cleanRankMatch.split("/")[0].trim();
        }

        if (cleanRankMatch.equalsIgnoreCase("w") || cleanRankMatch.equals("1")) {
            rankColor = new Color(255, 193, 7); // 金色
            rankTextColor = new Color(230, 173, 6);
        } else {
            try {
                int r = Integer.parseInt(cleanRankMatch);
                if (r >= 2 && r <= 5) {
                    rankColor = new Color(52, 152, 219); // 蓝色
                    rankTextColor = new Color(41, 128, 185);
                } else if (r >= 6 && r <= 10) {
                    rankColor = new Color(46, 204, 113); // 绿色
                    rankTextColor = new Color(39, 174, 96);
                }
            } catch (NumberFormatException e) {
            }
        }

        g2d.setColor(rankColor);
        // 使用Shape进行裁剪绘制圆角边缘，或者安全绘制在方框内
        g2d.fillRoundRect(x, y, 8, height, 12, 12);
        g2d.fillRect(x + 4, y, 4, height); // 直切右边

        int textX = x + padding + 10;
        int textY = y + padding + 15;
        int lineHeight = 22;

        // 模式和时间
        g2d.setColor(new Color(44, 62, 80));
        g2d.setFont(CARD_TITLE_FONT);
        g2d.drawString(match[0] + " " + match[1], textX, textY);

        // 分割线
        textY += 6;
        g2d.setColor(new Color(236, 240, 241));
        g2d.drawLine(textX, textY, x + width - padding, textY);
        textY += 16;

        g2d.setColor(new Color(52, 73, 94));
        // 对局时间
        g2d.setFont(CARD_VALUE_FONT);
        g2d.drawString("时长: " + match[2], textX, textY);

        // 排名
        textY += lineHeight;
        g2d.setColor(rankTextColor); // 排名字体颜色也对应修改
        g2d.drawString("排名: " + match[3], textX, textY);

        // 击杀
        textY += lineHeight;
        g2d.setColor(new Color(52, 73, 94)); // 还原默认文本色
        g2d.drawString("击杀: " + match[4] + "   |   伤害: " + match[5], textX, textY);

        // 移动距离
        textY += lineHeight;
        g2d.drawString("距离: " + match[6], textX, textY);
    }

    /**
     * 绘制小节标题
     */
    private static int drawSection(Graphics2D g2d, String title, Color color, int x, int y, int lineHeight) {
        // 小装饰块
        g2d.setColor(color);
        g2d.fillRoundRect(x, y - 18, 6, 22, 6, 6);

        g2d.setColor(new Color(44, 62, 80));
        g2d.setFont(SECTION_FONT);
        g2d.drawString(title.replace("▶ ", ""), x + 18, y);

        // 标题下的柔和着色线
        g2d.setColor(new Color(236, 240, 241));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawLine(x, y + 12, CHART_WIDTH - 60, y + 12);

        return y + lineHeight;
    }

    /**
     * 绘制平均统计卡片
     */
    private static int drawAvgStatsCard(Graphics2D g2d, Map<String, String> avgStats, int x, int y, int width,
            int height) {
        g2d.setColor(new Color(0, 0, 0, 15));
        g2d.fillRoundRect(x + 4, y + 4, width, height, 12, 12);
        g2d.setColor(CARD_BG_COLOR);
        g2d.fillRoundRect(x, y, width, height, 12, 12);
        g2d.setColor(new Color(220, 224, 228));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(x, y, width, height, 12, 12);

        if (avgStats.isEmpty())
            return y + height;

        int itemWidth = width / avgStats.size();
        int currentX = x;

        for (Map.Entry<String, String> entry : avgStats.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            Color valColor = TEXT_COLOR;

            try {
                if (key.toUpperCase().contains("K/D")) {
                    double kd = Double.parseDouble(value.replaceAll("[^\\d.]", ""));
                    if (kd < 0.5)
                        valColor = new Color(231, 76, 60); // 红色
                    else if (kd < 1.0)
                        valColor = new Color(230, 126, 34); // 橙色
                    else if (kd < 1.5)
                        valColor = new Color(176, 224, 230); // 浅蓝色
                    else
                        valColor = new Color(46, 204, 113); // 绿色
                } else if (key.contains("伤害")) {
                    double dmg = Double.parseDouble(value.replaceAll("[^\\d.]", ""));
                    if (dmg < 100)
                        valColor = new Color(231, 76, 60); // 红色
                    else if (dmg < 150)
                        valColor = new Color(176, 224, 230); // 浅蓝色
                    else if (dmg < 200)
                        valColor = new Color(46, 204, 113); // 绿色
                    else if (dmg < 250)
                        valColor = new Color(34, 139, 34); // 深绿色
                    else
                        valColor = new Color(155, 89, 182); // 紫色
                }
            } catch (Exception e) {
            }

            int keyWidth = g2d.getFontMetrics(LABEL_FONT).stringWidth(key);
            int valWidth = g2d.getFontMetrics(new Font("微软雅黑", Font.BOLD, 28)).stringWidth(value);

            g2d.setColor(new Color(127, 140, 141));
            g2d.setFont(LABEL_FONT);
            g2d.drawString(key, currentX + (itemWidth - keyWidth) / 2, y + 42);

            g2d.setColor(valColor);
            g2d.setFont(new Font("微软雅黑", Font.BOLD, 28));
            g2d.drawString(value, currentX + (itemWidth - valWidth) / 2, y + 80);

            if (currentX > x) {
                g2d.setColor(new Color(236, 240, 241));
                g2d.drawLine(currentX, y + 25, currentX, y + height - 25);
            }
            currentX += itemWidth;
        }
        return y + height;
    }

    /**
     * 绘制最近比赛走势图（折线图）
     */
    private static int drawTrendChart(Graphics2D g2d, List<String[]> matches, int x, int y, int width, int height) {
        g2d.setColor(new Color(0, 0, 0, 15));
        g2d.fillRoundRect(x + 4, y + 4, width, height, 12, 12);
        g2d.setColor(CARD_BG_COLOR);
        g2d.fillRoundRect(x, y, width, height, 12, 12);
        g2d.setColor(new Color(220, 224, 228));
        g2d.setStroke(new BasicStroke(1.5f));
        g2d.drawRoundRect(x, y, width, height, 12, 12);

        int chartX = x + 70;
        int chartY = y + 50;
        int chartWidth = width - 180; // 腾出右侧更多空间以便加排名刻度
        int chartHeight = height - 90;

        int n = Math.min(20, matches.size());
        if (n == 0)
            return y + height;

        int[] damages = new int[n];
        int[] kills = new int[n];
        int[] ranks = new int[n];
        int maxDmg = 100;
        int maxKills = 2;
        int maxRank = 10;

        for (int i = 0; i < n; i++) {
            // 反转顺序，最旧的在左侧，最新的在最右边
            String[] m = matches.get(n - 1 - i);
            try {
                damages[i] = Integer.parseInt(m[5].replaceAll("[^\\d]", ""));
                kills[i] = Integer.parseInt(m[4].replaceAll("[^\\d]", ""));
                String cleanRank = m[3].replace("#", "").trim();
                if (cleanRank.contains("/"))
                    cleanRank = cleanRank.split("/")[0].trim();
                if (cleanRank.equalsIgnoreCase("w"))
                    ranks[i] = 1;
                else
                    ranks[i] = Integer.parseInt(cleanRank);
            } catch (Exception e) {
                ranks[i] = 50; // default fallback
            }
            maxDmg = Math.max(maxDmg, damages[i]);
            maxKills = Math.max(maxKills, kills[i]);
            maxRank = Math.max(maxRank, ranks[i]);
        }

        maxDmg = (maxDmg / 100 + 1) * 100;
        maxKills = (maxKills / 2 + 1) * 2;
        maxRank = (maxRank / 10 + 1) * 10;
        if (maxRank < 20)
            maxRank = 20;

        // 绘制网格线与Y轴刻度
        g2d.setStroke(new BasicStroke(1.0f));
        g2d.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        for (int i = 0; i <= 4; i++) {
            int gridY = chartY + chartHeight - (chartHeight * i / 4);
            g2d.setColor(new Color(236, 240, 241));
            g2d.drawLine(chartX, gridY, chartX + chartWidth, gridY);

            // 伤害左刻度
            g2d.setColor(new Color(149, 165, 166));
            String lblDmg = String.valueOf(maxDmg * i / 4);
            int lblDmgWidth = g2d.getFontMetrics().stringWidth(lblDmg);
            g2d.drawString(lblDmg, chartX - lblDmgWidth - 10, gridY + 4);

            // 击杀右边第一个刻度
            String lblKills = String.valueOf(maxKills * i / 4);
            g2d.drawString(lblKills, chartX + chartWidth + 10, gridY + 4);

            // 排名右边第二个刻度 (倒序，排名1在最上，max在最下)
            int rankVal = maxRank - ((maxRank - 1) * i / 4);
            if (i == 4)
                rankVal = 1;
            g2d.drawString(String.valueOf(rankVal), chartX + chartWidth + 55, gridY + 4);

            // 为排名刻度划一个小的分隔线标识
            g2d.setColor(new Color(220, 224, 228));
            g2d.drawLine(chartX + chartWidth + 45, gridY, chartX + chartWidth + 50, gridY);
        }

        // 绘制数据点与连线
        int[] px = new int[n];
        int[] pyDmg = new int[n];
        int[] pyKills = new int[n];
        int[] pyRanks = new int[n];

        for (int i = 0; i < n; i++) {
            px[i] = chartX + (n > 1 ? i * chartWidth / (n - 1) : chartWidth / 2);
            pyDmg[i] = chartY + chartHeight - (int) ((double) damages[i] / maxDmg * chartHeight);
            pyKills[i] = chartY + chartHeight - (int) ((double) kills[i] / maxKills * chartHeight);
            pyRanks[i] = chartY + (int) ((double) (ranks[i] - 1) / (Math.max(1, maxRank - 1)) * chartHeight);
        }

        // 排名线 (金色) - 画在最底层
        g2d.setColor(new Color(241, 196, 15, 150)); // 半透明金色
        g2d.setStroke(new BasicStroke(2.0f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[] { 5 }, 0)); // 虚线表示排名
        for (int i = 0; i < n - 1; i++) {
            g2d.drawLine(px[i], pyRanks[i], px[i + 1], pyRanks[i + 1]);
        }
        for (int i = 0; i < n; i++) {
            g2d.setColor(CARD_BG_COLOR);
            g2d.fillOval(px[i] - 4, pyRanks[i] - 4, 8, 8);
            g2d.setColor(new Color(241, 196, 15, 200));
            g2d.drawOval(px[i] - 4, pyRanks[i] - 4, 8, 8);
        }

        // 伤害线 (红/橘红)
        g2d.setColor(new Color(231, 76, 60));
        g2d.setStroke(new BasicStroke(2.5f));
        for (int i = 0; i < n - 1; i++) {
            g2d.drawLine(px[i], pyDmg[i], px[i + 1], pyDmg[i + 1]);
        }
        for (int i = 0; i < n; i++) {
            g2d.setColor(CARD_BG_COLOR);
            g2d.fillOval(px[i] - 5, pyDmg[i] - 5, 10, 10);
            g2d.setColor(new Color(231, 76, 60));
            g2d.drawOval(px[i] - 5, pyDmg[i] - 5, 10, 10);
        }

        // 击杀线 (蓝)
        g2d.setColor(new Color(52, 152, 219));
        g2d.setStroke(new BasicStroke(2.5f));
        for (int i = 0; i < n - 1; i++) {
            g2d.drawLine(px[i], pyKills[i], px[i + 1], pyKills[i + 1]);
        }
        for (int i = 0; i < n; i++) {
            g2d.setColor(CARD_BG_COLOR);
            g2d.fillOval(px[i] - 5, pyKills[i] - 5, 10, 10);
            g2d.setColor(new Color(52, 152, 219));
            g2d.drawOval(px[i] - 5, pyKills[i] - 5, 10, 10);
        }

        // 图例
        g2d.setFont(new Font("微软雅黑", Font.BOLD, 13));
        g2d.setColor(new Color(231, 76, 60));
        g2d.drawString("● 伤害 (左侧刻度)", chartX + 10, chartY - 20);
        g2d.setColor(new Color(52, 152, 219));
        g2d.drawString("● 击杀 (右侧第1刻度)", chartX + 130, chartY - 20);
        g2d.setColor(new Color(241, 196, 15));
        g2d.drawString("┅ 排名 (右侧第2刻度，越靠上越好)", chartX + 280, chartY - 20);

        // 最新标识
        g2d.setColor(new Color(149, 165, 166));
        g2d.setFont(new Font("微软雅黑", Font.PLAIN, 12));
        g2d.drawString("时间 →（最新场次在右侧）", chartX + chartWidth - 140, chartY + chartHeight + 25);

        return y + height;
    }

    public static String fetchUserInfoWithSelenium(String username) {
        WebDriver driver = null;
        try {
            // 使用本地 EdgeDriver
            String edgeDriverPath = "edgedriver_win64" + File.separator + "msedgedriver.exe";
            System.out.println("使用 EdgeDriver 路径: " + new File(edgeDriverPath).getAbsolutePath());
            System.setProperty("webdriver.edge.driver", edgeDriverPath);

            // 配置Edge选项
            EdgeOptions options = new EdgeOptions();
            // 可选：添加无头模式（不显示浏览器窗口）
            // options.addArguments("--headless");
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");
            options.addArguments("--disable-blink-features=AutomationControlled");
            options.addArguments("user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");

            driver = new EdgeDriver(options);

            String url = BASE_URL + username;
            System.out.println("开始访问: " + url);
            driver.get(url);

            // 页面打开后先轮询 renewBtn，若存在则点击触发刷新
            pollAndClickRenewButton(driver, 30);

            // 等待页面加载完成（等待AWS WAF验证完成）
            // 等待challenge页面消失或数据加载完成
            Duration timeout = Duration.ofSeconds(30);
            WebDriverWait wait = new WebDriverWait(driver, timeout);

            // 等待页面不再包含"challenge"相关内容
            try {
                wait.until(d -> {
                    String pageSource = d.getPageSource();
                    return !pageSource.contains("challenge.js") && !pageSource.contains("awsWafCookieDomainList");
                });
                System.out.println("WAF验证完成，页面加载成功");
            } catch (Exception e) {
                System.out.println("等待超时，尝试继续获取页面内容...");
            }

            // 额外等待数据加载
            Thread.sleep(3000);

            // 等待关键数据元素出现
            try {
                wait.until(d -> d.findElements(By.xpath("//*[contains(text(), '击杀')]")).size() > 0);
                System.out.println("统计数据加载完成");
            } catch (Exception e) {
                System.out.println("统计数据加载超时，继续处理可用数据...");
            }

            // 提取PUBG用户信息
            PubgUserStats stats = extractUserStats(driver, username);

            // 生成图表
            String imagePath = generateAndSavePubgChart(stats, "img");

            System.out.println("\n========== 提取完成 ==========\n");
            return imagePath;

        } catch (Exception e) {
            System.err.println("获取用户信息失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        } finally {
            if (driver != null) {
                driver.quit();
                System.out.println("\n浏览器已关闭");
            }
        }
    }

    public static void main(String[] args) {
        System.out.println("========== PUBG用户信息获取 (使用Selenium) ==========\n");

        String username = "Arisu137";
        System.out.println("正在获取用户: " + username);
        System.out.println("请耐心等待，浏览器会自动打开并完成验证...\n");

        fetchUserInfoWithSelenium(username);

        System.out.println("\n========== 获取完成 ==========");
    }
}
