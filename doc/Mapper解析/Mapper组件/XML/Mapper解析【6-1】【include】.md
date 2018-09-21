## include节点解析

#### include节点解析发生在增删改查节点解析之中
```java
    // @desc: include节点解析
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    // @desc: 子节点只能是include
    includeParser.applyIncludes(context.getNode());
```
##### 接收select、insert、update、delete的单节点对象XNode开始解析

```java
  public void applyIncludes(Node source) {
    Properties variablesContext = new Properties();
    Properties configurationVariables = configuration.getVariables();
    if (configurationVariables != null) {
      variablesContext.putAll(configurationVariables);
    }
    // @desc: 解析出全局对象的properties变量后开始解析
    applyIncludes(source, variablesContext, false);
  }
  /**
   * @desc: 解析流程:
   * @param: source 解析的节点XNode
   * @param: variablesContext 该节点可以使用的properties变量
   * @param: included 指向的SQL节点已引入并且自身的properties已解析
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
      // @desc: 解析出内置properties属性后递归解析节点
      applyIncludes(toInclude, toIncludeContext, true);
      if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
        toInclude = source.getOwnerDocument().importNode(toInclude, true);
      }
      // @desc: 解析完成后替换
      source.getParentNode().replaceChild(toInclude, source);
      while (toInclude.hasChildNodes()) {
        toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
      }
      toInclude.getParentNode().removeChild(toInclude);
    // @desc: 非include的元素节点
    // @desc: 增删改查节点入口
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

#### 说明
1. 文本节点内容可以使用${value}作为properties变量
2. 元素节点属性可以使用${value}作为properties变量
3. include中的非include元素节点不解析,${value}属性会被解析
4. include可以使用property子节点来定义properties变量
5. include只能引用Sql节点
6. include可以递归调用
7. 解析完成后include会被指定的解析后的内容替换
