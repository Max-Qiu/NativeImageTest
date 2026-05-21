package com.maxqiu.test.controller;

import com.maxqiu.test.entity.User;
import com.maxqiu.test.mapper.UserMapper;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserMapper userMapper;

    public UserController(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    @GetMapping
    public List<User> list() {
        return userMapper.selectList(null);
    }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        return userMapper.selectById(id);
    }

    @PostMapping
    public User create(@RequestBody User user) {
        userMapper.insert(user);
        return user;
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        userMapper.updateById(user);
        return user;
    }

    @DeleteMapping("/{id}")
    public int delete(@PathVariable Long id) {
        return userMapper.deleteById(id);
    }
}
