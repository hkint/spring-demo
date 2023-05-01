package org.demo.utils;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.representer.Representer;
import org.yaml.snakeyaml.resolver.Resolver;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressWarnings("unused")
public class YamlUtils {
    // 从指定路径下的Yaml文件中加载数据为Map<String, Object>类型
    @SuppressWarnings("unchecked")
    public static Map<String, Object> loadYaml(String path) {
        // 创建LoaderOptions、DumperOptions、Representer、NoImplicitResolver以及Yaml对象
        var loaderOptions = new LoaderOptions();
        var dumperOptions = new DumperOptions();
        var representer = new Representer(dumperOptions);
        var resolver = new NoImplicitResolver();
        var yaml = new Yaml(new Constructor(loaderOptions), representer, dumperOptions, loaderOptions, resolver);
        // 读取指定路径下的Yaml文件并转换为Map<String, Object>类型
        return ClassPathUtils.readInputStream(path, yaml::load);
        //  return ClassPathUtils.readInputStream(path, (input) -> {
        //            return (Map<String, Object>) yaml.load(input);
        //        });
    }

    // 从指定路径下的Yaml文件中加载数据为Map<String, Object>类型，并转换为无层级的Map<String, Object>类型
    // SnakeYaml默认读出的结构是树形结构，需要“拍平”成abc.xyz格式的key；
    public static Map<String, Object> loadYamlAsPlainMap(String path) {
        Map<String, Object> data = loadYaml(path);
        Map<String, Object> plain = new LinkedHashMap<>();
        convertTo(data, "", plain);
        return plain;
    }

    // 将层次结构的Map<String, Object>对象转换为无层级的Map<String, Object>对象
    static void convertTo(Map<String, Object> source, String prefix, Map<String, Object> plain) {
        for (String key : source.keySet()) {
            Object value = source.get(key);
            if (value instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) value;
                convertTo(subMap, prefix + key + ".", plain);
            } else if (value instanceof List) {
                plain.put(prefix + key, value);
            } else {
                plain.put(prefix + key, value.toString());
            }
        }
    }
}

/**
 * 禁用所有隐式类型转换，将所有值都视为字符串类型。
 * SnakeYaml默认会自动转换int、boolean等value，需要禁用自动转换，把所有value均按String类型返回。
 */
class NoImplicitResolver extends Resolver {
    public NoImplicitResolver() {
        super();
        super.yamlImplicitResolvers.clear();
    }
}
