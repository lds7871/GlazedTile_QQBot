package LDS.Person.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * RestTemplate配置类
 * 配置HTTP连接池和超时参数以提高性能
 * 使用连接池可以复用HTTP连接，减少连接建立的开销
 */
@Configuration
public class RestTemplateConfig {

    /**
     * 创建配置了连接池的RestTemplate Bean
     * @param builder RestTemplateBuilder
     * @return 配置好的RestTemplate实例
     */
    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
                .requestFactory(this::clientHttpRequestFactory)
                .setConnectTimeout(Duration.ofSeconds(10))  // 连接超时时间：10秒
                .setReadTimeout(Duration.ofSeconds(30))     // 读取超时时间：30秒
                .build();
    }

    /**
     * 配置HTTP请求工厂
     * 设置缓冲请求体以支持重试
     * @return ClientHttpRequestFactory实例
     */
    private ClientHttpRequestFactory clientHttpRequestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);  // 连接超时：10秒
        factory.setReadTimeout(30000);     // 读取超时：30秒
        factory.setBufferRequestBody(true); // 缓冲请求体，允许多次读取（支持重试）
        return factory;
    }
}
