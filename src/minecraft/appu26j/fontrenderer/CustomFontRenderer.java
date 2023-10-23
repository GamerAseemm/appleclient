package appu26j.fontrenderer;

import java.awt.Font;
import java.util.ArrayList;

import org.lwjgl.opengl.GL11;

import appu26j.interfaces.MinecraftInterface;
import appu26j.utils.Fonts;
import net.minecraft.client.gui.ScaledResolution; import appu26j.Scale;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;

public class CustomFontRenderer extends CFont implements MinecraftInterface
{
    CharData[] boldChars;
    CharData[] italicChars;
    CharData[] boldItalicChars;
    int[] colorCode;
    String colorcodeIdentifiers;
    DynamicTexture texBold;
    DynamicTexture texItalic;
    DynamicTexture texItalicBold;
    int previousScaleFactor = 0;
    
    public CustomFontRenderer(final Font font, final boolean antiAlias, final boolean fractionalMetrics) {
        super(font, antiAlias, fractionalMetrics);
        ScaledResolution scaledResolution = Scale.getSR();
        this.previousScaleFactor = scaledResolution.getScaleFactor();
        this.boldChars = new CharData[256];
        this.italicChars = new CharData[256];
        this.boldItalicChars = new CharData[256];
        this.colorCode = new int[32];
        this.colorcodeIdentifiers = "0123456789abcdefklmnor";
        this.setupMinecraftColorcodes();
        this.setupBoldItalicIDs();
    }
    
    public int drawStringWithShadow(final String text, final double x2, final float y2, final int color) {
        float fontSize = this.font.getSize() / this.previousScaleFactor;
        final float shadowWidth = this.drawString(text, x2 + (fontSize / 8), y2 + (fontSize / 8), color, true, 8.0f);
        return (int)Math.max(shadowWidth, this.drawString(text, x2, y2, color, false, 8.0f));
    }
    
    public int drawString(final String text, final double x2, final float y2, final int color) {
        return (int)this.drawString(text, x2, y2, color, false, 8.0f);
    }
    
    public int drawPassword(final String text, final double x2, final float y2, final int color) {
        return (int)this.drawString(text.replaceAll(".", "."), x2, y2, color, false, 8.0f);
    }
    
    public double getPasswordWidth(final String text) {
        return this.getStringWidth(text.replaceAll(".", "."), 8.0f);
    }
    
    public float drawCenteredString(final String text, final float x2, final float y2, final int color) {
        return (float)this.drawString(text, x2 - (float)(this.getStringWidth(text) / 2.0), y2, color);
    }
    
    public float drawCenteredStringWithShadow(final String text, final float x2, final float y2, final int color) {
        return (float)this.drawStringWithShadow(text, x2 - (float)(this.getStringWidth(text) / 2.0), y2, color);
    }
    
    public float drawString(final String text, double x, double y, int color, final boolean shadow, final float kerning) {
        if (text == null || text.isEmpty())
        {
            return 0;
        }
        
        ScaledResolution scaledResolution = Scale.getSR();

        if (this.previousScaleFactor != scaledResolution.getScaleFactor())
        {
            this.previousScaleFactor = scaledResolution.getScaleFactor();
            Fonts.initialize();
            return 0;
        }
        
        if ((color & -67108864) == 0)
        {
            color |= -16777216;
        }
        
        if (shadow)
        {
            color = (color & 16579836) >> 2 | color & -16777216;
        }
        
        CharData[] currentData = this.charData;
        final float alpha = (color >> 24 & 255) / 255.0F;
        boolean randomCase = false;
        boolean bold = false;
        boolean italic = false;
        boolean strikethrough = false;
        boolean underline = false;
        x *= this.previousScaleFactor;
        y *= this.previousScaleFactor;
        y -= this.font.getSize() / 3;
        GL11.glPushMatrix();
        GlStateManager.scale(1F / this.previousScaleFactor, 1F / this.previousScaleFactor, 1F / this.previousScaleFactor);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(770, 771);
        GlStateManager.color((color >> 16 & 255) / 255.0F, (color >> 8 & 255) / 255.0F, (color & 255) / 255.0F, alpha);
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(this.tex.getGlTextureId());
        ArrayList<String> unallowedLetters = new ArrayList<>();
        String previousColorCode = "";
        
        for (int index = 0; index < text.length(); ++index) {
            char character = text.charAt(index);
            
            if (!isCharAllowed(character))
            {
                String thing = previousColorCode + String.valueOf(character) + " IloveAppleClientt " + x + "," + y;
                unallowedLetters.add(thing);
                x += mc.fontRendererObj.getStringWidthNoCustomFont(String.valueOf(character)) * previousScaleFactor;
                continue;
            }
            
            if (character == '�') {
                int colorIndex = 21;
                try {
                    colorIndex = "0123456789abcdefklmnor".indexOf(text.charAt(index + 1));
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                if (colorIndex < 16) {
                    bold = false;
                    italic = false;
                    randomCase = false;
                    underline = false;
                    strikethrough = false;
                    GlStateManager.bindTexture(this.tex.getGlTextureId());
                    currentData = this.charData;
                    if (colorIndex < 0) {
                        colorIndex = 15;
                    }
                    if (shadow) {
                        colorIndex += 16;
                    }
                    int colorcode = this.colorCode[colorIndex];
                    color = colorcode;
                    GlStateManager.color((colorcode >> 16 & 255) / 255.0F, (colorcode >> 8 & 255) / 255.0F, (colorcode & 255) / 255.0F, alpha);
                }
                else if (colorIndex == 16) {
                    randomCase = true;
                }
                else if (colorIndex == 17) {
                    bold = true;
                    if (italic) {
                        GlStateManager.bindTexture(this.texItalicBold.getGlTextureId());
                        currentData = this.boldItalicChars;
                    }
                    else {
                        GlStateManager.bindTexture(this.texBold.getGlTextureId());
                        currentData = this.boldChars;
                    }
                }
                else if (colorIndex == 18) {
                    strikethrough = true;
                }
                else if (colorIndex == 19) {
                    underline = true;
                }
                else if (colorIndex == 20) {
                    italic = true;
                    if (bold) {
                        GlStateManager.bindTexture(this.texItalicBold.getGlTextureId());
                        currentData = this.boldItalicChars;
                    }
                    else {
                        GlStateManager.bindTexture(this.texItalic.getGlTextureId());
                        currentData = this.italicChars;
                    }
                }
                else {
                    bold = false;
                    italic = false;
                    randomCase = false;
                    underline = false;
                    strikethrough = false;
                    GlStateManager.color((color >> 16 & 0xFF) / 255.0f, (color >> 8 & 0xFF) / 255.0f, (color & 0xFF) / 255.0f, alpha);
                    GlStateManager.bindTexture(this.tex.getGlTextureId());
                    currentData = this.charData;
                }
                previousColorCode = String.valueOf(text.charAt(index + 1));
                ++index;
            }
            else if (character < currentData.length) {
                GL11.glBegin(4);
                this.drawChar(currentData, character, (float)x, (float)y);
                GL11.glEnd();
                if (strikethrough) {
                    this.drawLine(x, y + currentData[character].height / 2, x + currentData[character].width - 8.0, y + currentData[character].height / 2, 1.0f);
                }
                if (underline) {
                    this.drawLine(x, y + currentData[character].height - 2.0, x + currentData[character].width - 8.0, y + currentData[character].height - 2.0, 1.0f);
                }
                x += currentData[character].width - kerning + this.charOffset;
            }
        }
        GlStateManager.popMatrix();
        GlStateManager.color(1, 1, 1, 1);
        
        if (!shadow)
        {
            for (int i = 0; i < unallowedLetters.size(); i++)
            {
                try
                {
                    String temp = unallowedLetters.get(i);
                    String name = temp.split(" IloveAppleClientt ")[0];
                    String positionTemp = temp.substring(temp.split(" IloveAppleClientt ").length);
                    positionTemp = positionTemp.substring(" IloveAppleClientt ".length());
                    float xPos = Float.parseFloat(positionTemp.split(",")[0]);
                    float yPos = Float.parseFloat(positionTemp.split(",")[1]);
                    String doubleS = previousColorCode.isEmpty() ? "" : "�";
                    mc.fontRendererObj.renderString(doubleS + name, xPos / previousScaleFactor + 1, yPos / previousScaleFactor + 3, color, false);
                }
                
                catch (Exception e)
                {
                    ;
                }
            }
        }
        
        return (float) (x / this.previousScaleFactor);
    }
    
    public int getStringWidth(final String text) {
        return this.getStringWidth(text, 8);
    }
    
    public int getStringWidth(String text, float kerning) {
        if (text == null || text.isEmpty())
        {
            return 0;
        }
        
        float width = 0;
        boolean bold = false;
        
        for (int index = 0; index < text.length(); index++)
        {
            char c = text.charAt(index);
            
            if (!isCharAllowed(c))
            {
                width += mc.fontRendererObj.getStringWidthNoCustomFont(String.valueOf(c)) * previousScaleFactor;
                continue;
            }
            
            if (c == '�' && (index + 1) < text.length())
            {
                if (text.charAt(index + 1) == 'l')
                {
                    bold = true;
                }
                
                else
                {
                    bold = false;
                }
                
                index++;
            }
            
            else if (c < this.charData.length)
            {
                width += this.charData[c].width - kerning;
                width += bold ? 1.5F : 0;
            }
        }
        
        float fontSize = this.font.getSize() / this.previousScaleFactor;
        return (int) ((width / this.previousScaleFactor) + (fontSize / 3));
    }
    
    @Override
    public void setFont(final Font font) {
        super.setFont(font);
        this.setupBoldItalicIDs();
    }
    
    @Override
    public void setAntiAlias(final boolean antiAlias) {
        super.setAntiAlias(antiAlias);
        this.setupBoldItalicIDs();
    }
    
    @Override
    public void setFractionalMetrics(final boolean fractionalMetrics) {
        super.setFractionalMetrics(fractionalMetrics);
        this.setupBoldItalicIDs();
    }
    
    private void setupBoldItalicIDs() {
        this.texBold = this.setupTexture(this.font.deriveFont(1), this.antiAlias, this.fractionalMetrics, this.boldChars);
        this.texItalic = this.setupTexture(this.font.deriveFont(2), this.antiAlias, this.fractionalMetrics, this.italicChars);
        this.texItalicBold = this.setupTexture(this.font.deriveFont(3), this.antiAlias, this.fractionalMetrics, this.boldItalicChars);
    }
    
    private void drawLine(final double x2, final double y2, final double x1, final double y1, final float width) {
        GL11.glDisable(3553);
        GL11.glLineWidth(width);
        GL11.glBegin(1);
        GL11.glVertex2d(x2, y2);
        GL11.glVertex2d(x1, y1);
        GL11.glEnd();
        GL11.glEnable(3553);
    }
    
    public ArrayList<String> wrapWords(final String text, final double width) {
        final ArrayList<String> finalWords = new ArrayList<String>();
        if (this.getStringWidth(text) > width) {
            final String[] words = text.split(" ");
            StringBuilder currentWord = new StringBuilder();
            char lastColorCode = '\uffff';
            final String[] stringArray = words;
            for (int n = words.length, n2 = 0; n2 < n; ++n2) {
                final String word = stringArray[n2];
                for (int innerIndex = 0; innerIndex < word.toCharArray().length; ++innerIndex) {
                    final char c = word.toCharArray()[innerIndex];
                    if (c == '�' && innerIndex < word.toCharArray().length - 1) {
                        lastColorCode = word.toCharArray()[innerIndex + 1];
                    }
                }
                final StringBuilder stringBuilder = new StringBuilder();
                if (this.getStringWidth(stringBuilder.append((Object)currentWord).append(word).append(" ").toString()) < width) {
                    currentWord.append(word).append(" ");
                }
                else {
                    finalWords.add(currentWord.toString());
                    currentWord = new StringBuilder("�" + lastColorCode + word + " ");
                }
            }
            if (currentWord.length() > 0) {
                if (this.getStringWidth(currentWord.toString()) < width) {
                    finalWords.add("�" + lastColorCode + (Object)currentWord + " ");
                    currentWord = new StringBuilder();
                }
                else {
                    finalWords.addAll(this.formatString(currentWord.toString(), width));
                }
            }
        }
        else {
            finalWords.add(text);
        }
        return finalWords;
    }
    
    public ArrayList<String> formatString(final String string, final double width) {
        final ArrayList<String> finalWords = new ArrayList<String>();
        StringBuilder currentWord = new StringBuilder();
        char lastColorCode = '\uffff';
        final char[] chars = string.toCharArray();
        for (int index = 0; index < chars.length; ++index) {
            final char c = chars[index];
            if (c == '�' && index < chars.length - 1) {
                lastColorCode = chars[index + 1];
            }
            final StringBuilder stringBuilder = new StringBuilder(String.valueOf(currentWord.toString()));
            if (this.getStringWidth(stringBuilder.append(c).toString()) < width) {
                currentWord.append(c);
            }
            else {
                finalWords.add(currentWord.toString());
                currentWord = new StringBuilder("�" + lastColorCode + c);
            }
        }
        if (currentWord.length() > 0) {
            finalWords.add(currentWord.toString());
        }
        return finalWords;
    }
    
    private void setupMinecraftColorcodes() {
        for (int index = 0; index < 32; ++index) {
            final int noClue = (index >> 3 & 0x1) * 85;
            int red = (index >> 2 & 0x1) * 170 + noClue;
            int green = (index >> 1 & 0x1) * 170 + noClue;
            int blue = (index & 0x1) * 170 + noClue;
            if (index == 6) {
                red += 85;
            }
            if (index >= 16) {
                red /= 4;
                green /= 4;
                blue /= 4;
            }
            this.colorCode[index] = ((red & 0xFF) << 16 | (green & 0xFF) << 8 | (blue & 0xFF));
        }
    }
    
    public String trimStringToWidth(final String text, final int width) {
        return this.trimStringToWidth(text, width, false);
    }
    
    public String trimStringToWidthPassword(String text, final int width, final boolean custom) {
        text = text.replaceAll(".", ".");
        return this.trimStringToWidth(text, width, custom);
    }
    
    private float getCharWidthFloat(final char c) {
        if (c == '�') {
            return -1.0f;
        }
        if (c == ' ') {
            return 2.0f;
        }
        final int var2 = "\u00c0\u00c1\u00c2\u00c8\u00ca\u00cb\u00cd\u00d3\u00d4\u00d5\u00da\u00df\u00e3\u00f5\u011f\u0130\u0131\u0152\u0153\u015e\u015f\u0174\u0175\u017e\u0207\u0000\u0000\u0000\u0000\u0000\u0000\u0000 !\"#$%&'()*+,-./0123456789:;<=>?@ABCDEFGHIJKLMNOPQRSTUVWXYZ[\\]^_`abcdefghijklmnopqrstuvwxyz{|}~\u0000\u00c7\u00fc\u00e9\u00e2\u00e4\u00e0\u00e5\u00e7\u00ea\u00eb\u00e8\u00ef\u00ee\u00ec\u00c4\u00c5\u00c9\u00e6\u00c6\u00f4\u00f6\u00f2\u00fb\u00f9\u00ff\u00d6\u00dc\u00f8�\u00d8\u00d7\u0192\u00e1\u00ed\u00f3\u00fa\u00f1\u00d1����������\u2591\u2592\u2593\u2502\u2524\u2561\u2562\u2556\u2555\u2563\u2551\u2557\u255d\u255c\u255b\u2510\u2514\u2534\u252c\u251c\u2500\u253c\u255e\u255f\u255a\u2554\u2569\u2566\u2560\u2550\u256c\u2567\u2568\u2564\u2565\u2559\u2558\u2552\u2553\u256b\u256a\u2518\u250c\u2588\u2584\u258c\u2590\u2580\u03b1\u03b2\u0393\u03c0\u03a3\u03c3\u03bc\u03c4\u03a6\u0398\u03a9\u03b4\u221e\u2205\u2208\u2229\u2261�\u2265\u2264\u2320\u2321\u00f7\u2248�\u2219�\u221a\u207f�\u25a0\u0000".indexOf(c);
        if (c > '\0' && var2 != -1) {
            return this.charData[var2].width / 2.0f - 4.0f;
        }
        if (this.charData[c].width / 2.0f - 4.0f != 0.0f) {
            int var3 = (int)(this.charData[c].width / 2.0f - 4.0f) >>> 4;
            int var4 = (int)(this.charData[c].width / 2.0f - 4.0f) & 0xF;
            return (float)((++var4 - (var3 &= 0xF)) / 2 + 1);
        }
        return 0.0f;
    }
    
    public String trimStringToWidth(final String text, final int width, final boolean custom) {
        final StringBuilder buffer = new StringBuilder();
        float lineWidth = 0.0f;
        final int offset = custom ? (text.length() - 1) : 0;
        final int increment = custom ? -1 : 1;
        boolean var8 = false;
        boolean var9 = false;
        for (int index = offset; index >= 0 && index < text.length() && lineWidth < width; index += increment) {
            final char character = text.charAt(index);
            final float charWidth = this.getCharWidthFloat(character);
            if (var8) {
                var8 = false;
                if (character != 'l' && character != 'L') {
                    if (character == 'r' || character == 'R') {
                        var9 = false;
                    }
                }
                else {
                    var9 = true;
                }
            }
            else if (charWidth < 0.0f) {
                var8 = true;
            }
            else {
                lineWidth += charWidth;
                if (var9) {
                    ++lineWidth;
                }
            }
            if (lineWidth > width) {
                break;
            }
            if (custom) {
                buffer.insert(0, character);
            }
            else {
                buffer.append(character);
            }
        }
        return buffer.toString();
    }
    
    private boolean isCharAllowed(char character)
    {
        String[] allowedLetters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+-=[]{}|\\'\";:,.<>/?� ".split("");
        boolean allowed = false;
        
        for (String letter : allowedLetters)
        {
            if (letter.equals(String.valueOf(character)))
            {
                allowed = true;
                break;
            }
        }
        
        return allowed;
    }
}