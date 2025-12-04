package LDS.Person.websocket;

import java.io.InputStream;
import java.net.URI;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
* OneBot WebSocket 客户端 - 连接到 Spring 服务器并定期发送心跳
* 支持两种模式：
* 1. 作为独立客户端连接到远程服务器
* 2. 连接到本地 Spring 应用的 WebSocket 端点
*    NapCat (ws://115.190.170.56:3001) 
*        ↓
*    RemoteOneBotClient (客户端连接到 NapCat)
*        ↓
*    RemoteWebSocketClientHandler (接收消息)
*        ↓
*    OneBotWebSocketHandler.broadcastToClients() (转发给 Spring 连接的所有客户端)
*        ↓
*    Spring WebSocket Server (ws://localhost:8090/onebot)
*        ↓
*    控制台输出（显示所有非心跳消息）
*/
public class OneBotWebSocketClient {

   // 本地 Spring 应用的 WebSocket 端点（推荐）
   private static final String WS_URL_LOCAL;
   
   // 远程 NapCat 服务器的 WebSocket 端点
   private static final String WS_URL_REMOTE;
   
   // 选择要连接的服务器：LOCAL 或 REMOTE
   private static final String WS_URL;
   
   // WebSocket 认证令牌
   private static final String TOKEN;
   
   // 心跳间隔（毫秒）
   private static final int HEARTBEAT_INTERVAL = 2950; // 30秒

   // 静态初始化块：从 config.properties 读取配置
   static {
       Properties props = new Properties();
       String wsUrlLocal = "000";  // 默认值
       String wsUrlRemote = "0000";   // 默认值
       String token = "0000";                  // 默认值
       String wsSelect = "0000";                          // 默认值

       try (InputStream input = OneBotWebSocketClient.class.getClassLoader()
               .getResourceAsStream("config.properties")) {
           if (input != null) {
               props.load(input);
               wsUrlLocal = props.getProperty("WS_URL_LOCAL", wsUrlLocal);
               wsUrlRemote = props.getProperty("WS_URL_REMOTE", wsUrlRemote);
               token = props.getProperty("WS_TOKEN", token);
               wsSelect = props.getProperty("WS_URL_SELECT", wsSelect);
               System.out.println("[INFO] 已从 config.properties 加载 WebSocket 配置");
           } else {
               System.out.println("[WARN] config.properties 未找到，使用默认配置");
           }
       } catch (Exception e) {
           System.err.println("[ERROR] 加载 config.properties 失败: " + e.getMessage());
       }

       // 根据配置选择 URL
       String selectedUrl = "LOCAL".equalsIgnoreCase(wsSelect) ? wsUrlLocal : wsUrlRemote;

       WS_URL_LOCAL = wsUrlLocal;
       WS_URL_REMOTE = wsUrlRemote;
       WS_URL = selectedUrl;
       TOKEN = token;

       System.out.println("[INFO] WebSocket 配置已加载: WS_URL=" + WS_URL);
   }

   private WebSocketClientHandler wsHandler;
   private Timer heartbeatTimer;
   private boolean isConnected = false;

   public OneBotWebSocketClient() {
       this.wsHandler = null;
   }

   /**
    * 启动 WebSocket 连接并启动心跳
    */
   public void start() throws Exception {
       try {
           // 创建并连接
           //String url = WS_URL + "?token=" + TOKEN;
           String url = WS_URL;
           System.out.println("[INFO] 正在连接到 WebSocket: " + WS_URL);
           System.out.println("[INFO] Token: " + TOKEN);

           // 创建 URI
           URI uri = new URI(url);

           // 创建处理器并建立连接
           this.wsHandler = new WebSocketClientHandler(uri);
           wsHandler.connect();

           // 等待连接建立 (最多等待10秒)
           if (wsHandler.waitForConnection(10000)) {
               isConnected = true;
               System.out.println("[SUCCESS] WebSocket 连接成功!");

               // 启动心跳任务
               startHeartbeat();
           } else {
               System.err.println("[ERROR] 连接超时");
               throw new Exception("WebSocket 连接超时");
           }

       } catch (Exception e) {
           System.err.println("[ERROR] 连接失败: " + e.getMessage());
           e.printStackTrace();
           throw e;
       }
   }

   /**
    * 启动心跳任务
    */
   private void startHeartbeat() {
       heartbeatTimer = new Timer("WebSocket-Heartbeat", true);
       heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
           @Override
           public void run() {
               try {
                   if (isConnected && wsHandler.isConnected()) {
                       // 发送 OneBot 11 标准的 get_status 心跳消息
                       // 参考: https://napneko.github.io/onebot/network
                       long echo = System.currentTimeMillis();
                       String heartbeatMsg = "{\"action\":\"get_status\",\"params\":{},\"echo\":" + echo + "}";
                       wsHandler.sendMessage(heartbeatMsg);
                       System.out.println("[HEARTBEAT] 发送心跳包: " + heartbeatMsg);
                   }
               } catch (Exception e) {
                   System.err.println("[ERROR] 发送心跳失败: " + e.getMessage());
               }
           }
       }, HEARTBEAT_INTERVAL, HEARTBEAT_INTERVAL);
   }

   /**
    * 停止连接和心跳
    */
   public void stop() {
       if (heartbeatTimer != null) {
           heartbeatTimer.cancel();
       }
       if (wsHandler != null) {
           try {
               wsHandler.close();
               isConnected = false;
               System.out.println("[INFO] WebSocket 连接已关闭");
           } catch (Exception e) {
               System.err.println("[ERROR] 关闭连接失败: " + e.getMessage());
           }
       }
   }

   public static void main(String[] args) {
       OneBotWebSocketClient client = new OneBotWebSocketClient();
       try {
           // 启动客户端
           client.start();

           // 保持连接
           System.out.println("[INFO] 按 Ctrl+C 退出程序...");
           Thread.currentThread().join();

       } catch (Exception e) {
           System.err.println("[ERROR] 程序运行出错: " + e.getMessage());
           e.printStackTrace();
       } finally {
           client.stop();
       }
   }
}
