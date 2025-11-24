package com.gameengine.core;

import com.gameengine.graphics.IRenderer;

/**
 * 简单的FPS计数器 - 用于测量游戏帧率
 */
public class GamePerformance {
    private int frameCount = 0;
    private float elapsedTime = 0.0f;
    private float currentFPS = 0.0f;

    // 用于计算整体平均帧率
    private int totalFrameCount = 0;
    private float totalElapsedTime = 0.0f;

    /**
     * 每帧调用此方法更新FPS
     * @param deltaTime 帧间隔时间（秒）
     */
    public void update(float deltaTime) {
        frameCount++;
        totalFrameCount++;
        elapsedTime += deltaTime;
        totalElapsedTime += deltaTime;

        // 每秒计算一次FPS
        if (elapsedTime >= 1.0f) {
            currentFPS = frameCount / elapsedTime;
            frameCount = 0;
            elapsedTime = 0.0f;

            // 打印到控制台
            System.out.printf("FPS: %.1f\n", currentFPS);
        }
    }

    /**
     * 获取当前FPS
     */
    public float getFPS() {
        return currentFPS;
    }

    /**
     * 在屏幕上显示FPS（屏幕左上角，生命条下方）
     */
    public void render(IRenderer renderer) {
        // 先绘制半透明背景
        renderer.drawRect(20, 50, 100, 20, 0.0f, 0.0f, 0.0f, 0.7f);
        // 在屏幕左上角显示FPS文字，位于生命条下方
        renderer.drawText(String.format("FPS: %.1f", currentFPS), 25, 65,
                         14, 1.0f, 1.0f, 1.0f, 1.0f);
    }

    /**
     * 计算并打印整体平均帧率（游戏结束时调用）
     */
    public void printSummary() {
        if (totalElapsedTime > 0) {
            float averageFPS = totalFrameCount / totalElapsedTime;
            System.out.println("\n================================");
            System.out.println("        游戏性能统计");
            System.out.println("================================");
            System.out.printf("总运行时间: %.2f 秒\n", totalElapsedTime);
            System.out.printf("总帧数: %d 帧\n", totalFrameCount);
            System.out.printf("平均帧率: %.2f FPS\n", averageFPS);
            System.out.println("================================\n");
        }
    }

}
