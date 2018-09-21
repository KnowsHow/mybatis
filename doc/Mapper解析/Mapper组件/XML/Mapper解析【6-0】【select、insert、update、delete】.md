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

#### 说明

增删改查节点以及键生成节点都构建为MappedStatement对象

