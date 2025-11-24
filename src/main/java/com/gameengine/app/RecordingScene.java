package com.gameengine.app;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.LinkedList;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.IRenderer;
import com.gameengine.scene.Scene;

public class RecordingScene extends Scene {
    private String recordingPath;
    private LinkedList<String> moves;
    private IRenderer renderer;
    private GameEngine engine;
    
    // 回放控制变量
    private int currentFrameIndex = 0;
    private float accumulatedTime = 0f;
    private float targetTime = 0f;
    private String currentFrameData = "";

    public RecordingScene(GameEngine engine, String recordingPath) {
        super("Recording");
        this.recordingPath = recordingPath;
        this.engine = engine;
        this.moves = getMoves();
        this.renderer = engine.getRenderer();
        
        // 加载第一帧
        if (!moves.isEmpty()) {
            loadFrame(0);
        }
    }

    @Override
    public void render() {

        // 绘制背景（地图扩大到1600x1200）
        renderer.drawRect(0, 0, 1600, 1200, 0.1f, 0.1f, 0.2f, 1.0f);

        // 渲染当前帧的所有对象
        if (!currentFrameData.isEmpty()) {
            renderFrame(currentFrameData);
        }
    }

    @Override
    public void update(float deltaTime) {
        // ESC键返回菜单
        if (engine.getInputManager().isKeyJustPressed(256)) { // GLFW_KEY_ESCAPE
            engine.setScene(new MenuScene(engine, "MainMenu"));
            return;
        }
        
        // 累加时间
        accumulatedTime += deltaTime;
        
        // 当累计时间达到目标时间时，切换到下一帧
        if (accumulatedTime >= targetTime && currentFrameIndex < moves.size() - 1) {
            currentFrameIndex++;
            loadFrame(currentFrameIndex);
        }
        
        // 如果已经播放完所有帧，可以选择循环或停止
        if (currentFrameIndex >= moves.size() - 1) {
            // 循环播放
            currentFrameIndex = 0;
            accumulatedTime = 0f;
            loadFrame(0);
        }
    }
    
    /**
     * 加载指定索引的帧数据
     */
    private void loadFrame(int index) {
        if (index < 0 || index >= moves.size()) {
            return;
        }
        
        String frameData = moves.get(index);
        
        // 解析 deltaTime
        int deltaIndex = frameData.indexOf("deltaTime=");
        if (deltaIndex != -1) {
            int semicolonIndex = frameData.indexOf(";", deltaIndex);
            if (semicolonIndex != -1) {
                String deltaTimeStr = frameData.substring(deltaIndex + 10, semicolonIndex);
                targetTime = Float.parseFloat(deltaTimeStr);
                currentFrameData = frameData.substring(semicolonIndex + 1);
            }
        }
    }
    
    /**
     * 渲染一帧的所有对象
     */
    private void renderFrame(String frameData) {
        // 按照 { } 分割每个游戏对象
        String[] objects = frameData.split("\\}");
        
        for (String obj : objects) {
            if (obj.trim().isEmpty() || !obj.contains("{")) {
                continue;
            }
            
            // 移除开头的 {
            obj = obj.substring(obj.indexOf("{") + 1);
            
            // 解析对象属性
            String[] properties = obj.split(",");
            
            String identity = "";
            float x = 0, y = 0;
            
            for (String prop : properties) {
                prop = prop.trim();
                
                if (prop.startsWith("GameIdentity=")) {
                    identity = prop.substring(13);
                } else if (prop.startsWith("TransformComponent=")) {
                    String transformData = prop.substring(19);
                    String[] coords = transformData.split("\\|");
                    if (coords.length >= 2) {
                        try {
                            x = Float.parseFloat(coords[0]);
                            y = Float.parseFloat(coords[1]);
                        } catch (NumberFormatException e) {
                            // 忽略解析错误
                        }
                    }
                }
            }
            
            // 根据 identity 渲染不同的对象
            if ("Player".equals(identity)) {
                renderHuluBodyParts(x, y);
            } else if ("Enemy".equals(identity)) {
                renderEnemy(x, y);
            }
            // 可以添加更多对象类型的渲染
        }
    }

    public LinkedList<String> getMoves() {

        // 初始化 moves 列表
        LinkedList<String> movesList = new LinkedList<>();

        try (BufferedReader reader = new BufferedReader(new FileReader(recordingPath))) {

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
                movesList.add(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return movesList;

    }

    private void renderHuluBodyParts(float x, float y) {

        // 渲染葫芦身体上部 - 较小的圆
        renderer.drawCircle(
                x, y - 8, 6.0f, 32,
                1.0f, 0.0f, 0.0f, 1.0f // 红色
        );

        // 头顶小叶子（绿色叶片 + 茎）
        renderer.drawRect(
                x - 0.75f, y - 20.0f, 1.5f, 5.0f,
                0.10f, 0.45f, 0.10f, 1.0f // 绿色茎
        );

        renderer.drawCircle(
                x + 3.0f, y - 20.0f, 3.4f, 27,
                0.15f, 0.70f, 0.20f, 1.0f // 右叶片
        );

        // 渲染眼睛（白色眼白 + 黑色瞳孔）
        renderer.drawCircle(
                x - 3.0f, y - 10.0f, 1.8f, 16,
                1.0f, 1.0f, 1.0f, 1.0f // 白色
        );
        renderer.drawCircle(
                x + 3.0f, y - 10.0f, 1.8f, 16,
                1.0f, 1.0f, 1.0f, 1.0f // 白色
        );
        renderer.drawCircle(
                x - 3.0f, y - 10.0f, 0.8f, 12,
                0.0f, 0.0f, 0.0f, 1.0f // 黑色瞳孔
        );
        renderer.drawCircle(
                x + 3.0f, y - 10.0f, 0.8f, 12,
                0.0f, 0.0f, 0.0f, 1.0f // 黑色瞳孔
        );

        // 渲染嘴巴（细长矩形）
        renderer.drawRect(
                x - 2.0f, y - 7.0f, 4.0f, 2.0f,
                0.0f, 0.0f, 0.0f, 1.0f // 黑色
        );

        // 渲染葫芦身体下部 - 较大的圆
        renderer.drawCircle(
                x, y + 5, 10.0f, 32,
                1.0f, 0.0f, 0.0f, 1.0f // 红色
        );

    }
    
    /**
     * 渲染敌人士兵
     */
    private void renderEnemy(float x, float y) {
        // Torso (uniform)
        renderer.drawRect(x - 8f, y - 2f, 16f, 20f, 0.12f, 0.40f, 0.18f, 1f);

        // Head
        renderer.drawCircle(x, y - 14f, 6f, 24, 1.0f, 0.86f, 0.72f, 1.0f);

        // Helmet
        renderer.drawRect(x - 7f, y - 19f, 14f, 6f, 0.10f, 0.30f, 0.12f, 1.0f);
        renderer.drawRect(x - 7f, y - 14f, 14f, 2f, 0.08f, 0.25f, 0.10f, 1.0f);

        // Eyes
        renderer.drawCircle(x - 2.0f, y - 14.0f, 0.8f, 12, 0f, 0f, 0f, 1f);
        renderer.drawCircle(x + 2.0f, y - 14.0f, 0.8f, 12, 0f, 0f, 0f, 1f);

        // Arms (uniform)
        renderer.drawRect(x - 14f, y - 2f, 6f, 14f, 0.12f, 0.40f, 0.18f, 1f);
        renderer.drawRect(x + 8f, y - 2f, 6f, 14f, 0.12f, 0.40f, 0.18f, 1f);

        // Belt
        renderer.drawRect(x - 8f, y + 6f, 16f, 2f, 0.05f, 0.05f, 0.05f, 1f);

        // Legs (pants)
        renderer.drawRect(x - 6f, y + 12f, 6f, 12f, 0.10f, 0.35f, 0.15f, 1f);
        renderer.drawRect(x + 0f, y + 12f, 6f, 12f, 0.10f, 0.35f, 0.15f, 1f);

        // Boots
        renderer.drawRect(x - 6f, y + 22f, 6f, 3f, 0f, 0f, 0f, 1f);
        renderer.drawRect(x + 0f, y + 22f, 6f, 3f, 0f, 0f, 0f, 1f);

        // Rifle
        renderer.drawRect(x + 12f, y - 2f, 14f, 2f, 0.1f, 0.1f, 0.1f, 1f);
        renderer.drawRect(x + 12f, y + 0f, 3f, 6f, 0.1f, 0.1f, 0.1f, 1f);
    }

}
