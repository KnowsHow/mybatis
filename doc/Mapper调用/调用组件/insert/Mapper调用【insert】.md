## insert方法的调用

#### 入口
```java
case INSERT: {
      Object param = method.convertArgsToSqlCommandParam(args);
        // @desc: rowCountResult是对返回结果的处理
        // @desc: SQL的执行使用的是SqlSession
        // @desc: command.getName()=MapperStatement的ID或者null
        // @desc: param为参数解析后的解析对象
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
```

#### DefaultSqlSession.insert
```java
  @Override
  public int insert(String statement, Object parameter) {
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
#### 调用BaseExecutor的update方法
```java
  @Override
  public int update(MappedStatement ms, Object parameter) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing an update").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }
    clearLocalCache();
    return doUpdate(ms, parameter);
  }

  /**
   * @desc: 抽象方法由子类实现
   */
  protected abstract int doUpdate(MappedStatement ms, Object parameter)
      throws SQLException;

```

#### 子类: SimpleExecutor

```java
  @Override
  public int doUpdate(MappedStatement ms, Object parameter) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      // @desc: 获取StatementHandler
      StatementHandler handler = configuration.newStatementHandler(this, ms, parameter, RowBounds.DEFAULT, null, null);
      // @desc: 预处理
      stmt = prepareStatement(handler, ms.getStatementLog());
      // @desc: 调用StatementHandler的update
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
    // @desc: 调用StatementHandler的prepare,启用超时时间
    stmt = handler.prepare(connection, transaction.getTimeout());
    // @desc: 调用parameterize获取数据库连接并将参数传入
    handler.parameterize(stmt);
    return stmt;
  }

  /**
   * @desc: 父类的prepare
   */
  @Override
  public Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException {
    ErrorContext.instance().sql(boundSql.getSql());
    Statement statement = null;
    try {
      // @desc: 实例化
      statement = instantiateStatement(connection);
      // @desc: 设置超时
      setStatementTimeout(statement, transactionTimeout);
      // @desc: 设置影响最大行数
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
   * @desc: 实例化Statement
   */
  @Override
  protected Statement instantiateStatement(Connection connection) throws SQLException {
    // @desc: 获取要执行的SQL
    String sql = boundSql.getSql();
    // @desc: 有selectKey键
    if (mappedStatement.getKeyGenerator() instanceof Jdbc3KeyGenerator) {
      String[] keyColumnNames = mappedStatement.getKeyColumns();
      if (keyColumnNames == null) {
        // @desc: 未设置数据库列时 映射到第一个字段上
        return connection.prepareStatement(sql, PreparedStatement.RETURN_GENERATED_KEYS);
      } else {
        // @desc: 配置了数据库列
        return connection.prepareStatement(sql, keyColumnNames);
      }
    // @desc: 无selectKey键且指定了ResultSetType
    } else if (mappedStatement.getResultSetType() != null) {
      return connection.prepareStatement(sql, mappedStatement.getResultSetType().getValue(), ResultSet.CONCUR_READ_ONLY);
    } else {
    // @desc: 无selectKey键且无ResultSetType
      return connection.prepareStatement(sql);
    }
  }
```
##### StatementHandler获取
```java
  public StatementHandler newStatementHandler(Executor executor, MappedStatement mappedStatement, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    // @desc: 路由类型的StatementHandler
    StatementHandler statementHandler = new RoutingStatementHandler(executor, mappedStatement, parameterObject, rowBounds, resultHandler, boundSql);
    // @desc: 执行拦截器插件
    statementHandler = (StatementHandler) interceptorChain.pluginAll(statementHandler);
    // @desc: 返回StatementHandler
    return statementHandler;
  }
```


#### StatementHandler的update
```java

  /**
   * @desc: 路由类型的StatementHandler
   */
  public RoutingStatementHandler(Executor executor, MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) {
    switch (ms.getStatementType()) {
      // @desc: 根据StatementType使用不同的处理器
      case STATEMENT:
        delegate = new SimpleStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        break;
      case PREPARED:
        delegate = new PreparedStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        break;
      case CALLABLE:
        delegate = new CallableStatementHandler(executor, ms, parameter, rowBounds, resultHandler, boundSql);
        break;
      default:
        throw new ExecutorException("Unknown statement type: " + ms.getStatementType());
    }

  }

  @Override
  public int update(Statement statement) throws SQLException {
    // @desc: 委托类执行
    return delegate.update(statement);
  }
```
#### 预处理类型
```java
  @Override
  public int update(Statement statement) throws SQLException {
    PreparedStatement ps = (PreparedStatement) statement;
    ps.execute();
    // @desc: 只获取影响的行数(基础类型)
    int rows = ps.getUpdateCount();
    Object parameterObject = boundSql.getParameterObject();
    KeyGenerator keyGenerator = mappedStatement.getKeyGenerator();
    // @desc: 键的后置处理:指定属性设置指定列的数据库返回值
    keyGenerator.processAfter(executor, mappedStatement, ps, parameterObject);
    return rows;
  }
```