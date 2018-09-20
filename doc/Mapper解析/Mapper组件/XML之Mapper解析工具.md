## XML解析对象XMLMapperBuilder

#### Mapper的XML文件构建者,用于初步解析XML文件个节点并使用MapperBuilderAssistant来完成对象的构建


#### XMLMapperBuilder主要功能说明
```java
public class XMLMapperBuilder extends BaseBuilder {

  /**
   * @desc: XML解析工具XPath解析器
   */
  private final XPathParser parser;
  /**
   * @desc: Mapper构建助手
   */
  private final MapperBuilderAssistant builderAssistant;
  /**
   * @desc: Sql节点存储
   */
  private final Map<String, XNode> sqlFragments;

  /**
   * @desc: Sql来源
   */
  private final String resource;

  /**
   * @desc: 构造方法1
   */
  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }
  /**
   * @desc: 构造方法2
   */
  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }
  /**
   * @desc: 构造方法3
   */
  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   * @desc: 解析方法
   */
  public void parse() {
    // @desc: XML只解析一次即可
    if (!configuration.isResourceLoaded(resource)) {
      // @desc: 开始解析
      configurationElement(parser.evalNode("/mapper"));
      // @desc: 解析完成
      configuration.addLoadedResource(resource);
      // @desc: 绑定命名空间
      bindMapperForNamespace();
    }
    // @desc: 解析挂起的ResultMap
    parsePendingResultMaps();
    // @desc: 解析挂起的CacheRef
    parsePendingCacheRefs();
    // @desc: 解析挂起的片段
    parsePendingStatements();
  }

  // @desc: 节点解析
  private void configurationElement(XNode context) {
    try {
      // @desc: 解析命名空间
      String namespace = context.getStringAttribute("namespace");
      if (namespace == null || namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      // @desc: 解析cache-ref
      cacheRefElement(context.evalNode("cache-ref"));
      // @desc: 解析cache
      cacheElement(context.evalNode("cache"));
      // @desc: 解析parameterMap
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      // @desc: 解析resultMap
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      // @desc: 解析sql
      sqlElement(context.evalNodes("/mapper/sql"));
      // @desc: 解析select|insert|update|delete
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. The XML location is '" + resource + "'. Cause: " + e, e);
    }
  }
}
```
#### 属性说明

|属性|描述|
|:--|:--|
|parser|XPath解析XML文件的解析器|
|builderAssistant|将XPath解析出来的Node转换为Java对象|
|sqlFragments|用于存储XPath解析出的Sql节点|

#### 构造器说明

* 主要使用构造方法1即InputStream作为XML源,指定namespace,不用Xpath解析器
* 构建默认的XPath解析器
    * inputStream=mapper的XML文件流
    * validation=true
    * variables=configuration.getVariables()
    * entityResolver=XMLMapperEntityResolver
* sqlFragments是配置对象中的全局属性,存储全部的Sql片段
    * key= namespace.id
    * value=XNode