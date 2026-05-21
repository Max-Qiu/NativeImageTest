package com.maxqiu.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maxqiu.test.dto.AgeCount;
import com.maxqiu.test.entity.User;
import com.maxqiu.test.mapper.UserMapper;
import com.maxqiu.test.service.UserService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    @Override
    public List<AgeCount> countByAge() {
        return baseMapper.countByAge();
    }
}
