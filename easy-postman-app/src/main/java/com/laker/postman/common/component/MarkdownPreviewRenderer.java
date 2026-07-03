package com.laker.postman.common.component;

import java.awt.Color;
import java.util.List;

import com.laker.postman.common.constants.ModernColors;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;

public class MarkdownPreviewRenderer {

    // 懒加载：仅在首次使用时创建 Parser 和 Renderer
    private static volatile Parser PARSER;
    private static volatile HtmlRenderer RENDERER;
    
    // 缓存完整的样式字符串，避免重复拼接
    private static volatile String CACHED_STYLES;
    // 缓存时的主题ID，用于检测主题切换
    private static volatile String CACHED_THEME_ID;

    /**
     * 获取 Parser 实例（懒加载 + 双重检查锁定）
     */
    private static Parser getParser() {
        if (PARSER == null) {
            synchronized (MarkdownPreviewRenderer.class) {
                if (PARSER == null) {
                    MutableDataSet options = new MutableDataSet();
                    options.set(Parser.EXTENSIONS, List.of(
                            TablesExtension.create(),              // ✅ 表格
                            StrikethroughExtension.create(),     // ✅ 删除线
                            TaskListExtension.create()           // ✅ 任务列表
                    ));
                    options.set(HtmlRenderer.ESCAPE_HTML, false);
                    options.set(HtmlRenderer.SUPPRESS_HTML, false);
                    PARSER = Parser.builder(options).build();
                }
            }
        }
        return PARSER;
    }

    /**
     * 获取 Renderer 实例（懒加载 + 双重检查锁定）
     */
    private static HtmlRenderer getRenderer() {
        if (RENDERER == null) {
            synchronized (MarkdownPreviewRenderer.class) {
                if (RENDERER == null) {
                    MutableDataSet options = new MutableDataSet();
                    options.set(Parser.EXTENSIONS, List.of(
                            TablesExtension.create(),
                            StrikethroughExtension.create(),
                            TaskListExtension.create()
                    ));
                    options.set(HtmlRenderer.ESCAPE_HTML, false);
                    options.set(HtmlRenderer.SUPPRESS_HTML, false);
                    RENDERER = HtmlRenderer.builder(options).build();
                }
            }
        }
        return RENDERER;
    }

    /**
     * 获取表格样式
     */
    public static String getTableStyle() {
        return "border-collapse:separate;border-spacing:0;width:100%;margin:0 0 16px 0;border-radius:8px;overflow:hidden;border:1px solid " + toHex(ModernColors.getBorderLightColor()) + ";box-shadow:0 1px 3px rgba(0,0,0,0.06);";
    }

    /**
     * 获取表格单元格样式
     */
    public static String getTableCellStyle() {
        return "padding:8px 12px;border:1px solid " + toHex(ModernColors.getBorderLightColor()) + ";vertical-align:top;";
    }

    /**
     * 获取表格表头样式
     */
    public static String getTableHeaderStyle() {
        String bgColor = toHex(ModernColors.getTableHeaderBackgroundColor());
        return getTableCellStyle() + "font-weight:600;background-color:" + bgColor + ";text-align:left;white-space:nowrap;color:" + toHex(ModernColors.getTextPrimary()) + ";";
    }

    /**
     * 获取表格行hover样式
     */
    public static String getTableRowHoverStyle() {
        String hoverBg = toHex(ModernColors.getHoverBackgroundColor());
        String stripeBg = toHex(ModernColors.getEmptyCellBackground());
        return "tr:nth-child(even){background-color:" + stripeBg + ";}" +
               "tr:nth-child(even):hover{background-color:" + hoverBg + ";}" +
               "tr:nth-child(odd):hover{background-color:" + hoverBg + ";}";
    }

    /**
     * 获取代码块样式
     */
    public static String getCodeBlockStyle() {
        return "background-color:" + toHex(ModernColors.getConsoleTextAreaBg()) +
                ";padding:12px 14px;overflow:auto;font-size:11px;line-height:1.6;border-radius:6px;" +
                "margin:0 0 12px 0;font-family:'JetBrains Mono','Consolas','Monaco',monospace;color:" +
                toHex(ModernColors.getTextSecondary()) +
                ";display:block;white-space:pre;word-wrap:normal;border:1px solid " + toHex(ModernColors.getBorderLightColor()) + ";border-left:3px solid " + toHex(ModernColors.getAccent()) + ";";
    }

    /**
     * 获取行内代码样式
     */
    public static String getInlineCodeStyle() {
        String bgColor = toHex(ModernColors.getHoverBackgroundColor());
        String textColor = toHex(ModernColors.getAccent());
        return "background-color:" + bgColor + ";color:" + textColor +
                ";padding:2px 6px;margin:0 2px;font-size:11px;border-radius:4px;font-family:'JetBrains Mono','Consolas',monospace;font-weight:500;";
    }

    /**
     * 获取标题样式
     */
    public static String getHeadingStyle(int level) {
        String dividerColor = toHex(ModernColors.getDividerBorderColor());
        String accentColor = toHex(ModernColors.getAccent());
        return switch (level) {
            case 1 ->
                    "font-size:22px;font-weight:700;margin:20px 0 10px 0;color:" + accentColor + ";border-bottom:2px solid " + dividerColor + ";padding-bottom:6px;";
            case 2 ->
                    "font-size:18px;font-weight:600;margin:18px 0 8px 0;color:" + toHex(ModernColors.getTextPrimary()) + ";border-bottom:1px solid " + dividerColor + ";padding-bottom:4px;";
            case 3 -> "font-size:16px;font-weight:600;margin:16px 0 6px 0;color:" + toHex(ModernColors.getTextPrimary()) + ";";
            case 4 -> "font-size:14px;font-weight:600;margin:14px 0 6px 0;color:" + toHex(ModernColors.getTextPrimary()) + ";";
            case 5 -> "font-size:13px;font-weight:600;margin:12px 0 4px 0;color:" + toHex(ModernColors.getTextSecondary()) + ";";
            case 6 ->
                    "font-size:12px;font-weight:500;margin:10px 0 4px 0;color:" + toHex(ModernColors.getTextHint()) + ";font-style:italic;";
            default -> "";
        };
    }

    /**
     * 获取引用样式
     */
    public static String getBlockquoteStyle() {
        Color accentColor = ModernColors.getAccent();
        String borderColor = toHex(accentColor);
        String bgColor = String.format(java.util.Locale.ROOT, "rgba(%d,%d,%d,0.1)",
                accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue());
        return "padding:10px 14px;color:" + toHex(ModernColors.getTextSecondary()) +
                ";border-left:3px solid " + borderColor + ";background-color:" + bgColor +
                ";margin:0 0 12px 0;border-radius:0 6px 6px 0;font-style:italic;";
    }

    /**
     * 获取水平线样式
     */
    public static String getHrStyle() {
        return "height:2px;margin:24px 0;background-color:" + toHex(ModernColors.getDividerBorderColor()) + ";border:0;";
    }

    /**
     * 获取任务列表样式
     */
    public static String getTaskListStyle() {
        String borderColor = toHex(ModernColors.getBorderLightColor());
        String accentColor = toHex(ModernColors.getAccent());
        String textColor = toHex(ModernColors.getTextHint());
        String hoverBg = toHex(ModernColors.getHoverBackgroundColor());
        return ".task-list-item{list-style:none;margin-left:-20px;padding-left:20px;}" +
               ".task-list-item:hover{background-color:" + hoverBg + ";border-radius:4px;margin-left:-22px;padding-left:22px;}" +
               ".task-list-item-checkbox{width:14px;height:14px;border:1px solid " + borderColor + ";" +
               "border-radius:4px;vertical-align:middle;margin-right:8px;cursor:pointer;" +
               "background-color:" + toHex(ModernColors.getCardBackgroundColor()) + ";}" +
               ".task-list-item-checkbox:checked{background-color:" + accentColor + ";border-color:" + accentColor + ";}" +
               ".task-list-item-checkbox:checked::before{content:'✓';color:white;font-size:10px;}" +
               ".task-list-item-label{display:inline;}" +
               ".task-list-item-done{color:" + textColor + ";text-decoration:line-through;}";
    }
    
    
    public static String renderWrapWithTheme(String markdown) {
   
        return wrapWithTheme(render( markdown));
    }
    

    public static String render(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "";
        }
        return getRenderer().render(getParser().parse(markdown));
    }
    
    /**
     * 将 Color 转换为十六进制字符串
     */
    public static String toHex(Color color) {
        return String.format("#%02x%02x%02x", color.getRed(), color.getGreen(), color.getBlue());
    }
    
    public static String wrapWithTheme(String bodyHtml) {
        String bg = toHex(ModernColors.getCardBackgroundColor());
        String fg = toHex(ModernColors.getTextPrimary());
        String linkColor = toHex(ModernColors.getAccent());
        String selectionBg = toHex(ModernColors.getSelectionBackgroundColor());
        String secondaryFg = toHex(ModernColors.getTextSecondary());
        String borderColor = toHex(ModernColors.getBorderLightColor());
        
        String styles = getCachedStyles();

        return "<!DOCTYPE html>"
            + "<html><head><meta charset='UTF-8'><style>"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif;"
            + "font-size:13px;line-height:1.8;color:" + fg + ";background:" + bg + ";margin:16px 20px;word-wrap:break-word}"
            + "a{color:" + linkColor + ";text-decoration:none}"
            + "a:hover{text-decoration:underline}"
            + "::selection{background:" + selectionBg + ";color:" + fg + "}"
            + "img{max-width:100%;height:auto;border-radius:6px;margin:8px 0;box-shadow:0 2px 8px rgba(0,0,0,0.12)}"
            + "hr{height:1px;border:none;background:" + borderColor + ";margin:24px 0}"
            + "small{font-size:11px;color:" + secondaryFg + "}"
            + "strong,b{font-weight:600}"
            + styles
            + "</style></head><body>"
            + bodyHtml
            + "</body></html>";
    }
    
    /**
     * 获取缓存的样式字符串（避免重复拼接）
     * 主题切换时自动失效缓存并重新生成
     */
    private static String getCachedStyles() {
        String currentThemeId = ModernColors.isDarkTheme() ? "dark" : "light";
        if (CACHED_STYLES == null || !java.util.Objects.equals(currentThemeId, CACHED_THEME_ID)) {
            synchronized (MarkdownPreviewRenderer.class) {
                if (CACHED_STYLES == null || !java.util.Objects.equals(currentThemeId, CACHED_THEME_ID)) {
                    StringBuilder sb = new StringBuilder(1024);
                    sb.append("table{").append(getTableStyle()).append("}");
                    sb.append(getTableRowHoverStyle());
                    sb.append("th{").append(getTableHeaderStyle()).append("}");
                    sb.append("td{").append(getTableCellStyle()).append("}");
                    sb.append("pre{").append(getCodeBlockStyle()).append("}");
                    sb.append("code{").append(getInlineCodeStyle()).append("}");
                    sb.append("blockquote{").append(getBlockquoteStyle()).append("}");
                    sb.append("hr{").append(getHrStyle()).append("}");
                    sb.append(getTaskListStyle());
                    for (int i = 1; i <= 6; i++) {
                        sb.append("h").append(i).append("{").append(getHeadingStyle(i)).append("}");
                    }
                    sb.append("p{margin:0 0 10px 0;}");
                    sb.append("br{margin:4px 0;}");
                    sb.append("ul{margin:0 0 10px 0;padding-left:24px;list-style-type:disc;}");
                    sb.append("ol{margin:0 0 10px 0;padding-left:24px;list-style-type:decimal;}");
                    sb.append("li{margin:3px 0;line-height:1.6;}");
                    sb.append("dl{margin:0 0 10px 0;}");
                    sb.append("dt{font-weight:600;margin:6px 0 2px 0;}");
                    sb.append("dd{margin:0 0 4px 20px;color:" + toHex(ModernColors.getTextSecondary()) + ";}");
                    sb.append("mark{background-color:" + toHex(ModernColors.getSearchHighlightBackgroundColor()) + ";color:" + toHex(ModernColors.getTextPrimary()) + ";padding:1px 3px;border-radius:2px;}");
                    CACHED_STYLES = sb.toString();
                    CACHED_THEME_ID = currentThemeId;
                }
            }
        }
        return CACHED_STYLES;
    }
}