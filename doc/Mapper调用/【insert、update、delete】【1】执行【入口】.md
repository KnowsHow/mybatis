## insert、update、delete语句的执行流程

#### 调用入口:MapperMethod#execute

```java
  case INSERT: {
  Object param = method.convertArgsToSqlCommandParam(args);
    result = rowCountResult(sqlSession.insert(command.getName(), param));
    break;
  }
  case UPDATE: {
    Object param = method.convertArgsToSqlCommandParam(args);
    result = rowCountResult(sqlSession.update(command.getName(), param));
    break;
  }
  case DELETE: {
    Object param = method.convertArgsToSqlCommandParam(args);
    result = rowCountResult(sqlSession.delete(command.getName(), param));
    break;
  }
```
#### 结果封装MapperMethod#rowCountResult
```java
  private Object rowCountResult(int rowCount) {
    final Object result;
    if (method.returnsVoid()) {
      // @desc: 转为void
      result = null;
    } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
       // @desc: 基础类型int
      result = rowCount;
    } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
      // @desc: 转为基础类型long
      result = (long)rowCount;
    } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
      // @desc: 转为基础类型boolean
      result = rowCount > 0;
    } else {
      throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
    }
    return result;
  }
```




