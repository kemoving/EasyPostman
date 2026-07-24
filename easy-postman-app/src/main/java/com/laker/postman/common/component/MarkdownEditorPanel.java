package com.laker.postman.common.component;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.InputMap;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSeparator;
import javax.swing.JSplitPane;
import javax.swing.JTextPane;
import javax.swing.JToggleButton;
import javax.swing.KeyStroke;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.undo.UndoManager;

import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.SyntaxConstants;

import com.formdev.flatlaf.util.SystemFileChooser;
import com.laker.postman.common.constants.ModernColors;
import com.laker.postman.util.EditorFontManager;
import com.laker.postman.util.EditorThemeUtil;
import com.laker.postman.util.FileChooserUtil;
import com.laker.postman.util.FontsUtil;
import com.laker.postman.util.I18nUtil;
import com.laker.postman.util.MessageKeys;

/**
 * Markdown 编辑器组件
 * 支持实时预览、工具栏、撤销/重做、导出等功能
 */
public class MarkdownEditorPanel extends JPanel {
	private static final long serialVersionUID = 1L;
	private RSyntaxTextArea editorArea;
    private SearchableTextArea searchableTextArea;
    private JTextPane previewPane;
    private JSplitPane splitPane;
    private JPanel toolbarPanel;
    private final List<DocumentListener> changeListeners = new ArrayList<>();
    private final UndoManager undoManager = new UndoManager();

    private JPanel editorPanelRef;
    private JPanel previewPanelRef;
    
    private JScrollPane editorScrollPane;
    private JScrollPane previewScrollPane;
    private boolean isSyncingScroll = false;  // 防止滚动循环的标志

    // 防抖相关
    private static final int PREVIEW_DEBOUNCE_DELAY = 300;  // 预览更新防抖延迟（毫秒）
    private Timer previewDebounceTimer;  // 预览防抖定时器
    private final AtomicBoolean isPreviewUpdating = new AtomicBoolean(false);  // 防止并发更新

    private static final int MODE_SPLIT = 0;
    private static final int MODE_EDIT_ONLY = 1;
    private static final int MODE_PREVIEW_ONLY = 2;
    private int viewMode = MODE_SPLIT;

    private JButton undoButton;
    private JButton redoButton;
    private boolean editable = true;

    public MarkdownEditorPanel() {
        initUI();
        setupKeyBindings();
    }

    @Override
    public void updateUI() {
        super.updateUI();

        if (toolbarPanel != null && editorPanelRef != null && previewPanelRef != null) {
            removeAll();

            toolbarPanel = createEnhancedToolbar();
            add(toolbarPanel, BorderLayout.NORTH);

            add(splitPane, BorderLayout.CENTER);

            JPanel statusBar = createStatusBar();
            add(statusBar, BorderLayout.SOUTH);

            setEditable(editable);
            updatePreviewPaneStyles();

            revalidate();
            repaint();
        }
    }

    /**
     * 更新预览面板样式以适应主题变化
     */
    private void updatePreviewPaneStyles() {
        if (previewPane != null) {
            updatePreview();
        }
    }


    private void initUI() {
        setLayout(new BorderLayout());
        ToolWindowSurfaceStyle.applyCard(this);

        editorPanelRef = createEditorPanel();
        previewPanelRef = createPreviewPanel();

        splitPane = AppToolWindowChrome.createHorizontalInnerSplitPane(editorPanelRef, previewPanelRef, 0);
        splitPane.setResizeWeight(0.5);

        toolbarPanel = createEnhancedToolbar();
        add(toolbarPanel, BorderLayout.NORTH);

        add(splitPane, BorderLayout.CENTER);

        JPanel statusBar = createStatusBar();
        add(statusBar, BorderLayout.SOUTH);

        SwingUtilities.invokeLater(() -> {
            splitPane.setDividerLocation(0.5);
            setupScrollSync();
        });
    }

    /**
     * 创建工具栏
     */
    private JPanel createEnhancedToolbar() {
        JPanel toolbarContainer = new JPanel(new WrapLayout(FlowLayout.LEFT, 5, 2));
        ToolWindowSurfaceStyle.applySectionHeader(toolbarContainer, 3, 5, 3, 5);
        
        // 添加边框突出显示工具栏
        toolbarContainer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, ModernColors.getBorderLightColor()),
                BorderFactory.createEmptyBorder(5, 8, 5, 8)
        ));

        undoButton = createFlatButton("↶", I18nUtil.getMessage(MessageKeys.MARKDOWN_UNDO), e -> undo());
        redoButton = createFlatButton("↷", I18nUtil.getMessage(MessageKeys.MARKDOWN_REDO), e -> redo());
        undoButton.setEnabled(false);
        redoButton.setEnabled(false);
        toolbarContainer.add(undoButton);
        toolbarContainer.add(redoButton);
        toolbarContainer.add(createVerticalDivider());

        toolbarContainer.add(createFlatButton("H1", I18nUtil.getMessage(MessageKeys.MARKDOWN_HEADING1), "# ", ""));
        toolbarContainer.add(createFlatButton("H2", I18nUtil.getMessage(MessageKeys.MARKDOWN_HEADING2), "## ", ""));
        toolbarContainer.add(createFlatButton("H3", I18nUtil.getMessage(MessageKeys.MARKDOWN_HEADING3), "### ", ""));
        toolbarContainer.add(createVerticalDivider());

        toolbarContainer.add(createFlatButton("<html><b>B</b></html>", I18nUtil.getMessage(MessageKeys.MARKDOWN_BOLD), "**", "**"));
        toolbarContainer.add(createFlatButton("<html><i>I</i></html>", I18nUtil.getMessage(MessageKeys.MARKDOWN_ITALIC), "_", "_"));
        toolbarContainer.add(createFlatButton("<html><s>S</s></html>", I18nUtil.getMessage(MessageKeys.MARKDOWN_STRIKETHROUGH), "~~", "~~"));
        toolbarContainer.add(createFlatButton("<html><code>`</code></html>", I18nUtil.getMessage(MessageKeys.MARKDOWN_INLINE_CODE), "`", "`"));
        toolbarContainer.add(createVerticalDivider());

        toolbarContainer.add(createFlatButton("🔗", I18nUtil.getMessage(MessageKeys.MARKDOWN_LINK), "[", "](url)"));
        toolbarContainer.add(createFlatButton("🖼", I18nUtil.getMessage(MessageKeys.MARKDOWN_IMAGE), "![", "](url)"));
        toolbarContainer.add(createFlatActionButton("⊞", I18nUtil.getMessage(MessageKeys.MARKDOWN_TABLE), this::insertTable));
        toolbarContainer.add(createFlatButton("{}", I18nUtil.getMessage(MessageKeys.MARKDOWN_CODE_BLOCK), "```\n", "\n```"));
        toolbarContainer.add(createVerticalDivider());

        toolbarContainer.add(createFlatButton("•", I18nUtil.getMessage(MessageKeys.MARKDOWN_UNORDERED_LIST), "- ", ""));
        toolbarContainer.add(createFlatButton("☑", I18nUtil.getMessage(MessageKeys.MARKDOWN_TASK_LIST), "- [ ] ", ""));
        toolbarContainer.add(createFlatButton("❝", I18nUtil.getMessage(MessageKeys.MARKDOWN_QUOTE), "> ", ""));
        toolbarContainer.add(createFlatButton("─", I18nUtil.getMessage(MessageKeys.MARKDOWN_HORIZONTAL_LINE), "---\n", ""));
        toolbarContainer.add(createVerticalDivider());

        JButton moreButton = createFlatButton("⋮", I18nUtil.getMessage(MessageKeys.MARKDOWN_MORE), null);
        JPopupMenu moreMenu = createMoreMenu();
        moreButton.addActionListener(e -> moreMenu.show(moreButton, 0, moreButton.getHeight()));
        toolbarContainer.add(moreButton);
        toolbarContainer.add(createVerticalDivider());

        JToggleButton splitViewBtn = new JToggleButton("⚏");
        JToggleButton editViewBtn = new JToggleButton("✎");
        JToggleButton previewViewBtn = new JToggleButton("👁");

        splitViewBtn.setToolTipText(I18nUtil.getMessage(MessageKeys.MARKDOWN_VIEW_SPLIT));
        editViewBtn.setToolTipText(I18nUtil.getMessage(MessageKeys.MARKDOWN_VIEW_EDIT_ONLY));
        previewViewBtn.setToolTipText(I18nUtil.getMessage(MessageKeys.MARKDOWN_VIEW_PREVIEW_ONLY));

        ButtonGroup viewGroup = new ButtonGroup();
        viewGroup.add(splitViewBtn);
        viewGroup.add(editViewBtn);
        viewGroup.add(previewViewBtn);

        styleToggleButton(splitViewBtn);
        styleToggleButton(editViewBtn);
        styleToggleButton(previewViewBtn);

        splitViewBtn.setSelected(true);

        splitViewBtn.addActionListener(e -> {
            viewMode = MODE_SPLIT;
            updateViewMode();
        });
        editViewBtn.addActionListener(e -> {
            viewMode = MODE_EDIT_ONLY;
            updateViewMode();
        });
        previewViewBtn.addActionListener(e -> {
            viewMode = MODE_PREVIEW_ONLY;
            updateViewMode();
        });

        toolbarContainer.add(splitViewBtn);
        toolbarContainer.add(editViewBtn);
        toolbarContainer.add(previewViewBtn);

        return toolbarContainer;
    }

    /**
     * 支持自动换行的 FlowLayout
     */
    private static class WrapLayout extends FlowLayout {
		private static final long serialVersionUID = 1L;

		public WrapLayout(int align, int hgap, int vgap) {
            super(align, hgap, vgap);
        }

        @Override
        public Dimension preferredLayoutSize(Container target) {
            return layoutSize(target, true);
        }

        @Override
        public Dimension minimumLayoutSize(Container target) {
            return layoutSize(target, false);
        }

        private Dimension layoutSize(Container target, boolean preferred) {
            synchronized (target.getTreeLock()) {
                int targetWidth = target.getWidth();
                if (targetWidth == 0) {
                    targetWidth = Integer.MAX_VALUE;
                }

                int hgap = getHgap();
                int vgap = getVgap();
                Insets insets = target.getInsets();
                int horizontalInsetsAndGap = insets.left + insets.right + (hgap * 2);
                int maxWidth = targetWidth - horizontalInsetsAndGap;

                Dimension dim = new Dimension(0, 0);
                int rowWidth = 0;
                int rowHeight = 0;

                int nmembers = target.getComponentCount();

                for (int i = 0; i < nmembers; i++) {
                    Component m = target.getComponent(i);

                    if (m.isVisible()) {
                        Dimension d = preferred ? m.getPreferredSize() : m.getMinimumSize();

                        if (rowWidth + d.width > maxWidth) {
                            addRow(dim, rowWidth, rowHeight);
                            rowWidth = 0;
                            rowHeight = 0;
                        }

                        if (rowWidth != 0) {
                            rowWidth += hgap;
                        }

                        rowWidth += d.width;
                        rowHeight = Math.max(rowHeight, d.height);
                    }
                }

                addRow(dim, rowWidth, rowHeight);

                dim.width += horizontalInsetsAndGap;
                dim.height += insets.top + insets.bottom + vgap * 2;

                return dim;
            }
        }

        private void addRow(Dimension dim, int rowWidth, int rowHeight) {
            dim.width = Math.max(dim.width, rowWidth);

            if (dim.height > 0) {
                dim.height += getVgap();
            }

            dim.height += rowHeight;
        }
    }

    /**
     * 创建更多功能菜单
     */
    private JPopupMenu createMoreMenu() {
        JPopupMenu menu = new JPopupMenu();
        ToolWindowSurfaceStyle.applyPopupMenuCard(menu);

        JMenuItem exportItem = new JMenuItem("💾 " + I18nUtil.getMessage(MessageKeys.MARKDOWN_EXPORT_HTML));
        exportItem.addActionListener(e -> exportToHtml());

        JMenuItem copyItem = new JMenuItem("📋 " + I18nUtil.getMessage(MessageKeys.MARKDOWN_COPY_HTML));
        copyItem.addActionListener(e -> copyHtmlToClipboard());

        menu.add(exportItem);
        menu.add(copyItem);

        return menu;
    }

    private JButton createFlatButton(String text, String tooltip, ActionListener action) {
        JButton button = new JButton(text);
        button.setToolTipText(tooltip);
        button.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, 0));
        button.setFocusPainted(false);
        button.setMargin(new Insets(4, 8, 4, 8));
        button.setPreferredSize(null);
        button.setMinimumSize(new Dimension(28, 28));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);

        if (action != null) {
            button.addActionListener(action);
        }

        return button;
    }

    private JButton createFlatButton(String text, String tooltip, String prefix, String suffix) {
        return createFlatButton(text, tooltip, e -> insertFormat(prefix, suffix));
    }

    private JButton createFlatActionButton(String text, String tooltip, Runnable action) {
        return createFlatButton(text, tooltip, e -> action.run());
    }

    private void styleToggleButton(JToggleButton button) {
        button.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, 0));
        button.setFocusPainted(false);
        button.setMargin(new Insets(4, 10, 4, 10));
        button.setPreferredSize(null);
        button.setMinimumSize(new Dimension(32, 28));
        button.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        button.putClientProperty(com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE,
                com.formdev.flatlaf.FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
    }

    private Component createVerticalDivider() {
        JSeparator separator = new JSeparator(SwingConstants.VERTICAL);
        separator.setPreferredSize(new Dimension(1, 20));
        separator.setForeground(ModernColors.getDividerBorderColor());
        return separator;
    }

    /**
     * 创建编辑器面板
     */
    private JPanel createEditorPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        // 创建 RSyntaxTextArea 用于 Markdown 编辑
        editorArea = new FallbackAwareRSyntaxTextArea();
        editorArea.setSyntaxEditingStyle(SyntaxConstants.SYNTAX_STYLE_MARKDOWN); // 设置为 Markdown 语法高亮
        editorArea.setCodeFoldingEnabled(false); // Markdown 不需要代码折叠
        editorArea.setTabSize(4); // 设置 Tab 宽度为 4 个空格
        // 统一加载主题、编辑器字体和缺字回退绘制
        EditorThemeUtil.loadTheme(editorArea);
        // 设置编辑器字体（在 loadTheme 之后，确保不被主题覆盖）
        EditorFontManager.applyConfiguredEditorFont(editorArea);
        //editorArea.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, 0));
        // 使用 SearchableTextArea 包装器（启用搜索替换功能）
        searchableTextArea = new SearchableTextArea(editorArea, true);

        // 添加撤销/重做支持，限制撤销步数防止内存占用过大
        undoManager.setLimit(100);  // 最多保留100步撤销历史
        editorArea.getDocument().addUndoableEditListener(e -> {
            undoManager.addEdit(e.getEdit());
            updateUndoRedoButtons();
        });

        // 初始化预览防抖定时器
        previewDebounceTimer = new Timer(PREVIEW_DEBOUNCE_DELAY, e -> {
            if (isPreviewUpdating.compareAndSet(false, true)) {
                try {
                    updatePreview();
                } finally {
                    isPreviewUpdating.set(false);
                }
            }
        });
        previewDebounceTimer.setRepeats(false);

        // 监听内容变化，使用防抖机制更新预览
        editorArea.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                notifyChangeListeners(e);
                // 防抖：重置定时器，延迟更新预览
                previewDebounceTimer.restart();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                notifyChangeListeners(e);
                // 防抖：重置定时器，延迟更新预览
                previewDebounceTimer.restart();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                notifyChangeListeners(e);
                // 防抖：重置定时器，延迟更新预览
                previewDebounceTimer.restart();
            }
        });

        panel.add(searchableTextArea, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 创建预览面板
     */
    private JPanel createPreviewPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setOpaque(false);

        previewPane = new JTextPane();
        previewPane.setContentType("text/html");
        previewPane.setEditable(false);
        previewPane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        ToolWindowSurfaceStyle.applyTextComponentCard(previewPane);
        previewScrollPane = new JScrollPane(previewPane);
        ToolWindowSurfaceStyle.applyScrollPaneCard(previewScrollPane);

        panel.add(previewScrollPane, BorderLayout.CENTER);
        return panel;
    }

    /**
     * 创建状态栏
     */
    private JPanel createStatusBar() {
        JPanel statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 3));
        ToolWindowSurfaceStyle.applySectionHeader(statusBar);

        JLabel statusLabel = new JLabel(I18nUtil.getMessage(MessageKeys.MARKDOWN_READY));
        statusLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        statusLabel.setForeground(ModernColors.getTextSecondary());
        statusBar.add(statusLabel);

        // 字数统计
        JLabel wordCountLabel = new JLabel(String.format("%s: 0 | %s: 0",
                I18nUtil.getMessage(MessageKeys.MARKDOWN_STATUS_WORDS),
                I18nUtil.getMessage(MessageKeys.MARKDOWN_STATUS_CHARS)));
        wordCountLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        wordCountLabel.setForeground(ModernColors.getTextSecondary());
        statusBar.add(new JSeparator(SwingConstants.VERTICAL));
        statusBar.add(wordCountLabel);

        // 行列号
        JLabel positionLabel = new JLabel(I18nUtil.getMessage(MessageKeys.MARKDOWN_STATUS_LINE) + ": 1, " +
                I18nUtil.getMessage(MessageKeys.MARKDOWN_STATUS_COLUMN) + ": 1");
        positionLabel.setFont(FontsUtil.getDefaultFontWithOffset(Font.PLAIN, -1));
        positionLabel.setForeground(ModernColors.getTextSecondary());
        statusBar.add(new JSeparator(SwingConstants.VERTICAL));
        statusBar.add(positionLabel);

        // 更新状态栏
        editorArea.addCaretListener(e -> {
           updateStatusBar(positionLabel, wordCountLabel);
        });

        return statusBar;
    }

    /**
     * 更新状态栏信息（防抖后执行）
     */
    private void updateStatusBar(JLabel positionLabel, JLabel wordCountLabel) {
        try {
            int pos = editorArea.getCaretPosition();
            int line = editorArea.getLineOfOffset(pos);
            int col = pos - editorArea.getLineStartOffset(line);
            positionLabel.setText(String.format("%s: %d, %s: %d",
                    I18nUtil.getMessage(MessageKeys.MARKDOWN_STATUS_LINE), line + 1,
                    I18nUtil.getMessage(MessageKeys.MARKDOWN_STATUS_COLUMN), col + 1));

            String text = editorArea.getText();
            // 计算单词数：空文本或只有空白字符时为0
            int wordCount = 0;
            int charCount = 0;
            if (text != null) {
                charCount = text.length();
                if (!text.trim().isEmpty()) {
                    wordCount = text.trim().split("\\s+").length;
                }
            }
            wordCountLabel.setText(String.format("%s: %d | %s: %d",
                    I18nUtil.getMessage(MessageKeys.MARKDOWN_STATUS_WORDS), wordCount,
                    I18nUtil.getMessage(MessageKeys.MARKDOWN_STATUS_CHARS), charCount));
        } catch (Exception ex) {
            // 忽略异常，避免影响用户体验
        }
    }


    /**
     * 设置快捷键
     */
    private void setupKeyBindings() {
        InputMap inputMap = editorArea.getInputMap(JComponent.WHEN_FOCUSED);
        ActionMap actionMap = editorArea.getActionMap();

        // Ctrl+B - 粗体
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_B, InputEvent.CTRL_DOWN_MASK), "bold");
        actionMap.put("bold", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertFormat("**", "**");
            }
        });

        // Ctrl+I - 斜体
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK), "italic");
        actionMap.put("italic", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertFormat("_", "_");
            }
        });

        // Ctrl+K - 链接
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK), "link");
        actionMap.put("link", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertFormat("[", "](url)");
            }
        });

        // Ctrl+Z - 撤销
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        actionMap.put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                undo();
            }
        });

        // Ctrl+Y - 重做
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        actionMap.put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                redo();
            }
        });


        // Ctrl+` - 行内代码
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_BACK_QUOTE, InputEvent.CTRL_DOWN_MASK), "inlineCode");
        actionMap.put("inlineCode", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertFormat("`", "`");
            }
        });

        // Ctrl+Shift+K - 删除线
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_K, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "strikethrough");
        actionMap.put("strikethrough", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertFormat("~~", "~~");
            }
        });

        // Ctrl+Shift+C - 代码块
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "codeBlock");
        actionMap.put("codeBlock", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertFormat("```\n", "\n```");
            }
        });

        // Ctrl+Shift+Q - 引用
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_Q, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "quote");
        actionMap.put("quote", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertLinePrefix("> ");
            }
        });

        // Ctrl+Shift+L - 无序列表
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_L, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "unorderedList");
        actionMap.put("unorderedList", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertLinePrefix("- ");
            }
        });

        // Ctrl+Shift+O - 有序列表
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_O, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "orderedList");
        actionMap.put("orderedList", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertLinePrefix("1. ");
            }
        });

        // Ctrl+Shift+T - 任务列表
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "taskList");
        actionMap.put("taskList", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertLinePrefix("- [ ] ");
            }
        });

        // Ctrl+1-6 - 标题级别
        for (int i = 1; i <= 6; i++) {
            final int level = i;
            inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_0 + i, InputEvent.CTRL_DOWN_MASK), "heading" + i);
            actionMap.put("heading" + i, new AbstractAction() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    String prefix = "#".repeat(level) + " ";
                    insertLinePrefix(prefix);
                }
            });
        }

        // Ctrl+Shift+H - 水平线
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "horizontalLine");
        actionMap.put("horizontalLine", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                int pos = editorArea.getCaretPosition();
                editorArea.insert("\n---\n", pos);
                editorArea.requestFocus();
            }
        });

        // Ctrl+Shift+I - 插入图片
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_I, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK), "image");
        actionMap.put("image", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertFormat("![", "](url)");
            }
        });

        // Ctrl+Shift+T - 插入表格
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_T, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK), "table");
        actionMap.put("table", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                insertTable();
            }
        });

        // Ctrl+S - 触发保存事件（通知监听器）
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_S, InputEvent.CTRL_DOWN_MASK), "save");
        actionMap.put("save", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                // 触发文档变化事件，让外部监听器处理保存
                editorArea.getDocument().putProperty("save-requested", true);
            }
        });

        // Ctrl+E - 导出HTML
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_E, InputEvent.CTRL_DOWN_MASK), "export");
        actionMap.put("export", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                exportToHtml();
            }
        });

        // Ctrl+Shift+C - 复制HTML（不与代码块冲突，使用Alt）
        inputMap.put(KeyStroke.getKeyStroke(KeyEvent.VK_C, InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK), "copyHtml");
        actionMap.put("copyHtml", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copyHtmlToClipboard();
            }
        });
    }

    /**
     * 在行首插入前缀（用于列表、引用、标题等）
     */
    private void insertLinePrefix(String prefix) {
        try {
            int pos = editorArea.getCaretPosition();
            int lineStart = editorArea.getLineStartOffset(editorArea.getLineOfOffset(pos));
            editorArea.insert(prefix, lineStart);
            editorArea.setCaretPosition(lineStart + prefix.length());
            editorArea.requestFocus();
        } catch (Exception e) {
            // 如果出错，就在光标位置插入
            int pos = editorArea.getCaretPosition();
            editorArea.insert(prefix, pos);
            editorArea.requestFocus();
        }
    }

    /**
     * 插入格式化文本
     */
    private void insertFormat(String prefix, String suffix) {
        int start = editorArea.getSelectionStart();
        int end = editorArea.getSelectionEnd();
        String selectedText = editorArea.getSelectedText();

        if (selectedText != null && !selectedText.isEmpty()) {
            editorArea.replaceSelection(prefix + selectedText + suffix);
            editorArea.setSelectionStart(start + prefix.length());
            editorArea.setSelectionEnd(end + prefix.length());
        } else {
            editorArea.insert(prefix + suffix, start);
            editorArea.setCaretPosition(start + prefix.length());
        }
        editorArea.requestFocus();
    }

    /**
     * 插入表格
     */
    private void insertTable() {
        String table = """
                | 列1 | 列2 | 列3 |
                | --- | --- | --- |
                | 单元格 | 单元格 | 单元格 |
                | 单元格 | 单元格 | 单元格 |
                """;
        int pos = editorArea.getCaretPosition();
        editorArea.insert(table, pos);
        editorArea.requestFocus();
    }

    /**
     * 撤销
     */
    private void undo() {
        if (undoManager.canUndo()) {
            undoManager.undo();
            updateUndoRedoButtons();
        }
    }

    /**
     * 重做
     */
    private void redo() {
        if (undoManager.canRedo()) {
            undoManager.redo();
            updateUndoRedoButtons();
        }
    }

    /**
     * 更新撤销/重做按钮状态
     */
    private void updateUndoRedoButtons() {
        if (undoButton != null) {
            undoButton.setEnabled(undoManager.canUndo());
        }
        if (redoButton != null) {
            redoButton.setEnabled(undoManager.canRedo());
        }
    }


    /**
     * 导出为 HTML（使用国际化文本）
     */
    private void exportToHtml() {
        SystemFileChooser fileChooser = FileChooserUtil.createSaveFileChooser(
                "markdown.export.html",
                I18nUtil.getMessage(MessageKeys.MARKDOWN_EXPORT_HTML));
        fileChooser.setFileFilter(FileChooserUtil.extensionFilter("HTML 文件", "html"));

        if (fileChooser.showSaveDialog(this) == SystemFileChooser.APPROVE_OPTION) {
            try {
                String html = convertMarkdownToHtml(editorArea.getText());
                java.io.File file = fileChooser.getSelectedFile();
                if (!file.getName().endsWith(".html")) {
                    file = new java.io.File(file.getAbsolutePath() + ".html");
                }
                java.nio.file.Files.writeString(file.toPath(), html);
                JOptionPane.showMessageDialog(this,
                        I18nUtil.getMessage(MessageKeys.MARKDOWN_EXPORT_SUCCESS),
                        I18nUtil.getMessage(MessageKeys.MARKDOWN_EXPORT_HTML),
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this,
                        I18nUtil.getMessage(MessageKeys.MARKDOWN_EXPORT_FAILED) + ": " + ex.getMessage(),
                        I18nUtil.getMessage(MessageKeys.GENERAL_ERROR),
                        JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    /**
     * 复制 HTML 到剪贴板（使用国际化文本）
     */
    private void copyHtmlToClipboard() {
        String html = convertMarkdownToHtml(editorArea.getText());
        StringSelection selection = new StringSelection(html);
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(selection, selection);
        JOptionPane.showMessageDialog(this,
                I18nUtil.getMessage(MessageKeys.MARKDOWN_HTML_COPIED),
                I18nUtil.getMessage(MessageKeys.MARKDOWN_COPY_HTML),
                JOptionPane.INFORMATION_MESSAGE);
    }

    /**
     * 更新视图模式
     */
    private void updateViewMode() {
        switch (viewMode) {
            case MODE_SPLIT:
                splitPane.setLeftComponent(editorPanelRef);
                splitPane.setRightComponent(previewPanelRef);
                splitPane.setDividerSize(AppToolWindowChrome.DIVIDER_SIZE);
                SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.5));
                break;
            case MODE_EDIT_ONLY:
                splitPane.setLeftComponent(editorPanelRef);
                splitPane.setRightComponent(null);
                splitPane.setDividerSize(0);
                break;
            case MODE_PREVIEW_ONLY:
                splitPane.setLeftComponent(null);
                splitPane.setRightComponent(previewPanelRef);
                splitPane.setDividerSize(0);
                break;
            default:
                break;
        }
        splitPane.revalidate();
        splitPane.repaint();
        updatePreview();
    }

    /**
     * 更新预览
     */
    private void updatePreview() {
    	
        String markdown = editorArea.getText();

        // ✅ 使用 Flexmark 渲染
        String fullHtml = convertMarkdownToHtml(markdown);

        previewPane.setContentType("text/html");
        previewPane.setText(fullHtml);
        previewPane.setCaretPosition(0);
    }
    



    /**
     * 企业级 Markdown 到 HTML 转换
     * 支持完整的 GitHub Flavored Markdown (GFM) 语法
     */
    private String convertMarkdownToHtml(String markdown) {
        if (markdown == null || markdown.isEmpty()) {
            return "<html><body style='margin:0;padding:12px;font-size:10px;'></body></html>";
        }
        
        return MarkdownPreviewRenderer.renderWrapWithTheme(markdown);
    }

    /**
     * 获取编辑器文本
     */
    public String getText() {
        return editorArea.getText();
    }


    /**
     * 设置编辑器文本
     */
    public void setText(String text) {
        editorArea.setText(text == null ? "" : text);
        editorArea.setCaretPosition(0);
        // setText 多用于切换请求/分组时加载已有描述，不应成为用户可撤销的一步。
        // 同时清理 RSTA 内置 undo 和本面板自管 undo，避免 Ctrl+Z 把加载出的内容清空。
        editorArea.discardAllEdits();
        undoManager.discardAllEdits();
        updateUndoRedoButtons();
        updatePreview();
    }

    public void setEditable(boolean editable) {
        this.editable = editable;
        editorArea.setEditable(editable);
        if (toolbarPanel != null) {
            setComponentTreeEnabled(toolbarPanel, editable);
        }
    }

    private void setComponentTreeEnabled(Component component, boolean enabled) {
        component.setEnabled(enabled);
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                setComponentTreeEnabled(child, enabled);
            }
        }
    }

    /**
     * 添加文档变化监听器
     */
    public void addDocumentListener(DocumentListener listener) {
        changeListeners.add(listener);
    }

    /**
     * 移除文档变化监听器（防止内存泄漏）
     */
    public void removeDocumentListener(DocumentListener listener) {
        changeListeners.remove(listener);
    }

    /**
     * 清理资源（组件销毁时调用）
     */
    public void dispose() {
        // 停止防抖定时器
        if (previewDebounceTimer != null) {
            previewDebounceTimer.stop();
        }
        
        // 清空监听器列表
        changeListeners.clear();
        
        // 清空撤销历史
        undoManager.discardAllEdits();
    }

    /**
     * 通知所有监听器
     */
    private void notifyChangeListeners(DocumentEvent e) {
        for (DocumentListener listener : changeListeners) {
            if (e.getType() == DocumentEvent.EventType.INSERT) {
                listener.insertUpdate(e);
            } else if (e.getType() == DocumentEvent.EventType.REMOVE) {
                listener.removeUpdate(e);
            } else {
                listener.changedUpdate(e);
            }
        }
    }

    /**
     * 设置编辑器和预览器的双向滚动联动
     */
    private void setupScrollSync() {
        // 获取编辑器的滚动面板
        editorScrollPane = findScrollPane(searchableTextArea);
        
        if (editorScrollPane != null && previewScrollPane != null) {
            // 编辑器滚动 → 预览器同步滚动
            editorScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
                if (!isSyncingScroll && viewMode == MODE_SPLIT) {
                    isSyncingScroll = true;
                    try {
                        syncScrollToPreview();
                    } finally {
                        isSyncingScroll = false;
                    }
                }
            });

            // 预览器滚动 → 编辑器同步滚动
            previewScrollPane.getVerticalScrollBar().addAdjustmentListener(e -> {
                if (!isSyncingScroll && viewMode == MODE_SPLIT) {
                    isSyncingScroll = true;
                    try {
                        syncScrollToEditor();
                    } finally {
                        isSyncingScroll = false;
                    }
                }
            });
        }
    }

    /**
     * 查找组件中的滚动面板
     */
    private JScrollPane findScrollPane(Component component) {
        if (component instanceof JScrollPane) {
            return (JScrollPane) component;
        }
        if (component instanceof Container) {
            for (Component child : ((Container) component).getComponents()) {
                JScrollPane result = findScrollPane(child);
                if (result != null) {
                    return result;
                }
            }
        }
        return null;
    }

    /**
     * 编辑器滚动同步到预览器（比例同步）
     */
    private void syncScrollToPreview() {
        try {
            if (previewScrollPane == null) {
                return;
            }
            
            int editorMax = editorScrollPane.getVerticalScrollBar().getMaximum() - 
                           editorScrollPane.getVerticalScrollBar().getVisibleAmount();
            int previewMax = previewScrollPane.getVerticalScrollBar().getMaximum() - 
                            previewScrollPane.getVerticalScrollBar().getVisibleAmount();
            
            if (editorMax > 0 && previewMax > 0) {
                double ratio = (double) editorScrollPane.getVerticalScrollBar().getValue() / editorMax;
                int targetValue = (int) (ratio * previewMax);
                previewScrollPane.getVerticalScrollBar().setValue(targetValue);
            }
        } catch (Exception ex) {
            // 忽略异常
        }
    }

    /**
     * 预览器滚动同步到编辑器（比例同步）
     */
    private void syncScrollToEditor() {
        try {
            if (editorScrollPane == null) {
                return;
            }
            
            int previewMax = previewScrollPane.getVerticalScrollBar().getMaximum() - 
                            previewScrollPane.getVerticalScrollBar().getVisibleAmount();
            int editorMax = editorScrollPane.getVerticalScrollBar().getMaximum() - 
                           editorScrollPane.getVerticalScrollBar().getVisibleAmount();
            
            if (previewMax > 0 && editorMax > 0) {
                double ratio = (double) previewScrollPane.getVerticalScrollBar().getValue() / previewMax;
                int targetValue = (int) (ratio * editorMax);
                editorScrollPane.getVerticalScrollBar().setValue(targetValue);
            }
        } catch (Exception ex) {
            // 忽略异常
        }
    }

}
