package com.mattymatty.carpet_reducedpackets.config;

import carpet.settings.ParsedRule;
import carpet.settings.Rule;
import carpet.settings.Validator;
import carpet.utils.Messenger;
import com.mattymatty.carpet_reducedpackets.utils.exp4j.Extras;
import net.minecraft.server.command.ServerCommandSource;
import net.objecthunter.exp4j.Expression;

import java.lang.reflect.Field;

import static carpet.settings.RuleCategory.*;

public class CarpetConfig {

    public static final String REDUCED_PACKETS = "REDUCED_PACKETS";

    @Rule(
            desc = "If Packet Reduction during High TPS operations is enabled",
            category =  {CREATIVE,REDUCED_PACKETS}
    )
    public static boolean enabled = true;

    @Rule(
            desc = "minimum TPS required to start reducing packets",
            category = {CREATIVE,REDUCED_PACKETS}
    )
    public static double MinTPS = 22.0;

    @Rule(
            desc = "minimum Ping required to start reducing packets",
            category =  {CREATIVE,REDUCED_PACKETS}
    )
    public static long MinPing = 100;

    private static class ExpressionValidator extends Validator<String> {
        @Override public String validate(ServerCommandSource source, ParsedRule<String> currentRule, String newValue, String string) {
            if (currentRule.get().equals(newValue) || source == null)
            {
                return newValue;
            }
            try{
                Expression expression = Extras.getExpression(newValue);
                Field field = CarpetConfig.class.getDeclaredField(currentRule.name.replace("Delay","Expression"));
                field.set(null,expression);
            } catch (Exception ex){
                Messenger.m(source, "r " + ex.getMessage());
                return null;
            }

            return newValue;
        }
    }
    private static class DefaultExpressionValidator extends ExpressionValidator{
        @Override
        public String validate(ServerCommandSource source, ParsedRule<String> currentRule, String newValue, String string) {
            if (newValue.trim().equals("DEFAULT")) {
                Messenger.m(source, "r Default expression cannot be \"DEFAULT\"");
                return null;
            }
            return super.validate(source, currentRule, newValue, string);
        }
    }
    @Rule(
            desc = "expression used to calculate delay between packet batches",
            category =  {CREATIVE,REDUCED_PACKETS},
            options = {"SUPPRESS", "PASS", "PASSTHOUGH", "50", "max(25,min(100, PING / 4 ))"},
            strict = false,
            validate = DefaultExpressionValidator.class
    )
    public static String defaultDelay = "max(25,min(100, PING / 4 ))";

    public static Expression defaultExpression = Extras.getExpression(defaultDelay);

    @Rule(
            desc = "expression used to calculate delay between packet batches",
            category =  {CREATIVE,REDUCED_PACKETS},
            options = {"DEFAULT", "SUPPRESS", "PASS", "PASSTHOUGH", "50", "max(25,min(100, PING / 4 ))"},
            strict = false,
            validate = ExpressionValidator.class
    )
    public static String entitiesDelay = "DEFAULT";

    public static Expression entitiesExpression = Extras.getExpression(entitiesDelay);
    @Rule(
            desc = "expression used to calculate delay between packet batches",
            category =  {CREATIVE,REDUCED_PACKETS},
            options = {"DEFAULT", "SUPPRESS", "PASS", "PASSTHOUGH", "50", "max(25,min(100, PING / 4 ))"},
            strict = false,
            validate = ExpressionValidator.class
    )
    public static String blocksDelay = "DEFAULT";

    public static Expression blocksExpression = Extras.getExpression(blocksDelay);
    @Rule(
            desc = "expression used to calculate delay between packet batches",
            category =  {CREATIVE,REDUCED_PACKETS},
            options = {"DEFAULT", "SUPPRESS", "PASS", "PASSTHOUGH", "50", "max(25,min(100, PING / 4 ))"},
            strict = false,
            validate = ExpressionValidator.class
    )
    public static String soundsDelay = "DEFAULT";

    public static Expression soundsExpression = Extras.getExpression(soundsDelay);
    @Rule(
            desc = "expression used to calculate delay between packet batches",
            category =  {CREATIVE,REDUCED_PACKETS},
            options = {"DEFAULT", "SUPPRESS", "PASS", "PASSTHOUGH", "50", "max(25,min(100, PING / 4 ))"},
            strict = false,
            validate = ExpressionValidator.class
    )
    public static String particlesDelay = "SUPPRESS";

    public static Expression particlesExpression = Extras.getExpression(particlesDelay);


}
