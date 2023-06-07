package com.mattymatty.carpet_reducedpackets.utils.exp4j;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import net.objecthunter.exp4j.function.Function;
import org.jetbrains.annotations.NotNull;

public class Extras {
    /**
     * Returns the smallest (closest to negative infinity) of two numbers.
     *
     * @see Math#min(double, double)
     * @since 0.8-riddler
     */
    public static final Function MIN = new Min();

    /**
     * Returns the largest (closest to positive infinity) of two numbers.
     *
     * @see Math#max(double, double)
     * @since 0.8-riddler
     */
    public static final Function MAX = new Max();
    public static Expression SUPPRESS = new ExpressionBuilder("-1").variables("MSPT", "TPS", "PING").build();
    public static Expression PASSTHOURGH = new ExpressionBuilder("-2").variables("MSPT", "TPS", "PING").build();

    public static Expression getExpression(@NotNull String expression) {
        switch (expression.trim()) {
            case "DEFAULT" -> {
                return null;
            }
            case "PASSTHOURGH", "PASS" -> {
                return PASSTHOURGH;
            }
            case "SUPPRESS" -> {
                return SUPPRESS;
            }
            default -> {
                Expression tmp = new ExpressionBuilder(expression)
                        .functions(MAX, MIN)
                        .variables("MSPT", "TPS", "PING")
                        .build();
                if (tmp.validate(false).isValid()) {
                    tmp.setVariable("MSPT", 50L);
                    tmp.setVariable("TPS", 20.0);
                    tmp.setVariable("PING", 10L);
                    tmp.evaluate();
                } else {
                    throw new IllegalArgumentException("this is not a valid Expression");
                }
                return tmp;
            }
        }
    }

    private static final class Min extends Function {
        private static final long serialVersionUID = -8343244242397439087L;
        Min() { super("min", 2); }
        @Override
        public double apply(double... args) {
            final double v1 = args[0];
            final double v2 = args[1];
            return Math.min(v1, v2);
        }
    }

    private static final class Max extends Function {
        private static final long serialVersionUID = 426041154853511222L;
        Max() { super("max", 2); }
        @Override
        public double apply(double... args) {
            final double v1 = args[0];
            final double v2 = args[1];
            return Math.max(v1, v2);
        }
    }

}
