## sqlSource 解析

#### sqlSource创建发生在增删改查节点解析之中,include、selectKey解析之后

```java
SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
```

#### 默认使用XMLLanguageDriver
```java
  @Override
  public SqlSource createSqlSource(Configuration configuration, XNode script, Class<?> parameterType) {
    XMLScriptBuilder builder = new XMLScriptBuilder(configuration, script, parameterType);
    return builder.parseScriptNode();
  }

  /**
   * @desc:解析脚本 XMLScriptBuilder.parseScriptNode()
   */
  public SqlSource parseScriptNode() {
    // @desc: 解析动态标签:最终解析为MixedSqlNode
    MixedSqlNode rootSqlNode = parseDynamicTags(context);
    SqlSource sqlSource = null;
    // @desc: 标签解析中判断是否为动态SQL
    if (isDynamic) {
      // @desc: 动态SQL源
      sqlSource = new DynamicSqlSource(configuration, rootSqlNode);
    } else {
      // @desc: 原始SQL源
      sqlSource = new RawSqlSource(configuration, rootSqlNode, parameterType);
    }
    return sqlSource;
  }

  // @desc: 解析动态标签:XNode=增删改查节点
  protected MixedSqlNode parseDynamicTags(XNode node) {
    List<SqlNode> contents = new ArrayList<SqlNode>();
    // @desc: 获取子节点列表
    NodeList children = node.getNode().getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      XNode child = node.newXNode(children.item(i));
      // @desc: <![CDATA[]]>节点与文本节点
      if (child.getNode().getNodeType() == Node.CDATA_SECTION_NODE || child.getNode().getNodeType() == Node.TEXT_NODE) {
        String data = child.getStringBody("");
        TextSqlNode textSqlNode = new TextSqlNode(data);
        // @desc: 解析SQL,如果SQL中含有参数表达式则说明是动态的
        if (textSqlNode.isDynamic()) {
          // @desc: textSqlNode此时已解析完毕
          contents.add(textSqlNode);
          isDynamic = true;
        } else {
          // @desc: 无参数表达式则说明为静态SQL
          contents.add(new StaticTextSqlNode(data));
        }
      // @desc: 元素节点
      } else if (child.getNode().getNodeType() == Node.ELEMENT_NODE) { // issue #628

        String nodeName = child.getNode().getNodeName();
        // @desc: 获取元素节点的名称对应的处理器
        NodeHandler handler = nodeHandlerMap.get(nodeName);
        // @desc: 不存在则说明为为自定义标签,抛异常
        if (handler == null) {
          throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
        }
        // @desc: 处理标签的动态SQL
        handler.handleNode(child, contents);
        // @desc: 存在标签则说明是动态SQL
        isDynamic = true;
      }
    }
    // @desc: 返回解析后的完整SQL
    return new MixedSqlNode(contents);
  }
```
#### RawSqlSource

```java
public class RawSqlSource implements SqlSource {

  private final SqlSource sqlSource;

  public RawSqlSource(Configuration configuration, SqlNode rootSqlNode, Class<?> parameterType) {
    // @desc: 构建时获取了SQL
    this(configuration, getSql(configuration, rootSqlNode), parameterType);
  }

  public RawSqlSource(Configuration configuration, String sql, Class<?> parameterType) {
    // @desc: 构建者
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    // @desc: parameterType为增删改查节点上的parameterType
    Class<?> clazz = parameterType == null ? Object.class : parameterType;
    // @desc: 将#{}参数替换为?并构建ParameterMapping返回StaticSqlSource
    sqlSource = sqlSourceParser.parse(sql, clazz, new HashMap<String, Object>());
  }

  private static String getSql(Configuration configuration, SqlNode rootSqlNode) {
    DynamicContext context = new DynamicContext(configuration, null);
    rootSqlNode.apply(context);
    // @desc: 这里就是一直拼接SQL为完整SQL
    return context.getSql();
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    return sqlSource.getBoundSql(parameterObject);
  }

}
```


#### DynamicSqlSource
```java
public class DynamicSqlSource implements SqlSource {

  private final Configuration configuration;
  private final SqlNode rootSqlNode;

  public DynamicSqlSource(Configuration configuration, SqlNode rootSqlNode) {
    this.configuration = configuration;
    this.rootSqlNode = rootSqlNode;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // @desc: parameterObject从调用处传入的参数
    DynamicContext context = new DynamicContext(configuration, parameterObject);
    // @desc: 解析为原始SQL语句(${}已解析为具体值)
    rootSqlNode.apply(context);
    // @desc: 构建者
    SqlSourceBuilder sqlSourceParser = new SqlSourceBuilder(configuration);
    Class<?> parameterType = parameterObject == null ? Object.class : parameterObject.getClass();
    // @desc: 解析为静态SQL语句,将#{}参数替换为?并构建ParameterMapping返回StaticSqlSource
    SqlSource sqlSource = sqlSourceParser.parse(context.getSql(), parameterType, context.getBindings());
    // @desc: StaticSqlSource中获取BoundSql
    BoundSql boundSql = sqlSource.getBoundSql(parameterObject);
    // @desc: 给boundSql设置额外参数:[数据库提供商ID,parameterObject]
    for (Map.Entry<String, Object> entry : context.getBindings().entrySet()) {
      boundSql.setAdditionalParameter(entry.getKey(), entry.getValue());
    }
    // @desc: 返回
    return boundSql;
  }
}
```
#### StaticSqlSource
```java
public class StaticSqlSource implements SqlSource {

  /**
   * @desc: 需要在数据库执行的SQL,只含有占位符?
   */
  private final String sql;

  /**
   * @desc: sql占位符解析用的参数映射列表
   */
  private final List<ParameterMapping> parameterMappings;
  private final Configuration configuration;

  public StaticSqlSource(Configuration configuration, String sql) {
    this(configuration, sql, null);
  }

  public StaticSqlSource(Configuration configuration, String sql, List<ParameterMapping> parameterMappings) {
    this.sql = sql;
    this.parameterMappings = parameterMappings;
    this.configuration = configuration;
  }

  @Override
  public BoundSql getBoundSql(Object parameterObject) {
    // @desc: 构建BoundSql
    return new BoundSql(configuration, sql, parameterMappings, parameterObject);
  }

}
```

#### SQL提供者源(todo)

#### 说明

|语言驱动|判断标准|描述|
|:--|:--|:--|
|XMLLanguageDriver|SQL中有${}或者有非include、selectKey标签|默认语言驱动
|RawLanguageDriver|SQL中无include、selectKey之外标签,无${},可以有#{}|XML的子类,用于处理XMl中的静态SQL语句

|SQL源|名称|注意事项
|:--|:--|:--|
|DynamicSqlSource|动态SQL源|SQL只有在调用时才确定使用的SQL|
|RawSqlSource|原始SQL源|确定了SQL,后续不能改变;#{}参数使用?作为占位符|
|StaticSqlSource|静态SQL源|所有的SQL源解析的最终结果,用于构建BoundSql|

#### 流程说明
* DynamicSqlSource转RawSqlSource
    * 将Sql中的${}解析为具体的值
* RawSqlSource转StaticSqlSource
    * 将Sql中的#{}替换为?后存储为`BoundSql.sql`
    * 存储替换之前的参数名称到`BoundSql.List<ParameterMapping>`
* DynamicSqlSource需要先转RawSqlSource再转到StaticSqlSource
* 只有StaticSqlSource才能调用dataSource执行SQL

#### 其他说明
**${}支持Ognl表达式,#{}不支持**