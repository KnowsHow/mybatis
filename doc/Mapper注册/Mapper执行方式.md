#### JDK代理的处理程序:MapperProxy#invoke


##### 待完善、mapper执行流程2
```java
@Override
public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
  try {
     // @desc: 代理和目标是同一个类则直接调用
    if (Object.class.equals(method.getDeclaringClass())) {
      return method.invoke(this, args);
      // @desc: default方法的调用
    } else if (isDefaultMethod(method)) {
      return invokeDefaultMethod(proxy, method, args);
    }
  } catch (Throwable t) {
    throw ExceptionUtil.unwrapThrowable(t);
  }
  // @desc: 从缓存中获取MapperMethod
  final MapperMethod mapperMethod = cachedMapperMethod(method);
  // @desc: 调用MapperMethod#execute方法
  return mapperMethod.execute(sqlSession, args);
}

/**
 * @desc: 缓存MapperMethod
 */
private MapperMethod cachedMapperMethod(Method method) {
  MapperMethod mapperMethod = methodCache.get(method);
  if (mapperMethod == null) {
    // @desc: 使用接口类、method和Configuration构建mapperMethod
    mapperMethod = new MapperMethod(mapperInterface, method, sqlSession.getConfiguration());
    methodCache.put(method, mapperMethod);
  }
  return mapperMethod;
}
```
#### MapperMethod的execute说明
```java
 public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    // @desc: 根据不同的执行命令类型进行不同的处理
    // @desc: command从Configuration中解析出来
    switch (command.getType()) {
      // @desc: inset类型SQL
      case INSERT: {
        // @desc: 处理参数
        Object param = method.convertArgsToSqlCommandParam(args);
        // @desc: 处理返回类型
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      // @desc: Select类型SQL:返回类型复杂特殊处理
      case SELECT:
        // @desc: ResultHandler处理
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
        // @desc: 多条结果处理
        } else if (method.returnsMany()) {
          result = executeForMany(sqlSession, args);
        // @desc: Map处理
        } else if (method.returnsMap()) {
          result = executeForMap(sqlSession, args);
        // @desc: 游标处理
        } else if (method.returnsCursor()) {
          result = executeForCursor(sqlSession, args);
        // @desc: 单结果基础类型处理
        } else {
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
        }
        break;
      case FLUSH:
        // @desc: 刷新SQL片段
        result = sqlSession.flushStatements();
        break;
      default:
        throw new BindingException("Unknown execution method for: " + command.getName());
    }
    if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
      throw new BindingException("Mapper method '" + command.getName()
          + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
    }
    return result;
  }
  /**
   * @desc:  返回类型处理: void、Integer、long、boolean返回的是基础类型
   */
  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      result = (long)rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }
```