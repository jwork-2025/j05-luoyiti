package com.gameengine.core;

import com.gameengine.components.TransformComponent;
import com.gameengine.components.LifeFeatureComponent;
import com.gameengine.components.PhysicsComponent;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

import java.util.List;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Random;

// 并行处理类导入
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * 游戏逻辑类，处理具体的游戏规则
 */
public class GameLogic {
    private Scene scene;
    private GameEngine engine;
    private InputManager inputManager;
    private Random random;
    private float enemyMoveTimer;
    private float enemyMoveInterval = 0.3f;
    private float EnemyAttackLifeTimer; // 攻击技能存活时间计时器
    private float EnemyAttackLifeDuration = 1.0f; // 攻击技能存活时间
    private float playerSkillCooldownTimer; // 玩家技能冷却计时器
    private float playerSkillCooldownDuration = 0.5f; // 玩家技能冷却时间（0.5秒）
    private float enemySkillCooldownTimer;
    private float enemySkillCooldownDuration = 2.0f;

    private boolean gameOver;

    // 多线程通用组件
    private final int PARALLEL_THRESHOLD = 20; // 组件数量超过此值才启用并行
    private final int threadCount;

    // 物理更新多线程赋值
    private ExecutorService physicsExecutor;
    // 碰撞检测多线程赋值
    private ExecutorService collisionExecutor;

    public GameLogic(Scene scene, GameEngine engine) {
        this.scene = scene;
        this.engine = engine;
        this.inputManager = InputManager.getInstance();
        this.random = new Random();
        this.enemyMoveTimer = 0;
        this.EnemyAttackLifeTimer = 0;
        this.playerSkillCooldownTimer = 0.5f; // 初始化为冷却完成状态
        this.enemySkillCooldownTimer = 0.5f;
        this.gameOver = false;

        // 多线程池通用threadCount赋值
        this.threadCount = Math.max(2, Runtime.getRuntime().availableProcessors() - 1);
        // 为避免重复创建线程池，在此初始化
        // 初始化物理更新多线程池
        physicsExecutor = Executors.newFixedThreadPool(threadCount);
        System.out.println("物理更新线程池已经被初始化完成");
        // 初始化碰撞检测多线程池
        this.collisionExecutor = Executors.newFixedThreadPool(threadCount);
        System.out.println("碰撞检测线程池已经被初始化完成");
    }

    /**
     * 返回当前游戏状态
     */
    public boolean isGameOver() {
        return gameOver;
    }

    /**
     * 获取当前游戏玩家
     */
    public GameObject getPlayer() {
        for (GameObject obj : scene.getGameObjects()) {
            if (obj.getidentity().equals("Player") && obj.hasComponent(PhysicsComponent.class)) {
                return obj;
            }
        }
        return null;
    }

    /**
     * 获取所有玩家(为日后扩展成多人游戏作准备)
     */
        public List<GameObject> getPlayers() {
        return scene.getGameObjects().stream()
            .filter(obj -> obj.getidentity().equals("Player"))
            .filter(obj -> obj.isActive())
            .collect(Collectors.toList());
    }

    /**
     * 获取当前所有怪物
     */
    public List<GameObject> getEnemies() {
        return scene.getGameObjects().stream()
            .filter(obj -> obj.getidentity().equals("Enemy"))
            .filter(obj -> obj.isActive())
            .collect(Collectors.toList());
    }

    /**
     * 获取所有技能对象
     */
    public List<GameObject> getSkills() {
        return scene.getGameObjects().stream()
            .filter(obj -> obj.getidentity().equals("Player Skill"))
            .filter(obj -> obj.isActive())
            .collect(Collectors.toList());
    }

    /**
     * 记录游戏对象的当前状态
     */
    public void updateRecords(float keyTimer, FileWriter recordingWriter) {

        String records = "";
        for (GameObject obj : scene.getGameObjects()) {

            if (!obj.isActive()) continue;

            if (obj.getidentity().equals("None")) continue; // 对于无身份的对象不记录

            String singleRecord = obj.getRecords();
            records += singleRecord + ";";
        }
        records = "deltaTime=" + keyTimer + ";" + records;
        try {
                if (!records.isEmpty()) {
                    recordingWriter.write(records);
                    recordingWriter.write("\n");
                }
            } catch (Exception e) {
                System.err.println("记录游戏对象状态时出错: " + e.getMessage());
            }
    }

    /**
     * 处理玩家输入
     */
    public void handlePlayerInput() {

        if (gameOver)
            return;

        GameObject player = getPlayer();
        TransformComponent transform = player.getComponent(TransformComponent.class);
        PhysicsComponent physics = player.getComponent(PhysicsComponent.class);

        if (transform == null || physics == null)
            return;

        Vector2 movement = new Vector2();

        if (inputManager.isKeyPressed(87) || inputManager.isKeyPressed(38)) { // W或上箭头
            movement.y -= 1;
        }
        if (inputManager.isKeyPressed(83) || inputManager.isKeyPressed(40)) { // S或下箭头
            movement.y += 1;
        }
        if (inputManager.isKeyPressed(65) || inputManager.isKeyPressed(37)) { // A或左箭头
            movement.x -= 1;
        }
        if (inputManager.isKeyPressed(68) || inputManager.isKeyPressed(39)) { // D或右箭头
            movement.x += 1;
        }

        if (inputManager.isKeyPressed(74)) { // J按键

            handlePlayerAttackJ();

        }

        if (movement.magnitude() > 0) {
            movement = movement.normalize().multiply(200);
            physics.setVelocity(movement);
        }

        // 边界检查（地图扩大到1600x1200）
        Vector2 pos = transform.getPosition();
        if (pos.x < 0)
            pos.x = 0;
        if (pos.y < 0)
            pos.y = 0;
        if (pos.x > 1600 - 20)
            pos.x = 1600 - 20;
        if (pos.y > 1200 - 20)
            pos.y = 1200 - 20;
        transform.setPosition(pos);
    }

    /**
     * 更新物理系统
     * 由组件数量决定并串行方式
     * 若组件过少，则串行，反之并行
     */
    public void updatePhysics() {
        List<PhysicsComponent> physicsComponents = scene.getComponents(PhysicsComponent.class);
        if (physicsComponents.isEmpty())
            return;

        // 根据组件数量决定串行还是并行
        if (physicsComponents.size() < PARALLEL_THRESHOLD) {
            updatePhysicsSerial(physicsComponents);
        } else {
            updatePhysicsParallel(physicsComponents);
        }

    }

    /**
     * 并行处理物理系统
     * 
     */
    private void updatePhysicsParallel(List<PhysicsComponent> physicsComponents) {

        // 初始化物理更新线程池（若为空或已关闭则重新创建）
        if (physicsExecutor == null || physicsExecutor.isShutdown() || physicsExecutor.isTerminated()) {
            physicsExecutor = Executors.newFixedThreadPool(threadCount);
            System.out.println("物理更新线程池已经被初始化完成");
        }

        // 分批次处理优化
        int batchSize = Math.max(1, physicsComponents.size() / threadCount + 1);

        List<Future<?>> futures = new ArrayList<>();

        for (int i = 0; i < physicsComponents.size(); i += batchSize) {
            final int start = i;
            final int end = Math.min(i + batchSize, physicsComponents.size());

            Future<?> future = physicsExecutor.submit(() -> {
                for (int j = start; j < end; j++) {
                    PhysicsComponent physics = physicsComponents.get(j);
                    updateSinglePhysics(physics);
                }
            });

            futures.add(future);
        }

        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 串行更新物理组件
     * 
     */
    private void updatePhysicsSerial(List<PhysicsComponent> physicsComponents) {
        for (PhysicsComponent physicsComponent : physicsComponents) {
            updateSinglePhysics(physicsComponent);
        }
    }

    /**
     * 对单个物理部件进行更新
     * 是决定具体更新方式的关键
     */
    private void updateSinglePhysics(PhysicsComponent physics) {
        TransformComponent transform = physics.getOwner().getComponent(TransformComponent.class);
        if (transform != null) {
            Vector2 pos = transform.getPosition();
            Vector2 velocity = physics.getVelocity();

            boolean velocityChanged = false;

            if (pos.x <= 0 || pos.x >= 1600 - 15) {
                velocity.x = -velocity.x;
                velocityChanged = true;
            }
            if (pos.y <= 0 || pos.y >= 1200 - 15) {
                velocity.y = -velocity.y;
                velocityChanged = true;
            }

            if (pos.x < 0)
                pos.x = 0;
            if (pos.y < 0)
                pos.y = 0;
            if (pos.x > 1600 - 15)
                pos.x = 1600 - 15;
            if (pos.y > 1200 - 15)
                pos.y = 1200 - 15;

            transform.setPosition(pos);

            if (velocityChanged) {
                physics.setVelocity(velocity);
            }
        }
    }

    /**
     * 对对象的碰撞进行检测
     * 
     * @param deltaTime
     */
    public void checkAiCollisions(float deltaTime) {

        if (gameOver)
            return;

        List<GameObject> players = getPlayers();
        List<GameObject> enemies = getEnemies();
        List<GameObject> playerSkills = getSkills();

        // 如果没有需要检测的对象，直接返回
        if (players.isEmpty() && enemies.isEmpty()) {
            return;
        }
        // 根据对象多少决定并行或串行的方式
        int totalCollisions = players.size() + enemies.size();
        if (totalCollisions < PARALLEL_THRESHOLD) {
            checkAiCollisionsSerial(players, enemies, playerSkills);
        } else {
            checkAiCollisionsParallel(players, enemies, playerSkills);
        }

    }

    /**
     * 串行处理组件碰撞
     * 
     */
    public void checkAiCollisionsSerial(List<GameObject> players,
            List<GameObject> enemies,
            List<GameObject> playerSkills) {
        // 检查玩家与敌人/敌人技能的碰撞
        for (GameObject player : players) {
            checkPlayerCollisions(player, enemies);
        }

        // 检查敌人与玩家技能的碰撞
        for (GameObject enemy : enemies) {
            checkEnemyCollisions(enemy, playerSkills);
        }
    }

    /**
     * 并行处理组件碰撞
     */
    private void checkAiCollisionsParallel(List<GameObject> players,
            List<GameObject> enemies,
            List<GameObject> playerSkills) {
        List<Future<?>> futures = new ArrayList<>();
        
        // 防止未初始化成果，这里检测一次
        if (collisionExecutor == null || collisionExecutor.isShutdown() || collisionExecutor.isTerminated()) {
            collisionExecutor = Executors.newFixedThreadPool(threadCount);
            System.out.println("碰撞检测线程池已经被初始化完成");
        }

        // 批量处理玩家碰撞
        int playerBatchSize = Math.max(1, players.size() / threadCount);
        for (int i = 0; i < players.size(); i += playerBatchSize) {
            final int start = i;
            final int end = Math.min(i + playerBatchSize, players.size());

            Future<?> future = collisionExecutor.submit(() -> {
                try {
                    for (int j = start; j < end; j++) {
                        GameObject player = players.get(j);
                        checkPlayerCollisions(player, enemies);
                    }
                } catch (Exception e) {
                    System.err.println("碰撞检测中出现错误" + e.getMessage());
                }
            });
            futures.add(future);
        }

        // 批量处理敌人碰撞
        int enemyBatchSize = Math.max(1, enemies.size() / threadCount);
        for (int i = 0; i < enemies.size(); i += enemyBatchSize) {
            final int start = i;
            final int end = Math.min(i + enemyBatchSize, enemies.size());

            Future<?> future = collisionExecutor.submit(() -> {
                try {
                    for (int j = start; j < end; j++) {
                        GameObject enemy = enemies.get(j);
                        checkEnemyCollisions(enemy, playerSkills);
                    }
                } catch (Exception e) {
                    System.err.println("碰撞检测中出现错误" + e.getMessage());
                }
            });
            futures.add(future);
        }
        // 等待所有任务完成
        for (Future<?> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                System.err.println("等待碰撞检测完成时出现错误" + e.getMessage());
            }
        }
    }

    /**
     * 检查玩家受到攻击
     */
    public void checkPlayerCollisions(GameObject player, List<GameObject> enemies) {

        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        if (playerTransform == null)
            return;

        LifeFeatureComponent playerLife = player.getComponent(LifeFeatureComponent.class);
        if (playerLife == null || playerLife.isunbeatable)
            return;

        Vector2 playerPos = playerTransform.getPosition();

        // 检测与敌人的碰撞
        for (GameObject enemy : enemies) {
            if (!enemy.isActive())
                continue;

            TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
            if (enemyTransform != null) {
                float distance = playerPos.distance(enemyTransform.getPosition());
                if (distance < 25) {
                    // 使用同步块保护共享状态的修改
                    synchronized (player) {
                        playerTransform.setPosition(new Vector2(800, 600));
                        playerLife.blood -= 10;
                    }
                    return; // 一次只处理一个碰撞
                }
            }
        }

        // 检测与敌人技能的碰撞
        for (GameObject obj : scene.getGameObjects()) {
            if ("Enemy Skill".equals(obj.getidentity()) && obj.isActive()) {
                TransformComponent skillTransform = obj.getComponent(TransformComponent.class);
                if (skillTransform != null) {
                    float distance = playerPos.distance(skillTransform.getPosition());
                    if (distance < 25) {
                        synchronized (player) {
                            playerTransform.setPosition(new Vector2(800, 600));
                            playerLife.blood -= 10;
                        }
                        return;
                    }
                }
            }
        }
    }

    /**
     * 检查怪物受到攻击
     */
    private void checkEnemyCollisions(GameObject enemy, List<GameObject> playerSkills) {
        TransformComponent enemyTransform = enemy.getComponent(TransformComponent.class);
        if (enemyTransform == null)
            return;

        LifeFeatureComponent enemyLife = enemy.getComponent(LifeFeatureComponent.class);
        if (enemyLife == null || enemyLife.isunbeatable)
            return;

        Vector2 enemyPos = enemyTransform.getPosition();

        // 只检查屏幕内的玩家技能
        for (GameObject skill : playerSkills) {
            if (!skill.isActive())
                continue;

            TransformComponent skillTransform = skill.getComponent(TransformComponent.class);
            if (skillTransform == null)
                continue;

            Vector2 skillPos = skillTransform.getPosition();

            // 检查技能是否在屏幕内
            if (skillPos.x < -100 || skillPos.x > 1700 ||
                    skillPos.y < -100 || skillPos.y > 1300) {
                continue;
            }
            // 检查碰撞
            float distance = enemyPos.distance(skillPos);
            if (distance < 25) {
                // 使用同步块保护共享状态
                synchronized (enemy) {
                    PhysicsComponent skillPhysics = skill.getComponent(PhysicsComponent.class);

                    // 移动技能到屏幕外
                    skillTransform.setPosition(new Vector2(-1000, -1000));
                    if (skillPhysics != null) {
                        skillPhysics.setVelocity(new Vector2(0, 0));
                    }

                    // 扣除生命值
                    enemyLife.blood -= 20;
                }
                return; // 一次只处理一个碰撞
            }
        }
    }

    public void handleEnemyMove() {

        if (gameOver)
            return;

        List<GameObject> enemies = scene.findGameObjectsByComponent(TransformComponent.class);
        if (enemies.isEmpty())
            return;

        // 筛选出所有敌人
        List<GameObject> enemyList = enemies.stream()
                .filter(obj -> obj.getidentity().equals("Enemy"))
                .collect(Collectors.toList());

        if (enemyList.isEmpty())
            return;

        // 获取玩家位置
        GameObject player = enemies.stream()
                .filter(obj -> obj.getidentity().equals("Player"))
                .findFirst()
                .orElse(null);

        if (player == null)
            return;

        TransformComponent playerTransform = player.getComponent(TransformComponent.class);
        Vector2 playerPos = playerTransform.getPosition();

        // 根据敌人数量决定并行或串行
        if (enemyList.size() < PARALLEL_THRESHOLD) {
            enemyList.forEach(enemy -> updateSingleEnemyMove(enemy, playerPos));
        } else {
        // 这里采用并行流处理敌人移动，而不是executor方法，以简化代码
            System.out.println("敌人移动采用并行流处理");
            enemyList.parallelStream()
                    .forEach(enemy -> updateSingleEnemyMove(enemy, playerPos));
        }
    }

    private void updateSingleEnemyMove(GameObject enemy, Vector2 playerPos) {
        TransformComponent transform = enemy.getComponent(TransformComponent.class);
        PhysicsComponent physics = enemy.getComponent(PhysicsComponent.class);

        if (transform == null || physics == null)
            return;

        Random random = new Random();
        float PlayerAngle = playerPos.subtract(transform.getPosition()).angle();
        float randomAngle = (random.nextFloat() * 60 - 45) * (float) Math.PI / 180;
        float adjustedX = (float) Math.cos(PlayerAngle + randomAngle);
        float adjustedY = (float) Math.sin(PlayerAngle + randomAngle);

        Vector2 direction = new Vector2(adjustedX, adjustedY);
        if (direction.magnitude() > 0) {
            direction = direction.normalize();
        }

        // 国王速度更快
        float speed = enemy.getName().equals("EnemyKing") ? 200 : 100;
        Vector2 velocity = direction.multiply(speed);

        synchronized (enemy) {
            physics.setVelocity(velocity);

            // 边界检查
            Vector2 pos = transform.getPosition();
            pos.x = Math.max(0, Math.min(1600, pos.x));
            pos.y = Math.max(0, Math.min(1200, pos.y));
            transform.setPosition(pos);
        }
    }

    public void updateEnemyMovement(float deltaTime) {
        enemyMoveTimer += deltaTime;

        if (enemyMoveTimer >= enemyMoveInterval) {
            enemyMoveTimer = 0;
            handleEnemyMove();
        }
    }

    public void updateEnemyAttack(float deltaTime) {
        EnemyAttackLifeTimer += deltaTime;

        // 每2秒重新发起攻击
        if (EnemyAttackLifeTimer >= EnemyAttackLifeDuration) {
            EnemyAttackLifeTimer = 0;
            handleEnemyAttack();
        }

    }

    public void updateAttack(float deltaTime) {
        // 更新技能冷却计时器
        if (playerSkillCooldownTimer < playerSkillCooldownDuration) {
            playerSkillCooldownTimer += deltaTime;
        }

        // 更新所有 PlayerAttack 的生命周期
        for (GameObject gameObject : scene.getGameObjects()) {
            if (gameObject.getidentity().equals("Player Skill")) {
                LifeFeatureComponent lifeFeature = gameObject.getComponent(LifeFeatureComponent.class);
                if (lifeFeature != null) {
                    lifeFeature.update(deltaTime);
                }
            }
            if (gameObject.getidentity().equals("Enemy Skill")) {
                LifeFeatureComponent lifeFeature = gameObject.getComponent(LifeFeatureComponent.class);
                if (lifeFeature != null) {
                    lifeFeature.update(deltaTime);
                }
            }
        }
    }

    /**
     * 获取技能冷却的百分比（0.0 到 1.0）
     * 
     * @return 冷却进度百分比，1.0 表示完全冷却
     */
    public float getSkillCooldownPercentage() {
        if (playerSkillCooldownDuration <= 0) {
            return 1.0f;
        }
        return Math.min(1.0f, playerSkillCooldownTimer / playerSkillCooldownDuration);
    }

    public void handlePlayerAttackJ() {
        // 检查技能是否在冷却中
        if (playerSkillCooldownTimer < playerSkillCooldownDuration) {
            System.out.println("技能冷却中，剩余时间: " +
                    String.format("%.2f", playerSkillCooldownDuration - playerSkillCooldownTimer) + "秒");
            return;
        }

        List<GameObject> gameObjects = scene.getGameObjects();
        if (gameObjects.isEmpty())
            return;

        // 查找 EnemyKing 和所有攻击技能
        GameObject player = null;
        List<GameObject> attackingSkills = new ArrayList<>();

        for (GameObject obj : gameObjects) {
            if (obj.getidentity().equals("Player")) {
                player = obj;
            }

            if (obj.getidentity().equals("Player Skill")) {
                attackingSkills.add(obj);
            }
        }

        // 如果找到了玩家和攻击技能
        if (player != null && !attackingSkills.isEmpty()) {
            TransformComponent playerTransform = player.getComponent(TransformComponent.class);

            if (playerTransform != null) {
                Vector2 playerPosition = playerTransform.getPosition();

                // 计算均匀分布的角度间隔 (360度 / 2个方向 = 180度)
                float angleStep = (2 * (float) Math.PI) / attackingSkills.size(); // 每个攻击间隔的弧度

                // 为每个攻击技能设置新的位置和方向
                for (int i = 0; i < attackingSkills.size(); i++) {

                    GameObject attackingSkill = attackingSkills.get(i);
                    TransformComponent skillTransform = attackingSkill.getComponent(TransformComponent.class);
                    PhysicsComponent skillPhysics = attackingSkill.getComponent(PhysicsComponent.class);

                    if (skillTransform != null && skillPhysics != null) {
                        // 将攻击技能重置到葫芦的位置
                        skillTransform.setPosition(new Vector2(playerPosition.x, playerPosition.y));

                        // 计算均匀分布的方向 (i * 180度)
                        float angle = i * angleStep;
                        float directionX = (float) Math.cos(angle);
                        float directionY = (float) Math.sin(angle);
                        Vector2 direction = new Vector2(directionX, directionY);

                        if (direction.magnitude() > 0) {
                            direction = direction.normalize();
                        }

                        // 设置速度 - 攻击技能速度为200
                        Vector2 velocity = direction.multiply(300);
                        skillPhysics.setVelocity(velocity);

                        // 激活技能并重置其生命周期
                        attackingSkill.setActive(true);

                        // 重置技能的生命周期组件
                        LifeFeatureComponent lifeFeature = attackingSkill.getComponent(LifeFeatureComponent.class);
                        if (lifeFeature != null) {
                            lifeFeature.resetLifetime();
                        }
                    }
                }

                // 技能释放成功，重置冷却计时器
                playerSkillCooldownTimer = 0;
            }
        }
    }

    public void handleEnemyAttack() {
        List<GameObject> gameObjects = scene.getGameObjects();
        if (gameObjects.isEmpty())
            return;

        // 查找 EnemyKing 和所有攻击技能
        GameObject enemyKing = null;
        List<GameObject> attackingSkills = new ArrayList<>();

        for (GameObject obj : gameObjects) {
            if (obj.getName().equals("EnemyKing")) {
                enemyKing = obj;
            }
            // 查找所有敌人攻击力对象
            if (obj.getidentity().equals("Enemy Skill")) {
                attackingSkills.add(obj);
            }
        }

        // 如果找到了国王和攻击技能
        if (enemyKing != null && !attackingSkills.isEmpty()) {
            TransformComponent kingTransform = enemyKing.getComponent(TransformComponent.class);

            if (kingTransform != null) {
                Vector2 kingPosition = kingTransform.getPosition();

                // 计算均匀分布的角度间隔 (360度 / 5个方向 = 72度)
                float angleStep = (2 * (float) Math.PI) / attackingSkills.size(); // 每个攻击间隔的弧度

                // 为每个攻击技能设置新的位置和方向
                for (int i = 0; i < attackingSkills.size(); i++) {
                    GameObject attackingSkill = attackingSkills.get(i);
                    TransformComponent skillTransform = attackingSkill.getComponent(TransformComponent.class);
                    PhysicsComponent skillPhysics = attackingSkill.getComponent(PhysicsComponent.class);

                    if (skillTransform != null && skillPhysics != null) {
                        // 将攻击技能重置到国王的位置
                        skillTransform.setPosition(new Vector2(kingPosition.x, kingPosition.y));

                        // 计算均匀分布的方向 (i * 72度)
                        float angle = i * angleStep;
                        float directionX = (float) Math.cos(angle);
                        float directionY = (float) Math.sin(angle);
                        Vector2 direction = new Vector2(directionX, directionY);

                        if (direction.magnitude() > 0) {
                            direction = direction.normalize();
                        }

                        // 设置速度 - 攻击技能速度为200
                        Vector2 velocity = direction.multiply(300);
                        skillPhysics.setVelocity(velocity);

                        // 重置技能的生命周期组件
                        LifeFeatureComponent lifeFeature = attackingSkill.getComponent(LifeFeatureComponent.class);
                        if (lifeFeature != null) {
                            lifeFeature.resetLifetime();
                        }

                    }
                }
            }
        }
    }

    public void checkEntityAlive() {

        List<LifeFeatureComponent> lifeFeatureComponents = scene.getComponents(LifeFeatureComponent.class);

        for (LifeFeatureComponent lifeFeatureComponent : lifeFeatureComponents) {
            if (lifeFeatureComponent.getBlood() <= 0) {

                GameObject entity = lifeFeatureComponent.getOwner();

                if (entity.getidentity().equals("Enemy")) {
                    System.out.println(entity.getName() + " died");
                    entity.setActive(false);
                } else if (entity.getidentity().equals("Player")) {
                    System.out.println("Game Over");
                    this.gameOver = true;
                    engine.gameOver();
                }

                // 使实体消失（设置为不活跃状态）
                // 这会在下一次Scene.update()中被移除

            }
        }
    }



    public boolean checkEnemiesDied() {
        for (GameObject obj : scene.getGameObjects()) {
            if (obj.isActive() && obj.getidentity().equals("Enemy")) {
                return false;
            }
        }
        return true;
    }
}