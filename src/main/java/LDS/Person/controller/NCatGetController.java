package LDS.Person.controller;

import LDS.Person.tasks.MsgLisATTask;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * NapCat Get 接口控制器
 * 用于调用 NapCat 服务的 GET 接口
 * 参考: http://0.0.0.0:3000
 */
@RestController
@RequestMapping("/api/ncat/get")
@Api(tags = "NapCat Get 接口", description = "NapCat Get 接口相关操作")
@Slf4j
@CrossOrigin(origins = "*", maxAge = 3600)
public class NCatGetController {

    @Autowired
    private RestTemplate restTemplate;

    private static String NCAT_API_BASE = "00";
    private static String NCAT_AUTH_TOKEN = "0000";


        // 静态初始化块：从 config.properties 读取配置
    static {
        Properties props = new Properties();

        try (InputStream input = MsgLisATTask.class.getClassLoader()
                .getResourceAsStream("config.properties")) {
            if (input != null) {
                props.load(input);
                NCAT_API_BASE = props.getProperty("NapCatApiBase", NCAT_API_BASE);
                NCAT_AUTH_TOKEN = props.getProperty("NapCatAuthToken", NCAT_AUTH_TOKEN);
            } else {
                System.out.println("[WARN] config.properties 没有找到, 会使用不可用的默认值");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] 无法读取 config.properties: " + e.getMessage());
        }
    }

    /**
     * 获取好友列表
     */
    @GetMapping("/friend-list")
    @ApiOperation(value = "获取好友列表", notes = "从 NapCat 服务获取好友列表")
    public ResponseEntity<Map<String, Object>> getFriendList() {
        try {
            log.info("开始获取好友列表...");

            // 调用 NapCat API
            String url = NCAT_API_BASE + "/get_friend_list";
            log.debug("调用 NapCat API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (apiResponse.getStatusCode() != HttpStatus.OK) {
                log.error("NapCat API 返回错误状态: {}", apiResponse.getStatusCode());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "API 请求失败，状态码: " + apiResponse.getStatusCode());
                return ResponseEntity.status(apiResponse.getStatusCode()).body(errorResponse);
            }

            // 解析响应
            JSONObject jsonResponse = JSON.parseObject(apiResponse.getBody());
            
            // 检查是否有错误
            if (jsonResponse.containsKey("status") && !jsonResponse.getString("status").equals("ok")) {
                String errorMsg = jsonResponse.getString("message");
                log.error("NapCat API 返回错误: {}", errorMsg);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", errorMsg);
                errorResponse.put("status", jsonResponse.getString("status"));
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // 提取好友数据
            JSONArray dataArray = jsonResponse.getJSONArray("data");
            List<Map<String, Object>> friends = new ArrayList<>();

            if (dataArray != null && dataArray.size() > 0) {
                for (int i = 0; i < dataArray.size(); i++) {
                    JSONObject friendObj = dataArray.getJSONObject(i);
                    Map<String, Object> friend = new HashMap<>();
                    
                    // 提取关键信息
                    friend.put("user_id", friendObj.get("user_id"));
                    friend.put("nickname", friendObj.get("nickname"));
                    friend.put("remark", friendObj.get("remark"));
                    friend.put("class_id", friendObj.get("class_id"));
                    friend.put("is_vip", friendObj.get("is_vip"));
                    friend.put("avatar", friendObj.get("avatar"));
                    
                    friends.add(friend);
                }
            }

            log.info("✅ 成功获取 {} 个好友", friends.size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "获取好友列表成功");
            response.put("total", friends.size());
            response.put("data", friends);
            response.put("raw_response", jsonResponse);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取好友列表异常", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 获取群组列表
     */
    @GetMapping("/group-list")
    @ApiOperation(value = "获取群组列表", notes = "从 NapCat 服务获取群组列表")
    public ResponseEntity<Map<String, Object>> getGroupList() {
        try {
            log.info("开始获取群组列表...");

            // 调用 NapCat API
            String url = NCAT_API_BASE + "/get_group_list";
            log.debug("调用 NapCat API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (apiResponse.getStatusCode() != HttpStatus.OK) {
                log.error("NapCat API 返回错误状态: {}", apiResponse.getStatusCode());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "API 请求失败，状态码: " + apiResponse.getStatusCode());
                return ResponseEntity.status(apiResponse.getStatusCode()).body(errorResponse);
            }

            // 解析响应
            JSONObject jsonResponse = JSON.parseObject(apiResponse.getBody());
            
            if (jsonResponse.containsKey("status") && !jsonResponse.getString("status").equals("ok")) {
                String errorMsg = jsonResponse.getString("message");
                log.error("NapCat API 返回错误: {}", errorMsg);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", errorMsg);
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // 提取群组数据
            JSONArray dataArray = jsonResponse.getJSONArray("data");
            List<Map<String, Object>> groups = new ArrayList<>();

            if (dataArray != null && dataArray.size() > 0) {
                for (int i = 0; i < dataArray.size(); i++) {
                    JSONObject groupObj = dataArray.getJSONObject(i);
                    Map<String, Object> group = new HashMap<>();
                    
                    group.put("group_id", groupObj.get("group_id"));
                    group.put("group_name", groupObj.get("group_name"));
                    group.put("member_count", groupObj.get("member_count"));
                    group.put("max_member_count", groupObj.get("max_member_count"));
                    group.put("owner_id", groupObj.get("owner_id"));
                    group.put("avatar", groupObj.get("avatar"));
                    group.put("create_time", groupObj.get("create_time"));
                    
                    groups.add(group);
                }
            }

            log.info("✅ 成功获取 {} 个群组", groups.size());

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "获取群组列表成功");
            response.put("total", groups.size());
            response.put("data", groups);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取群组列表异常", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }

    /**
     * 获取登录用户信息
     */
    @GetMapping("/login-info")
    @ApiOperation(value = "获取登录用户信息", notes = "从 NapCat 服务获取登录用户的基本信息")
    public ResponseEntity<Map<String, Object>> getLoginInfo() {
        try {
            log.info("开始获取登录用户信息...");

            // 调用 NapCat API
            String url = NCAT_API_BASE + "/get_login_info";
            log.debug("调用 NapCat API: {}", url);

            HttpHeaders headers = new HttpHeaders();
            headers.set("Authorization", "Bearer " + NCAT_AUTH_TOKEN);
            headers.set("Content-Type", "application/json");

            HttpEntity<Void> entity = new HttpEntity<>(headers);
            ResponseEntity<String> apiResponse = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (apiResponse.getStatusCode() != HttpStatus.OK) {
                log.error("NapCat API 返回错误状态: {}", apiResponse.getStatusCode());
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", "API 请求失败，状态码: " + apiResponse.getStatusCode());
                return ResponseEntity.status(apiResponse.getStatusCode()).body(errorResponse);
            }

            // 解析响应
            JSONObject jsonResponse = JSON.parseObject(apiResponse.getBody());
            
            if (jsonResponse.containsKey("status") && !jsonResponse.getString("status").equals("ok")) {
                String errorMsg = jsonResponse.getString("message");
                log.error("NapCat API 返回错误: {}", errorMsg);
                Map<String, Object> errorResponse = new HashMap<>();
                errorResponse.put("success", false);
                errorResponse.put("message", errorMsg);
                return ResponseEntity.badRequest().body(errorResponse);
            }

            // 提取用户数据
            JSONObject dataObj = jsonResponse.getJSONObject("data");
            Map<String, Object> userInfo = new HashMap<>();
            
            if (dataObj != null) {
                userInfo.put("user_id", dataObj.get("user_id"));
                userInfo.put("nickname", dataObj.get("nickname"));
                userInfo.put("avatar", dataObj.get("avatar"));
                userInfo.put("age", dataObj.get("age"));
                userInfo.put("gender", dataObj.get("gender"));
                userInfo.put("sign", dataObj.get("sign"));
            }

            log.info("✅ 成功获取登录用户信息");

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "获取登录用户信息成功");
            response.put("data", userInfo);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("获取登录用户信息异常", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "服务器错误: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse);
        }
    }
}
