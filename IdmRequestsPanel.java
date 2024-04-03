/*---------------------------------------------------------------------------------------------
 *  (c) Copyright 2008 - 2019 Micro Focus or one of its affiliates.

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

package com.serena.eclipse.dimensions.internal.ui.views;

import java.net.MalformedURLException;
import java.net.URL;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.ISchedulingRule;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.CheckboxTableViewer;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewer;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.OwnerDrawLabelProvider;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerColumn;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.GC;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.PaletteData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.graphics.RGB;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.TextLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.ui.PartInitException;
import org.eclipse.ui.PlatformUI;

import com.serena.dmclient.api.IDMHelper;
import com.serena.dmclient.api.IDMReportRequest;
import com.serena.dmclient.api.IDMRequestIdentifier;
import com.serena.dmclient.api.IDMRequestPage;
import com.serena.dmclient.api.IDMRequestV2;
import com.serena.dmclient.api.Project;
import com.serena.dmclient.objects.IDMRequestAttributes;
import com.serena.dmclient.objects.IDMRequestFilter;
import com.serena.dmclient.objects.Product;
import com.serena.dmclient.objects.RequestProvider;
import com.serena.eclipse.dimensions.core.DMException;
import com.serena.eclipse.dimensions.core.DimensionsConnectionDetailsEx;
import com.serena.eclipse.dimensions.core.OctaneRequestTypes;
import com.serena.eclipse.dimensions.core.Session;
import com.serena.eclipse.dimensions.core.util.Utils;
import com.serena.eclipse.dimensions.internal.ui.DMUIPlugin;
import com.serena.eclipse.dimensions.internal.ui.IDMImages;
import com.serena.eclipse.dimensions.internal.ui.Messages;
import com.serena.eclipse.dimensions.internal.ui.TableColumnModel;
import com.serena.eclipse.dimensions.internal.ui.editors.ReportBrowserEditorInput;
import com.serena.eclipse.dimensions.internal.ui.model.IdmRequestData;
import com.serena.eclipse.dimensions.internal.ui.model.IdmWorkingListHolder;
import com.serena.eclipse.ui.SortImageHelper;

import merant.adm.dimensions.util.StringUtils;

public class IdmRequestsPanel implements IMenuListener {

    public enum DisplayMode {
        INBOX, WORKING_LIST, REPORT
    }
    private String reportId;

    private List<IdmRequestData> requestsToDisplay;
    private List<IdmRequestData> inboxRequests;
    private IdmWorkingListHolder workingListHolder;
    private IdmRequestsPanel.DisplayMode displayMode = IdmRequestsPanel.DisplayMode.INBOX;
    private MenuManager menuManager;
    // change #1
    // change #2
    // change #3
    // change #4
    // change #5
    // change #6
    // change #7
    // change #91

    private Table table;
    private TableViewer tableViewer;
    private boolean allowCheckboxesInTable;
    private boolean allowContextMenu;
    private long lastDataRequestID;

    private DimensionsConnectionDetailsEx connection;
    private Composite parent;

    private final String jobFamily = "IdmRequestsPanel_Jobs";
    private final SchedulingRule schedulingRule = new SchedulingRule();

    enum SearchFields {
        TITLE, TYPE_NAME;
    }

    private Map<SearchFields, String> filters = new HashMap<SearchFields, String>();
//    private Image imageAscending;
//    private Image imageDescending;

    private SortImageHelper imageHelper;

    public IdmRequestsPanel(Composite parent, DimensionsConnectionDetailsEx connection, boolean allowCheckboxesInTable,
            boolean allowContextMenu) {
        this.connection = connection;
        this.parent = parent;
        this.allowCheckboxesInTable = allowCheckboxesInTable;
        this.allowContextMenu = allowContextMenu;
        workingListHolder = new IdmWorkingListHolder(connection);
        createControl();
    }

    public CheckboxTableViewer getCheckboxTableViewer() {
        return (CheckboxTableViewer) getTableViewer();
    }

    public TableViewer getTableViewer() {
        return tableViewer;
    }

    @SuppressWarnings("unchecked")
    public void setCheckedRequests(final List<String> requestNames) {

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Job.getJobManager().join(jobFamily, new NullProgressMonitor());

                    Display.getDefault().asyncExec(new Runnable() {

                        @Override
                        public void run() {
                            if (table == null || table.isDisposed()) {
                                return;
                            }
                            for (IdmRequestData request : (List<IdmRequestData>) tableViewer.getInput()) {
                                if (requestNames.contains(request.name)) {
                                    getCheckboxTableViewer().setChecked(request, true);
                                }
                            }
                        }
                    });
                } catch (Exception ignored) {
                }
            }
        }).start();
    }

    public List<String> getCheckedRequestNames() {
        ArrayList<String> result = new ArrayList<String>();
        for (Object requestObject : getCheckboxTableViewer().getCheckedElements()) {
            IdmRequestData request = (IdmRequestData) requestObject;
            result.add(request.name);
        }
        return result;
    }
    
    public List<IdmRequestData> getCheckedRequests() {
        ArrayList<IdmRequestData> result = new ArrayList<IdmRequestData>();
        for (Object requestObject : getCheckboxTableViewer().getCheckedElements()) {
            IdmRequestData request = (IdmRequestData) requestObject;
            result.add(request);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public List<String> getAllRequestNames() {
        ArrayList<String> result = new ArrayList<String>();
        for (IdmRequestData request : (List<IdmRequestData>) tableViewer.getInput()) {
            result.add(request.name);
        }
        return result;
    }

    public void showReportRequests(String reportId) {
        displayMode = IdmRequestsPanel.DisplayMode.REPORT;
        this.reportId = reportId;
        refresh(false);
    }
    
    public void showInboxRequests() {
        displayMode = IdmRequestsPanel.DisplayMode.INBOX;
        refresh(false);
    }

    public void showActiveRequests() {
        displayMode = IdmRequestsPanel.DisplayMode.WORKING_LIST;
        refresh(false);
    }

    public void removeFromWorkingList(String[] requestNames) throws DMException {
        workingListHolder.remove(Arrays.asList(requestNames));
    }

    private void refresh(boolean force) {
        final long requestID = new Date().getTime();
        lastDataRequestID = requestID;
        
        requestsToDisplay = new ArrayList<IdmRequestData>();

        if (force) {
            inboxRequests = null;
            workingListHolder.clear();
        }
        if (table.getColumnCount() == 0) {
            createColumns();
        }
        // clear the table to avoid possible user activity while data query is in
        // progress
        tableViewer.setInput(requestsToDisplay);
        tableViewer.refresh();
        table.setCursor(new Cursor(table.getDisplay(), SWT.CURSOR_WAIT));
        
        String jobName = Messages.objList_updateJob;
        if (hasFilter()) {
            jobName = Messages.objList_filterApplyingJob;
        }

        Job job = new Job(jobName) {
            @Override
            protected IStatus run(IProgressMonitor monitor) {
                try {
                    List<IdmRequestData> list = new ArrayList<IdmRequestData>();
                    switch (displayMode) {
                    case INBOX:
                        // ensure favorites is up to date
                        workingListHolder.getRequestsData();
                        list = getInboxRequests(connection);
                        break;

                    case WORKING_LIST:
                        list = workingListHolder.getRequestsData();
                        break;

                    case REPORT:
                        workingListHolder.getRequestsData();
                        list = getReportRequests();

                    default:
                        break;
                    }
                    if (lastDataRequestID == requestID) {
                        requestsToDisplay = list;
                        Display.getDefault().asyncExec(new Runnable() {

                            @Override
                            public void run() {
                                if (table == null || table.isDisposed()) {
                                    return;
                                }
                                tableViewer.setInput(requestsToDisplay);
                            }
                        });
                    }
                } catch (final DMException e) {
                    Display.getDefault().asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            if (parent == null || parent.isDisposed()) {
                                return;
                            }
                            DMUIPlugin.getDefault().handle(e, parent.getShell());
                        }
                    });
                } finally {
                    Display.getDefault().asyncExec(new Runnable() {
                        @Override
                        public void run() {
                            if (parent == null || parent.isDisposed()) {
                                return;
                            }
                            tableViewer.getTable().setCursor(null);
                            tableViewer.refresh();
                        }
                    });
                }
                
                return Status.OK_STATUS;
            }

            @Override
            public boolean belongsTo(Object family) {
                return family.equals(jobFamily);
            }
        };
        job.setRule(schedulingRule);
        job.schedule();
    }

    @Override
    public void menuAboutToShow(IMenuManager manager) {

        IdmRequestData request = getSelectedRequest();

        if (request != null) {
            if (workingListHolder.contains(request)) {

                Action deactivateAction = new Action(Messages.octane_removeFavorites) {

                    @Override
                    public void run() {
                        final IdmRequestData requestToRemove = getSelectedRequest();
                        if (requestToRemove == null) {
                            return;
                        }
                        Job job = new Job(Messages.objList_updateWorkingList) {
                            @Override
                            protected IStatus run(IProgressMonitor monitor) {
                                try {

                                    workingListHolder.remove(requestToRemove);
                                    if (displayMode == IdmRequestsPanel.DisplayMode.WORKING_LIST) {

                                        Display.getDefault().asyncExec(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (parent == null || parent.isDisposed()) {
                                                    return;
                                                }
                                                // remove this request from the list
                                                refresh(false);
                                            }
                                        });
                                    }
                                } catch (final DMException e) {
                                    Display.getDefault().asyncExec(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (parent == null || parent.isDisposed()) {
                                                return;
                                            }
                                            DMUIPlugin.getDefault().handle(e, parent.getShell());
                                        }
                                    });
                                }
                                return Status.OK_STATUS;
                            }

                            @Override
                            public boolean belongsTo(Object family) {
                                return family.equals(jobFamily);
                            }
                        };
                        job.setRule(schedulingRule);
                        job.schedule();
                    }
                };
                manager.add(deactivateAction);
            } else {

                Action activateAction = new Action(Messages.octane_addFavorites) {

                    @Override
                    public void run() {

                        final IdmRequestData requestToAdd = getSelectedRequest();
                        if (requestToAdd == null) {
                            return;
                        }
                        // TODO job timeout
                        Job job = new Job(Messages.objList_updateWorkingList) {
                            @Override
                            protected IStatus run(IProgressMonitor monitor) {
                                try {
                                    workingListHolder.add(requestToAdd);
                                } catch (final DMException e) {
                                    Display.getDefault().asyncExec(new Runnable() {
                                        @Override
                                        public void run() {
                                            if (parent == null || parent.isDisposed()) {
                                                return;
                                            }
                                            DMUIPlugin.getDefault().handle(e, parent.getShell());
                                        }
                                    });
                                }
                                return Status.OK_STATUS;
                            }

                            @Override
                            public boolean belongsTo(Object family) {
                                return family.equals(jobFamily);
                            }

                        };
                        job.setRule(schedulingRule);
                        job.schedule();
                    }
                };
                manager.add(activateAction);
            }
            OpenRequestAction openRequestAction = new OpenRequestAction();
            manager.add(openRequestAction);
        }

        RefreshAction refreshAction = new RefreshAction();
        manager.add(refreshAction);
    }

    public void applyFilter(String searchKey, SearchFields field) {
        switch (field) {
            case TITLE:
                filters.put(SearchFields.TITLE, searchKey);
                refresh(true);
                break;
            case TYPE_NAME:
                if(searchKey.equals(OctaneRequestTypes.ALL)) {
                    filters.remove(SearchFields.TYPE_NAME);
                } else {
                    filters.put(SearchFields.TYPE_NAME, searchKey);
                }
                refresh(true);
        }
    }

    public void forceRefresh() {
        // skip this refresh attempt in case when previous refresh is in progress
        if (Job.getJobManager().find(jobFamily).length == 0) {
            refresh(true);
        }
    }
    
    void openSelectedRequestInBrowser() {
        if (getSelectedRequest() != null) {
            try {
                if (connection.isSbmRequestProvider()) {
                    DMUIPlugin.showInBrowser(new ReportBrowserEditorInput(getSelectedRequest().name, getSelectedRequest().octaneLink));
                } else {
                    PlatformUI.getWorkbench().getBrowserSupport().getExternalBrowser().openURL(new URL(getSelectedRequest().octaneLink));
                }
            } catch (PartInitException ignored) {
            } catch (MalformedURLException ignored) {
            }
        }
    }
    
    private void createControl() {

        int tableStyle = SWT.V_SCROLL | SWT.H_SCROLL | SWT.SINGLE | SWT.FULL_SELECTION;

        if (allowCheckboxesInTable) {
            tableStyle |= SWT.CHECK;
        }
        table = new Table(parent, tableStyle);
        imageHelper = new SortImageHelper(table);

        GridData gridData = new GridData(GridData.FILL_HORIZONTAL | GridData.FILL_VERTICAL);
        table.setLayoutData(gridData);
        table.setHeaderVisible(true);

        // 'best fit' action support for columns
        table.addListener(SWT.MeasureItem, new Listener() {
            public void handleEvent(Event event) {
                TableColumn column = table.getColumn(event.index);
                int maxWidth = event.gc.textExtent(column.getText(), 0).x + 20;
                int width = 0;

                if (requestsToDisplay != null) {
                    for (IdmRequestData request : requestsToDisplay) {
                        width = event.gc.textExtent(request.getText(column.getText()), 0).x + 15;
                        if (maxWidth < width) {
                            maxWidth = width;
                        }
                    }
                }
                event.width = maxWidth;
            }
        });
        
        table.addListener(SWT.Paint, new Listener() {
            public void handleEvent(Event event) {
                if (table.getItemCount() != 0 || Job.getJobManager().find(jobFamily).length != 0) {
                    return;
                }
                String message = null;
                if (connection.isOffline() || !connection.isSessionOpen()) {
                    message = Messages.octane_requestsAreUsedToPlan;
                } else {
                    switch (displayMode) {
                    case INBOX:
                        if (hasFilter()) {
                            message = Messages.octane_thereAreNoRequestsByFilter;
                        } else {
                            message = Messages.octane_createRequestToGetStarted;
                        }
                        break;

                    case WORKING_LIST:
                        message = Messages.octane_favoriteRequestsWillAppearHere; 
                        break;

                    case REPORT:
                        message = Messages.octane_thereAreNoRequestsByReport;
                        break;

                    default:
                        break;
                    }
                }
                if (!StringUtils.isBlank(message)) {

                    int minIndent = 10;
                    List<String> lines = splitText(event.gc, message, table.getSize().x - minIndent * 2);
                    int lineInterval = event.gc.getFont().getFontData()[0].getHeight() - 1;

                    int y = table.getSize().y / 2;
                    if (lines.size() > 1) {
                        int totalHeight = lines.size()
                                * (event.gc.getFont().getFontData()[0].getHeight() + lineInterval);
                        y -= totalHeight / 2;
                        if (y < 0) {
                            y = 10;
                        }
                    }
                    for (String line : lines) {
                        // center each line individually
                        int x = table.getSize().x / 2 - event.gc.textExtent(line, 0).x / 2;
                        if (x < minIndent) {
                            x = minIndent;
                        }
                        event.gc.drawText(line, x, y, true /* isTransparent */);

                        y += event.gc.textExtent(line, 0).y + lineInterval;
                    }
                }
            }
            
            private List<String> splitText(final GC gc, String text, final int maxAllowedWidth) {

                ArrayList<String> lines = new ArrayList<String>();
                if (gc.textExtent(text, 0).x < maxAllowedWidth) {
                    lines.add(text);
                    return lines;
                }
                String[] words = text.split(" ");

                String line = "";
                int wordIndex = 0;
                do {
                    // check current line plus the next word 
                    if (gc.textExtent(line, 0).x + gc.textExtent(" ", 0).x + gc.textExtent(words[wordIndex], 0).x <= maxAllowedWidth) {
                        if (line.length() > 0) {
                            line += " ";
                        }
                        line += words[wordIndex];
                        wordIndex++;
                    } else {
                        // start new line
                        lines.add(line);
                        line = "";
                    }
                } while (wordIndex < words.length);
                
                if (line.length() > 0) {
                    lines.add(line);
                }
                return lines;
            }
        });


        if (allowCheckboxesInTable) {
            tableViewer = new CheckboxTableViewer(table);
        } else {
            tableViewer = new TableViewer(table);
        }
        tableViewer.setContentProvider(ArrayContentProvider.getInstance());

        // simple sorting support
        tableViewer.setComparator(new ViewerComparator() {

            Collator collator = Collator.getInstance(Locale.getDefault());

            @Override
            public int compare(Viewer viewer, Object e1, Object e2) {
                IdmRequestData one = (IdmRequestData) e1;
                IdmRequestData another = (IdmRequestData) e2;

                TableColumn sortColumn = null;
                for (TableColumn column : table.getColumns()) {
                    if (column.getImage() != null) {
                        sortColumn = column;
                        break;
                    }
                }
//                if (table.getSortColumn() == null) {
//                    return 0;
//                }
                if (sortColumn == null) {
                    return 0;
                }
                // TODO first time sort column is not set but UI can indicate sorting
//                String caption = table.getSortColumn().getText();
                String caption = sortColumn.getText();
                int result = 0;
                if (caption.equals(IdmRequestData.COLUMN_NAME)) {
                    result = collator.compare(one.name, another.name);
                } else if (caption.equals(IdmRequestData.COLUMN_STATUS)) {
                    result = collator.compare(one.status, another.status);
                } else if (caption.equals(IdmRequestData.COLUMN_TITLE)) {
                    result = collator.compare(one.title, another.title);
                } else if (caption == IdmRequestData.COLUMN_UPDATE_DATE) {
                    result = collator.compare(one.updateDate, another.updateDate);
                }
//                if (table.getSortDirection() == SWT.DOWN) {
//                    result = -result;
//                }
                if (sortColumn.getImage() == imageHelper.getImageDescending()) {
                    result = -result;
                }
                return result;
            }
        });
        ColumnViewerToolTipSupport.enableFor(tableViewer);

        if (allowContextMenu) {
            createContextMenu();
            table.addMouseListener(new MouseListener() {
                @Override
                public void mouseUp(MouseEvent arg0) {
                    // do nothing
                }

                @Override
                public void mouseDown(MouseEvent arg0) {
                    // do nothing
                }

                @Override
                public void mouseDoubleClick(MouseEvent arg0) {
                    openSelectedRequestInBrowser();
                }
            });
        }
    }

    private void createColumns() {

        Listener sortListener = new Listener() {

            public void handleEvent(Event e) {
                
                TableColumn thisColumn = (TableColumn) e.widget;
                if (thisColumn.getImage() == imageHelper.getImageAscending()) {
                    // switch to descending
                    thisColumn.setImage(imageHelper.getImageDescending());
                } else {
                    thisColumn.setImage(imageHelper.getImageAscending());
                }
                // we support single sort column only
                for (TableColumn column : table.getColumns()) {
                    if (column != thisColumn) {
                        column.setImage(null);
                    }
                }
                tableViewer.refresh();
            }
        };
        
        TableViewerColumn column = null;

        if (allowCheckboxesInTable) {

            column = new TableViewerColumn(tableViewer, SWT.NONE);
            column.getColumn().setText("");
            column.getColumn().setResizable(false);
            column.getColumn().setWidth(30);

            column.setLabelProvider(new ColumnLabelProvider() {
                @Override
                public String getText(Object element) {
                    return Utils.EMPTY_STRING;
                }
            });
        }
        
        column = createViewerColumn(IdmRequestData.COLUMN_NAME, sortListener);
        column.getColumn().setWidth(100);
        column = createViewerColumn(IdmRequestData.COLUMN_STATUS, sortListener);
        column.getColumn().setWidth(120);
        column = createViewerColumn(IdmRequestData.COLUMN_TITLE, sortListener);
        column.getColumn().setWidth(300);
        column = createViewerColumn(IdmRequestData.COLUMN_TYPE_NAME, sortListener);
        column.getColumn().setWidth(100);
        column = createViewerColumn(IdmRequestData.COLUMN_AUTHOR, sortListener);
        column.getColumn().setWidth(120);
        column = createViewerColumn(IdmRequestData.COLUMN_OWNER, sortListener);
        column.getColumn().setWidth(120);
        column = createViewerColumn(IdmRequestData.COLUMN_UPDATE_DATE, sortListener);
        column.getColumn().setWidth(170);
    }

    private TableViewerColumn createViewerColumn(String title, Listener sortListener) {
        TableViewerColumn column = new TableViewerColumn(tableViewer, SWT.NONE);
        column.getColumn().setText(title);
        column.getColumn().setResizable(true);
        column.setLabelProvider(new LabelProvider());
        column.getColumn().addListener(SWT.Selection, sortListener);
        return column;
    }
    
//    Image getColumnSortImage(boolean ascending) {
//        if (imageAscending == null || imageDescending == null) {
//            createImages();
//        }
//        if (ascending) {
//            return imageAscending;
//        } else {
//            return imageDescending;
//        }
//    }

//    private void createImages() {
////        Control control = tableViewer.getControl();
//        int itemHeight = table.getItemHeight();
//
//        Color foreground = table.getForeground();
//        Color background = table.getBackground();
//
//        /* \/ - descending */
//        PaletteData palette = new PaletteData(new RGB[] { foreground.getRGB(), background.getRGB() });
//        ImageData imageData = new ImageData(itemHeight, itemHeight, 4, palette);
//        imageData.transparentPixel = 1;
//        imageDescending = new Image(table.getDisplay(), imageData);
//        GC gc = new GC(imageDescending);
//        gc.setBackground(background);
//        gc.fillRectangle(0, 0, itemHeight, itemHeight);
////        gc.setForeground(foreground);
//
//        int midPoint = itemHeight / 2;
////        gc.drawPoint(midPoint, midPoint);
//        // triangle around midpoint
////        gc.drawPolygon(new int[] { midPoint - 2, midPoint - 1, midPoint + 2, midPoint - 1, midPoint, midPoint + 1 });
//        gc.setBackground(foreground);
//        gc.fillPolygon(new int[] { midPoint - 6, midPoint - 3, midPoint + 6, midPoint - 3, midPoint, midPoint + 3 });
//
//        gc.dispose();
//
//        /* /\ - ascending */
//        palette = new PaletteData(new RGB[] { foreground.getRGB(), background.getRGB() });
//        imageData = new ImageData(itemHeight, itemHeight, 4, palette);
//        imageData.transparentPixel = 1;
//        imageAscending = new Image(control.getDisplay(), imageData);
//        gc = new GC(imageAscending);
//        gc.setBackground(background);
//        gc.fillRectangle(0, 0, itemHeight, itemHeight);
////        gc.setForeground(foreground);
//        gc.setBackground(foreground);
////        gc.drawPoint(midPoint, midPoint);
//        // triangle around midpoint
////        gc.fillPolygon(new int[] { midPoint - 6, midPoint + 3, midPoint, midPoint - 3, midPoint + 6, midPoint + 3 });
////        gc.fillPolygon(new int[] { midPoint - 6, midPoint + 4, midPoint, midPoint - 2, midPoint + 6, midPoint + 4 });
//        
////        gc.fillPolygon(new int[] { midPoint - 6, midPoint + 2, midPoint, midPoint - 4, midPoint + 6, midPoint + 2 });
//        gc.fillPolygon(new int[] { midPoint - 7, midPoint + 3, midPoint, midPoint - 4, midPoint + 7, midPoint + 3 });
//        
////        gc.fillPolygon(new int[] { midPoint - 5, midPoint + 2, midPoint, midPoint - 3, midPoint + 5, midPoint + 2 });
//        
//
//        gc.dispose();
//    }


    private List<IdmRequestData> getInboxRequests(DimensionsConnectionDetailsEx connection) throws DMException {
        if (inboxRequests != null) {
            return inboxRequests;
        }
        List<IdmRequestData> newInboxRequests = new ArrayList<IdmRequestData>();
        final Session session = connection.openSession(null);
        final Product productObj = session.getObjectFactory().getCurrentUser().getDefaultProject().getProduct();
        final RequestProvider productRequestProvider = productObj.getRequestProvider();
        final IDMRequestFilter filter = new IDMRequestFilter();
        if (hasFilterByTitle()) {
            filter.add(IDMRequestAttributes.System.TITLE, Utils.wrapForSearch(filters.get(SearchFields.TITLE)));
        }
        if (hasFilterByTypeName()) {
            filter.add(IDMRequestAttributes.System.TYPE_NAME, Utils.wrapForSearch(filters.get(SearchFields.TYPE_NAME)));
        }

        final IDMRequestPage idmRequests = session.getObjectFactory().getIDMHelper().getIDMInbox(productRequestProvider,
                filter /* idmRequestFilter */, null /* idmRequestSort */, null /* paginationInfo */);

        for (IDMRequestV2 request : idmRequests.getRequests()) {
        	newInboxRequests.add(new IdmRequestData(request));
        }
        inboxRequests = newInboxRequests;
        return inboxRequests;
    }
    
    private boolean hasFilterByTypeName() {
        return filters.containsKey(SearchFields.TYPE_NAME);
    }

    private boolean hasFilterByTitle() {
        return filters.containsKey(SearchFields.TITLE) && !filters.get(SearchFields.TITLE).isEmpty();
    }
    
    private boolean hasFilter() {
        return hasFilterByTitle() || hasFilterByTypeName();
    }

    private List<IdmRequestData> getReportRequests() throws DMException {
        final Session session = connection.openSession(null);
        IDMHelper helper = session.getObjectFactory().getIDMHelper();
        
        List<IDMRequestIdentifier> identifiers = new ArrayList<IDMRequestIdentifier>();
        RequestProvider provider = session.getObjectFactory().getRequestProvider(null);
        Project defaultProject = session.getObjectFactory().getCurrentUser().getDefaultProject();
        List<IDMReportRequest> res = session.getObjectFactory().runIDMReportByName(reportId, defaultProject);

        for (IDMReportRequest idmRequest : res) {
            identifiers.add(new IDMRequestIdentifier(provider.getUID(), idmRequest.getID()));
        }
        List<IDMRequestV2> idmRequests = helper.getIDMRequests(identifiers);
        List<IdmRequestData> reportRequests = new ArrayList<IdmRequestData>();
        for (IDMRequestV2 request : idmRequests) {
            reportRequests.add(new IdmRequestData(request));
        }
        return reportRequests;
    }
    
    private void createContextMenu() {
        if (menuManager == null) {
            menuManager = new MenuManager();
            menuManager.addMenuListener(this);
            menuManager.setRemoveAllWhenShown(true);
        }
        Menu menu = menuManager.createContextMenu(tableViewer.getTable());
        tableViewer.getTable().setMenu(menu);
    }

    private IdmRequestData getSelectedRequest() {
        IdmRequestData request = null;
        IStructuredSelection selection = (IStructuredSelection) tableViewer.getSelection();
        if (selection.isEmpty()) {
            return null;
        } else {
            @SuppressWarnings("rawtypes")
            Iterator iter = selection.iterator();
            while (iter.hasNext()) {
                Object selected = iter.next();
                if (selected instanceof IdmRequestData) {
                    request = (IdmRequestData) selected;
                    break;
                }
            }
        }
        return request;
    }
    
//    private String getTimeSpan(Date requestDate) {
//        GregorianCalendar now = new GregorianCalendar();
//        
//        GregorianCalendar calThisWeek = (GregorianCalendar) now.clone();
//        // get day in week 1 = sun 7 = sat
//        int dayInWeek = calThisWeek.get(Calendar.DAY_OF_WEEK);
//        // first day locale dependent
//        int firstday = calThisWeek.getFirstDayOfWeek();
//        int dayDiff = dayInWeek - firstday;
//        if (dayDiff >= 0) {
//            calThisWeek.add(Calendar.DAY_OF_YEAR, -dayDiff);
//        } else {
//            calThisWeek.add(Calendar.DAY_OF_YEAR, -7); // sow is mon 2 we are sun so this week is last week !
//        }
//        
//        return "";
//    }
    
//    private static String GetTimeSpan(Date dateTime)
//    {
//        String stringy = "";
//        ReadablePeriod
//        TimeSpan diff = Date.Now.Subtract(dateTime);
//        double days = diff.Days;
//        double hours = diff.Hours + days * 24;
//        double minutes = diff.Minutes + hours * 60;
//
////        if (minutes < 0 || days >= 183)
////            return dateTime.GetGeneralDateShortTime();
//
//        if (minutes <= 1)
//            return "just now";
//
//        double weeks = Math.Floor(diff.TotalDays / 7);
//        if (weeks >= 1)
//        {
//            double partOfWeek = days - weeks * 7;
//            if (partOfWeek > 0)
//                stringy = String.format(", {0} day{1}", partOfWeek, partOfWeek > 1 ? "s" : null);
//
//            return String.format("{0} week{1}{2} ago", weeks, weeks >= 2 ? "s" : null, stringy);
//        }
//        if (days >= 1)
//        {
//            double partOfDay = hours - days * 24;
//            if (partOfDay > 0)
//                stringy = String.format(", {0} hour{1}", partOfDay, partOfDay > 1 ? "s" : null);
//
//            return String.format("{0} day{1}{2} ago", days, days >= 2 ? "s" : null, stringy);
//        }
//        if (hours >= 1)
//        {
//            double partOfHour = minutes - hours * 60;
//            if (partOfHour > 0)
//                stringy = String.format(", {0} minute{1}", partOfHour, partOfHour > 1 ? "s" : null);
//
//            return String.format("{0} hour{1}{2} ago", hours, hours >= 2 ? "s" : null, stringy);
//        }
//
//        // Only condition left is minutes > 1
//        return String.format("{0} minutes ago", minutes);
//    }


    private class LabelProvider extends OwnerDrawLabelProvider {

        TableViewerColumn column;

        private int lastKnownBackgroundColorHashCode;
        private Color darkSelectedRowBackgroundColor;
        private Color lightSelectedRowBackgroundColor;

        private Color whiteColor;
        private Color evenRowColor;
        private Color greenColor;
        private Color defaultIdTextColor;

        int cellOffset = 4;
        int rectangleOffset = 2;

        public LabelProvider() {
            java.awt.Color helper = java.awt.Color.decode("#0078d7");
            darkSelectedRowBackgroundColor = new Color(Display.getCurrent(), helper.getRed(), helper.getGreen(),
                    helper.getBlue());
            whiteColor = table.getShell().getDisplay().getSystemColor(SWT.COLOR_WHITE);
            
            helper = java.awt.Color.decode("#c9def5");
            lightSelectedRowBackgroundColor = new Color(Display.getCurrent(), helper.getRed(), helper.getGreen(),
                    helper.getBlue());

            helper = java.awt.Color.decode("#3d6ad9");
            defaultIdTextColor = new Color(Display.getCurrent(), helper.getRed(), helper.getGreen(), helper.getBlue());

            helper = java.awt.Color.decode("#3c763d");
            greenColor = new Color(Display.getCurrent(), helper.getRed(), helper.getGreen(), helper.getBlue());
        }

        @Override
        public String getToolTipText(Object element) {
            return getCellText(element);
        }

        @Override
        protected void initialize(ColumnViewer viewer, ViewerColumn column) {
            this.column = (TableViewerColumn) column;
            super.initialize(viewer, column);
        }

        @Override
        protected void measure(Event event, Object element) {
            event.setBounds(new Rectangle(event.x, event.y, event.width, 27));
        }

        @Override
        protected void paint(Event event, Object element) {
            Rectangle bounds = event.getBounds();
            String cellText = getCellText(element);


            TableItem[] selection = table.getSelection();
            if (selection != null && selection.length > 0 && selection[0] == (TableItem) event.item) {
                Color oldBackground = event.gc.getBackground();
                if (!isLight(event.gc.getForeground())) {
                    event.gc.setBackground(lightSelectedRowBackgroundColor);
                } else {
                    event.gc.setBackground(darkSelectedRowBackgroundColor);
                }
                event.gc.fillRectangle(event.x - 5, event.y, table.getClientArea().width + 5, event.height);
                event.gc.setBackground(oldBackground);
            } else {
                int index = table.indexOf((TableItem) event.item);
                Color oldBackground = event.gc.getBackground();
                if (evenRowColor == null || lastKnownBackgroundColorHashCode != event.gc.getBackground().hashCode()) {
                    int brightnessFactor = isDarkTheme(event.gc.getForeground(), event.gc.getBackground()) ? 30 : -30;
                    int r = adjustBrightness(event.gc.getBackground().getRed(), brightnessFactor);
                    int g = adjustBrightness(event.gc.getBackground().getGreen(), brightnessFactor);
                    int b = adjustBrightness(event.gc.getBackground().getBlue(), brightnessFactor);
                    evenRowColor = new Color(Display.getCurrent(), r, g, b);
                }
                // switch to 1-based indexing
                if ((index + 1) % 2 == 0) {
                    event.gc.setBackground(evenRowColor);
                } else {
                    event.gc.setBackground(event.gc.getBackground());
                }
                event.gc.fillRectangle(event.x - 5, event.y, table.getClientArea().width + 5, event.height);
                event.gc.setBackground(oldBackground);
            }
            if (isStatusCell()) {
                drawStatusCell(cellText, event);
                return;
            }
            if (isIdCell()) {
                prepareIdCellDrawing(cellText, event);
            }

            int cellWidth = column.getColumn().getWidth();
            int maxTextWidth = cellWidth - cellOffset * 2;
            String adjustedCellText = shortenText(event.gc, cellText, maxTextWidth, "...");

            event.gc.drawText(adjustedCellText, bounds.x + cellOffset, bounds.y + cellOffset + rectangleOffset,
                    true /* isTransparent */);

        }

        private int adjustBrightness(int colorComponent, int brightnessFactor) {
            colorComponent += brightnessFactor;
            if (colorComponent > 255) { 
                colorComponent = 255;
            }
            if (colorComponent < 0) {
                colorComponent = 0;
            }
            return colorComponent;
        }

        private boolean isDarkTheme(Color foreground, Color background) {

            // bright text color and dark background means the theme is presumably dark
            if (isLight(foreground) && !isLight(background)) {
                return true;
            }
            return false;
        }
        
        private boolean isLight(Color color) {
            // HSP (hue, saturation, perceived brightness) equation from http://alienryderflex.com/hsp.html
            int r = color.getRed();
            int g = color.getGreen();
            int b = color.getBlue();

            double hsp = Math.sqrt(0.299 * (r * r) + 0.587 * (g * g) + 0.114 * (b * b));
            if (hsp > 127.5) {
                // color is light
                return true;
            } else {
                // color is dark
                return false;
            }
        }

        private void prepareIdCellDrawing(String cellText, Event event) {

            TableItem[] selection = table.getSelection();
            if (selection == null || selection.length == 0 || selection[0] != (TableItem) event.item) {
                event.gc.setForeground(defaultIdTextColor);
            }
            Font font = event.gc.getFont();
            FontData data[] = font.getFontData();
            data[0].setStyle(SWT.BOLD);
            font = new Font(Display.getCurrent(), data[0]);
            event.gc.setFont(font);
        }

        private void drawStatusCell(String cellText, Event event) {
            Rectangle bounds = event.getBounds();            
            if (!isLight(event.gc.getForeground())) {
            event.gc.setForeground(whiteColor);
            }
            event.gc.setBackground(greenColor);
            int cellWidth = column.getColumn().getWidth();

            int maxTextWidth = cellWidth - cellOffset * 2 - rectangleOffset * 2;
            String adjustedCellText = shortenText(event.gc, cellText, maxTextWidth, "...");
            Point adjustedCaptionSize = event.gc.stringExtent(adjustedCellText);
            fillRoundedRectangle(event.gc, bounds.x, bounds.y + cellOffset,
                    adjustedCaptionSize.x + 2 * rectangleOffset + 4,
                    adjustedCaptionSize.y + cellOffset + rectangleOffset - 2, 2);

            event.gc.drawText(adjustedCellText, bounds.x + rectangleOffset + 2, bounds.y + cellOffset + rectangleOffset,
                    true /* isTransparent */);
        }

        private boolean isStatusCell() {
            return column.getColumn().getText().equals(IdmRequestData.COLUMN_STATUS);
        }

        private boolean isIdCell() {
            return column.getColumn().getText().equals(IdmRequestData.COLUMN_NAME);
        }

        private String shortenText(final GC gc, String text, final int width, final String ellipses) {

            if (gc.textExtent(text, 0).x <= width) {
                return text;
            }

            final int ellipseWidth = gc.textExtent(ellipses, 0).x;
            final int length = text.length();
            final TextLayout layout = new TextLayout(gc.getDevice());
            layout.setText(text);

            int end = layout.getPreviousOffset(length, SWT.MOVEMENT_CLUSTER);
            while (end > 0) {
                text = text.substring(0, end);
                final int l = gc.textExtent(text, 0).x;
                if (l + ellipseWidth <= width) {
                    break;
                }
                end = layout.getPreviousOffset(end, SWT.MOVEMENT_CLUSTER);
            }
            layout.dispose();
            return end == 0 ? text.substring(0, 1) : text + ellipses;
        }

        private void fillRoundedRectangle(GC gc, int x, int y, int width, int height, int radius) {
            int diameter = radius * 2;

            // two rectangles and four circles
            gc.fillRectangle(x, y + radius, width, height - diameter);
            gc.fillRectangle(x + radius, y, width - diameter, height);

            // x,y point to topmost left corner of the circle
            // top left
            gc.fillOval(x, y, diameter, diameter);
            // top right
            gc.fillOval(x + width - diameter, y, diameter, diameter);
            // bottom left
            gc.fillOval(x, y + height - diameter, diameter, diameter);
            // bottom right
            gc.fillOval(x + width - diameter, y + height - diameter, diameter, diameter);
        }

        private String getCellText(Object element) {
            if (!(element instanceof IdmRequestData)) {
                return "unknown element";
            }
            IdmRequestData request = (IdmRequestData) element;
            return request.getText(column.getColumn().getText());
        }

    }

    private class RefreshAction extends Action {

        public RefreshAction() {
            setText(Messages.objList_refresh_text);
            setImageDescriptor(DMUIPlugin.getDefault().getImageDescriptor(IDMImages.OCTANE_REFRESH));
            setToolTipText(Messages.objList_refresh_tooltip);
        }

        @Override
        public void run() {
            forceRefresh();
        }
    }
    
    private class OpenRequestAction extends Action {
        public OpenRequestAction() {
            setText(Messages.octane_openRequest);
            setToolTipText(Messages.octane_openRequest);
            if (!connection.isSbmRequestProvider()) {
                setImageDescriptor(DMUIPlugin.getDefault().getImageDescriptor(IDMImages.OCTANE_OPEN));
            }
        }

        @Override
        public void run() {
        	openSelectedRequestInBrowser();
        }
    }

    private class SchedulingRule implements ISchedulingRule {
        @Override
        public boolean contains(ISchedulingRule rule) {
            return isConflicting(rule);
        }

        @Override
        public boolean isConflicting(ISchedulingRule rule) {
            return this == rule;
        }
    }
}
