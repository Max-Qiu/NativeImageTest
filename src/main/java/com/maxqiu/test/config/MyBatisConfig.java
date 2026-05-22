package com.maxqiu.test.config;

import com.maxqiu.test.mapper.SysUserMapper;
import com.maxqiu.test.mapper.UserMapper;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MyBatisConfig {

    @Bean
    public MapperFactoryBean<UserMapper> userMapper(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<UserMapper> bean = new MapperFactoryBean<>(UserMapper.class);
        bean.setSqlSessionFactory(sqlSessionFactory);
        return bean;
    }

    @Bean
    public MapperFactoryBean<SysUserMapper> sysUserMapper(SqlSessionFactory sqlSessionFactory) {
        MapperFactoryBean<SysUserMapper> bean = new MapperFactoryBean<>(SysUserMapper.class);
        bean.setSqlSessionFactory(sqlSessionFactory);
        return bean;
    }
}
