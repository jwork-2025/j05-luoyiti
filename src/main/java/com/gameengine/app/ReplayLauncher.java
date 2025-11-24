package com.gameengine.app;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.RenderBackend;

import java.io.File;
import java.util.Arrays;

/**
 * 回放启动器
 * 独立启动回放功能
 */
public class ReplayLauncher {
    public static void main(String[] args) {
        String path = null;
        
        // 检查命令行参数
        if (args != null && args.length > 0) {
            path = args[0];
        } else {
            // 自动查找最新的录制文件
            File dir = new File("recordings");
            if (dir.exists() && dir.isDirectory()) {
                File[] files = dir.listFiles((d, name) -> name.endsWith(".json") || name.endsWith(".jsonl"));
                if (files != null && files.length > 0) {
                    Arrays.sort(files, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
                    path = files[0].getAbsolutePath();
                    System.out.println("使用录制文件: " + path);
                }
            }
        }

        if (path == null) {
            System.err.println("未找到回放文件");
            System.err.println("用法: java ReplayLauncher [录制文件路径]");
            return;
        }

        // 创建游戏引擎
        GameEngine engine = new GameEngine(1600, 1200, "回放", RenderBackend.GPU);
        
        // 创建回放场景
        ReplayScene replay = new ReplayScene(engine, path);
        engine.setScene(replay);
        
        // 运行
        engine.run();
    }
}
