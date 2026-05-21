package com.maxqiu.test.controller;

import com.maxqiu.test.dto.AgeCount;
import com.maxqiu.test.entity.User;
import com.maxqiu.test.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<User> list() {
        return userService.list();
    }

    @GetMapping("/{id}")
    public User get(@PathVariable Long id) {
        return userService.getById(id);
    }

    @PostMapping
    public User create(@RequestBody User user) {
        userService.save(user);
        return user;
    }

    @PutMapping("/{id}")
    public User update(@PathVariable Long id, @RequestBody User user) {
        user.setId(id);
        userService.updateById(user);
        return user;
    }

    @DeleteMapping("/{id}")
    public boolean delete(@PathVariable Long id) {
        return userService.removeById(id);
    }

    @GetMapping("/stats/age")
    public List<AgeCount> countByAge() {
        return userService.countByAge();
    }
}
