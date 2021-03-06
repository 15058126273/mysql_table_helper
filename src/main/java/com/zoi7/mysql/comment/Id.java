package com.zoi7.mysql.comment;

import java.lang.annotation.*;

/**
 * 主键
 * @author yjy
 * 2017年2月24日上午9:36:45
 */
@Inherited
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({java.lang.annotation.ElementType.FIELD})
public @interface Id {

    /**
     * @return 是否自增长
     */
    boolean autoIncrease() default true;

}