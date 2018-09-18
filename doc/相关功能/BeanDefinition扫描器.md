## BeanDefinition类路径扫描器 ClassPathBeanDefinitionScanner

```java
public class ClassPathBeanDefinitionScanner extends ClassPathScanningCandidateComponentProvider {
    /**
     * @desc: 持有一个BeanDefinition注册器
     */
   private final BeanDefinitionRegistry registry;
    /**
     * @desc: BeanDefinition的默认定义
     */
   private BeanDefinitionDefaults beanDefinitionDefaults = new BeanDefinitionDefaults();

   @Nullable
   private String[] autowireCandidatePatterns;

    /**
     * @desc: Bean名称生成器
     */
   private BeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();
    /**
     * @desc: 作用域元数据解析器
     */
   private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();

    /**
     * @desc: 默认包含注解配置
     */
   private boolean includeAnnotationConfig = true;

   public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry) {
      this(registry, true);
   }

   public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters) {
      this(registry, useDefaultFilters, getOrCreateEnvironment(registry));
   }
   public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
         Environment environment) {

      this(registry, useDefaultFilters, environment,
            (registry instanceof ResourceLoader ? (ResourceLoader) registry : null));
   }
    /**
     * @desc: 完整构造器
     */
   public ClassPathBeanDefinitionScanner(BeanDefinitionRegistry registry, boolean useDefaultFilters,
         Environment environment, @Nullable ResourceLoader resourceLoader) {
      // @desc: Bean定义注册器不能为空
      Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
      this.registry = registry;
      // @desc: 是否使用默认的过滤器:默认为true
      if (useDefaultFilters) {
         // @desc: 调用父类的方法注册默认的过滤器：
         registerDefaultFilters();
      }
      // @desc: 设置环境
      setEnvironment(environment);
      // @desc: 设置资源加载器
      setResourceLoader(resourceLoader);
   }

   @Override
   public final BeanDefinitionRegistry getRegistry() {
      return this.registry;
   }

    /**
     * @desc: 设置默认的BeanDefinition
     */
   public void setBeanDefinitionDefaults(@Nullable BeanDefinitionDefaults beanDefinitionDefaults) {
      this.beanDefinitionDefaults =
            (beanDefinitionDefaults != null ? beanDefinitionDefaults : new BeanDefinitionDefaults());
   }

   public BeanDefinitionDefaults getBeanDefinitionDefaults() {
      return this.beanDefinitionDefaults;
   }

   public void setAutowireCandidatePatterns(@Nullable String... autowireCandidatePatterns) {
      this.autowireCandidatePatterns = autowireCandidatePatterns;
   }
   /**
    * @desc: 设置Bean名称生成器：AnnotationBeanNameGenerator
    *        shortName=首字母小写(前2字母均为大写时不处理)
    */
   public void setBeanNameGenerator(@Nullable BeanNameGenerator beanNameGenerator) {
      this.beanNameGenerator = (beanNameGenerator != null ? beanNameGenerator : new AnnotationBeanNameGenerator());
   }

   /**
    * @desc: 设置作用域元数据解析器：AnnotationScopeMetadataResolver
    *        默认:作用域为singleton,作用域代理模式为NO
    */
   public void setScopeMetadataResolver(@Nullable ScopeMetadataResolver scopeMetadataResolver) {
      this.scopeMetadataResolver =
            (scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
   }
   /**
    * @desc: 设置作用域代理模式
    */
   public void setScopedProxyMode(ScopedProxyMode scopedProxyMode) {
      this.scopeMetadataResolver = new AnnotationScopeMetadataResolver(scopedProxyMode);
   }


   public void setIncludeAnnotationConfig(boolean includeAnnotationConfig) {
      this.includeAnnotationConfig = includeAnnotationConfig;
   }


   /**
    * @desc: 扫描逻辑：返回注册的Bean个数
    */
   public int scan(String... basePackages) {
      int beanCountAtScanStart = this.registry.getBeanDefinitionCount();

      doScan(basePackages);

      // Register annotation config processors, if necessary.
      // @desc: 基于注解的Bean后置处理
      if (this.includeAnnotationConfig) {
         AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
      }

      return (this.registry.getBeanDefinitionCount() - beanCountAtScanStart);
   }

    /**
     * @desc: 扫描逻辑
     */
   protected Set<BeanDefinitionHolder> doScan(String... basePackages) {
      Assert.notEmpty(basePackages, "At least one base package must be specified");
      Set<BeanDefinitionHolder> beanDefinitions = new LinkedHashSet<>();
       // @desc: 遍历扫描包路径
      for (String basePackage : basePackages) {
         // @desc: 调用父类方法获取符合条件的真正需要注册的BeanDefinition
         Set<BeanDefinition> candidates = findCandidateComponents(basePackage);
         for (BeanDefinition candidate : candidates) {
            ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(candidate);
           // @desc: 设置作用域值
            candidate.setScope(scopeMetadata.getScopeName());
           // @desc: 获取Bean名称
            String beanName = this.beanNameGenerator.generateBeanName(candidate, this.registry);
            if (candidate instanceof AbstractBeanDefinition) {
               // @desc: AbstractBeanDefinition实例的处理 设置默认Bean定义、设置注入属性
               postProcessBeanDefinition((AbstractBeanDefinition) candidate, beanName);
            }
            if (candidate instanceof AnnotatedBeanDefinition) {
                 // @desc: AnnotatedBeanDefinition后置处理：通用注解处理
               AnnotationConfigUtils.processCommonDefinitionAnnotations((AnnotatedBeanDefinition) candidate);
            }
            // @desc: BeanDefinition兼容性校验
            if (checkCandidate(beanName, candidate)) {
               BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(candidate, beanName);
                 // @desc: 设置Bean的代理模式
               definitionHolder =
                     AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
               // @desc: BeanDefinition持有者放入Set集合
               beanDefinitions.add(definitionHolder);
              // @desc: 注册BeanDefinition
               registerBeanDefinition(definitionHolder, this.registry);
            }
         }
      }
      return beanDefinitions;
   }

   /**
    * @desc: AbstractBeanDefinition 处理
    * 1. 设置默认属性
    * 2. 设置@Autoware的类型{name,type}
    */
   protected void postProcessBeanDefinition(AbstractBeanDefinition beanDefinition, String beanName) {
      beanDefinition.applyDefaults(this.beanDefinitionDefaults);
      if (this.autowireCandidatePatterns != null) {
         beanDefinition.setAutowireCandidate(PatternMatchUtils.simpleMatch(this.autowireCandidatePatterns, beanName));
      }
   }
   /**
    * @desc: 注册Bean定义
    */
   protected void registerBeanDefinition(BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry) {
      BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, registry);
   }

   /**
    * @desc: BeanDefinition校验
    */
   protected boolean checkCandidate(String beanName, BeanDefinition beanDefinition) throws IllegalStateException {
       // @desc: IOC中尚未注册时直接返回true
      if (!this.registry.containsBeanDefinition(beanName)) {
         return true;
      }
      BeanDefinition existingDef = this.registry.getBeanDefinition(beanName);
      BeanDefinition originatingDef = existingDef.getOriginatingBeanDefinition();
      if (originatingDef != null) {
         existingDef = originatingDef;
      }
      // @desc: 校验兼容性:兼容一致则不再进行注册
      if (isCompatible(beanDefinition, existingDef)) {
         return false;
      }
      throw new ConflictingBeanDefinitionException("Annotation-specified bean name '" + beanName +
            "' for bean class [" + beanDefinition.getBeanClassName() + "] conflicts with existing, " +
            "non-compatible bean definition of same name and class [" + existingDef.getBeanClassName() + "]");
   }

    /**
     * @desc: 校验兼容性:以下说明兼容[等价]
     * 1. 已注册的Bean不是扫描得到的
     * 2. 已注册的Bean和扫描得到的Bean来源相同
     * 3. 已注册的Bean等价于扫描得到的Bean
     */
   protected boolean isCompatible(BeanDefinition newDefinition, BeanDefinition existingDefinition) {
      return (!(existingDefinition instanceof ScannedGenericBeanDefinition) ||  // explicitly registered overriding bean
            (newDefinition.getSource() != null && newDefinition.getSource().equals(existingDefinition.getSource())) ||  // scanned same file twice
            newDefinition.equals(existingDefinition));  // scanned equivalent class twice
   }
    /**
     * @desc: 运行环境
     */
   private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
      Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
      if (registry instanceof EnvironmentCapable) {
         return ((EnvironmentCapable) registry).getEnvironment();
      }
      return new StandardEnvironment();
   }

}
```
#### registerDefaultFilters源码:
```java
protected void registerDefaultFilters() {
        // @desc: 添加过滤器：@Component
        // @desc: 说明:@Component 包含了@Service @Controller @RestController等子类注解
		this.includeFilters.add(new AnnotationTypeFilter(Component.class));
		ClassLoader cl = ClassPathScanningCandidateComponentProvider.class.getClassLoader();
		try {
		    // @desc: 添加过滤器：@ManagedBean
			this.includeFilters.add(new AnnotationTypeFilter(
					((Class<? extends Annotation>) ClassUtils.forName("javax.annotation.ManagedBean", cl)), false));
			logger.debug("JSR-250 'javax.annotation.ManagedBean' found and supported for component scanning");
		}
		catch (ClassNotFoundException ex) {
			// JSR-250 1.1 API (as included in Java EE 6) not available - simply skip.
		}
		try {
		 // @desc: 添加过滤器：@Named
			this.includeFilters.add(new AnnotationTypeFilter(
					((Class<? extends Annotation>) ClassUtils.forName("javax.inject.Named", cl)), false));
			logger.debug("JSR-330 'javax.inject.Named' annotation found and supported for component scanning");
		}
		catch (ClassNotFoundException ex) {
			// JSR-330 API not available - simply skip.
		}
	}
```
#### findCandidateComponents源码:
```java
public Set<BeanDefinition> findCandidateComponents(String basePackage) {
    Set<BeanDefinition> candidates = new LinkedHashSet<BeanDefinition>();
    try {
        // @desc: 从classpath下查找对应包下的class文件路径:classpath*:ABCDEF/**/*.class
        String packageSearchPath = ResourcePatternResolver.CLASSPATH_ALL_URL_PREFIX +
                resolveBasePackage(basePackage) + '/' + this.resourcePattern;
        // @desc: 从资源路径中解析出资源列表
        Resource[] resources = this.resourcePatternResolver.getResources(packageSearchPath);
        boolean traceEnabled = logger.isTraceEnabled();
        boolean debugEnabled = logger.isDebugEnabled();
        for (Resource resource : resources) {
            // @desc: 省略日志
            // @desc: 资源必须是可读的
            if (resource.isReadable()) {
                try {
                    MetadataReader metadataReader = this.metadataReaderFactory.getMetadataReader(resource);
                    // @desc: 资源必须是符合过滤条件的:
                    // @desc: excludeFilters、includeFilters配置在这里起作用
                    if (isCandidateComponent(metadataReader)) {
                        ScannedGenericBeanDefinition sbd = new ScannedGenericBeanDefinition(metadataReader);
                        sbd.setResource(resource);
                        sbd.setSource(resource);
                        // @desc: 构建的组件必须符合条件
                        if (isCandidateComponent(sbd)) {
                            // @desc: 省略日志
                            // @desc: 校验完毕说明可注册
                            candidates.add(sbd);
                        }
                        else {// @desc: 省略日志}
                    }
                    else {// @desc: 省略日志}
                }
                catch (Throwable ex) {
                   // @desc: 省略异常 BeanDefinitionStoreException
                }
            }
             else {// @desc: 省略日志}
        }
    }
    catch (IOException ex) {
        // @desc: 省略异常 BeanDefinitionStoreException
    }
    return candidates;
}

```

#### 扫描流程说明

1. 获取包扫描路径:ABCDEF
    1. 解析后格式为:classpath*:ABCDEF/**/*.class
    2. 从class文件中获取Spring资源对象
2. Filters校验
    1. excludeFilters
    2. includeFilters
        * 默认扫描@Component、@ManagedBean、@Named等及其子类注解
            * @Service
            * @Controller
            * @ResetController
            * @Component
            * @Configuration
            * @ManagedBean
            * @Named
            * ...
3. 具体化为ScannedGenericBeanDefinition
    1. 设置资源对象
4. 组件校验
    1. 顶级类或者静态内部类
    2. 具体的类
    3. 抽象类但是其方法含有@Lookup
5. 装配BeanDefinition
    1. 装配Bean的通用属性
    2. 装配Bean的注解属性
6. 兼容性校验:[以下条件为校验失败]
    1. 已注册的Bean不是扫描得到的
    2. 已注册的Bean和扫描得到的Bean来源相同
    3. 已注册的Bean等价于扫描得到的Bean
7. 前往注册BeanDefinition
