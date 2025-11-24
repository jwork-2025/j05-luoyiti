package com.gameengine.app;

import com.gameengine.components.*;
import com.gameengine.core.GameObject;
import com.gameengine.core.GameLogic;
import com.gameengine.core.ParticleSystem;
import com.gameengine.graphics.IRenderer;
import com.gameengine.graphics.Renderer;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.io.File;
import java.io.FileWriter;
import java.util.*;

import com.gameengine.core.GameEngine;

/**
 * 葫芦娃游戏场景类
 * 管理游戏中的所有对象和游戏逻辑
 */
public class GameScene extends Scene {
    private IRenderer renderer;
    private Random random;
    private float time;
    private GameLogic gameLogic;
    private int level;
    private GameEngine engine;

    // 录制系统
    private boolean isRecording;
    @SuppressWarnings("unused")
    private FileWriter recordingWriter;
    private float recordingTimer = 0f;
    private static final float RECORDING_INTERVAL = 0.02f; // 每0.02秒记录一次
    private float keyTimer = 0f;

    // 粒子效果系统
    private ParticleSystem playerParticles;
    private List<ParticleSystem> collisionParticles;
    private Map<GameObject, ParticleSystem> EnemyParticles;

    // 时间系统
    private boolean waitingReturn;
    private float waitInputTimer;
    private float freezeTimer;
    private final float inputCooldown = 0.25f;
    private final float freezeDelay = 0.20f;

    public GameScene(String name, GameEngine engine) {
        super(name);
        this.random = new Random();
        this.time = 0;
        this.engine = engine;
        this.isRecording = false;
        this.recordingWriter = null;
    }

    public void setRecording(FileWriter fw) {
        this.isRecording = true;
        this.recordingWriter = fw;
    }

    @Override
    public void initialize() {
        super.initialize();
        this.gameLogic = new GameLogic(this, engine);
        this.level = 1;
        this.renderer = engine.getRenderer();

        // 创建初始游戏对象
        createHulu();
        createTrees();
        // 初始游戏关卡
        level1();

        // 初始化粒子效果
        collisionParticles = new ArrayList<>();
        EnemyParticles = new HashMap<>();

        playerParticles = new ParticleSystem(renderer, new Vector2(renderer.getWidth() / 2.0f, renderer.getHeight() / 2.0f));
        playerParticles.setActive(true);

        // 初始化时间系统
        this.waitingReturn = false;
        this.waitInputTimer = 0f;
        this.freezeTimer = 0f;

    }

    public float getTime() {
        return this.time;
    }

    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        time += deltaTime;

        // 检查ESC键退出
        if (engine.getInputManager().isKeyJustPressed(256)) { // GLFW_KEY_ESCAPE
            engine.stop();
            return;
        }

        // 录制按钮
        if (engine.getInputManager().isKeyJustPressed(82)) { // GLFW_KEY_ENTER
            if (!isRecording) {
                System.out.println("开始录制游戏...");
                try {
                    this.recordingWriter = new FileWriter("recordings/recording_" + System.currentTimeMillis() + ".txt", true);
                    this.isRecording = true;
                } catch (Exception e) {
                    System.err.println("无法创建录制文件: " + e.getMessage());
                    this.isRecording = false;
                    this.recordingWriter = null;
                }
            } else {
                System.out.println("结束录制游戏");
                this.isRecording = false;
                this.recordingWriter = null;
                // 手动关闭 FileWriter
                if (this.recordingWriter != null) {
                    try {
                        this.recordingWriter.flush();
                        this.recordingWriter.close();
                    } catch (Exception e) {
                        System.err.println("关闭录制文件时出错: " + e.getMessage());
                    }
                    this.recordingWriter = null;
                }
            }
        }

        // 游戏使用到的逻辑规则
        gameLogic.handlePlayerInput();
        gameLogic.updatePhysics();
        gameLogic.updateEnemyMovement(deltaTime);
        gameLogic.updateAttack(deltaTime);
        gameLogic.updateEnemyAttack(deltaTime);
        gameLogic.checkEntityAlive();

        // 记录游戏过程（每0.1秒记录一次）
        if (isRecording && recordingWriter != null) {
            recordingTimer += deltaTime;
            keyTimer += deltaTime;

            if (recordingTimer >= RECORDING_INTERVAL) {
                gameLogic.updateRecords(keyTimer, recordingWriter);
                recordingTimer = 0f;
            }
        }

        boolean wasGameOver = gameLogic.isGameOver();
        gameLogic.checkAiCollisions(deltaTime);
        
        if (gameLogic.isGameOver() && !wasGameOver) {
            GameObject player = gameLogic.getPlayer();
            if (player != null) {
                TransformComponent transform = player.getComponent(TransformComponent.class);
                if (transform != null) {
                    ParticleSystem.Config cfg = new ParticleSystem.Config();
                    cfg.initialCount = 0;
                    cfg.spawnRate = 9999f;
                    cfg.opacityMultiplier = 1.0f;
                    cfg.minRenderSize = 3.0f;
                    cfg.burstSpeedMin = 250f;
                    cfg.burstSpeedMax = 520f;
                    cfg.burstLifeMin = 0.5f;
                    cfg.burstLifeMax = 1.2f;
                    cfg.burstSizeMin = 18f;
                    cfg.burstSizeMax = 42f;
                    cfg.burstR = 1.0f;
                    cfg.burstGMin = 0.0f;
                    cfg.burstGMax = 0.05f;
                    cfg.burstB = 0.0f;
                    ParticleSystem explosion = new ParticleSystem(renderer, transform.getPosition(), cfg);
                    explosion.burst(180);
                    collisionParticles.add(explosion);
                    waitingReturn = true;
                    waitInputTimer = 0f;
                    freezeTimer = 0f;
                }
            }

            if (waitingReturn) {
                waitInputTimer += deltaTime;
                freezeTimer += deltaTime;
            }
        }

        // 游戏所处的关卡
        if (gameLogic.checkEnemiesDied()) {
            this.level++;

            if (this.level == 2) {
                level2();
            } else {
                level3();
            }
        }

        // 游戏粒子效果
        updateParticles(deltaTime);

        if (waitingReturn) {
            waitInputTimer += deltaTime;
            freezeTimer += deltaTime;
        }
    }

    private void updateParticles(float deltaTime) {
        boolean freeze = waitingReturn && freezeTimer >= freezeDelay;

        if (playerParticles != null && !freeze) {
            GameObject player = gameLogic.getPlayer();
            if (player != null) {
                TransformComponent transform = player.getComponent(TransformComponent.class);
                if (transform != null) {
                    Vector2 playerPos = transform.getPosition();
                    playerParticles.setPosition(playerPos);
                }
            }
            playerParticles.update(deltaTime);
        }

        List<GameObject> Enemies = gameLogic.getEnemies();
        if (!freeze) {
            for (GameObject Enemy : Enemies) {
                if (Enemy != null && Enemy.isActive()) {
                    ParticleSystem particles = EnemyParticles.get(Enemy);
                    if (particles == null) {
                        TransformComponent transform = Enemy.getComponent(TransformComponent.class);
                        if (transform != null) {
                            particles = new ParticleSystem((IRenderer) renderer, transform.getPosition(), ParticleSystem.Config.light());
                            particles.setActive(true);
                            EnemyParticles.put(Enemy, particles);
                        }
                    }
                    if (particles != null) {
                        TransformComponent transform = Enemy.getComponent(TransformComponent.class);
                        if (transform != null) {
                            particles.setPosition(transform.getPosition());
                        }
                        particles.update(deltaTime);
                    }
                }
            }
        }

        List<GameObject> toRemove = new ArrayList<>();
        for (Map.Entry<GameObject, ParticleSystem> entry : EnemyParticles.entrySet()) {
            if (!entry.getKey().isActive() || !Enemies.contains(entry.getKey())) {
                toRemove.add(entry.getKey());
            }
        }
        for (GameObject removed : toRemove) {
            EnemyParticles.remove(removed);
        }

        for (int i = collisionParticles.size() - 1; i >= 0; i--) {
            ParticleSystem ps = collisionParticles.get(i);
            if (ps != null) {
                if (!freeze) {
                    ps.update(deltaTime);
                }
            }
        }
    }

    @Override
    public void render() {
        // 绘制背景（地图扩大到1600x1200）
        renderer.drawRect(0, 0, 1600, 1200, 0.1f, 0.1f, 0.2f, 1.0f);
        
        // 渲染所有对象
        super.render();

        renderParticles();

        if (gameLogic.isGameOver()) {
            float cx = renderer.getWidth() / 2.0f;
            float cy = renderer.getHeight() / 2.0f;
            renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.0f, 0.0f, 0.0f, 0.35f);
            renderer.drawRect(cx - 200, cy - 60, 400, 120, 0.0f, 0.0f, 0.0f, 0.7f);
            renderer.drawText("GAME OVER", cx - 100, cy - 10, 1.0f, 1.0f, 1.0f, 1.0f, cy + 40);
            renderer.drawText("PRESS ANY KEY TO RETURN", cx - 180, cy + 30, 0.8f, 0.8f, 0.8f, 1.0f, cy + 40);
        }
        
        // 渲染level数在场景正上方
        renderLevel();
        
        // 渲染玩家血条在屏幕左上角
        renderPlayerHealthBar();
        
        // 渲染技能冷却条在屏幕右上角
        renderSkillCooldownBar();
    }
    
    private void renderParticles() {
        if (playerParticles != null) {
            int count = playerParticles.getParticleCount();
            if (count > 0) {
                playerParticles.render();
            }
        }

        for (ParticleSystem ps : EnemyParticles.values()) {
            if (ps != null && ps.getParticleCount() > 0) {
                ps.render();
            }
        }

        for (ParticleSystem ps : collisionParticles) {
            if (ps != null && ps.getParticleCount() > 0) {
                ps.render();
            }
        }
    }

    /**
     * 在屏幕左上角渲染玩家血条
     */
    private void renderPlayerHealthBar() {
        // 查找玩家对象
        GameObject player = null;
        for (GameObject obj : getGameObjects()) {
            if ("Player".equals(obj.getidentity())) {
                player = obj;
                break;
            }
        }
        
        if (player != null) {
            LifeFeatureComponent lifeFeature = player.getComponent(LifeFeatureComponent.class);
            if (lifeFeature != null) {
                int currentHealth = lifeFeature.getBlood();
                int maxHealth = 100;
                
                // 绘制半透明黑色背景框
                renderer.drawRect(10, 10, 160, 40, 0.0f, 0.0f, 0.0f, 0.7f);
                
                // 绘制"玩家血量"标签
                renderer.drawText("玩家血量", 20, 30, 14, 1.0f, 1.0f, 1.0f, 1.0f);
                
                // 绘制血条
                renderer.drawHealthBar(20, 35, 120, 10, currentHealth, maxHealth);
                
                // 绘制血量数值
                String healthText = currentHealth + " / " + maxHealth;
                renderer.drawText(healthText, 145, 45, 12, 1.0f, 1.0f, 1.0f, 1.0f);
            }
        }
    }
    
    /**
     * 在屏幕右上角渲染技能冷却条
     */
    private void renderSkillCooldownBar() {
        if (gameLogic != null) {
            float cooldownPercentage = gameLogic.getSkillCooldownPercentage();
            
            // 冷却条的位置和尺寸（根据新地图尺寸1600x1200调整位置）
            int barX = 1410;  // 右上角 x 坐标
            int barY = 10;   // 右上角 y 坐标
            int barWidth = 180;
            int barHeight = 40;
            
            // 绘制半透明黑色背景框
            renderer.drawRect(barX, barY, barWidth, barHeight, 0.0f, 0.0f, 0.0f, 0.7f);
            
            // 绘制"技能冷却"标签
            renderer.drawText("技能冷却 (J)", barX + 10, barY + 20, 14, 1.0f, 1.0f, 1.0f, 1.0f);
            
            // 绘制冷却条
            int cooldownBarWidth = 140;
            int cooldownBarHeight = 10;
            int cooldownBarX = barX + 20;
            int cooldownBarY = barY + 25;
            
            // 绘制冷却条背景（灰色）
            renderer.drawRect(cooldownBarX, cooldownBarY, cooldownBarWidth, cooldownBarHeight, 
                            0.3f, 0.3f, 0.3f, 1.0f);
            
            // 根据冷却状态绘制冷却进度条
            int filledWidth = (int)(cooldownBarWidth * cooldownPercentage);
            
            if (cooldownPercentage >= 1.0f) {
                // 冷却完成，绘制绿色条
                renderer.drawRect(cooldownBarX, cooldownBarY, filledWidth, cooldownBarHeight, 
                                0.0f, 1.0f, 0.0f, 1.0f);
            } else {
                // 冷却中，绘制黄色条
                renderer.drawRect(cooldownBarX, cooldownBarY, filledWidth, cooldownBarHeight, 
                                1.0f, 0.8f, 0.0f, 1.0f);
            }
            
            // 绘制百分比文字
            String percentText = String.format("%.0f%%", cooldownPercentage * 100);
            renderer.drawText(percentText, barX + 165, barY + 35, 12, 1.0f, 1.0f, 1.0f, 1.0f);
        }
    }

    private void renderLevel() {
        // 在屏幕顶部中央绘制level数
        String levelText = "Level: " + level;
        // 屏幕宽度1600，文本居中，假设字体大小为20，位置大约在x=800-50=750
        renderer.drawText(levelText, 750, 30, 20, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    private void createHulu() {
        /**
         * 创建葫芦娃实体，他会被系统当作主玩家
         * 可以通过GameLogic中的规则操控他
         */


        GameObject hulu = new GameObject("Hulu Player") {

            private Vector2 basePosition;
            public List<GameObject> attackingSkillsJ = new ArrayList<>();

            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                updateComponents(deltaTime);
                updateBodyParts();
                if (attackingSkillsJ.isEmpty()) {
                    initAttackSkillJ();
                }
            }

            @Override
            public void render() {
                renderBodyParts();
            }
            
            private void updateBodyParts() {
                TransformComponent transform = getComponent(TransformComponent.class);
                if (transform != null) {
                    basePosition = transform.getPosition();
                }
            }

            public void initAttackSkillJ() {
                
                /**
                 * 
                 * 葫芦娃的技能1，按J触发
                 * 可以向左右方向发射两道光束，造成10伤害
                 * 
                 */

                for (int i = 0; i < 2; i++) {

                    GameObject attackingSkillJ = new GameObject("Attacking SkillJ " + i) {

                        @Override
                        public void update(float deltaTime) {
                            super.update(deltaTime);
                            updateComponents(deltaTime);
                        }

                        @Override
                        public void render() {
                            renderBodyParts();
                        }

                        private void renderBodyParts() {
                            TransformComponent transform = getComponent(TransformComponent.class);
                            if (transform == null) return;
                            Vector2 p = transform.getPosition();
                            
                            // 渲染箭头形状的攻击技能
                            renderer.drawRect(p.x + 5f, p.y - 4f, 5f, 3f, 1.0f, 1.0f, 1.0f, 1.0f);
                            renderer.drawRect(p.x + 5f, p.y + 1f, 5f, 3f, 1.0f, 1.0f, 1.0f, 1.0f);
                        }
                    };

                    attackingSkillJ.setPlayerSkill();

                    TransformComponent AttackingSkillJTransform = getComponent(TransformComponent.class);
                    Vector2 AttackingSkillJTransformPosition = AttackingSkillJTransform.getPosition();

                    Vector2 position = new Vector2(
                        AttackingSkillJTransformPosition.x + (random.nextFloat() - 0.5f) * 20,
                        AttackingSkillJTransformPosition.y + (random.nextFloat() - 0.5f) * 20
                    );
            
                    attackingSkillJ.addComponent(new TransformComponent(position));
                    
                    RenderComponent render = attackingSkillJ.addComponent(new RenderComponent(
                        RenderComponent.RenderType.RECTANGLE,
                        new Vector2(20, 20),
                        new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f)
                    ));
                    render.setRenderer(renderer);
                    
                    PhysicsComponent physics = attackingSkillJ.addComponent(new PhysicsComponent(0.5f));
                    physics.setVelocity(new Vector2(
                        (random.nextFloat() - 0.5f) * 100,
                        (random.nextFloat() - 0.5f) * 100
                    ));
                    physics.setFriction(0.98f);
                    
                    // 添加生命周期组件，设置0.5秒生命周期
                    LifeFeatureComponent lifeFeature = attackingSkillJ.addComponent(new LifeFeatureComponent(1));
                    lifeFeature.setLifetime(1.0f); // 技能存活1秒

                    this.attackingSkillsJ.add(attackingSkillJ);
                    addGameObject(attackingSkillJ);
                }
            }

            public List<GameObject> getAttackingSkillsJ() {
                return this.attackingSkillsJ;
            }

            private void renderBodyParts() {
                if (basePosition == null) return;
                
                // 渲染葫芦身体上部 - 较小的圆
                renderer.drawCircle(
                    basePosition.x, basePosition.y - 8, 6.0f, 32,
                    1.0f, 0.0f, 0.0f, 1.0f  // 红色
                );

                // 头顶小叶子（绿色叶片 + 茎）
                renderer.drawRect(
                    basePosition.x - 0.75f, basePosition.y - 20.0f, 1.5f, 5.0f,
                    0.10f, 0.45f, 0.10f, 1.0f  // 绿色茎
                );

                renderer.drawCircle(
                    basePosition.x + 3.0f, basePosition.y - 20.0f, 3.4f, 27,
                    0.15f, 0.70f, 0.20f, 1.0f  // 右叶片
                );

                // 渲染眼睛（白色眼白 + 黑色瞳孔）
                renderer.drawCircle(
                    basePosition.x - 3.0f, basePosition.y - 10.0f, 1.8f, 16,
                    1.0f, 1.0f, 1.0f, 1.0f  // 白色
                );
                renderer.drawCircle(
                    basePosition.x + 3.0f, basePosition.y - 10.0f, 1.8f, 16,
                    1.0f, 1.0f, 1.0f, 1.0f  // 白色
                );
                renderer.drawCircle(
                    basePosition.x - 3.0f, basePosition.y - 10.0f, 0.8f, 12,
                    0.0f, 0.0f, 0.0f, 1.0f  // 黑色瞳孔
                );
                renderer.drawCircle(
                    basePosition.x + 3.0f, basePosition.y - 10.0f, 0.8f, 12,
                    0.0f, 0.0f, 0.0f, 1.0f  // 黑色瞳孔
                );

                // 渲染嘴巴（细长矩形）
                renderer.drawRect(
                    basePosition.x - 2.0f, basePosition.y - 7.0f, 4.0f, 2.0f,
                    0.0f, 0.0f, 0.0f, 1.0f  // 黑色
                );
                
                // 渲染葫芦身体下部 - 较大的圆
                renderer.drawCircle(
                    basePosition.x, basePosition.y + 5, 10.0f, 32,
                    1.0f, 0.0f, 0.0f, 1.0f  // 红色
                );
                
            }
        };
        
        hulu.setPlayer();
        
        // 添加变换组件（玩家初始位置在地图中心：1600x1200的中心）
        hulu.addComponent(new TransformComponent(new Vector2(800, 600)));

        // 添加物理组件
        PhysicsComponent physics = hulu.addComponent(new PhysicsComponent(1.0f));
        physics.setFriction(0.95f);

        // 添加生命特征组件
        LifeFeatureComponent lifeFeatures = hulu.addComponent(new LifeFeatureComponent(100));
        
        addGameObject(hulu);
    }

    private void createEnemySoldiers() {
        for (int i = 0; i <= 5; i++) {
            createEnemySoldier();
        }
    }

    private void createEnemySoldier() {
        GameObject enemySoldier = new GameObject("EnemySoldier") {

            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                updateComponents(deltaTime);
            }

            @Override
            public void render() {
                renderBodyParts();
            }

            private void renderBodyParts() {
                TransformComponent transform = getComponent(TransformComponent.class);
                if (transform == null) return;
                Vector2 p = transform.getPosition();

                // Torso (uniform)
                renderer.drawRect(p.x - 8f, p.y - 2f, 16f, 20f, 0.12f, 0.40f, 0.18f, 1f);

                // Head
                renderer.drawCircle(p.x, p.y - 14f, 6f, 24, 1.0f, 0.86f, 0.72f, 1.0f);

                // Helmet
                renderer.drawRect(p.x - 7f, p.y - 19f, 14f, 6f, 0.10f, 0.30f, 0.12f, 1.0f);
                renderer.drawRect(p.x - 7f, p.y - 14f, 14f, 2f, 0.08f, 0.25f, 0.10f, 1.0f);

                // Eyes
                renderer.drawCircle(p.x - 2.0f, p.y - 14.0f, 0.8f, 12, 0f, 0f, 0f, 1f);
                renderer.drawCircle(p.x + 2.0f, p.y - 14.0f, 0.8f, 12, 0f, 0f, 0f, 1f);

                // Arms (uniform)
                renderer.drawRect(p.x - 14f, p.y - 2f, 6f, 14f, 0.12f, 0.40f, 0.18f, 1f);
                renderer.drawRect(p.x + 8f, p.y - 2f, 6f, 14f, 0.12f, 0.40f, 0.18f, 1f);

                // Belt
                renderer.drawRect(p.x - 8f, p.y + 6f, 16f, 2f, 0.05f, 0.05f, 0.05f, 1f);

                // Legs (pants)
                renderer.drawRect(p.x - 6f, p.y + 12f, 6f, 12f, 0.10f, 0.35f, 0.15f, 1f);
                renderer.drawRect(p.x + 0f, p.y + 12f, 6f, 12f, 0.10f, 0.35f, 0.15f, 1f);

                // Boots
                renderer.drawRect(p.x - 6f, p.y + 22f, 6f, 3f, 0f, 0f, 0f, 1f);
                renderer.drawRect(p.x + 0f, p.y + 22f, 6f, 3f, 0f, 0f, 0f, 1f);

                // Rifle
                renderer.drawRect(p.x + 12f, p.y - 2f, 14f, 2f, 0.1f, 0.1f, 0.1f, 1f);
                renderer.drawRect(p.x + 12f, p.y + 0f, 3f, 6f, 0.1f, 0.1f, 0.1f, 1f);
                
                // 渲染血条在头顶上方
                LifeFeatureComponent lifeFeature = getComponent(LifeFeatureComponent.class);
                if (lifeFeature != null) {
                    int currentHealth = lifeFeature.getBlood();
                    int maxHealth = 100; // 最大血量为100
                    // 血条位于敌人头顶上方
                    renderer.drawHealthBar(p.x - 15f, p.y - 30f, 30f, 4f, currentHealth, maxHealth);
                }
            }
        };

        enemySoldier.setEnemy();

        // 生成远离玩家的位置（优先相对于当前玩家位置）
        // 确保敌人距离玩家至少 minDistance 像素
        Vector2 position;
        int minDistance = 400; // 最小距离
        int maxAttempts = 200; // 最大尝试次数（增大重试次数以提高成功率）
        int attempts = 0;

        // 使用当前玩家位置作为参考中心（如果不存在则回退到地图中心）
        Vector2 playerCenter = new Vector2(800, 600);
        if (gameLogic != null) {
            GameObject player = gameLogic.getPlayer();
            if (player != null) {
                TransformComponent pt = player.getComponent(TransformComponent.class);
                if (pt != null) {
                    playerCenter = pt.getPosition();
                }
            }
        }

        do {
            position = new Vector2(
                random.nextFloat() * 1600,
                random.nextFloat() * 1200
            );
            attempts++;

            // 计算与玩家中心的距离
            float dx = position.x - playerCenter.x;
            float dy = position.y - playerCenter.y;
            float distance = (float)Math.sqrt(dx * dx + dy * dy);

            // 如果距离足够远，或者尝试次数过多，就使用这个位置
            if (distance >= minDistance || attempts >= maxAttempts) {
                break;
            }
        } while (true);
        
        // 添加变换组件
        enemySoldier.addComponent(new TransformComponent(position));
        
        // 添加渲染组件
        RenderComponent render = enemySoldier.addComponent(new RenderComponent(
            RenderComponent.RenderType.RECTANGLE,
            new Vector2(20, 20),
            new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f)
        ));
        render.setRenderer(renderer);
        
        // 添加物理组件
        PhysicsComponent physics = enemySoldier.addComponent(new PhysicsComponent(0.5f));
        physics.setVelocity(new Vector2(
            (random.nextFloat() - 0.5f) * 100,
            (random.nextFloat() - 0.5f) * 100
        ));
        physics.setFriction(0.98f);

        // 添加生命特征组件
        LifeFeatureComponent lifeFeatures = enemySoldier.addComponent(new LifeFeatureComponent(100));
        
        addGameObject(enemySoldier);
    }

    /**
     * 国王怪
     */

    private void createEnemyKing() {

        GameObject enemyKing = new GameObject("EnemyKing") {

            public List<GameObject> attackingSkills = new ArrayList<>();
            
            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                updateComponents(deltaTime);
                if (attackingSkills.isEmpty()) {
                    initAttackingSkills();
                }
            }

            @Override
            public void render() {
                renderBodyParts();
            }

            private void renderBodyParts() {
                TransformComponent transform = getComponent(TransformComponent.class);
                if (transform == null) return;
                Vector2 p = transform.getPosition();

                // Torso (king's robe)
                renderer.drawRect(p.x - 10f, p.y - 10f, 20f, 30f, 0.8f, 0.2f, 0.2f, 1f);

                // Head
                renderer.drawCircle(p.x, p.y - 20f, 8f, 24, 1.0f, 0.8f, 0.6f, 1.0f);

                // Crown
                renderer.drawRect(p.x - 10f, p.y - 28f, 20f, 6f, 1.0f, 0.8f, 0.0f, 1.0f);

                // Eyes
                renderer.drawCircle(p.x - 3.0f, p.y - 20.0f, 1.0f, 12, 0f, 0f, 0f, 1f);
                renderer.drawCircle(p.x + 3.0f, p.y - 20.0f, 1.0f, 12, 0f, 0f, 0f, 1f);

                // Mouth
                renderer.drawRect(p.x - 3.0f, p.y - 15.0f, 6.0f, 2.0f, 0.0f, 0.0f, 0.0f, 1.0f);

                // Arms (king's sleeves)
                renderer.drawRect(p.x - 15f, p.y - 10f, 5f, 20f, 0.8f, 0.2f, 0.2f, 1f);
                renderer.drawRect(p.x + 10f, p.y - 10f, 5f, 20f, 0.8f, 0.2f, 0.2f, 1f);

                // Detailed hands
                renderer.drawRect(p.x - 20f, p.y - 10f, 5f, 5f, 0.8f, 0.6f, 0.4f, 1f);
                renderer.drawRect(p.x + 15f, p.y - 10f, 5f, 5f, 0.8f, 0.6f, 0.4f, 1f);

                // Legs (king's pants)
                renderer.drawRect(p.x - 6f, p.y + 20f, 6f, 12f, 0.5f, 0.5f, 0.5f, 1f);
                renderer.drawRect(p.x + 0f, p.y + 20f, 6f, 12f, 0.5f, 0.5f, 0.5f, 1f);

                // Detailed feet
                renderer.drawRect(p.x - 6f, p.y + 32f, 6f, 3f, 0.3f, 0.3f, 0.3f, 1f);
                renderer.drawRect(p.x + 0f, p.y + 32f, 6f, 3f, 0.3f, 0.3f, 0.3f, 1f);
                renderer.drawRect(p.x - 6f, p.y + 35f, 3f, 2f, 0.2f, 0.2f, 0.2f, 1f);
                renderer.drawRect(p.x + 3f, p.y + 35f, 3f, 2f, 0.2f, 0.2f, 0.2f, 1f);
                
                // 渲染血条在王冠上方
                LifeFeatureComponent lifeFeature = getComponent(LifeFeatureComponent.class);
                if (lifeFeature != null) {
                    int currentHealth = lifeFeature.getBlood();
                    int maxHealth = 200; // 最大血量为200
                    // 血条位于敌人王冠上方
                    renderer.drawHealthBar(p.x - 20f, p.y - 38f, 40f, 5f, currentHealth, maxHealth);
                }
            }

            public void initAttackingSkills() {
                for (int i = 0; i < 5; i++) {
                    GameObject attackingSkill = new GameObject("Attacking Skill " + i) {
                        @Override
                        public void update(float deltaTime) {
                            super.update(deltaTime);
                            updateComponents(deltaTime);
                        }

                        @Override
                        public void render() {
                            renderBodyParts();
                        }

                        private void renderBodyParts() {
                            TransformComponent transform = getComponent(TransformComponent.class);
                            if (transform == null) return;
                            Vector2 p = transform.getPosition();
                            
                            // 渲染箭头形状的攻击技能
                            renderer.drawRect(p.x - 10f, p.y - 1f, 14f, 3f, 1.0f, 0.0f, 0.0f, 1f);
                            renderer.drawRect(p.x + 5f, p.y - 4f, 5f, 3f, 1.0f, 0.0f, 0.0f, 1f);
                            renderer.drawRect(p.x + 5f, p.y + 1f, 5f, 3f, 1.0f, 0.0f, 0.0f, 1f);
                            renderer.drawRect(p.x + 7f, p.y - 2f, 3f, 2f, 1.0f, 0.0f, 0.0f, 1f);
                            renderer.drawRect(p.x + 7f, p.y + 1f, 3f, 2f, 1.0f, 0.0f, 0.0f, 1f);
                        }
                    };

                    TransformComponent enemyKingTransform = getComponent(TransformComponent.class);
                    Vector2 enemyKingPosition = enemyKingTransform.getPosition();
                    Vector2 position = new Vector2(
                        enemyKingPosition.x + (random.nextFloat() - 0.5f) * 20,
                        enemyKingPosition.y + (random.nextFloat() - 0.5f) * 20
                    );
                    attackingSkill.setEnemySkill();
                    attackingSkill.addComponent(new TransformComponent(position));
                    
                    RenderComponent render = attackingSkill.addComponent(new RenderComponent(
                        RenderComponent.RenderType.RECTANGLE,
                        new Vector2(20, 20),
                        new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f)
                    ));
                    render.setRenderer(renderer);
                    
                    PhysicsComponent physics = attackingSkill.addComponent(new PhysicsComponent(0.5f));
                    physics.setVelocity(new Vector2(
                        (random.nextFloat() - 0.5f) * 100,
                        (random.nextFloat() - 0.5f) * 100
                    ));
                    physics.setFriction(0.98f);

                    // 添加生命周期组件，设置0.5秒生命周期
                    LifeFeatureComponent lifeFeature = attackingSkill.addComponent(new LifeFeatureComponent(1));
                    lifeFeature.setLifetime(2.0f); // 技能存活2秒

                    this.attackingSkills.add(attackingSkill);
                    addGameObject(attackingSkill);
                }
            }

            public List<GameObject> getAttackingSkills() {
                return this.attackingSkills;
            }
        };

        enemyKing.setEnemy();

        // 生成远离玩家中心(800, 600)的随机位置
        // 确保敌人距离玩家至少400像素
        Vector2 position;
        int minDistance = 400; // 最小距离
        int maxAttempts = 50; // 最大尝试次数
        int attempts = 0;
        
        do {
            position = new Vector2(
                random.nextFloat() * 1600,
                random.nextFloat() * 1200
            );
            attempts++;
            
            // 计算与玩家中心的距离
            float dx = position.x - 800;
            float dy = position.y - 600;
            float distance = (float)Math.sqrt(dx * dx + dy * dy);
            
            // 如果距离足够远，或者尝试次数过多，就使用这个位置
            if (distance >= minDistance || attempts >= maxAttempts) {
                break;
            }
        } while (true);
        
        enemyKing.addComponent(new TransformComponent(position));
        
        RenderComponent render = enemyKing.addComponent(new RenderComponent(
            RenderComponent.RenderType.RECTANGLE,
            new Vector2(20, 20),
            new RenderComponent.Color(1.0f, 0.5f, 0.0f, 1.0f)
        ));
        render.setRenderer(renderer);
        
        PhysicsComponent physics = enemyKing.addComponent(new PhysicsComponent(0.5f));
        physics.setVelocity(new Vector2(
            (random.nextFloat() - 0.5f) * 100,
            (random.nextFloat() - 0.5f) * 100
        ));
        physics.setFriction(0.98f);
        
        // 添加生命特征组件
        LifeFeatureComponent lifeFeatures = enemyKing.addComponent(new LifeFeatureComponent(200));

        addGameObject(enemyKing);
    }

    private void createTree() {
        GameObject tree = new GameObject("tree") {
            @Override
            public void update(float deltaTime) {
                super.update(deltaTime);
                updateComponents(deltaTime);
            }
            
            @Override
            public void render() {
                renderBodyParts();
            }

            public void renderBodyParts() {
                TransformComponent transform = getComponent(TransformComponent.class);
                if (transform == null) return;
                Vector2 p = transform.getPosition();
                
                // Render the trunk
                renderer.drawRect(p.x - 1.0f, p.y, 2.0f, 10.0f, 0.54f, 0.27f, 0.07f, 1.0f);

                // Render the leaves (top)
                renderer.drawCircle(p.x, p.y - 5.0f, 8.0f, 32, 0.0f, 0.5f, 0.0f, 1.0f);
            }
        };
        
        Vector2 position = new Vector2(
            random.nextFloat() * 1600,
            random.nextFloat() * 1200
        );
        
        tree.addComponent(new TransformComponent(position));
        
        RenderComponent render = tree.addComponent(new RenderComponent(
            RenderComponent.RenderType.CIRCLE,
            new Vector2(5, 5),
            new RenderComponent.Color(0.5f, 0.5f, 1.0f, 0.8f)
        ));
        render.setRenderer(renderer);
        
        addGameObject(tree);
    }

    private void createTrees() {
        for (int i = 0; i < 3; i++) {
            createTree();
        }
    }

    public void level1() {
        createEnemySoldiers();
    }

    public void level2() {
        createEnemyKing();
    }

    public void level3() {
        createEnemySoldiers();
        createEnemyKing();
    }

}
