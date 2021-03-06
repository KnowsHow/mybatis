package com.footprint.mybatis.config;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * MybatisConfig
 *
 * @author: JiaHaiGang
 * @date: 2018/9/12 14:03
 */
@Configuration
@MapperScan("com.footprint.mybatis.mapper.**")
public class MybatisConfig {

    @Bean
    public DemoInterceptor demoInterceptor(){
        DemoInterceptor demoInterceptor= new DemoInterceptor();
        demoInterceptor.setFormatSql(true);
        return demoInterceptor;
    }
}
