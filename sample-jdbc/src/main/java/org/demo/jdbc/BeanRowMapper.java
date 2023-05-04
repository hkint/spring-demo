package org.demo.jdbc;

import org.demo.exception.DataAccessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * Bean 对象映射器
 *
 * @param <T> 泛型参数，表示映射后的 Bean 对象类型
 *            <p>
 *            该类是一个 Bean 对象映射器，用于将结果集中的一行数据映射为 Bean 对象。该类实现了 RowMapper 接口，并实现了其中的 mapRow 方法。在 BeanRowMapper 类中，主要包含以下成员变量和方法：
 *            <p>
 *            clazz：映射后的 Bean 对象类型；
 *            constructor：映射后的 Bean 对象类型的构造方法；
 *            fields：结果集列名和映射后的 Bean 对象中的字段名的映射关系；
 *            <p>
 *            methods：结果集列名和映射后的 Bean 对象中的 setter 方法名的映射关系；
 *            BeanRowMapper(Class<T> clazz)：构造函数，初始化 clazz 和 constructor，并通过反射获取 clazz 中的所有公共字段和 setter 方法；
 *            mapRow(ResultSet rs, int rowNum)：将结果集中的一行数据映射为 Bean 对象。具体实现中，该方法首先通过 this.constructor.newInstance() 创建一个 clazz 类型的 Bean 对象 bean，然后通过 ResultSetMetaData 对象获取结果集的列数和每一列的列名，并根据列名在 this.methods 和 this.fields 中查找对应的 setter 方法或字段，并将结果集中对应列的值设置到 bean 对象的相应字段中或通过 setter 方法设置到 bean 对象中。最后将 bean 对象返回。
 *            这个 Bean 对象映射器是通用的，可以将结果集中的任何一行数据映射为一个 Bean 对象，只需要提供映射后的 Bean 对象类型 clazz 即可。
 */
public class BeanRowMapper<T> implements RowMapper<T> {

    final Logger logger = LoggerFactory.getLogger(getClass());

    Class<T> clazz;
    Constructor<T> constructor;
    Map<String, Field> fields = new HashMap<>();
    Map<String, Method> methods = new HashMap<>();

    /**
     * 构造 BeanRowMapper 对象
     * @param clazz 映射后的 Bean 对象类型
     */
    public BeanRowMapper(Class<T> clazz) {
        this.clazz = clazz;
        try {
            this.constructor = clazz.getConstructor();
        } catch (ReflectiveOperationException e) {
            throw new DataAccessException(String.format("No public default constructor found for class %s when build BeanRowMapper.", clazz.getName()), e);
        }
        for (Field f : clazz.getFields()) {
            String name = f.getName();
            this.fields.put(name, f);
            logger.atDebug().log("Add row mapping: {} to field {}", name, name);
        }
        for (Method m : clazz.getMethods()) {
            Parameter[] ps = m.getParameters();
            if (ps.length == 1) {
                String name = m.getName();
                if (name.length() >= 4 && name.startsWith("set")) {
                    String prop = Character.toLowerCase(name.charAt(3)) + name.substring(4);
                    this.methods.put(prop, m);
                    logger.atDebug().log("Add row mapping: {} to {}({})", prop, name, ps[0].getType().getSimpleName());
                }
            }
        }
    }

    /**
     * 将结果集中的一行数据映射为 Bean 对象
     * @param rs 结果集
     * @param rowNum 行号
     * @return 返回映射后的 Bean 对象
     * @throws SQLException SQL 异常
     */
    @Override
    public T mapRow(ResultSet rs, int rowNum) throws SQLException {
        T bean;
        try {
            bean = this.constructor.newInstance();
            ResultSetMetaData meta = rs.getMetaData();
            int columns = meta.getColumnCount();
            for (int i = 1; i <= columns; i++) {
                String label = meta.getColumnLabel(i);
                Method method = this.methods.get(label);
                if (method != null) {
                    method.invoke(bean, rs.getObject(label));
                } else {
                    Field field = this.fields.get(label);
                    if (field != null) {
                        field.set(bean, rs.getObject(label));
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new DataAccessException(String.format("Could not map result set to class %s", this.clazz.getName()), e);
        }
        return bean;
    }
}