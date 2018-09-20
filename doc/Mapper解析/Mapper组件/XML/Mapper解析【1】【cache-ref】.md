### cache-ref解析

####  接收cache-ref的节点对象XNode开始解析
```java
  private void cacheRefElement(XNode context) {
    if (context != null) {
      // @desc: 解析出当前Mapper的命名空间和缓存指向的内存空间并设置到配置对象中
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      // @desc: 构建一个缓存解析器
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        // @desc: 调用解析器的解析方法
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        // @desc: 解析失败则先挂起,有可能指向的命名空间未加载
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }
```
#### 缓存解析器
```java
public class CacheRefResolver {
  private final MapperBuilderAssistant assistant;
  private final String cacheRefNamespace;

  /**
   * @desc: 需要一个Mapper构建助手和指向的命名空间
   */
  public CacheRefResolver(MapperBuilderAssistant assistant, String cacheRefNamespace) {
    this.assistant = assistant;
    this.cacheRefNamespace = cacheRefNamespace;
  }

  /**
   * @desc: 解析方法
   */
  public Cache resolveCacheRef() {
    // @desc: 配置到解析助手中
    return assistant.useCacheRef(cacheRefNamespace);
  }
}
```

####  指向缓存的使用
```
  public Cache useCacheRef(String namespace) {
    if (namespace == null) {
      throw new BuilderException("cache-ref element requires a namespace attribute.");
    }
    try {
      unresolvedCacheRef = true;
      // @desc: 获取缓存
      Cache cache = configuration.getCache(namespace);
      if (cache == null) {
        throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.");
      }
      // @desc: 返回缓存
      currentCache = cache;
      unresolvedCacheRef = false;
      return cache;
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("No cache for namespace '" + namespace + "' could be found.", e);
    }
  }
```

#### 对挂起的缓存指向再次解析
```
  private void parsePendingCacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }
```

#### 流程说明

* 明确2点
    1. 节点所在的命名空间:节点所在Mapper的命名空间
    2. 节点指向的命名空间:复用的命名空间
* 功能
    * 通过指向的命名空间来获取其缓存对象
* 注意
    * 指向的命名空间有还可能未被解析到
        * 会在整个XML解析后再次尝试解析一次
    * 指向的命名空间有还可能不存在
    * 无论解析是否成功都将存储在配置对象中
        * 存储结构:Map<String, String> cacheRefMap
        * key=所在的命名空间
        * value=指向的命名空间
