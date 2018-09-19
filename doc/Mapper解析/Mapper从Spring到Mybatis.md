## MapperFactoryBean

#### 继承关系说明

1. `MapperFactoryBean<T> extends SqlSessionDaoSupport implements FactoryBean<T>`
2. `public abstract class SqlSessionDaoSupport extends DaoSupport`
3. `public abstract class DaoSupport implements InitializingBean`

#### DaoSupport说明
##### DaoSupport实现了InitializingBean接口:该接口会在Bean初始化调用init-method之前调用afterPropertiesSet

```java
public final void afterPropertiesSet() throws IllegalArgumentException, BeanInitializationException {
    // @desc: 第一步调用:抽象方法
    this.checkDaoConfig();
    try {
        // @desc: 第二步调用:空实现
        this.initDao();
    } catch (Exception var2) {
        throw new BeanInitializationException("Initialization of DAO failed", var2);
    }
}
```
#### SqlSessionDaoSupport说明
##### SqlSessionDaoSupport在DaoSupport基础上新增SqlSession的获取以及设置获取方式

```java
/**
 * @desc: 设置sqlSession的SqlSessionFactory
 */
public void setSqlSessionFactory(SqlSessionFactory sqlSessionFactory) {
    if (!this.externalSqlSession) {
        this.sqlSession = new SqlSessionTemplate(sqlSessionFactory);
    }
}

/**
 * @desc: 设置sqlSession的SqlSessionTemplate
 * SqlSessionTemplate 优于 SqlSessionFactory
 */
public void setSqlSessionTemplate(SqlSessionTemplate sqlSessionTemplate) {
    this.sqlSession = sqlSessionTemplate;
    this.externalSqlSession = true;
}

/**
 * @desc: SqlSession是一个线程安全的对象
 */
public SqlSession getSqlSession() {
    return this.sqlSession;
}

/**
 * @desc: 校验SqlSession不能为空
 */
@Override
protected void checkDaoConfig() {
    notNull(this.sqlSession, "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required");
}
```

#### MapperFactoryBean说明
##### MapperFactoryBean继承了SqlSessionDaoSupport并实现了IOC:FactoryBean

1. 重写DaoSupport#checkDaoConfig:向Mybatis的Configuration中注册Mapper

```java
@Override
protected void checkDaoConfig() {
    super.checkDaoConfig();
    notNull(this.mapperInterface, "Property 'mapperInterface' is required");
    // @desc: 获取Configuration
    Configuration configuration = getSqlSession().getConfiguration();
    // @desc: addToConfig默认为true
    // @desc: mybtais的Configuration中未注册mapperInterface时注册
    if (this.addToConfig && !configuration.hasMapper(this.mapperInterface)) {
      try {
        configuration.addMapper(this.mapperInterface);
      } catch (Exception e) {
        logger.error("Error while adding the mapper '" + this.mapperInterface + "' to configuration.", e);
        throw new IllegalArgumentException(e);
      } finally {
        ErrorContext.instance().reset();
      }
    }
}
 ```

2. 重写FactoryBean#getObject:从IOC中获取Mapper

```java
@Override
public T getObject() throws Exception {
return getSqlSession().getMapper(this.mapperInterface);
}
```

3. mapperInterface说明: 详见MapperScannerRegistrar#registerBeanDefinitions

```java
// @desc: 设置扫描器属性:指定接口
Class<?> markerInterface = annoAttrs.getClass("markerInterface");
// @desc: markerInterface默认值为Class.class
if (!Class.class.equals(markerInterface)) {
    //将不是Class的类标记为接口
    scanner.setMarkerInterface(markerInterface);
}
```

#### 总结

1. MapperFactoryBean实现了InitializingBean因此初始化时会调用afterPropertiesSet
2. afterPropertiesSet调用了MapperFactoryBean#checkDaoConfig
3. checkDaoConfig调用了Configuration#addMapper
4. 至此Mapper被注册到Mybatis#Configuration中