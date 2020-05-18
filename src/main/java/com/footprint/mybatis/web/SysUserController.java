package com.footprint.mybatis.web;

import com.footprint.mybatis.pojo.SysUserVo;
import com.footprint.mybatis.pojo.dto.SysUserDto;
import com.footprint.mybatis.service.user.SysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping(value = "/vo/{id}")
    public SysUserVo selectVoById(@PathVariable String id){
        return userService.selectVoById(id);
    }

    @GetMapping(value = "/one/{id}")
    public SysUserDto selectOne(@PathVariable String id){
        return userService.selectOne(id);
    }

    @PutMapping(value = "/{id}")
    public int updateById(@PathVariable String id){
        return userService.updateById(id);
    }
}
