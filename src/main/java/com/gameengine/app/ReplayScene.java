package com.gameengine.app;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.scene.Scene;

/**
 * 回放场景
 * 从录制文件中读取并重现游戏过程
 * (当前为简化版本，完整功能需要recording包支持)
 */
public class ReplayScene extends Scene {
    private GameEngine engine;
    private IRenderer renderer;
    private InputManager inputManager;
    @SuppressWarnings("unused")
    private String recordingPath;
    
    public ReplayScene(GameEngine engine, String recordingPath) {
        super("Replay");
        this.engine = engine;
        this.recordingPath = recordingPath;
    }
    
    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.inputManager = engine.getInputManager();
    }
    
    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        
        // ESC键返回菜单
        if (inputManager.isKeyJustPressed(256)) { // GLFW_KEY_ESCAPE
            returnToMenu();
        }
    }
    
    private void returnToMenu() {
        Scene menuScene = new MenuScene(engine, "MainMenu");
        engine.setScene(menuScene);
    }
    
    @Override
    public void render() {
        // 绘制背景
        renderer.drawRect(0, 0, renderer.getWidth(), renderer.getHeight(), 0.06f, 0.06f, 0.08f, 1.0f);
        
        // 显示提示信息
        String hint = "回放功能暂未实现 - ESC 返回菜单";
        float w = hint.length() * 12.0f;
        renderer.drawText(hint, renderer.getWidth()/2.0f - w/2.0f, renderer.getHeight()/2.0f, 12, 0.8f, 0.8f, 0.8f, 1.0f);
    }
}
