package LDS.Person.config;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * HttpClient工厂类 - 单例模式
 * 提供配置好的HttpClient实例，避免重复创建
 * 复用HttpClient可以提高性能并减少资源消耗
 */
public class HttpClientFactory {
    
    private static volatile HttpClient instance;
    
    /**
     * 私有构造函数，防止实例化
     */
    private HttpClientFactory() {
    }
    
    /**
     * 获取共享的HttpClient实例（双重检查锁定）
     * HttpClient是线程安全的，可以在多个线程间共享使用
     * @return 配置好的HttpClient实例
     */
    public static HttpClient getInstance() {
        if (instance == null) {
            synchronized (HttpClientFactory.class) {
                if (instance == null) {
                    instance = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(20))
                            .version(HttpClient.Version.HTTP_2) // 使用HTTP/2提高性能
                            .build();
                    System.out.println("[HttpClientFactory] HttpClient实例已创建");
                }
            }
        }
        return instance;
    }
    
    /**
     * 获取自定义超时时间的HttpClient实例
     * 注意：此方法会创建新的HttpClient实例，不使用缓存
     * 建议仅在确实需要不同超时设置时使用，否则应使用getInstance()
     * 
     * @param timeoutSeconds 超时时间（秒）
     * @return 配置好的HttpClient实例
     */
    public static HttpClient getInstanceWithTimeout(int timeoutSeconds) {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeoutSeconds))
                .version(HttpClient.Version.HTTP_2)
                .build();
    }
}
