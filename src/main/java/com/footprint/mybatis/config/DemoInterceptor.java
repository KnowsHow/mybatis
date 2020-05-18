package com.footprint.mybatis.config;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.*;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;

import java.lang.reflect.Proxy;
import java.sql.Statement;
import java.util.Properties;

/**
 * DemoInterceptor
 *
 * @author <a href="mailto:haigang.jia@ikang.com">Jia haiGang</a>
 * @date 2020年05月18日
 */
@Intercepts({
        @Signature(
                type = StatementHandler.class,
                method = "parameterize",
                args = {Statement.class}
        )
})
@Slf4j
public class DemoInterceptor implements Interceptor {

    @Getter
    @Setter
    private boolean formatSql = false;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        // 先获取原生对象
        StatementHandler statementHandler = (StatementHandler) realTarget(invocation.getTarget());
        // 构建其元数据
        MetaObject metaObject = SystemMetaObject.forObject(statementHandler);
        // 获取其指定属性
        String sql = (String) metaObject.getValue("delegate.boundSql.sql");
        if (formatSql) {
            log.info("exec sql = {}", sql.replaceAll("\\\\s*|\\t|\\r|\\n|\\r\\n", " ").replaceAll(" +", " "));
            return invocation.proceed();
        }
        log.info("exec sql = {}", sql);
        return invocation.proceed();
    }

    @Override
    public Object plugin(Object target) {
        if (target instanceof StatementHandler) {
            return Plugin.wrap(target, this);
        }
        return target;
    }

    @Override
    public void setProperties(Properties properties) {
        String formatSql = properties.getProperty("formatSql");
        if (formatSql != null && formatSql.equalsIgnoreCase(Boolean.TRUE.toString())) {
            this.formatSql = true;
        }
    }

    public static Object realTarget(Object target) {
        if (Proxy.isProxyClass(target.getClass())) {
            MetaObject metaObject = SystemMetaObject.forObject(target);
            return realTarget(metaObject.getValue("h.target"));
        }
        return target;
    }
}
