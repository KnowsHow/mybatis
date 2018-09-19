## ResultMap注册

#### MapperBuilderAssistant#addResultMap

```java
public ResultMap addResultMap(
      String id,
      Class<?> type,
      String extend,
      Discriminator discriminator,
      List<ResultMapping> resultMappings,
      Boolean autoMapping) {
    // @desc: 将ResultMap的ID挂载到命名空间
    id = applyCurrentNamespace(id, false);
    // @desc: 将继承的ResultMap的ID挂载到命名空间
    extend = applyCurrentNamespace(extend, true);
    // @desc: 继承了其他ResultMap
    if (extend != null) {
      if (!configuration.hasResultMap(extend)) {
        throw new IncompleteElementException("Could not find a parent resultmap with id '" + extend + "'");
      }
      // @desc: 获取ResultMap的父类存在则开始整合
      ResultMap resultMap = configuration.getResultMap(extend);
      // @desc: 即将继承的ResultMapping
      List<ResultMapping> extendedResultMappings = new ArrayList<ResultMapping>(resultMap.getResultMappings());
      // @desc: 过滤掉子类的ResultMapping
      extendedResultMappings.removeAll(resultMappings);
      // Remove parent constructor if this resultMap declares a constructor.
      boolean declaresConstructor = false;
      for (ResultMapping resultMapping : resultMappings) {
        if (resultMapping.getFlags().contains(ResultFlag.CONSTRUCTOR)) {
          declaresConstructor = true;
          break;
        }
      }
      // @desc: 过滤掉父类中定义为构造器的ResultMapping
      if (declaresConstructor) {
        Iterator<ResultMapping> extendedResultMappingsIter = extendedResultMappings.iterator();
        while (extendedResultMappingsIter.hasNext()) {
          if (extendedResultMappingsIter.next().getFlags().contains(ResultFlag.CONSTRUCTOR)) {
            extendedResultMappingsIter.remove();
          }
        }
      }
      // @desc: 继承下可以使用的ResultMapping
      resultMappings.addAll(extendedResultMappings);
    }
    // @desc: 构建ResultMap并注册
    ResultMap resultMap = new ResultMap.Builder(configuration, id, type, resultMappings, autoMapping)
        .discriminator(discriminator)
        .build();
    configuration.addResultMap(resultMap);
    return resultMap;
  }
```

#### 参数说明

|参数名称| 描述|
|:--|:--|
|id|ResultMap的ID|
|type|ResultMap的Java类型|
|extend|继承的ResultMap的ID|
|discriminator|鉴别器|
|resultMappings|映射列表|
|autoMapping|是否自动映射|

#### 细节说明

* ResultMap与命名空间(Mapper类)的关联
  * ResultMap是一个对象,含有ID属性
  * ResultMap是一个属性,指向一个ResultMap
    * 如case#resultMap
    * 如select#resultMap
  * 将ID使用-与相关组件拼接为新的resultMapId
  * 关联起来:namespace.resultMapId
* ResultMap的继承
  * 继承的ResultMap需要先被加载
  * 继承的ResultMap可以在其他命名空间中
  * 继承时会过滤掉父级的构造参数
* 构建
  * 使用ResultMap.Builder完成
* 注册
  * 注册到Configuration的resultMaps中
  * 结构为:Map<String, ResultMap>
      * key=id
      * value=ResultMap
      * id=namespace.resultMapId
#### 组件

##### 1. 鉴别器:Discriminator
1. 解析 **MapperAnnotationBuilder#applyDiscriminator**
```java
  private Discriminator applyDiscriminator(String resultMapId, Class<?> resultType, TypeDiscriminator discriminator) {
    if (discriminator != null) {
      // @desc: 应用的列
      String column = discriminator.column();
      // @desc: java类型
      Class<?> javaType = discriminator.javaType() == void.class ? String.class : discriminator.javaType();
      // @desc: Jdbc类型
      JdbcType jdbcType = discriminator.jdbcType() == JdbcType.UNDEFINED ? null : discriminator.jdbcType();
      @SuppressWarnings("unchecked")
      // @desc: 类型处理器
      Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>)
              (discriminator.typeHandler() == UnknownTypeHandler.class ? null : discriminator.typeHandler());
      // @desc: 获取Case列表
      Case[] cases = discriminator.cases();
      Map<String, String> discriminatorMap = new HashMap<String, String>();
      for (Case c : cases) {
        String value = c.value();
        // @desc: case下ResultMapId的生成
        String caseResultMapId = resultMapId + "-" + value;
        discriminatorMap.put(value, caseResultMapId);
      }
      // @desc: 设置在助手中
      return assistant.buildDiscriminator(resultType, column, javaType, jdbcType, typeHandler, discriminatorMap);
    }
    return null;
  }
```
2. 构建 **MapperBuilderAssistant#buildDiscriminator**
```java
  public Discriminator buildDiscriminator(
      Class<?> resultType,
      String column,
      Class<?> javaType,
      JdbcType jdbcType,
      Class<? extends TypeHandler<?>> typeHandler,
      Map<String, String> discriminatorMap) {
     // @desc: 先构建了结果映射
    ResultMapping resultMapping = buildResultMapping(
        resultType,
        null,
        column,
        javaType,
        jdbcType,
        null,
        null,
        null,
        null,
        typeHandler,
        new ArrayList<ResultFlag>(),
        null,
        null,
        false);
    Map<String, String> namespaceDiscriminatorMap = new HashMap<String, String>();
    // @desc: 将case中ResultMap应用到命名空间
    for (Map.Entry<String, String> e : discriminatorMap.entrySet()) {
      // @desc: 获取的value=resultMapId
      String resultMap = e.getValue();
      // @desc: 应用到命名空间
      resultMap = applyCurrentNamespace(resultMap, true);
      namespaceDiscriminatorMap.put(e.getKey(), resultMap);
    }
    // @desc: 使用Discriminator.Builder完成构建
    return new Discriminator.Builder(configuration, resultMapping, namespaceDiscriminatorMap).build();
  }
```
##### 2.映射集:List<ResultMapping>
1. 解析
    * **MapperAnnotationBuilder#applyConstructorArgs**
    * **MapperAnnotationBuilder#applyResults**
```java
  /**
   * @desc: 从@Arg中解析
   */
  private void applyConstructorArgs(Arg[] args, Class<?> resultType, List<ResultMapping> resultMappings) {
    for (Arg arg : args) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
      // @desc: 标记为构造器
      flags.add(ResultFlag.CONSTRUCTOR);
      if (arg.id()) {
        flags.add(ResultFlag.ID);
      }
      @SuppressWarnings("unchecked")
      Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>)
              (arg.typeHandler() == UnknownTypeHandler.class ? null : arg.typeHandler());
      // @desc: 开始构建
      ResultMapping resultMapping = assistant.buildResultMapping(
          resultType,
          nullOrEmpty(arg.name()),
          nullOrEmpty(arg.column()),
          arg.javaType() == void.class ? null : arg.javaType(),
          arg.jdbcType() == JdbcType.UNDEFINED ? null : arg.jdbcType(),
          nullOrEmpty(arg.select()),
          nullOrEmpty(arg.resultMap()),
          null,
          null,
          typeHandler,
          flags,
          null,
          null,
          false);
      // @desc: 注册
      resultMappings.add(resultMapping);
    }
  }

 /**
  * @desc: 从@Result中解析
  */
 private void applyResults(Result[] results, Class<?> resultType, List<ResultMapping> resultMappings) {
    for (Result result : results) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
      if (result.id()) {
        flags.add(ResultFlag.ID);
      }
      @SuppressWarnings("unchecked")
      Class<? extends TypeHandler<?>> typeHandler = (Class<? extends TypeHandler<?>>)
              ((result.typeHandler() == UnknownTypeHandler.class) ? null : result.typeHandler());
      // @desc: 开始构建
      ResultMapping resultMapping = assistant.buildResultMapping(
          resultType,
          nullOrEmpty(result.property()),
          nullOrEmpty(result.column()),
          result.javaType() == void.class ? null : result.javaType(),
          result.jdbcType() == JdbcType.UNDEFINED ? null : result.jdbcType(),
          hasNestedSelect(result) ? nestedSelectId(result) : null,
          null,
          null,
          null,
          typeHandler,
          flags,
          null,
          null,
          isLazy(result));
      // @desc: 注册
      resultMappings.add(resultMapping);
    }
  }
```
2. 构建
    * **MapperBuilderAssistant#buildResultMapping**
```java
 public ResultMapping buildResultMapping(
      Class<?> resultType,
      String property,
      String column,
      Class<?> javaType,
      JdbcType jdbcType,
      String nestedSelect,
      String nestedResultMap,
      String notNullColumn,
      String columnPrefix,
      Class<? extends TypeHandler<?>> typeHandler,
      List<ResultFlag> flags,
      String resultSet,
      String foreignColumn,
      boolean lazy) {
    Class<?> javaTypeClass = resolveResultJavaType(resultType, property, javaType);
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);
    List<ResultMapping> composites = parseCompositeColumnName(column);
    return new ResultMapping.Builder(configuration, property, column, javaTypeClass)
        .jdbcType(jdbcType)
        .nestedQueryId(applyCurrentNamespace(nestedSelect, true))
        .nestedResultMapId(applyCurrentNamespace(nestedResultMap, true))
        .resultSet(resultSet)
        .typeHandler(typeHandlerInstance)
        .flags(flags == null ? new ArrayList<ResultFlag>() : flags)
        .composites(composites)
        .notNullColumns(parseMultipleColumnNames(notNullColumn))
        .columnPrefix(columnPrefix)
        .foreignColumn(foreignColumn)
        .lazy(lazy)
        .build();
  }
```