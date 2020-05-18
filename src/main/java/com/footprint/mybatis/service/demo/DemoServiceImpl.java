package com.footprint.mybatis.service.demo;

import com.footprint.mybatis.mapper.demo.DemoMapper;
import com.footprint.mybatis.pojo.BookVo;
import com.footprint.mybatis.pojo.NestedBook;
import com.footprint.mybatis.pojo.RecursiveDemo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * DemoServiceImpl
 *
 * @author Tinyice
 */
@Slf4j
@Service
public class DemoServiceImpl {

    @Autowired
    private DemoMapper demoMapper;


    public List<RecursiveDemo> recursiveDemo(Integer level) {
        List<RecursiveDemo> list = demoMapper.selectByPid(0);
        // 触发全部懒加载
        if (level == 0) {
            return Collections.singletonList(list.get(0));
        }
        // 不触发children[1]
        if (level == 1) {
            return Collections.singletonList(list.get(0).getChildren().get(0));
        }
        // 不触发懒加载内容
        return new ArrayList<>();

    }

    public BookVo selectBooVoByUid(Integer uid) {
        return demoMapper.selectBookByUid(uid);
    }

    public List<RecursiveDemo> recursiveDemoNested(Integer level) {
        List<RecursiveDemo> list = demoMapper.selectByPidNested(0);
        // 触发全部懒加载
        if (level == 0) {
            return Collections.singletonList(list.get(0));
        }
        // 不触发children[1]
        if (level == 1) {
            return Collections.singletonList(list.get(0).getChildren().get(0));
        }
        // 不触发懒加载内容
        return new ArrayList<>();
    }

    public List  multiSelect() {
        return this.demoMapper.multiSelect();
    }

    public NestedBook selectNestedBook(int i) {
        if(1==i){
            return this.demoMapper.selectNestedBook1();
        }else {
            return this.demoMapper.selectNestedBook2();
        }
    }
}
