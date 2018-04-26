package com.nvlad.yii2support.migrations.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.Separator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.ui.AnActionButton;
import com.intellij.ui.CheckboxTree;
import com.intellij.ui.CheckedTreeNode;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.UIUtil;
import com.nvlad.yii2support.migrations.MigrationManager;
import com.nvlad.yii2support.migrations.MigrationsMouseListener;
import com.nvlad.yii2support.migrations.actions.*;
import com.nvlad.yii2support.migrations.entities.Migration;
import com.nvlad.yii2support.migrations.entities.MigrationStatus;
import com.nvlad.yii2support.migrations.util.MigrationUtil;
import com.nvlad.yii2support.utils.Yii2SupportSettings;

import javax.swing.*;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.TreeSelectionModel;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class MigrationPanel extends SimpleToolWindowPanel {
    private final Project myProject;
    private CheckboxTree myTree;
    private Map<String, Collection<Migration>> myMigrationMap;

    public MigrationPanel(Project project, ToolWindow toolWindow) {
        super(false);

        myProject = project;

        initContent();
        initToolBar();
        initActivationListener(toolWindow);
    }

    public JTree getTree() {
        return myTree;
    }

    public Map<String, Collection<Migration>> getMigrationMap() {
        return myMigrationMap;
    }

    public void updateMigrations() {
        Map<String, Collection<Migration>> newMigrationsMap = MigrationManager.getInstance(myProject).getMigrations();
        boolean newestFirst = Yii2SupportSettings.getInstance(myProject).newestFirst;
        if (!newMigrationsMap.equals(myMigrationMap)) {
            myMigrationMap = newMigrationsMap;
            MigrationUtil.updateTree(myTree, newMigrationsMap, false, newestFirst);
        }

        final Map<String, Date> history = MigrationManager.getInstance(myProject).migrateHistory();
        if (history == null) {
            return;
        }

        myMigrationMap = getMigrationMap();
        myMigrationMap.forEach((path, migrations) -> {
            for (Migration migration : migrations) {
                migration.status = MigrationStatus.NotApply;
                if (history.containsKey(migration.name)) {
                    migration.status = MigrationStatus.Success;
                    migration.applyAt = history.get(migration.name);
                }
            }
        });

        MigrationUtil.updateTree(myTree, myMigrationMap, false, newestFirst);

    }

    private void initActivationListener(ToolWindow toolWindow) {
        toolWindow.getActivation().doWhenDone(() -> {
            Map<String, Collection<Migration>> migrationTree = MigrationManager.getInstance(myProject).getMigrations();
            boolean newestFirst = Yii2SupportSettings.getInstance(myProject).newestFirst;
            MigrationUtil.updateTree(myTree, migrationTree, true, newestFirst);
        });
    }

    private void initContent() {
        MigrationTreeCellRenderer renderer = new MigrationTreeCellRenderer();
        CheckedTreeNode myRootNode = new CheckedTreeNode("");
        myRootNode.add(new DefaultMutableTreeNode("Init"));

        myTree = new CheckboxTree(renderer, myRootNode);
        myTree.addMouseListener(new MigrationsMouseListener());
        myTree.getSelectionModel().setSelectionMode(TreeSelectionModel.CONTIGUOUS_TREE_SELECTION);

        JBScrollPane scrollPane = new JBScrollPane(myTree);
        UIUtil.removeScrollBorder(scrollPane);
        setContent(scrollPane);
    }


    private void initToolBar() {
        ActionToolbar toolbar = createToolbar();
        setToolbar(toolbar.getComponent());
    }

    private ActionToolbar createToolbar() {
        DefaultActionGroup group = new DefaultActionGroup();
        try {
            group.add(createAction(RefreshAction.class));
            group.add(new Separator());
            group.add(createAction(MigrateUpAction.class));
            group.add(createAction(MigrateDownAction.class));
            group.add(createAction(MigrateRedoAction.class));
            group.add(new Separator());
            group.add(createAction(OrderAscAction.class));
        } catch (IllegalAccessException | InstantiationException e) {
            e.printStackTrace();
        }

        return ActionManager.getInstance().createActionToolbar("Migrations", group, false);
    }

    private <T extends AnActionButton> T createAction(Class<T> clazz) throws IllegalAccessException, InstantiationException {
        T action = clazz.newInstance();
        action.setContextComponent(this);
        return action;
    }
}
