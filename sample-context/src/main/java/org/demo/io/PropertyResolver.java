package org.demo.io;

import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.*;
import java.util.*;
import java.util.function.Function;

public class PropertyResolver {
    Logger logger = LoggerFactory.getLogger(getClass());

    Map<String, String> properties = new HashMap<>();
    Map<Class<?>, Function<String, Object>> converters = new HashMap<>();

    public PropertyResolver(Properties props) {
        // 将系统环境变量放入属性map中
        this.properties.putAll(System.getenv());
        // 获取配置文件中的属性名集合
        Set<String> names = props.stringPropertyNames();
        // 将配置文件中的属性名和属性值放入属性map中
        for (String name : names) {
            this.properties.put(name, props.getProperty(name));
        }
        if (logger.isDebugEnabled()) {
            // 获取属性map中的所有键
            List<String> keys = new ArrayList<>(this.properties.keySet());
            // 将键列表按字典序进行排序
            Collections.sort(keys);
            // 输出排序后的属性名和属性值
            for (String key : keys) {
                logger.debug("PropertyResolver: {} = {}", key, this.properties.get(key));
            }
        }
        // 注册转换器
        this.registerConverters();
    }
    private void registerConverters() {
        // 注册字符串类型转换器
        converters.put(String.class, s -> s);
        // 注册布尔类型转换器
        converters.put(boolean.class, Boolean::parseBoolean);
        converters.put(Boolean.class, Boolean::valueOf);
        // 注册字节类型转换器
        converters.put(byte.class, Byte::parseByte);
        converters.put(Byte.class, Byte::valueOf);
        // 注册短整型类型转换器
        converters.put(short.class, Short::parseShort);
        converters.put(Short.class, Short::valueOf);
        // 注册整型类型转换器
        converters.put(int.class, Integer::parseInt);
        converters.put(Integer.class, Integer::valueOf);
        // 注册长整型类型转换器
        converters.put(long.class, Long::parseLong);
        converters.put(Long.class, Long::valueOf);
        // 注册单精度浮点型类型转换器
        converters.put(float.class, Float::parseFloat);
        converters.put(Float.class, Float::valueOf);
        // 注册双精度浮点型类型转换器
        converters.put(double.class, Double::parseDouble);
        converters.put(Double.class, Double::valueOf);
        // 注册日期类型转换器
        converters.put(LocalDate.class, LocalDate::parse);
        converters.put(LocalTime.class, LocalTime::parse);
        converters.put(LocalDateTime.class, LocalDateTime::parse);
        converters.put(ZonedDateTime.class, ZonedDateTime::parse);
        converters.put(Duration.class, Duration::parse);
        converters.put(ZoneId.class, ZoneId::of);
    }

    public boolean containsProperty(String key) {
        return this.properties.containsKey(key);
    }

    @Nullable
    public String getProperty(String key) {
        // 解析${abc.xyz:defaultValue}:
        PropertyExpr keyExpr = parsePropertyExpr(key);
        if (keyExpr != null) {
            if (keyExpr.defaultValue() != null) {
                // 带默认值查询:
                return getProperty(keyExpr.key(), keyExpr.defaultValue());
            } else {
                // 不带默认值查询:
                return getRequiredProperty(keyExpr.key());
            }
        }
        // 普通key查询:
        String value = this.properties.get(key);
        if (value != null) {
            return parseValue(value);
        }
        return value;
    }

    public String getProperty(String key, String defaultValue) {
        String value = getProperty(key);
        return value == null ? parseValue(defaultValue) : value;
    }

    @Nullable
    public <T> T getProperty(String key, Class<T> targetType) {
        String value = getProperty(key);
        if (value == null) {
            return null;
        }
        // 转换为指定类型:
        return convert(targetType, value);
    }

    public <T> T getProperty(String key, Class<T> targetType, T defaultValue) {
        String value = getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        return convert(targetType, value);
    }

    public String getRequiredProperty(String key) {
        String value = getProperty(key);
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }

    public <T> T getRequiredProperty(String key, Class<T> targetType) {
        T value = getProperty(key, targetType);
        return Objects.requireNonNull(value, "Property '" + key + "' not found.");
    }

    // 转换到指定Class类型:
    @SuppressWarnings("unchecked")
    <T> T convert(Class<?> clazz, String value) {
        Function<String, Object> fn = this.converters.get(clazz);
        if (fn == null) {
            throw new IllegalArgumentException("Unsupported value type: " + clazz.getName());
        }
        return (T) fn.apply(value);
    }

    String parseValue(String value) {
        PropertyExpr expr = parsePropertyExpr(value);
        if (expr == null) {
            return value;
        }
        if (expr.defaultValue() != null) {
            return getProperty(expr.key(), expr.defaultValue());
        } else {
            return getRequiredProperty(expr.key());
        }
    }

    PropertyExpr parsePropertyExpr(String key) {
        if (key.startsWith("${") && key.endsWith("}")) {
            // 是否存在defaultValue?
            int n = key.indexOf(':');
            if (n == (-1)) {
                // 没有defaultValue: ${key}
                String k = key.substring(2, key.length() - 1);
                return new PropertyExpr(k, null);
            } else {
                // 有defaultValue: ${key:default}
                String k = key.substring(2, n);
                return new PropertyExpr(k, key.substring(n + 1, key.length() - 1));
            }
        }
        return null;
    }

    String notEmpty(String key) {
        if (key.isEmpty()) {
            throw new IllegalArgumentException("Invalid key: " + key);
        }
        return key;
    }
}

record PropertyExpr(String key, String defaultValue) {
}
