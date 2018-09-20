## parameterMap 解析

#### 接收parameterMap的节点对象XNode列表开始解析
```java
  private void parameterMapElement(List<XNode> list) throws Exception {
    for (XNode parameterMapNode : list) {
      // @desc: ID
      String id = parameterMapNode.getStringAttribute("id");
      // @desc: 参数类型名称
      String type = parameterMapNode.getStringAttribute("type");
      // @desc: 参数类型Class
      Class<?> parameterClass = resolveClass(type);
      // @desc: 子节点列表:参数列表
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
      // @desc: 遍历参数列表
      for (XNode parameterNode : parameterNodes) {
        // @desc: 参数名称
        String property = parameterNode.getStringAttribute("property");
        // @desc: 参数Java类型
        String javaType = parameterNode.getStringAttribute("javaType");
        // @desc: 参数Jdbc类型
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        // @desc: 参数对应的ResultMapId
        String resultMap = parameterNode.getStringAttribute("resultMap");
        // @desc: 参数的性质:IN、OUT、INOUT
        String mode = parameterNode.getStringAttribute("mode");
        // @desc: 参数的类型处理器
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        // @desc: 范围
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        // @desc: 获取参数的性质
        ParameterMode modeEnum = resolveParameterMode(mode);
        // @desc: 参数的Java类型Class
        Class<?> javaTypeClass = resolveClass(javaType);
        // @desc: 参数的Jdbc类型Class
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        @SuppressWarnings("unchecked")
        // @desc: 参数的类型处理器Class
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        // @desc: 构建参数对象的参数映射
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      // @desc: 应用参数Map
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }
```
#### 构建参数映射
```java
  public ParameterMapping buildParameterMapping(
      Class<?> parameterType,
      String property,
      Class<?> javaType,
      JdbcType jdbcType,
      String resultMap,
      ParameterMode parameterMode,
      Class<? extends TypeHandler<?>> typeHandler,
      Integer numericScale) {

    // @desc: 获取参数映射使用的结果集Map的Id
    resultMap = applyCurrentNamespace(resultMap, true);
    // @desc: 获取参数的Java类型
    Class<?> javaTypeClass = resolveParameterJavaType(parameterType, property, javaType, jdbcType);
    // @desc: 获取参数的类型处理器
    TypeHandler<?> typeHandlerInstance = resolveTypeHandler(javaTypeClass, typeHandler);
   // @desc: 构建参数映射
    return new ParameterMapping.Builder(configuration, property, javaTypeClass)
        .jdbcType(jdbcType)
        .resultMapId(resultMap)
        .mode(parameterMode)
        .numericScale(numericScale)
        .typeHandler(typeHandlerInstance)
        .build();
  }
```
#### 参数Map构建
```java
  public ParameterMap addParameterMap(String id, Class<?> parameterClass, List<ParameterMapping> parameterMappings) {
    // @desc: 挂载命名空间
    id = applyCurrentNamespace(id, false);
    // @desc: 构建实例
    ParameterMap parameterMap = new ParameterMap.Builder(configuration, id, parameterClass, parameterMappings).build();
    // @desc: 存储到全局配置
    configuration.addParameterMap(parameterMap);
    return parameterMap;
  }
```

#### 说明
* 适用场景
    * 参数中Java实体的属性与结果中Jdbc字段不对应时
* 功能:
    * 将Java实体属性与结果中Jdbc字段一一对应
* 使用:
    * id: 指定唯一标识
    * type:指定Java实体
    * parameter 指定需要对应的参数列表
        * property 实体的属性名称
        * javaType 实体的Java类型
        * jdbcType 对应的jdbc类型
        * typeHandler 使用的类型处理器
        * resultMap   使用的ResultMap的ID
        * mode   参数性质:输入、输出、输入输出
        * numericScale
* 存储到全局配置
    * 存储结构: Map<String, ParameterMap> parameterMaps
    * key= 命名空间.ID
    * value= parameterMap
* 建议:
    * 不使用该对象,使用数据库别名来实现映射一一对应