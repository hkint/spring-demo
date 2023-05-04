package org.demo.jdbc;

import org.demo.exception.DataAccessException;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class JdbcTemplate {
    final DataSource dataSource;

    public JdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    /**
     * 以回调作为参数的模板方法
     *
     * @param action 在连接上执行的回调操作
     * @param <T>    回调返回类型
     * @return 回调操作的结果
     * @throws DataAccessException 当连接时发生SQL异常时抛出
     */
    public <T> T execute(ConnectionCallback<T> action) {
        try (Connection newConn = dataSource.getConnection()) {
            T result = action.doInConnection(newConn); // 在新连接上执行给定的回调操作
            return result; // 返回操作的结果
        } catch (SQLException e) {
            throw new DataAccessException(e); // 抛出数据库访问异常
        }
    }

    /**
     * 实现了ConnectionCallback，内部又调用了传入的PreparedStatementCreator和PreparedStatementCallback
     * 执行给定的PreparedStatementCreator和给定的PreparedStatementCallback回调操作，
     * 并返回回调操作的结果。使用try-with-resources语句确保资源的正确关闭。
     *
     * @param psc    对于创建新的预处理语句的预处理语句创建器
     * @param action 在新预处理语句上执行的回调操作
     * @param <T>    回调返回类型
     * @return 回调操作的结果
     * @throws DataAccessException 当连接时发生SQL异常时抛出
     */
    public <T> T execute(PreparedStatementCreator psc, PreparedStatementCallback<T> action) {
        return execute((Connection con) -> { // 执行在连接上进行操作的lambda表达式
            try (PreparedStatement ps = psc.createPreparedStatement(con)) { // 创建新预处理语句并在try-with-resources语句中使用
                return action.doInPreparedStatement(ps); // 在预处理语句上执行给定的回调操作
            }
        });
    }

    /**
     * 使用给定的SQL语句和参数数组执行更新操作，
     * 并返回更新操作的结果。
     *
     * @param sql  要执行的SQL语句
     * @param args 用于填充SQL语句占位符的参数数组
     * @return 更新操作的结果
     * @throws DataAccessException 当连接时发生SQL异常时抛出
     */
    public int update(String sql, Object... args) {
        return execute( // 执行execute方法并传入PreparedStatementCreator和PreparedStatementCallback回调
                preparedStatementCreator(sql, args), // 使用预处理语句创建器创建新的预处理语句
                (PreparedStatement ps) -> { // 在预处理语句上执行给定的回调操作
                    return ps.executeUpdate(); // 执行更新操作并返回更新操作的结果
                }
        );
    }

    /**
     * 先创建一个 PreparedStatementCreator 对象，该对象的作用是创建一个带有自动生成主键功能的 PreparedStatement 对象。
     * 具体实现是通过 Connection.prepareStatement(String sql, int autoGeneratedKeys) 方法创建 PreparedStatement 对象，并将 autoGeneratedKeys 参数设置为 Statement.RETURN_GENERATED_KEYS
     * 然后将 SQL 语句中的占位符绑定上实际参数。接着执行 SQL 更新操作，将更新的行数 n 保存下来。
     * 如果更新的行数 n 为 0，则抛出异常；如果更新的行数 n 大于 1，则抛出异常。
     * 否则，通过 PreparedStatement.getGeneratedKeys() 方法获取自动生成的主键，然后返回第一个主键值。如果没有自动生成的主键，则抛出异常。
     * <p>
     * 执行 SQL 更新操作，并返回自动生成的主键
     *
     * @param sql  SQL 语句
     * @param args SQL 语句中占位符对应的参数
     * @return 返回自动生成的主键
     * @throws DataAccessException 数据访问异常
     */
    public Number updateAndReturnGeneratedKey(String sql, Object... args) throws DataAccessException {
        return execute(
                // PreparedStatementCreator
                (Connection con) -> {
                    var ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    bindArgs(ps, args);
                    return ps;
                },
                // PreparedStatementCallback
                (PreparedStatement ps) -> {
                    int n = ps.executeUpdate();
                    if (n == 0) {
                        throw new DataAccessException("0 rows inserted.");
                    }
                    if (n > 1) {
                        throw new DataAccessException("Multiple rows inserted.");
                    }
                    try (ResultSet keys = ps.getGeneratedKeys()) {
                        while (keys.next()) {
                            return (Number) keys.getObject(1);
                        }
                    }
                    throw new DataAccessException("Should not reach here.");
                });
    }


    /**
     * 查询数据库，返回结果集对应的 List 集合
     *
     * @param sql       SQL 语句
     * @param rowMapper 结果集映射器
     * @param args      SQL 语句中占位符对应的参数
     * @param <T>       泛型参数，表示结果集中一行数据对应的类型
     * @return 返回结果集对应的 List 集合
     */
    public <T> List<T> queryForList(String sql, RowMapper<T> rowMapper, Object... args) {
        return execute(preparedStatementCreator(sql, args),
                (PreparedStatement ps) -> {
                    List<T> list = new ArrayList<>();
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            list.add(rowMapper.mapRow(rs, rs.getRow()));
                        }
                    }
                    return list;
                }
        );
    }

    public <T> List<T> queryForList(String sql, Class<T> clazz, Object... args) throws DataAccessException {
        return queryForList(sql, new BeanRowMapper<>(clazz), args);
    }

    /**
     * 根据 clazz 的类型选择合适的结果对象映射器
     * <p>
     * 查询数据库并返回一个结果对象
     *
     * @param sql   SQL 语句
     * @param clazz 结果对象的类型
     * @param args  SQL 语句中占位符对应的参数
     * @param <T>   泛型参数，表示结果对象的类型
     * @return 返回一个结果对象
     * @throws DataAccessException 数据访问异常
     */
    @SuppressWarnings("unchecked")
    public <T> T queryForObject(String sql, Class<T> clazz, Object... args) throws DataAccessException {
        if (clazz == String.class) {
            return (T) queryForObject(sql, StringRowMapper.instance, args);
        }
        if (clazz == Boolean.class || clazz == boolean.class) {
            return (T) queryForObject(sql, BooleanRowMapper.instance, args);
        }
        if (Number.class.isAssignableFrom(clazz) || clazz.isPrimitive()) {
            return (T) queryForObject(sql, NumberRowMapper.instance, args);
        }
        return queryForObject(sql, new BeanRowMapper<>(clazz), args);
    }

    /**
     * 通过调用 execute(PreparedStatementCreator psc, PreparedStatementCallback<T> action) 方法来实现
     * PreparedStatementCreator 接口用于创建 PreparedStatement 对象，PreparedStatementCallback 接口用于执行 PreparedStatement 对象并返回一个结果对象。
     * 调用 execute 方法执行 SQL 语句，将结果集中的第一行数据映射为结果对象，如果结果集中有多行数据则抛出异常，如果结果集为空则抛出异常
     * <p>
     * 查询数据库并返回一个结果对象
     *
     * @param sql       SQL 语句
     * @param rowMapper 结果对象映射器
     * @param args      SQL 语句中占位符对应的参数
     * @param <T>       泛型参数，表示结果对象的类型
     * @return 返回一个结果对象
     * @throws DataAccessException 数据访问异常
     */
    public <T> T queryForObject(String sql, RowMapper<T> rowMapper, Object... args) throws DataAccessException {
        return execute(preparedStatementCreator(sql, args),
                // PreparedStatementCallback
                (PreparedStatement ps) -> {
                    T t = null;
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            if (t == null) {
                                t = rowMapper.mapRow(rs, rs.getRow());
                            } else {
                                throw new DataAccessException("Multiple rows found.");
                            }
                        }
                    }
                    if (t == null) {
                        throw new DataAccessException("Empty result set.");
                    }
                    return t;
                });
    }

    /**
     * 查询数据库并返回一个数字类型的结果对象
     *
     * @param sql  SQL 语句
     * @param args SQL 语句中占位符对应的参数
     * @return 返回一个数字类型的结果对象
     * @throws DataAccessException 数据访问异常
     */
    public Number queryForNumber(String sql, Object... args) throws DataAccessException {
        return queryForObject(sql, NumberRowMapper.instance, args);
    }


    /**
     * 根据给定的 SQL 语句和参数创建 PreparedStatementCreator
     * 使用了 Lambda 表达式来实现 PreparedStatementCreator 接口。
     * 在 Lambda 表达式中，先创建了一个 PreparedStatement 实例，然后调用  bindArgs()  方法将参数绑定到 PreparedStatement 中，最后返回 PreparedStatement 实例
     *
     * @param sql  SQL 语句
     * @param args 参数
     * @return PreparedStatementCreator
     */
    private PreparedStatementCreator preparedStatementCreator(String sql, Object... args) {
        return (Connection con) -> {
            var ps = con.prepareStatement(sql);
            bindArgs(ps, args);
            return ps;
        };
    }

    /**
     * 绑定参数到 PreparedStatement 中。
     *
     * @param ps   PreparedStatement 实例
     * @param args 参数
     * @throws SQLException
     */
    private void bindArgs(PreparedStatement ps, Object... args) throws SQLException {
        for (int i = 0; i < args.length; i++) {
            ps.setObject(i + 1, args[i]);
        }
    }

}

class StringRowMapper implements RowMapper<String> {

    static StringRowMapper instance = new StringRowMapper();

    @Override
    public String mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getString(1);
    }
}

class BooleanRowMapper implements RowMapper<Boolean> {

    static BooleanRowMapper instance = new BooleanRowMapper();

    @Override
    public Boolean mapRow(ResultSet rs, int rowNum) throws SQLException {
        return rs.getBoolean(1);
    }
}

class NumberRowMapper implements RowMapper<Number> {

    static NumberRowMapper instance = new NumberRowMapper();

    @Override
    public Number mapRow(ResultSet rs, int rowNum) throws SQLException {
        return (Number) rs.getObject(1);
    }
}