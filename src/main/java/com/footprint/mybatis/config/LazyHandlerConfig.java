package com.footprint.mybatis.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.context.annotation.Configuration;

/**
 * LazyHandlerConfig
 * Mybtais在延迟加载时Spring无法序列化null的字段
 * @author Tinyice
 */
@Configuration
public class LazyHandlerConfig extends ObjectMapper {

    private static final long serialVersionUID = 4025947225928036339L;

    public LazyHandlerConfig() {
        //返回为null的值则去除，
        this.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        //解决延迟加载的对象
        this.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }
}
