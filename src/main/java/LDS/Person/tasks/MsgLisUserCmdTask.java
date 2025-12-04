package LDS.Person.tasks;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import LDS.Person.config.NapCatTaskIsOpen;
import LDS.Person.tasks.MsgLisLogic.SteamSearchLogic;
import LDS.Person.tasks.MsgLisLogic.WikiSearchLogic;
import LDS.Person.tasks.MsgLisLogic.GalgameSearchLogic;
import LDS.Person.tasks.MsgLisLogic.OldGameGetLogic;
import LDS.Person.dto.request.SendGroupMessageRequest;

import java.io.InputStream;
import java.util.Properties;

/**
 * 用户指令消息监听处理器
 * 监听群聊消息，所有用户都可以使用的公共指令
 * 
 * 支持的指令:
 * - Steam->游戏名  : 搜索 Steam 游戏信息并发送
 * - Wiki->内容    : 搜索 Wikipedia 内容并发送
 * - gal->         : 搜索 Galgame 游戏并发送
 * - 今日怀旧->     : 发送最近添加的怀旧游戏信息
 */
@Component
@Slf4j
public class MsgLisUserCmdTask {

    @Autowired
    private SteamSearchLogic steamSearchLogic;

    @Autowired
    private WikiSearchLogic wikiSearchLogic;

    @Autowired
    private GalgameSearchLogic galgameSearchLogic;

    @Autowired
    private OldGameGetLogic oldGameGetLogic;

    @Autowired
    private RestTemplate restTemplate;

    private static String NCAT_API_BASE = "00";
    private static String NCAT_AUTH_TOKEN = "0000";

    // 静态初始化块：从 config.properties 读取配置
    static {
        Properties props = new Properties();

        try (InputStream input = MsgLisUserCmdTask.class.getClassLoader()
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
     * 处理接收到的 WebSocket 消息
     * 
     * @param message WebSocket 消息的 JSON 对象
     */
    public void handleMessage(JSONObject message) {
        // 检查该任务是否启用
        if (!NapCatTaskIsOpen.isMsgLisUserCmdTask) {
            // System.out.println("isMsgLisUserCmdTask未启用");
            return;
        }

        try {
            String postType = message.getString("post_type");

            // 只处理聊天消息
            if (!"message".equals(postType)) {
                return;
            }

            String messageType = message.getString("message_type");

            // 只处理群聊消息
            if (!"group".equals(messageType)) {
                return;
            }

            Long groupId = message.getLong("group_id");
            String rawMessage = message.getString("raw_message");

            if (rawMessage == null || rawMessage.isEmpty()) {
                return;
            }

            // 处理 Steam 搜索指令
            if (rawMessage.startsWith("Steam->") || rawMessage.startsWith("steam->")) {
                String gameName = rawMessage.substring(7).trim();
                if (!gameName.isEmpty()) {
                    steamSearchLogic.handleSteamSearch(groupId, gameName);
                }
            }

            // 处理 Wiki 搜索指令
            if (rawMessage.startsWith("Wiki->") || rawMessage.startsWith("wiki->")) {
                String wikiContent = rawMessage.substring(6).trim();
                if (!wikiContent.isEmpty()) {
                    wikiSearchLogic.handleWikiSearch(groupId, wikiContent);
                }
            }

            // 处理 Galgame 搜索指令（仅需要 "gal->" 就可以触发）
            if (rawMessage.startsWith("gal->") || rawMessage.startsWith("Gal->")) {
                galgameSearchLogic.handleGalgameSearch(groupId);
            }

            // 处理 今日怀旧 指令
            if (rawMessage.startsWith("今日怀旧->")) {
                oldGameGetLogic.handleOldGameGet(groupId);
            }

        } catch (Exception e) {
            log.error("处理用户指令异常", e);
        }
    }

    /**
     * 检查是否触发了用户指令
     * 
     * @param rawMessage 原始消息文本
     * @return 如果触发了用户指令返回 true，否则返回 false
     */
    public boolean isUserCommandTriggered(String rawMessage) {
        if (rawMessage == null || rawMessage.isEmpty()) {
            return false;
        }
        
        // 检查 Steam 指令
        if (rawMessage.startsWith("Steam->") || rawMessage.startsWith("steam->")) {
            String gameName = rawMessage.substring(7).trim();
            return !gameName.isEmpty();
        }

        // 检查 Wiki 指令
        if (rawMessage.startsWith("Wiki->") || rawMessage.startsWith("wiki->")) {
            String wikiContent = rawMessage.substring(6).trim();
            return !wikiContent.isEmpty();
        }

        // 检查 Galgame 指令
        if (rawMessage.startsWith("gal->") || rawMessage.startsWith("Gal->")) {
            return true;
        }

        // 检查 今日怀旧 指令
        if (rawMessage.startsWith("今日怀旧->")) {
            return true;
        }
        
        return false;
    }
}
