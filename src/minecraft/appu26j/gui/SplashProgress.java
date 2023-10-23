package appu26j.gui;

import java.awt.Color;

import org.lwjgl.opengl.GL11;

import appu26j.Scale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.client.shader.Framebuffer;
import net.minecraft.util.ResourceLocation;

public class SplashProgress {
    private static final int MAX = 4;
    private static int PROGRESS = 0;
    private static String CURRENT = "";
    private static ResourceLocation splash;

    public static void update() {
        if (Minecraft.getMinecraft() == null || Minecraft.getMinecraft().getLanguageManager() == null) {
            return;
        }
        
        drawSplash(Minecraft.getMinecraft().getTextureManager());
    }
    
    public static void setProgress(int givenProgress, String givenText) {
        PROGRESS = givenProgress;
        CURRENT = givenText;
        update();
    }
    
    public static void drawSplash(TextureManager tm) { 
        ScaledResolution scaledResolution = Scale.getSR();
        int scaleFactor = scaledResolution.getScaleFactor();
        
        Framebuffer framebuffer = new Framebuffer(scaledResolution.getScaledWidth() * scaleFactor, scaledResolution.getScaledHeight() * scaleFactor, true);
        framebuffer.bindFramebuffer(false);

        GlStateManager.matrixMode(GL11.GL_PROJECTION);
        GlStateManager.loadIdentity();
        GlStateManager.ortho(0.0D, (double)scaledResolution.getScaledWidth(), (double)scaledResolution.getScaledHeight(), 0.0D, 1000.0D, 3000.0D);
        GlStateManager.matrixMode(GL11.GL_MODELVIEW);
        GlStateManager.loadIdentity();
        GlStateManager.translate(0.0F, 0.0F, -2000.0F);
        GlStateManager.disableLighting();
        GlStateManager.disableFog();
        GlStateManager.disableDepth();
        GlStateManager.enableTexture2D();
        
        if (splash == null) {
            splash = new ResourceLocation("panorama.png");
        }
        
        tm.bindTexture(splash);
        GlStateManager.resetColor();
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.enableAlpha();
        GlStateManager.enableBlend();
        GlStateManager.alphaFunc(516, 0.1F);
        Gui.drawModalRectWithCustomSizedTexture(0, 0, 0, 0, scaledResolution.getScaledWidth(), scaledResolution.getScaledHeight(), 480, 238);
        tm.bindTexture(new ResourceLocation("icons/icon_32x32.png"));
        Gui.drawModalRectWithCustomSizedTexture((scaledResolution.getScaledWidth() / 2) - 48, (scaledResolution.getScaledHeight() / 2) - 64, 0, 0, 96, 96, 96, 96);
        drawProgress();
        framebuffer.unbindFramebuffer();
        framebuffer.framebufferRender(scaledResolution.getScaledWidth() * scaleFactor, scaledResolution.getScaledHeight() * scaleFactor);
        Minecraft.getMinecraft().updateDisplay();
    }
    
    private static void drawProgress() {
        if (Minecraft.getMinecraft().gameSettings == null || Minecraft.getMinecraft().getTextureManager() == null) {
            return;
        }
        
        ScaledResolution sr = Scale.getSR();
        
        double nProgress = (double)PROGRESS;
        double calc = (nProgress / MAX) * sr.getScaledWidth();
        
        Gui.drawRect(0, sr.getScaledHeight() - 35, sr.getScaledWidth(), sr.getScaledHeight(), new Color(0, 0, 0, 50).getRGB());
        
        GlStateManager.resetColor();
        resetTextureState();
        
        
        Minecraft.getMinecraft().fontRendererObj.drawString(CURRENT, 15, sr.getScaledHeight() - 22, -1);
        
        String step = PROGRESS + "/" + MAX;
        Minecraft.getMinecraft().fontRendererObj.drawString(step, sr.getScaledWidth() - 15 - Minecraft.getMinecraft().fontRendererObj.getStringWidth(step), sr.getScaledHeight() - 22, new Color(200, 200, 200).getRGB());
        
        GlStateManager.resetColor();
        resetTextureState();
        
        Gui.drawRect(0, sr.getScaledHeight() - 2, (int)calc, sr.getScaledHeight(), new Color(149, 201, 144).getRGB());
        Gui.drawRect(0, sr.getScaledHeight() - 2, sr.getScaledWidth(), sr.getScaledHeight(), new Color(0, 0, 10).getRGB());
    }
    
    private static void resetTextureState() {
        GlStateManager.textureState[GlStateManager.activeTextureUnit].textureName = -1;
    }
}
