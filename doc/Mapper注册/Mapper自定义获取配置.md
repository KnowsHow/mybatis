## MapperScannerRegistrar#registerBeanDefinitions

#### 配置方式

1. @MapperScan使用MapperScannerRegistrar引入
2. XML使用MapperScannerConfigurer引入

#### MapperScannerRegistrar说明

1. 引入方式
```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Import(MapperScannerRegistrar.class)
public @interface MapperScan {
...
}
```
2. 扫描配置

```java
@Override
public void registerBeanDefinitions(AnnotationMetadata importingClassMetadata, BeanDefinitionRegistry registry) {
    // @desc: 获取引入类的MapperScan注解、并解析得到注解属性
    AnnotationAttributes annoAttrs = AnnotationAttributes.fromMap(importingClassMetadata.getAnnotationAttributes(MapperScan.class.getName()));
    // @desc: 获取扫描对象
    ClassPathMapperScanner scanner = new ClassPathMapperScanner(registry);
    // this check is needed in Spring 3.1
    // @desc: 配置资源加载器
    if (resourceLoader != null) {
        scanner.setResourceLoader(resourceLoader);
    }
    // @desc: 设置扫描器属性:指定注解
    Class<? extends Annotation> annotationClass = annoAttrs.getClass("annotationClass");
    if (!Annotation.class.equals(annotationClass)) {
        // @desc 将不是Annotation的注解指定为扫描对象
        scanner.setAnnotationClass(annotationClass);
    }
    // @desc: 设置扫描器属性:指定接口
    Class<?> markerInterface = annoAttrs.getClass("markerInterface");
    // @desc: markerInterface默认值为Class.class
    if (!Class.class.equals(markerInterface)) {
        //将不是Class的类标记为接口
        scanner.setMarkerInterface(markerInterface);
    }

    // @desc: 设置Bean名称生成器
    Class<? extends BeanNameGenerator> generatorClass = annoAttrs.getClass("nameGenerator");
    if (!BeanNameGenerator.class.equals(generatorClass)) {
        scanner.setBeanNameGenerator(BeanUtils.instantiateClass(generatorClass));
    }
    // @desc: 设置MapperFactoryBean
    Class<? extends MapperFactoryBean> mapperFactoryBeanClass = annoAttrs.getClass("factoryBean");
    if (!MapperFactoryBean.class.equals(mapperFactoryBeanClass)) {
        scanner.setMapperFactoryBean(BeanUtils.instantiateClass(mapperFactoryBeanClass));
    }
    // @desc: 设置sqlSessionTemplateRef、sqlSessionFactoryRef属性
    scanner.setSqlSessionTemplateBeanName(annoAttrs.getString("sqlSessionTemplateRef"));
    scanner.setSqlSessionFactoryBeanName(annoAttrs.getString("sqlSessionFactoryRef"));

    List<String> basePackages = new ArrayList<String>();
    for (String pkg : annoAttrs.getStringArray("value")) {
        if (StringUtils.hasText(pkg)) {
            basePackages.add(pkg);
        }
    }
    for (String pkg : annoAttrs.getStringArray("basePackages")) {
        if (StringUtils.hasText(pkg)) {
            basePackages.add(pkg);
        }
    }
    for (Class<?> clazz : annoAttrs.getClassArray("basePackageClasses")) {
        basePackages.add(ClassUtils.getPackageName(clazz));
    }
    // @desc: 注册扫描过滤器
    scanner.registerFilters();
    // @desc: 执行Mapper扫描程序
    scanner.doScan(StringUtils.toStringArray(basePackages));
}
 ```

#### 说明
1. 使用@MapperScan引入
2. 使用该配置后将不会采用默认配置方式
3. 扫描路径为自定义路径
4. 扫描所有非注解类
5. 扫描@Component、@ManagedBean、@Named等及其子类注解
6. 标记所有接口
