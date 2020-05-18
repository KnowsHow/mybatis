package com.footprint.mybatis.web;

import com.footprint.mybatis.pojo.BookVo;
import com.footprint.mybatis.pojo.NestedBook;
import com.footprint.mybatis.pojo.RecursiveDemo;
import com.footprint.mybatis.pojo.entity.Book;
import com.footprint.mybatis.service.demo.DemoServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * DemoController
 *
 * @author Tinyice
 */
@RestController
@RequestMapping("demo")
public class DemoController {

    @Autowired
    private DemoServiceImpl demoService;

    @GetMapping(value = "/recursive/{level}")
    public List<RecursiveDemo> recursiveDemo(@PathVariable Integer level) {
        return demoService.recursiveDemo(level);
    }

    @GetMapping(value = "/recursive/nested/{level}")
    public List<RecursiveDemo> recursiveDemoNested(@PathVariable Integer level) {
        return demoService.recursiveDemoNested(level);
    }


    @GetMapping(value = "/resultMap/extends/{uid}")
    public BookVo selectBooVoByUid(@PathVariable Integer uid) {
        return demoService.selectBooVoByUid(uid);
    }


    @GetMapping(value = "/resultMap/nested/1")
    public NestedBook selectNestedBook1() {
        return demoService.selectNestedBook(1);
    }

    @GetMapping(value = "/resultMap/nested/2")
    public NestedBook selectNestedBook2() {
        return demoService.selectNestedBook(2);
    }

    @GetMapping(value = "/multi/select")
    public List  multiSelect() {
        return demoService.multiSelect();
    }
}
