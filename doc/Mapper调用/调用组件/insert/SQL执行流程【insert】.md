## insert类型的SQL语句的执行流程

#### 调用入口

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

