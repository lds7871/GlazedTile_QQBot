package LDS.Person.websocket.config;

/**
 * WebSocket 配置常量类
 */
public class WebSocketConstants {

    // ==================== 本地 Spring 服务器 ====================
    public static final String LOCAL_WS_ENDPOINT = "/onebot";
    public static final String LOCAL_WS_URL = "ws://localhost:8090" + LOCAL_WS_ENDPOINT;

    // ==================== 远程 NapCat 服务器 ====================
    public static final String REMOTE_NAPCAT_URL = "ws://115.190.170.56:3001";

    // ==================== 心跳配置 ====================
    public static final int HEARTBEAT_INTERVAL_MS = 129500; // 129.5秒（NapCat 推荐 30秒）
    public static final int CONNECTION_TIMEOUT_MS = 100000; // 100秒连接超时

    // ==================== 消息类型 ====================
    public static final String ACTION_GET_STATUS = "get_status";
    public static final String ACTION_GET_VERSION = "get_version";
    public static final String ACTION_GET_LOGIN_INFO = "get_login_info";

    // ==================== 事件类型 ====================
    public static final String POST_TYPE_META_EVENT = "meta_event";
    public static final String META_EVENT_TYPE_HEARTBEAT = "heartbeat";

    // ==================== 日志前缀 ====================
    public static final String LOG_PREFIX_WEBSOCKET = "[WEBSOCKET]";
    public static final String LOG_PREFIX_REMOTE = "[REMOTE-WEBSOCKET]";
    public static final String LOG_PREFIX_HEARTBEAT = "[HEARTBEAT]";
    public static final String LOG_PREFIX_ONEBOT = "[ONEBOT]";
    public static final String LOG_PREFIX_INFO = "[INFO]";
    public static final String LOG_PREFIX_ERROR = "[ERROR]";
    public static final String LOG_PREFIX_SUCCESS = "[SUCCESS]";

    private WebSocketConstants() {
        throw new AssertionError("Cannot instantiate constants class");
    }
}
