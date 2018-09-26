
## select语句执行流程

#### 调用入口:MapperMethod#execute

```java
  case SELECT:
    if (method.returnsVoid() && method.hasResultHandler()) {
      executeWithResultHandler(sqlSession, args);
      result = null;
    } else if (method.returnsMany()) {
      result = executeForMany(sqlSession, args);
    } else if (method.returnsMap()) {
      result = executeForMap(sqlSession, args);
    } else if (method.returnsCursor()) {
      result = executeForCursor(sqlSession, args);
    } else {
      Object param = method.convertArgsToSqlCommandParam(args);
      result = sqlSession.selectOne(command.getName(), param);
    }
    break;
```

#### 1. 参数列表中含有ResultHandler且select语句无返回值

```java
  private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
    MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
    // @desc: 非存储过程类型SQL必须含有ResultMap或者ResultType属性或注解
    if (!StatementType.CALLABLE.equals(ms.getStatementType())
        && void.class.equals(ms.getResultMaps().get(0).getType())) {
      throw new BindingException("method " + command.getName()
          + " needs either a @ResultMap annotation, a @ResultType annotation,"
          + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
    }
    // @desc: 参数转换
    Object param = method.convertArgsToSqlCommandParam(args);
    // @desc: 分页处理
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      // @desc: 传入分页对象和结果处理器
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    // @desc: 无分页处理
    } else {
      // @desc: 传入结果处理器
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }
```

#### 2. select语句返回多条结果

```java
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    // @desc: 参数转换
    Object param = method.convertArgsToSqlCommandParam(args);
    // @desc: 分页处理
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.<E>selectList(command.getName(), param, rowBounds);
    // @desc: 无分页时处理
    } else {
      result = sqlSession.<E>selectList(command.getName(), param);
    }
    // @desc: 返回结果不是List
    if (!method.getReturnType().isAssignableFrom(result.getClass())) {
      // @desc: Array数组处理
      if (method.getReturnType().isArray()) {
        return convertToArray(result);
      // @desc: Collection集合处理
      } else {
        return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
      }
    }
    return result;
  }

  /**
   * @desc: 将结果集转为集合类型
   */
  private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
    // @desc: 使用对象工厂获取集合实例
    Object collection = config.getObjectFactory().create(method.getReturnType());
    // @desc: 构建MetaObject
    MetaObject metaObject = config.newMetaObject(collection);
    // @desc: 集合元素封装
    metaObject.addAll(list);
    return collection;
  }

  /**
   * @desc: 将结果集转为数组类型
   */
  @SuppressWarnings("unchecked")
  private <E> Object convertToArray(List<E> list) {
    Class<?> arrayComponentType = method.getReturnType().getComponentType();
    // @desc: 构建数组实例
    Object array = Array.newInstance(arrayComponentType, list.size());
    // @desc: 数组类型是基础类型的数组
    if (arrayComponentType.isPrimitive()) {
      for (int i = 0; i < list.size(); i++) {
        Array.set(array, i, list.get(i));
      }
      return array;
    // @desc: 数组类型是对象类型的数组
    } else {
      return list.toArray((E[])array);
    }
  }

```


#### 3. select语句返回Map

```java
  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    // @desc: 参数转换
    Object param = method.convertArgsToSqlCommandParam(args);
    // @desc: 分页处理
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      // @desc: 传入Map的key和分页对象
      result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    // @desc: 非分页处理
    } else {
      // @desc: 传入Map的key
      result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }
```

#### 4. select语句返回游标

```java
  private <T> Cursor<T> executeForCursor(SqlSession sqlSession, Object[] args) {
    Cursor<T> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.<T>selectCursor(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.<T>selectCursor(command.getName(), param);
    }
    return result;
  }
```

#### 5. select语句返回单个对象

```java
sqlSession.selectOne(command.getName(), param);
```

#### 说明

1.  执行method.convertArgsToSqlCommandParam(args)获取参数转换后的参数对象
2.  含有分页对象时传入分页对象
3.  根据返回结果执行不同的查询方法
    *   参数传入了ResultHandler且无返回值——>执行select
    *   返回多条结果——>执行selectList返回封装为具体类型
        * List
        * Array
        * Collection
        * List.get(0)
    *   返回Map——>执行selectMap
    *   返回游标执——>行selectCursor
4. SqlSession目前只有DefaultSqlSession









