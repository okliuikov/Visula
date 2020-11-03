/*---------------------------------------------------------------------------------------------
 *  (c) Copyright 2020 Micro Focus or one of its affiliates.

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

package com.serena.eclipse.dimensions.internal.ui.controls;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;

import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.PaintEvent;
import org.eclipse.swt.events.PaintListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Link;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.ScrollBar;
import org.eclipse.swt.widgets.Scrollable;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.ui.PlatformUI;

/**
 * A customizable message over a control.
 */
public class MessageOverlay {

    private List<Composite> parents;
    private Control objectToOverlay;
    private Shell shell;
    private Label label;
    private ControlListener controlListener;
    private DisposeListener disposeListener;
    private PaintListener paintListener;
    private boolean hasClientArea;
    private Scrollable scrollableToOverlay;

    private Link link;
    private boolean isWindows = System.getProperty("os.name").toLowerCase().indexOf("win") >= 0;

    private DesiredAction desiredAction = DesiredAction.NONE;
    // schedules two kind of tasks: show with delay task and another one to hide overlay when parent control gets invisible
    private Timer serviceTimer = new Timer();
    // show overlay with delay task
    private TimerTask actionWithDelayTask = null;
    private Runnable action;

    private enum DesiredAction {
        NONE, SHOW, HIDE
    }

    /**
     * Creates an overlay over the control and displays centered information message
     * or link
     * 
     * @param objectToOverlay
     * @param action          Runnable to call when the link is clicked.
     */
    public MessageOverlay(Control objectToOverlay, Runnable action) {

        Objects.requireNonNull(objectToOverlay);
        Objects.requireNonNull(action);

        this.action = action;

        this.objectToOverlay = objectToOverlay;

        // if the object to overlay is an instance of Scrollable (e.g. Shell) then it
        // has the getClientArea method, which is preferable over Control.getSize
        if (objectToOverlay instanceof Scrollable) {
            hasClientArea = true;
            scrollableToOverlay = (Scrollable) objectToOverlay;
        } else {
            hasClientArea = false;
            scrollableToOverlay = null;
        }

        // save the parents of the object, so we can add/remove listeners to them
        parents = new ArrayList<Composite>();
        Composite parent = objectToOverlay.getParent();
        while (parent != null) {
            parents.add(parent);
            parent = parent.getParent();
        }

        // listener to track position and size changes in order to modify the overlay
        // bounds as well
        controlListener = new ControlListener() {
            @Override
            public void controlMoved(ControlEvent e) {
                reposition();
            }

            @Override
            public void controlResized(ControlEvent e) {
                reposition();
            }
        };

        // listener to track paint changes, like when the object or its parents become
        // not visible (for example changing tab in a TabFolder)
        paintListener = new PaintListener() {
            @Override
            public void paintControl(PaintEvent arg0) {
                reposition();
            }
        };

        // listener to remove the overlay if the object to overlay is disposed
        disposeListener = new DisposeListener() {
            @Override
            public void widgetDisposed(DisposeEvent e) {
                close();
            }
        };

        // create the overlay shell
        shell = new Shell(objectToOverlay.getShell(), SWT.NO_TRIM);

        // though we set shell background exactly the same as content area of the
        // overlay,
        // it's a guard that the overlay won't completely hide any important part of UI
        // in the case
        // when its size calculation is wrong
        shell.setAlpha(200);

        // label to display a text
        // style WRAP so if it is too long the text get wrapped
        label = new Label(shell, SWT.WRAP);
        createLink();
        // to center the label
        shell.setLayout(new GridLayout());

        label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));

        // add listeners to the object to overlay
        objectToOverlay.addControlListener(controlListener);
        objectToOverlay.addDisposeListener(disposeListener);
        objectToOverlay.addPaintListener(paintListener);

        // add listeners also to the parents because if they change then also the
        // visibility of our object could change
        for (Composite parent_ : parents) {
            parent_.addControlListener(controlListener);
            parent_.addPaintListener(paintListener);
        }

        shell.addListener(SWT.Traverse, new Listener() {
            public void handleEvent(Event e) {
                // avoid closing by escape
                if (e.detail == SWT.TRAVERSE_ESCAPE) {
                    e.doit = false;
                } else if (e.detail == SWT.TRAVERSE_RETURN) {
                    // Triggers callback action when enter is pressed
                    if (link.isFocusControl()) {
                        action.run();
                    }
                }
            }
        });
        
        // main goal of having this timer task
        // is handling the case when parent of the overlay gets invisible 
        // so overlay shell should also fade out
        // there is no better way to achieve this as SWT doesn't propose 'visibility
        // change' events for the overlay parent
        serviceTimer.schedule(new TimerTask( ) {

            @Override
            public void run() {
                Display.getDefault().asyncExec(new Runnable() {

                    @Override
                    public void run() {
                        // object to overlay is NOT visible, shell is still visible, need to force shell
                        // to fade out
                        if (!isInvalid() && !objectToOverlay.isVisible() && shell.isVisible()) {
                            System.out.println("Forcing shell overlay to fade out (close or hide) gracefully");
                            reposition();
                        }

                    }
                });
            }}, 0, 100);
    }

    public void hide() {
        // react fast, mark desired action but hide without delay
        desiredAction = DesiredAction.HIDE;
        hide_();
    }

    public boolean isInvalid() {
        return shell.isDisposed() || objectToOverlay.isDisposed()
        // handle host view detach/attach activity in eclipse
                || shell.getParent() != objectToOverlay.getShell();
    }

    public void show() {

        schedule(DesiredAction.SHOW);
    }

    public void setMessage(String text, boolean isLink) {
        if (isLink) {
            link.setText("<a>" + text + "</a>");
            label.setText("");
        } else {
            label.setText(text);
            link.setText("");
        }

        link.setVisible(isLink);
        link.setEnabled(true);
        ((GridData) link.getLayoutData()).exclude = !isLink;

        label.setVisible(!isLink);
        ((GridData) label.getLayoutData()).exclude = isLink;

        // adjust the label size accordingly
        shell.layout();
    }

    private void createLink() {
        link = new Link(shell, SWT.WRAP);
        link.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, true, true));
        link.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseUp(MouseEvent event) {
                action.run();
            }
        });
    }

    private void detach() {

        if (!objectToOverlay.isDisposed()) {
            objectToOverlay.removeControlListener(controlListener);
            objectToOverlay.removeDisposeListener(disposeListener);
            objectToOverlay.removePaintListener(paintListener);
        }

        // remove the parents listeners
        for (Composite parent : parents) {
            if (!parent.isDisposed()) {
                parent.removeControlListener(controlListener);
                parent.removePaintListener(paintListener);
            }
        }
    }

    private void show_() {
        if (isInvalid()) {
            return;
        }

        if (!shell.isVisible()) {
            
            // detect currently active shell, we should avoid overlapping it by our shell then
            Shell activeShell = PlatformUI.getWorkbench().getDisplay().getActiveShell();
            
            reposition();
            shell.setVisible(true);
            
            // be safe as much as possible
            if (activeShell != null && !activeShell.isDisposed()) { 
                if (activeShell != objectToOverlay.getShell()) {
                    System.out.println("active shell is: " + activeShell.getText());
                    // bring to front
                    activeShell.setActive();
                }
            }
        }
    }

    private void hide_() {

        if (!shell.isDisposed() && shell.isVisible()) {
            shell.setVisible(false);
        }
    }

    private void close() {

        if (!shell.isDisposed()) {
            shell.close();
        }
    }

    private void reposition() {

        if (isInvalid()) {
            detach();
            close();
            return;
        }

        // if the object is not visible, we hide the overlay and exit
        if (!objectToOverlay.isVisible()) {
            hide_();
            return;
        }

        // if the object is visible we need to find the visible region in order to
        // correctly place the overlay

        // get the display bounds of the object to overlay
        Point objectToOverlayDisplayLocation = objectToOverlay.toDisplay(0, 0);

        Point shellSize;

        Rectangle shellBounds = new Rectangle(objectToOverlayDisplayLocation.x, objectToOverlayDisplayLocation.y, 0, 0);

        // if it has a client area, we prefer that instead of the size
        if (hasClientArea) {
            Rectangle clientArea = scrollableToOverlay.getClientArea();
            shellBounds.x += clientArea.x;
            shellSize = new Point(clientArea.width, clientArea.height);
        } else {
            shellSize = objectToOverlay.getSize();
        }

        shellBounds.width = shellSize.x;
        shellBounds.height = shellSize.y;

        if (objectToOverlay instanceof Table) {
            int height = ((Table) objectToOverlay).getHeaderHeight();
            shellBounds.height -= height;
            if (isWindows) {
                shellBounds.y += height;
            } else {
                ScrollBar bar = ((Table) objectToOverlay).getHorizontalBar();
                if (bar != null) {
                    shellBounds.height -= bar.getSize().y;
                }
            }
        }

        // theme switching support
        // using shell.setBackgroundMode(SWT.INHERIT_DEFAULT) won't help..
        // label background in dark theme is different than overlay one
        Color background = objectToOverlay.getBackground();
        if (!shell.getBackground().equals(background) || !link.getBackground().equals(background)
                || !label.getBackground().equals(background)) {
            shell.setBackground(background);

            // SWT doesn't support changing the foreground of the link dynamically..
            // so recreating the link from the scratch
            String oldText = link.getText();
            link.dispose();
            createLink();
            link.setText(oldText);
            shell.layout(true, true);

            link.setBackground(background);
            label.setBackground(background);
        }

        if (!isWindows) {
//        	Rectangle monitorBounds = shell.getDisplay().getMonitors()[0].getBounds();
            Rectangle monitorBounds = shell.getMonitor().getBounds();
            shellBounds = shellBounds.intersection(monitorBounds);
        }
        shell.setBounds(shellBounds);
    }

    // Linux: switching rapidly from visible to invisible and back several times
    // leads to UI
    // thread deadlocking.. Use the same approach on windows for consistency
    private void schedule(DesiredAction action) {
        desiredAction = action;

        if (isInvalid()) {
            return;
        }
        // can skip the request if we're already in desired condition
        if (desiredAction == DesiredAction.SHOW) {
            if (shell.isVisible()) {
                return;
            }
        } else if (desiredAction == DesiredAction.HIDE) {
            if (!shell.isVisible()) {
                return;
            }
        }

        if (actionWithDelayTask != null) {
            return;
        }

        // apply desired state with delay
        actionWithDelayTask = new TimerTask() {
            @Override
            public void run() {

                Display.getDefault().asyncExec(new Runnable() {

                    @Override
                    public void run() {

                        if (desiredAction == DesiredAction.SHOW) {
                            show_();
                        } else if (desiredAction == DesiredAction.HIDE) {
                            hide_();
                        }
                        actionWithDelayTask = null;
                    }
                });
            };
        };
        serviceTimer.schedule(actionWithDelayTask, 300);
    }
}

