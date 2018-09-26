##  SQL语句处理器:StatementHandler

#### 构建说明:Configuration#newStatementHandler

```java
  public StatementHandler newStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    // @desc: 构建路由类型处理器
    StatementHandler statementHandler = new RoutingStatementHandler(executor, mappedStatement, parameterObject, rowBounds, resultHandler, boundSql);
    // @desc: 处理插件
    statementHandler = (StatementHandler) interceptorChain.pluginAll(statementHandler);
    return statementHandler;
  }
```

##### 路由类初始化

```java
  public RoutingStatementHandler(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    switch (ms.getStatementType()) {
      // @desc: 根据StatementType使用不同的处理器
      case STATEMENT:
        // @desc: 简单的SQL语句处理器
        delegate = new SimpleStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        break;
      case PREPARED:
        // @desc: 预编译的SQL语句处理器
        delegate = new PreparedStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        break;
      case CALLABLE:
        // @desc: 存储过程的SQL语句处理器
        delegate = new CallableStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        break;
      default:
        throw new ExecutorException("Unknown statement type: " + ms.getStatementType());
    }
  }
```

#### 委托类初始化

```java
  /**
   * @desc: 构造器
   * executor: 执行器
   * mappedStatement SQL语句解析后的Java实体
   * parameterObject 参数对象
   * rowBounds 分页对象
   * resultHandler 结果处理器
   * boundSql BoundSql对象
   */
  protected BaseStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    this.configuration = mappedStatement.getConfiguration();
    this.executor = executor;
    this.mappedStatement = mappedStatement;
    this.rowBounds = rowBounds;
    this.typeHandlerRegistry = configuration.getTypeHandlerRegistry();
    this.objectFactory = configuration.getObjectFactory();
    if (boundSql == null) { // issue #435, get the key before calculating the statement
      // @desc: 处理键生成器
      generateKeys(parameterObject);
      // @desc: 获取要执行的SQL,传入parameterObject(调用SQL源入口)
      boundSql = mappedStatement.getBoundSql(parameterObject);
    }
    this.boundSql = boundSql;
    // @desc: 参数处理器
    this.parameterHandler = configuration.newParameterHandler(mappedStatement, parameterObject, boundSql);
    // @desc: 结果集处理器
    this.resultSetHandler = configuration.newResultSetHandler(executor, mappedStatement, rowBounds, parameterHandler, resultHandler, boundSql);
  }

  /**
   * @desc: 处理键生成器
   */
  protected void generateKeys(Object parameter) {
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    ErrorContext.instance().store();
    // @desc: 尝试执行Before类型的键生成
    keyGenerator.processBefore(executor, mappedStatement, null, parameter);
    ErrorContext.instance().recall();
  }

 /**
  * @desc: BaseStatementHandler已实现的prepare方法
  */
  @Override
  public Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException {
    ErrorContext.instance().sql(boundSql.getSql());
    Statement statement = null;
    try {
      // @desc: 初始化SQL语句
      statement = instantiateStatement(connection);
      // @desc: 设置SQL执行超时时间
      setStatementTimeout(statement, transactionTimeout);
      // @desc: 设置影响行数最大值
      setFetchSize(statement);
      return statement;
    } catch (SQLException e) {
      closeStatement(statement);
      throw e;
    } catch (Exception e) {
      closeStatement(statement);
      throw new ExecutorException("Error preparing statement.  Cause: " + e, e);
    }
  }
  /**
   * @desc: 子类实现具体的初始化SQL语句
   */
  protected abstract Statement instantiateStatement(Connection connection) throws SQLException;
```
#### 委托类处理器调用

##### 预编译类型:PreparedStatementHandler

```java

  /**
   * @desc: SQL语句初始化
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    // @desc: 获取要执行的Sql
    String sql = boundSql.getSql();
    // @desc: 数据库自增键
    if (mappedStatement.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
      String[] keyColumnNames = mappedStatement.getKeyColumns();
      // @desc: 执行数据库键
      if (keyColumnNames == null) {
        // @desc: 执行预编译的sql
        return connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
      } else {
        // @desc: 执行预编译的sql,指定数据库键
        return connection.prepareStatement(sql, keyColumnNames);
      }
    // @desc: 非数据库自增键且指定ResultSetType
    } else if (mappedStatement.getResultSetType() != null) {
      return connection.prepareStatement(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    // @desc: 其他
    } else {
      return connection.prepareStatement(sql);
    }
  }

 /**
  * @desc: 参数化
  */
  @Override
  public void parameterize(Statement statement) throws SQLException {
    // @desc: 参数处理器设置参数
    parameterHandler.setParameters((PreparedStatement) statement);
  }
  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.execute();
    return resultSetHandler.<E> handleResultSets(ps);
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.execute();
    return resultSetHandler.<E> handleCursorResultSets(ps);
  }
```
##### 说明

1. 执行statement
2. 调用初始化时注册的resultSetHandler处理结果集

##### 简单类型:SimpleStatementHandler

```java

  /**
   * @desc: SQL语句初始化
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    // @desc: 构建Statement
    if (mappedStatement.getResultSetType() != null) {
      return connection.createStatement(mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    } else {
      return connection.createStatement();
    }
  }

  /**
   * @desc: SQL语句参数化
   */
  @Override
  public void parameterize(Statement statement) throws SQLException {
    // N/A
  }

  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    String sql = boundSql.getSql();
    statement.execute(sql);
    return resultSetHandler.<E>handleResultSets(statement);
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.execute(sql);
    return resultSetHandler.<E>handleCursorResultSets(statement);
  }
```

##### 说明
1. 获取要执行的sql
2. 执行sql
3. 调用初始化时注册的resultSetHandler处理结果集

##### 存储过程类型:CallableStatementHandler

```java

  /**
   * @desc: SQL语句初始化
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    // @desc: 获取Sql并执行预处理SQL
    String sql = boundSql.getSql();
    if (mappedStatement.getResultSetType() != null) {
      return connection.prepareCall(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    } else {
      return connection.prepareCall(sql);
    }
  }

  /**
   * @desc: 参数化
   */
  @Override
  public void parameterize(Statement statement) throws SQLException {
    // @desc: 给statement注册存储过程的参数
    registerOutputParameters((CallableStatement) statement);
    // @desc: 参数处理器设置参数
    parameterHandler.setParameters((CallableStatement) statement);
  }

  @Override
  public <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException {
    CallableStatement cs = (CallableStatement) statement;
    cs.execute();
    List<E> resultList = resultSetHandler.<E>handleResultSets(cs);
    resultSetHandler.handleOutputParameters(cs);
    return resultList;
  }

  @Override
  public <E> Cursor<E> queryCursor(Statement statement) throws SQLException {
    CallableStatement cs = (CallableStatement) statement;
    cs.execute();
    Cursor<E> resultList = resultSetHandler.<E>handleCursorResultSets(cs);
    resultSetHandler.handleOutputParameters(cs);
    return resultList;
  }
```

##### 说明
1. 执行CallableStatement
2. 调用初始化时注册的resultSetHandler处理结果集
3. 调用初始化时注册的resultSetHandler处理存储过程的参数


### 总结
1. 从二级缓存中获取结果
    * 存在则返回
    * 不存在则继续
1. 从MappedStatement中获取配置对象
2. 配置对象构建StatementHandler:路由类型处理器
3. 根据statementType构建对应的委托类处理器
4. 构建中完成statementHandler属性配置
5. 处理插件
6. 获取数据库连接
    * 复用型执行器复用成功直接进行到调用处理器的parameterize方法
7. 调用处理器的prepare方法
    * 非简单类型的处理器会在这里预编译SQL
    * 处理数据库键与指定的ResultSetType
8. 调用处理器的parameterize方法
    * 非简单类型设置预编译中的参数
    * 简单类型无预编译因此这里为空实现
9. 执行SQL语句
    *  执行SQL
10. 结果集处理器处理
11. 二级缓存开启则放入缓存
11. 存储过程参数处理
12. 关闭连接