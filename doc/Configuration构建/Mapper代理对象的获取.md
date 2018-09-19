## Mapper代理对象的获取

#### 调用入口:MapperRegistry
```java
 /**
   * @desc: Mapper获取方法
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
```

#### Mapper的代理工厂:MapperProxyFactory

```java
public class MapperProxyFactory<T> {

  /**
   * @desc: 代理的类Class
   */
  private final Class<T> mapperInterface;

  /**
   * @desc: 代理类的方法级别的存储
   */
  private final Map<Method, MapperMethod> methodCache = new ConcurrentHashMap<Method, MapperMethod>();

  /**
   * @desc: 构造器(代理类Class)
   */
  public MapperProxyFactory(Class<T> mapperInterface) {
    this.mapperInterface = mapperInterface;
  }

  public Class<T> getMapperInterface() {
    return mapperInterface;
  }

  public Map<Method, MapperMethod> getMethodCache() {
    return methodCache;
  }

  /**
   * @desc: 产生实例(MapperProxy)
   */
  @SuppressWarnings("unchecked")
  protected T newInstance(MapperProxy<T> mapperProxy) {
    return (T) Proxy.newProxyInstance(mapperInterface.getClassLoader(), new Class[] { mapperInterface }, mapperProxy);
  }

  /**
   * @desc: 产生实例(SqlSession)
   */
  public T newInstance(SqlSession sqlSession) {
    final MapperProxy<T> mapperProxy = new MapperProxy<T>(sqlSession, mapperInterface, methodCache);
    return newInstance(mapperProxy);
  }

}
```




#### 说明

1. Mapper代理对象在Mapper注册器中处获取
2. Mapper代理对象是由MapperProxyFactory工厂创建的,使用了JDK动态代理
    * 加载器:mapperInterface.getClassLoader()
    * Class:mapperInterface
    * 调用处理器:MapperProxy
3. 每次获取都是一个新的代理对象(一般只需要获取一次即可)
