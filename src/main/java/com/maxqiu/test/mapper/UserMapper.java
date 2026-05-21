package com.maxqiu.test.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.maxqiu.test.dto.AgeCount;
import com.maxqiu.test.entity.User;

import java.util.List;

public interface UserMapper extends BaseMapper<User> {

    List<AgeCount> countByAge();
}
