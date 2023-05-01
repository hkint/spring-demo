package org.demo.utils;

import org.demo.io.InputStreamCallback;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

public class ClassPathUtils {

    /**
     * @param path                路径
     * @param inputStreamCallback 输入流回调
     * @param <T>                 泛型
     * @return 泛型对象
     * @throws UncheckedIOException IO异常
     */
    public static <T> T readInputStream(String path, InputStreamCallback<T> inputStreamCallback) {
        // 判断路径是否以 / 开头
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        try (InputStream input = getContextClassLoader().getResourceAsStream(path)) {
            // 判断输入流是否为 null
            if (input == null) {
                throw new FileNotFoundException("File not found in classpath: " + path);
            }
            return inputStreamCallback.doWithInputStream(input);
        } catch (IOException e) {
            e.printStackTrace();
            throw new UncheckedIOException(e);
        }
    }

    /**
     * 读取字符串
     *
     * @param path 路径
     * @return 字符串
     * @throws UncheckedIOException IO异常
     */
    public static String readString(String path) {
        return readInputStream(path, input -> {
            byte[] data = input.readAllBytes();
            return new String(data, StandardCharsets.UTF_8);
        });
    }

    /**
     * 获取 ClassLoader
     *
     * @return ClassLoader 对象
     */
    private static ClassLoader getContextClassLoader() {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = ClassPathUtils.class.getClassLoader();
        }
        return cl;
    }
}
