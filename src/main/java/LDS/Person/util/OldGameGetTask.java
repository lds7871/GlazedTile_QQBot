package LDS.Person.util;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.core.env.Environment;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.nio.charset.StandardCharsets;

/**
 * Abandonware 浏览器：访问 https://www.myabandonware.com/browse/random
 * 每日凌晨 1:30 执行，获取随机废弃软件页面并提取信息保存至数据库
 */
@Component
public class OldGameGetTask {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private Environment env;

    public static class FetchResult {
        public final String html;
        public final String finalUrl;
        public FetchResult(String html, String finalUrl) {
            this.html = html;
            this.finalUrl = finalUrl;
        }
    }

    /**
     * 获取随机废弃软件页面内容（处理 gzip 压缩）
     *
     * @return HTML 页面内容
     */
    private FetchResult fetchRandomGame() throws Exception {
        String urlString = "https://www.myabandonware.com/browse/random";
        
        URL url = new URL(urlString);
        
        // 根据配置判断是否启用代理
        Proxy proxy = getConfiguredProxy();
        HttpURLConnection connection = proxy == null ? (HttpURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection(proxy);
        
        try {
            // 配置请求头（不要求自动解压，让我们手动处理）
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(10000);
            
            // 获取响应状态码
            int responseCode = connection.getResponseCode();
          //  System.out.println("✓ 响应状态码: " + responseCode);
          //  System.out.println("✓ 内容编码: " + connection.getContentEncoding());
            
            if (responseCode != 200) {
                throw new Exception("HTTP 错误: " + responseCode);
            }
            
            // 读取响应内容（自动处理 gzip 压缩）
            StringBuilder response = new StringBuilder();
            java.io.InputStream inputStream = connection.getInputStream();
            
            // 检查内容编码并处理
            String contentEncoding = connection.getContentEncoding();
            if (contentEncoding != null && contentEncoding.contains("gzip")) {
                inputStream = new java.util.zip.GZIPInputStream(inputStream);
            //    System.out.println("✓ 检测到 gzip 压缩，进行解压...");
            }
            
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line).append("\n");
                }
            }
            
            // 获取重定向后的最终 URL
            String finalUrl = connection.getURL() != null ? connection.getURL().toString() : urlString;
            return new FetchResult(response.toString(), finalUrl);
            
        } finally {
            connection.disconnect();
        }
    }




    /**
     * 提取游戏基本信息（标题、平台、年份）
     * 格式：<div class="box"><h2>标题</h2><p class="h2 h2--subtitle">平台 - 年份</p></div>
     */
    private static java.util.Map<String, String> extractGameBasicInfo(String htmlContent) {
        java.util.Map<String, String> info = new java.util.HashMap<>();
        
        // 查找第一个 <div class="box">
        int boxStart = htmlContent.indexOf("<div class=\"box\">");
        if (boxStart == -1) {
            return info;
        }
        
        int boxEnd = htmlContent.indexOf("</div>", boxStart);
        String boxContent = htmlContent.substring(boxStart, boxEnd + 6);
        
        // 提取 <h2> 标题
        int h2Start = boxContent.indexOf("<h2>");
        int h2End = boxContent.indexOf("</h2>");
        if (h2Start != -1 && h2End != -1) {
            String title = boxContent.substring(h2Start + 4, h2End).trim();
            info.put("title", title);
        }
        
        // 提取 <p class="h2 h2--subtitle"> 内容（平台和年份）
        int pStart = boxContent.indexOf("<p class=\"h2 h2--subtitle\">");
        int pEnd = boxContent.indexOf("</p>", pStart);
        if (pStart != -1 && pEnd != -1) {
            String platformYear = boxContent.substring(pStart + 28, pEnd).trim();
            info.put("platformYear", platformYear);
        }
        
        return info;
    }

    /**
     * 提取游戏信息表格内容
     * 格式：<table class="gameInfo">...</table>
     */
    private static String extractGameInfoTable(String htmlContent) {
        int tableStart = htmlContent.indexOf("<table class=\"gameInfo\">");
        if (tableStart == -1) {
            return "";
        }
        
        int tableEnd = htmlContent.indexOf("</table>", tableStart);
        if (tableEnd == -1) {
            return "";
        }
        
        return htmlContent.substring(tableStart, tableEnd + 8);
    }

    /**
     * 解析游戏信息表格为键值对
     * 从 <table class="gameInfo"> 中提取所有 <tr> 中的 <td> 信息
     * 格式: <tr><td>标题</td><td>内容</td></tr>
     */
    private static java.util.Map<String, String> parseGameInfoTable(String htmlContent) {
        java.util.Map<String, String> tableData = new java.util.LinkedHashMap<>();
        
        String tableHtml = extractGameInfoTable(htmlContent);
        if (tableHtml.isEmpty()) {
            return tableData;
        }
        
        // 查找所有 <tr> 行
        int startIndex = 0;
        while (true) {
            int trStart = tableHtml.indexOf("<tr>", startIndex);
            if (trStart == -1) {
                break;
            }
            
            int trEnd = tableHtml.indexOf("</tr>", trStart);
            if (trEnd == -1) {
                break;
            }
            
            String trContent = tableHtml.substring(trStart, trEnd + 5);
            
            // 从一行中提取所有 <th> 或 <td>
            java.util.List<String> cellValues = new java.util.ArrayList<>();
            int cellStartIndex = 0;
            while (true) {
                int thStart = trContent.indexOf("<th", cellStartIndex);
                int tdStart = trContent.indexOf("<td", cellStartIndex);
                int start = -1;
                String tag = null;
                if (thStart != -1 && (tdStart == -1 || thStart < tdStart)) {
                    start = thStart;
                    tag = "th";
                } else if (tdStart != -1) {
                    start = tdStart;
                    tag = "td";
                }
                if (start == -1) {
                    break;
                }
                int contentStart = trContent.indexOf(">", start);
                if (contentStart == -1) break;
                contentStart += 1;
                int contentEnd = trContent.indexOf("</" + tag + ">", contentStart);
                if (contentEnd == -1) break;
                String cellValue = trContent.substring(contentStart, contentEnd).trim();
                cellValue = cellValue.replaceAll("<[^>]*>", "");
                cellValues.add(cellValue);
                cellStartIndex = contentEnd + tag.length() + 3; // move past </td> or </th>
            }
            // (已使用上面的通用 cell 解析，不再使用单独的 td 循环)
            
            // 如果有至少2个 td，第一个作为 key，第二个作为 value
            if (cellValues.size() >= 2) {
                // key 是第一列，value 是其余列的合并（以逗号分隔）
                String key = cellValues.get(0);
                String value = String.join(", ", cellValues.subList(1, cellValues.size()));
                tableData.put(key, value);
            } else if (cellValues.size() == 1) {
                // 只有一列的情况：认为是 key，value 为空
                tableData.put(cellValues.get(0), "");
            }
            
            startIndex = trEnd + 1;
        }
        
        return tableData;
    }

    /**
     * 使用Google Translate API翻译文本（英文到中文）
     * 简化版实现：通过 Google Translate 的简单 URL 调用
     */
    private String translateToChineseGoogle(String text) {
        if (text == null || text.isEmpty() || text.length() > 500) {
            return text;
        }
        
        try {
            // 使用 Google 翻译 API（需要网络连接）
            String encoded = java.net.URLEncoder.encode(text, "UTF-8");
            String urlStr = "https://translate.googleapis.com/translate_a/single?client=gtx&sl=en&tl=zh-CN&dt=t&q=" + encoded;
            
            URL url = new URL(urlStr);
            Proxy proxy = getConfiguredProxy();
            HttpURLConnection conn = proxy == null ? (HttpURLConnection) url.openConnection() : (HttpURLConnection) url.openConnection(proxy);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            // 增加超时时间：连接超时15秒，读取超时15秒（代理连接可能更慢）
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            
            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                StringBuilder response = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        response.append(line);
                    }
                }
                
                // 解析翻译结果：[[["翻译结果",...
                String result = response.toString();
                int start = result.indexOf("\"");
                if (start != -1) {
                    int end = result.indexOf("\"", start + 1);
                    if (end != -1) {
                        return result.substring(start + 1, end);
                    }
                }
            } else {
                System.err.println("⚠ 翻译API返回错误码: " + responseCode);
            }
            conn.disconnect();
        } catch (java.net.SocketTimeoutException e) {
            System.err.println("⚠ 翻译超时（请检查网络和代理配置）: " + e.getMessage());
        } catch (Exception e) {
            System.err.println("⚠ 翻译失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return text;
    }

    /**
     * 简单的英文检测（包含字母则认为是英文）
     */
    private static boolean isEnglish(String text) {
        if (text == null || text.isEmpty()) {
            return false;
        }
        // 检查是否包含英文字母
        return text.matches(".*[a-zA-Z].*");
    }

    /**
     * 提取游戏描述
     * 从第二个 <div class="box"> 中提取 <h3>Description... 后的 <p> 元素内容
     */
    private static String extractDescription(String htmlContent) {
        // 查找包含 "Description" 的 div
        int descStart = htmlContent.indexOf("<h3>Description");
        if (descStart == -1) {
            return "";
        }
        
        // 找到该区域的 <p> 标签
        int boxStart = htmlContent.lastIndexOf("<div class=\"box\">", descStart);
        int pStart = htmlContent.indexOf("<p>", descStart);
        int pEnd = htmlContent.indexOf("</p>", pStart);
        
        if (pStart != -1 && pEnd != -1 && pStart > boxStart) {
            String description = htmlContent.substring(pStart + 3, pEnd).trim();
            return description;
        }
        
        return "";
    }

    /**
     * 提取下载链接和文件大小
     * 格式：<a href="#download">Download <span>大小</span></a>
     */
    private static java.util.Map<String, String> extractDownloadInfo(String htmlContent, String pageUrl) {
        java.util.Map<String, String> downloadInfo = new java.util.HashMap<>();
        
        int aStart = htmlContent.indexOf("<a href=\"#download\">");
        if (aStart == -1) {
            return downloadInfo;
        }
        
        int aEnd = htmlContent.indexOf("</a>", aStart);
        String aContent = htmlContent.substring(aStart, aEnd + 4);
        
        // 提取 <span> 内容（文件大小）
        int spanStart = aContent.indexOf("<span>");
        int spanEnd = aContent.indexOf("</span>", spanStart);
        if (spanStart != -1 && spanEnd != -1) {
            String fileSize = aContent.substring(spanStart + 6, spanEnd).trim();
            downloadInfo.put("fileSize", fileSize);
            // 生成完整下载链接：使用页面 URL + #download
            if (pageUrl != null && !pageUrl.isEmpty()) {
                String base = pageUrl.split("#")[0];
                downloadInfo.put("downloadUrl", base + "#download");
            } else {
                downloadInfo.put("downloadUrl", "#download");
            }
        }
        
        return downloadInfo;
    }

    /**
     * 解析完整的游戏页面并返回结构化数据
     */
    private static java.util.Map<String, Object> parseGamePage(String htmlContent, String pageUrl) {
        java.util.Map<String, Object> gameData = new java.util.LinkedHashMap<>();
        
        // 获取基本信息
        java.util.Map<String, String> basicInfo = extractGameBasicInfo(htmlContent);
        gameData.put("basicInfo", basicInfo);
        
        // 获取游戏信息表格
        String gameInfoTable = extractGameInfoTable(htmlContent);
        gameData.put("gameInfoTable", gameInfoTable);
        
        // 获取描述
        String description = extractDescription(htmlContent);
        gameData.put("description", description);
        
        // 页面 URL
        gameData.put("pageUrl", pageUrl);
        // 页面截图
        String screenshotUrl = extractFirstScreenshotUrl(htmlContent, pageUrl);
        gameData.put("screenshotUrl", screenshotUrl);
        // 获取下载信息
        java.util.Map<String, String> downloadInfo = extractDownloadInfo(htmlContent, pageUrl);
        gameData.put("downloadInfo", downloadInfo);
        
        return gameData;
    }


    /**
     * 将游戏数据格式化为字符串并返回（不包含图片 URL）
     */
    private String renderGameDataAsString(java.util.Map<String, Object> gameData) {
        StringBuilder sb = new StringBuilder();
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> basicInfo = (java.util.Map<String, String>) gameData.get("basicInfo");
        if (!basicInfo.isEmpty()) {
            sb.append("[基本信息]").append(System.lineSeparator());
            sb.append("  游戏名: ").append(basicInfo.getOrDefault("title", "N/A")).append(System.lineSeparator());
            sb.append("  平台/年份: ").append(basicInfo.getOrDefault("platformYear", "N/A")).append(System.lineSeparator());
        }
        
        String gameInfoTable = (String) gameData.get("gameInfoTable");
        if (!gameInfoTable.isEmpty()) {
            sb.append(System.lineSeparator()).append("[游戏信息表]").append(System.lineSeparator());
            java.util.Map<String, String> tableData = parseGameInfoTable(gameInfoTable);
            if (!tableData.isEmpty()) {
                for (java.util.Map.Entry<String, String> entry : tableData.entrySet()) {
                    String key = entry.getKey();
                    String value = entry.getValue();
                    String keyClean = key == null ? "" : key.trim().toLowerCase();
                    if (keyClean.contains("alt name") || keyClean.contains("alt names") || keyClean.contains("alternate") || keyClean.contains("altnames") || keyClean.startsWith("alt ") || keyClean.startsWith("alt:")) {
                        continue;
                    }
                    java.util.Map<String, String> keyTranslations = new java.util.HashMap<>();
                    keyTranslations.put("Year", "年份");
                    keyTranslations.put("Platform", "平台");
                    keyTranslations.put("Released in", "发行地");
                    keyTranslations.put("Genre", "类型");
                    keyTranslations.put("Theme", "主题");
                    keyTranslations.put("Publisher", "发行商");
                    keyTranslations.put("Developer", "开发商");
                    keyTranslations.put("Perspective", "视角");
                    keyTranslations.put("Perspectives", "视角");
                    String displayKey = keyTranslations.getOrDefault(key, key);
                    if ("Genre".equalsIgnoreCase(key) || "Theme".equalsIgnoreCase(key)) {
                        String[] parts = value.split("\\s*,\\s*|\\s*/\\s*|\\s*;\\s*|\\s*\\|\\s*");
                        java.util.List<String> translatedParts = new java.util.ArrayList<>();
                        for (String part : parts) {
                            String trimmed = part.trim();
                            if (isEnglish(trimmed)) {
                                translatedParts.add(translateToChineseGoogle(trimmed));
                            } else {
                                translatedParts.add(trimmed);
                            }
                        }
                        value = String.join("、", translatedParts);
                    }
                    sb.append("  ").append(displayKey).append("：").append(value).append(System.lineSeparator());
                }
            }
        }
        
        String description = (String) gameData.get("description");
        if (!description.isEmpty()) {
            sb.append(System.lineSeparator()).append("[描述]").append(System.lineSeparator());
            String translatedDesc = description;
            if (isEnglish(description)) {
                translatedDesc = translateToChineseGoogle(description);
            }
            sb.append("  ").append(translatedDesc).append(System.lineSeparator());
        }
        
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> downloadInfo = (java.util.Map<String, String>) gameData.get("downloadInfo");
        if (!downloadInfo.isEmpty()) {
            sb.append(System.lineSeparator()).append("[下载信息]").append(System.lineSeparator());
            sb.append("  文件大小: ").append(downloadInfo.getOrDefault("fileSize", "N/A")).append(System.lineSeparator());
            sb.append("  下载链接: ").append(downloadInfo.getOrDefault("downloadUrl", "N/A")).append(System.lineSeparator());
        }

        return sb.toString();
    }

    /**
     * 从游戏数据中提取图片 URL（如果为 "找不到图片" 则返回 null）
     */
    private static String extractScreenshotUrl(java.util.Map<String, Object> gameData) {
        Object screenshotObj = gameData.get("screenshotUrl");
        String screenshot = screenshotObj == null ? "" : screenshotObj.toString();
        if (screenshot == null || screenshot.isEmpty() || "找不到图片".equals(screenshot)) {
            return null;  // 返回 null 表示无有效的图片 URL
        }
        return screenshot;
    }

    /**
     * 提取页面内第一个截图链接：查找 class="item itemListScreenshot thumb c-thumb lb" 的元素并取 href
     */
    private static String extractFirstScreenshotUrl(String htmlContent, String pageUrl) {
        if (htmlContent == null || htmlContent.isEmpty()) return null;
        String marker = "class=\"item itemListScreenshot thumb c-thumb lb\"";
        int idx = htmlContent.indexOf(marker);
        if (idx == -1) return null;
        // 从 marker 位置查找 href
        int hrefIdx = htmlContent.indexOf("href=\"", idx);
        if (hrefIdx == -1) return null;
        int start = hrefIdx + 6; // after href="
        int end = htmlContent.indexOf('"', start);
        if (end == -1) return null;
        String href = htmlContent.substring(start, end).trim();
        if (href.isEmpty()) return null;
        // 完整化 URL
        if (href.startsWith("http://") || href.startsWith("https://")) return href;
        if (href.startsWith("//")) return "https:" + href;
        if (href.startsWith("/")) return "https://www.myabandonware.com" + href;
        // 相对路径（没有以 / 开头），尝试基于 pageUrl
        if (pageUrl != null && !pageUrl.isEmpty()) {
            try {
                URL base = new URL(pageUrl);
                String basePath = base.getProtocol() + "://" + base.getHost();
                if (base.getPort() != -1) basePath += ":" + base.getPort();
                if (!href.startsWith("/")) href = "/" + href;
                return basePath + href;
            } catch (Exception e) {
                return href;
            }
        }
        return href;
    }

    /**
     * 根据 `config.properties` 读取代理配置，如果未启用则返回 null
     */
    private Proxy getConfiguredProxy() {
        try {
            // 首先检查 env 是否可用
            if (env == null) {
                System.err.println("[OldGameTask] Environment 未注入，无法读取代理配置");
                return null;
            }
            
            String enabled = env.getProperty("proxy.is.open");
            // System.out.println("[OldGameTask] proxy.is.open = " + enabled);
            
            if (enabled == null || !Boolean.parseBoolean(enabled.trim())) {
                System.out.println("[OldGameTask] 代理未启用，使用直接连接");
                return null;
            }
            
            String host = env.getProperty("proxy.host", "127.0.0.1");
            String portStr = env.getProperty("proxy.port.socks", "33211");
            
            // System.out.println("[OldGameTask] 代理配置 - host: " + host + ", port: " + portStr);
            
            int port = Integer.parseInt(portStr);
            Proxy proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(host, port));
            // System.out.println("[OldGameTask] 代理已启用: " + host + ":" + port);
            return proxy;
        } catch (Exception e) {
            System.err.println("[OldGameTask] 读取 proxy 配置失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 定时任务：每天凌晨 1:30 执行
     * 获取随机游戏、解析、保存到数据库
     */
    @Scheduled(cron = "0 30 1 * * *")
    public void executeScheduledTask() {
        try {
            System.out.println("[OldGameTask] 开始执行定时任务...");
            
            // 获取随机游戏页面
            FetchResult result = fetchRandomGame();
            String htmlContent = result.html;
            String finalUrl = result.finalUrl;
            
            // 解析游戏数据
            java.util.Map<String, Object> gameData = parseGamePage(htmlContent, finalUrl);
            
            // 获取格式化文本和图片 URL
            String contentText = renderGameDataAsString(gameData);
            String imageUrl = extractScreenshotUrl(gameData);
            
            // 保存到数据库
            saveGameDataToDb(contentText, imageUrl);
            
            System.out.println("[OldGameTask] 定时任务执行完成");
            
        } catch (Exception e) {
            System.err.println("[OldGameTask] 定时任务执行失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 将游戏数据保存到数据库
     * @param contentText 游戏信息文本（不包含图片 URL）
     * @param imageUrl 图片 URL（可能为 null）
     */
    private void saveGameDataToDb(String contentText, String imageUrl) {
        try {
            String sql = "INSERT INTO old_game (content, image_url, created_at) VALUES (?, ?, NOW())";
            
            jdbcTemplate.update(sql, contentText, imageUrl);
            
            System.out.println("[OldGameTask] 数据已保存到数据库");
        } catch (Exception e) {
            System.err.println("[OldGameTask] 数据保存失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 主方法：用于本地测试（不作为定时任务执行）
     */
    // public static void main(String[] args) {
    //    // System.out.println("========== Abandonware 浏览器 ==========\n");
        
    //     try {
    //        // System.out.println("正在获取随机游戏页面...");
    //         FetchResult result = fetchRandomGame();
    //         String htmlContent = result.html;
    //         String finalUrl = result.finalUrl;
            
    //         //System.out.println("✓ 页面获取成功");
    //        // System.out.println("✓ 页面大小: " + getPageSize(htmlContent) + " 字节\n");
            
    //         // 解析游戏数据
    //         java.util.Map<String, Object> gameData = parseGamePage(htmlContent, finalUrl);
            
    //         // 显示结果（包括文本和图片 URL）
    //         String contentText = renderGameDataAsString(gameData);
    //         String imageUrl = extractScreenshotUrl(gameData);
    //         System.out.println(contentText);
    //         if (imageUrl != null) {
    //             System.out.println(imageUrl);
    //         }
            
    //     } catch (Exception e) {
    //         System.err.println("✗ 错误: " + e.getMessage());
    //         e.printStackTrace();
    //     }
    // }
}
