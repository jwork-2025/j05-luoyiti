package com.gameengine.app;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.RenderBackend;

/**
 * 游戏主入口
 * 启动游戏引擎并显示主菜单
 */
public class Game {
    public static void main(String[] args) {
        System.out.println("启动游戏引擎...");

        GameEngine engine = null;
        try {
            System.out.println("使用渲染后端: GPU");
            // 初始化游戏引擎（1600x1200分辨率）
            engine = new GameEngine(1600, 1200, "葫芦娃大战妖怪", RenderBackend.GPU);

            // 创建主菜单场景
            MenuScene menuScene = new MenuScene(engine, "MainMenu");
            engine.setScene(menuScene);
            
            // 运行游戏
            engine.run();
        } catch (Exception e) {
            System.err.println("游戏运行出错: " + e.getMessage());
            e.printStackTrace();
        }

        System.out.println("游戏结束");
    }
}
