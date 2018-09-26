#### select语句结果集处理器

#### 入口

#### 初始化
```java
// @desc: 参数处理器
this.parameterHandler = configuration.newParameterHandler(mappedStatement, parameterObject, boundSql);
// @desc: 结果集处理器
this.resultSetHandler = configuration.newResultSetHandler(executor, mappedStatement, rowBounds, parameterHandler, resultHandler, boundSql);

```

#### 构建
```java
  public ParameterHandler newParameterHandler(MappedStatement mappedStatement, Object parameterObject, BoundSql boundSql) {
    // @desc: 根据语言驱动获取参数处理器
    ParameterHandler parameterHandler = mappedStatement.getLang().createParameterHandler(mappedStatement, parameterObject, boundSql);
    // @desc: 插件处理
    parameterHandler = (ParameterHandler) interceptorChain.pluginAll(parameterHandler);
    return parameterHandler;
  }

  public ResultSetHandler newResultSetHandler(Executor executor, MappedStatement mappedStatement, RowBounds rowBounds, ParameterHandler parameterHandler,
      ResultHandler resultHandler, BoundSql boundSql) {
    // @desc: 构建DefaultResultSetHandler
    ResultSetHandler resultSetHandler = new DefaultResultSetHandler(executor, mappedStatement, parameterHandler, resultHandler, boundSql, rowBounds);
    // @desc: 插件处理
    resultSetHandler = (ResultSetHandler) interceptorChain.pluginAll(resultSetHandler);
    return resultSetHandler;
  }
```
#### 调用
```java
List<E> resultList = resultSetHandler.<E>handleResultSets(cs);
```

#### DefaultResultSetHandler#handleResultSets

```java
  @Override
  public List<Object> handleResultSets(Statement stmt) throws SQLException {
    ErrorContext.instance().activity("handling results").object(mappedStatement.getId());

    final List<Object> multipleResults = new ArrayList<Object>();

    int resultSetCount = 0;
    // @desc: 获取sql执行后返回的resultSet
    ResultSetWrapper rsw = getFirstResultSet(stmt);
    // @desc: 获取配置中的resultMap
    List<ResultMap> resultMaps = mappedStatement.getResultMaps();
    int resultMapCount = resultMaps.size();
    // @desc: 校验:返回resultSet则必须配置ResultMap或者ResultType
    validateResultMapsCount(rsw, resultMapCount);
    // @desc: 遍历结果集
    while (rsw != null && resultMapCount > resultSetCount) {
      // @desc: 对应封装
      ResultMap resultMap = resultMaps.get(resultSetCount);
      // @desc: 封装结果集
      handleResultSet(rsw, resultMap, multipleResults, null);
      // @desc: 多个结果集是遍历处理
      rsw = getNextResultSet(stmt);
      cleanUpAfterHandlingResultSet();
      resultSetCount++;
    }

    // @desc: 获取配置中的ResultSet
    String[] resultSets = mappedStatement.getResultSets();
    if (resultSets != null) {
      while (rsw != null && resultSetCount < resultSets.length) {
        ResultMapping parentMapping = nextResultMaps.get(resultSets[resultSetCount]);
        if (parentMapping != null) {
          String nestedResultMapId = parentMapping.getNestedResultMapId();
          ResultMap resultMap = configuration.getResultMap(nestedResultMapId);
          // @desc: 封装嵌套ResultMap
          handleResultSet(rsw, resultMap, null, parentMapping);
        }
        rsw = getNextResultSet(stmt);
        cleanUpAfterHandlingResultSet();
        resultSetCount++;
      }
    }

    return collapseSingleResultList(multipleResults);
  }

  private void handleResultSet(ResultSetWrapper rsw, ResultMap resultMap, List<Object> multipleResults, ResultMapping parentMapping) throws SQLException {
    try {
      // @desc: 父映射存在
      if (parentMapping != null) {
        handleRowValues(rsw, resultMap, null, RowBounds.DEFAULT, parentMapping);
      // @desc: 父映射不存在
      } else {
        // @desc: resultHandler为空时:详见执行器
        // @desc: selectList、selectMap、selectCursor都为Executor.NO_RESULT_HANDLER
        if (resultHandler == null) {
          // @desc: 使用默认的DefaultResultHandler
          DefaultResultHandler defaultResultHandler = new DefaultResultHandler(objectFactory);
          // @desc: 字段映射
          handleRowValues(rsw, resultMap, defaultResultHandler, rowBounds, null);
          // @desc: 添加到结果列表中
          multipleResults.add(defaultResultHandler.getResultList());
        // @desc: resultHandler不为空时:详见执行器
        // @desc: 无返回值的查询时可以传入resultHandler
        } else {
          handleRowValues(rsw, resultMap, resultHandler, rowBounds, null);
        }
      }
    } finally {
      // issue #228 (close resultsets)
      closeResultSet(rsw.getResultSet());
    }
  }

  /**
   * @desc: 字段映射
   */
  public void handleRowValues(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
    // @desc: 嵌套ResultMap处理
    if (resultMap.hasNestedResultMaps()) {
      ensureNoRowBounds();
      checkResultHandler();
      handleRowValuesForNestedResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    // @desc: 非嵌套ResultMap处理
    } else {
      handleRowValuesForSimpleResultMap(rsw, resultMap, resultHandler, rowBounds, parentMapping);
    }
  }
```

#### 嵌套ResultMap结果映射
```java
  private void handleRowValuesForNestedResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping) throws SQLException {
    final DefaultResultContext<Object> resultContext = new DefaultResultContext<Object>();
    skipRows(rsw.getResultSet(), rowBounds);
    Object rowValue = previousRowValue;
    while (shouldProcessMoreRows(resultContext, rowBounds) && rsw.getResultSet().next()) {
      final ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw.getResultSet(), resultMap, null);
      final CacheKey rowKey = createRowKey(discriminatedResultMap, rsw, null);
      Object partialObject = nestedResultObjects.get(rowKey);
      // issue #577 && #542
      if (mappedStatement.isResultOrdered()) {
        if (partialObject == null && rowValue != null) {
          nestedResultObjects.clear();
          storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
        }
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, null, partialObject);
      } else {
        rowValue = getRowValue(rsw, discriminatedResultMap, rowKey, null, partialObject);
        if (partialObject == null) {
          storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
        }
      }
    }
    if (rowValue != null && mappedStatement.isResultOrdered() && shouldProcessMoreRows(resultContext, rowBounds)) {
      storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
      previousRowValue = null;
    } else if (rowValue != null) {
      previousRowValue = rowValue;
    }
  }
```

#### 非嵌套ResultMap结果映射

```java
  private void handleRowValuesForSimpleResultMap(ResultSetWrapper rsw, ResultMap resultMap, ResultHandler<?> resultHandler, RowBounds rowBounds, ResultMapping parentMapping)
      throws SQLException {
    DefaultResultContext<Object> resultContext = new DefaultResultContext<Object>();
    // @desc: 跳过分页、游标偏移的行数
    skipRows(rsw.getResultSet(), rowBounds);
    // @desc: 只处理分页范围之内的行数
    while (shouldProcessMoreRows(resultContext, rowBounds) && rsw.getResultSet().next()) {
      // @desc: 鉴别器处理,获取满足鉴别器条件后的最终ResultMap
      ResultMap discriminatedResultMap = resolveDiscriminatedResultMap(rsw.getResultSet(), resultMap, null);
      // @desc: 将结果集映射到对象
      Object rowValue = getRowValue(rsw, discriminatedResultMap);
      // @desc: 存储在resultHandler的List中
      storeObject(resultHandler, resultContext, rowValue, parentMapping, rsw.getResultSet());
    }
  }

  private Object getRowValue(ResultSetWrapper rsw, ResultMap resultMap) throws SQLException {
    final ResultLoaderMap lazyLoader = new ResultLoaderMap();
    // @desc: 构建ResultMap的JavaBean
    Object rowValue = createResultObject(rsw, resultMap, lazyLoader, null);
    if (rowValue != null && !hasTypeHandlerForResultObject(rsw, resultMap.getType())) {
      // @desc: 获取对象的属性操作权限
      final MetaObject metaObject = configuration.newMetaObject(rowValue);
      boolean foundValues = this.useConstructorMappings;
      // @desc: 自动映射处理,autoMapping=true
      if (shouldApplyAutomaticMappings(resultMap, false)) {
        foundValues = applyAutomaticMappings(rsw, resultMap, metaObject, null) || foundValues;
      }
      // @desc: 普通属性处理
      foundValues = applyPropertyMappings(rsw, resultMap, metaObject, lazyLoader, null) || foundValues;
      foundValues = lazyLoader.size() > 0 || foundValues;
      rowValue = foundValues || configuration.isReturnInstanceForEmptyRow() ? rowValue : null;
    }
    return rowValue;
  }

  /**
   * @desc: 自动映射
   */
  private boolean applyAutomaticMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, String columnPrefix) throws SQLException {
    // @desc:
    List<UnMappedColumnAutoMapping> autoMapping = createAutomaticMappings(rsw, resultMap, metaObject, columnPrefix);
    boolean foundValues = false;
    // @desc: 设置了autoMapping属性
    if (!autoMapping.isEmpty()) {
      // @desc: 遍历未映射的列名称:数据集中返回的列不在配置的column的列的大写转换后的列表中
      for (UnMappedColumnAutoMapping mapping : autoMapping) {
        // @desc: 使用类型处理器获取结果集中指定列名称的值
        final Object value = mapping.typeHandler.getResult(rsw.getResultSet(), mapping.column);
        // @desc: 值不为空则说明能映射到
        if (value != null) {
          foundValues = true;
        }
        // @desc: 获取到了映射值或者(专职Map,改属性不是基础类型且允许设置null属性)
        if (value != null || (configuration.isCallSettersOnNulls() && !mapping.primitive)) {
         // @desc: 设置指定属性为指定值
          metaObject.setValue(mapping.property, value);
        }
      }
    }
    // @desc: 返回是否映射到了列
    return foundValues;
  }

 /**
  * @desc: 指定column的列字段映射
  */
 private boolean applyPropertyMappings(ResultSetWrapper rsw, ResultMap resultMap, MetaObject metaObject, ResultLoaderMap lazyLoader, String columnPrefix)
      throws SQLException {
    // @desc: 获取指定了column且结果集中含有该column的列
    final List<String> mappedColumnNames = rsw.getMappedColumnNames(resultMap, columnPrefix);
    boolean foundValues = false;
    // @desc: 获取result节点列表
    final List<ResultMapping> propertyMappings = resultMap.getPropertyResultMappings();
    // @desc: 遍历
    for (ResultMapping propertyMapping : propertyMappings) {
      String column = prependPrefix(propertyMapping.getColumn(), columnPrefix);
      if (propertyMapping.getNestedResultMapId() != null) {
        // the user added a column attribute to a nested result map, ignore it
        column = null;
      }
      if (propertyMapping.isCompositeResult()
          || (column != null && mappedColumnNames.contains(column.toUpperCase(Locale.ENGLISH)))
          || propertyMapping.getResultSet() != null) {
         // @desc: 获取该列在结果集中的值(嵌套查询、resultset处理、typeHandler处理)
        Object value = getPropertyMappingValue(rsw.getResultSet(), metaObject, propertyMapping, lazyLoader, columnPrefix);
        // issue #541 make property optional
        final String property = propertyMapping.getProperty();
        // @desc: 属性为null不设置值
        if (property == null) {
          continue;
        // @desc: 属性值为占位时不设置值
        } else if (value == DEFERED) {
          foundValues = true;
          continue;
        }
        // @desc: 值不为空则说明能映射到
        if (value != null) {
          foundValues = true;
        }
        // @desc: 获取到了映射值或者(专职Map,改属性不是基础类型且允许设置null属性)
        if (value != null || (configuration.isCallSettersOnNulls() && !metaObject.getSetterType(property).isPrimitive())) {
          // @desc: 设置指定属性为指定值
          metaObject.setValue(property, value);
        }
      }
    }
    return foundValues;
  }
```