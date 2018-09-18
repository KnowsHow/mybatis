## 注解BeanDefinition的读取器 AnnotatedBeanDefinitionReader

```java
public class AnnotatedBeanDefinitionReader {

    /**
     * @desc: BeanDefinition注册器
     */
	private final BeanDefinitionRegistry registry;
    /**
     * @desc: Bean名称生成器
     */
	private BeanNameGenerator beanNameGenerator = new AnnotationBeanNameGenerator();
    /**
     * @desc: Bean作用域解析器
     */
	private ScopeMetadataResolver scopeMetadataResolver = new AnnotationScopeMetadataResolver();
    /**
     * @desc: 条件校验
     */
	private ConditionEvaluator conditionEvaluator;

	public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry) {
		this(registry, getOrCreateEnvironment(registry));
	}

    /**
     * @desc:  构造方法
     */
	public AnnotatedBeanDefinitionReader(BeanDefinitionRegistry registry, Environment environment) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		Assert.notNull(environment, "Environment must not be null");
		this.registry = registry;
		this.conditionEvaluator = new ConditionEvaluator(registry, environment, null);
		AnnotationConfigUtils.registerAnnotationConfigProcessors(this.registry);
	}

	public final BeanDefinitionRegistry getRegistry() {
		return this.registry;
	}

	public void setEnvironment(Environment environment) {
		this.conditionEvaluator = new ConditionEvaluator(this.registry, environment, null);
	}

    /**
     * @desc: 设置Bean名称生成器: 默认AnnotationBeanNameGenerator
     */
	public void setBeanNameGenerator(BeanNameGenerator beanNameGenerator) {
		this.beanNameGenerator = (beanNameGenerator != null ? beanNameGenerator : new AnnotationBeanNameGenerator());
	}

    /**
     * @desc: 设置Bean作用域解析器: 默认AnnotationScopeMetadataResolver
     */
	public void setScopeMetadataResolver(ScopeMetadataResolver scopeMetadataResolver) {
		this.scopeMetadataResolver =
				(scopeMetadataResolver != null ? scopeMetadataResolver : new AnnotationScopeMetadataResolver());
	}
	
	/**
	 * @desc:  Bean注册功能
	 */
	public void register(Class<?>... annotatedClasses) {
		for (Class<?> annotatedClass : annotatedClasses) {
			registerBean(annotatedClass);
		}
	}

	@SuppressWarnings("unchecked")
	public void registerBean(Class<?> annotatedClass) {
		registerBean(annotatedClass, null, (Class<? extends Annotation>[]) null);
	}

	@SuppressWarnings("unchecked")
	public void registerBean(Class<?> annotatedClass, Class<? extends Annotation>... qualifiers) {
		registerBean(annotatedClass, null, qualifiers);
	}

	/**
	 * @desc: Bean注册逻辑
	 */
	@SuppressWarnings("unchecked")
	public void registerBean(Class<?> annotatedClass, String name, Class<? extends Annotation>... qualifiers) {
	    // @desc: 通用基于注解的BeanDefinition
		AnnotatedGenericBeanDefinition abd = new AnnotatedGenericBeanDefinition(annotatedClass);
		// @desc: @Conditional注解且不满足其条件则不加载
		if (this.conditionEvaluator.shouldSkip(abd.getMetadata())) {
			return;
		}
        // @desc: 获取注解的作用域元数据
		ScopeMetadata scopeMetadata = this.scopeMetadataResolver.resolveScopeMetadata(abd);
		// @desc: 设置作用域
		abd.setScope(scopeMetadata.getScopeName());
		// @desc: 设置Bean名称
		String beanName = (name != null ? name : this.beanNameGenerator.generateBeanName(abd, this.registry));
		// @desc: 设置注解的通用BeanDefinition[lazy、Primary、DependsOn、Role等]
		AnnotationConfigUtils.processCommonDefinitionAnnotations(abd);
		// @desc: @Qualifier注解处理
		if (qualifiers != null) {
			for (Class<? extends Annotation> qualifier : qualifiers) {
				if (Primary.class == qualifier) {
					abd.setPrimary(true);
				}
				else if (Lazy.class == qualifier) {
					abd.setLazyInit(true);
				}
				else {
					abd.addQualifier(new AutowireCandidateQualifier(qualifier));
				}
			}
		}
		
        // @desc: 构建BeanDefinition持有者
		BeanDefinitionHolder definitionHolder = new BeanDefinitionHolder(abd, beanName);
		// @desc: 设置持有者的Bean作用域的代理模式
		definitionHolder = AnnotationConfigUtils.applyScopedProxyMode(scopeMetadata, definitionHolder, this.registry);
		// @desc: 使用工具类注册BeanDefinition
		BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, this.registry);
	}


	private static Environment getOrCreateEnvironment(BeanDefinitionRegistry registry) {
		Assert.notNull(registry, "BeanDefinitionRegistry must not be null");
		// @desc: registry为AnnotationConfigApplicationContext
		// @desc: 其环境默认为StandardEnvironment
		if (registry instanceof EnvironmentCapable) {
			return ((EnvironmentCapable) registry).getEnvironment();
		}
		// @desc: 非Servlet环境下普通WEB运行环境
		return new StandardEnvironment();
	}

}
```
#### 说明:

1. 注解读取器接收一个注册器:这里为AnnotationConfigApplicationContext
2. 注解读取器读取对象为注解类、主要读取注解元数据
3. 主要功能为：通过注册器具有注册BeanDefinition功能
4. 注册实现为:BeanDefinitionReaderUtils.registerBeanDefinition


