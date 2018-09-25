## insert语句使用SimpleExecutor的执行逻辑说明

#### 执行器的调用

```java
  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      // @desc: 获取StatementHandler
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      // @desc: 预处理SQL语句
      stmt = prepareStatement(handler, ms.getStatementLog());
      // @desc: 处理器处理SQL语句
      return handler.update(stmt);
    } finally {
      closeStatement(stmt);
    }
  }

  /**
   * @desc: 预处理
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    Connection connection = getConnection(statementLog);
    // @desc: 调用初始化SQL语句、尝试使用键的Before生成、启用超时时间、启用受影响的最大行数,该方法为父类公共方法
    stmt = handler.prepare(connection, transaction.getTimeout());
    // @desc: 调用具体子类的参数化流程
    handler.parameterize(stmt);
    return stmt;
  }
```


##### 处理器的初始化

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

#### 委托处理器的初始化

```java
  /**
   * @desc: 构造器
   * executor: 执行器
   * mappedStatement SQL语句解析后的Java实体
   * parameterObject 参数对象
   * rowBounds 分页对象
   * resultHandler 结果集处理器
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
```


#### 处理器的调用

```
  @Override
  public Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException {
    return delegate.prepare(connection, transactionTimeout);
  }

  @Override
  public void parameterize(Statement statement) throws SQLException {
    delegate.parameterize(statement);
  }

  @Override
  public int update(Statement statement) throws SQLException {
    return delegate.update(statement);
  }
```


#### BaseStatementHandler#prepare

```java

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
```