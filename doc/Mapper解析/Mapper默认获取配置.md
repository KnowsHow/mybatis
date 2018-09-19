## MybatisAutoConfiguration#AutoConfiguredMapperScannerRegistrar

#### AutoConfiguredMapperScannerRegistrar说明
##### Spring boot 默认的Mapper扫描配置
```java
@Override
public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
   // @desc: 构建Mapper扫描器
  ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);
  try {
    if (this.resourceLoader != null) {
      scanner.setResourceLoader(this.resourceLoader);
    }
    // @desc: 获取启动类所在的包路径
    List<String> packages = AutoConfigurationPackages.get(this.beanFactory);
    // @desc: 记录日志
    // @desc: 设置扫描的注解类@Mapper
    scanner.setAnnotationClass(Mapper.class);
    // @desc: 注册默认的过滤器(与Spring类扫描配置一致)
    scanner.registerFilters();
    // @desc: 执行真正的扫描方法
    scanner.doScan(StringUtils.toStringArray(packages));
  } catch (IllegalStateException ex) {
    logger.debug("Could not determine auto-configuration package, automatic mapper scanning disabled.", ex);
  }
}
```

#### 说明：

1. 在用户未定义MapperFactoryBean时采用该配置
2. 该配置的扫描路径为:启动类使用的包路径
3. 该配置扫描@Mapper注解
4. 该配置扫描@Component、@ManagedBean、@Named等及其子类注解
