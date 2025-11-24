package com.gameengine.core;

import com.gameengine.graphics.IRenderer;
import com.gameengine.graphics.RenderBackend;
import com.gameengine.graphics.RendererFactory;
import com.gameengine.input.InputManager;
import com.gameengine.scene.Scene;

/**
 * 游戏引擎
 */
public class GameEngine {
    private IRenderer renderer;
    private InputManager inputManager;
    private Scene currentScene;
    private boolean running;
    private float targetFPS;
    private float deltaTime;
    private long lastTime;
    @SuppressWarnings("unused")
    private String title;
    private GamePerformance gamePerformance;
    
    public GameEngine(int width, int height, String title, RenderBackend backend) {
        this.title = title;
        this.renderer = RendererFactory.createRenderer(backend, width, height, title);
        this.inputManager = InputManager.getInstance();
        this.running = false;
        this.targetFPS = 60.0f;
        this.deltaTime = 0.0f;
        this.lastTime = System.nanoTime();
        this.gamePerformance = new GamePerformance();
        
    }
    
    /**
     * 初始化游戏引擎
     */
    public boolean initialize() {
        return true; // Swing渲染器不需要特殊初始化
    }
    
    /**
     * 运行游戏引擎
     */
    public void run() {
        if (!initialize()) {
            System.err.println("游戏引擎初始化失败");
            return;
        }
        
        running = true;
        
        if (currentScene != null) {
            currentScene.initialize();
            
        }
        
        long lastFrameTime = System.nanoTime();
        long frameTimeNanos = (long)(1_000_000_000.0 / targetFPS);
        
        while (running) {
            long currentTime = System.nanoTime();
            
            if (currentTime - lastFrameTime >= frameTimeNanos) {
                update();
                if (running) {
                    render();
                }
                lastFrameTime = currentTime;
            }
            
            if (renderer.shouldClose()) {
                running = false;
            }
            
            try {
                Thread.sleep(1);
            } catch (InterruptedException e) {
                break;
            }
        }
    }
    
    /**
     * 更新游戏逻辑
     */
    private void update() {
        // 计算时间间隔
        long currentTime = System.nanoTime();
        deltaTime = (currentTime - lastTime) / 1_000_000_000.0f; // 转换为秒
        lastTime = currentTime;

        // 计算游戏帧率
        gamePerformance.update(deltaTime);
        
        // 处理事件（先处理输入事件）
        renderer.pollEvents();
        
        // 更新场景
        if (currentScene != null) {
            currentScene.update(deltaTime);
        }
        
        // 清除输入状态（在场景update之后）
        inputManager.update();
        
        // 检查退出条件（支持多种ESC键码）
        if (inputManager.isKeyPressed(27) || inputManager.isKeyPressed(256)) { // ESC键（27=传统码，256=GLFW码）
            running = false;
            gamePerformance.printSummary(); // 打印平均帧率统计
            renderer.cleanup();
        }
        
        // 检查窗口是否关闭
        if (renderer.shouldClose()) {
            running = false;
            gamePerformance.printSummary(); // 打印平均帧率统计
        }
    }
    
    /**
     * 渲染游戏
     */
    private void render() {
        renderer.beginFrame();
        
        // 渲染场景
        if (currentScene != null) {
            currentScene.render();
        }

        // 渲染帧率
        gamePerformance.render(renderer);

        renderer.endFrame();
    }
    
    /**
     * 设置当前场景
     */
    public void setScene(Scene scene) {
        this.currentScene = scene;
        if (scene != null && running) {
            scene.initialize();
        }
    }
    
    /**
     * 获取当前场景
     */
    public Scene getCurrentScene() {
        return currentScene;
    }
    
    /**
     * 停止游戏引擎
     */
    public void stop() {
        running = false;
    }

    /**
     * 游戏结束
     */
    public void gameOver() {
        stop();
        gamePerformance.printSummary(); // 打印平均帧率统计
        System.out.println("Game Over");
        cleanup();
        System.exit(0);
    }
    
    /**
     * 清理资源
     */
    private void cleanup() {
        if (currentScene != null) {
            currentScene.clear();
        }
        renderer.cleanup();
    }
    
    /**
     * 获取渲染器
     */
    public IRenderer getRenderer() {
        return renderer;
    }
    
    /**
     * 获取输入管理器
     */
    public InputManager getInputManager() {
        return inputManager;
    }
    
    /**
     * 获取时间间隔
     */
    public float getDeltaTime() {
        return deltaTime;
    }
    
    /**
     * 设置目标帧率
     */
    public void setTargetFPS(float fps) {
        this.targetFPS = fps;
    }
    
    /**
     * 获取目标帧率
     */
    public float getTargetFPS() {
        return targetFPS;
    }
    
    /**
     * 检查引擎是否正在运行
     */
    public boolean isRunning() {
        return running;
    }
}
