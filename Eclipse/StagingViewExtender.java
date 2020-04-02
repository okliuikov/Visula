/*---------------------------------------------------------------------------------------------
 *  (c) Copyright 2019 - 2020 Micro Focus or one of its affiliates.

 *  The only warranties for products and services of Micro Focus and its affiliates and licensors
 *  ("Micro Focus") are as may be set forth in the express warranty statements accompanying such
 *  products and services. Nothing herein should be construed as constituting an additional warranty
 *  Micro Focus shall not be liable for technical or editorial errors or omissions contained herein.
 *  The information contained herein is subject to change without notice.

 *  Except as specifically indicated otherwise, this document contains confidential information and
 *  a valid license is required for possession, use or copying. If this work is provided to the
 *  U.S. Government, consistent with FAR 12.211 and 12.212, Commercial Computer Software, Computer
 *  Software Documentation, and Technical Data for Commercial Items are licensed to the U.S. Government
 *  under vendor's standard commercial license.
 *--------------------------------------------------------------------------------------------*/

package com.microfocus.dimensions.plugin.ui;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.egit.ui.internal.dialogs.CommitMessageComponent;
import org.eclipse.egit.ui.internal.dialogs.SpellcheckableMessageArea;
import org.eclipse.egit.ui.internal.staging.StagingView;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.ActionContributionItem;
import org.eclipse.jface.action.ControlContribution;
import org.eclipse.jface.action.IContributionItem;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.TraverseEvent;
import org.eclipse.swt.events.TraverseListener;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IActionBars;

import com.microfocus.common.plugin.request.Request;
import com.microfocus.common.plugin.rest.DataProvider;
import com.microfocus.common.plugin.rest.DmErrorResults;
import com.microfocus.common.plugin.sso.AuthorizationException;
import com.microfocus.common.plugin.utils.ConfigReadingException;
import com.microfocus.dimensions.common.EclipseGitRemoteInfo;
import com.microfocus.dimensions.plugin.Activator;
import com.microfocus.dimensions.plugin.jgit.services.EclipseDataManager;
import com.microfocus.dimensions.plugin.utils.GitUtils;
import com.microfocus.dimensions.plugin.utils.LoggerHelper;
import com.microfocus.dimensions.plugin.utils.StringUtils;

@SuppressWarnings("restriction")
public class StagingViewExtender {

    private static HashMap<StagingView, StagingViewExtender> instances;
    private StagingView stagingView = null;

    private ApplyDefaultRequestContribution applyDefaultRequestContribution;
    private Action selectDefaultRequestAction;
    private Action removeDefaultRequestAction;

    private Timer updateTimer;
    // TODO resources
    private final String noDefaultRequestText = "<Select Request>";
    private Repository lastKnownRepository;

    private StagingViewExtender(StagingView stagingView) {

        this.stagingView = stagingView;

        addToolBarCommands();

        setupTimer();
        updateCommands();
    }

    public static StagingViewExtender setup(StagingView stagingView) {
        if (instances == null) {
            instances = new HashMap<>();
        }
        if (instances.get(stagingView) == null) {
            instances.put(stagingView, new StagingViewExtender(stagingView));
        }
        return instances.get(stagingView);
    }

    public static void remove(StagingView stagingView) {
        if (instances == null) {
            return;
        }
        StagingViewExtender extender = instances.get(stagingView);
        if (extender != null) {
            extender.stopTimer();
            extender.detachView();
        }
        instances.remove(stagingView);
    }


    public String getRequestNameInBrackets() {
        String requestName = getRequestName();
        if (StringUtils.isNullEmpty(requestName)) {
            return "";
        }
        return "[" + requestName + "]";
    }

    private void detachView() {
        stagingView = null;
    }
    
    private void stopTimer() {
        updateTimer.cancel();
    }

    private void setupTimer() {
        updateTimer = new Timer();
        updateTimer.scheduleAtFixedRate(new TimerTask() {

            @Override
            public void run() {
                Display.getDefault().asyncExec(new Runnable() {
                    @Override
                    public void run() {
                        updateCommands();
                    }
                });
            }
        }, 0, 500);
    }

    private void updateCommands() {
        if (stagingView == null) {
            return;
        }
        Repository repository = stagingView.getCurrentRepository();

        boolean enabled = applyDefaultRequestContribution.isEnabled();
        if (repository == null || lastKnownRepository != repository) {
            if (repository == null || GitUtils.getDimensionsScmURI(repository) == null) {
                enabled = false;
                setDefaultRequest("", "");
            } else {
                enabled = true;
                setDefaultRequest(GitUtils.getDefaultRequestName(repository),
                        GitUtils.getDefaultRequestTitle(repository));
            }
        }
        lastKnownRepository = repository;

        if (enabled != applyDefaultRequestContribution.isEnabled()) {
            // do update actions and tool bar
            applyDefaultRequestContribution.setEnabled(enabled);
            selectDefaultRequestAction.setEnabled(enabled);
            removeDefaultRequestAction.setEnabled(enabled);
            IActionBars actionBars = stagingView.getViewSite().getActionBars();
            actionBars.updateActionBars();
        }
    }

    private void addToolBarCommands() {
        IActionBars actionBars = stagingView.getViewSite().getActionBars();
        IToolBarManager manager = actionBars.getToolBarManager();

        String targetActionId = null;
        // the goal is to insert our actions before 'Compare Mode' one added by staging
        // view
        for (IContributionItem item : manager.getItems()) {
            if (item instanceof ActionContributionItem) {
                ActionContributionItem action = (ActionContributionItem) item;
                if (action.getAction().getText()
                        .equalsIgnoreCase(org.eclipse.egit.ui.internal.UIText.StagingView_CompareMode)) {
                    targetActionId = action.getId();
                    if (StringUtils.isNullEmpty(targetActionId)) {
                        targetActionId = org.eclipse.egit.ui.internal.UIText.StagingView_CompareMode;
                        action.setId(targetActionId);
                        break;
                    }
                }
            }
        }
        addApplyRequestAction(manager, targetActionId);
        addSelectRequestAction(manager, targetActionId);
        addRemoveRequestAction(manager, targetActionId);

        if (!StringUtils.isNullEmpty(targetActionId)) {
            manager.insertBefore(targetActionId, new Separator());
        }

        actionBars.updateActionBars();
    }

    private void addApplyRequestAction(IToolBarManager manager, String targetPredecessorActionId) {
    	
    	applyDefaultRequestContribution = new ApplyDefaultRequestContribution();
		if (!StringUtils.isNullEmpty(targetPredecessorActionId)) {
			manager.insertBefore(targetPredecessorActionId, applyDefaultRequestContribution);
		} else {
			manager.add(applyDefaultRequestContribution);
		}
		
    }

    private void addRemoveRequestAction(IToolBarManager manager, String targetPredecessorActionId) {
        removeDefaultRequestAction = new Action("", Activator.getDefault().getImageDescriptor(IPluginImages.REMOVE)) {

            @Override
            public void run() {
                if (!GitUtils.isValidDimensionsRepository(stagingView.getCurrentRepository())) {
                    return;
                }
                setDefaultRequest(null, null);
            }
        };
        removeDefaultRequestAction.setToolTipText("Remove Default Request");

        if (!StringUtils.isNullEmpty(targetPredecessorActionId)) {
            manager.insertBefore(targetPredecessorActionId, new ActionContributionItem(removeDefaultRequestAction));
        } else {
            manager.add(new ActionContributionItem(removeDefaultRequestAction));
        }
    }

    private void addSelectRequestAction(IToolBarManager manager, String targetPredecessorActionId) {
        selectDefaultRequestAction = new Action("",
                Activator.getDefault().getImageDescriptor(IPluginImages.SET_DEFAULT_REQUEST)) {

            @Override
            public void run() {
                if (!GitUtils.isValidDimensionsRepository(stagingView.getCurrentRepository())) {
                    return;
                }
                selectDefaultRequest();
            }
        };
        selectDefaultRequestAction.setToolTipText("Set Default Request");

        if (!StringUtils.isNullEmpty(targetPredecessorActionId)) {
            manager.insertBefore(targetPredecessorActionId, new ActionContributionItem(selectDefaultRequestAction));
        } else {
            manager.add(new ActionContributionItem(selectDefaultRequestAction));
        }
    }

    private void selectDefaultRequest() {
        Shell parentShell = stagingView.getViewSite().getShell();
        Repository repository = stagingView.getCurrentRepository();
        Point preferredLocation = parentShell.getDisplay().getCursorLocation();

        DataProvider provider = null;
        AuthorizationException error = null;
        EclipseGitRemoteInfo repositoryInfo = new EclipseGitRemoteInfo(GitUtils.getDimensionsScmURI(repository));
        try {
            provider = EclipseDataManager.getProvider(repository, parentShell, repositoryInfo);
        } catch (AuthorizationException e) {
            error = e;
            e.printStackTrace();
        } catch (ConfigReadingException e) {
            e.printStackTrace();
            error = new AuthorizationException("") {
                private static final long serialVersionUID = 1L;

                @Override
                public DmErrorResults getErrorCase() {
                    return DmErrorResults.FAILED;
                }
            };
        }
        IRequestSelectionListener selectionListener = new IRequestSelectionListener() {

            @Override
            public void requestSelected(Request request) {
                setDefaultRequest(request.getName(), request.getTitle());
            }
        };
        SelectRequestDialog dlg = null;
        if (error != null) {
            dlg = new SelectRequestDialog(parentShell, preferredLocation, selectionListener, error);
        } else {
            dlg = new SelectRequestDialog(parentShell, preferredLocation, provider, selectionListener);
        }
        dlg.open();
    }

    private void setDefaultRequest(String requestName, String requestTitle) {

    	String oldText = applyDefaultRequestContribution.getText(); 
        if (!StringUtils.isNullEmpty(requestName)) {
            applyDefaultRequestContribution.setText("[" + requestName + "]");
            applyDefaultRequestContribution.setToolTipText(requestTitle);
        } else {
        	applyDefaultRequestContribution.setText(noDefaultRequestText);
        	applyDefaultRequestContribution.setToolTipText("");
        }
        if (oldText.equals(applyDefaultRequestContribution.getText())) {
            return;
        }
        // update layout to adjust the contibution's button width
        stagingView.getViewSite().getActionBars().updateActionBars();

        applyRequest();

        Repository repository = stagingView.getCurrentRepository();
        if (GitUtils.isValidDimensionsRepository(repository)) {
            GitUtils.setDefaultRequestName(repository, requestName);
            GitUtils.setDefaultRequestTitle(repository, requestTitle);
        }
    }


    private void applyRequest() {
        try {
            Field field = stagingView.getClass().getDeclaredField("commitMessageComponent");
            field.setAccessible(true);
            Object rawCommitMessageComponent = field.get(stagingView);
            if (rawCommitMessageComponent instanceof CommitMessageComponent) {
                CommitMessageComponent commitMessageComponent = (CommitMessageComponent) rawCommitMessageComponent;

                field = commitMessageComponent.getClass().getDeclaredField("commitText");
                field.setAccessible(true);
                Object rawCommitText = field.get(commitMessageComponent);

                if (rawCommitText instanceof SpellcheckableMessageArea) {
                    SpellcheckableMessageArea commitText = (SpellcheckableMessageArea) rawCommitText;
                    String messageWithRequestId = addRequestToCommitMessage(commitText.getText());
                    commitText.setText(messageWithRequestId);
                }
            }
        } catch (NoSuchFieldException | SecurityException | IllegalArgumentException | IllegalAccessException e) {
            LoggerHelper.error("Error adding request to commit message", this.getClass());
            e.printStackTrace();
        }
    }

    private String addRequestToCommitMessage(String originalMessage) {

        String requestName = getRequestName();
        String updatedMessage = originalMessage;

        if (StringUtils.isNullEmpty(requestName)) {
            // empty request means that we need to clear out old request
            updatedMessage = originalMessage.replaceAll("(?<=\\[).*?(?=\\])", "");
            // remove brackets itself
            return updatedMessage.replaceAll("\\[|\\]", "");
        } else {
            if (originalMessage.contains(getRequestNameInBrackets())) {
                // nothing to do
                return originalMessage;
            }
            // try replace old request with new one
            updatedMessage = originalMessage.replaceAll("(?<=\\[).*?(?=\\])", requestName);

            if (updatedMessage.equals(originalMessage)) {
                // we haven't replaced anything
                if (!updatedMessage.endsWith(" ")) {
                    updatedMessage += " ";
                }
                return updatedMessage + getRequestNameInBrackets();
            }
        }
        return updatedMessage;
    }

    private String getRequestName() {
        String currentRequestName = applyDefaultRequestContribution.getText();
        if (noDefaultRequestText.equals(currentRequestName)) {
            return "";
        }
        if (currentRequestName.startsWith("[") && currentRequestName.endsWith("]")) {
            currentRequestName = currentRequestName.substring(1, currentRequestName.length() - 1);
        }
        return currentRequestName;
    }
    
    // Need this special contribution because default action (button) won't get properly rendered when adding text to it
    private class ApplyDefaultRequestContribution extends ControlContribution {

        // button that will be added to tool bar 
        private Button button;
        
        boolean isMouseOver = false;
        int textIndent = 6;

        public ApplyDefaultRequestContribution() {
        	super("applyDefaultRequestId");
        }

        @Override
        public boolean isEnabled() {
            if (button == null) {
                System.out.println("Control is not created yet: cannot calculate enablement");
                return false;
            } 
            return button.isEnabled();
        }
        
        public void setEnabled(boolean enabled) {
            if (button != null) {
                button.setEnabled(enabled);
            } else {
                System.out.println("Control is not created yet: cannot apply enablement");
            }
        }

        public String getText() {
            return button == null ? "" : button.getText();
        }

        public void setToolTipText(String requestTitle) {
			button.setToolTipText(requestTitle);
		}

		public void setText(String text) {
        	if (button != null) {
        	    button.setText(text);
        	} else {
        	    System.out.println("Control is not created yet: cannot apply request text");
        	}
        }
        
        @Override
        protected Control createControl(Composite parent) {

            parent.getDisplay().addFilter(SWT.MouseMove, new Listener() {
                @Override
                public void handleEvent(Event e) {
                    isMouseOver = e.widget == button;
                }
            });

            button = new Button(parent, SWT.PUSH | SWT.FLAT);
            setText("");

            button.addPaintListener(new PaintListener() {
                @Override
                public void paintControl(PaintEvent event) {
                    if (isMouseOver || button.isFocusControl() || !button.isEnabled()) {
                        // use default drawing in these cases
                        return;
                    }
                    event.gc.setBackground(button.getParent().getBackground());
                    event.gc.fillRectangle(event.x, event.y, event.width, event.height);

                    int x = button.getSize().x / 2 - event.gc.textExtent(button.getText(), 0).x / 2;
                    if (x < textIndent) {
                        x = textIndent;
                    }
                    event.gc.drawText(button.getText(), x, event.y + 3, SWT.DRAW_TRANSPARENT);
                }
            });

        	button.addSelectionListener(new SelectionAdapter() {
				@Override
				public void widgetSelected(SelectionEvent e) {
				    handleButtonClick();
				}

			});
        	
        	button.addTraverseListener(new TraverseListener()
        	  {
                @Override
                public void keyTraversed(final TraverseEvent event) {
                    if (event.detail == SWT.TRAVERSE_RETURN) {
                        handleButtonClick();
                    }
                }
        	  });

            return button;
        }

        private void handleButtonClick() {
            if (!GitUtils.isValidDimensionsRepository(stagingView.getCurrentRepository())) {
                return;
            }
            if (getText().equals(noDefaultRequestText)) {
                selectDefaultRequest();
            } else {
                applyRequest();
            }
        }
        
        @Override
        public int computeWidth(Control control) {
            String text = getText(); 
            if (StringUtils.isNullEmpty(text)) {
                return 20;
            }
            GC gc = new GC(button);
            int preferredWidth = gc.textExtent(text, 0).x + 2 * textIndent;
            gc.dispose();
            return preferredWidth;
        }
    }
}


