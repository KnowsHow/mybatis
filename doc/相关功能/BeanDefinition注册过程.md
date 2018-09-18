## BeanDefinition注册流程


#### BeanDefinition通过读取器或者扫描器处理
1. 构建出BeanDefinitionHolder对象
2. 调用方法 `BeanDefinitionReaderUtils.registerBeanDefinition(definitionHolder, registry);`


#### registerBeanDefinition源码:

```java
public static void registerBeanDefinition(
			BeanDefinitionHolder definitionHolder, BeanDefinitionRegistry registry)
		throws BeanDefinitionStoreException {
	// Register bean definition under primary name.
	// @desc: 获取Bean名称
	String beanName = definitionHolder.getBeanName();
	// @desc: 调用注册器的注册方法registerBeanDefinition
	registry.registerBeanDefinition(beanName, definitionHolder.getBeanDefinition());

	// Register aliases for bean name, if any.
	// @desc: 注册Bean名称的别名
	String[] aliases = definitionHolder.getAliases();
	if (aliases != null) {
		for (String alias : aliases) {
		   // @desc: 调用注册器的注册方法registerAlias
			registry.registerAlias(beanName, alias);
		}
	}
}
```
#### 参数来源说明

1. BeanDefinitionHolder:
    1. 在AnnotatedBeanDefinitionReader中构造
    2. 在ClassPathBeanDefinitionScanner中构造
2. BeanDefinitionRegistry:
    1. AnnotatedBeanDefinitionReader->AnnotationConfigApplicationContext
    2. ClassPathBeanDefinitionScanner->AnnotationConfigApplicationContext

#### 逻辑注册流程

1. BeanDefinitionReaderUtils.registerBeanDefinition ——> registry
2. registry.registerBeanDefinition ——> AnnotationConfigApplicationContext
2. AnnotationConfigApplicationContext ——> DefaultListableBeanFactory
3. DefaultListableBeanFactory.registerBeanDefinition

#### IOC注册流程
##### DefaultListableBeanFactory#registerBeanDefinition

```java
@Override
public void registerBeanDefinition(String beanName, BeanDefinition beanDefinition)
		throws BeanDefinitionStoreException {
	Assert.hasText(beanName, "Bean name must not be empty");
	Assert.notNull(beanDefinition, "BeanDefinition must not be null");
	// @desc:通用类处理[抽象方法Overrides校验]
	if (beanDefinition instanceof AbstractBeanDefinition) {
		try {
			((AbstractBeanDefinition) beanDefinition).validate();
		}
		catch (BeanDefinitionValidationException ex) {
			// @desc:  省略异常:BeanDefinitionStoreException
		}
	}
	BeanDefinition oldBeanDefinition;
	// @desc: 尝试获取BeanDefinition
	oldBeanDefinition = this.beanDefinitionMap.get(beanName);
	// @desc: 获取到说明被注册过
	if (oldBeanDefinition != null) {
		// @desc: 不允许被重写则抛出异常
		if (!isAllowBeanDefinitionOverriding()) {
			// @desc:  省略异常:BeanDefinitionStoreException
		}
		 // @desc: @Role级别小于新的定义则只记录日志,允许重写
		else if (oldBeanDefinition.getRole() < beanDefinition.getRole()) {
			// @desc: 省略日志
		}
		// @desc: 新的定义与旧的定义一致只记录日志
		else if (!beanDefinition.equals(oldBeanDefinition)) {
			// @desc: 省略日志
		}
		else {
			// @desc: 省略日志
		}
		// @desc: 注册的真正位置,方法ConcurrentHashMap中
		this.beanDefinitionMap.put(beanName, beanDefinition);
	}
	else {
	// @desc: 未获取到则说明是第一次注册
	// @desc: Bean工厂已经创建过Bean
		if (hasBeanCreationStarted()) {
			// Cannot modify startup-time collection elements anymore (for stable iteration)
			// @desc: 这里由于IOC已启动,所以所有的集合存储都为了不影响迭代,就new对象处理后再赋值给IOC了
			// @desc: 例如不是初始化时注册的Bean
			synchronized (this.beanDefinitionMap) {
				// @desc: 把BeanDefinition放入Map
				this.beanDefinitionMap.put(beanName, beanDefinition);
				List<String> updatedDefinitions = new ArrayList<String>(this.beanDefinitionNames.size() + 1);
				updatedDefinitions.addAll(this.beanDefinitionNames);
				updatedDefinitions.add(beanName);
				this.beanDefinitionNames = updatedDefinitions;
				if (this.manualSingletonNames.contains(beanName)) {
					Set<String> updatedSingletons = new LinkedHashSet<String>(this.manualSingletonNames);
					updatedSingletons.remove(beanName);
					this.manualSingletonNames = updatedSingletons;
				}
			}
		}
		// @desc: Bean工厂尚未创建时放入Map即可
		else {
			// Still in startup registration phase
			this.beanDefinitionMap.put(beanName, beanDefinition);
			this.beanDefinitionNames.add(beanName);
			this.manualSingletonNames.remove(beanName);
		}
		// @desc: 关闭冻结BeanDefinition
		this.frozenBeanDefinitionNames = null;
	}
	// @desc: IOC中存在或者属于单例Bean则注销、清空从此使用最新注册的BeanDefinition
	if (oldBeanDefinition != null || containsSingleton(beanName)) {
		resetBeanDefinition(beanName);
	}
}
```
#### IOC注册说明

1. 实质为放入IOC容器的beanDefinitionMap中
2. 存储形式:`private final Map<String, BeanDefinition> beanDefinitionMap = new ConcurrentHashMap<String, BeanDefinition>(256);`
3. 其中: key=`BeanName` value=`BeanDefinition`
4. 注册的BeanDefinition
    1. 全新的从未注册的
        1. IOC启动中
            1. 克隆注册再赋值回去
        2. 未启动
            1. 直接注册
    2. 已经存在IOC中
        1. 校验相关条件


