## MapperRegistry

#### 注册方式:MapperRegistry#addMapper

```java

/**
 * @desc: 存储Mapper接口的MapperProxyFactory的Map
 */
private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<Class<?>, MapperProxyFactory<?>>();

/**
 * @desc: 注册Mapper接口的方法
 */
public <T> void addMapper(Class<T> type) {
  // @desc: 必须为接口
  if (type.isInterface()) {
    // @desc: 只能注册一次
    if (hasMapper(type)) {
      throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
    }
    boolean loadCompleted = false;
    try {
      // @desc: 生成代理对象放入已注册列表
      knownMappers.put(type, new MapperProxyFactory<T>(type));
      // @desc: 注解解析Mapper:用于使用注解提供的SQL
      MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
      parser.parse();
      loadCompleted = true;
     } finally {
      // @desc: 构建失败则标记为未注册
      if (!loadCompleted) {
       knownMappers.remove(type);
      }
    }
  }
}
```

#### 流程说明:
1. 调用Configuration#addMapper作为入口注册Mapper
2. 调用MapperRegistry#addMapper
    1. 只注册接口
    2. 只能注册一次
3. 构建对应的MapperProxyFactory并存储在knownMappers
    1. key = 接口Class
    2. value = MapperProxyFactory
4. Mapper使用注解时解析其注解
    1. MapperAnnotationBuilder#parse

