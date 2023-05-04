package org.demo.web;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.demo.context.ApplicationContext;
import org.demo.io.PropertyResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;

public class DispatcherServlet extends HttpServlet {

    final Logger logger = LoggerFactory.getLogger(getClass());

    ApplicationContext applicationContext;

    public DispatcherServlet(ApplicationContext applicationContext, PropertyResolver properyResolver) {
        this.applicationContext = applicationContext;
    }

    @Override
    public void destroy() {
        this.applicationContext.close();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        logger.info("{} {}", req.getMethod(), req.getRequestURI());
        PrintWriter pw = resp.getWriter();
        pw.write("<h1>Hello, World !</h1>");
        pw.flush();
    }
}