## select、insert、update、delete 解析

#### 接收select、insert、update、delete的节点对象XNode列表开始解析

```java
  private void buildStatementFromContext(List<XNode> list) {
    // @desc: 区分下数据库提供商ID
    if (configuration.getDatabaseId() != null) {
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    buildStatementFromContext(list, null);
  }

  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    // @desc: 遍历XNode节点列表
    for (XNode context : list) {
      // @desc: 使用专门的构建器来构建
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        // @desc: 构建开始
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        // @desc: 构建失败则先挂起,后续尝试解析
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }
```

#### 构建流程

```java
public void parseStatementNode() {
    // @desc: ID
    String id = context.getStringAttribute("id");
    // @desc: 数据库提供商ID
    String databaseId = context.getStringAttribute("databaseId");
    // @desc: 不匹配则不解析:匹配原则在SQL节点解析
    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      return;
    }
    // @desc: 每次获取SQL结果条数
    Integer fetchSize = context.getIntAttribute("fetchSize");
    // @desc: 执行超时时间
    Integer timeout = context.getIntAttribute("timeout");
    // @desc: 参数Map
    String parameterMap = context.getStringAttribute("parameterMap");
    // @desc: 参数类型
    String parameterType = context.getStringAttribute("parameterType");
    Class<?> parameterTypeClass = resolveClass(parameterType);
    // @desc: 结果Map
    String resultMap = context.getStringAttribute("resultMap");
    // @desc: 结果类型
    String resultType = context.getStringAttribute("resultType");
    // @desc: 驱动语言
    String lang = context.getStringAttribute("lang");
    LanguageDriver langDriver = getLanguageDriver(lang);

    Class<?> resultTypeClass = resolveClass(resultType);
    // @desc: 结果集类型
    String resultSetType = context.getStringAttribute("resultSetType");
    // @desc: SQL执行类型
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);

    String nodeName = context.getNode().getNodeName();
    // @desc: SQL语句类型
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));
    // @desc: 是否为查询语句
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    // @desc: 是否清空缓存
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    // @desc: 是否使用缓存
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);
    // @desc: 结果排序
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);
    // @desc: include节点解析
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    // @desc: 传入增删改查节点
    includeParser.applyIncludes(context.getNode());
    // @desc: 主键生成器解析
    processSelectKeyNodes(id, parameterTypeClass, langDriver);
    // @desc: 构建SQL源
    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);
    // @desc: 结果集
    String resultSets = context.getStringAttribute("resultSets");
    // @desc: 主键对应的Java属性
    String keyProperty = context.getStringAttribute("keyProperty");
    // @desc: 主键对应的数据库列
    String keyColumn = context.getStringAttribute("keyColumn");
    // @desc: 主键生成器选择机制
    KeyGenerator keyGenerator;
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    if (configuration.hasKeyGenerator(keyStatementId)) {
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else {
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
          ? Jdbc3KeyGenerator.INSTANCE : NoKeyGenerator.INSTANCE;
    }
    // @desc: 增删改查节点构建为MappedStatement
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
  }
```

#### 流程细节

1. include节点解析
```java
    // @desc: include节点解析
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    // @desc: 子节点只能是include
    includeParser.applyIncludes(context.getNode());
```
##### include 解析

###### 接收select、insert、update、delete的单节点对象XNode开始解析

```java
  public void applyIncludes(Node source) {
    Properties variablesContext = new Properties();
    Properties configurationVariables = configuration.getVariables();
    if (configurationVariables != null) {
      variablesContext.putAll(configurationVariables);
    }
    applyIncludes(source, variablesContext, false);
  }
  /**
   * @desc: 解析流程: included表示 指向的SQL节点已引入并且自身的properties已解析
   */
  private void applyIncludes(Node source, final Properties variablesContext, boolean included) {
    // @desc: 解析include节点
    if (source.getNodeName().equals("include")) {
      // @desc: 获取refid指向的SQL节点,该节点已解析在Configuration的sqlFragments中
      // @desc: variablesContext为配置对象的全局变量
      Node toInclude = findSqlFragment(getStringAttribute(source, "refid"), variablesContext);
      // @desc: 获取include的properties属性+配置对象的全局变量
      Properties toIncludeContext = getVariablesContext(source, variablesContext);
      // @desc: 此时toInclude=SQL节点,toIncludeContext为所有变量
      applyIncludes(toInclude, toIncludeContext, true);
      if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
        toInclude = source.getOwnerDocument().importNode(toInclude, true);
      }
      source.getParentNode().replaceChild(toInclude, source);
      while (toInclude.hasChildNodes()) {
        toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
      }
      toInclude.getParentNode().removeChild(toInclude);
    // @desc: 非include的元素节点
    } else if (source.getNodeType() == Node.ELEMENT_NODE) {
      // @desc: 已引入SQL节点则解析属性
      if (included && !variablesContext.isEmpty()) {
        // replace variables in attribute values
        NamedNodeMap attributes = source.getAttributes();
        // @desc:将元素标签属性中的${properties}值解析
        for (int i = 0; i < attributes.getLength(); i++) {
          Node attr = attributes.item(i);
          attr.setNodeValue(PropertyParser.parse(attr.getNodeValue(), variablesContext));
        }
      }
      // @desc: 获取子节点列表
      NodeList children = source.getChildNodes();
      // @desc: 遍历子节点
      for (int i = 0; i < children.getLength(); i++) {
        // @desc: 解析
        applyIncludes(children.item(i), variablesContext, included);
      }
    }
    // @desc: 纯文本节点
    else if (included && source.getNodeType() == Node.TEXT_NODE
        && !variablesContext.isEmpty()) {
      // 将文本节点中的${properties}值解析
      source.setNodeValue(PropertyParser.parse(source.getNodeValue(), variablesContext));
    }
  }
```