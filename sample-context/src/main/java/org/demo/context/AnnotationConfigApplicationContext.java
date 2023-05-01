package org.demo.context;

import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.demo.annotation.*;
import org.demo.exception.BeanCreationException;
import org.demo.exception.BeanDefinitionException;
import org.demo.exception.BeanNotOfRequiredTypeException;
import org.demo.exception.NoUniqueBeanDefinitionException;
import org.demo.io.PropertyResolver;
import org.demo.io.ResourceResolver;
import org.demo.utils.ClassUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.stream.Collectors;

public class AnnotationConfigApplicationContext {

    protected final Logger logger = LoggerFactory.getLogger(getClass());

    protected final PropertyResolver propertyResolver;
    protected final Map<String, BeanDefinition> beans;

    public AnnotationConfigApplicationContext(Class<?> configClass, PropertyResolver propertyResolver) {
        this.propertyResolver = propertyResolver;

        // 扫描获取所有Bean的Class类型:
        final Set<String> beanClassNames = scanForClassNames(configClass);

        // 创建Bean的定义:
        this.beans = createBeanDefinitions(beanClassNames);
    }

    /**
     * 根据扫描的ClassName创建BeanDefinition
     */
    Map<String, BeanDefinition> createBeanDefinitions(Set<String> classNameSet) {
        // 创建名称与BeanDefinition映射的HashMap:
        Map<String, BeanDefinition> beanDefsMap = new HashMap<>();
        // 循环处理传入的类名集合:
        for (String className : classNameSet) {
            // 获取对应类的Class对象:
            Class<?> clazz = null;
            try {
                clazz = Class.forName(className);
            } catch (ClassNotFoundException e) {
                throw new BeanCreationException(e);
            }
            // 如果类是注解、枚举、接口或纪录，则跳过:
            if (clazz.isAnnotation() || clazz.isEnum() || clazz.isInterface() || clazz.isRecord()) {
                continue;
            }
            // 检查类是否标注了@Component注解:
            Component component = ClassUtils.findAnnotation(clazz, Component.class);
            if (component != null) {
                // 记录找到的组件:
                logger.atDebug().log("found component: {}", clazz.getName());
                // 检查类是否是抽象类:
                int mod = clazz.getModifiers();
                if (Modifier.isAbstract(mod)) {
                    throw new BeanDefinitionException("@Component class " + clazz.getName() + " must not be abstract.");
                }
                // 检查类是否是私有类:
                if (Modifier.isPrivate(mod)) {
                    throw new BeanDefinitionException("@Component class " + clazz.getName() + " must not be private.");
                }
                // 获取Bean的名称:
                String beanName = ClassUtils.getBeanName(clazz);
                // 创建BeanDefinition对象:
                var def = new BeanDefinition(
                        beanName, // Bean名称
                        clazz, // Bean的Class对象
                        getSuitableConstructor(clazz), // 获取最合适的构造方法
                        getOrder(clazz), // 获取@Order注解中的值
                        clazz.isAnnotationPresent(Primary.class), // 判断是否@Primary注解
                        null, // 初始化方法的名称
                        null, // 销毁方法的名称
                        ClassUtils.findAnnotationMethod(clazz, PostConstruct.class), // 获取@PostConstruct注解标注的方法
                        ClassUtils.findAnnotationMethod(clazz, PreDestroy.class) // 获取@PreDestroy注解标注的方法
                );
                // 将BeanDefinition添加到HashMap中:
                addBeanDefinitions(beanDefsMap, def);
                // 记录定义的Bean：
                logger.atDebug().log("define bean: {}", def);
                // 检查类是否标注了@Configuration注解:
                Configuration configuration = ClassUtils.findAnnotation(clazz, Configuration.class);
                // 带有@Configuration注解的Class，视为Bean的工厂，我们需要继续在scanFactoryMethods()中查找@Bean标注的方法
                if (configuration != null) {
                    // 扫描工厂方法并添加对应的BeanDefinition到HashMap中:
                    scanFactoryMethods(beanName, clazz, beanDefsMap);
                }
            }
        }
        // 返回HashMap:
        return beanDefsMap;
    }

    /**
     * Get public constructor or non-public constructor as fallback.
     */
    Constructor<?> getSuitableConstructor(Class<?> clazz) {
        Constructor<?>[] cons = clazz.getConstructors();
        if (cons.length == 0) {
            cons = clazz.getDeclaredConstructors();
            if (cons.length != 1) {
                throw new BeanDefinitionException("More than one constructor found in class " + clazz.getName() + ".");
            }
        }
        if (cons.length != 1) {
            throw new BeanDefinitionException("More than one public constructor found in class " + clazz.getName() + ".");
        }
        return cons[0];
    }

    /**
     * Scan factory method that annotated with @Bean:
     *
     * <code>
     * &#64;Configuration
     * public class Hello {
     *     @Bean
     *     ZoneId createZone() {
     *         return ZoneId.of("Z");
     *     }
     * }
     * </code>
     */
    void scanFactoryMethods(String factoryBeanName, Class<?> clazz, Map<String, BeanDefinition> beanDefsMap) {
        for (Method method : clazz.getDeclaredMethods()) {
            Bean bean = method.getAnnotation(Bean.class);
            if (bean != null) {
                int mod = method.getModifiers();
                if (Modifier.isAbstract(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be abstract.");
                }
                if (Modifier.isFinal(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be final.");
                }
                if (Modifier.isPrivate(mod)) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not be private.");
                }
                Class<?> beanClass = method.getReturnType();
                if (beanClass.isPrimitive()) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return primitive type.");
                }
                if (beanClass == void.class || beanClass == Void.class) {
                    throw new BeanDefinitionException("@Bean method " + clazz.getName() + "." + method.getName() + " must not return void.");
                }
                var def = new BeanDefinition(ClassUtils.getBeanName(method), beanClass, factoryBeanName, method, getOrder(method),
                        method.isAnnotationPresent(Primary.class),
                        // init method:
                        bean.initMethod().isEmpty() ? null : bean.initMethod(),
                        // destroy method:
                        bean.destroyMethod().isEmpty() ? null : bean.destroyMethod(),
                        // @PostConstruct / @PreDestroy method:
                        null, null);
                addBeanDefinitions(beanDefsMap, def);
                logger.atDebug().log("define bean: {}", def);
            }
        }
    }

    /**
     * Check and add bean definitions.
     */
    void addBeanDefinitions(Map<String, BeanDefinition> beanDefsMap, BeanDefinition def) {
        if (beanDefsMap.put(def.getName(), def) != null) {
            throw new BeanDefinitionException("Duplicate bean name: " + def.getName());
        }
    }

    /**
     * Get order by:
     *
     * <code>
     * &#64;Order(100)
     * &#64;Component
     * public class Hello {}
     * </code>
     */
    int getOrder(Class<?> clazz) {
        Order order = clazz.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    /**
     * Get order by:
     *
     * <code>
     * &#64;Order(100)
     * &#64;Bean
     * Hello createHello() {
     *     return new Hello();
     * }
     * </code>
     */
    int getOrder(Method method) {
        Order order = method.getAnnotation(Order.class);
        return order == null ? Integer.MAX_VALUE : order.value();
    }

    /**
     * Do component scan and return class names.
     */
    protected Set<String> scanForClassNames(Class<?> configClass) {
        // 获取要扫描的package名称:
        // 获取@ComponentScan注解，如果没有指定value，则默认为configClass所在的包
        ComponentScan scan = ClassUtils.findAnnotation(configClass, ComponentScan.class);
        final String[] scanPackages = scan == null || scan.value().length == 0 ? new String[] { configClass.getPackage().getName() } : scan.value();
        logger.atInfo().log("component scan in packages: {}", Arrays.toString(scanPackages));
        Set<String> classNameSet = new HashSet<>();
        for (String pkg : scanPackages) {
            // 扫描package:
            logger.atDebug().log("scan package: {}", pkg);
            // 构建ResourceResolver并扫描指定包下的类
            var rr = new ResourceResolver(pkg);
            List<String> classList = rr.scan(res -> {
                String name = res.name();
                // 如果这是一个类文件，则返回类名
                if (name.endsWith(".class")) {
                    return name.substring(0, name.length() - 6).replace("/", ".").replace("\\", ".");
                }
                return null;
            });
            // 打印日志
            if (logger.isDebugEnabled()) {
                classList.forEach((className) -> {
                    logger.debug("class found by component scan: {}", className);
                });
            }
            // 将扫描到的类名添加到集合中
            classNameSet.addAll(classList);
        }
        // 查找@Import(Xyz.class):
        // 获取@Import注解，如果有，则将指定的类添加到集合中
        Import importConfig = configClass.getAnnotation(Import.class);
        if (importConfig != null) {
            for (Class<?> importConfigClass : importConfig.value()) {
                String importClassName = importConfigClass.getName();
                if (classNameSet.contains(importClassName)) {
                    logger.warn("ignore import: " + importClassName + " for it is already been scanned.");
                } else {
                    logger.debug("class found by import: {}", importClassName);
                    classNameSet.add(importClassName);
                }
            }
        }
        return classNameSet;
    }

    boolean isConfigurationDefinition(BeanDefinition def) {
        return ClassUtils.findAnnotation(def.getBeanClass(), Configuration.class) != null;
    }

    /**
     * 根据Name查找BeanDefinition，如果Name不存在，返回null
     */
    @Nullable
    public BeanDefinition findBeanDefinition(String name) {
        return this.beans.get(name);
    }

    /**
     * 根据Name和Type查找BeanDefinition，如果Name不存在，返回null，如果Name存在，但Type不匹配，抛出异常。
     */
    @Nullable
    public BeanDefinition findBeanDefinition(String name, Class<?> requiredType) {
        BeanDefinition def = findBeanDefinition(name);
        if (def == null) {
            return null;
        }
        if (!requiredType.isAssignableFrom(def.getBeanClass())) {
            throw new BeanNotOfRequiredTypeException(String.format("Autowire required type '%s' but bean '%s' has actual type '%s'.", requiredType.getName(),
                    name, def.getBeanClass().getName()));
        }
        return def;
    }

    /**
     * 根据Type查找若干个BeanDefinition，返回0个或多个。
     */
    public List<BeanDefinition> findBeanDefinitions(Class<?> type) {
        return this.beans.values().stream()
                // filter by type and sub-type:
                .filter(def -> type.isAssignableFrom(def.getBeanClass()))
                // 排序:
                .sorted().collect(Collectors.toList());
    }

    /**
     * 根据Type查找某个BeanDefinition，如果不存在返回null，如果存在多个返回@Primary标注的一个，如果有多个@Primary标注，或没有@Primary标注但找到多个，均抛出NoUniqueBeanDefinitionException
     */
    @Nullable
    public BeanDefinition findBeanDefinition(Class<?> type) {
        List<BeanDefinition> defs = findBeanDefinitions(type);
        if (defs.isEmpty()) {
            return null;
        }
        if (defs.size() == 1) {
            return defs.get(0);
        }
        // more than 1 beans, require @Primary:
        List<BeanDefinition> primaryDefs = defs.stream().filter(def -> def.isPrimary()).collect(Collectors.toList());
        if (primaryDefs.size() == 1) {
            return primaryDefs.get(0);
        }
        if (primaryDefs.isEmpty()) {
            throw new NoUniqueBeanDefinitionException(String.format("Multiple bean with type '%s' found, but no @Primary specified.", type.getName()));
        } else {
            throw new NoUniqueBeanDefinitionException(String.format("Multiple bean with type '%s' found, and multiple @Primary specified.", type.getName()));
        }
    }
}
