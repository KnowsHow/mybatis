## insert、update、delete语句 执行器调用

#### DefaultSqlSession中执行器的调用

```java
  @Override
  public int insert(String statement, Object parameter) {
    return update(statement, parameter);
  }

  @Override
  public int delete(String statement, Object parameter) {
    return update(statement, parameter);
  }

  @Override
  public int update(String statement, Object parameter) {
    try {
      dirty = true;
      // @desc: 获取要执行的SQL的MappedStatement
      MappedStatement ms = configuration.getMappedStatement(statement);
      // @desc: 执行器调用update,参数对象为集合或者数组时单独封装到Map中,key=collection、list、array
      return executor.update(ms, wrapCollection(parameter));
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error updating database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }
```

#### CachingExecutor:执行器调用

```java
  @Override
  public int update(MappedStatement ms, Object parameterObject) throws SQLException {
    // @desc: 尝试清空二级缓存
    flushCacheIfRequired(ms);
    // @desc: 调用的BaseExecutor的子类执行器
    return delegate.update(ms, parameterObject);
  }
```

##### 说明
在配置了清空缓存时先清空二级缓存再进行增删改操作。

#### BaseExecutor:执行器调用

```java
  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    // @desc: 清空一级缓存
    clearLocalCache();
    return doUpdate(ms, parameter);
  }

  /**
   * @desc: 抽象方法由子类实现
   */
  protected abstract int doUpdate(MappedStatement ms, Object parameter)
      throws SQLException;

```
##### 说明
先清空一级缓存再进行增删改操作。


#### BatchExecutor:执行器的调用
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
##### 说明
1. 先判断当前执行的SQL与上次执行的SQL是否一致
    1. 一致
        1. 不再进行预编译
        2. 不再构建BatchResult
        3. 配置参数
        4. 应用事务
    2. 不一致
        1. 获取数据库连接
        2. 预处理SQL
        3. 设置参数
        4. 缓存当前Statement以便复用
        5. 构建BatchResult并缓存
2. 执行batch,返回批处理允许的最大条数


#### ReuseExecutor:执行器的调用

```java
  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    // @desc: 获取StatementHandler
    StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
    // @desc: 预处理SQL语句
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    // @desc: 处理器处理SQL语句
    return handler.update(stmt);
  }

  /**
   * @desc: 预处理SQL语句
   */
  private Statement prepareStatement(StatementHandler handler, Log statementLog) throws SQLException {
    Statement stmt;
    BoundSql boundSql = handler.getBoundSql();
    String sql = boundSql.getSql();
    // @desc: 判断该Sql是否已经使用并存储过
    if (hasStatementFor(sql)) {
      // @desc: 从statementMap中获取Sql
      stmt = getStatement(sql);
      // @desc: 应用于事务
      applyTransactionTimeout(stmt);
    // @desc: 否则则说明第一次执行Sql
    } else {
      // @desc: 获取数据库连接
      Connection connection = getConnection(statementLog);
      // @desc: 处理器预处理
      stmt = handler.prepare(connection, transaction.getTimeout());
      // @desc: 存储到statementMap
      putStatement(sql, stmt);
    }
    // @desc: 处理器处理器参数
    handler.parameterize(stmt);
    return stmt;
  }
```

#### SimpleExecutor:执行器的调用

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