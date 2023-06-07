package com.mattymatty.carpet_reducedpackets.config;

import com.mattymatty.carpet_reducedpackets.utils.exp4j.Extras;
import net.objecthunter.exp4j.Expression;
import org.jetbrains.annotations.NotNull;

public class PlayerConfig implements Cloneable {

    private double minimum_TPS;
    private long minimum_ping;
    private String default_delay;

    private Expression default_delay_expression;
    private String entity_delay;
    private Expression entity_delay_expression;
    private String block_delay;
    private Expression block_delay_expression;
    private String sound_delay;
    private Expression sound_delay_expression;
    private String particle_delay;
    private Expression particle_delay_expression;

    public PlayerConfig(double minimum_TPS, long minimum_ping, @NotNull String default_delay, String entity_delay, String block_delay, String sound_delay, String particle_delay) {
        this.minimum_TPS = minimum_TPS;
        this.minimum_ping = minimum_ping;
        this.setDefault_delay(default_delay);
        this.setEntity_delay(entity_delay);
        this.setBlock_delay(block_delay);
        this.setSound_delay(sound_delay);
        this.setParticle_delay(particle_delay);
    }

    public double getMinimum_TPS() {
        return minimum_TPS;
    }

    public void setMinimum_TPS(double minimum_TPS) {
        this.minimum_TPS = minimum_TPS;
    }

    public long getMinimum_ping() {
        return minimum_ping;
    }

    public void setMinimum_ping(long minimum_ping) {
        this.minimum_ping = minimum_ping;
    }

    @NotNull
    public String getDefault_delay() {
        return default_delay;
    }

    public void setDefault_delay(@NotNull String default_delay) {
        Expression tmp = Extras.getExpression(default_delay);
        if (tmp == null)
            throw new IllegalArgumentException("The default value has to be defined!");
        this.default_delay = default_delay;
        this.default_delay_expression = tmp;
    }

    public String getEntity_delay() {
        return entity_delay;
    }

    public void setEntity_delay(String entity_delay) {
        if (entity_delay != null) {
            this.entity_delay_expression = Extras.getExpression(entity_delay);
        } else {
            this.entity_delay_expression = null;
        }
        this.entity_delay = entity_delay;
    }

    public String getBlock_delay() {
        return block_delay;
    }

    public void setBlock_delay(String block_delay) {
        if (block_delay != null)
            this.block_delay_expression = Extras.getExpression(block_delay);
        else
            this.block_delay_expression = null;
        this.block_delay = block_delay;
    }

    public String getSound_delay() {
        return sound_delay;
    }

    public void setSound_delay(String sound_delay) {
        if (sound_delay != null)
            this.sound_delay_expression = Extras.getExpression(sound_delay);
        else
            this.sound_delay_expression = null;
        this.sound_delay = sound_delay;
    }

    public String getParticle_delay() {
        return particle_delay;
    }

    public void setParticle_delay(String particle_delay) {
        if (particle_delay != null)
            this.particle_delay_expression = Extras.getExpression(particle_delay);
        else
            this.particle_delay_expression = null;
        this.particle_delay = particle_delay;
    }

    @NotNull
    public Expression getDefault_delay_expression() {
        return default_delay_expression;
    }

    @NotNull
    public Expression getEntity_delay_expression() {
        return entity_delay_expression != null ? entity_delay_expression : default_delay_expression;
    }

    @NotNull
    public Expression getBlock_delay_expression() {
        return block_delay_expression != null ? block_delay_expression : default_delay_expression;
    }

    @NotNull
    public Expression getSound_delay_expression() {
        return sound_delay_expression != null ? sound_delay_expression : default_delay_expression;
    }

    @NotNull
    public Expression getParticle_delay_expression() {
        return particle_delay_expression != null ? particle_delay_expression : default_delay_expression;
    }

    @SuppressWarnings("all")
    @Override
    public PlayerConfig clone() {
        return new PlayerConfig(this.minimum_TPS, this.minimum_ping, this.default_delay, this.entity_delay, this.block_delay, this.sound_delay, this.particle_delay);
    }
}
