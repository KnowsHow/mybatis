## insert语句使用BatchExecutor的执行逻辑说明

#### 执行器的调用

```java
  @Override
  public int doUpdate(MappedStatement ms, Object parameterObject) throws SQLException {
    final Configuration configuration = ms.getConfiguration();
    // @desc: 获取StatementHandler
    final StatementHandler handler = configuration.newStatementHandler(this, ms, parameterObject, RowBounds.DEFAULT, null, null);
    final BoundSql boundSql = handler.getBoundSql();
    // @desc: 获取要执行的Sql
    final String sql = boundSql.getSql();
    final Statement stmt;

    // @desc: 执行的sql和上次执行的一致
    if (sql.equals(currentSql) && ms.equals(currentStatement)) {
      // @desc: 计当前次数
      int last = statementList.size() - 1;
      // @desc: 获取当前Statement
      stmt = statementList.get(last);
      // @desc: 事务处理
      applyTransactionTimeout(stmt);
      // @desc: 参数化
      handler.parameterize(stmt);//fix Issues 322
      // @desc: 获取本次的执行结果
      BatchResult batchResult = batchResultList.get(last);
      // @desc: 设置参数对象
      batchResult.addParameterObject(parameterObject);

    // @desc: 首次执行
    } else {
      // @desc: 获取数据库连接
      Connection connection = getConnection(ms.getStatementLog());
      // @desc: 处理器预处理
      stmt = handler.prepare(connection, transaction.getTimeout());
      // @desc: 处理器参数化
      handler.parameterize(stmt);    //fix Issues 322
      // @desc: 设置当前执行的sql
      currentSql = sql;
      // @desc: 设置当前执行的Statement
      currentStatement = ms;
      // @desc: Statement添加到ArrayList中
      statementList.add(stmt);
      // @desc: BatchResult添加到ArrayList中
      batchResultList.add(new BatchResult(ms, sql, parameterObject));
    }
    // @desc: 执行批处理
    handler.batch(stmt);
    // @desc: 返回可执行的批处理最大条数
    return BATCH_UPDATE_RETURN_VALUE;
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
  public void batch(Statement statement) throws SQLException {
    delegate.batch(statement);
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
   * @desc: 批处理
   */
  @Override
  public void batch(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.addBatch();
  }

 /**
  * @desc: 参数化
  */
  @Override
  public void parameterize(Statement statement) throws SQLException {
    // @desc: 参数处理器设置参数
    parameterHandler.setParameters((PreparedStatement) statement);
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
   * @desc: 参数化
   */
  @Override
  public void parameterize(Statement statement) throws SQLException {
    // N/A
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

  /**
   * @desc: 批处理
   */
  @Override
  public void batch(Statement statement) throws SQLException {
    CallableStatement cs = (CallableStatement) statement;
    cs.addBatch();
  }
```