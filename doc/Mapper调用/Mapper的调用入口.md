## Mapper的调用

1. Mapper的获取得到Mapper的代理对象
2. 代理对象调用方法
3. 触发Mapper的真正SQL执行,执行位置MapperMethod#execute

#### 代理模式简单说明

* Mapper代理工厂采用JDK动态代理,
    1. 代理类为:MapperProxyFactory.newInstance(sqlSession)
    2. 委托类为:Mapper接口
    3. 调用处理器为:MapperProxy
* 代理类与委托类有同样的接口
    * 当调用代理类的方法时会执行调用处理器的invoke方法
    * 即当调用Mapper代理类的方法时会执行MapperProxy的invoke方法

#### 调用处理器:MapperProxy

```java
public class MapperProxy<T> implements InvocationHandler, Serializable {

  private static final long serialVersionUID = -6424540398559729838L;

  /**
   * @desc: SqlSession SQL会话
   */
  private final SqlSession sqlSession;

  /**
   * @desc: Mapper的Class
   */
  private final Class<T> mapperInterface;

  /**
   * @desc: 代理类的方法的缓存存储
   */
  private final Map<Method, MapperMethod> methodCache;

  public MapperProxy(SqlSession sqlSession, Class<T> mapperInterface, Map<Method, MapperMethod> methodCache) {
    this.sqlSession = sqlSession;
    this.mapperInterface = mapperInterface;
    this.methodCache = methodCache;
  }

  /**
   * @desc: 代理类调用方法时的处理
   */
  @Override
  public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
    try {
      // @desc: 代理类就是目标类则直接调用方法
      if (Object.class.equals(method.getDeclaringClass())) {
        return method.invoke(this, args);
      // @desc: 默认方法的处理
      } else if (isDefaultMethod(method)) {
        return invokeDefaultMethod(proxy, method, args);
      }
    } catch (Throwable t) {
      throw ExceptionUtil.unwrapThrowable(t);
    }
    // @desc: 正常Mapper接口的处理
    // @desc: 从代理类的方法的缓存存储中取出MapperMethod
    final MapperMethod mapperMethod = cachedMapperMethod(method);
    // @desc: 调用MapperMethod的执行方法(SQL会话和参数列表)
    return mapperMethod.execute(sqlSession, args);
  }

  /**
   * @desc: MapperMethod的优化获取方式
   */
  private MapperMethod cachedMapperMethod(Method method) {
    MapperMethod mapperMethod = methodCache.get(method);
    if (mapperMethod == null) {
      mapperMethod = new MapperMethod(mapperInterface, method, sqlSession.getConfiguration());
      methodCache.put(method, mapperMethod);
    }
    return mapperMethod;
  }

  /**
   * @desc: 调用默认方法
   */
  @UsesJava7
  private Object invokeDefaultMethod(Object proxy, Method method, Object[] args)
      throws Throwable {
    final Constructor<MethodHandles.Lookup> constructor = MethodHandles.Lookup.class
        .getDeclaredConstructor(Class.class, int.class);
    if (!constructor.isAccessible()) {
      constructor.setAccessible(true);
    }
    final Class<?> declaringClass = method.getDeclaringClass();
    return constructor
        .newInstance(declaringClass,
            MethodHandles.Lookup.PRIVATE | MethodHandles.Lookup.PROTECTED
                | MethodHandles.Lookup.PACKAGE | MethodHandles.Lookup.PUBLIC)
        .unreflectSpecial(method, declaringClass).bindTo(proxy).invokeWithArguments(args);
  }

  // @desc: 默认发方法的判定
  private boolean isDefaultMethod(Method method) {
    return (method.getModifiers()
        & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) == Modifier.PUBLIC
        && method.getDeclaringClass().isInterface();
  }
}
```

#### 方法对象:MapperMethod

```java
public class MapperMethod {

  /**
   * @desc: SQL命令
   */
  private final SqlCommand command;

  /**
   * @desc: 方法签名
   */
  private final MethodSignature method;

  public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
    // @desc: SQL命令的构建
    this.command = new SqlCommand(config, mapperInterface, method);
    // @desc: 方法签名的构建
    this.method = new MethodSignature(config, mapperInterface, method);
  }

  /**
   * @desc: Mapper方法的执行,需要SQL会话和方法参数
   */
  public Object execute(SqlSession sqlSession, Object[] args) {
    Object result;
    // @desc: 判断返回类型void、Integer、long、boolean、对象、集合、Map、游标、其他
    switch (command.getType()) {
      // @desc: 插入语句
      case INSERT: {
      Object param = method.convertArgsToSqlCommandParam(args);
        // @desc: rowCountResult是对返回结果的处理
        // @desc: SQL的执行使用的是SqlSession
        result = rowCountResult(sqlSession.insert(command.getName(), param));
        break;
      }
      // @desc: 更新语句
      case UPDATE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.update(command.getName(), param));
        break;
      }
      // @desc: 删除语句
      case DELETE: {
        Object param = method.convertArgsToSqlCommandParam(args);
        result = rowCountResult(sqlSession.delete(command.getName(), param));
        break;
      }
      // @desc: 查询语句
      case SELECT:
        // @desc: 无返回类型但是有ResultHandler的查询处理
        if (method.returnsVoid() && method.hasResultHandler()) {
          executeWithResultHandler(sqlSession, args);
          result = null;
        // @desc: 集合处理
        } else if (method.returnsMany()) {
          result = executeForMany(sqlSession, args);
        // @desc: Map处理
        } else if (method.returnsMap()) {
          result = executeForMap(sqlSession, args);
        // @desc: 游标处理
        } else if (method.returnsCursor()) {
          result = executeForCursor(sqlSession, args);
        // @desc: 其他处理
        } else {
          Object param = method.convertArgsToSqlCommandParam(args);
          result = sqlSession.selectOne(command.getName(), param);
        }
        break;
      case FLUSH:
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

  // @desc: 影响行数转换为返回类型
  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      // @desc: 转为void
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
       // @desc: 基础类型int
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      // @desc: 转为基础类型long
      result = (long)rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      // @desc: 转为基础类型boolean
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }

  /**
   * @desc: 无返回类型查询语句的处理
   */
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
      sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
    } else {
      sqlSession.select(command.getName(), param, method.extractResultHandler(args));
    }
  }

  /**
   * @desc: 返回集合的查询语句处理
   */
  private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
    List<E> result;
    // @desc: 参数解析和分页处理
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.<E>selectList(command.getName(), param, rowBounds);
    } else {
      result = sqlSession.<E>selectList(command.getName(), param);
    }
    // issue #510 Collections & arrays support

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
   * @desc: 返回游标类型的查询语句
   */
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

  /**
   * @desc: 返回类型为Map的查询语句
   */
  private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
    Map<K, V> result;
    Object param = method.convertArgsToSqlCommandParam(args);
    if (method.hasRowBounds()) {
      RowBounds rowBounds = method.extractRowBounds(args);
      result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey(), rowBounds);
    } else {
      result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey());
    }
    return result;
  }

  /**
   * @desc: 特殊类型ParamMap
   */
  public static class ParamMap<V> extends HashMap<String, V> {

    private static final long serialVersionUID = -2212268410512043556L;

    @Override
    public V get(Object key) {
      if (!super.containsKey(key)) {
        throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
      }
      return super.get(key);
    }

  }

 /**
  * @desc: SQL命令
  */
  public static class SqlCommand {

    /**
     * @desc: 命令名称
     */
    private final String name;

    /**
     * @desc: 命令类型
     */
    private final SqlCommandType type;

    public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
      // @desc: 获取方法名称
      final String methodName = method.getName();
      // @desc: 获取方法所属的类Class
      final Class<?> declaringClass = method.getDeclaringClass();
      // @desc: 获取SQL语句
      MappedStatement ms = resolveMappedStatement(mapperInterface, methodName, declaringClass,
          configuration);
      // @desc: SQL语句不存在时
      if (ms == null) {
        // @desc:  获取注解@Flush
        if (method.getAnnotation(Flush.class) != null) {
          name = null;
          type = SqlCommandType.FLUSH;
        // @desc: 无注解时抛出找不到的SQL的异常
        } else {
          throw new BindingException("Invalid bound statement (not found): "
              + mapperInterface.getName() + "." + methodName);
        }
      } else {
        // @desc: 获取SQL语句的ID
        name = ms.getId();
        // @desc: 获取SQL语句的类型
        type = ms.getSqlCommandType();
        // @desc: 未知类型处理
        if (type == SqlCommandType.UNKNOWN) {
          throw new BindingException("Unknown execution method for: " + name);
        }
      }
    }

    public String getName() {
      return name;
    }

    public SqlCommandType getType() {
      return type;
    }

    /**
     * @desc: 获取指定方法的SQL语句
     */
    private MappedStatement resolveMappedStatement(Class<?> mapperInterface, String methodName,
        Class<?> declaringClass, Configuration configuration) {
      // @desc: 获取SQL语句的解析后ID
      String statementId = mapperInterface.getName() + "." + methodName;
      // @desc: 从Configuration中获取
      if (configuration.hasStatement(statementId)) {
        return configuration.getMappedStatement(statementId);
      // @desc: 否则判断若是同一个类说明该方法无SQL语句
      } else if (mapperInterface.equals(declaringClass)) {
        return null;
      }
      // @desc: 否则递归实现的接口,从接口中获取
      for (Class<?> superInterface : mapperInterface.getInterfaces()) {
        if (declaringClass.isAssignableFrom(superInterface)) {
          MappedStatement ms = resolveMappedStatement(superInterface, methodName,
              declaringClass, configuration);
          if (ms != null) {
            return ms;
          }
        }
      }
      return null;
    }
  }

  /**
   * @desc: 方法签名:用于封装Mapper的方法
   */
  public static class MethodSignature {

    private final boolean returnsMany;
    private final boolean returnsMap;
    private final boolean returnsVoid;
    private final boolean returnsCursor;
    private final Class<?> returnType;
    private final String mapKey;
    private final Integer resultHandlerIndex;
    private final Integer rowBoundsIndex;
    private final ParamNameResolver paramNameResolver;

    public MethodSignature(Configuration configuration, Class<?> mapperInterface, Method method) {
      // @desc:解析返回Type
      Type resolvedReturnType = TypeParameterResolver.resolveReturnType(method, mapperInterface);
      // @desc: 返回Type是Class
      if (resolvedReturnType instanceof Class<?>) {
        this.returnType = (Class<?>) resolvedReturnType;
      // @desc: 返回Type是泛型获取其原始类型
      } else if (resolvedReturnType instanceof ParameterizedType) {
        this.returnType = (Class<?>) ((ParameterizedType) resolvedReturnType).getRawType();
      // @desc: 其他Type
      } else {
        this.returnType = method.getReturnType();
      }
      // @desc: 返回否为void
      this.returnsVoid = void.class.equals(this.returnType);
      // @desc: 返回是否为多个
      this.returnsMany = configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray();
      // @desc: 返回是否为游标
      this.returnsCursor = Cursor.class.equals(this.returnType);
      // @desc: 获取resultMap的Key
      this.mapKey = getMapKey(method);
      // @desc: 存在key则说明是ResultMap
      this.returnsMap = this.mapKey != null;
      // @desc: 获取唯一的分页对象在参数列表中的下标
      this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
      // @desc: 获取唯一的结果集处理器下标
      this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
      // @desc: 构建参数名称解析器(configuration, method)
      this.paramNameResolver = new ParamNameResolver(configuration, method);
    }

    /**
     * @desc: 从配置文件中解析出SQL参数对象
     */
    public Object convertArgsToSqlCommandParam(Object[] args) {
      return paramNameResolver.getNamedParams(args);
    }

    public boolean hasRowBounds() {
      return rowBoundsIndex != null;
    }

    public RowBounds extractRowBounds(Object[] args) {
      return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
    }

    public boolean hasResultHandler() {
      return resultHandlerIndex != null;
    }

    public ResultHandler extractResultHandler(Object[] args) {
      return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
    }

    public String getMapKey() {
      return mapKey;
    }

    public Class<?> getReturnType() {
      return returnType;
    }

    public boolean returnsMany() {
      return returnsMany;
    }

    public boolean returnsMap() {
      return returnsMap;
    }

    public boolean returnsVoid() {
      return returnsVoid;
    }

    public boolean returnsCursor() {
      return returnsCursor;
    }

    /**
     * @desc:获取指定参数类型在方法中参数的唯一下标
     */
    private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
      Integer index = null;
      final Class<?>[] argTypes = method.getParameterTypes();
      for (int i = 0; i < argTypes.length; i++) {
        if (paramType.isAssignableFrom(argTypes[i])) {
          // @desc: 指定类型在方法参数类型列表中则返回其下标
          if (index == null) {
            index = i;
          // @desc: 存在多个则抛出异常
          } else {
            throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
          }
        }
      }
      return index;
    }

    // @desc: 获取ResultMap的Key
    private String getMapKey(Method method) {
      String mapKey = null;
      // @desc: 返回类型必须为Map
      if (Map.class.isAssignableFrom(method.getReturnType())) {
        // @desc: 尝试从注解中获取
        final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
        if (mapKeyAnnotation != null) {
          mapKey = mapKeyAnnotation.value();
        }
      }
      return mapKey;
    }
  }

}
```

#### 说明
1. MethodSignature:Mapper方法描述的抽象
    1. 解析返回结果类型
        * void
        * 多条结果
        * Map
        * 游标
        * ResultMap
    2. 解析返回对象的Java类型
        * Class
    3. 解析特殊参数
        * 分页对象
        * 类型处理器
    4. 解析普通参数
        * 参数名称多维解析
2. SqlCommand: 数据库DML、DQL语言模式的抽象
    1. SQL类型
        * INSERT
        * UPDATE
        * DELETE
        * SELECT
        * FLUSH
        * UNKNOWN
    2. SQL语句
        * 指定方法的SQL语句
3. MapperMethod: Mapper方法的抽象
    * 方法的执行结果集处理
        * void
        * int
        * long
        * boolean
        * Array
        * Collection
        * Map
        * Cursor