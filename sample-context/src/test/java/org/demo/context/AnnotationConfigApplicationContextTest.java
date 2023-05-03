package org.demo.context;

import org.imported.LocalDateConfiguration;
import org.imported.ZonedDateConfiguration;
import org.demo.io.PropertyResolver;
import org.scan.ScanApplication;
import org.scan.convert.ValueConverterBean;
import org.scan.custom.annotation.CustomAnnotationBean;
import org.scan.init.AnnotationInitBean;
import org.scan.init.SpecifyInitBean;
import org.scan.nested.OuterBean;
import org.scan.primary.DogBean;
import org.scan.primary.PersonBean;
import org.scan.primary.StudentBean;
import org.scan.primary.TeacherBean;
import org.junit.jupiter.api.Test;
import org.scan.sub1.Sub1Bean;
import org.scan.sub1.sub2.Sub2Bean;
import org.scan.sub1.sub2.sub3.Sub3Bean;

import java.time.*;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

public class AnnotationConfigApplicationContextTest {

    @Test
    public void testAnnotationConfigApplicationContext() {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        // @CustomAnnotation:
        assertNotNull(ctx.findBeanDefinition(CustomAnnotationBean.class));
        assertNotNull(ctx.findBeanDefinition("customAnnotation"));

        // @Import():
        assertNotNull(ctx.findBeanDefinition(LocalDateConfiguration.class));
        assertNotNull(ctx.findBeanDefinition("startLocalDate"));
        assertNotNull(ctx.findBeanDefinition("startLocalDateTime"));
        assertNotNull(ctx.findBeanDefinition(ZonedDateConfiguration.class));
        assertNotNull(ctx.findBeanDefinition("startZonedDateTime"));
        // nested:
        assertNotNull(ctx.findBeanDefinition(OuterBean.class));
        assertNotNull(ctx.findBeanDefinition(OuterBean.NestedBean.class));

        BeanDefinition studentDef = ctx.findBeanDefinition(StudentBean.class);
        BeanDefinition teacherDef = ctx.findBeanDefinition(TeacherBean.class);
        // 2 PersonBean:
        List<BeanDefinition> defs = ctx.findBeanDefinitions(PersonBean.class);
        assertSame(studentDef, defs.get(0));
        assertSame(teacherDef, defs.get(1));
        // 1 @Primary PersonBean:
        BeanDefinition personPrimaryDef = ctx.findBeanDefinition(PersonBean.class);
        assertSame(teacherDef, personPrimaryDef);
    }

    PropertyResolver createPropertyResolver() {
        var ps = new Properties();
        ps.put("app.title", "Scan App");
        ps.put("app.version", "v1.0");
        ps.put("jdbc.url", "jdbc:hsqldb:file:testdb.tmp");
        ps.put("jdbc.username", "sa");
        ps.put("jdbc.password", "");
        ps.put("convert.boolean", "true");
        ps.put("convert.byte", "123");
        ps.put("convert.short", "12345");
        ps.put("convert.integer", "1234567");
        ps.put("convert.long", "123456789000");
        ps.put("convert.float", "12345.6789");
        ps.put("convert.double", "123456789.87654321");
        ps.put("convert.localdate", "2023-03-29");
        ps.put("convert.localtime", "20:45:01");
        ps.put("convert.localdatetime", "2023-03-29T20:45:01");
        ps.put("convert.zoneddatetime", "2023-03-29T20:45:01+08:00[Asia/Shanghai]");
        ps.put("convert.duration", "P2DT3H4M");
        ps.put("convert.zoneid", "Asia/Shanghai");
        var pr = new PropertyResolver(ps);
        return pr;
    }

    @Test
    public void testCustomAnnotation() {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        assertNotNull(ctx.getBean(CustomAnnotationBean.class));
        assertNotNull(ctx.getBean("customAnnotation"));
    }

    @Test
    public void testImport() {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        assertNotNull(ctx.getBean(LocalDateConfiguration.class));
        assertNotNull(ctx.getBean("startLocalDate"));
        assertNotNull(ctx.getBean("startLocalDateTime"));
        assertNotNull(ctx.getBean(ZonedDateConfiguration.class));
        assertNotNull(ctx.getBean("startZonedDateTime"));
    }

    @Test
    public void testNested() {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        ctx.getBean(OuterBean.class);
        ctx.getBean(OuterBean.NestedBean.class);
    }

    @Test
    public void testPrimary() {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        var person = ctx.getBean(PersonBean.class);
        assertEquals(TeacherBean.class, person.getClass());
        var dog = ctx.getBean(DogBean.class);
        assertEquals("Husky", dog.type);
    }

    @Test
    public void testSub() {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        ctx.getBean(Sub1Bean.class);
        ctx.getBean(Sub2Bean.class);
        ctx.getBean(Sub3Bean.class);
    }

    @Test
    public void testInitMethod() {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        // test @PostConstruct:
        var bean1 = ctx.getBean(AnnotationInitBean.class);
        var bean2 = ctx.getBean(SpecifyInitBean.class);
        assertEquals("Scan App / v1.0", bean1.appName);
        assertEquals("Scan App / v1.0", bean2.appName);
    }

    @Test
    public void testConverter() {
        var ctx = new AnnotationConfigApplicationContext(ScanApplication.class, createPropertyResolver());
        var bean = ctx.getBean(ValueConverterBean.class);

        assertNotNull(bean.injectedBoolean);
        assertTrue(bean.injectedBooleanPrimitive);
        assertTrue(bean.injectedBoolean);

        assertNotNull(bean.injectedByte);
        assertEquals((byte) 123, bean.injectedByte);
        assertEquals((byte) 123, bean.injectedBytePrimitive);

        assertNotNull(bean.injectedShort);
        assertEquals((short) 12345, bean.injectedShort);
        assertEquals((short) 12345, bean.injectedShortPrimitive);

        assertNotNull(bean.injectedInteger);
        assertEquals(1234567, bean.injectedInteger);
        assertEquals(1234567, bean.injectedIntPrimitive);

        assertNotNull(bean.injectedLong);
        assertEquals(123456789_000L, bean.injectedLong);
        assertEquals(123456789_000L, bean.injectedLongPrimitive);

        assertNotNull(bean.injectedFloat);
        assertEquals(12345.6789F, bean.injectedFloat, 0.0001F);
        assertEquals(12345.6789F, bean.injectedFloatPrimitive, 0.0001F);

        assertNotNull(bean.injectedDouble);
        assertEquals(123456789.87654321, bean.injectedDouble, 0.0000001);
        assertEquals(123456789.87654321, bean.injectedDoublePrimitive, 0.0000001);

        assertEquals(LocalDate.parse("2023-03-29"), bean.injectedLocalDate);
        assertEquals(LocalTime.parse("20:45:01"), bean.injectedLocalTime);
        assertEquals(LocalDateTime.parse("2023-03-29T20:45:01"), bean.injectedLocalDateTime);
        assertEquals(ZonedDateTime.parse("2023-03-29T20:45:01+08:00[Asia/Shanghai]"), bean.injectedZonedDateTime);
        assertEquals(Duration.parse("P2DT3H4M"), bean.injectedDuration);
        assertEquals(ZoneId.of("Asia/Shanghai"), bean.injectedZoneId);
    }
}