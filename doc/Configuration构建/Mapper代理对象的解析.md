## Mapper代理对象的解析
##### 解析入口:MapperRegistry#addMapper
```java
public <T> void addMapper(Class<T> type) {
    if (type.isInterface()) {
      if (hasMapper(type)) {
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
        knownMappers.put(type, new MapperProxyFactory<T>(type));
        // @desc: 注解构建者
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        // @desc: 注解解析
        parser.parse();
        loadCompleted = true;
      } finally {
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }
```
####  注解解析:MapperAnnotationBuilder

```java
public class MapperAnnotationBuilder {

  private final Set<Class<? extends Annotation>> sqlAnnotationTypes = new HashSet<Class<? extends Annotation>>();
  private final Set<Class<? extends Annotation>> sqlProviderAnnotationTypes = new HashSet<Class<? extends Annotation>>();

  private final Configuration configuration;
  private final MapperBuilderAssistant assistant;
  private final Class<?> type;

  /**
   * @desc:构造器: 需要配置对象和指定的Mapper类Class
   */
  public MapperAnnotationBuilder(Configuration configuration, Class<?> type) {
    String resource = type.getName().replace('.', '/') + ".java (best guess)";
    // @desc: 这里构建了一个解析助手类
    this.assistant = new MapperBuilderAssistant(configuration, resource);
    this.configuration = configuration;
    this.type = type;

    sqlAnnotationTypes.add(Select.class);
    sqlAnnotationTypes.add(Insert.class);
    sqlAnnotationTypes.add(Update.class);
    sqlAnnotationTypes.add(Delete.class);

    sqlProviderAnnotationTypes.add(SelectProvider.class);
    sqlProviderAnnotationTypes.add(InsertProvider.class);
    sqlProviderAnnotationTypes.add(UpdateProvider.class);
    sqlProviderAnnotationTypes.add(DeleteProvider.class);
  }

  /**
   * @desc: 解析逻辑
   */
  public void parse() {
    String resource = type.toString();
    // @desc: Mapper的XML只解析一次
    if (!configuration.isResourceLoaded(resource)) {
      // @desc: 解析XML文件
      loadXmlResource();
      // @desc: XML解析完毕
      configuration.addLoadedResource(resource);
      // @desc: 设置命名空间
      assistant.setCurrentNamespace(type.getName());
      // @desc: 解析@CacheNamespace
      parseCache();
      // @desc: 解析@CacheNamespaceRef
      parseCacheRef();
      Method[] methods = type.getMethods();
      for (Method method : methods) {
        try {
          // issue #237
          // @desc: 非桥接方法时才解析该方法即直解析真正的方法
          // @desc: 桥接方法为JVM在解析泛型为具体类型时模拟的中间转换方法
          if (!method.isBridge()) {
            parseStatement(method);
          }
        } catch (IncompleteElementException e) {
          // @desc: 出现解析异常则先挂起,有可能缺失的部分会在别的位置解析出来
          configuration.addIncompleteMethod(new MethodResolver(this, method));
        }
      }
    }
    // @desc: 解析被挂起的方法
    parsePendingMethods();
  }

  /**
   * @desc: 解析被挂起的方法
   */
  private void parsePendingMethods() {
    Collection<MethodResolver> incompleteMethods = configuration.getIncompleteMethods();
    synchronized (incompleteMethods) {
      Iterator<MethodResolver> iter = incompleteMethods.iterator();
      while (iter.hasNext()) {
        try {
          // @desc: 解析完成则清空该条挂起信息
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
          // This method is still missing a resource
        }
      }
    }
  }
 // @desc: 生成ResultMap的名称
  private String generateResultMapName(Method method) {
    // @desc: 解析@Results
    Results results = method.getAnnotation(Results.class);
    if (results != null && !results.id().isEmpty()) {
      return type.getName() + "." + results.id();
    }
    StringBuilder suffix = new StringBuilder();
    for (Class<?> c : method.getParameterTypes()) {
      suffix.append("-");
      suffix.append(c.getSimpleName());
    }
    if (suffix.length() < 1) {
      suffix.append("-void");
    }
    return type.getName() + "." + method.getName() + suffix;
  }

  /**
   * @desc: 从注解中获取SQL源
   */
  private SqlSource getSqlSourceFromAnnotations(Method method, Class<?> parameterType, LanguageDriver languageDriver) {
    try {
      Class<? extends Annotation> sqlAnnotationType = getSqlAnnotationType(method);
      Class<? extends Annotation> sqlProviderAnnotationType = getSqlProviderAnnotationType(method);
      if (sqlAnnotationType != null) {
        if (sqlProviderAnnotationType != null) {
          throw new BindingException("You cannot supply both a static SQL and SqlProvider to method named " + method.getName());
        }
        Annotation sqlAnnotation = method.getAnnotation(sqlAnnotationType);
        final String[] strings = (String[]) sqlAnnotation.getClass().getMethod("value").invoke(sqlAnnotation);
        // @desc: 构建SQL源
        return buildSqlSourceFromStrings(strings, parameterType, languageDriver);
      } else if (sqlProviderAnnotationType != null) {
        Annotation sqlProviderAnnotation = method.getAnnotation(sqlProviderAnnotationType);
        return new ProviderSqlSource(assistant.getConfiguration(), sqlProviderAnnotation, type, method);
      }
      return null;
    } catch (Exception e) {
      throw new BuilderException("Could not find value method on SQL annotation.  Cause: " + e, e);
    }
  }

  /**
   * @desc: 构建SQL源
   */
  private SqlSource buildSqlSourceFromStrings(String[] strings, Class<?> parameterTypeClass, LanguageDriver languageDriver) {
    final StringBuilder sql = new StringBuilder();
    for (String fragment : strings) {
      sql.append(fragment);
      sql.append(" ");
    }
    // @desc: 通过语言驱动来构建SQL源
    return languageDriver.createSqlSource(configuration, sql.toString().trim(), parameterTypeClass);
  }

  /**
   * @desc: 获取SQL语句类型
   */
  private SqlCommandType getSqlCommandType(Method method) {
    Class<? extends Annotation> type = getSqlAnnotationType(method);

    if (type == null) {
      type = getSqlProviderAnnotationType(method);

      if (type == null) {
        return SqlCommandType.UNKNOWN;
      }

      if (type == SelectProvider.class) {
        type = Select.class;
      } else if (type == InsertProvider.class) {
        type = Insert.class;
      } else if (type == UpdateProvider.class) {
        type = Update.class;
      } else if (type == DeleteProvider.class) {
        type = Delete.class;
      }
    }

    return SqlCommandType.valueOf(type.getSimpleName().toUpperCase(Locale.ENGLISH));
  }

  private Class<? extends Annotation> getSqlAnnotationType(Method method) {
    return chooseAnnotationType(method, sqlAnnotationTypes);
  }

  private Class<? extends Annotation> getSqlProviderAnnotationType(Method method) {
    return chooseAnnotationType(method, sqlProviderAnnotationTypes);
  }

  /**
   * @desc: 获取注解类型
   */
  private Class<? extends Annotation> chooseAnnotationType(Method method, Set<Class<? extends Annotation>> types) {
    for (Class<? extends Annotation> type : types) {
      Annotation annotation = method.getAnnotation(type);
      if (annotation != null) {
        return type;
      }
    }
    return null;
  }


  // @desc: 嵌套查询
  private String nestedSelectId(Result result) {
    String nestedSelect = result.one().select();
    if (nestedSelect.length() < 1) {
      nestedSelect = result.many().select();
    }
    if (!nestedSelect.contains(".")) {
      nestedSelect = type.getName() + "." + nestedSelect;
    }
    return nestedSelect;
  }

  /**
   * @desc: 懒加载
   */
  private boolean isLazy(Result result) {
    boolean isLazy = configuration.isLazyLoadingEnabled();
    if (result.one().select().length() > 0 && FetchType.DEFAULT != result.one().fetchType()) {
      isLazy = result.one().fetchType() == FetchType.LAZY;
    } else if (result.many().select().length() > 0 && FetchType.DEFAULT != result.many().fetchType()) {
      isLazy = result.many().fetchType() == FetchType.LAZY;
    }
    return isLazy;
  }

  /**
   * @desc: 是否为嵌套查询
   */
  private boolean hasNestedSelect(Result result) {
    if (result.one().select().length() > 0 && result.many().select().length() > 0) {
      throw new BuilderException("Cannot use both @One and @Many annotations in the same @Result");
    }
    return result.one().select().length() > 0 || result.many().select().length() > 0;
  }

  private String nullOrEmpty(String value) {
    return value == null || value.trim().length() == 0 ? null : value;
  }

  private Result[] resultsIf(Results results) {
    return results == null ? new Result[0] : results.value();
  }

  private Arg[] argsIf(ConstructorArgs args) {
    return args == null ? new Arg[0] : args.value();
  }

  /**
   * @desc: 解析主键生成器
   */
  private KeyGenerator handleSelectKeyAnnotation(SelectKey selectKeyAnnotation, String baseStatementId, Class<?> parameterTypeClass, LanguageDriver languageDriver) {
    String id = baseStatementId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    Class<?> resultTypeClass = selectKeyAnnotation.resultType();
    StatementType statementType = selectKeyAnnotation.statementType();
    String keyProperty = selectKeyAnnotation.keyProperty();
    String keyColumn = selectKeyAnnotation.keyColumn();
    boolean executeBefore = selectKeyAnnotation.before();

    // defaults
    boolean useCache = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    SqlSource sqlSource = buildSqlSourceFromStrings(selectKeyAnnotation.statement(), parameterTypeClass, languageDriver);
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    assistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass, resultSetTypeEnum,
        flushCache, useCache, false,
        keyGenerator, keyProperty, keyColumn, null, languageDriver, null);

    id = assistant.applyCurrentNamespace(id, false);

    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    SelectKeyGenerator answer = new SelectKeyGenerator(keyStatement, executeBefore);
    configuration.addKeyGenerator(id, answer);
    return answer;
  }

}
```

#### 解析XML文件
```java
  // @desc: Mapper的XML解析
  private void loadXmlResource() {
    // @desc: XML资源初次解析
    if (!configuration.isResourceLoaded("namespace:" + type.getName())) {
      // @desc: 解析的是与Mapper同名称的XML文件
      String xmlResource = type.getName().replace('.', '/') + ".xml";
      InputStream inputStream = null;
      try {
        inputStream = Resources.getResourceAsStream(type.getClassLoader(), xmlResource);
      } catch (IOException e) {
        // @desc: 忽略异常:可以只用注解
      }
      if (inputStream != null) {
        // @desc: Mapper解析入口XMLMapperBuilder
        XMLMapperBuilder xmlParser = new XMLMapperBuilder(inputStream, assistant.getConfiguration(), xmlResource, configuration.getSqlFragments(), type.getName());
        // @desc: 解析开始
        xmlParser.parse();
      }
    }
  }
```

#### 解析@CacheNamespace

```java
 private void parseCache() {
    // @desc: 解析出注解中需要缓存的命名空间
    CacheNamespace cacheDomain = type.getAnnotation(CacheNamespace.class);
    if (cacheDomain != null) {
      Integer size = cacheDomain.size() == 0 ? null : cacheDomain.size();
      Long flushInterval = cacheDomain.flushInterval() == 0 ? null : cacheDomain.flushInterval();
      Properties props = convertToProperties(cacheDomain.properties());
       // @desc: 设置在解析助手中
      assistant.useNewCache(cacheDomain.implementation(), cacheDomain.eviction(), flushInterval, size, cacheDomain.readWrite(), cacheDomain.blocking(), props);
    }
  }
  // @desc: 解析properties属性
  private Properties convertToProperties(Property[] properties) {
    if (properties.length == 0) {
      return null;
    }
    Properties props = new Properties();
    for (Property property : properties) {
      props.setProperty(property.name(),
          // @desc: 从配置文件的属性中获取properties值
          PropertyParser.parse(property.value(), configuration.getVariables()));
    }
    return props;
  }
```

#### 解析:@CacheNamespaceRef
```java
  private void parseCacheRef() {
    // @desc: 获取注解@CacheNamespaceRef
    CacheNamespaceRef cacheDomainRef = type.getAnnotation(CacheNamespaceRef.class);
    if (cacheDomainRef != null) {
      Class<?> refType = cacheDomainRef.value();
      String refName = cacheDomainRef.name();
      // @desc: name、value只能配置一个
      if (refType == void.class && refName.isEmpty()) {
        throw new BuilderException("Should be specified either value() or name() attribute in the @CacheNamespaceRef");
      }
      if (refType != void.class && !refName.isEmpty()) {
        throw new BuilderException("Cannot use both value() and name() attribute in the @CacheNamespaceRef");
      }
      // @desc: 获取命名空间名称
      String namespace = (refType != void.class) ? refType.getName() : refName;
      // @desc: 设置在解析助手中
      assistant.useCacheRef(namespace);
    }
  }
```
#### 解析指定方法的SQL语句
```java
  void parseStatement(Method method) {
    // @desc: 获取参数Class: 无参数、单参、ParamMap
    Class<?> parameterTypeClass = getParameterType(method);
    // @desc: 语言驱动
    LanguageDriver languageDriver = getLanguageDriver(method);
    // @desc: 获取SQL源
    SqlSource sqlSource = getSqlSourceFromAnnotations(method, parameterTypeClass, languageDriver);
    // @desc: SQL源不能为空
    if (sqlSource != null) {
      // @desc: 解析@Options:用户自定义配置
      Options options = method.getAnnotation(Options.class);
      // @desc: 生成SQL语句的ID
      final String mappedStatementId = type.getName() + "." + method.getName();
      // @desc: 默认获取条数
      Integer fetchSize = null;
      // @desc: 超时时间
      Integer timeout = null;
      // @desc: 设置SQL指向模式:预处理
      StatementType statementType = StatementType.PREPARED;
      // @desc: 设置结果集类型:光标只能往前移动
      ResultSetType resultSetType = ResultSetType.FORWARD_ONLY;
      // @desc: 获取SQL类型
      SqlCommandType sqlCommandType = getSqlCommandType(method);
      // @desc: 是否为Select语句
      boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
      // @desc: 非select语句清空缓存
      boolean flushCache = !isSelect;
      // @desc: select语句使用缓存
      boolean useCache = isSelect;
      KeyGenerator keyGenerator;
      String keyProperty = "id";
      String keyColumn = null;
      // @desc: 插入与更新语句解析SelectKey
      if (SqlCommandType.INSERT.equals(sqlCommandType) || SqlCommandType.UPDATE.equals(sqlCommandType)) {
        // @desc: 获取@SelectKey
        SelectKey selectKey = method.getAnnotation(SelectKey.class);
        // @desc: 获取KEY生成器
        if (selectKey != null) {
          // @desc: 使用了注解则从注解中获取
          keyGenerator = handleSelectKeyAnnotation(selectKey, mappedStatementId, getParameterType(method), languageDriver);
          keyProperty = selectKey.keyProperty();
        } else if (options == null) {
          // @desc: 未使用注解且用户无自定义配置则从配置文件中获取
          keyGenerator = configuration.isUseGeneratedKeys() ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
        } else {
         // @desc:  从用户自定义配置中获取
          keyGenerator = options.useGeneratedKeys() ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
          keyProperty = options.keyProperty();
          keyColumn = options.keyColumn();
        }
      // @desc: 其他类型的语句不能使用SelectKey
      } else {
        keyGenerator = NoKeyGenerator.INSTANCE;
      }
      // @desc: 用户自定义配置覆盖默认配置
      if (options != null) {
        if (FlushCachePolicy.TRUE.equals(options.flushCache())) {
          flushCache = true;
        } else if (FlushCachePolicy.FALSE.equals(options.flushCache())) {
          flushCache = false;
        }
        useCache = options.useCache();
        fetchSize = options.fetchSize() > -1 || options.fetchSize() == Integer.MIN_VALUE ? options.fetchSize() : null; //issue #348
        timeout = options.timeout() > -1 ? options.timeout() : null;
        statementType = options.statementType();
        resultSetType = options.resultSetType();
      }

      String resultMapId = null;
      // @desc: 解析@ResultMap
      ResultMap resultMapAnnotation = method.getAnnotation(ResultMap.class);
      if (resultMapAnnotation != null) {
        String[] resultMaps = resultMapAnnotation.value();
        StringBuilder sb = new StringBuilder();
        for (String resultMap : resultMaps) {
          if (sb.length() > 0) {
            sb.append(",");
          }
          sb.append(resultMap);
        }
        // @desc: 设置ResultMapId
        resultMapId = sb.toString();
      // @desc: 无注解且是查询语句时解析出ResultsMapId
      } else if (isSelect) {
        resultMapId = parseResultMap(method);
      }

      // @desc: 注册语句解析结果
      assistant.addMappedStatement(
          mappedStatementId,
          sqlSource,
          statementType,
          sqlCommandType,
          fetchSize,
          timeout,
          // ParameterMapID
          null,
          parameterTypeClass,
          resultMapId,
          getReturnType(method),
          resultSetType,
          flushCache,
          useCache,
          // TODO gcode issue #577
          false,
          keyGenerator,
          keyProperty,
          keyColumn,
          // DatabaseID
          null,
          languageDriver,
          // ResultSets
          options != null ? nullOrEmpty(options.resultSets()) : null);
    }
  }
```
#### 解析@SelectKey

``` java
  private KeyGenerator handleSelectKeyAnnotation(SelectKey selectKeyAnnotation, String baseStatementId, Class<?> parameterTypeClass, LanguageDriver languageDriver) {
    String id = baseStatementId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    Class<?> resultTypeClass = selectKeyAnnotation.resultType();
    StatementType statementType = selectKeyAnnotation.statementType();
    String keyProperty = selectKeyAnnotation.keyProperty();
    String keyColumn = selectKeyAnnotation.keyColumn();
    boolean executeBefore = selectKeyAnnotation.before();

    // defaults
    boolean useCache = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    SqlSource sqlSource = buildSqlSourceFromStrings(selectKeyAnnotation.statement(), parameterTypeClass, languageDriver);
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    assistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType, fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass, resultSetTypeEnum,
        flushCache, useCache, false,
        keyGenerator, keyProperty, keyColumn, null, languageDriver, null);

    id = assistant.applyCurrentNamespace(id, false);

    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    SelectKeyGenerator answer = new SelectKeyGenerator(keyStatement, executeBefore);
    configuration.addKeyGenerator(id, answer);
    return answer;
  }
```
#### 解析@ResultMap

```java
 String resultMapId = null;
  ResultMap resultMapAnnotation = method.getAnnotation(ResultMap.class);
  if (resultMapAnnotation != null) {
    String[] resultMaps = resultMapAnnotation.value();
    StringBuilder sb = new StringBuilder();
    for (String resultMap : resultMaps) {
      if (sb.length() > 0) {
        sb.append(",");
      }
      sb.append(resultMap);
    }
    // @desc: 设置ResultMapId
    resultMapId = sb.toString();
  // @desc: 无注解且是查询语句时尝试解析出ResultsMapId
  } else if (isSelect) {
    resultMapId = parseResultMap(method);
  }
```
#### 无注解且是查询语句时尝试解析出ResultsMapId

```java
  private String parseResultMap(Method method) {
    Class<?> returnType = getReturnType(method);
    ConstructorArgs args = method.getAnnotation(ConstructorArgs.class);
    Results results = method.getAnnotation(Results.class);
    TypeDiscriminator typeDiscriminator = method.getAnnotation(TypeDiscriminator.class);
    // @desc: 生成resultMapId
    String resultMapId = generateResultMapName(method);
    // @desc: 应用
    applyResultMap(resultMapId, returnType, argsIf(args), resultsIf(results), typeDiscriminator);
    return resultMapId;
  }

  // @desc: 生成ResultMap的Id
  private String generateResultMapName(Method method) {
    // @desc: 获取@Results
    Results results = method.getAnnotation(Results.class);
    if (results != null && !results.id().isEmpty()) {
      // @desc: 直接返回
      return type.getName() + "." + results.id();
    }
    // @desc: 未使用@Results注解或者使用了@Results注解但是无ID
    StringBuilder suffix = new StringBuilder();
    for (Class<?> c : method.getParameterTypes()) {
      suffix.append("-");
      suffix.append(c.getSimpleName());
    }
    if (suffix.length() < 1) {
      suffix.append("-void");
    }
    // @desc: 追加上参数名称列表,使用-连接
    return type.getName() + "." + method.getName() + suffix;
  }

  /**
   * @desc: 应用ResultMap
   */
  private void applyResultMap(String resultMapId, Class<?> returnType, Arg[] args, Result[] results, TypeDiscriminator discriminator) {
    List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
    // @desc: 构造方法应用
    applyConstructorArgs(args, returnType, resultMappings);
    // @desc: Results应用
    applyResults(results, returnType, resultMappings);
    // @desc: 鉴别器应用
    Discriminator disc = applyDiscriminator(resultMapId, returnType, discriminator);
    // @desc: 设置助手中
    assistant.addResultMap(resultMapId, returnType, null, disc, resultMappings, null);
    // @desc: 构建鉴别器下的结果映射
    createDiscriminatorResultMaps(resultMapId, returnType, discriminator);
  }
```


