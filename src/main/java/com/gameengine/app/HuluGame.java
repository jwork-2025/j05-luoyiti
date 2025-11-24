package com.gameengine.app;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.RenderBackend;

/**
 * 游戏程序入口
 * 这是一个类土豆兄弟游戏
 * 玩家操控游戏主角，即葫芦娃，它需要躲避不断生成的妖怪的攻击
 * 葫芦娃自己并不能进行攻击操作，其获得胜利的目标是存活过规定的时间
 * 
 * 基本的实体包括：
 *  Hulu 葫芦娃对象，是玩家操控的实体
 *  Monster 妖怪对象，在这里把它分为 MonsterKing 和 MonsterSoilder，它们有着不同的攻击方式
 */
public class HuluGame {
    
    public static void main(String[] args) {
        System.out.println("游戏引擎启动....");

        try {
            // 初始化游戏引擎（地图扩大到原来的两倍：1600x1200）
            GameEngine engine = new GameEngine(1600, 1200, "葫芦娃大战妖怪", RenderBackend.GPU);
            
            // 创建游戏场景
            GameScene gameScene = new GameScene("Hulu Game", engine);

            // 设置场景
            engine.setScene(gameScene);

            // 运行游戏
            engine.run();

        } catch (Exception e) {
            System.out.println("游戏运行出错！");
            e.printStackTrace();
        }

        System.out.println("游戏结束");
    }
}
