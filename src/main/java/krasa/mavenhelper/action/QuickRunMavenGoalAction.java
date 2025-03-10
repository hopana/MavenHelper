package krasa.mavenhelper.action;

import com.intellij.execution.Location;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.execution.actions.ConfigurationContext;
import com.intellij.execution.actions.CreateAction;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.icons.AllIcons;
import com.intellij.ide.actions.QuickSwitchSchemeAction;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.popup.PopupFactoryImpl;
import com.intellij.ui.popup.list.ListPopupImpl;
import com.intellij.ui.popup.list.ListPopupModel;
import krasa.mavenhelper.MavenHelperApplicationService;
import krasa.mavenhelper.action.debug.DebugConfigurationAction;
import krasa.mavenhelper.action.debug.DebugGoalAction;
import krasa.mavenhelper.action.debug.DebugTestFileAction;
import krasa.mavenhelper.gui.GoalEditor;
import krasa.mavenhelper.icons.MyIcons;
import krasa.mavenhelper.model.ApplicationSettings;
import krasa.mavenhelper.model.Goal;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.execution.MavenGoalLocation;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.utils.actions.MavenActionUtil;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.List;

public class QuickRunMavenGoalAction extends QuickSwitchSchemeAction implements DumbAware {

	private final Logger LOG = Logger.getInstance("#" + getClass().getCanonicalName());

	@Override
	protected void fillActions(final Project currentProject, DefaultActionGroup group, DataContext dataContext) {
		if (currentProject != null) {
			group.addAll(new MainMavenActionGroup() {

				@Override
				protected void addTestFile(List<AnAction> result) {
					QuickRunMavenGoalAction.this.addTestFile(result);
				}

				@Override
				protected AnAction getRunConfigurationAction(Project project, RunnerAndConfigurationSettings cfg) {
					return QuickRunMavenGoalAction.this.getRunConfigurationAction(project, cfg);
				}

				@Override
				protected AnAction createGoalRunAction(Goal goal, final Icon icon, boolean plugin, MavenProjectInfo mavenProject) {
					return QuickRunMavenGoalAction.this.createGoalRunAction(goal, icon, plugin, mavenProject);
				}
			}.getActions(dataContext, currentProject));
		}
	}

	@Override
	public @NotNull ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT; //MavenActionUtil.getMavenProject(e.getDataContext()) does not work on EDT
	}

	@Override
	public void update(AnActionEvent e) {
		super.update(e);
		Presentation p = e.getPresentation();
		p.setEnabled(isEnabled(e));
		p.setVisible(MavenActionUtil.isMavenizedProject(e.getDataContext()));
	}

	private boolean isEnabled(AnActionEvent e) {
		return MavenActionUtil.hasProject(e.getDataContext()) && Utils.getMavenProject(e.getDataContext()) != null;
	}

	@Override
	protected JBPopupFactory.ActionSelectionAid getAidMethod() {
		return JBPopupFactory.ActionSelectionAid.SPEEDSEARCH;
	}

	@Override
	protected void showPopup(AnActionEvent e, ListPopup p) {
		final ListPopupImpl popup = (ListPopupImpl) p;
		registerActions(popup);
		super.showPopup(e, popup);
	}

	private void registerActions(final ListPopupImpl popup) {
		if (ApplicationSettings.get().isEnableDelete()) {
			popup.registerAction("delete", KeyStroke.getKeyStroke(KeyEvent.VK_DELETE, 0), new AbstractAction() {
				@Override
				public void actionPerformed(ActionEvent e) {
					JList list = popup.getList();
					int selectedIndex = list.getSelectedIndex();
					ListPopupModel model = (ListPopupModel) list.getModel();
					PopupFactoryImpl.ActionItem selectedItem = (PopupFactoryImpl.ActionItem) model.get(selectedIndex);

					if (selectedItem != null && selectedItem.getAction() instanceof MyActionGroup) {
						MyActionGroup action = (MyActionGroup) selectedItem.getAction();
						boolean deleted = MavenHelperApplicationService.getInstance().getState().removeGoal(action.getGoal());

						if (deleted) {
							model.deleteItem(selectedItem);
							if (selectedIndex == list.getModel().getSize()) { // is last
								list.setSelectedIndex(selectedIndex - 1);
							} else {
								list.setSelectedIndex(selectedIndex);
							}
						}
					}
				}
			});

		}
	}

	void addTestFile(List<AnAction> result) {
		RunTestFileAction action = new RunTestFileAction();
		result.add(new ActionGroup(action.getTemplatePresentation().getText(), action.getTemplatePresentation().getDescription(), action.getTemplatePresentation().getIcon()) {
			@NotNull
			@Override
			public AnAction[] getChildren(@Nullable AnActionEvent anActionEvent) {
				return new AnAction[]{new DebugTestFileAction() {
					@Override
					protected String getText(String s) {
						return "Debug";
					}
				}};
			}

			@Override
			public void update(@NotNull AnActionEvent e) {
				action.update(e);
				e.getPresentation().setHideGroupIfEmpty(true);
				e.getPresentation().setPerformGroup(true);
			}

			@Override
			public @NotNull ActionUpdateThread getActionUpdateThread() {
				return ActionUpdateThread.BGT;
			}

			@Override
			public void actionPerformed(@NotNull AnActionEvent e) {
				action.actionPerformed(e);
			}




		});

	}


	protected AnAction getRunConfigurationAction(Project project, RunnerAndConfigurationSettings cfg) {
		RunConfigurationAction action = new RunConfigurationAction(DefaultRunExecutor.getRunExecutorInstance(), true, project, cfg);

		return new ActionGroup(action.getTemplatePresentation().getText(), action.getTemplatePresentation().getDescription(), action.getTemplatePresentation().getIcon()) {
			@NotNull
			@Override
			public AnAction[] getChildren(@Nullable AnActionEvent anActionEvent) {
				DebugConfigurationAction debugConfigurationAction = new DebugConfigurationAction(DefaultDebugExecutor.getDebugExecutorInstance(), true, project, cfg);
				debugConfigurationAction.getTemplatePresentation().setText("Debug");
				return new AnAction[]{debugConfigurationAction};
			}

			@Override
			public void actionPerformed(@NotNull AnActionEvent e) {
				action.actionPerformed(e);
			}


			@Override
			public @NotNull ActionUpdateThread getActionUpdateThread() {
				return ActionUpdateThread.BGT;
			}

			@Override
			public void update(@NotNull AnActionEvent e) {
				super.update(e);
				e.getPresentation().setPopupGroup(true);
				e.getPresentation().setPerformGroup(true);
			}
		};
	}

	protected AnAction createGoalRunAction(Goal goal, Icon runIcon, boolean plugin, MavenProjectInfo mavenProject) {
		RunGoalAction goalRunAction = RunGoalAction.create(goal, runIcon, true, mavenProject);
		return new MyActionGroup(goalRunAction, plugin, goal, mavenProject);
	}

	private class MyActionGroup extends ActionGroup implements DumbAware {
		private final RunGoalAction goalRunAction;
		private final boolean plugin;
		private final Goal goal;
		private final MavenProjectInfo mavenProjectInfo;

		public MyActionGroup(RunGoalAction goalRunAction, boolean plugin, Goal goal, MavenProjectInfo mavenProjectInfo) {
			super(goalRunAction.getTemplatePresentation().getText(), goalRunAction.getTemplatePresentation().getDescription(), goalRunAction.getTemplatePresentation().getIcon());
			this.goalRunAction = goalRunAction;
			this.plugin = plugin;
			this.goal = goal;
			this.mavenProjectInfo = mavenProjectInfo;
		}

		@Override
		public @NotNull ActionUpdateThread getActionUpdateThread() {
			return ActionUpdateThread.BGT;
		}

		@Override
		public void actionPerformed(@NotNull AnActionEvent e) {
			goalRunAction.actionPerformed(e);
		}

		@Override
		public void update(@NotNull AnActionEvent e) {
			super.update(e);
			e.getPresentation().setPopupGroup(true);
			e.getPresentation().setPerformGroup(true);
		}

		@NotNull
		@Override
		public AnAction[] getChildren(@Nullable AnActionEvent anActionEvent) {
			if (plugin) {
				return new AnAction[]{
						debug(goal, mavenProjectInfo)
				};

			} else {
				return new AnAction[]{
						debug(goal, mavenProjectInfo), editAndRun(goal, mavenProjectInfo), delete(goal), new MyCreateAction(goal, mavenProjectInfo)
				};
			}
		}

		public Goal getGoal() {
			return goal;
		}

		private AnAction editAndRun(Goal goal, MavenProjectInfo mavenProject) {
			return new DumbAwareAction("Edit and Run", null, AllIcons.Actions.Edit) {

				@Override
				public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
					Goal edit_and_run = GoalEditor.editGoal("Edit and Run", ApplicationSettings.get(), goal);
					if (edit_and_run != null) {
						RunGoalAction.create(goal, MyIcons.RUN_MAVEN_ICON, true, mavenProject).actionPerformed(anActionEvent);
					}
				}
			};
		}

		private AnAction debug(Goal goalRunAction, MavenProjectInfo mavenProject) {
			return DebugGoalAction.createDebug(goalRunAction, "Debug", MyIcons.ICON, mavenProject);
		}

		private AnAction delete(Goal goal) {
			return new DumbAwareAction("Delete", null, AllIcons.General.Remove) {
				@Override
				public void actionPerformed(@NotNull AnActionEvent anActionEvent) {
					MavenHelperApplicationService.getInstance().getState().removeGoal(goal);
				}
			};
		}

		private class MyCreateAction extends DumbAwareAction {
			private final Goal goal;
			private final MavenProjectInfo mavenProjectInfo;
			private CreateAction createAction;

			public MyCreateAction(@NotNull Goal goal, @NotNull MavenProjectInfo mavenProjectInfo) {
				super("Create Run Configuration");
				this.goal = goal;
				this.mavenProjectInfo = mavenProjectInfo;
				createAction = new CreateAction();
			}

			@Override
			public void actionPerformed(@NotNull AnActionEvent e) {
				createAction.actionPerformed(getAnActionEvent(e));
			}

			@Override
			public void update(@NotNull AnActionEvent e) {
//				createAction.update(getAnActionEvent(e));
			}

			@Override
			public @NotNull ActionUpdateThread getActionUpdateThread() {
				return ActionUpdateThread.BGT;
			}


			@NotNull
			private AnActionEvent getAnActionEvent(@NotNull AnActionEvent e) {
				DataContext dataContext = new DataContext() {
					@Nullable
					@Override
					public Object getData(@NotNull String s) {
						if (Location.DATA_KEY.is(s)) {
							PsiFile data = LangDataKeys.PSI_FILE.getData(e.getDataContext());
							ConfigurationContext fromContext = ConfigurationContext.getFromContext(e.getDataContext());
							PsiFile psiFile = PsiManager.getInstance(e.getProject()).findFile(mavenProjectInfo.mavenProject.getFile());
							MavenProjectsManager manager = MavenProjectsManager.getInstanceIfCreated(e.getProject());
							return new MavenGoalLocation(e.getProject(), psiFile, goal.parse(data, fromContext, mavenProjectInfo, manager));
						}
						return e.getDataContext().getData(s);
					}
				};

				return AnActionEvent.createFromDataContext("MavenRunHelper.CreateRunConfiguration", e.getPresentation(), dataContext);
			}
		}
	}
}
