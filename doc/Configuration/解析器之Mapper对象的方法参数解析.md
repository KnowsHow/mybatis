## ParamNameResolver

```java
public class ParamNameResolver {

  /**
   * @desc: 通用参数名称前缀
   */
  private static final String GENERIC_NAME_PREFIX = "param";

  /**
   * @desc: 参数Map,key=索引,value=参数名称
   */
  private final SortedMap<Integer, String> names;

  private boolean hasParamAnnotation;

 /**
  * @desc: 构造器(Configuration config, Method method)
  */
  public ParamNameResolver(Configuration config, Method method) {
    // @desc: 获取参数类型列表
    final Class<?>[] paramTypes = method.getParameterTypes();
    // @desc: 获取参数注解列表
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    final SortedMap<Integer, String> map = new TreeMap<Integer, String>();
    int paramCount = paramAnnotations.length;
    // @desc: 遍历参数注解列表
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // @desc: 不处理RowBounds、ResultHandler参数
      if (isSpecialParameter(paramTypes[paramIndex])) {
        continue;
      }
      String name = null;
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        // @desc: 处理该参数上的注解列表:只解析@Param
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          // @desc: 解析出name
          name = ((Param) annotation).value();
          break;
        }
      }
      // @desc: 注解未编码value时
      if (name == null) {
        // @desc: 使用参数真是名称
        // @desc: 该判断使用了configuration的配置项:useActualParamName=true
        if (config.isUseActualParamName()) {
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) {
          // @desc: 尝试使用参数下标
          // @desc: 使用Map的size来作为将来的下标
          name = String.valueOf(map.size());
        }
      }
      // @desc: 放入参数Map中同时维护了参数下标=map.size()
      map.put(paramIndex, name);
    }
    // @desc: 参数解析后冻结Map
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    // @desc: 校验JDK版本中参数反射类是否存在
    if (Jdk.parameterExists) {
      // @desc: (需要JDK8)
      return ParamNameUtil.getParamNames(method).get(paramIndex);
    }
    return null;
  }

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

 /**
  * @desc: 获取解析参数名称列表
  */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

 /**
  * @desc: 获取解析后的参数对象
  */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    // @desc: 未解析出参数则返回null
    if (args == null || paramCount == 0) {
      return null;
    // @desc: 没有使用@Param注解且只有一个参数则返回Map中第一个
    } else if (!hasParamAnnotation && paramCount == 1) {
      return args[names.firstKey()];
    // @desc: 使用了@Param或者多个参数时
    } else {
      final Map<String, Object> param = new ParamMap<Object>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        param.put(entry.getValue(), args[entry.getKey()]);
        // @desc: 通用参数名称结构 param+(index+1)
        final String genericParamName = GENERIC_NAME_PREFIX + String.valueOf(i + 1);
        // @desc: 保证已解析的参数名称不会被覆盖
        if (!names.containsValue(genericParamName)) {
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }
}
```

#### 说明
1. 解析器构造时
    * 参数为:Configuration、Method
    * 解析paramAnnotations=[参数个数][注解列表]
        * 不处理RowBounds、ResultHandler类型参数
        * 使用@Param注解的参数
            * value!=null 则使用其value作为参数名称
            * value=null
                1. 配置项支持使用参数真实名称则使用(需要JDK8)
                2. 不支持JDK8时才使用下标作为名称
        * 其他参数
            1. 配置项支持使用参数真实名称则使用(需要JDK8)
            2. 不支持JDK8时才使用下标作为名称
    * 解析后的参数存储在SortedMap中
        * key=参数使用的索引
        * value=解析后的参数名称
2. 获取解析参数名称列表
    * 返回SortedMap的value转String[]

2. 获取解析后的参数对象
    * 无参数时返回null
    * 无@Param且单参数时返回 SortedMap.firstKey()对应的参数对象
    * 使用@Param或者多参数时
        * SortedMap转储ParamMap
        * 复制SortedMap将name=param+(index+1)作为Key,value不变存储ParamMap
    * 说明:
        1. 无参数或者无@Param且单参数则直接返回解析后的参数名称
        2. 多参数或者使用@Param则转储为HashMap
          * key= 解析参数名称,value=对应参数对象
          * key= 通用参数名称[param+下标+1],value=对应参数对象
            * 当通用参数名称=解析参数名称列表中任意一个时使用解析参数名称而不进行复制
            * 否则将复制一份通用参数名称来存储
          * 通用参数名称格式: param1,param2,param3...
          * 解析参数名称格式: 参数本身名称,@Param的value,整型数字0,1,2...

#### 使用位置1: MethodSignature

```java
public Object convertArgsToSqlCommandParam(Object[] args) {
  // @desc: 获取解析后的参数对象
  return paramNameResolver.getNamedParams(args);
}
```
