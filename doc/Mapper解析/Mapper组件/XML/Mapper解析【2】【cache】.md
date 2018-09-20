## cache 解析

####  接收cache的节点对象XNode开始解析
```java
  private void cacheElement(XNode context) throws Exception {
    if (context != null) {
      // @desc: 缓存类型
      String type = context.getStringAttribute("type", "PERPETUAL");
      // @desc: 缓存类的Class
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      // @desc: 回收策略
      String eviction = context.getStringAttribute("eviction", "LRU");
      // @desc: 回收策略类Class
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      // @desc: 刷新间隔
      Long flushInterval = context.getLongAttribute("flushInterval");
      // @desc: 缓存对象个数
      Integer size = context.getIntAttribute("size");
      // @desc: 缓存只读
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      // @desc: 缓存阻塞
      boolean blocking = context.getBooleanAttribute("blocking", false);
      // @desc: 缓存属性
      Properties props = context.getChildrenAsProperties();
      // @desc: 构建使用
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }
```
#### 缓存对象的构建
```java
  public Cache useNewCache(Class<? extends Cache> typeClass,
      Class<? extends Cache> evictionClass,
      Long flushInterval,
      Integer size,
      boolean readWrite,
      boolean blocking,
      Properties props) {
    // @desc: 缓存必须指定命名空间
    Cache cache = new CacheBuilder(currentNamespace)
        // @desc: 设置缓存个各个属性
        .implementation(valueOrDefault(typeClass, PerpetualCache.class))
        .addDecorator(valueOrDefault(evictionClass, LruCache.class))
        .clearInterval(flushInterval)
        .size(size)
        .readWrite(readWrite)
        .blocking(blocking)
        .properties(props)
        .build();
    // @desc: 全局配置
    configuration.addCache(cache);
    // @desc: 应用到解析中
    currentCache = cache;
    return cache;
  }
```

#### 说明

1. 配置的是缓存对象的各个属性
2. 功能为:给当前命名空间配置一个缓存对象
3. 存储到全局配置中
    *  存储结构:Map<String, Cache> caches
    *  key=命名空间
    *  value=Cache实例
4. 设置到当前助手中用于后续解析
