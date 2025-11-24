package com.gameengine.app;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import com.gameengine.core.GameEngine;
import com.gameengine.graphics.IRenderer;
import com.gameengine.input.InputManager;
import com.gameengine.scene.Scene;

/**
 * 回放菜单场景
 * 显示录制文件列表，允许用户选择要回放的文件
 *M (当前为简化版本，完整功能需要recording包支持)
 */
public class ReplayScene extends Scene {

    private GameEngine engine;
    private IRenderer renderer;
    private InputManager inputManager;
    @SuppressWarnings("unused")
    private String recordingPath;
    private static final String RECORDING_PATH = "recordings";
    private File recordingFolder;
    private boolean hasRecording;
    
    public ReplayScene(GameEngine engine, String recordingPath) {
        super("ReplayMenu");
        this.engine = engine;
        this.recordingPath = recordingPath;
    }
    
    @Override
    public void initialize() {
        super.initialize();
        this.renderer = engine.getRenderer();
        this.inputManager = engine.getInputManager();

        // 初始化录制文件列表
        this.recordingFiles = new ArrayList<>();

        // 在初始化阶段即读取录制文件并设置游戏逻辑
        recordingFolder = new File(RECORDING_PATH);

        if (recordingPath != null && !recordingPath.isEmpty()) {
            loadRecording(recordingPath);
            return;
        }

        if (!recordingFolder.exists() || !recordingFolder.isDirectory()) {
            System.err.println("录制文件夹不存在: " + RECORDING_PATH);
            hasRecording = false;
        } else {
            // 加载录制文件列表
            File[] files = recordingFolder.listFiles();
            if (files != null && files.length > 0) {
                for (File file : files) {
                    if (file.isFile() && file.getName().endsWith(".txt")) {
                        recordingFiles.add(file);
                    }
                }
                System.out.println("已成功读取" + recordingFiles.size() + "个录制文件");
                hasRecording = !recordingFiles.isEmpty();
            } else {
                hasRecording = false;
            }
        }
    }
    
    @Override
    public void update(float deltaTime) {
        super.update(deltaTime);
        
        // 如果有录制文件，处理文件选择
        if (hasRecording && recordingFiles != null && !recordingFiles.isEmpty()) {
            handleFileSelection();
            return;
        }
        
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
        
        if (!hasRecording) {
            renderer.drawText("没有录制文件记录", 100.f, 100.f, 20.f, 1.0f, 1.0f, 1.0f, 1.0f);
        } else {
            renderFileList();
        }
    
    }

    // 文档列表渲染
    private List<File> recordingFiles;
    private int selectedIndex = 0;

    private void handleFileSelection() {

        // 键盘选择
        if (inputManager.isKeyJustPressed(38) || inputManager.isKeyJustPressed(265)) { // up (AWT 38 / GLFW 265)
            selectedIndex = (selectedIndex - 1 + Math.max(1, recordingFiles.size())) % Math.max(1, recordingFiles.size());
        } else if (inputManager.isKeyJustPressed(40) || inputManager.isKeyJustPressed(264)) { // down (AWT 40 / GLFW 264)
            selectedIndex = (selectedIndex + 1) % Math.max(1, recordingFiles.size());
        } else if (inputManager.isKeyJustPressed(10) || inputManager.isKeyJustPressed(32) || inputManager.isKeyJustPressed(257) || inputManager.isKeyJustPressed(335)) { // enter/space (AWT 10/32, GLFW 257/335)
            if (recordingFiles.size() > 0) {
                String path = recordingFiles.get(selectedIndex).getAbsolutePath();
                this.recordingPath = path;
                clear();
                initialize();
            }
        } else if (inputManager.isKeyJustPressed(27)) { // esc
            engine.setScene(new MenuScene(engine, "MainMenu"));
        }
        
        // 鼠标选择逻辑
        float mouseX = inputManager.getMouseX();
        float mouseY = inputManager.getMouseY();
        
        float startY = 140f;
        float itemH = 28f;
        float x = 100f;
        
        // 检测鼠标悬停在哪个文件上
        for (int i = 0; i < recordingFiles.size(); i++) {
            float y = startY + i * itemH;
            // 检测鼠标是否在这个文件区域内（x: 90-690, y: y-6 到 y+18）
            if (mouseX >= x - 10 && mouseX <= x + 600 && 
                mouseY >= y - 6 && mouseY <= y + 18) {
                selectedIndex = i;
                
                // 如果点击左键，加载该录制文件
                if (inputManager.isMouseButtonJustPressed(0)) { // 0 = 左键
                    String path = recordingFiles.get(selectedIndex).getAbsolutePath();
                    this.recordingPath = path;
                    clear();
                    initialize();
                }
                break;
            }
        }
    }

    private void renderFileList() {

        int w = renderer.getWidth();
        int h = renderer.getHeight();
        String title = "SELECT RECORDING";
        float tw = title.length() * 16f;
        renderer.drawText(title, w/2f - tw/2f, 80, 16f, 1f, 1f, 1f, 1f);

        if (recordingFiles.isEmpty()) {
            String none = "NO RECORDINGS FOUND";
            float nw = none.length() * 14f;
            renderer.drawText(none, w/2f - nw/2f, h/2f, 14f, 0.9f, 0.8f, 0.2f, 1f);
            String back = "ESC TO RETURN";
            float bw = back.length() * 12f;
            renderer.drawText(back, w/2f - bw/2f, h - 60, 12f, 0.7f, 0.7f, 0.7f, 1f);
            return;
        }

        float startY = 140f;
        float itemH = 28f;
        for (int i = 0; i < recordingFiles.size(); i++) {
            String name = recordingFiles.get(i).getName();
            float x = 100f;
            float y = startY + i * itemH;
            if (i == selectedIndex) {
                renderer.drawRect(x - 10, y - 6, 600, 24, 0.3f,0.3f,0.4f,0.8f);
            }
            renderer.drawText(name, x, y, 14f, 0.9f, 0.9f, 0.9f, 1f);
        }

        String hint = "UP/DOWN SELECT, ENTER PLAY, ESC RETURN";
        float hw = hint.length() * 12f;
        renderer.drawText(hint, w/2f - hw/2f, h - 60, 12f, 0.7f, 0.7f, 0.7f, 1f);
    }


    public void loadRecording(String recordingFilePath) {
        Scene recordingScene = new RecordingScene(engine, recordingFilePath);
        engine.setScene(recordingScene);
    }
    
}
