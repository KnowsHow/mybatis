## 总结

####  Mapper.xml解析出完整的增删改查(select|insert|update|delete)文本节点

1. 获取(select|insert|update|delete)文本节点
2. 引入bind指定的sql节点
3. 解析bind节点,将properties属性运用到bind指定的sql节点中
4. 构建主键节点
5. 移除主键节点
6. 构建完整的增删改查(select|insert|update|delete)文本节点,返回context
	* context的标签名称为(select|insert|update|delete)中的一种
	* context的标签体中只会含有xml自定义标签(#PCDATA | include | trim | where | set | foreach | choose | if | bind)

#### 解析context节点:langDriver.createSqlSource(configuration, context, parameterTypeClass);

1. 构建XMLScriptBuilder对象,XMLLanguageDriver默认的解析脚本的方法(从Mapper中解析出来都是XNode对象)
2. 调用parseScriptNode()解析context中的标签
    1. 调用parseDynamicTags(context)方法【含有${字符串即为动态】,构建增删改查节点标签体中内容对应的Java对象
        1. 遍历子节点
            1. 对于文本节点(CDATA节点和Text节点)
                * ${}或者有非include、selectKey标签则为动态,转为TextSqlNode对象
                * 非动态SQL将节点转为StaticTextSqlNode对象
            2. 对于元素节点(include | trim | where | set | foreach | choose | if | bind),含有即为动态
                * 据节点名称获取对应的标签处理器(从Map中取出,key为标签名称,value为标签处理器),
                * 存在标签处理器则调用其handleNode(XNode nodeToHandle, List<SqlNode> targetContents)处理,转换为对应的SqlNode对象
                * 不存在则抛出不识别节点名称的异常
            3. 将转换的SqlNode对象封装到一个List中,该List的每个元素都是xml节点对应的SqlNode对象,返回该List<SqlNode>
    2. 将返回的List<SqlNode>作为参数构建新的MixedSqlNode对象即自身对应的Java对象
    3. List中的SqlNode对象只要不是只含有StaticTextSqlNode对象就是动态的,创建DynamicSqlSource对象,否则创建RawSqlSource对象
    4. 返回SqlSource实例,完成解析


#### 构建BoundSql

* DynamicSqlSource(动态Sql源)
    * 获取BoundSql方法
    1. 构建DynamicContext对象,将参数"Map"化,即将参数对象放到ContextMap(HashMap的子类)中,同时把数据库ID也放到ContextMap中,key为final String类型
    2. (select|insert|update|delete)根节点对象调用节点对象apply(DynamicContext)方法
        1. MixedSqlNode
          * 组合节点对象,内部维护一个List<SqlNode>,apply:遍历内部SqlNode,调用自身的apply方法
        2. TextSqlNode
           * 纯文本节点对象
           * apply:分词器解析出${OGNL}中的OGNL表达式,返回解析后的String  sql,append到DynamicContext的StringBuilder中
        3. ForEachSqlNode
            * 先把open属性追加到DynamicContext的StringBuilder中
            * OGNL解析出collection
                * Iterable实现,直接返回
                * Array实现,返回List
                * Map实现,返回Map的entrySet
                * 其他类型都不支持
            * 循环遍历collection对象的Iterable,
                * 解析出item属性,index属性,使用标准命名规范命名放到binds中
                * 分词解析器解析出#{OGNL}中的表达式,返回解析后的String  sql,append到DynamicContext的StringBuilder中
          * 把close属性追加到DynamicContext的StringBuilder中
            * 返回解析后的String  sql,append到DynamicContext的StringBuilder中
        4. IfSqlNode
          * OGNL解析出test属性布尔结果,==true则继续解析
        5. VarDeclSqlNode
          * OGNL解析出变量的value值,放到bindings内
        6. TrimSqlNode
            1. WhereSqlNode
            2. SetSqlNode
                * 将子节点对象apply各子的apply方法解析出来,返回一个StringBuilder对象
                * 将所有的要覆盖的前后缀,异常,如果配置了前后缀再把前后缀加上(等价于替换)
                * 返回解析后的String  sql,append到DynamicContext的StringBuilder中
        7. ChooseSqlNode
            1. when节点对象,使用IfSqlNode的apply方法解析
            2. default存在,则调用自身ChooseSqlNode的解析方法循环解析
    3. 最终返回的是一个处理了${}和#{}后的StringBuilder对象,构建SqlSourceBuilder对象,调用其parse方法
        * 分词器解析#{OGNL结果}中的表达式,将#{OGNL结果}替换为?,每个?对应一个ParameterMapping对象,返回解析后的String  sql,append到DynamicContext的StringBuilder中
        * 至此,所有动态SQL转为了静态sql,构建StaticSqlSource对象

* StaticSqlSource(静态Sql源)
    * 直接new一个BoundSql对象



####    节点解析器介绍:将XML节点DOM对象转成对应的Java对象即SqlNode实例

1. "bind"标签处理器:获取name,expression,关联属性构建VarDeclSqlNode对象
2. "trim"标签处理器:
    1. 调用parseDynamicTags(context)方法,解析节点,返回"trim"标签体中内容对应的Java对象
    2. 构建MixedSqlNode对象,构建"trim"自身对应Java对象
    3. 获取属性:prefix,prefixOverrides,suffix,suffixOverrides
    4. 关联属性构建TrimSqlNode对象,其中prefixOverrides和suffixOverrides会转换为List(多个字符串间用竖线"|"分割,以此为分隔符解析出List,字符串都转为大写)
3. "where"标签处理器:继承了"trim"标签处理器
    1. 调用parseDynamicTags(context)方法,解析节点,返回"where"标签体中内容对应的Java对象
    2. 构建MixedSqlNode对象,构建"where"自身对应Java对象
    3. 默认:prefix="WHERE",prefixOverrides转换的List=Arrays.asList("AND ","OR ","AND\n", "OR\n", "AND\r", "OR\r", "AND\t", "OR\t"),suffix=null,suffixOverrides转换的List=null
    4. 关联属性构建WhereSqlNode对象
4. "set"标签处理器:继承了"trim"标签处理器
    1. 调用parseDynamicTags(context)方法,解析节点,返回"set"标签体中内容对应的Java对象
    2. 构建MixedSqlNode对象,构建"set"自身对应Java对象
    3. 默认:prefix="SET",prefixOverrides转换的List=Arrays.asList(","),suffix=null,suffixOverrides转换的List=null
    4. 关联属性构建SetSqlNode对象
5. "forEach"标签处理器
    1. 调用parseDynamicTags(context)方法,解析节点,返回"forEach"标签体中内容对应的Java对象
    2. 构建MixedSqlNode对象,构建"forEach"自身对应Java对象
    3. 获取属性:collection,item,index,open,close,separator
    4. 关联属性构建ForEachSqlNode对象
6. "if"标签处理器
    1. 调用parseDynamicTags(context)方法,解析节点,返回"if"标签体中内容对应的Java对象
    2. 构建MixedSqlNode对象,构建"if"自身对应Java对象
    3. 获取属性:test
    4. 关联属性构建IfSqlNode对象
7. "otherwise" 标签处理器
    1. 调用parseDynamicTags(context)方法,解析节点,返回"otherwise"标签体中内容对应的Java对象
    2. 构建MixedSqlNode对象,无属性此时已解析出"otherwise"的Java对象
8. "choose"标签处理器
    1. 解析"when otherwise"标签,返回"choose"标签体中内容对应的Java对象
        1. 如果子标签为"when",调用"if"标签处理器(这里的when实质是if)
        2. 否则调用"otherwise"标签处理器
    2. 判断"otherwise"标签处理器处理的结果List
        1. List.size()==1,则返回get(0)
        2. List.size()>1,抛出异常
    3. 构建ChooseSqlNode对象(when,otherwise)




