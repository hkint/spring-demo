package org.demo.web.utils;

import jakarta.servlet.ServletException;

import java.util.regex.Pattern;

public class PathUtils {

    /**
     * 将给定的路径编译成一个带有命名捕获组的正则表达式模式。
     * 命名捕获组是根据路径中的占位符创建的，占位符用花括号表示，例如{id}。
     * 命名捕获组将匹配除正斜杠之外的任何字符。
     *
     * @param path 要编译为正则表达式模式的路径
     * @return 带有命名捕获组的正则表达式模式
     * @throws ServletException 如果给定的路径无效
     */
    public static Pattern compile(String path) throws ServletException {
        // 将占位符替换为命名捕获组
        String regPath = path.replaceAll("\\{([a-zA-Z][a-zA-Z0-9]*)\\}", "(?<$1>[^/]*)");
        // 检查路径中是否有剩余的占位符
        if (regPath.indexOf('{') >= 0 || regPath.indexOf('}') >= 0) {
            throw new ServletException("无效的路径: " + path);
        }
        // 返回一个匹配整个字符串的正则表达式模式
        return Pattern.compile("^" + regPath + "$");
    }

}
