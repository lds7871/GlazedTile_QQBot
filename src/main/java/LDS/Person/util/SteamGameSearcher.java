package LDS.Person.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Properties;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.json.JSONObject;
import org.json.JSONArray;

/**
 * Steam 游戏搜索工具
 * 用于获取 https://store.steampowered.com/search/?term=游戏名 的内容
 * 以及从 Steam API 获取游戏详细信息
 *  
 */
public class SteamGameSearcher {

    private static final String STEAM_SEARCH_URL = "https://store.steampowered.com/search/?term=";
    private static final String STEAM_API_URL = "https://store.steampowered.com/api/appdetails?appids=";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36";

    private static boolean PROXY_IS_OPEN = false;
    private static String PROXY_HOST = "127.0.0.1";
    private static int PROXY_PORT = 32110;

    // 静态初始化块：从 config.properties 读取代理配置
    static {
        Properties props = new Properties();
        try (InputStream input = SteamGameSearcher.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                PROXY_IS_OPEN = Boolean.parseBoolean(props.getProperty("proxy.is.open", "false"));
                PROXY_HOST = props.getProperty("proxy.host", PROXY_HOST);
                PROXY_PORT = Integer.parseInt(props.getProperty("proxy.port", "8080"));
                
                if (PROXY_IS_OPEN) {
                    System.out.println("[CONFIG] Steam 代理已启用" );
                }
            } else {
                System.out.println("[WARN] config.properties 没有找到, 使用默认配置");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] 无法读取 config.properties: " + e.getMessage());
        }
    }

    /**
     * 从 Steam 游戏链接中提取 App ID
     * 例如: https://store.steampowered.com/app/1174180/Red_Dead_Redemption_2/ -> 1174180
     */
    public static String extractAppId(String link) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("/app/(\\d+)");
        java.util.regex.Matcher matcher = pattern.matcher(link);
        
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 从 Steam API 获取游戏详细信息
     */
    public static String getGameDetails(String appId) {
        try {
            String apiUrl = STEAM_API_URL + appId;

            var connection = Jsoup.connect(apiUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10000)
                    .ignoreContentType(true);

            // 应用代理配置
            if (PROXY_IS_OPEN) {
                connection.proxy(PROXY_HOST, PROXY_PORT);
            }

           // 使用 execute() 获取原始响应体，避免 Jsoup 解析破坏 JSON
            var response = connection.execute();
            String responseText = response.body();


            return responseText;

        } catch (IOException e) {
            System.err.println("获取游戏详情失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 从 API 响应中提取关键信息并返回文本
     */
    public static String getSimplifiedGameInfo(String jsonResponse, String appId) {
        StringBuilder sb = new StringBuilder();
        
        try {
            JSONObject responseObj = new JSONObject(jsonResponse);
            
            if (!responseObj.has(appId)) {
                return "未找到游戏数据";
            }
            
            JSONObject gameObj = responseObj.getJSONObject(appId);
            
            if (!gameObj.getBoolean("success")) {
                return "游戏数据获取失败";
            }
            
            JSONObject data = gameObj.getJSONObject("data");
            
            // 提取关键信息
            sb.append("游戏信息:\n");
            sb.append("  游戏名称: ").append(data.getString("name")).append("\n");
            sb.append("  是否免费: ").append(data.getBoolean("is_free") ? "是" : "否").append("\n");
            
            // 开发商
            JSONArray developers = data.getJSONArray("developers");
            sb.append("开发商:\n");
            for (int i = 0; i < developers.length(); i++) {
                sb.append("  ").append(developers.getString(i)).append("\n");
            }
            
            // 发行商
            JSONArray publishers = data.getJSONArray("publishers");
            sb.append("发行商:\n");
            for (int i = 0; i < publishers.length(); i++) {
                sb.append("  ").append(publishers.getString(i)).append("\n");
            }
            
            // 价格信息
            if (data.has("price_overview")) {
                JSONObject price = data.getJSONObject("price_overview");
                int discount = price.getInt("discount_percent");
                String finalPrice = price.getString("final_formatted");
                sb.append("价格信息:\n");
                sb.append("  当前价格: ").append(finalPrice).append("\n");
                if (discount > 0) {
                    sb.append("  折扣: ").append(discount).append("% OFF\n");
                } else {
                    sb.append("  当前无折扣\n");
                }
            }
            
            // 推荐数
            if (data.has("recommendations")) {
                JSONObject recommendations = data.getJSONObject("recommendations");
                int total = recommendations.getInt("total");
                sb.append("用户评价:\n");
                sb.append("  置信评论: ").append(String.format("%,d", total)).append(" \n");
            }
            
            // 发布日期
            if (data.has("release_date")) {
                JSONObject releaseDate = data.getJSONObject("release_date");
                boolean comingSoon = releaseDate.getBoolean("coming_soon");
                String date = releaseDate.getString("date");
                sb.append("发布信息:\n");
                if (comingSoon) {
                    sb.append("  状态: 即将发布\n");
                    sb.append("  预计日期: ").append(date).append("\n");
                } else {
                    sb.append("  状态: 已发布\n");
                    sb.append("  发布日期: ").append(date).append("\n");
                }
            }
            
        } catch (Exception e) {
            sb.append("解析 JSON 失败: ").append(e.getMessage()).append("\n");
        }
        return sb.toString();
    }

    /**
     * 从 API 响应中提取头部图像 URL
     */
    public static String getGameHeaderImage(String jsonResponse, String appId) {
        try {
            JSONObject responseObj = new JSONObject(jsonResponse);
            
            if (!responseObj.has(appId)) {
                return "";
            }
            
            JSONObject gameObj = responseObj.getJSONObject(appId);
            
            if (!gameObj.getBoolean("success")) {
                return "";
            }
            
            JSONObject data = gameObj.getJSONObject("data");
            
            if (data.has("header_image")) {
                return data.getString("header_image");
            }
            
        } catch (Exception e) {
            System.err.println("提取头部图像失败: " + e.getMessage());
        }
        return "";
    }

    /**
     * 根据游戏名搜索并返回游戏信息，同时返回 API 响应和 App ID
     * 返回格式: [gameInfo, jsonResponse, appId]
     */
    public static String[] searchAndGetGameInfoWithImage(String gameName) {
        try {
            String encodedGameName = URLEncoder.encode(gameName, StandardCharsets.UTF_8);
            String searchUrl = STEAM_SEARCH_URL + encodedGameName + "&ignore_preferences=1&supportedlang=schinese%2Ctchinese&ndl=1";

            var jsoupConnection = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .cookie("steamLoginSecure", "76561198912395789%7C%7CeyAidHlwIjogIkpXVCIsICJhbGciOiAiRWREU0EiIH0.eyAiaXNzIjogInI6MDAxOV8yNzE5QjEwQl82RTlFQSIsICJzdWIiOiAiNzY1NjExOTg5MTIzOTU3ODkiLCAiYXVkIjogWyAid2ViOnN0b3JlIiBdLCAiZXhwIjogMTc2NDIwNzMwOCwgIm5iZiI6IDE3NTU0ODAzNTEsICJpYXQiOiAxNzY0MTIwMzUxLCAianRpIjogIjAwMENfMjc0RkREOEVfNzcxRTEiLCAib2F0IjogMTc2MDY2MzYwNSwgInJ0X2V4cCI6IDE3NzkwOTU1NTksICJwZXIiOiAwLCAiaXBfc3ViamVjdCI6ICIxNTYuMjI5LjE2My4xNjYiLCAiaXBfY29uZmlybWVyIjogIjIyMS4yMzcuMTEzLjE1MiIgfQ.Njp0GY5ePANkI5ctvYdCGxa6mZcUXWQp_U-0CC4w00Qw_QGvlm-HkuqUZkgHDBHq8t9tZfRbAg2RmUZ6tfwjCQ")
                    .timeout(10000);

            // 应用代理配置
            if (PROXY_IS_OPEN) {
                jsoupConnection.proxy(PROXY_HOST, PROXY_PORT);
            }

            Document doc = jsoupConnection.get();

            var searchResults = doc.select("a.search_result_row");
            
            if (searchResults.size() > 0) {
                Element firstResult = searchResults.get(0);
                String link = firstResult.attr("href");
                String appId = extractAppId(link);
                
                if (appId != null) {
                    String apiResponse = getGameDetails(appId);
                    if (apiResponse != null) {
                        String gameInfo = getSimplifiedGameInfo(apiResponse, appId);
                        return new String[]{gameInfo, apiResponse, appId};
                    }
                }
            }
            return new String[]{"未找到相关游戏", "", ""};
        } catch (IOException e) {
            return new String[]{"搜索失败: " + e.getMessage(), "", ""};
        }
    }

    /**
     * 搜索第一个游戏结果并获取详细信息
     */
    public static String searchFirstGameAndGetDetails(String gameName) {
        try {
            String encodedGameName = URLEncoder.encode(gameName, StandardCharsets.UTF_8);
            String searchUrl = STEAM_SEARCH_URL + encodedGameName;
            System.out.println("搜索游戏: " + gameName);

            var jsoupConnection = Jsoup.connect(searchUrl)
                    .userAgent(USER_AGENT)
                    .timeout(10000);

            // 应用代理配置
            if (PROXY_IS_OPEN) {
                jsoupConnection.proxy(PROXY_HOST, PROXY_PORT);
            }

            Document doc = jsoupConnection.get();

            Element firstResult = doc.selectFirst("a.search_result_row");

            if (firstResult == null) {
                System.out.println("未找到相关游戏");
                return null;
            }

            String link = firstResult.attr("href");

            String appId = extractAppId(link);
            
            if (appId == null) {
                return null;
            }

            System.out.println("App ID: " + appId);

            return getGameDetails(appId);

        } catch (IOException e) {
            System.err.println("搜索失败: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    // public static void main(String[] args) {
    //     String gameName = "求生之路2";

    //     if (args.length > 0) {
    //         gameName = args[0];
    //     }

    //     // 获取游戏信息、API 响应和 App ID
    //     String[] result = searchAndGetGameInfoWithImage(gameName);
    //     String gameInfo = result[0];
    //     String jsonResponse = result[1];
    //     String appId = result[2];
        
    //     // 输出游戏信息
    //     System.out.println(gameInfo);
        
    //     // 获取并输出头部图像 URL
    //     if (!jsonResponse.isEmpty() && !appId.isEmpty()) {
    //         String imageUrl = getGameHeaderImage(jsonResponse, appId);
    //         if (!imageUrl.isEmpty()) {
    //             //输出URL
    //             System.out.println(imageUrl);
    //         }
    //     }
    // } 
}
