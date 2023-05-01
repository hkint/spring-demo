package org.demo.io;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ResourceResolver {
    Logger logger = LoggerFactory.getLogger(getClass());

    String basePackage;

    public ResourceResolver(String basePackage) {
        this.basePackage = basePackage;
    }

    public <R> List<R> scan(Function<Resource, R> mapper) {
        String basePackagePath = this.basePackage.replace(".", "/");
        String path = basePackagePath;
        try {
            List<R> collector = new ArrayList<>();
            scan0(basePackagePath, path, collector, mapper);
            return collector;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    <R> void scan0(String basePackagePath, String path, List<R> collector, Function<Resource, R> mapper) throws IOException, URISyntaxException {
        logger.atDebug().log("scan path: {}", path);
        // 通过ClassLoader获取URL列表:
        Enumeration<URL> en = getContextClassLoader().getResources(path);
        while (en.hasMoreElements()) {
            URL url = en.nextElement();
            URI uri = url.toURI();
            String uriStr = removeTrailingSlash(uri.toString());
            String uriBaseStr = uriStr.substring(0, uriStr.length() - basePackagePath.length());
            if (uriBaseStr.startsWith("file:")) {
                // 在目录搜索
                uriBaseStr = uriBaseStr.substring(5);
            }
            if (uriStr.startsWith("jar:")) {
                // 在 Jar 包中搜索
                scanFile(true, uriBaseStr, jarUriToPath(basePackagePath, uri), collector, mapper);
            } else {
                scanFile(false, uriBaseStr, Paths.get(uri), collector, mapper);
            }
        }
    }

    /**
     * ClassLoader首先从Thread.getContextClassLoader()获取，如果获取不到，再从当前Class获取，因为Web应用的ClassLoader不是JVM提供的基于Classpath的ClassLoader，而是Servlet容器提供的ClassLoader，它不在默认的Classpath搜索，而是在/WEB-INF/classes目录和/WEB-INF/lib的所有jar包搜索，从Thread.getContextClassLoader()可以获取到Servlet容器专属的ClassLoader；
     */
    ClassLoader getContextClassLoader() {
        ClassLoader cl = null;
        cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) {
            cl = getClass().getClassLoader();
        }
        return cl;
    }

    Path jarUriToPath(String basePackagePath, URI jarUri) throws IOException {
        return FileSystems.newFileSystem(jarUri, Map.of()).getPath(basePackagePath);
    }

    // 扫描文件以查找资源
    <R> void scanFile(boolean isJar, String base, Path root, List<R> collector, Function<Resource, R> mapper) throws IOException {
        // 从基本目录中移除尾部斜线
        String baseDir = removeTrailingSlash(base);
        // 遍历给定的根路径
        Files.walk(root).filter(Files::isRegularFile).forEach(file -> {
            Resource res = null;
            // 检查文件是否为JAR文件
            if (isJar) {
                // 如果是，则创建一个新的资源对象，其中包含基本目录和文件路径
                res = new Resource(baseDir, removeLeadingSlash(file.toString()));
            } else {
                // 否则，创建一个新的资源对象，其中包含文件URL和文件名
                String path = file.toString();
                String name = removeLeadingSlash(path.substring(baseDir.length()));
                res = new Resource("file:" + path, name);
            }
            // 记录找到的资源
            logger.atDebug().log("找到资源：{}", res);
            // 对资源应用映射函数
            R r = mapper.apply(res);
            // 如果结果不为空，则将其添加到集合中
            if (r != null) {
                collector.add(r);
            }
        });
    }


    String removeTrailingSlash(String base) {
        // 检查字符串是否以斜线结尾
        if (base.endsWith("/")) {
            // 如果是，则移除斜线并返回新字符串
            return base.substring(0, base.length() - 1);
        }
        // 否则，返回原始字符串
        return base;
    }

    // 从给定字符串中移除前导斜线
    String removeLeadingSlash(String path) {
        // 检查字符串是否以斜线开头
        if (path.startsWith("/")) {
            // 如果是，则移除斜线并返回新字符串
            return path.substring(1);
        }
        // 否则，返回原始字符串
        return path;
    }
}
