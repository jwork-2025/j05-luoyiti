package com.gameengine.app;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.math.Vector2;
import com.gameengine.scene.Scene;

/**
 * 主菜单场景
 * 提供开始游戏、回放、退出等选项
 */
public class MenuScene extends Scene {
    private enum MenuOption {
        START_GAME,
        REPLAY,
        EXIT
    }
    
    private static class MenuButton {
        float x, y, width, height;
        MenuOption option;
        
        MenuButton(float x, float y, float width, float height, MenuOption option) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.option = option;
        }
        
        boolean contains(float mx, float my) {
            return mx >= x && mx <= x + width && my >= y && my <= y + height;
        }
    }
    
    private GameEngine engine;
    private IRenderer renderer;
    private InputManager inputManager;
    private MenuOption[] options = {MenuOption.START_GAME, MenuOption.REPLAY, MenuOption.EXIT};
    private int selectedIndex = 0;
    private boolean showReplayInfo = false;
    private float replayInfoTimer = 0.0f;
    private int debugFrames = 0;
    private MenuButton[] menuButtons = new MenuButton[3];
    
    public MenuScene(GameEngine engine, String name) {
        super(name);
        this.engine = engine;
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
        
        if (showReplayInfo) {
            replayInfoTimer += deltaTime;
            if (replayInfoTimer > 2.0f) {
                showReplayInfo = false;
                replayInfoTimer = 0.0f;
            }
        }
        
        // 鼠标悬停检测
        Vector2 mousePos = inputManager.getMousePosition();
        for (int i = 0; i < menuButtons.length; i++) {
            if (menuButtons[i] != null && menuButtons[i].contains(mousePos.x, mousePos.y)) {
                selectedIndex = i;
                break;
            }
        }
        
        // 鼠标左键点击
        if (inputManager.isMouseButtonJustPressed(0)) { // GLFW_MOUSE_BUTTON_LEFT = 0
            for (int i = 0; i < menuButtons.length; i++) {
                if (menuButtons[i] != null && menuButtons[i].contains(mousePos.x, mousePos.y)) {
                    selectedIndex = i;
                    handleSelection();
                    break;
                }
            }
        }
        
        // 向上箭头
        if (inputManager.isKeyJustPressed(265)) { // GLFW_KEY_UP
            selectedIndex = (selectedIndex - 1 + options.length) % options.length;
        }
        
        // 向下箭头
        if (inputManager.isKeyJustPressed(264)) { // GLFW_KEY_DOWN
            selectedIndex = (selectedIndex + 1) % options.length;
        }
        
        // 回车键确认
        if (inputManager.isKeyJustPressed(257)) { // GLFW_KEY_ENTER
            handleSelection();
        }
        
        // ESC键退出
        if (inputManager.isKeyPressed(256)) { // GLFW_KEY_ESCAPE
            engine.stop();
        }
    }
    
    private void handleSelection() {
        MenuOption selected = options[selectedIndex];
        
        switch (selected) {
            case START_GAME:
                switchToGameScene();
                break;
            case REPLAY:
                switchToReplayScene();
                break;
            case EXIT:
                engine.stop();
                break;
        }
    }
    
    private void switchToGameScene() {
        Scene gameScene = new GameScene("Hulu Game", engine);
        engine.setScene(gameScene);
    }
    
    private void switchToReplayScene() {
        // 回放功能暂未实现



        
        showReplayInfo = true;
    }
    
    @Override
    public void render() {
        if (renderer == null) return;
        
        int width = renderer.getWidth();
        int height = renderer.getHeight();
        
        if (debugFrames < 5) {
            debugFrames++;
        }
        
        // 绘制背景
        renderer.drawRect(0, 0, width, height, 0.25f, 0.25f, 0.35f, 1.0f);
        
        super.render();
        
        renderMainMenu();
    }
    
    private void renderMainMenu() {
        if (renderer == null) return;
        
        int width = renderer.getWidth();
        int height = renderer.getHeight();
        
        float centerX = width / 2.0f;
        float centerY = height / 2.0f;
        
        // 绘制标题
        String title = "葫芦娃大战妖怪";
        float titleWidth = title.length() * 20.0f;
        float titleX = centerX - titleWidth / 2.0f;
        float titleY = 120.0f;
        
        renderer.drawRect(centerX - titleWidth / 2.0f - 20, titleY - 40, titleWidth + 40, 80, 0.4f, 0.4f, 0.5f, 1.0f);
        renderer.drawText(title, titleX, titleY, 20, 1.0f, 1.0f, 1.0f, 1.0f);
        
        // 绘制菜单选项
        for (int i = 0; i < options.length; i++) {
            String text = "";
            if (options[i] == MenuOption.START_GAME) {
                text = "开始游戏";
            } else if (options[i] == MenuOption.REPLAY) {
                text = "观看回放";
            } else if (options[i] == MenuOption.EXIT) {
                text = "退出";
            }
            
            float textWidth = text.length() * 20.0f;
            float textX = centerX - textWidth / 2.0f;
            float textY = centerY - 80.0f + i * 80.0f;
            
            // 更新按钮区域
            float buttonX = textX - 20;
            float buttonY = textY - 20;
            float buttonWidth = textWidth + 40;
            float buttonHeight = 50;
            menuButtons[i] = new MenuButton(buttonX, buttonY, buttonWidth, buttonHeight, options[i]);
            
            float r, g, b;
            
            if (i == selectedIndex) {
                r = 1.0f;
                g = 1.0f;
                b = 0.5f;
                renderer.drawRect(buttonX, buttonY, buttonWidth, buttonHeight, 0.6f, 0.5f, 0.2f, 0.9f);
            } else {
                r = 0.95f;
                g = 0.95f;
                b = 0.95f;
                renderer.drawRect(buttonX, buttonY, buttonWidth, buttonHeight, 0.2f, 0.2f, 0.3f, 0.5f);
            }
            
            renderer.drawText(text, textX, textY, 20, r, g, b, 1.0f);
        }
        
        // 绘制操作提示
        String hint1 = "使用箭头键或鼠标选择，回车键确认";
        float hint1Width = hint1.length() * 20.0f;
        float hint1X = centerX - hint1Width / 2.0f;
        renderer.drawText(hint1, hint1X, height - 100, 20, 0.6f, 0.6f, 0.6f, 1.0f);
        
        String hint2 = "ESC 退出游戏";
        float hint2Width = hint2.length() * 20.0f;
        float hint2X = centerX - hint2Width / 2.0f;
        renderer.drawText(hint2, hint2X, height - 70, 20, 0.6f, 0.6f, 0.6f, 1.0f);

        if (showReplayInfo) {
            String info = "暂无可用的回放文件";
            float w = info.length() * 20.0f;
            renderer.drawText(info, centerX - w / 2.0f, height - 140, 20, 0.9f, 0.8f, 0.2f, 1.0f);
        }
    }
}
