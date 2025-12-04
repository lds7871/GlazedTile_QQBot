package LDS.Person.util;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinUser;

import java.awt.Desktop;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.imageio.ImageIO;

/**
 * 统一的 Galgame 处理器：获取 API 数据 → 解析 JSON → 生成 HTML → 打开浏览器 → 截图 → 关闭浏览器
 */
public class GalgameProcessor {

    // 截取窗口的中心区域的比例：纵向，横向
    private static final double CAPTURE_VERTICAL_RATIO = 0.72;
    private static final double CAPTURE_HORIZONTAL_RATIO = 0.87;

    /**
     * 游戏数据模型
     */
    public static class GalgameData {
        public String name;
        public String bannerUrl;
        public String createdDate;
        public int viewCount;
        public int downloadCount;
        public String avgRating;

        public GalgameData(String name, String bannerUrl, String createdDate, 
                          int viewCount, int downloadCount, String avgRating) {
            this.name = name;
            this.bannerUrl = bannerUrl;
            this.createdDate = createdDate;
            this.viewCount = viewCount;
            this.downloadCount = downloadCount;
            this.avgRating = avgRating;
        }
    }

    /**
     * 调用 Galgame API 获取游戏列表（使用 HttpURLConnection）
     */
    public static String fetchGalgamesJson() throws Exception {
        String urlString = "https://www.touchgal.us/api/galgame?selectedType=all&selectedLanguage=all" +
                          "&selectedPlatform=all&sortField=created&sortOrder=desc&page=1&limit=5" +
                          "&yearString=[%22all%22]&monthString=[%22all%22]";
        
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        
        try {
            setupConnectionHeaders(connection);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                throw new Exception("HTTP 错误: " + responseCode);
            }
            
            return readResponse(connection);
        } finally {
            connection.disconnect();
        }
    }

    /**
     * 配置连接请求头
     */
    private static void setupConnectionHeaders(HttpURLConnection connection) throws Exception {
        connection.setRequestMethod("GET");
        connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        connection.setRequestProperty("Accept", "application/json");
        connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        connection.setConnectTimeout(10000);
        connection.setReadTimeout(10000);
    }

    /**
     * 读取 HTTP 响应
     */
    private static String readResponse(HttpURLConnection connection) throws Exception {
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    /**
     * 使用 Gson 解析 API 响应并转换为游戏数据列表
     */
    public static List<GalgameData> parseGalgamesWithGson(String jsonResponse) {
        List<GalgameData> games = new ArrayList<>();
        
        try {
            Gson gson = new Gson();
            JsonObject root = gson.fromJson(jsonResponse, JsonObject.class);
            
            if (!root.has("galgames")) {
                System.err.println("✗ 响应中未找到 'galgames' 字段");
                return games;
            }
            
            JsonArray galgamesArray = root.getAsJsonArray("galgames");
            
            for (int i = 0; i < galgamesArray.size(); i++) {
                JsonObject game = galgamesArray.get(i).getAsJsonObject();
                
                String name = getJsonString(game, "name", "未知");
                String banner = getJsonString(game, "banner", "");
                String created = getJsonString(game, "created", "N/A");
                int view = getJsonInt(game, "view", 0);
                int download = getJsonInt(game, "download", 0);
                String rating = parseRating(game);
                
                String formattedDate = formatDateOnly(created);
                games.add(new GalgameData(name, banner, formattedDate, view, download, rating));
            }
            
           // System.out.println("✓ 成功解析 " + games.size() + " 款游戏");
            
        } catch (Exception e) {
            System.err.println(" 解析 Gson 失败: " + e.getMessage());
            e.printStackTrace();
        }
        
        return games;
    }

    /**
     * 从 JsonObject 中安全获取字符串值
     */
    private static String getJsonString(JsonObject obj, String key, String defaultValue) {
        return obj.has(key) ? obj.get(key).getAsString() : defaultValue;
    }

    /**
     * 从 JsonObject 中安全获取整数值
     */
    private static int getJsonInt(JsonObject obj, String key, int defaultValue) {
        return obj.has(key) ? obj.get(key).getAsInt() : defaultValue;
    }

    /**
     * 解析评分
     */
    private static String parseRating(JsonObject game) {
        if (game.has("averageRating")) {
            double avgRating = game.get("averageRating").getAsDouble();
            if (avgRating > 0) {
                return String.format("%.2f", avgRating);
            }
        }
        return "暂无";
    }

    /**
     * 格式化日期 (ISO-8601 → YYYY-MM-DD)
     */
    public static String formatDateOnly(String dateTime) {
        if (dateTime == null || dateTime.isEmpty()) {
            return "N/A";
        }
        if (dateTime.length() >= 10) {
            try {
                String datePart = dateTime.substring(0, 10);
                if (datePart.charAt(4) == '-' && datePart.charAt(7) == '-') {
                    return datePart;
                }
            } catch (Exception ignored) {}
        }
        return dateTime;
    }

    /**
     * 生成 HTML 表格
     */
    public static String generateHtmlTable(List<GalgameData> games) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>\n")
            .append("<html>\n")
            .append("<head>\n")
            .append("    <meta charset=\"UTF-8\">\n")
            .append("    <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n")
            .append("    <title>Galgame 游戏列表</title>\n")
            .append("    <style>\n")
            .append("        * { margin: 0; padding: 0; box-sizing: border-box; }\n")
            .append("        html, body { \n")
            .append("            height: 100%;\n")
            .append("            width: 100%;\n")
            .append("        }\n")
            .append("        body { \n")
            .append("            font-family: 'Segoe UI', 'Microsoft YaHei', Arial, sans-serif;\n")
            .append("            background: #ffffff;\n")
            .append("            overflow: hidden;\n")
            .append("        }\n")
            .append("        .wrapper {\n")
            .append("            height: 100vh;\n")
            .append("            width: 100%;\n")
            .append("            padding: 40px 0 40px 5%;\n")
            .append("            display: flex;\n")
            .append("            flex-direction: column;\n")
            .append("            overflow: hidden;\n")
            .append("        }\n")
            .append("        .container { \n")
            .append("            flex: 1;\n")
            .append("            width: 100%;\n")
            .append("            display: flex;\n")
            .append("            flex-direction: column;\n")
            .append("            overflow: hidden;\n")
            .append("        }\n")
            .append("        .header { \n")
            .append("            text-align: center;\n")
            .append("            flex-shrink: 0;\n")
            .append("            padding-bottom: 20px;\n")
            .append("        }\n")
            .append("        h1 { \n")
            .append("            color: #1a1a1a;\n")
            .append("            font-size: 2em;\n")
            .append("            font-weight: 700;\n")
            .append("            letter-spacing: 1px;\n")
            .append("            margin: 0;\n")
            .append("        }\n")
            .append("        .subtitle {\n")
            .append("            color: #999;\n")
            .append("            font-size: 0.85em;\n")
            .append("            margin-top: 6px;\n")
            .append("        }\n")
            .append("        .table-wrapper {\n")
            .append("            flex: 1;\n")
            .append("            width: 100%;\n")
            .append("            overflow-y: auto;\n")
            .append("            border: 1px solid #e5e5e5;\n")
            .append("            border-radius: 4px;\n")
            .append("            box-shadow: 0 1px 3px rgba(0,0,0,0.05);\n")
            .append("        }\n")
            .append("        table { \n")
            .append("            width: 100%;\n")
            .append("            background: white;\n")
            .append("            border-collapse: collapse;\n")
            .append("        }\n")
            .append("        thead {\n")
            .append("            position: sticky;\n")
            .append("            top: 0;\n")
            .append("            z-index: 10;\n")
            .append("        }\n")
            .append("        th { \n")
            .append("            background: linear-gradient(135deg, #f8f8f8 0%, #f0f0f0 100%);\n")
            .append("            color: #1a1a1a;\n")
            .append("            padding: 16px;\n")
            .append("            text-align: left;\n")
            .append("            font-weight: 700;\n")
            .append("            font-size: 0.9em;\n")
            .append("            border-bottom: 2px solid #e5e5e5;\n")
            .append("            letter-spacing: 0.5px;\n")
            .append("        }\n")
            .append("        td { \n")
            .append("            border-bottom: 1px solid #f0f0f0;\n")
            .append("            padding: 14px 16px;\n")
            .append("            vertical-align: middle;\n")
            .append("            color: #333;\n")
            .append("            font-weight: 500;\n")
            .append("            font-size: 0.9em;\n")
            .append("            text-align: left;\n")
            .append("        }\n")
            .append("        tr:last-child td { border-bottom: none; }\n")
            .append("        tbody tr:hover { \n")
            .append("            background: #f9f9f9;\n")
            .append("            transition: background-color 0.2s ease;\n")
            .append("        }\n")
            .append("        .game-name { \n")
            .append("            font-weight: 700;\n")
            .append("            color: #1a1a1a;\n")
            .append("            word-wrap: break-word;\n")
            .append("        }\n")
            .append("        .game-banner {\n")
            .append("            text-align: left;\n")
            .append("        }\n")
            .append("        img { \n")
            .append("            max-width: 100px;\n")
            .append("            height: auto;\n")
            .append("            border-radius: 3px;\n")
            .append("            box-shadow: 0 1px 3px rgba(0,0,0,0.1);\n")
            .append("            transition: transform 0.2s ease;\n")
            .append("        }\n")
            .append("        img:hover {\n")
            .append("            transform: scale(1.05);\n")
            .append("        }\n")
            .append("        .stat-value {\n")
            .append("            text-align: left;\n")
            .append("            color: #666;\n")
            .append("            font-weight: 600;\n")
            .append("        }\n")
            .append("        .rating { \n")
            .append("            text-align: left;\n")
            .append("            color: #ff9800;\n")
            .append("            font-weight: 700;\n")
            .append("            font-size: 0.95em;\n")
            .append("        }\n")
            .append("        .rating-none { \n")
            .append("            text-align: left;\n")
            .append("            color: #ccc;\n")
            .append("            font-weight: 500;\n")
            .append("        }\n")
            .append("        .footer-space {\n")
            .append("            flex-shrink: 0;\n")
            .append("            height: 10%;\n")
            .append("        }\n")
            .append("    </style>\n")
            .append("</head>\n")
            .append("<body>\n")
            .append("    <div class=\"wrapper\">\n")
            .append("        <div class=\"header\">\n")
            .append("            <div class=\"subtitle\">数据来源：TouchGal</div>\n")
            .append("        </div>\n")
            .append("        <div class=\"container\">\n")
            .append("            <div class=\"table-wrapper\">\n")
            .append("                <table>\n")
            .append("                    <thead>\n")
            .append("                        <tr>\n")
            .append("                            <th style=\"width: 25%;\">游戏名称</th>\n")
            .append("                            <th style=\"width: 20%;\">封面图片</th>\n")
            .append("                            <th style=\"width: 15%;\">创建日期</th>\n")
            .append("                            <th style=\"width: 13%;\">浏览</th>\n")
            .append("                            <th style=\"width: 13%;\">下载</th>\n")
            .append("                            <th style=\"width: 14%;\">评分</th>\n")
            .append("                        </tr>\n")
            .append("                    </thead>\n")
            .append("                    <tbody>\n");
        
        // 表体
        for (GalgameData game : games) {
            html.append("                        <tr>\n")
                .append("                            <td class=\"game-name\">").append(escapeHtml(game.name)).append("</td>\n")
                .append("                            <td class=\"game-banner\"><img src=\"").append(escapeHtml(game.bannerUrl))
                .append("\" alt=\"").append(escapeHtml(game.name)).append("\"></td>\n")
                .append("                            <td>").append(game.createdDate).append("</td>\n")
                .append("                            <td class=\"stat-value\">").append(formatNumber(game.viewCount)).append("</td>\n")
                .append("                            <td class=\"stat-value\">").append(formatNumber(game.downloadCount)).append("</td>\n")
                .append("                            <td class=\"")
                .append("暂无".equals(game.avgRating) ? "rating-none" : "rating")
                .append("\">").append(game.avgRating).append("</td>\n")
                .append("                        </tr>\n");
        }
        
        html.append("                    </tbody>\n")
            .append("                </table>\n")
            .append("            </div>\n")
            .append("        </div>\n")
            .append("        <div class=\"footer-space\"></div>\n")
            .append("    </div>\n")
            .append("</body>\n")
            .append("</html>\n");
        
        return html.toString();
    }

    /**
     * 格式化数字（添加千分位分隔符）
     */
    private static String formatNumber(int number) {
        return String.format("%,d", number).replace(",", ",");
    }

    /**
     * 转义 HTML 特殊字符
     */
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&#39;");
    }

    /**
     * 打开 HTML 文件
     */
    public static void openHtmlFile(String htmlFilePath) throws Exception {
        File htmlFile = new File(htmlFilePath);

        // 如果找不到，则尝试使用 user.dir（运行时工作目录）
        if (!htmlFile.exists()) {
            htmlFile = new File(java.nio.file.Paths.get(System.getProperty("user.dir")).toFile(), htmlFilePath);
        }

        // 如果仍然找不到，则尝试从类所在位置（jar 所在目录或 classes 目录）查找
        if (!htmlFile.exists()) {
            try {
                java.nio.file.Path classPath = java.nio.file.Paths.get(GalgameProcessor.class.getProtectionDomain().getCodeSource().getLocation().toURI());
                java.nio.file.Path root = classPath.getParent();
                htmlFile = new File(root.toFile(), htmlFilePath);
            } catch (Exception ex) {
                // 忽略并继续下方的检查
            }
        }

        // 若仍然找不到则输出有帮助的提示并退出
        if (!htmlFile.exists()) {
            System.err.println("✗ 找不到 HTML 文件。请确保它位于项目根目录或当前工作目录。当前路径: " +
                    java.nio.file.Paths.get("").toAbsolutePath().toString());
            throw new Exception("HTML 文件不存在");
        }

        String absolutePath = htmlFile.getAbsolutePath();
        boolean opened = false;

        // 尝试方案 1：使用 Desktop API（如果支持）
        if (Desktop.isDesktopSupported()) {
            try {
                Desktop.getDesktop().browse(htmlFile.toURI());
              //  System.out.println(" 已打开 HTML 文件（Desktop API）: " + absolutePath);
                opened = true;
            } catch (Exception ex) {
                System.out.println("✗ Desktop API 打开失败，尝试备选方案...");
            }
        }

        // 尝试方案 2：使用 Windows start 命令
        if (!opened) {
            try {
                    // Windows 系统
                    ProcessBuilder pb = new ProcessBuilder("cmd", "/c", "start", absolutePath);
                    pb.start();
               //     System.out.println("✓ 已打开 HTML 文件（Windows start 命令）: " + absolutePath);
                    opened = true;
            } catch (Exception ex) {
                System.out.println("✗ 系统命令打开失败: " + ex.getMessage());
            }
        }

        // 如果所有方案都失败
        if (!opened) {
            System.err.println("✗ 无法自动打开浏览器，请手动打开: " + absolutePath);
            // 不抛异常，允许继续执行后续的截图步骤
        }
    }

    /**
     * 使用 JNA + Robot 对浏览器窗口进行截图（中心 80% 纵向，60% 横向）
     * 如果环境是 headless，则使用 Windows 命令行工具替代
     */
    public static void captureTableAsImage(String outputImagePath) throws Exception {
        // 此处不再等待加载（加载等待由调用方在打开浏览器后处理）

        try {
        //    System.out.println("✓ 正在查找 Edge 窗口...");
            HWND hwnd = findWindowByTitle("Galgame 游戏列表");
            
            if (hwnd != null) {
                // 先尝试使用 Robot（仅在 GUI 环境中有效）
                try {
                    captureWindowUsingRobot(hwnd, outputImagePath);
                    return;
                } catch (java.awt.AWTException e) {
                    System.out.println("✗ Robot 截图不可用（headless 环境），尝试使用 Windows 命令行...");
                    captureWindowUsingPowerShell(outputImagePath);
                }
            } else {
                System.err.println("✗ 未找到名为 'Galgame 游戏列表' 的浏览器窗口，已跳过截图。");
            }
        } catch (Exception ex) {
            System.err.println("✗ 截图过程中出现错误: " + ex.getMessage());
            ex.printStackTrace();
        }
    }

    /**
     * 使用 Robot 进行截图（GUI 环境）
     */
    private static void captureWindowUsingRobot(HWND hwnd, String outputImagePath) throws Exception {
        RECT rect = new RECT();
        User32.INSTANCE.GetWindowRect(hwnd, rect);
        int left = rect.left;
        int top = rect.top;
        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;

        // 计算中心截取区域（纵向 80%，横向 60%）
        int captureWidth = (int) Math.max(1, Math.round(width * CAPTURE_HORIZONTAL_RATIO));
        int captureHeight = (int) Math.max(1, Math.round(height * CAPTURE_VERTICAL_RATIO));
        int startX = left + (width - captureWidth) / 2;
        int startY = top + (height - captureHeight) / 2;
        Rectangle captureRect = new Rectangle(startX, startY, captureWidth, captureHeight);

        Robot robot = new Robot();
        BufferedImage screenshot = robot.createScreenCapture(captureRect);

        File imgDir = new File("img");
        if (!imgDir.exists()) imgDir.mkdirs();
        
        File outFile = new File(imgDir, outputImagePath);
        ImageIO.write(screenshot, "png", outFile);
     //   System.out.println("✓ 截图已保存（Robot）: " + outFile.getAbsolutePath());
    }

    /**
     * 使用 PowerShell 进行截图（Windows headless 环境）
     * 使用 Windows 10+ 内置的 PrintWindow API
     */
    private static void captureWindowUsingPowerShell(String outputImagePath) throws Exception {
        try {
            // 创建输出目录
            File imgDir = new File("img");
            if (!imgDir.exists()) imgDir.mkdirs();
            
            String outputPath = new File(imgDir, outputImagePath).getAbsolutePath();
            
            // PowerShell 脚本：获取窗口并截图
            String psScript = 
                "$hwnd = (Get-Process | Where-Object {$_.MainWindowTitle -like '*Galgame*'} | Select-Object -First 1).MainWindowHandle; " +
                "if ($hwnd) { " +
                "  [Windows.Graphics.Capture.GraphicsCapturePicker, Windows.Graphics.Capture, ContentType=WindowsRuntime] > $null; " +
                "  Add-Type -AssemblyName System.Windows.Forms; " +
                "  $bitmap = New-Object System.Drawing.Bitmap(1280, 720); " +
                "  $graphics = [System.Drawing.Graphics]::FromImage($bitmap); " +
                "  $graphics.CopyFromScreen(0, 0, 0, 0, $bitmap.Size); " +
                "  $bitmap.Save('" + outputPath + "'); " +
                "  Write-Output '截图成功'; " +
                "} else { " +
                "  Write-Output '未找到窗口'; " +
                "}";
            
            // 执行 PowerShell 命令
            ProcessBuilder pb = new ProcessBuilder(
                "powershell", "-NoProfile", "-NonInteractive", "-Command", psScript
            );
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            // 读取输出
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
            );
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("[PowerShell] " + line);
            }
            
            int exitCode = process.waitFor();
            if (exitCode == 0) {
             //   System.out.println("✓ 截图已保存（PowerShell）: " + outputPath);
            } else {
                System.err.println("✗ PowerShell 截图失败，退出码: " + exitCode);
            }
        } catch (Exception ex) {
            System.err.println("✗ PowerShell 截图异常: " + ex.getMessage());
            // 不抛异常，允许流程继续
        }
    }

    /**
     * 裁剪已存在的图片：从中央截取指定比例的区域
     * @param inputImagePath 输入图片路径
     * @param outputImagePath 输出图片路径
     */
    public static void cropImageFromCenter(String inputImagePath, String outputImagePath) throws Exception {
        try {
            File inputFile = new File(inputImagePath);
            
            // 如果找不到，尝试加上 img/ 前缀
            if (!inputFile.exists() && !inputImagePath.startsWith("img/")) {
                inputFile = new File("img/" + inputImagePath);
            }
            
            if (!inputFile.exists()) {
                System.err.println("✗ 输入图片不存在: " + inputImagePath);
                return;
            }
            
            // 读取原始图片
            BufferedImage originalImage = ImageIO.read(inputFile);
            int originalWidth = originalImage.getWidth();
            int originalHeight = originalImage.getHeight();
            
        //    System.out.println("✓ 加载原始图片: " + inputImagePath + " (" + originalWidth + "x" + originalHeight + ")");
            
            // 计算中心裁剪区域
            int croppedWidth = (int) Math.max(1, Math.round(originalWidth * CAPTURE_HORIZONTAL_RATIO));
            int croppedHeight = (int) Math.max(1, Math.round(originalHeight * CAPTURE_VERTICAL_RATIO));
            int startX = (originalWidth - croppedWidth) / 2;
            int startY = (originalHeight - croppedHeight) / 2;
            
         //   System.out.println("✓ 裁剪参数 - 宽度: " + croppedWidth + ", 高度: " + croppedHeight + 
         //                    ", 起始位置: (" + startX + ", " + startY + ")");
            
            // 裁剪图片
            BufferedImage croppedImage = originalImage.getSubimage(startX, startY, croppedWidth, croppedHeight);
            
            // 创建输出目录
            File outputFile = new File(outputImagePath);
            File outputDir = outputFile.getParentFile();
            if (outputDir != null && !outputDir.exists()) {
                outputDir.mkdirs();
            }
            
            // 保存裁剪后的图片
            ImageIO.write(croppedImage, "png", outputFile);
         //   System.out.println("✓ 裁剪后的图片已保存: " + outputFile.getAbsolutePath() + 
         //                    " (" + croppedWidth + "x" + croppedHeight + ")");
            
        } catch (Exception ex) {
            System.err.println("✗ 裁剪图片时出现错误: " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }

    /**
     * 获取裁剪后的图片的 Data URI
     * @param imagePath 图片路径
     * @return Data URI 字符串（Base64 编码）
     */
    public static String getImageAsDataUri(String imagePath) throws Exception {
        try {
            File imageFile = new File(imagePath);
            
            // 如果找不到，尝试加上 img/ 前缀
            if (!imageFile.exists() && !imagePath.startsWith("img/")) {
                imageFile = new File("img/" + imagePath);
            }
            
            if (!imageFile.exists()) {
                System.err.println("✗ 图片不存在: " + imagePath);
                return "";
            }
            
            // 获取文件扩展名，确定 MIME 类型
            String fileName = imageFile.getName().toLowerCase();
            String mimeType = getMimeType(fileName);
            
            // 读取图片文件为字节数组
            byte[] imageBytes = Files.readAllBytes(imageFile.toPath());
            
            // 编码为 Base64
            String base64String = Base64.getEncoder().encodeToString(imageBytes);
            
            // 构建 Data URI
            String dataUri = "data:" + mimeType + ";base64," + base64String;
            
         //   System.out.println("✓ 已转换图片为 Data URI");
         //   System.out.println("  - 图片: " + imageFile.getAbsolutePath());
         //   System.out.println("  - MIME 类型: " + mimeType);
         //   System.out.println("  - Data URI 长度: " + dataUri.length() + " 字符 (" + 
         //                    String.format("%.2f", dataUri.length() / 1024.0) + " KB)");
            
            return dataUri;
            
        } catch (Exception ex) {
            System.err.println("✗ 转换图片为 Data URI 失败: " + ex.getMessage());
            ex.printStackTrace();
            throw ex;
        }
    }
    
    /**
     * 获取裁剪后的 Galgame 表格截图的 Data URI
     * 便捷方法：直接获取 img/galgame_table_cropped.png 的 Data URI
     * @return Data URI 字符串
     */
    public static String getGalgameCroppedImageAsDataUri() throws Exception {
        return getImageAsDataUri("img/galgame_table_cropped.png");
    }
    
    /**
     * 获取文件的 MIME 类型
     */
    private static String getMimeType(String fileName) {
        if (fileName.endsWith(".jpg") || fileName.endsWith(".jpeg")) {
            return "image/jpeg";
        } else if (fileName.endsWith(".png")) {
            return "image/png";
        } else if (fileName.endsWith(".gif")) {
            return "image/gif";
        } else if (fileName.endsWith(".bmp")) {
            return "image/bmp";
        } else if (fileName.endsWith(".webp")) {
            return "image/webp";
        } else {
            return "image/png"; // 默认为 PNG
        }
    }

    /**
     * 关闭浏览器
     */
    public static void closeBrowser() throws Exception {
        Runtime.getRuntime().exec("taskkill /IM msedge.exe /F");
    //    System.out.println("✓ 已关闭浏览器");
    }

    /**
     * 在系统中查找包含指定标题的窗口（部分匹配）。返回 HWND 或 null.
     */
    private static HWND findWindowByTitle(final String partialTitle) {
        final HWND[] result = new HWND[1];
        User32.INSTANCE.EnumWindows(new WinUser.WNDENUMPROC() {
            @Override
            public boolean callback(HWND hWnd, Pointer arg) {
                // 只查找可见窗口
                if (!User32.INSTANCE.IsWindowVisible(hWnd)) return true;
                // 获取窗口标题
                char[] windowText = new char[512];
                User32.INSTANCE.GetWindowText(hWnd, windowText, 512);
                String wText = new String(windowText).trim();
                // 截断第一个 NULL 字符后面的无效字符
                int nullIdx = wText.indexOf('\u0000');
                if (nullIdx > 0) wText = wText.substring(0, nullIdx);
                if (wText != null && wText.contains(partialTitle)) {
                    result[0] = hWnd;
                    return false; // 停止枚举
                }
                return true; // 继续枚举
            }
        }, null);
        return result[0];
    }

    /**
     * 完整处理流程：获取 API 数据 → 解析 JSON → 生成 HTML → 打开浏览器 → 截图 → 关闭浏览器
     */
    public static void process() {
        try {
            // 第一步：获取 API 数据
            System.out.println("第一步：获取 Galgame 数据...");
            String jsonResponse = fetchGalgamesJson();

            // 第二步：解析 JSON 数据
            System.out.println("第二步：解析 JSON 数据...");
            List<GalgameData> games = parseGalgamesWithGson(jsonResponse);

            if (games.isEmpty()) {
                System.err.println(" 未获取到任何游戏数据");
                return;
            }

            // 第三步：生成 HTML 表格
            System.out.println("第三步：生成 HTML 表格...");
            String htmlContent = generateHtmlTable(games);
            
            String htmlFilePath = "galgame_table.html";
            Files.write(Paths.get(htmlFilePath), htmlContent.getBytes(StandardCharsets.UTF_8));
            System.out.println("✓ HTML 表格已生成: " + new File(htmlFilePath).getAbsolutePath());

            // 第四步：打开浏览器
            System.out.println("\n第四步：打开浏览器...");
            openHtmlFile(htmlFilePath);

            // 等待 5 秒钟以确保浏览器页面加载完成后再触发截图流程
            try { Thread.sleep(5000); } catch (InterruptedException ignored) { }

            // 第五步：截图
            System.out.println("\n第五步：进行截图...");
            captureTableAsImage("galgame_table.png");

            // 第六步：裁剪图片（从中央截取高75%宽80%的部分）
            System.out.println("\n第六步：裁剪图片...");
            try {
                cropImageFromCenter("img/galgame_table.png", "img/galgame_table_cropped.png");
            } catch (Exception ex) {
                System.err.println("✗ 裁剪失败，但继续执行后续步骤");
            }

            // 第七步：获取裁剪后图片的 Data URI
            System.out.println("\n第七步：转换图片为 Data URI...");
            String imageDataUri = null;
            try {
                imageDataUri = getImageAsDataUri("img/galgame_table_cropped.png");
            } catch (Exception ex) {
                System.err.println("✗ 转换失败，但继续执行后续步骤");
            }

            // 第八步：关闭浏览器
            System.out.println("\n第八步：关闭浏览器...");
            closeBrowser();

            System.out.println("\n 所有操作完成！");
            System.out.println("   - HTML 文件: " + htmlFilePath);
            System.out.println("   - 原始截图文件: img/galgame_table.png");
            System.out.println("   - 裁剪后截图文件: img/galgame_table_cropped.png");
            if (imageDataUri != null && !imageDataUri.isEmpty()) {
                System.out.println("   - 图片 Data URI 已生成，可直接用于发送");
            }

        } catch (Exception e) {
            System.err.println(" 处理过程中出现错误: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 主方法：调用处理流程
     */
    // public static void main(String[] args) {
        
    //     process();
    // }
}
