package LDS.Person.util;


import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Alcy WebP 图片获取器：获取 http://t.alcy.cc/moez/ 重定向后的图片并保存
 * 使用 HttpURLConnection 处理重定向和文件下载
 *
 * 使用示例：
 * String finalUrl = getAndSaveImage("http://t.alcy.cc/moez/", "img/早安图片.webp");
 * System.out.println("图片获取完成: " + finalUrl);
 */
public class AlcyWebpGet {

    /**
     * 获取重定向后的图片URL并下载保存
     *
     * @param initialUrl 初始URL
     * @param savePath 保存路径
     * @return 最终的图片URL
     */
    public static String getAndSaveImage(String initialUrl, String savePath) throws Exception {
        // 跟随重定向获取最终URL
        String finalUrl = followSingleRedirect(initialUrl);
        //System.out.println(finalUrl);

        // 下载并保存文件
        downloadFile(finalUrl, savePath);

        return finalUrl;
    }

    /**
     * 下载文件并保存到指定路径
     *
     * @param fileUrl 文件URL
     * @param savePath 保存路径
     */
    public static void downloadFile(String fileUrl, String savePath) throws Exception {
        URL url = new URL(fileUrl);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();

        try {
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(30000); // 下载文件可能需要更长时间

            int statusCode = connection.getResponseCode();
            if (statusCode != 200) {
                throw new Exception("下载失败，HTTP状态码: " + statusCode);
            }

            // 确保img目录存在
            Path saveDir = Paths.get(savePath).getParent();
            if (saveDir != null) {
                Files.createDirectories(saveDir);
            }

            // 下载文件
            try (InputStream inputStream = connection.getInputStream();
                 FileOutputStream outputStream = new FileOutputStream(savePath)) {

                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }

            System.out.println("文件已保存到: " + savePath);

        } finally {
            connection.disconnect();
        }
    }

    /**
     * 只跟随重定向并返回最终URL
     *
     * @param urlString 初始 URL
     * @return 最终重定向后的 URL
     */
    public static String followSingleRedirect(String urlString) throws Exception {
        String currentUrl = urlString;
        int redirectCount = 0;
        final int MAX_REDIRECTS = 2; // 跟随两次重定向

        while (redirectCount < MAX_REDIRECTS) {
            URL url = new URL(currentUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();

            try {
                // 配置请求头
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
                connection.setRequestProperty("Accept",
                    "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
                connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);
                connection.setInstanceFollowRedirects(false); // 手动处理重定向

                int statusCode = connection.getResponseCode();

                // 检查是否是重定向状态码 (301, 302, 303, 307, 308)
                if (statusCode >= 300 && statusCode < 400) {
                    String location = connection.getHeaderField("Location");

                    if (location == null || location.isEmpty()) {
                        throw new Exception("重定向状态码但没有 Location 头");
                    }

                    // 处理相对 URL
                    if (location.startsWith("http://") || location.startsWith("https://")) {
                        currentUrl = location;
                    } else if (location.startsWith("//")) {
                        currentUrl = "http:" + location;
                    } else if (location.startsWith("/")) {
                        URL baseUrl = new URL(currentUrl);
                        currentUrl = baseUrl.getProtocol() + "://" + baseUrl.getHost();
                        if (baseUrl.getPort() != -1) {
                            currentUrl += ":" + baseUrl.getPort();
                        }
                        currentUrl += location;
                    } else {
                        // 相对路径，基于当前 URL
                        URL baseUrl = new URL(currentUrl);
                        String basePath = baseUrl.getProtocol() + "://" + baseUrl.getHost();
                        if (baseUrl.getPort() != -1) {
                            basePath += ":" + baseUrl.getPort();
                        }
                        String path = baseUrl.getPath();
                        if (!path.endsWith("/")) {
                            path = path.substring(0, path.lastIndexOf("/") + 1);
                        }
                        currentUrl = basePath + path + location;
                    }

                    redirectCount++;
                } else {
                    // 不是重定向，返回当前URL
                    return currentUrl;
                }

            } finally {
                connection.disconnect();
            }
        }

        // 返回最终URL
        return currentUrl;
    }

    /**
     * 主方法
     */
    public static void main(String[] args) {
        String targetUrl = "http://t.alcy.cc/moez/";
        String savePath = "img/早安图片.webp";

        try {
            // 获取重定向后的图片并保存
            String finalUrl = getAndSaveImage(targetUrl, savePath);
            System.out.println("图片获取完成: " + finalUrl);

        } catch (Exception e) {
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
