package com.laker.postman.common.component;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;

import javax.swing.text.TabExpander;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.Token;
import org.fife.ui.rsyntaxtextarea.TokenPainter;

import com.laker.postman.util.FontsUtil;

/**
 * 一个完全通用的 TokenPainter，用于在 RSyntaxTextArea 中支持备用字体（fallback font）。
 *
 * <p>该实现不依赖任何具体的语法样式（Syntax Style），也不包含任何性能优化假设，
 * 仅保证在任何场景下都能正确、安全地绘制文本。
 *
 * <p>主要特性：
 * <ul>
 *   <li>从 client property 中读取 {@link EditorFontProperties#FALLBACK_FONT_CLIENT_PROPERTY}</li>
 *   <li>自动派生备用字体，使其与主字体在样式和字号上保持一致</li>
 *   <li>仅在主字体无法显示字符时才切换字体</li>
 *   <li>与 RSyntaxTextArea 的内建绘制逻辑保持高度一致</li>
 * </ul>
 *
 * <p>适用于 JSON、Markdown、Java、XML、PlainText 等所有语法类型。
 */
public class FallbackTokenPainter implements TokenPainter {

    // ------------------------------------------------------------------------
    // TokenPainter API
    // ------------------------------------------------------------------------

    @Override
    public float nextX(Token token, int charCount, float x, RSyntaxTextArea host, TabExpander e) {
        if (token == null || charCount <= 0) {
            return x;
        }

        Font primaryFont = host.getFontForToken(token);
        Font fallbackFont = resolveFallbackFont(host, primaryFont);

        FontMetrics primaryFm = host.getFontMetrics(primaryFont);
        FontMetrics fallbackFm = fallbackFont != null ? host.getFontMetrics(fallbackFont) : null;

        char[] text = token.getTextArray();
        int offset = token.getTextOffset();
        int limit = Math.min(offset + charCount, offset + token.length());

        return measure(text, offset, limit, primaryFont, primaryFm, fallbackFont, fallbackFm, e, x, host);
    }

    @Override
    public float paint(Token token, Graphics2D g, float x, float y, RSyntaxTextArea host, TabExpander e) {
        return paint(token, g, x, y, host, e, 0);
    }

    @Override
    public float paint(Token token, Graphics2D g, float x, float y, RSyntaxTextArea host,
                       TabExpander e, float clipStart) {
        return paintImpl(token, g, x, y, host, e, clipStart, true, false);
    }

    @Override
    public float paint(Token token, Graphics2D g, float x, float y, RSyntaxTextArea host,
                       TabExpander e, float clipStart, boolean paintBG) {
        return paintImpl(token, g, x, y, host, e, clipStart, paintBG, false);
    }

    @Override
    public float paintSelected(Token token, Graphics2D g, float x, float y, RSyntaxTextArea host,
                               TabExpander e, boolean useSTC) {
        return paintSelected(token, g, x, y, host, e, 0, useSTC);
    }

    @Override
    public float paintSelected(Token token, Graphics2D g, float x, float y, RSyntaxTextArea host,
                               TabExpander e, float clipStart, boolean useSTC) {
        return paintImpl(token, g, x, y, host, e, clipStart, false, useSTC);
    }

    // ------------------------------------------------------------------------
    // Core painting logic
    // ------------------------------------------------------------------------

    private float paintImpl(Token token, Graphics2D g, float x, float y,
                            RSyntaxTextArea host, TabExpander e,
                            float clipStart, boolean paintBG, boolean selected) {

        if (token == null || !token.isPaintable()) {
            return x;
        }

        Font primaryFont = host.getFontForToken(token);
        Font fallbackFont = resolveFallbackFont(host, primaryFont);

        FontMetrics primaryFm = host.getFontMetrics(primaryFont);
        FontMetrics fallbackFm = fallbackFont != null ? host.getFontMetrics(fallbackFont) : null;

        char[] text = token.getTextArray();
        int offset = token.getTextOffset();
        int limit = offset + token.length();

        Color fg = selected ? host.getSelectedTextColor() : host.getForegroundForToken(token);
        Color bg = paintBG ? host.getBackgroundForToken(token) : null;
        boolean underline = host.getUnderlineForToken(token);

        g.setColor(fg);

        float currentX = x;
        int runStart = offset;
        Font runFont = selectFont(text, offset, primaryFont, fallbackFont);
        FontMetrics runFm = runFont == primaryFont ? primaryFm : fallbackFm;

        for (int i = offset; i < limit; ) {
            int codePoint = codePointAt(text, i, limit);
            int charCount = Character.charCount(codePoint);

            Font nextFont = selectFont(text, i, primaryFont, fallbackFont);
            FontMetrics nextFm = nextFont == primaryFont ? primaryFm : fallbackFm;

            if (nextFont != runFont) {
                currentX = flushRun(g, text, runStart, i, currentX, y,
                        runFont, runFm, fg, bg, underline, host, e);
                runStart = i;
                runFont = nextFont;
                runFm = nextFm;
            }
            i += charCount;
        }

        return flushRun(g, text, runStart, limit, currentX, y,
                runFont, runFm, fg, bg, underline, host, e);
    }

    private float flushRun(Graphics2D g, char[] text, int start, int end,
                           float x, float y,
                           Font font, FontMetrics fm,
                           Color fg, Color bg, boolean underline,
                           RSyntaxTextArea host, TabExpander e) {

        if (start >= end) {
            return x;
        }

        float width = measure(text, start, end, font, fm, null, null, e, x, host);

        if (bg != null) {
            g.setColor(bg);
            g.fillRect((int) x, (int) (y - fm.getAscent()),
                    (int) (width - x), host.getLineHeight());
            g.setColor(fg);
        }

        g.setFont(font);
        g.drawChars(text, start, end - start, (int) x, (int) y);

        if (underline) {
            g.drawLine((int) x, (int) y + 1, (int) width, (int) y + 1);
        }

        return width;
    }

    // ------------------------------------------------------------------------
    // Measurement
    // ------------------------------------------------------------------------

    private float measure(char[] text, int start, int end,
                          Font primaryFont, FontMetrics primaryFm,
                          Font fallbackFont, FontMetrics fallbackFm,
                          TabExpander e, float x, RSyntaxTextArea host) {

        float currentX = x;
        int runStart = start;
        Font runFont = selectFont(text, start, primaryFont, fallbackFont);
        FontMetrics runFm = runFont == primaryFont ? primaryFm : fallbackFm;

        for (int i = start; i < end; ) {
            char ch = text[i];

            if (ch == '\t') {
                currentX += runFm.charsWidth(text, runStart, i - runStart);
                currentX = e.nextTabStop(currentX, 0);
                runStart = i + 1;
                i++;
                continue;
            }

            int codePoint = codePointAt(text, i, end);
            int charCount = Character.charCount(codePoint);

            Font nextFont = selectFont(text, i, primaryFont, fallbackFont);
            FontMetrics nextFm = nextFont == primaryFont ? primaryFm : fallbackFm;

            if (nextFont != runFont) {
                currentX += runFm.charsWidth(text, runStart, i - runStart);
                runStart = i;
                runFont = nextFont;
                runFm = nextFm;
            }

            i += charCount;
        }

        currentX += runFm.charsWidth(text, runStart, end - runStart);
        return currentX;
    }

    // ------------------------------------------------------------------------
    // Font resolution & selection
    // ------------------------------------------------------------------------

    private Font selectFont(char[] text, int index, Font primaryFont, Font fallbackFont) {
        int codePoint = codePointAt(text, index, text.length);

        if (canDisplay(primaryFont, codePoint)) {
            return primaryFont;
        }
        if (fallbackFont != null && canDisplay(fallbackFont, codePoint)) {
            return fallbackFont;
        }
        // 最终兜底：JVM 逻辑字体（系统级 fallback）
        return FontsUtil.getDefaultFontWithOffset(Font.PLAIN, 0);
    }

    private boolean canDisplay(Font font, int codePoint) {
        try {
            return font != null && font.canDisplay(codePoint);
        } catch (Exception ignored) {
            return false;
        }
    }

    private Font resolveFallbackFont(RSyntaxTextArea host, Font primaryFont) {
        Object value = host.getClientProperty(EditorFontProperties.FALLBACK_FONT_CLIENT_PROPERTY);
        if (!(value instanceof Font fallback) || primaryFont == null) {
            return null;
        }

        Font derived = fallback.deriveFont(primaryFont.getStyle(), primaryFont.getSize2D());
        if (samePhysicalFont(primaryFont, derived)) {
            return null;
        }
        return derived;
    }

    private boolean samePhysicalFont(Font f1, Font f2) {
        return f1.getFamily().equals(f2.getFamily())
                && f1.getStyle() == f2.getStyle()
                && f1.getSize() == f2.getSize();
    }

    // ------------------------------------------------------------------------
    // UTF-16 helpers
    // ------------------------------------------------------------------------

    private int codePointAt(char[] text, int index, int limit) {
        char c1 = text[index];
        if (Character.isHighSurrogate(c1) && index + 1 < limit) {
            char c2 = text[index + 1];
            if (Character.isLowSurrogate(c2)) {
                return Character.toCodePoint(c1, c2);
            }
        }
        return c1;
    }
}