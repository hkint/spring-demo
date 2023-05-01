package org.demo.io;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.sql.DataSourceDefinition;
import jakarta.annotation.sub.AnnoScan;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResourceResolverTest {
    @Test
    public void scanClass() {
        var pkg = "org.demo.scan";
        // 定义一个扫描器
        var rr = new ResourceResolver(pkg);
        List<String> classes = rr.scan(res -> {
            String name = res.name();
            if (name.endsWith(".class")) {
                // 把"org/example/Hello.class"变为"org.example.Hello":
                return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
            }
            // 否则返回null表示不是有效的Class Name:
            return null;
        });
        Collections.sort(classes);
        System.out.println(classes);
        String[] listClasses = new String[]{
                // list of some scan classes:
                "org.demo.scan.convert.ValueConverterBean", //
                "org.demo.scan.destroy.AnnotationDestroyBean", //
                "org.demo.scan.init.SpecifyInitConfiguration", //
                "org.demo.scan.proxy.OriginBean", //
                "org.demo.scan.proxy.FirstProxyBeanPostProcessor", //
                "org.demo.scan.proxy.SecondProxyBeanPostProcessor", //
                "org.demo.scan.nested.OuterBean", //
                "org.demo.scan.nested.OuterBean$NestedBean", //
                "org.demo.scan.sub1.Sub1Bean", //
                "org.demo.scan.sub1.sub2.Sub2Bean", //
                "org.demo.scan.sub1.sub2.sub3.Sub3Bean", //
        };
        for (String clazz : listClasses) {
            assertTrue(classes.contains(clazz));
        }
    }

    @Test
    public void scanJar() {
        var pkg = PostConstruct.class.getPackageName();
        var rr = new ResourceResolver(pkg);
        List<String> classes = rr.scan(res -> {
            String name = res.name();
            if (name.endsWith(".class")) {
                return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
            }
            return null;
        });
        // classes in jar:
        assertTrue(classes.contains(PostConstruct.class.getName()));
        assertTrue(classes.contains(PreDestroy.class.getName()));
        assertTrue(classes.contains(PermitAll.class.getName()));
        assertTrue(classes.contains(DataSourceDefinition.class.getName()));
        // jakarta.annotation.sub.AnnoScan is defined in classes:
        assertTrue(classes.contains(AnnoScan.class.getName()));
    }

    @Test
    public void scanTxt() {
        var pkg = "org.demo.scan";
        var rr = new ResourceResolver(pkg);
        List<String> classes = rr.scan(res -> {
            String name = res.name();
            if (name.endsWith(".txt")) {
                return name.replace("\\", "/");
            }
            return null;
        });
        Collections.sort(classes);
        assertArrayEquals(new String[]{
                // txt files:
                "org/demo/scan/sub1/sub1.txt", //
                "org/demo/scan/sub1/sub2/sub2.txt", //
                "org/demo/scan/sub1/sub2/sub3/sub3.txt", //
        }, classes.toArray(String[]::new));
    }
}