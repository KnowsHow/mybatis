## sql 解析

#### 接收sql的节点对象XNode列表开始解析

```java
  private void sqlElement(List<XNode> list) throws Exception {
    // @desc: 数据库厂商ID标识决定解析流程
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }

  private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
    for (XNode context : list) {
      // @desc: 数据库厂商ID
      String databaseId = context.getStringAttribute("databaseId");
      String id = context.getStringAttribute("id");
      // @desc: 挂载到命名空间
      id = builderAssistant.applyCurrentNamespace(id, false);
      // @desc: 验证全局配置的数据库厂商标识和SQL节点配置的数据厂商标识是否一致
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        // @desc: 一致了才存储起来，存储结构为：Map<String, XNode>
        sqlFragments.put(id, context);
      }
    }
  }

  /**
   * @desc: 数据库厂商ID匹配机制
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      // @desc: 二者都配置了但是不等则不再解析
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      // @desc: 全局未配置,SQL节点配置了则不再解析
      if (databaseId != null) {
        return false;
      }
      // @desc: 相同ID的SQL节点以前配置过则不再解析
      if (this.sqlFragments.containsKey(id)) {
        XNode context = this.sqlFragments.get(id);
        if (context.getStringAttribute("databaseId") != null) {
          return false;
        }
      }
    }
    return true;
  }
```

#### 说明
* 相关属性

|属性|描述|
|:--|:--|
|id|SQL节点的ID|
|databaseId|SQL节点的数据库提供商ID|
|requiredDatabaseId|配置对象的数据库提供商ID|

* 流程说明
    1. 配置对象配置了数据库提供商ID,SQL节点未配置数据库提供商ID
        * 不解析SQL节点
    3. 配置对象未配置数据库提供商ID,SQL节点配置了数据库提供商ID
        * 不解析SQL节点
    2. 二者都配置了
        * 相等 解析SQL节点
        * 不等 不解析SQL节点
    4. 二者都未配置
        * 以前解析过同样的SQL节点,而该节点配置了数据库提供商ID 不解析SQL节点(按理说不可能出现)
        * 解析SQL节点
* 解析结果
    * 存储在sqlFragments中,sqlFragments来自Configuration
        * 存储结构Map<String, XNode>
        * key=命名空间.id
        * value=SQL节点的XPath对象XNode