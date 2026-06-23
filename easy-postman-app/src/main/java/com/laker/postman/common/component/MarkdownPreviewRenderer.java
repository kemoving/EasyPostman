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
        return "border-collapse:separate;border-spacing:0;width:100%;margin:0 0 12px 0;border-radius:6px;overflow:hidden;border:1px solid " + toHex(ModernColors.getBorderLightColor()) + ";";
    }

    /**
     * 获取表格单元格样式
     */
    public static String getTableCellStyle() {
        return "padding:6px 10px;border:1px solid " + toHex(ModernColors.getBorderLightColor()) + ";vertical-align:middle;";
    }

    /**
     * 获取表格表头样式
     */
    public static String getTableHeaderStyle() {
        String bgColor = toHex(ModernColors.getHoverBackgroundColor());
        return getTableCellStyle() + "font-weight:600;background-color:" + bgColor + ";text-align:left;white-space:nowrap;";
    }

    /**
     * 获取表格行hover样式
     */
    public static String getTableRowHoverStyle() {
        String hoverBg = toHex(ModernColors.getHoverBackgroundColor());
        return "tr:hover{background-color:" + hoverBg + ";}";
    }

    /**
     * 获取代码块样式
     */
    public static String getCodeBlockStyle() {
        return "background-color:" + toHex(ModernColors.getConsoleTextAreaBg()) +
                ";padding:8px;overflow:auto;font-size:10px;line-height:1.5;border-radius:4px;" +
                "margin:0 0 8px 0;font-family:monospace;color:" +
                toHex(ModernColors.getConsoleText()) +
                ";display:block;white-space:pre;word-wrap:normal;";
    }

    /**
     * 获取行内代码样式
     */
    public static String getInlineCodeStyle() {
        String bgColor = toHex(ModernColors.getHoverBackgroundColor());
        String textColor = toHex(ModernColors.getErrorDark());
        return "background-color:" + bgColor + ";color:" + textColor +
                ";padding:1px 4px;margin:0 1px;font-size:10px;border-radius:3px;font-family:monospace;";
    }

    /**
     * 获取标题样式
     */
    public static String getHeadingStyle(int level) {
        String dividerColor = toHex(ModernColors.getDividerBorderColor());
        return switch (level) {
            case 1 ->
                    "font-size:18px;font-weight:600;margin:4px 0 4px 0;border-bottom:2px solid " + dividerColor + ";padding-bottom:0.2em;";
            case 2 ->
                    "font-size:16px;font-weight:600;margin:4px 0 3px 0;border-bottom:1px solid " + dividerColor + ";padding-bottom:0.2em;";
            case 3 -> "font-size:14px;font-weight:600;margin:4px 0 3px 0;";
            case 4 -> "font-size:12px;font-weight:600;margin:4px 0 3px 0;";
            case 5 -> "font-size:11px;font-weight:600;margin:4px 0 3px 0;";
            case 6 ->
                    "font-size:10px;font-weight:600;margin:4px 0 3px 0;color:" + toHex(ModernColors.getTextHint()) + ";";
            default -> "";
        };
    }

    /**
     * 获取引用样式
     */
    public static String getBlockquoteStyle() {
        Color accentColor = ModernColors.getAccent();
        String borderColor = toHex(accentColor);
        String bgColor = String.format(java.util.Locale.ROOT, "rgba(%d,%d,%d,0.08)",
                accentColor.getRed(), accentColor.getGreen(), accentColor.getBlue());
        return "padding:6px 10px;color:" + toHex(ModernColors.getTextSecondary()) +
                ";border-left:3px solid " + borderColor + ";background-color:" + bgColor +
                ";margin:0 0 8px 0;border-radius:0 3px 3px 0;";
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
        return ".task-list-item{list-style:none;margin-left:-20px;padding-left:20px;}" +
               ".task-list-item-checkbox{width:14px;height:14px;border:1px solid " + borderColor + ";" +
               "border-radius:3px;vertical-align:middle;margin-right:6px;" +
               "background-color:" + toHex(ModernColors.getCardBackgroundColor()) + ";}" +
               ".task-list-item-checkbox:checked{background-color:" + accentColor + ";border-color:" + accentColor + ";}" +
               ".task-list-item-checkbox:checked::before{content:'✓';color:white;font-size:10px;" +
               "display:flex;align-items:center;justify-content:center;height:100%;}" +
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
        
        // 使用缓存的样式字符串（如果已缓存）
        String styles = getCachedStyles();

        return "<!DOCTYPE html>"
            + "<html><head><meta charset='UTF-8'><style>"
            + "body{font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif;"
            + "font-size:10px;line-height:1.6;color:" + fg + ";background:" + bg + ";margin:8px}"
            + styles
            + "</style></head><body>"
            + bodyHtml
            + "</body></html>";
    }
    
    /**
     * 获取缓存的样式字符串（避免重复拼接）
     */
    private static String getCachedStyles() {
        if (CACHED_STYLES == null) {
            synchronized (MarkdownPreviewRenderer.class) {
                if (CACHED_STYLES == null) {
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
                    sb.append("p{margin:0 0 6px 0;}");
                    sb.append("br{margin:4px 0;}");
                    CACHED_STYLES = sb.toString();
                }
            }
        }
        return CACHED_STYLES;
    }
}