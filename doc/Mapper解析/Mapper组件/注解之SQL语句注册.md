## SQL语句注册

#### MapperBuilderAssistant#addMappedStatement

```java
  public MappedStatement addMappedStatement(
      String id,
      SqlSource sqlSource,
      StatementType statementType,
      SqlCommandType sqlCommandType,
      Integer fetchSize,
      Integer timeout,
      String parameterMap,
      Class<?> parameterType,
      String resultMap,
      Class<?> resultType,
      ResultSetType resultSetType,
      boolean flushCache,
      boolean useCache,
      boolean resultOrdered,
      KeyGenerator keyGenerator,
      String keyProperty,
      String keyColumn,
      String databaseId,
      LanguageDriver lang,
      String resultSets) {

   // @desc: 缓存指向不正确则抛出异常
    if (unresolvedCacheRef) {
      throw new IncompleteElementException("Cache-ref not yet resolved");
    }

    id = applyCurrentNamespace(id, false);
    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;

    MappedStatement.Builder statementBuilder = new MappedStatement.Builder(configuration, id, sqlSource, sqlCommandType)
        // @desc: SQL源
        .resource(resource)
        // @desc: 预支数量
        .fetchSize(fetchSize)
        // @desc: 超时时间
        .timeout(timeout)
        // @desc: 执行类型
        .statementType(statementType)
        // @desc: 主键生成
        .keyGenerator(keyGenerator)
        // @desc: 主键的Java属性
        .keyProperty(keyProperty)
        // @desc: 主键的数据库列表
        .keyColumn(keyColumn)
        // @desc: 数据库厂商ID
        .databaseId(databaseId)
        // @desc: 语言驱动
        .lang(lang)
        // @desc: 排序
        .resultOrdered(resultOrdered)
        // @desc: 结果集
        .resultSets(resultSets)
        // @desc: 结果集
        .resultMaps(getStatementResultMaps(resultMap, resultType, id))
        // @desc: 结果集Type
        .resultSetType(resultSetType)
        // @desc: 刷新缓存
        .flushCacheRequired(valueOrDefault(flushCache, !isSelect))
        // @desc: 使用缓存
        .useCache(valueOrDefault(useCache, isSelect))
        // @desc: 缓存对象:设置缓存规则
        .cache(currentCache);

    // @desc: 参数Map
    ParameterMap statementParameterMap = getStatementParameterMap(parameterMap, parameterType, id);
    if (statementParameterMap != null) {
      statementBuilder.parameterMap(statementParameterMap);
    }

    MappedStatement statement = statementBuilder.build();
    // @desc: 注册到配置文件中
    configuration.addMappedStatement(statement);
    return statement;
  }
```
#### 参数说明

|参数名称| 描述|
|:--|:--|
|id|SQL映射的ID|
|sqlSource|SQL源:可以获取BindSql|
|statementType|预处理、静态处理、存储过程|
|resultSets|结果集,不推荐使用|
|parameterMap|参数Map,不推荐使用|
|cache|缓存对象:不同的缓存规则有不同的缓存实现|
