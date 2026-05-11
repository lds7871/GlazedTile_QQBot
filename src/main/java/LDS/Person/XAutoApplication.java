package LDS.Person;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * SpringBoot 启动应用类
 */
@SpringBootApplication(exclude = { DataSourceAutoConfiguration.class })
@EnableScheduling
public class XAutoApplication {

    public static void main(String[] args) {
        SpringApplication.run(XAutoApplication.class, args);
        System.out.println("PersonLog 应用启动成功！");
        System.out.println("访问地址: http://localhost:9200");
    }

}
