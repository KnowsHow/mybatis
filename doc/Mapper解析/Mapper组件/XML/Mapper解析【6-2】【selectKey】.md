## selectKey节点解析

#### selectKey节点解析发生在增删改查节点解析之中,include解析之后

```
 processSelectKeyNodes(id, parameterTypeClass, langDriver);
 ```

##### 接收select、insert、update、delete的单节点对象XNode开始解析
```java
 /**
  * @desc: 解析开始  context=select、insert、update、delete的单节点对象XNode
  */
 private void processSelectKeyNodes(String id, Class<?> parameterTypeClass, LanguageDriver langDriver) {
    // @desc: 获取selectKey节点对象列表
    List<XNode> selectKeyNodes = context.evalNodes("selectKey");
    // @desc: 数据库厂商提供者处理
    if (configuration.getDatabaseId() != null) {
      parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, configuration.getDatabaseId());
    }
    // @desc: 解析节点
    parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, null);
    // @desc: 删除节点
    removeSelectKeyNodes(selectKeyNodes);
  }

  private void parseSelectKeyNodes(String parentId, List<XNode> list, Class<?> parameterTypeClass, LanguageDriver langDriver, String skRequiredDatabaseId) {
    for (XNode nodeToHandle : list) {
      // @desc: ID=增删改查节点ID+"!selectKey"
      String id = parentId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
      String databaseId = nodeToHandle.getStringAttribute("databaseId");
      // @desc: 数据库提供商ID匹配则解析
      if (databaseIdMatchesCurrent(id, databaseId, skRequiredDatabaseId)) {
        parseSelectKeyNode(id, nodeToHandle, parameterTypeClass, langDriver, databaseId);
      }
    }
  }

  /**
   * @desc: 但节点解析流程
   */
  private void parseSelectKeyNode(String id, XNode nodeToHandle, Class<?> parameterTypeClass, LanguageDriver langDriver, String databaseId) {
    // @desc: resultType
    String resultType = nodeToHandle.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);
    // @desc: SQL语句执行类型
    StatementType statementType = StatementType.valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    // @desc: 生成键的java属性
    String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
    // @desc: 生成键的数据库列表
    String keyColumn = nodeToHandle.getStringAttribute("keyColumn");
    // @desc: 生成键的执行时机
    boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));

    //defaults
    boolean useCache = false;
    boolean resultOrdered = false;
    KeyGenerator keyGenerator = NoKeyGenerator.INSTANCE;
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    SqlSource sqlSource = langDriver.createSqlSource(configuration, nodeToHandle, parameterTypeClass);
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    // @desc: 将selectKey中的SQL语句构建为MappedStatement
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, null);
    // @desc: 挂载到命名空间
    id = builderAssistant.applyCurrentNamespace(id, false);
    // @desc: 获取MappedStatement对象,不处理挂起的解析器
    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    // @desc: 存储到配置文件中
    configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
  }

  private void removeSelectKeyNodes(List<XNode> selectKeyNodes) {
    // @desc: 移除 selectKey 节点
    for (XNode nodeToHandle : selectKeyNodes) {
      nodeToHandle.getParent().getNode().removeChild(nodeToHandle.getNode());
    }
  }
```

#### 说明

1. 只有与数据库提供商ID匹配才解析selectKey节点
2. selectKey节点内容与增删改查节点内容等价
3. 解析后存储到配置对象中
    * 结构:Map<String, KeyGenerator> keyGenerators
    * key=命名空间.id!selectKey
    * value=KeyGenerator
4. 解析完成后该节点别移除便于增删改查节点的解析