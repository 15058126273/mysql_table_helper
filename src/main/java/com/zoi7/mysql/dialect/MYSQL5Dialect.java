package com.zoi7.mysql.dialect;

import com.zoi7.mysql.analysis.ScanJar;
import com.zoi7.mysql.analysis.ScanPackage;
import com.zoi7.mysql.comment.*;
import com.zoi7.mysql.config.Config;
import com.zoi7.mysql.config.DataConfig;
import com.zoi7.mysql.driverManager.DriverManagerDataSource;
import com.zoi7.mysql.util.FieldUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import static com.zoi7.mysql.comment.FieldType.*;
import static com.zoi7.mysql.config.DataConfig.*;

public class MYSQL5Dialect {

    private static final Logger log = LoggerFactory.getLogger(MYSQL5Dialect.class);

    private List<String> sqlList = new ArrayList<String>();
    private List<String> alterUpdates = new ArrayList<String>();

    private DataConfig config;
    private DataSource dataSource; // 数据库连接
    private Connection connect;

    {
        this.config = Config.config;
        this.dataSource = new DriverManagerDataSource(config);
    }

    /**
     * 初始化入口
     */
    public void init() {
        try {
            if (TYPE_NONE.equals(this.config.getType())) {
                return;
            }
            this.connect = this.dataSource.getConnection();
            Set<Class<?>> clazzSet = new HashSet<Class<?>>();
            for (String package1 : this.config.getPackages()) {
                clazzSet.addAll(ScanPackage.getClassesByPackageName(package1));
                if (this.config.isScanJar()) {
                    clazzSet.addAll(ScanJar.getClassesByPackageName(package1));
                }
            }
            log.info("MYSQL5Dialect init > packagesSize: {}, auto : {}, classListSize : {}",
                    this.config.getPackages().length, this.config.getType(), clazzSet.size());
            if (TYPE_CREATE.equals(this.config.getType())) {
                create(clazzSet);
            } else if (TYPE_UPDATE.equals(this.config.getType())) {
                update(clazzSet);
            }
            this.sqlList.addAll(this.alterUpdates);
            Statement statement = this.connect.createStatement();
            for (String sql : this.sqlList) {
                if (this.config.isShowSql()) {
                    log.info(sql);
                }
                statement.execute(sql);
            }
            this.connect.close();
            log.info("MYSQL5Dialect init finished...");
        } catch (Exception e) {
            e.printStackTrace();
            log.error("init throw an error", e);
        } finally {
            if (this.connect != null) {
                try {
                    this.connect.close();
                } catch (Exception e) {
                    log.error("init > close connection failed", e);
                }
            }
        }
    }

    /**
     * 重新创建表
     * @param clazzSet 表实体列表
     */
    private void create(Set<Class<?>> clazzSet) throws SQLException {
        log.debug("MYSQL5Dialect create...");
        for (Class<?> clazz : clazzSet) {
            Entity entity; // 表实体
            String tableName;
            // 是否表实体
            if (!clazz.isAnnotationPresent(Entity.class)) {
                continue;
            }
            // 是否需要检测表结构, 如果不需, 则跳过该表实体
            if (!(entity = clazz.getAnnotation(Entity.class)).check()) {
                continue;
            }
            // 验证表名
            if ("".equals((tableName = entity.tableName()).trim())) {
                throw new RuntimeException(clazz.getName() + " 未指定或指定了错误的表名 : " + tableName);
            }
            // 删除原有表
            this.sqlList.add("DROP TABLE IF EXISTS " + tableName + ";");
            // 创建新表
            createTable(entity, clazz);
        }
    }

    /**
     * 新建表
     * @param entity 表信息
     * @param clazz 表实体
     */
    private void createTable(Entity entity, Class<?> clazz) throws SQLException {
        String tableName = entity.tableName();
        log.debug("MYSQL5Dialect createTable: {}", tableName);
        String idField = null;
        boolean firstColumn = true;
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS " + tableName + "(\n");
        // 遍历字段
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            // 非注解字段 > 跳过
            if (!field.isAnnotationPresent(Field.class)) {
                continue;
            }
            // 验证字段名
            Field fieldAnnotation = field.getAnnotation(Field.class);
            // 如果是不是第一个字段, 则sql先加','
            if (firstColumn) {
                firstColumn = false;
            } else {
                sql.append(",\n");
            }
            // 如果是id字段 > 标记 & not null
            if (field.isAnnotationPresent(Id.class)) {
                if (idField == null) {
                    idField = FieldUtils.getColumn(field, config.isUppercase());
                }
                FieldType type = FieldUtils.getType(field);
                sql.append("\t")
                        .append(FieldUtils.getColumn(field, config.isUppercase()))
                        .append(" ")
                        .append(getTypeLength(type, fieldAnnotation.length(), fieldAnnotation.decimalLength()))
                        .append(isInteger(type) && fieldAnnotation.unsigned() ? " UNSIGNED " : "")
                        .append(" NOT NULL ");
                if (FieldUtils.isAutoIncrease(field)) {
                    sql.append(" AUTO_INCREMENT ");
                }
            }
            // 普通字段
            else {
                sql.append("\t").append(getColumnSql(field));
                // if 有索引
                if (fieldAnnotation.index().index()) {
                    sql.append(",\n\t");
                    sql.append(getIndexSql(field));
                }
            }
        }
        if (idField != null) {
            sql.append(", PRIMARY KEY (").append(idField).append(")");
        }
        // if 存在联合索引
        if (entity.indices().length > 0) {
            sql.append(getUniteIndexSql(entity));
        }
        sql.append("\n) COMMENT \"");
        sql.append(entity.comment());
        sql.append("\" ");
        // if 指定了编码
        if (!"".equals(entity.charset())) {
            sql.append(" DEFAULT CHARSET = ").append(entity.charset());
        }
        sql.append(";");
        this.sqlList.add(sql.toString());
    }

    /**
     * 更新表结构
     * @param clazzSet 表实体列表
     */
    private void update(Set<Class<?>> clazzSet) throws Exception {
        log.debug("MYSQL5Dialect update...");
        for (Class<?> clazz : clazzSet) {
            Entity entity; // 表实体
            String tableName;
            // 是否表实体
            if (!clazz.isAnnotationPresent(Entity.class)) {
                continue;
            }
            // 是否需要检测表结构, 如果不需, 则跳过该表实体
            if (!(entity = clazz.getAnnotation(Entity.class)).check()) {
                continue;
            }
            // 验证表名
            if ("".equals((tableName = entity.tableName().trim()))) {
                throw new RuntimeException(clazz.getName() + " 未指定或指定了错误的表名 : " + tableName);
            }
            // 如果表不存在, 则新建表
            if (!checkTableExist(tableName)) {
                createTable(entity, clazz);
            }
            // 如果表已存在, 则检查并更新字段
            else {
                checkForAddColumn(clazz);
            }
        }
    }

    /**
     * 检测表中是否含有该字段, 如不包含, 则新增该字段
     * @param clazz 表实体
     */
    private void checkForAddColumn(Class<?> clazz) throws Exception {
        log.debug("MYSQL5Dialect checkForAddColumn ...");
        // 遍历字段
        for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
            PreparedStatement ps;
            ResultSet resultSet;
            // 非注解字段 > 跳过
            if (!field.isAnnotationPresent(Field.class)) {
                continue;
            }
            // 检查字段是否存在
            String assertField = "DESCRIBE " + (clazz.getAnnotation(Entity.class)).tableName() + " " + FieldUtils.getColumn(field, config.isUppercase());
            ps = this.connect.prepareStatement(assertField, ResultSet.TYPE_SCROLL_INSENSITIVE, ResultSet.CONCUR_READ_ONLY);
            resultSet = ps.executeQuery();
            // 不存在则新增字段
            if (!resultSet.last()) {
                String tableName = clazz.getAnnotation(Entity.class).tableName();
                String alterSql = "ALTER TABLE " + tableName + " ADD COLUMN " + getColumnSql(field);
                this.alterUpdates.add(alterSql);
                Field field1 = field.getAnnotation(Field.class);
                if (field1.index().index()) {
                    String indexSql = "ALTER TABLE " + tableName + " ADD " + getIndexSql(field);
                    this.alterUpdates.add(indexSql);
                }
            }
            ps.close();
            resultSet.close();
        }
    }

    /**
     * 检查表是否存在
     * @param name 表名
     * @return 是否存在
     */
    private boolean checkTableExist(String name) {
        log.debug("MYSQL5Dialect checkTableExist > tableName: {}", name);
        PreparedStatement ps;
        ResultSet resultSet;
        try {
            String sql = "DESC " + name;
            ps = this.connect.prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY);
            resultSet = ps.executeQuery();
            boolean hasRow = resultSet.last();
            ps.close();
            resultSet.close();
            return hasRow;
        } catch (SQLException e1) {
            String message = e1.getMessage();
            if (message != null &&
                    (message.contains("1146") || Pattern.matches("Table '.*' doesn't exist", message))) {
                return false;
            } else {
                throw new RuntimeException(e1);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 获取字段相对应的sql语句
     * @param field 属性
     * @return 字段sql
     */
    private String getColumnSql(java.lang.reflect.Field field) {
        Field fieldAnnotation = field.getAnnotation(Field.class);
        // 字段名
        String column = FieldUtils.getColumn(field, config.isUppercase());
        FieldType type = FieldUtils.getType(field);
        String typeLength = getTypeLength(type, fieldAnnotation.length(), fieldAnnotation.decimalLength());
        String unsigned = isInteger(type) && fieldAnnotation.unsigned() ? " UNSIGNED " : " ";
        String nullableString = (fieldAnnotation.nullable() ? " " : " NOT NULL ");
        String defaultString = needDefault(field) ? " DEFAULT \"" + fieldAnnotation.defaultValue() + "\"" : " ";
        String comment = " COMMENT \"" + fieldAnnotation.comment() + "\"";
        return " " + column + typeLength + unsigned + nullableString + defaultString + comment;
    }

    /**
     * 获取索引对应的 sql语句
     * @param field 字段
     * @return sql
     */
    private String getIndexSql(java.lang.reflect.Field field) {
        Field fieldAnnotation = field.getAnnotation(Field.class);
        // 字段名
        String column = FieldUtils.getColumn(field, config.isUppercase());
        String indexString = "";
        Index index = fieldAnnotation.index();
        if (index.index()) {
            indexString += index.unique()? "UNIQUE INDEX " : "INDEX ";
            if (!"".equals(index.name())) {
                indexString += index.name();
            }
            indexString += "(" + column + ") ";
        }
        return indexString;
    }

    /**
     * 获取联合索引对应的 sql语句
     * @param entity 表实体注解
     * @return sql
     */
    private String getUniteIndexSql(Entity entity) throws SQLException {
        StringBuilder indexString = new StringBuilder();
        // 联合索引集合
        UniteIndex[] indices = entity.indices();
        for (UniteIndex index : indices) {
            if (index.columns().length == 0 && index.fields().length == 0) {
                throw new SQLException("表[" + entity.tableName() + "]联合索引的列名未指定!");
            }
            String[] columns = index.columns();
            String[] fields = index.fields();

            StringBuilder columnSb = new StringBuilder();
            if (columns.length > 0) {
                for (int i = 0; i < columns.length; i++) {
                    columnSb.append(columns[i]);
                    if (i < columns.length - 1) {
                        columnSb.append(",");
                    }
                }
            } else {
                for (int i = 0; i < fields.length; i++) {
                    columnSb.append(FieldUtils.getColumnByField(fields[i], config.isUppercase()));
                    if (i < fields.length - 1) {
                        columnSb.append(",");
                    }
                }
            }
            indexString.append(",\n\t");
            indexString.append(index.unique()? "UNIQUE INDEX " : "INDEX ");
            if (!"".equals(index.name())) {
                indexString.append(index.name());
            } else {
                String cs = columnSb.toString();
                indexString.append(cs.replace(",", "_").replace(" ", ""));
            }
            indexString.append("(").append(columnSb.toString()).append(")");
        }
        return indexString.toString();
    }

    /**
     * 获取字段属性描述
     * @param type 类型
     * @param length 长度
     * @param decimalLength 小数位
     * @return 描述
     */
    private String getTypeLength(FieldType type, int length, int decimalLength) {
        String typeLength;
        switch (type) {
            case BIGINT: {
                typeLength = " BIGINT ";
                break;
            }
            case INTEGER: {
                typeLength = " INTEGER ";
                break;
            }
            case TINYINT: {
                typeLength = " TINYINT ";
                break;
            }
            case SMALLINT: {
                typeLength = " SMALLINT ";
                break;
            }
            case FLOAT: {
                typeLength = " FLOAT(" + Math.min(255, length) + ", " + Math.min(30, decimalLength) + ") ";
                break;
            }
            case DOUBLE: {
                typeLength = " DOUBLE(" + Math.min(255, length) + ", " + Math.min(53, decimalLength) + ") ";
                break;
            }
            case DECIMAL: {
                typeLength = " DECIMAL(" + Math.min(65, length) + ", " + Math.min(30, decimalLength) + ") ";
                break;
            }
            case TEXT: {
                typeLength = " TEXT ";
                break;
            }
            case DATE: {
                typeLength = " DATE ";
                break;
            }
            case DATETIME: {
                typeLength = " DATETIME ";
                break;
            }
            case TIME: {
                typeLength = " TIME ";
                break;
            }
            case TIMESTAMP: {
                typeLength = " TIMESTAMP ";
                break;
            }
            default: {
                typeLength = " VARCHAR(" + Math.min(21845, length) + ") ";
                break;
            }
        }
        return typeLength;
    }

    /**
     * 判断字段类型是否为整数
     * @param type 字段类型
     * @return 是否为整数
     */
    private boolean isInteger(FieldType type) {
        return type == BIGINT || type == INTEGER || type == TINYINT || type == SMALLINT;
    }

    /**
     * 判断字段类型是否为字符串
     * @param type 字段类型
     * @return 是否为字符串
     */
    private boolean isChar(FieldType type) {
        return type == VARCHAR || type == TINYTEXT || type == TEXT || type == MEDIUMTEXT || type == LONGTEXT;
    }

    /**
     * 判断是否需要指定默认值
     * @param field 字段信息
     * @return 是否需要指定默认值
     */
    private boolean needDefault(java.lang.reflect.Field field) {
        Field fieldAnnotation = field.getAnnotation(Field.class);
        FieldType type = FieldUtils.getType(field);
        if (!fieldAnnotation.nullable()) {
            String defVal = fieldAnnotation.defaultValue();
            return isChar(type) || !"".equals(defVal);
        }
        return false;
    }

}