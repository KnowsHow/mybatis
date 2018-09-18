package com.footprint.mybatis.web;

import com.footprint.mybatis.pojo.dto.SysUserDto;
import com.footprint.mybatis.service.user.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * SysUserController
 *
 * @author: JiaHaiGang
 * @date: 2018/9/12 13:09
 */
@RestController
@RequestMapping("users")
public class SysUserController {

    @Autowired
    private SysUserService userService;


    @GetMapping(value = "{id}")
    public SysUserDto findById(@PathVariable String id){
        return userService.findById(id);
    }
}
