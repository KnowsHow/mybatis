##  动态SQL解析

||判断条件|
|:--|:--|
|动态SQL|增删改查节点的SQL中有${}或者有非include、selectKey标签的SQL|
|解析位置|XMLScriptBuilder#parseDynamicTags|

##### 解析发生在创建SqlSource时判断是否为动态标签时

##### 内置支持的动态标签
```java
  private void initNodeHandlerMap() {
    nodeHandlerMap.put("trim", new TrimHandler());
    nodeHandlerMap.put("where", new WhereHandler());
    nodeHandlerMap.put("set", new SetHandler());
    nodeHandlerMap.put("foreach", new ForEachHandler());
    nodeHandlerMap.put("if", new IfHandler());
    nodeHandlerMap.put("choose", new ChooseHandler());
    nodeHandlerMap.put("when", new IfHandler());
    nodeHandlerMap.put("otherwise", new OtherwiseHandler());
    nodeHandlerMap.put("bind", new BindHandler());
  }
```
##### 解析发生处:
|属性|描述|
|:--|:--|
|child|增删改查节点的child|
|nodeHandlerMap|注册节点处理器的Map|
|contents|封装结果SQL的List<SqlNode>|

```java
String nodeName = child.getNode().getNodeName();
// @desc: 获取元素节点的名称对应的处理器
NodeHandler handler = nodeHandlerMap.get(nodeName);
// @desc: 不存在则说明为为自定义标签,抛异常
if (handler == null) {
throw new BuilderException("Unknown element <" + nodeName + "> in SQL statement.");
}
// @desc: 对应的处理器处理其标签的动态SQL
handler.handleNode(child, contents);
```

#### if

##### handleNode
```java
 @Override
 public void handleNode(XNode nodeToHandle, List<SqlNode> targetContents) {
   // @desc: 解析内容节点
   MixedSqlNode mixedSqlNode = parseDynamicTags(nodeToHandle);
   // @desc: 获取test属性
   String test = nodeToHandle.getStringAttribute("test");
   // @desc: 构建If节点,传入了条件和要解析的XNode
   IfSqlNode ifSqlNode = new IfSqlNode(mixedSqlNode, test);
   // @desc: 添加到contents
   targetContents.add(ifSqlNode);
 }
```
##### apply
```java
  @Override
  public boolean apply(DynamicContext context) {
    // @desc:评估器使用的是Ognl表达式处理,只有Boolean=true才解析内容
    if (evaluator.evaluateBoolean(test, context.getBindings())) {
      // @desc: contents为if节点的解析内容
      contents.apply(context);
      return true;
    }
    return false;
  }
```

#### 说明

所有动态标签的解析与If类似
1. 对应的标签处理器
2. 对应的标签对象XNode,含有apply方法,该方法会真正解析器内容
3. 每个增删改查节点对象最终创建为一个SqlSource
4. SqlSource持有XNode,该XNode为解析后的MixedSqlNode
5. MixedSqlNode持有List<SqlNode>,该集合为各个节点解析后的标签对象SqlNode
6. 创建SqlSource时会调用XNode的apply方法，该方法遍历List<SqlNode>调用SqlNode#apply完成Sql解析
