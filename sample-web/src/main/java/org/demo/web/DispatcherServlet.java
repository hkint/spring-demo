package org.demo.web;

import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.demo.annotation.*;
import org.demo.context.ApplicationContext;
import org.demo.context.ConfigurableApplicationContext;
import org.demo.exception.ErrorResponseException;
import org.demo.exception.NestedRuntimeException;
import org.demo.exception.ServerErrorException;
import org.demo.exception.ServerWebInputException;
import org.demo.io.PropertyResolver;
import org.demo.utils.ClassUtils;
import org.demo.web.utils.JsonUtils;
import org.demo.web.utils.PathUtils;
import org.demo.web.utils.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DispatcherServlet extends HttpServlet {

    final Logger logger = LoggerFactory.getLogger(getClass());

    ApplicationContext applicationContext;

    List<Dispatcher> getDispatchers = new ArrayList<>();
    List<Dispatcher> postDispatchers = new ArrayList<>();

    ViewResolver viewResolver;

    String resourcePath;
    String faviconPath;


    public DispatcherServlet(ApplicationContext applicationContext, PropertyResolver properyResolver) {
        this.applicationContext = applicationContext;
        this.viewResolver = applicationContext.getBean(ViewResolver.class);

        this.resourcePath = properyResolver.getProperty("${summer.web.static-path:/static/}");
        this.faviconPath = properyResolver.getProperty("${summer.web.favicon-path:/favicon.ico}");
        if (!this.resourcePath.endsWith("/")) {
            this.resourcePath = this.resourcePath + "/";
        }
    }

    /**
     * 初始化方法，扫描 @Controller 和 @RestController 注解并添加到控制器列表
     * DispatcherServlet通过反射拿到一组Dispatcher对象
     *
     * @throws ServletException 如果在同一个类上找到了 @Controller 和 @RestController 注解，则抛出此异常
     */
    @Override
    public void init() throws ServletException {
        // 使用 logger 记录日志
        logger.info("DispatcherServlet 初始化 {}.", getClass().getName());

        // 扫描 @Controller 和 @RestController 注解:
        for (var def : ((ConfigurableApplicationContext) this.applicationContext).findBeanDefinitions(Object.class)) {
            Class<?> beanClass = def.getBeanClass();
            Object bean = def.getRequiredInstance();
            Controller controller = beanClass.getAnnotation(Controller.class);
            RestController restController = beanClass.getAnnotation(RestController.class);

            // 如果在同一个类上找到了 @Controller 和 @RestController 注解，则抛出异常
            if (controller != null && restController != null) {
                throw new ServletException("在类 " + beanClass.getName() + " 上找到了 @Controller 和 @RestController 注解");
            }

            // 如果是 @Controller 注解，则将其添加到控制器列表
            if (controller != null) {
                addController(false, def.getName(), bean);
            }

            // 如果是 @RestController 注解，则将其添加到控制器列表
            if (restController != null) {
                addController(true, def.getName(), bean);
            }
        }
    }

    @Override
    public void destroy() {
        this.applicationContext.close();
    }

    void addController(boolean isRest, String name, Object instance) throws ServletException {
        logger.info("add {} controller '{}': {}", isRest ? "REST" : "MVC", name, instance.getClass().getName());
        addMethods(isRest, name, instance, instance.getClass());
    }

    /**
     * 将 @GetMapping 和 @PostMapping 注解的方法添加到分派器列表中
     *
     * @param isRest   是否为 @RestController 注解的类
     * @param name     bean 的名称
     * @param instance bean 实例
     * @param type     bean 的类类型
     * @throws ServletException 如果方法不符合要求，则抛出此异常
     */
    void addMethods(boolean isRest, String name, Object instance, Class<?> type) throws ServletException {
        // 遍历该类声明的所有方法
        for (Method m : type.getDeclaredMethods()) {
            GetMapping get = m.getAnnotation(GetMapping.class);
            if (get != null) {
                checkMethod(m);
                // 将该方法添加到 GET 请求的分派器列表中
                this.getDispatchers.add(new Dispatcher("GET", isRest, instance, m, get.value()));
            }
            PostMapping post = m.getAnnotation(PostMapping.class);
            if (post != null) {
                checkMethod(m);
                // 将该方法添加到 POST 请求的分派器列表中
                this.postDispatchers.add(new Dispatcher("POST", isRest, instance, m, post.value()));
            }
        }
        // 递归查找父类并添加方法
        Class<?> superClass = type.getSuperclass();
        if (superClass != null) {
            addMethods(isRest, name, instance, superClass);
        }
    }

    /**
     * 确认方法是否符合要求
     *
     * @param m 要检查的方法
     * @throws ServletException 如果方法参数不为空，则抛出此异常
     */
    void checkMethod(Method m) throws ServletException {
        if (m.getParameterCount() != 0) {
            throw new ServletException("方法 " + m.getName() + " 不应该有参数");
        }
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doService(req, resp, this.getDispatchers);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doService(req, resp, this.postDispatchers);
    }

    /**
     * 处理客户端的请求
     * 入口方法，整个流程如下：
     * <p>
     * Servlet容器调用DispatcherServlet的service()方法处理HTTP请求；
     * service()根据GET或POST调用doGet()或doPost()方法；
     * 根据URL依次匹配Dispatcher，匹配后调用process()方法，获得返回值；
     * 根据返回值写入响应：
     * void或null返回值无需写入响应；
     * String或byte[]返回值直接写入响应（或重定向）；
     * REST类型写入JSON序列化结果；
     * ModelAndView类型调用ViewResolver写入渲染结果。
     * 未匹配到判断是否静态资源：
     * 符合静态目录（默认/static/）则读取文件，写入文件内容；
     * 网站图标（默认/favicon.ico）则读取.ico文件，写入文件内容；
     * 其他情况返回404。
     * <p>
     * 在处理的每一步都可以向HttpServletResponse写入响应，因此，后续步骤写入时，应判断前面的步骤是否已经写入并发送了HTTP Header。isCommitted()方法判断是否已经发送了响应。
     *
     * @param req         客户端请求
     * @param resp        服务端响应
     * @param dispatchers 分发器列表
     * @throws ServletException servlet异常
     * @throws IOException      IO异常
     */
    void doService(HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers) throws ServletException, IOException {
        String url = req.getRequestURI();
        try {
            doService(url, req, resp, dispatchers);
        } catch (ErrorResponseException e) {
            logger.warn("处理请求失败，状态码：" + e.statusCode + "，URL：" + url, e);
            if (!resp.isCommitted()) {
                resp.resetBuffer();
                resp.sendError(e.statusCode);
            }
        } catch (RuntimeException | ServletException | IOException e) {
            logger.warn("处理请求失败，URL：" + url, e);
            throw e;
        } catch (Exception e) {
            logger.warn("处理请求失败，URL：" + url, e);
            throw new NestedRuntimeException(e);
        }
    }

    /**
     * 处理客户端的请求
     *
     * @param url         客户端请求的URL
     * @param req         客户端请求
     * @param resp        服务端响应
     * @param dispatchers 分发器列表
     * @throws Exception 异常
     */
    void doService(String url, HttpServletRequest req, HttpServletResponse resp, List<Dispatcher> dispatchers) throws Exception {
        for (Dispatcher dispatcher : dispatchers) {
            Result result = dispatcher.process(url, req, resp);
            if (result.processed()) {
                Object r = result.returnObj();
                if (dispatcher.isRest) {
                    // 发送REST响应：
                    if (!resp.isCommitted()) {
                        resp.setContentType("application/json");
                    }
                    if (dispatcher.isResponseBody) {
                        if (r instanceof String s) {
                            // 作为响应正文发送：
                            PrintWriter pw = resp.getWriter();
                            pw.write(s);
                            pw.flush();
                        } else if (r instanceof byte[] data) {
                            // 作为响应正文发送：
                            ServletOutputStream output = resp.getOutputStream();
                            output.write(data);
                            output.flush();
                        } else {
                            // 错误：
                            throw new ServletException("无法处理REST结果，处理URL：" + url);
                        }
                    } else if (!dispatcher.isVoid) {
                        PrintWriter pw = resp.getWriter();
                        JsonUtils.writeJson(pw, r);
                        pw.flush();
                    }
                } else {
                    // 处理MVC：
                    if (!resp.isCommitted()) {
                        resp.setContentType("text/html");
                    }
                    if (r instanceof String s) {
                        if (dispatcher.isResponseBody) {
                            // 作为响应正文发送：
                            PrintWriter pw = resp.getWriter();
                            pw.write(s);
                            pw.flush();
                        } else if (s.startsWith("redirect:")) {
                            // 重定向：
                            resp.sendRedirect(s.substring(9));
                        } else {
                            // 错误：
                            throw new ServletException("无法处理String结果，处理URL：" + url);
                        }
                    } else if (r instanceof byte[] data) {
                        if (dispatcher.isResponseBody) {
                            // 作为响应正文发送：
                            ServletOutputStream output = resp.getOutputStream();
                            output.write(data);
                            output.flush();
                        } else {
                            // 错误：
                            throw new ServletException("无法处理byte[]结果，处理URL：" + url);
                        }
                    } else if (r instanceof ModelAndView mv) {
                        String view = mv.getViewName();
                        if (view.startsWith("redirect:")) {
                            // 重定向：
                            resp.sendRedirect(view.substring(9));
                        } else {
                            this.viewResolver.render(view, mv.getModel(), req, resp);
                        }
                    } else if (!dispatcher.isVoid && r != null) {
                        // 错误：
                        throw new ServletException("无法处理" + r.getClass().getName() + "结果，处理URL：" + url);
                    }
                    return;
                }
            }
        }
        // 未找到：
        resp.sendError(404, "未找到");
    }


    /**
     * PATH_VARIABLE：路径参数，从URL中提取；
     * REQUEST_PARAM：URL参数，从URL Query或Form表单提取；
     * REQUEST_BODY：REST请求参数，从Post传递的JSON提取；
     * SERVLET_VARIABLE：HttpServletRequest等Servlet API提供的参数，直接从DispatcherServlet的方法参数获得
     */
    enum ParamType {
        PATH_VARIABLE, REQUEST_PARAM, REQUEST_BODY, SERVLET_VARIABLE;
    }

    static class Dispatcher {
        final static Result NOT_PROCESSED = new Result(false, null);
        final Logger logger = LoggerFactory.getLogger(getClass());

        // 是否返回REST
        boolean isRest;
        // 是否有@ResponseBody
        boolean isResponseBody;
        // 是否返回void
        boolean isVoid;
        // URL正则匹配
        Pattern urlPattern;
        // Bean实例
        Object controller;
        // 处理方法
        Method handlerMethod;
        // 方法参数
        Param[] methodParameters;

        /**
         * 构造方法，创建一个请求处理器。
         *
         * @param httpMethod HTTP方法
         * @param isRest     是否是REST请求
         * @param controller 控制器对象
         * @param method     方法对象
         * @param urlPattern URL模式
         * @throws ServletException 如果方法注解有误
         */
        public Dispatcher(String httpMethod, boolean isRest, Object controller, Method method, String urlPattern) throws ServletException {
            this.isRest = isRest;
            this.isResponseBody = method.getAnnotation(ResponseBody.class) != null;
            this.isVoid = method.getReturnType() == void.class;
            this.urlPattern = PathUtils.compile(urlPattern);
            this.controller = controller;
            this.handlerMethod = method;
            Parameter[] params = method.getParameters();
            Annotation[][] paramsAnnos = method.getParameterAnnotations();
            this.methodParameters = new Param[params.length];
            for (int i = 0; i < params.length; i++) {
                this.methodParameters[i] = new Param(httpMethod, method, params[i], paramsAnnos[i]);
            }
            logger.atDebug().log("将 {} 映射到处理器 {}.{}", urlPattern, controller.getClass().getSimpleName(), method.getName());
            if (logger.isDebugEnabled()) {
                for (var p : this.methodParameters) {
                    logger.debug("> parameter: {}", p);
                }
            }
        }

        /**
         * 匹配URL路径并处理请求。
         *
         * @param url      要处理的URL
         * @param request  HttpServletRequest对象
         * @param response HttpServletResponse对象
         * @return 处理结果
         * @throws Exception 处理过程中可能会发生的异常
         */
        Result process(String url, HttpServletRequest request, HttpServletResponse response) throws Exception {
            // 用正则表达式匹配URL
            Matcher matcher = urlPattern.matcher(url);
            if (matcher.matches()) {
                // 构造参数列表
                Object[] arguments = new Object[this.methodParameters.length];
                for (int i = 0; i < arguments.length; i++) {
                    Param param = methodParameters[i];
                    // 根据参数类型进行不同的处理
                    arguments[i] = switch (param.paramType) {
                        case PATH_VARIABLE -> {
                            // 获取路径变量的值
                            try {
                                String s = matcher.group(param.name);
                                // 将字符串转换为指定类型
                                // yield 是 Java 12 中引入的新关键字，用于支持 switch 表达式的新语法。在 switch 表达式中，yield 用于返回一个值，并终止该分支的执行。它的作用类似于 return 语句，但是可以在表达式中使用，从而提供更简洁的代码。
                                yield convertToType(param.classType, s);
                            } catch (IllegalArgumentException e) {
                                throw new ServerWebInputException("找不到路径变量 '" + param.name + "'。");
                            }
                        }
                        case REQUEST_BODY -> {
                            // 读取请求体并解析为指定类型
                            BufferedReader reader = request.getReader();
                            yield JsonUtils.readJson(reader, param.classType);
                        }
                        case REQUEST_PARAM -> {
                            // 获取请求参数的值并转换为指定类型
                            String s = getOrDefault(request, param.name, param.defaultValue);
                            yield convertToType(param.classType, s);
                        }
                        case SERVLET_VARIABLE -> {
                            // 处理Servlet API相关对象
                            Class<?> classType = param.classType;
                            if (classType == HttpServletRequest.class) {
                                yield request;
                            } else if (classType == HttpServletResponse.class) {
                                yield response;
                            } else if (classType == HttpSession.class) {
                                yield request.getSession();
                            } else if (classType == ServletContext.class) {
                                yield request.getServletContext();
                            } else {
                                throw new ServerErrorException("无法确定参数类型：" + classType);
                            }
                        }
                    };
                }
                Object result = null;
                try {
                    // 调用控制器方法并获取返回值
                    result = this.handlerMethod.invoke(this.controller, arguments);
                } catch (InvocationTargetException e) {
                    // 如果控制器方法抛出异常，则抛出异常的原因
                    Throwable t = e.getCause();
                    if (t instanceof Exception ex) {
                        throw ex;
                    }
                    throw e;
                } catch (ReflectiveOperationException e) {
                    throw new ServerErrorException(e);
                }
                return new Result(true, result);
            }
            // 如果URL不匹配，则返回未处理结果
            return NOT_PROCESSED;
        }

        Object convertToType(Class<?> classType, String s) {
            if (classType == String.class) {
                return s;
            } else if (classType == boolean.class || classType == Boolean.class) {
                return Boolean.valueOf(s);
            } else if (classType == int.class || classType == Integer.class) {
                return Integer.valueOf(s);
            } else if (classType == long.class || classType == Long.class) {
                return Long.valueOf(s);
            } else if (classType == byte.class || classType == Byte.class) {
                return Byte.valueOf(s);
            } else if (classType == short.class || classType == Short.class) {
                return Short.valueOf(s);
            } else if (classType == float.class || classType == Float.class) {
                return Float.valueOf(s);
            } else if (classType == double.class || classType == Double.class) {
                return Double.valueOf(s);
            } else {
                throw new ServerErrorException("Could not determine argument type: " + classType);
            }
        }

        String getOrDefault(HttpServletRequest request, String name, String defaultValue) {
            String s = request.getParameter(name);
            if (s == null) {
                if (WebUtils.DEFAULT_PARAM_VALUE.equals(defaultValue)) {
                    throw new ServerWebInputException("Request parameter '" + name + "' not found.");
                }
                return defaultValue;
            }
            return s;
        }
    }


    /**
     * Param类，表示方法参数。
     */
    static class Param {

        /**
         * 参数名。
         */
        String name;

        /**
         * 参数类型。
         */
        ParamType paramType;

        /**
         * 参数类。
         */
        Class<?> classType;

        /**
         * 参数默认值。
         */
        String defaultValue;

        /**
         * 构造方法，根据方法参数的注解构造Param对象。
         *
         * @param httpMethod  HTTP方法
         * @param method      方法对象
         * @param parameter   方法参数对象
         * @param annotations 参数注解数组
         * @throws ServletException 如果参数注解有误
         */
        public Param(String httpMethod, Method method, Parameter parameter, Annotation[] annotations) throws ServletException {
            PathVariable pv = ClassUtils.getAnnotation(annotations, PathVariable.class);
            RequestParam rp = ClassUtils.getAnnotation(annotations, RequestParam.class);
            RequestBody rb = ClassUtils.getAnnotation(annotations, RequestBody.class);
            // 应该只有一个注解
            int total = (pv == null ? 0 : 1) + (rp == null ? 0 : 1) + (rb == null ? 0 : 1);
            if (total > 1) {
                throw new ServletException("方法 " + method + " 中不能同时使用 @PathVariable、@RequestParam 和 @RequestBody 注解");
            }
            // 获取参数类型和参数名
            this.classType = parameter.getType();
            if (pv != null) {
                this.name = pv.value();
                this.paramType = ParamType.PATH_VARIABLE;
            } else if (rp != null) {
                this.name = rp.value();
                this.defaultValue = rp.defaultValue();
                this.paramType = ParamType.REQUEST_PARAM;
            } else if (rb != null) {
                this.paramType = ParamType.REQUEST_BODY;
            } else {
                this.paramType = ParamType.SERVLET_VARIABLE;
                // 检查Servlet变量类型
                if (this.classType != HttpServletRequest.class && this.classType != HttpServletResponse.class && this.classType != HttpSession.class
                        && this.classType != ServletContext.class) {
                    throw new ServerErrorException("方法 " + method + " 中的参数类型 " + classType + " 不支持");
                }
            }
        }
    }

    record Result(boolean processed, Object returnObj) {
    }
}