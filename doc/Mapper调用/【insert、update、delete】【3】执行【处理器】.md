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

#### 预编译类型:PreparedStatementHandler

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
      // @desc: 执行自动生成键
      if (keyColumnNames == null) {
        return connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
      } else {
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
  public int update(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.execute();
    // @desc: 只获取影响的行数
    int rows = ps.getUpdateCount();
    Object parameterObject = boundSql.getParameterObject();
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    // @desc: 键的后置处理:指定属性设置指定列的数据库返回值
    keyGenerator.processAfter(executor, mappedStatement, ps, parameterObject);
    return rows;
  }

  /**
   * @desc: 批处理
   */
  @Override
  public void batch(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.addBatch();
  }
```

#### 简单类型:SimpleStatementHandler

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

  /**
   * @desc: SQL语句执行
   */
  @Override
  public int update(Statement statement) throws SQLException {
    // @desc: 即将执行的Sql
    String sql = boundSql.getSql();
    // @desc: 封装在BoundSql中的参数变量
    Object parameterObject = boundSql.getParameterObject();
    // @desc: 键生成器
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    int rows;
    // @desc: Jdbc3KeyGenerator:数据库自增的键生成器
    if (keyGenerator instanceof Jdbc3KeyGenerator) {
      // @desc: 先执行
      statement.execute(sql, Statement.RETURN_GENERATED_KEYS);
      // @desc: 获取影响的行数
      rows = statement.getUpdateCount();
      // @desc: 键的后置处理:指定属性设置指定列的数据库返回值
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    // @desc: SelectKeyGenerator:执行SQL生成键的键生成器
    } else if (keyGenerator instanceof SelectKeyGenerator) {
      // @desc: 执行SQL
      statement.execute(sql);
      // @desc: 获取影响的行数
      rows = statement.getUpdateCount();
      // @desc: 键的后置处理:指定属性设置指定列的数据库返回值
      keyGenerator.processAfter(executor, mappedStatement, statement, parameterObject);
    // @desc: 其他类型或者NoKeyGenerator
    } else {
      // @desc: 执行SQL
      statement.execute(sql);
      // @desc: 获取影响行数
      rows = statement.getUpdateCount();
    }
    // @desc: 返回执行SQL后影响的行数
    return rows;
  }

  /**
   * @desc: 批处理
   */
  @Override
  public void batch(Statement statement) throws SQLException {
    String sql = boundSql.getSql();
    statement.addBatch(sql);
  }
```

#### 存储过程类型:CallableStatementHandler

```java

  /**
   * @desc: SQL语句初始化
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    // @desc: 获取Sql并执行
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
  public int update(Statement statement) throws SQLException {
    CallableStatement cs = (CallableStatement) statement;
    cs.execute();
    // @desc: 获取影响行数
    int rows = cs.getUpdateCount();
    Object parameterObject = boundSql.getParameterObject();
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    // @desc: 键生成器后置处理
    keyGenerator.processAfter(executor, mappedStatement, cs, parameterObject);
    // @desc: 存储过程参数处理
    resultSetHandler.handleOutputParameters(cs);
    // @desc: 返回影响的行数
    return rows;
  }

  /**
   * @desc: 批处理
   */
  @Override
  public void batch(Statement statement) throws SQLException {
    CallableStatement cs = (CallableStatement) statement;
    cs.addBatch();
  }
```

####  总结
* 批处理
    1. 只能执行增删改操作
    2. 返回值只是批处理执行的最大条数与执行的SQL语句无关
    3. 能复用重复的sql
* 其他
    1. 配置了二级缓存则在操作之前清空二级缓存
    2. 清空一级缓存
    3. 获取配置对象Configuration
    2. 配置对象构建StatementHandler:路由类型处理器
    3. 根据statementType构建对应的委托类处理器
    4. 构建中完成statementHandler属性配置
        * 键生成器前置调用
        * 构建参数处理器
        * 构建结果集处理器
        * 获取类型处理器
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
        * 执行SQL
    11. 键生成器后置调用
    11. 存储过程参数处理
    12. 返回受影响的行数
    12. 关闭连接




