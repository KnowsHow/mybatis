/**
 *    Copyright ${license.git.copyrightYears} the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.executor.statement;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.apache.ibatis.executor.parameter.ParameterHandler;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.session.ResultHandler;

/**
 * @author Clinton Begin
 */
public interface StatementHandler {

  /** 准备过程：statement的初步处理 */
  Statement prepare(Connection connection, Integer transactionTimeout) throws SQLException;

  /** 参数调整 */
  void parameterize(Statement statement) throws SQLException;

  /** 批处理 */
  void batch(Statement statement) throws SQLException;

  /** 更新操作 */
  int update(Statement statement) throws SQLException;

  /** 查询操作 */
  <E> List<E> query(Statement statement, ResultHandler resultHandler) throws SQLException;

  /** 游标查询操作 */
  <E> Cursor<E> queryCursor(Statement statement) throws SQLException;

  /** statement所处理的SQL */
  BoundSql getBoundSql();

  /** 参数处理器 */
  ParameterHandler getParameterHandler();

}
