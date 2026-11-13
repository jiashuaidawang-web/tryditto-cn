package cn.aiditto;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
@MapperScan(basePackages = "cn.aiditto", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class AidittoApplication {
    public static void main(String[] args) {
        SpringApplication.run(AidittoApplication.class, args);
    }
}
