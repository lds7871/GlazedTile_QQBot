package LDS.Person.util;

import LDS.Person.config.ConfigManager;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;

public class WikipediaSearcher {

    // 使用ConfigManager获取配置，避免重复加载配置文件，提高性能
    private static final ConfigManager configManager = ConfigManager.getInstance();

    /**
     * 初始化 SSL 上下文，禁用证书验证（用于代理环境）
     */
    static {
        try {
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new javax.net.ssl.TrustManager[]{
                    new javax.net.ssl.X509TrustManager() {
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }
                        public void checkClientTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                        public void checkServerTrusted(java.security.cert.X509Certificate[] certs, String authType) {}
                    }
            }, new java.security.SecureRandom());
            HttpsURLConnection.setDefaultSSLSocketFactory(sslContext.getSocketFactory());
            HttpsURLConnection.setDefaultHostnameVerifier((hostname, session) -> true);
        } catch (Exception e) {
            System.err.println("SSL 初始化失败: " + e.getMessage());
        }
    }

    /**
     * 处理 HTTP 响应流，支持 gzip 压缩
     */
    private static String readResponse(HttpURLConnection connection) throws IOException {
        String encoding = connection.getContentEncoding();
        java.io.InputStream inputStream = connection.getInputStream();
        
        // 如果是 gzip 压缩，则使用 GZIPInputStream 解压
        if ("gzip".equalsIgnoreCase(encoding)) {
            inputStream = new java.util.zip.GZIPInputStream(inputStream);
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        return response.toString();
    }

    /**
     * 搜索维基百科
     *
     * @param keyword 搜索关键词
     * @param limit   返回结果数量
     * String result = executeSearch(keyword, limit);
     * System.out.println(result);
     * @return 搜索结果的JSON字符串数组[keyword, titles[], descriptions[], urls[]]
     */
    public static String searchWikipedia(String keyword, int limit) {
        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8);
            String url = "https://zh.wikipedia.org/w/api.php?action=opensearch&search=" + encodedKeyword 
                    + "&limit=" + limit + "&namespace=0&format=json";
            
            // 根据配置决定是否使用代理
            String result;
            if (configManager.isProxyOpen()) {
                // 使用 HTTP 代理
                result = searchWithHTTPProxy(url, configManager.getProxyHost(), configManager.getProxyPort());
                if (result.startsWith("Error:")) {
                    System.err.println("HTTP 代理失败，尝试直接连接...");
                    result = searchDirect(url);
                }
            } else {
                // 直接连接
                result = searchDirect(url);
            }
            
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * 使用 HTTP 代理搜索 Wikipedia
     */
    private static String searchWithHTTPProxy(String url, String proxyHost, int proxyPort) {
        try {
            // 配置 HTTP 代理
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(proxy);
            connection.setRequestMethod("GET");
            
            // 设置请求头，模拟浏览器
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("Referer", "https://zh.wikipedia.org/");
            
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            
            StringBuilder response = new StringBuilder();
            
            try {
                if (responseCode >= 200 && responseCode < 300) {
                    response.append(readResponse(connection));
                } else {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line).append("\n");
                        }
                    }
                }
            } finally {
                connection.disconnect();
            }
            
            String result = response.toString().trim();
            
            if (responseCode != 200) {
                return "Error (HTTP Proxy " + proxyHost + ":" + proxyPort + "): HTTP " + responseCode;
            }
            
            if (!result.startsWith("[")) {
                return "Error (HTTP Proxy " + proxyHost + ":" + proxyPort + "): Invalid JSON format";
            }
            
            System.out.println("[✓] HTTP 代理 " + proxyHost + ":" + proxyPort + " 连接成功");
            return result;
        } catch (Exception e) {
            return "Error (HTTP Proxy " + proxyHost + ":" + proxyPort + "): " + e.getMessage();
        }
    }
    
    /**
     * 直接连接搜索 Wikipedia（不使用代理）
     */
    private static String searchDirect(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            
            // 设置请求头，模拟浏览器
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("Referer", "https://zh.wikipedia.org/");
            
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            
            StringBuilder response = new StringBuilder();
            
            try {
                if (responseCode >= 200 && responseCode < 300) {
                    response.append(readResponse(connection));
                } else {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line).append("\n");
                        }
                    }
                }
            } finally {
                connection.disconnect();
            }
            
            String result = response.toString().trim();
            
            if (responseCode != 200) {
                return "Error (Direct): HTTP " + responseCode;
            }
            
            if (!result.startsWith("[")) {
                return "Error (Direct): Invalid JSON format";
            }
            
            System.out.println("✓ 直接连接成功");
            return result;
        } catch (Exception e) {
            return "Error (Direct): " + e.getMessage();
        }
    }

    /**
     * 从URL中提取标题并进行URL解码
     *
     * @param url 维基百科文章URL
     * @return 解码后的标题
     */
    public static String extractTitleFromUrl(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }
        String prefix = "https://zh.wikipedia.org/wiki/";
        if (url.startsWith(prefix)) {
            String title = url.substring(prefix.length());
            // 对提取的标题进行URL解码
            return decodeUrlEncoding(title);
        }
        return null;
    }

    /**
     * 获取文章详细信息
     *
     * @param title 文章标题
     * @return JSON格式的文章详细信息
     */
    public static String getArticleDetail(String title) {
        try {
            String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
            String url = "https://zh.wikipedia.org/w/api.php?action=query&prop=extracts&exintro&explaintext&titles=" + encodedTitle + "&format=json";
            
            // 根据配置决定是否使用代理
            String result;
            if (configManager.isProxyOpen()) {
                // 使用 HTTP 代理
                result = getArticleDetailWithHTTPProxy(url, configManager.getProxyHost(), configManager.getProxyPort());
                if (result.startsWith("Error:")) {
                    System.err.println("HTTP 代理获取文章失败，尝试直接连接...");
                    result = getArticleDetailDirect(url);
                }
            } else {
                // 直接连接
                result = getArticleDetailDirect(url);
            }
            
            return result;
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
    
    /**
     * 使用 HTTP 代理获取文章详细信息
     */
    private static String getArticleDetailWithHTTPProxy(String url, String proxyHost, int proxyPort) {
        try {
            Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection(proxy);
            connection.setRequestMethod("GET");
            
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("Referer", "https://zh.wikipedia.org/");
            
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            
            StringBuilder response = new StringBuilder();
            
            try {
                if (responseCode >= 200 && responseCode < 300) {
                    response.append(readResponse(connection));
                } else {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line).append("\n");
                        }
                    }
                }
            } finally {
                connection.disconnect();
            }
            
            String result = response.toString().trim();
            
            if (responseCode != 200) {
                return "Error (HTTP Proxy " + proxyHost + ":" + proxyPort + "): HTTP " + responseCode;
            }
            
            System.out.println("[✓] HTTP 代理 " + proxyHost + ":" + proxyPort + " 获取文章成功");
            return result;
        } catch (Exception e) {
            return "Error (HTTP Proxy " + proxyHost + ":" + proxyPort + "): " + e.getMessage();
        }
    }
    
    /**
     * 直接连接获取文章详细信息（不使用代理）
     */
    private static String getArticleDetailDirect(String url) {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8");
            connection.setRequestProperty("Connection", "close");
            connection.setRequestProperty("Referer", "https://zh.wikipedia.org/");
            
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(15000);
            
            int responseCode = connection.getResponseCode();
            
            StringBuilder response = new StringBuilder();
            
            try {
                if (responseCode >= 200 && responseCode < 300) {
                    response.append(readResponse(connection));
                } else {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getErrorStream(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            response.append(line).append("\n");
                        }
                    }
                }
            } finally {
                connection.disconnect();
            }
            
            String result = response.toString().trim();
            
            if (responseCode != 200) {
                return "Error (Direct): HTTP " + responseCode;
            }
            
            System.out.println("✓ 直接连接获取文章成功");
            return result;
        } catch (Exception e) {
            return "Error (Direct): " + e.getMessage();
        }
    }

    /**
     * 解析维基百科搜索结果
     *
     * @param jsonResponse OpenSearch API返回的JSON字符串
     * @return 格式化的结果文本
     */
    public static String parseWikipediaResults(String jsonResponse) {
        try {
            // 清理响应
            jsonResponse = jsonResponse.trim();
            
            // 检查响应是否有效
            if (jsonResponse.isEmpty() || jsonResponse.equals("[]")) {
                return "没有找到搜索结果";
            }
            
            if (!jsonResponse.startsWith("[")) {
                return "无效的响应格式，API可能返回了错误信息";
            }
            
            JSONArray results = new JSONArray(jsonResponse);
            
            if (results.length() < 4) {
                return "无效的响应格式：结果数据不完整";
            }
            
            String keyword = results.getString(0);
            JSONArray titles = results.getJSONArray(1);
            // JSONArray descriptions = results.getJSONArray(2); // 暂不使用
            JSONArray urls = results.getJSONArray(3);
            
            // 检查是否有搜索结果
            if (titles.length() == 0) {
                return "未找到与 \"" + keyword + "\" 相关的结果";
            }
            
            StringBuilder output = new StringBuilder();
            for (int i = 0; i < titles.length(); i++) {
                output.append(String.format("%d. %s\n", i + 1, titles.getString(i)));
                if (i < urls.length() && !urls.getString(i).isEmpty()) {
                    output.append("   链接: ").append(urls.getString(i));
                }
                output.append("\n");
            }
            
            return output.toString();
        } catch (Exception e) {
            return "解析错误: " + e.getMessage() + "\n提示: 可能是API返回了HTML错误页面而非JSON数据";
        }
    }

    /**
     * 将Unicode转义序列转换为中文
     *
     * @param json 包含Unicode转义序列的JSON字符串
     * @return 转换后的JSON字符串
     */
    public static String decodeUnicode(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        
        StringBuilder result = new StringBuilder();
        int i = 0;
        while (i < json.length()) {
            if (i < json.length() - 5 && json.charAt(i) == '\\' && json.charAt(i + 1) == 'u') {
                try {
                    String hex = json.substring(i + 2, i + 6);
                    int codePoint = Integer.parseInt(hex, 16);
                    result.append((char) codePoint);
                    i += 6;
                } catch (NumberFormatException e) {
                    result.append(json.charAt(i));
                    i++;
                }
            } else {
                result.append(json.charAt(i));
                i++;
            }
        }
        return result.toString();
    }

    /**
     * 解码URL中的百分比编码（%XX）为中文
     *
     * @param url 包含URL编码的URL字符串
     * @return 解码后的URL字符串
     */
    public static String decodeUrlEncoding(String url) {
        try {
            return URLDecoder.decode(url, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return url;
        }
    }

    /**
     * 从JSON中提取title和extract字段
     *
     * @param jsonResponse 完整的JSON响应
     * @return 包含title和extract的格式化字符串
     */
    public static String extractTitleAndExtract(String jsonResponse) {
        try {
            // 验证输入
            if (jsonResponse == null || jsonResponse.isEmpty()) {
                return "无有效的JSON响应";
            }
            
            // 清理响应
            jsonResponse = jsonResponse.trim();
            
            // 检查是否为有效的JSON对象
            if (!jsonResponse.startsWith("{")) {
                return "无效的JSON格式: " + jsonResponse.substring(0, Math.min(50, jsonResponse.length()));
            }
            
            JSONObject json = new JSONObject(jsonResponse);
            
            // 检查必需的字段
            if (!json.has("query")) {
                return "API响应缺少 'query' 字段";
            }
            
            JSONObject query = json.getJSONObject("query");
            
            if (!query.has("pages")) {
                return "API响应缺少 'pages' 字段";
            }
            
            JSONObject pages = query.getJSONObject("pages");
            
            // 检查是否有页面
            if (pages.length() == 0) {
                return "未找到页面信息";
            }
            
            StringBuilder result = new StringBuilder();
            for (String pageId : pages.keySet()) {
                JSONObject page = pages.getJSONObject(pageId);
                String title = page.optString("title", "");
                String extract = page.optString("extract", "");
                
                // 转换Unicode
                title = decodeUnicode(title);
                extract = decodeUnicode(extract);
                if(extract.isEmpty()) {
                    extract = "无内容摘要,wiki收录混淆会导致此结果，请尝试繁体中文或英文搜索";
                }
                
                // 处理extract的换行，每行开头缩进两格
                String[] lines = extract.split("\n");
                StringBuilder formattedExtract = new StringBuilder();
                for (String line : lines) {
                    formattedExtract.append("  ").append(line).append("\n");
                }
                
                result.append("标题: ").append(title).append("\n");
                result.append("内容:\n");
                result.append(formattedExtract);
            }
            
            return result.toString();
        } catch (org.json.JSONException e) {
            return "JSON解析错误: " + e.getMessage() + "\n响应可能不是有效的JSON格式";
        } catch (Exception e) {
            return "提取失败: " + e.getMessage();
        }
    }

    /**
     * 执行维基百科搜索并返回格式化结果
     *
     * @param keyword 搜索关键词
     * @param limit 返回结果数量
     * @return 格式化后的搜索结果和详细信息
     */
    public static String executeSearch(String keyword, int limit) {
        StringBuilder output = new StringBuilder();
        
        String jsonResponse = searchWikipedia(keyword, limit);
        
        // 验证搜索响应的有效性
        if (jsonResponse == null || jsonResponse.isEmpty() || jsonResponse.equals("[]")) {
            return "没有找到搜索结果";
        }
        
        if (!jsonResponse.startsWith("[")) {
            return "搜索请求失败: " + jsonResponse;
        }
        
        // 提取第一个结果的URL并获取详细信息
        try {
            JSONArray results = new JSONArray(jsonResponse);
            if (results.length() >= 4) {
                JSONArray urls = results.getJSONArray(3);
                if (urls.length() > 0) {
                    String firstUrl = urls.getString(0);
                    String title = extractTitleFromUrl(firstUrl);
                    
                    if (title != null) {
                        String detailJson = getArticleDetail(title);
                        
                        // 验证详细信息响应
                        if (detailJson == null || detailJson.isEmpty()) {
                            output.append("获取详细信息失败: 空响应");
                        } else if (detailJson.startsWith("Error:")) {
                            output.append(detailJson);
                        } else if (!detailJson.startsWith("{")) {
                            output.append("获取详细信息失败: 无效的响应格式\n");
                            output.append("响应内容: ").append(detailJson.substring(0, Math.min(100, detailJson.length()))).append("\n");
                        } else {
                            String extracted = extractTitleAndExtract(detailJson);
                            output.append(extracted).append("\n");
                        }
                        
                        // 输出近似的词条
                        output.append("=== 近似的词条 ===\n");
                        JSONArray titles = results.getJSONArray(1);
                        for (int i = 0; i < titles.length(); i++) {
                            output.append(titles.getString(i)).append("\n");
                            // if (i < urls.length() && !urls.getString(i).isEmpty()) {
                            //     String decodedUrl = decodeUrlEncoding(urls.getString(i));
                            //     output.append("   ").append(decodedUrl).append("\n");
                            // }
                        }
                    } else {
                        output.append("无法提取文章标题");
                    }
                } else {
                    output.append("没有找到搜索结果");
                }
            } else {
                output.append("搜索响应格式无效");
            }
        } catch (Exception e) {
            output.append("获取详细信息失败: ").append(e.getMessage());
        }
        
        return output.toString();
    }

    /**
     * 主入口方法
     *
     * @param args 命令行参数
     */
    // public static void main(String[] args) {
    //     String keyword = "DOTA";
    //     int limit = 5;

    //     if (args.length > 0) {
    //         keyword = args[0];
    //     }
    //     if (args.length > 1) {
    //         try {
    //             limit = Integer.parseInt(args[1]);
    //         } catch (NumberFormatException e) {
    //             System.err.println("Invalid limit parameter, using default: 5");
    //         }
    //     }

    //     // 执行搜索并获取结果
    //     String result = executeSearch(keyword, limit);
    //     System.out.println(result);
    // }
}
