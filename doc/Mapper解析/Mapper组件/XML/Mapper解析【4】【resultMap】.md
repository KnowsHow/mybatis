## resultMap 解析

#### 接收resultMap的节点对象XNode列表开始解析
```java
  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
        // ignore, it will be retried
      }
    }
  }

  /**
   * @desc: 解析单个ResultMap节点:便于复用
   */
  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.<ResultMapping> emptyList());
  }

  /**
   * @desc: 解析单个ResultMap节点
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    // @desc: 获取ID,ID=null则使用getValueBasedIdentifier规则生成一个
    // @desc: 生成ID主要用于嵌套的resultMap,相当于匿名的ResultMap,
    // @desc: 生成规则,所有的父元素节点名称直接用下划线_隔开,对于含有id>value>property的value值得节点,则名称后面追加[value]
    String id = resultMapNode.getStringAttribute("id",
        resultMapNode.getValueBasedIdentifier());
    // @desc: 推算JavaType的实际值,优先级:type>ofType>resultType>javaType
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    // @desc: 获取extends
    String extend = resultMapNode.getStringAttribute("extends");
    // @desc: 获取autoMapping
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    Class<?> typeClass = resolveClass(type);
    Discriminator discriminator = null;
    // @desc: 构建结果映射
    List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
    resultMappings.addAll(additionalResultMappings);
    // @desc: 解析子节点列表
    List<XNode> resultChildren = resultMapNode.getChildren();
    // @desc: 遍历
    for (XNode resultChild : resultChildren) {
      // @desc: 解析构造器节点
      if ("constructor".equals(resultChild.getName())) {
        processConstructorElement(resultChild, typeClass, resultMappings);
      // @desc: 解析鉴别器节点
      } else if ("discriminator".equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      // @desc: 解析普通节点
      } else {
        List<ResultFlag> flags = new ArrayList<ResultFlag>();
        if ("id".equals(resultChild.getName())) {
          flags.add(ResultFlag.ID);
        }
        // @desc: 记录结果映射
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    // @desc: 构建结果映射解析器
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      // @desc: 解析
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      // @desc: 解析失败则临时挂起,以后再尝试解析
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }
```

#### 结果映射 解析
```java
private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    String property;
    // @desc: flag需要先解析,来标识是ID还是构造方法参数还是普通属性
    if (flags.contains(ResultFlag.CONSTRUCTOR)) {
      property = context.getStringAttribute("name");
    } else {
      property = context.getStringAttribute("property");
    }
    // @desc: 数据库列
    String column = context.getStringAttribute("column");
    // @desc: java类型
    String javaType = context.getStringAttribute("javaType");
    // @desc: 数据库类型
    String jdbcType = context.getStringAttribute("jdbcType");
    // @desc: 嵌套了select语句的ID
    String nestedSelect = context.getStringAttribute("select");
    // @desc: 嵌套了ResultMap
    String nestedResultMap = context.getStringAttribute("resultMap",
        // @desc: ResultMap为指向时返回ID否则解析
        processNestedResultMappings(context, Collections.<ResultMapping> emptyList()));
    // @desc: 非空列表
    String notNullColumn = context.getStringAttribute("notNullColumn");
    // @desc: 列表统一前缀
    String columnPrefix = context.getStringAttribute("columnPrefix");
    // @desc: 类型处理器
    String typeHandler = context.getStringAttribute("typeHandler");
    // @desc: 使用结果集
    String resultSet = context.getStringAttribute("resultSet");
    // @desc: 外键列表
    String foreignColumn = context.getStringAttribute("foreignColumn");
    // @desc: 懒加载策略:无配置时从配置信息Configuration中读取了isLazyLoadingEnabled属性
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // @desc: 构建结果映射
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resultSet, foreignColumn, lazy);
  }
```

#### 结果映射 构建
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
    // @desc: 解析出返回结果类型Class
    Class<?> javaTypeClass = resolveResultJavaType(resultType, property, javaType);
    // @desc: 从注册器中获取类型处理器实例
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);
    // @desc: 解析出混合列
    List<ResultMapping> composites = parseCompositeColumnName(column);
    return new ResultMapping.Builder(configuration, property, column, javaTypeClass)
        .jdbcType(jdbcType)
         // @desc: 嵌套Select挂载命名空间
        .nestedQueryId(applyCurrentNamespace(nestedSelect, true))
         // @desc: 嵌套ResultMap挂载命名空间
        .nestedResultMapId(applyCurrentNamespace(nestedResultMap, true))
        .resultSet(resultSet)
        .typeHandler(typeHandlerInstance)
        .flags(flags == null ? new ArrayList<ResultFlag>() : flags)
         // @desc: 混合列
        .composites(composites)
         // @desc: 非空列
        .notNullColumns(parseMultipleColumnNames(notNullColumn))
        .columnPrefix(columnPrefix)
         // @desc: 列统一前缀
        .foreignColumn(foreignColumn)
        .lazy(lazy)
        .build();
  }

  /**
   * @desc: 使用多个非空列时使用逗号、空格、{、}隔开
   */
  private Set<String> parseMultipleColumnNames(String columnName) {
    Set<String> columns = new HashSet<String>();
    if (columnName != null) {
      if (columnName.indexOf(',') > -1) {
        StringTokenizer parser = new StringTokenizer(columnName, "{}, ", false);
        while (parser.hasMoreTokens()) {
          String column = parser.nextToken();
          columns.add(column);
        }
      } else {
        columns.add(columnName);
      }
    }
    return columns;
  }

  /**
   * @desc: 混合列:同时配置了java属性和数据库列
   * 使用{、}、逗号、空格、等号隔开
   */
  private List<ResultMapping> parseCompositeColumnName(String columnName) {
    List<ResultMapping> composites = new ArrayList<ResultMapping>();
    if (columnName != null && (columnName.indexOf('=') > -1 || columnName.indexOf(',') > -1)) {
      StringTokenizer parser = new StringTokenizer(columnName, "{}=, ", false);
      while (parser.hasMoreTokens()) {
        String property = parser.nextToken();
        String column = parser.nextToken();
        ResultMapping complexResultMapping = new ResultMapping.Builder(
            configuration, property, column, configuration.getTypeHandlerRegistry().getUnknownTypeHandler()).build();
        composites.add(complexResultMapping);
      }
    }
    return composites;
  }
```

#### 嵌套ResultMap 解析

```java
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
    // @desc: 遇到association|collection|case则说明使用了嵌套
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
      // @desc: 嵌套时未使用select则说明使用了详细配置的ResultMap
      if (context.getStringAttribute("select") == null) {
        // @desc: 解析嵌套的ResultMap
        ResultMap resultMap = resultMapElement(context, resultMappings);
        return resultMap.getId();
      }
    }
    return null;
  }
```

#### 构造器——结果映射 解析

```java
 private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    // @desc: 获取子节点:构造器参数配置
    List<XNode> argChildren = resultChild.getChildren();
    // @desc: 遍历
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
      flags.add(ResultFlag.CONSTRUCTOR);
      // @desc: 构造参数为ID
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      // @desc: 构建参数映射
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }
```

#### 鉴别器——结果映射 解析

```java
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    // @desc: 数据库列
    String column = context.getStringAttribute("column");
    // @desc: java类型
    String javaType = context.getStringAttribute("javaType");
    // @desc: 数据库类型
    String jdbcType = context.getStringAttribute("jdbcType");
    // @desc: 类型处理器
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    // @desc: 构建Map来存储鉴别属性
    Map<String, String> discriminatorMap = new HashMap<String, String>();
    // @desc: 子节点case列表
    for (XNode caseChild : context.getChildren()) {
      // @desc: case的value
      String value = caseChild.getStringAttribute("value");
      // @desc: case的resultMap,允许嵌套
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
      // @desc:
      discriminatorMap.put(value, resultMap);
    }
    // @desc: 构建 鉴别器
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }
```
#### 鉴别器构建

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

#### 使用规范


```xml
<resultMap id="" type="" extends="" autoMapping="true">
    <constructor>
        <idArg/>
        <arg/>
    </constructor>
    <id property="" column="" javaType="" jdbcType="" typeHandler=""/>
    <result property="" column="" javaType="" typeHandler=""/>
    <result property="" column="" javaType="" typeHandler=""/>
    <association property=""/>
    <collection property=""/>
    <discriminator column="" javaType="" jdbcType="" typeHandler="">
        <case value="" resultType="">
        </case>
    </discriminator>
</resultMap>
```

#### 说明

|节点名称|适用场景|注意事项|
|:--|:--|:--|
|constructor|编码了有参构造器且未编码无参构造器, <br/>需要在构建对象时传入指定参数|ID标志的构造参可以提高解析效率<br/>未配置name时必须按顺序写参数|
|association|存在一对一关系的级联|使用嵌套select时,该select语句会单独执行;<br/>column={属性=列字段,属性=列字段}来给select语句设置多个参数,此时接收参数类型为属性所属的对象;<br/>column=列字段,此时接收参数的类型为列字段对应的Java类型,用于给select语句设置一个参数|
|collection|存在一对多关系的级联|可以使用嵌套select,也可以使用嵌套ResultMap的2种用法;<br/>javaType为属性的java类型,ofType来指定实际类型(如List的具体类型)|
|discriminator|不同条件的结果Java实体不一样|相当于case  when  then|
    
#### 属性

|属性名称|描述|
|:--|:--|
|id|必要属性,DTD验证,缺少就会报错,同一类型的节点的Id不能重复|
|column|数据库的列字段名称,忽略大小写|
|javaType|javaType是通过别名获取的实例Class,别名解析忽略大小写,基本类型和包装类型的别名不一样,==null时进行推算,推算级为type>ofType>resultType>javaType|
|typeHandler|typeHandler是通过别名获取实例Class,别名解析忽略大小写,当typeHandler==null时,使用javaType和JdbcType推算出实例Class|
|property|javaBean的属性名称|
|columnPrefix|数据库字段公共前缀,配置了该属性会自动给数据库字段追加前缀|
|name|resultMap节点构造器专用属性,表名构造器参数的名称,指定了name则构造器的参数列表不用保证顺序,用于多参数构造器|
|fetchType="lazy"|懒加载策略,值为 lazy和eager,优先级别最高,大于全局的lazyLoadingEnabled|
|notNullColumn|非空数据库字段,多个字段用逗号隔开,只有这些列的值全部不为空时才会映射到属性上|
|select|嵌套select,值为一个select节点的Id,该select节点的SQL语句接收column属性值作为参数,参数类型为column对象的属性的Java类型;<br/>多参数时column="{属性=列字段,属性=列字段,...}"结构,select节点的参数类型为属性所属的对象|
|resultMap|嵌套resultMap,分为2种情况<br/>1.resultMap的值为一个外部ResultMap节点的ID,所有的配置写在外部ResultMap中<br/> 2.不写resultMap,当前节点等价于resultMap,直接配置子节点|
|ofType|当配置多对多关系时,javaType一般都是集合,因此使用ofType来指定集合的泛型来获取实例Class|
|extends|当多个ResultMap拥有相同的结构时,可以抽取出来作为父ResultMap,子类可以继承|
|autoMapping=true|默认开启自动映射|

#### 自动映射示例

|JavaBean属性|Jdbc列字段|驼峰规则|映射结果|
|:--|:--|:--|:--|
|userName|user_name|关闭|失败|
|userName|user_name|开启|成功|
|userName|userName |关闭|失败|
|userName|userName |开启|失败|
|username|userName |关闭|成功|
|username|userName |开启|成功|


#### 其他说明

* 嵌套
    * select为指向型=外部select节点的ID
    * resultMap可以为指向型也可以为配置型
    * 不能同时嵌套select和resultMap
    * 不能没有类型处理器,数据库列字段必须存在
* constructor必须第一个配置或者不配置
* constructor,discriminator二者只能配置一个,其他任意
* 属性值不配置就不要写出来,="" 等价于配置=空