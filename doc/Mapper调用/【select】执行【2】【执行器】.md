## select语句 执行器的调用


#### DefaultSqlSession中执行器的调用

```java

  /**
   * @desc: select
   */
  @Override
  public void select(String statement, Object parameter, RowBounds rowBounds, ResultHandler handler) {
    try {
      MappedStatement ms = configuration.getMappedStatement(statement);
      // @desc: 调用执行器的query方法
      executor.query(ms, wrapCollection(parameter), rowBounds, handler);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public <E> List<E> selectList(String statement, Object parameter, RowBounds rowBounds) {
    try {
      MappedStatement ms = configuration.getMappedStatement(statement);
      // @desc: 调用执行器的query方法
      return executor.query(ms, wrapCollection(parameter), rowBounds, Executor.NO_RESULT_HANDLER);
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public <K, V> Map<K, V> selectMap(String statement, Object parameter, String mapKey, RowBounds rowBounds) {
    // @desc: 调用自身的selectList方法
    final List<? extends V> list = selectList(statement, parameter, rowBounds);
    final DefaultMapResultHandler<K, V> mapResultHandler = new DefaultMapResultHandler<K, V>(mapKey,
        configuration.getObjectFactory(), configuration.getObjectWrapperFactory(), configuration.getReflectorFactory());
    final DefaultResultContext<V> context = new DefaultResultContext<V>();
    // @desc: 构建默认的Map结果处理器
    for (V o : list) {
      context.nextResultObject(o);
      // @desc: 处理器处理器Map
      mapResultHandler.handleResult(context);
    }
    // @desc: 返回处理后的Map
    return mapResultHandler.getMappedResults();
  }

  @Override
  public <T> Cursor<T> selectCursor(String statement, Object parameter, RowBounds rowBounds) {
    try {
      MappedStatement ms = configuration.getMappedStatement(statement);
      // @desc: 调用执行器的queryCursor方法
      Cursor<T> cursor = executor.queryCursor(ms, wrapCollection(parameter), rowBounds);
      registerCursor(cursor);
      return cursor;
    } catch (Exception e) {
      throw ExceptionFactory.wrapException("Error querying database.  Cause: " + e, e);
    } finally {
      ErrorContext.instance().reset();
    }
  }

  @Override
  public <T> T selectOne(String statement, Object parameter) {
    // @desc: 调用列表查询
    List<T> list = this.<T>selectList(statement, parameter);
    if (list.size() == 1) {
      // @desc: 返回List的第一个元素
      return list.get(0);
    } else if (list.size() > 1) {
      throw new TooManyResultsException("Expected one result (or null) to be returned by selectOne(), but found: " + list.size());
    } else {
      // @desc: 可以返回null
      return null;
    }
  }
```

#### 说明

1. 获取sql语句的Java对象MappedStatement
2. 调用query或者queryCursor执行查询
    1. query
         1. 无查询结果
         2. 返回结果List
         3. 返回结果List,转为Map
    2. queryCursor
         1. 返回游标
4. 查询参数RowBounds为必须项
    1. 参数列表中含有时直接传入
    2. 参数列表中不含有时传入默认值
        1. offset=0
        2. limit=Integer.MAX_VALUE

#### BaseExecutor:执行器调用

```java
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler) throws SQLException {
    // @desc: 获取BoundSql
    BoundSql boundSql = ms.getBoundSql(parameter);
    // @desc: 设置CacheKey
    CacheKey key = createCacheKey(ms, parameter, rowBounds, boundSql);
    // @desc: 查询
    return query(ms, parameter, rowBounds, resultHandler, key, boundSql);
 }

  @SuppressWarnings("unchecked")
  @Override
  public <E> List<E> query(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    ErrorContext.instance().resource(ms.getResource()).activity("executing a query").object(ms.getId());
    if (closed) {
      throw new ExecutorException("Executor was closed.");
    }

    // @desc: 初次查询且配置清空缓存
    if (queryStack == 0 && ms.isFlushCacheRequired()) {
      clearLocalCache();
    }
    List<E> list;
    try {
      // @desc: 执行计数
      queryStack++;
      // @desc: 从一级缓存中获取结果
      list = resultHandler == null ? (List<E>) localCache.getObject(key) : null;
      // @desc: 缓存中存在结果
      if (list != null) {
        // @desc: 如果是存储过程则处理输入输出参数
        handleLocallyCachedOutputParameters(ms, key, parameter, boundSql);
      // @desc: 缓存中不存在结果
      } else {
        // @desc: 执行查询获取结果
        list = queryFromDatabase(ms, parameter, rowBounds, resultHandler, key, boundSql);
      }
    } finally {
      queryStack--;
    }
    if (queryStack == 0) {
      for (DeferredLoad deferredLoad : deferredLoads) {
        deferredLoad.load();
      }
      deferredLoads.clear();
      if (configuration.getLocalCacheScope() == LocalCacheScope.STATEMENT) {
        // @desc: 缓存作用域为STATEMENT则清空一级缓存
        clearLocalCache();
      }
    }
    return list;
  }

  /**
   * @desc: 从数据库查询结果
   */
  private <E> List<E> queryFromDatabase(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql) throws SQLException {
    List<E> list;
    // @desc: 一级缓存,value使用占位符占位
    localCache.putObject(key, EXECUTION_PLACEHOLDER);
    try {
      // @desc: 执行查询
      list = doQuery(ms, parameter, rowBounds, resultHandler, boundSql);
    } finally {
      // @desc: 执行后清空一级缓存
      localCache.removeObject(key);
    }
    // @desc: 将查询结果缓存在一级缓存中
    localCache.putObject(key, list);
    // @desc: 存储过程将参数也缓存在一级缓存中
    if (ms.getStatementType() == StatementType.CALLABLE) {
      localOutputParameterCache.putObject(key, parameter);
    }
    return list;
  }

  /**
   * @desc: 子类实现查询
   */
  protected abstract <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException;


  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    BoundSql boundSql = ms.getBoundSql(parameter);
    return doQueryCursor(ms, parameter, rowBounds, boundSql);
  }

  /**
   * @desc: 子类实现查询
   */
  protected abstract <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql)
      throws SQLException;
```


#### CachingExecutor:执行器调用

```java
  @Override
  public <E> Cursor<E> queryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds) throws SQLException {
    // @desc: 查询之前尝试清空二级缓存
    flushCacheIfRequired(ms);
    // @desc: 调用BaseExecutor子类进行查询
    return delegate.queryCursor(ms, parameter, rowBounds);
  }

  @Override
  public <E> List<E> query(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, CacheKey key, BoundSql boundSql)
      throws SQLException {
    // @desc: 获取二级缓存
    Cache cache = ms.getCache();
    if (cache != null) {
      // @desc: 尝试清空二级缓存
      flushCacheIfRequired(ms);
      if (ms.isUseCache() && resultHandler == null) {
        // @desc: 缓存执行器不支持存储过程的OUT类型参数
        ensureNoOutParams(ms, boundSql);
        @SuppressWarnings("unchecked")
        List<E> list = (List<E>) tcm.getObject(cache, key);
        if (list == null) {
          // @desc: 调用BaseExecutor子类进行查询并将结果缓存起来
          list = delegate.<E> query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
          tcm.putObject(cache, key, list); // issue #578 and #116
        }
        return list;
      }
    }
    // @desc: 无缓存配置则直接调用BaseExecutor子类进行查询
    return delegate.<E> query(ms, parameterObject, rowBounds, resultHandler, key, boundSql);
  }
```

#### BatchExecutor:执行器的调用

```java
 @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameterObject, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql)
      throws SQLException {
    Statement stmt = null;
    try {
      flushStatements();
      Configuration configuration = ms.getConfiguration();
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameterObject, rowBounds, resultHandler, boundSql);
      Connection connection = getConnection(ms.getStatementLog());
      stmt = handler.prepare(connection, transaction.getTimeout());
      handler.parameterize(stmt);
      return handler.<E>query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    flushStatements();
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Connection connection = getConnection(ms.getStatementLog());
    Statement stmt = handler.prepare(connection, transaction.getTimeout());
    handler.parameterize(stmt);
    return handler.<E>queryCursor(stmt);
  }
```
#### 说明
1. 获取配置对象
2. 从配置对象中构建StatementHandler
3. 获取数据库连接
4. StatementHandler调用prepare
5. StatementHandler调用parameterize
6. StatementHandler调用query、queryCursor


#### ReuseExecutor:执行器的调用

```java
  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    return handler.<E>query(stmt, resultHandler);
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    return handler.<E>queryCursor(stmt);
  }

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

#### 说明

1. 获取配置对象
2. 从配置对象中获取StatementHandler
3. StatementHandler预处理SQL语句
4. StatementHandler调用其query、queryCursor方法

#### SimpleExecutor:执行器的调用

```java
  @Override
  public <E> List<E> doQuery(MappedStatement ms, Object parameter, RowBounds rowBounds, ResultHandler resultHandler, BoundSql boundSql) throws SQLException {
    Statement stmt = null;
    try {
      Configuration configuration = ms.getConfiguration();
      // @desc: 获取StatementHandler
      StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, resultHandler, boundSql);
      // @desc: 预处理SQL语句
      stmt = prepareStatement(handler, ms.getStatementLog());
      // @desc: 处理器query
      return handler.<E>query(stmt, resultHandler);
    } finally {
      closeStatement(stmt);
    }
  }

  @Override
  protected <E> Cursor<E> doQueryCursor(MappedStatement ms, Object parameter, RowBounds rowBounds, BoundSql boundSql) throws SQLException {
    Configuration configuration = ms.getConfiguration();
    // @desc: 获取StatementHandler
    StatementHandler handler = configuration.newStatementHandler(wrapper, ms, parameter, rowBounds, null, boundSql);
    // @desc: 预处理SQL语句
    Statement stmt = prepareStatement(handler, ms.getStatementLog());
    // @desc: 处理器queryCursor
    return handler.<E>queryCursor(stmt);
  }

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



