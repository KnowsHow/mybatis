package com.footprint.mybatis.pojo;

import com.footprint.mybatis.pojo.entity.Recursive;
import lombok.Data;

import java.util.List;

/**
 * RecursiveDemo
 *
 * @author Tinyice
 */
@Data
public class RecursiveDemo implements java.io.Serializable{
    private static final long serialVersionUID = 4177205338518886778L;

    private Integer id;

    private Integer pid;

    private String name;

    private List<RecursiveDemo> children;
}
