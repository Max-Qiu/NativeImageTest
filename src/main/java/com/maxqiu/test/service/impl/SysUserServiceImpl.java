package com.maxqiu.test.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.maxqiu.test.entity.SysUser;
import com.maxqiu.test.mapper.SysUserMapper;
import com.maxqiu.test.service.SysUserService;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
}
