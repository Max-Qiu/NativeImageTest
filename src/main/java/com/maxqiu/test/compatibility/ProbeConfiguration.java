package com.maxqiu.test.compatibility;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportRuntimeHints;

@Configuration(proxyBeanMethods = false)
@ImportRuntimeHints(ProbeNativeHints.class)
public class ProbeConfiguration {
    @Bean
    public MapperFactoryBean<ProbeMapper> probeMapper(SqlSessionFactory factory) {
        MapperFactoryBean<ProbeMapper> bean = new MapperFactoryBean<>(ProbeMapper.class);
        bean.setSqlSessionFactory(factory);
        return bean;
    }

    @Bean
    public MybatisPlusInterceptor probePagination() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }

}
