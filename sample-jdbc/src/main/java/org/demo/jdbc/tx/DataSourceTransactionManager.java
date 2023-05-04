package org.demo.jdbc.tx;

import org.demo.exception.TransactionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.SQLException;


public class DataSourceTransactionManager implements PlatformTransactionManager, InvocationHandler {
    // 用于存储当前线程的事务状态
    static final ThreadLocal<TransactionStatus> transactionStatus = new ThreadLocal<>();
    // 获取日志记录器
    final Logger logger = LoggerFactory.getLogger(getClass());
    // 数据源
    final DataSource dataSource;
    /**
     * 构造方法
     * @param dataSource 数据源
     */
    public DataSourceTransactionManager(DataSource dataSource) {
        this.dataSource = dataSource;
    }
    /**
     * 代理方法调用
     * @param proxy 代理对象
     * @param method 方法对象
     * @param args 方法参数
     * @return 方法执行结果
     * @throws Throwable 抛出异常
     */
    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        TransactionStatus ts = transactionStatus.get();
        if (ts == null) {
            // 当前无事务,开启新事务
            try (Connection connection = dataSource.getConnection()) {
                final boolean autoCommit = connection.getAutoCommit();
                if (autoCommit) {
                    connection.setAutoCommit(false);
                }
                try {
                    // 设置ThreadLocal状态
                    transactionStatus.set(new TransactionStatus(connection));
                    // 调用业务方法
                    Object r = method.invoke(proxy, args);
                    // 提交事务
                    connection.commit();
                    // 方法返回
                    return r;
                } catch (InvocationTargetException e) {
                    // 回滚事务
                    logger.warn("由于异常原因，将回滚事务：{}", e.getCause() == null ? "null" : e.getCause().getClass().getName());
                    TransactionException te = new TransactionException(e.getCause());
                    try {
                        connection.rollback();
                    } catch (SQLException sqle) {
                        te.addSuppressed(sqle);
                    }
                    throw te;
                } finally {
                    // 删除 ThreadLocal 状态
                    transactionStatus.remove();
                    if (autoCommit) {
                        connection.setAutoCommit(true);
                    }
                }
            }
        } else {
            // 当前已有事务,加入当前事务执行:
            return method.invoke(proxy, args);
        }
    }
}
