## MapperRegistry:用于注册Mapper接口

```java
public class MapperRegistry {

  private final Configuration config;

  /**
   * @desc: 存储结构
   */
  private final Map<Class<?>, MapperProxyFactory<?>> knownMappers = new HashMap<Class<?>, MapperProxyFactory<?>>();

  /**
   * @desc: 构造方法
   */
  public MapperRegistry(Configuration config) {
    this.config = config;
  }

  /**
   * @desc: 获取方法
   */
  @SuppressWarnings("unchecked")
  public <T> T getMapper(Class<T> type, SqlSession sqlSession) {
    // @desc: 从Map中获取JDK代理工厂
    final MapperProxyFactory<T> mapperProxyFactory = (MapperProxyFactory<T>) knownMappers.get(type);
    if (mapperProxyFactory == null) {
      throw new BindingException("Type " + type + " is not known to the MapperRegistry.");
    }
    try {
      // @desc: 返回的是JDK代理工厂产生的实例
      return mapperProxyFactory.newInstance(sqlSession);
    } catch (Exception e) {
      throw new BindingException("Error getting mapper instance. Cause: " + e, e);
    }
  }

  public <T> boolean hasMapper(Class<T> type) {
    return knownMappers.containsKey(type);
  }

  /**
   * @desc: 注册方法
   */
  public <T> void addMapper(Class<T> type) {
    if (type.isInterface()) {
      if (hasMapper(type)) {
        throw new BindingException("Type " + type + " is already known to the MapperRegistry.");
      }
      boolean loadCompleted = false;
      try {
        // @desc: 注册实质
        knownMappers.put(type, new MapperProxyFactory<T>(type));
        // @desc: 注解构建者
        MapperAnnotationBuilder parser = new MapperAnnotationBuilder(config, type);
        // @desc: 注解解析
        parser.parse();
        loadCompleted = true;
      } finally {
        if (!loadCompleted) {
          knownMappers.remove(type);
        }
      }
    }
  }

  /**
   * @since 3.2.2
   * @desc: 获取全部Mapper
   */
  public Collection<Class<?>> getMappers() {
    return Collections.unmodifiableCollection(knownMappers.keySet());
  }

  /**
   * @since 3.2.2
   * @desc: 注册方法:基于包路径和指定的类型
   */
  public void addMappers(String packageName, Class<?> superType) {
    ResolverUtil<Class<?>> resolverUtil = new ResolverUtil<Class<?>>();
    resolverUtil.find(new ResolverUtil.IsA(superType), packageName);
    Set<Class<? extends Class<?>>> mapperSet = resolverUtil.getClasses();
    for (Class<?> mapperClass : mapperSet) {
      addMapper(mapperClass);
    }
  }

  /**
   * @since 3.2.2
   * @desc: 注册方法:基于包路径
   */
  public void addMappers(String packageName) {
    addMappers(packageName, Object.class);
  }

}
```

#### 说明
1. 注册实质为存储在Map<Class<?>, MapperProxyFactory<?>> knownMappers中
    * key=Mapper.class
    * value=MapperProxyFactory
2. MapperProxyFactory为Mapper的代理工厂,使用JDK代理
3. 注册方式:
    * Mapper.class注册
    * 包路径扫描注册
    * 包路径扫描+指定父类型注册
4. 获取方式:
    * 需要Mapper.class和SqlSession
    * 返回的是代理工厂产生的代理对象
    * 代理调用处理器为MapperProxy