package com.yjy.mysql.comment;

/**
 * 支持的字段类型
 * @author yjy
 * 2017年2月24日上午9:36:45
 */
public enum FieldType {

	AUTO, // 自动匹配类型
    BIGINT, // 长整数
    VARCHAR, //字符串
    INT, // 整数
    TINYINT, // 超小整数
    SMALLINT, // 小整数
    FLOAT, // 单精度浮点数
    DOUBLE, // 双精度浮点数
    DECIMAL, // 小数
    TEXT, // 文本
    DATE, // 日期
    DATETIME, //
    TIME, // 时间
    TIMESTAMP, // 时间戳

}
