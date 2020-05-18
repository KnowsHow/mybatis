package com.footprint.mybatis.pojo;

import lombok.Data;

/**
 * Book
 *
 * @author Tinyice
 */
@Data
public class BookVo implements java.io.Serializable {

    private static final long serialVersionUID = -33570868395441815L;

    private Integer id;

    private Integer uid;

    private String name;

    private Float price;

    private String author;
}
