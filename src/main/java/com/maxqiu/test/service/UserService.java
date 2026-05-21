package com.maxqiu.test.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.maxqiu.test.dto.AgeCount;
import com.maxqiu.test.entity.User;

import java.util.List;

public interface UserService extends IService<User> {

    List<AgeCount> countByAge();
}
