package com.dingdong.agent.tool.builtin;

import com.dingdong.core.annotation.Tool;
import com.dingdong.core.annotation.ToolParam;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * 计算器工具。支持基础四则运算、括号、幂运算等。
 * 群聊中遇到算数问题时使用。
 */
@Slf4j
@Component
public class CalculatorTool {

    @Tool(
        name = "calculator",
        description = "数学计算器。当用户要求计算数学表达式时使用。\n" +
                      "支持：加减乘除、括号、幂运算(^)、百分数。\n" +
                      "示例：\n" +
                      "- expression=\"1+2*3\" → 7\n" +
                      "- expression=\"(100-20)/4\" → 20\n" +
                      "- expression=\"2^10\" → 1024\n" +
                      "- expression=\"15%\" → 0.15"
    )
    public String calculate(
        @ToolParam(value = "expression", description = "数学表达式，如 1+2*3、(100-20)/4、2^10", required = true) String expression
    ) {
        if (expression == null || expression.isBlank()) {
            return "❌ 请输入要计算的表达式";
        }

        try {
            // 预处理表达式
            String processed = preprocessExpression(expression.trim());

            // 安全检查：只允许数字和基本运算符
            if (!isSafeExpression(processed)) {
                return "❌ 表达式包含不允许的字符，仅支持数字和 +-*/^%() 运算符";
            }

            // 使用 JavaScript 引擎计算
            ScriptEngineManager mgr = new ScriptEngineManager();
            ScriptEngine engine = mgr.getEngineByName("js");
            
            if (engine == null) {
                // 如果 JS 引擎不可用，使用简单解析
                return simpleCalculate(processed);
            }

            Object result = engine.eval(processed);
            return formatResult(expression, result);
        } catch (Exception e) {
            log.debug("Calculator error: {}", e.getMessage());
            return "❌ 计算出错：" + e.getMessage() + "\n请检查表达式格式是否正确";
        }
    }

    /**
     * 预处理表达式：将用户友好的写法转换为可执行的表达式
     */
    private String preprocessExpression(String expr) {
        String result = expr;
        // 替换中文运算符
        result = result.replace("×", "*").replace("÷", "/").replace("＋", "+").replace("－", "-");
        // 替换幂运算符号
        result = result.replace("^", "**");
        // 替换百分数
        result = result.replaceAll("(\\d+)%", "($1/100)");
        // 移除空格
        result = result.replaceAll("\\s+", "");
        return result;
    }

    /**
     * 安全检查：确保表达式只包含合法字符
     */
    private boolean isSafeExpression(String expr) {
        // 允许的字符：数字、小数点、运算符、括号
        return expr.matches("[0-9+\\-*/().%\\s**^]+");
    }

    /**
     * 简单的四则运算计算器（当 JS 引擎不可用时的后备方案）
     */
    private String simpleCalculate(String expr) {
        // 移除幂运算符号（简单计算器不支持）
        if (expr.contains("**")) {
            return "❌ 当前环境不支持幂运算，请尝试其他计算";
        }

        try {
            // 使用 Java 的 ScriptEngine 备选方案
            double result = evaluateSimple(expr);
            return formatResult(expr, result);
        } catch (Exception e) {
            return "❌ 计算失败：" + e.getMessage();
        }
    }

    /**
     * 简单的表达式求值（仅支持四则运算）
     */
    private double evaluateSimple(String expr) {
        // 这里使用一个简单实现，实际应该用更完善的解析器
        // 暂时用 JavaScript 引擎作为主要方案
        throw new UnsupportedOperationException("请使用 JavaScript 引擎进行计算");
    }

    /**
     * 格式化计算结果
     */
    private String formatResult(String originalExpr, Object result) {
        double num;
        if (result instanceof Number) {
            num = ((Number) result).doubleValue();
        } else {
            num = Double.parseDouble(result.toString());
        }

        // 如果是整数，去掉小数点
        if (num == Math.floor(num) && !Double.isInfinite(num)) {
            return "🧮 " + originalExpr + " = **" + (long) num + "**";
        }

        // 保留合理精度
        String formatted;
        if (Math.abs(num) < 0.0001 || Math.abs(num) > 1000000) {
            formatted = String.format("%.6g", num);
        } else {
            formatted = String.format("%.4f", num);
            // 去掉末尾多余的零
            formatted = formatted.replaceAll("0+$", "").replaceAll("\\.$", "");
        }

        return "🧮 " + originalExpr + " = **" + formatted + "**";
    }
}
