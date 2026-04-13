package LDS.Person.tasks.MsgLisCmdLogic;

/**
 * 命令逻辑处理接口
 * 所有命令关键词处理逻辑都应该实现此接口
 * 
 * 提供可拓展的架构，支持：
 * - 多种关键词处理
 * - 统一的执行结果格式
 * - 便于添加新的命令处理器
 */
public interface IMsgLisCmdLogic {

  /**
   * 执行命令逻辑
   * 
   * @param keyword 触发的关键词
   * @return 执行结果
   */
  Object execute(String keyword);

  /**
   * 获取处理器名称
   */
  String getName();

  /**
   * 获取处理器描述
   */
  String getDescription();
}
