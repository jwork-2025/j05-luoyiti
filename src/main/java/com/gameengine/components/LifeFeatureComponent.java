package com.gameengine.components;

import com.gameengine.core.Component;
import com.gameengine.math.Vector2;

public class LifeFeatureComponent extends Component<LifeFeatureComponent> {
    /**
     * 生命属性组件，用于定义实体的生命属性信息
     * 例如其攻击力，生命值，攻击的生命周期等
     */
    public int blood;
    private float lifetime; // 生命周期计时器
    private float maxLifetime; // 最大生命周期（-1表示无限）
    private boolean hasLifetime; // 是否启用生命周期
    public boolean isunbeatable; // 是否是无敌状态

    public LifeFeatureComponent(int blood) {
        this.blood = blood;
        this.lifetime = 0;
        this.maxLifetime = -1; // 默认无限生命周期
        this.hasLifetime = false;
        this.isunbeatable = false; // 默认不是无敌的，即均可受到伤害
    }
    
    /**
     * 设置生命周期（用于技能等临时对象）
     * @param maxLifetime 最大生命周期（秒）
     */
    public void setLifetime(float maxLifetime) {
        this.maxLifetime = maxLifetime;
        this.hasLifetime = true;
        this.lifetime = 0;
    }
    
    /**
     * 重置生命周期计时器
     */
    public void resetLifetime() {
        this.lifetime = 0;
    }

    @Override
    public void initialize() {
        // TODO Auto-generated method stub
    }

    @Override
    public void update(float deltaTime) {
        // 如果启用了生命周期，则更新计时器
        if (hasLifetime && maxLifetime > 0) {
            lifetime += deltaTime;
            
            // 生命周期结束，将对象移到屏幕外
            if (lifetime >= maxLifetime) {
                TransformComponent transform = owner.getComponent(TransformComponent.class);
                PhysicsComponent physics = owner.getComponent(PhysicsComponent.class);
                
                if (transform != null) {
                    transform.setPosition(new Vector2(-1000, -1000));
                }
                if (physics != null) {
                    physics.setVelocity(new Vector2(0, 0));
                }
                
                // 重置生命周期以便下次使用
                lifetime = 0;
            }
        }
    }

    @Override
    public void render() {
        // TODO Auto-generated method stub
    }

    public int getBlood() {
        return this.blood;
    }
    
    public float getLifetime() {
        return this.lifetime;
    }
    
    public boolean isLifetimeExpired() {
        return hasLifetime && maxLifetime > 0 && lifetime >= maxLifetime;
    }

}