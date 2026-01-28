package com.nabukey.ui.components.expression

/**
 * 表情状态枚举
 * 定义语音助手的各种表情状态
 */
sealed class ExpressionState {
    /**
     * 待机状态 - 随机眨眼 + 循环表情
     * @param cycleExpressions 是否启用表情循环
     */
    data class Idle(val cycleExpressions: Boolean = true) : ExpressionState()
    
    /**
     * 监听状态 - 正在监听用户说话
     */
    data object Listening : ExpressionState()
    
    /**
     * 思考状态 - 正在处理用户请求
     */
    data object Thinking : ExpressionState()
    
    /**
     * 说话状态 - 正在播放语音回复
     */
    data object Speaking : ExpressionState()
    
    /**
     * 错误状态 - 发生错误
     */
    data object Error : ExpressionState()
    
    /**
     * 休眠状态 - 闭眼/呼吸
     */
    data object Sleeping : ExpressionState()
    
    // ===== 情绪表情 =====
    
    /**
     * 愤怒表情
     */
    data object Angry : ExpressionState()
    
    /**
     * 兴奋表情
     */
    data object Excited : ExpressionState()
    
    /**
     * 恐惧表情
     */
    data object Fear : ExpressionState()
    
    /**
     * 悲伤表情
     */
    data object Sad : ExpressionState()
    
    /**
     * 轻蔑表情
     */
    data object Disdain : ExpressionState()
}
