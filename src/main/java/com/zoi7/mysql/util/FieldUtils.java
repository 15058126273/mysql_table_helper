package com.zoi7.mysql.util;

import com.zoi7.mysql.comment.Field;
import com.zoi7.mysql.comment.FieldType;
import com.zoi7.mysql.comment.Id;

import java.math.BigDecimal;
import java.sql.Blob;
import java.sql.Date;

/**
 * @author yjy
 * 2018-06-21 13:28
 */
public class FieldUtils {

    /**
     * 获取字段名
     * @param field 属性名
     * @return 字段名
     */
    public static String getColumn(java.lang.reflect.Field field) {
        return getColumn(field, false);
    }

    /**
     * 获取字段名
     * @param field 属性名
     * @return 字段名
     */
    public static String getColumn(java.lang.reflect.Field field, boolean uppercase) {
        Field fieldAnnotation = field.getAnnotation(Field.class);
        // 字段名
        String column = fieldAnnotation.field();
        if ("".equals(column)) {
            column = getColumnByField(field.getName(), uppercase);
        }
        return column;
    }

    /**
     * 根据属性名获取字段名
     * @param fieldName 属性名
     * @return 字段名
     */
    public static String getColumnByField(String fieldName) {
        return getColumnByField(fieldName, false);
    }

    /**
     * 根据属性名获取字段名
     * @param fieldName 属性名
     * @param uppercase 是否大写
     * @return 字段名
     */
    public static String getColumnByField(String fieldName, boolean uppercase) {
        StringBuilder column = new StringBuilder();
        for (int i = 0; i < fieldName.length(); i++) {
            char c = fieldName.charAt(i);
            if (c >= 'A' && c <= 'Z') {
                c += 32;
                column.append('_');
            }
            column.append(c);
        }
        String field = column.toString();
        return uppercase ? field.toUpperCase() : field;
    }

    /**
     * 获取字段指定的类型
     * @param field 字段
     * @return 类型
     */
    public static FieldType getType(java.lang.reflect.Field field) {
        Field fieldAnnotation = field.getAnnotation(Field.class);
        FieldType type = fieldAnnotation.type();
        if (FieldType.AUTO.equals(type)) {
            Class clazz = field.getType();
            // int
            if (clazz == Integer.class || clazz == int.class) {
                type = FieldType.INTEGER;
            }
            // long
            else if (clazz == Long.class || clazz == long.class) {
                type = FieldType.BIGINT;
            }
            // double
            else if (clazz == Double.class || clazz == double.class) {
                type = FieldType.DOUBLE;
            }
            // float
            else if (clazz == Float.class || clazz == float.class) {
                type = FieldType.FLOAT;
            }
            // datetime
            else if (clazz == Date.class || clazz == java.util.Date.class) {
                type = FieldType.DATETIME;
            }
            // decimal
            else if (clazz == BigDecimal.class) {
                type = FieldType.DECIMAL;
            }
            // blob
            else if (clazz == Blob.class) {
                type = FieldType.BLOB;
            }
            // 其他
            else {
                type = FieldType.VARCHAR;
            }
        }
        return type;
    }

    /**
     * 是否自增长
     * @param idField id字段
     * @return 是否
     */
    public static boolean isAutoIncrease(java.lang.reflect.Field idField) {
        if (idField.isAnnotationPresent(Id.class)) {
            Id fieldAnnotation = idField.getAnnotation(Id.class);
            return fieldAnnotation.autoIncrease();
        }
        return false;
    }

    /**
     * 判断字段类型是否为数字
     * @param type 类型
     * @return 是否数字
     */
    public static boolean isNumber(FieldType type) {
        return type.equals(FieldType.INTEGER) || type.equals(FieldType.BIGINT)
                || type.equals(FieldType.SMALLINT) || type.equals(FieldType.TINYINT) || type.equals(FieldType.FLOAT)
                || type.equals(FieldType.DOUBLE) || type.equals(FieldType.DECIMAL);
    }

}
