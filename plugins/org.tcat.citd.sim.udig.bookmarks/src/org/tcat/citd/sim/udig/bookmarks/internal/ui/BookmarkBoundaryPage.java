/* uDig - User Friendly Desktop Internet GIS client
 * http://udig.refractions.net
 * (C) 2011, Refractions Research Inc.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation;
 * version 2.1 of the License.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 */

package org.tcat.citd.sim.udig.bookmarks.internal.ui;

import java.util.Collection;
import java.util.List;

import net.refractions.udig.boundary.BoundaryProxy;
import net.refractions.udig.boundary.IBoundaryService;
import net.refractions.udig.boundary.IBoundaryStrategy;
import net.refractions.udig.ui.PlatformGIS;

import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.ui.part.IPageSite;
import org.eclipse.ui.part.Page;
import org.tcat.citd.sim.udig.bookmarks.BookmarkBoundaryStrategy;
import org.tcat.citd.sim.udig.bookmarks.BookmarkListener;
import org.tcat.citd.sim.udig.bookmarks.BookmarksPlugin;
import org.tcat.citd.sim.udig.bookmarks.IBookmark;
import org.tcat.citd.sim.udig.bookmarks.IBookmarkService;

/**
 * A page to add to the Boundary View used for additional configuration of the boundary.
 * <p>
 * Idea:add a combo to select a bookmark and use the current bookmark as the boundary. 
 * 
 * @author jody
 * @version 1.3.0
 */
public class BookmarkBoundaryPage extends Page {

    private Composite page;
    private BoundaryProxy strategy;
    private ComboViewer comboViewer;

    private ISelectionChangedListener comboListener = new ISelectionChangedListener(){
        @Override
        public void selectionChanged( SelectionChangedEvent event ) {
            IStructuredSelection selectedBookmark = (IStructuredSelection) event.getSelection();
            IBookmark selected = (IBookmark) selectedBookmark.getFirstElement();
            
            IBoundaryService service = PlatformGIS.getBoundaryService();
            IBoundaryStrategy bookmarkStrategy = service.findProxy(BookmarkBoundaryStrategy.ID).getStrategy();
            
            if (bookmarkStrategy instanceof BookmarkBoundaryStrategy) {
                ((BookmarkBoundaryStrategy) bookmarkStrategy).setCurrentBookmark(selected);
            }
        }
    };

    /*
     * Listens to the workbench IBookmarkService and updates our view if anything changes!
     */
    private BookmarkListener serviceWatcher = new BookmarkListener(){
        
        public void handleEvent( BookmarkListener.Event event ) {
            // must be run in the UI thread to be able to call setSelected
            PlatformGIS.asyncInDisplayThread(new Runnable(){
                
                @Override
                public void run() {
                    IBookmark currentBookmark = getSelected();
                    Collection<IBookmark> bookmarks = BookmarksPlugin.getBookmarkService().getBookmarks();
                    comboViewer.setInput(bookmarks);
                    // check if the current bookmark still exists
                    if (bookmarks.contains(currentBookmark)) {
                        setSelected(currentBookmark);
                    }
                    else {
                        // this may need to reset the strategy but at this stage 
                        // the bookmarkBoundaryStrategy holds on to the current bookmark
                        // even when it is deleted
                        setSelected(null);
                    }
                    
                }
            }, true);
        }
        
    };

    public BookmarkBoundaryPage() {
        // careful don't do any work here
    }
    
    // We would overrride init if we needed to (remmeber to call super)
    @Override
    public void init(IPageSite pageSite){
        super.init(pageSite); // provides access to stuff
        IBoundaryService service = PlatformGIS.getBoundaryService();
        strategy = service.findProxy(BookmarkBoundaryStrategy.ID);        
    }
    
    protected BookmarkBoundaryStrategy getStrategy(){
        if( strategy == null ){
            return null;
        }
        return (BookmarkBoundaryStrategy) strategy.getStrategy();
    }
    

    @Override
    public void createControl( Composite parent ) {
        page = new Composite(parent, SWT.NONE);
        
        GridLayout layout = new GridLayout();
        layout.numColumns = 2;
        page.setLayout(layout);

        Label label = new Label(page, SWT.LEFT);
        //label.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
        label.setText("Bookmarks ");
        label.pack();
        
        IBookmarkService bookmarkService = BookmarksPlugin.getBookmarkService();
        listenService(true);
        
        comboViewer = new ComboViewer(page, SWT.READ_ONLY);
        comboViewer.setContentProvider(new ArrayContentProvider());
        comboViewer.setLabelProvider(new LabelProvider(){
            @Override
            public String getText( Object element ) {
                if (element instanceof IBookmark) {
                    IBookmark bookmark = (IBookmark) element;
                    return bookmark.getName();
                }
                return super.getText(element);
            }
        });
        List<IBookmark> bookmarks = (List<IBookmark>)bookmarkService.getBookmarks();
        comboViewer.setInput(bookmarks);
        
        comboViewer.addSelectionChangedListener(comboListener);
        
        if (strategy != null ){
            // add any listeners to strategy
            
        }
    }
    
    /*
     * This will update the combo viewer (carefully unhooking events while the viewer is updated).
     * 
     * @param selected
     */
    private void setSelected( IBookmark selected ) {
//        if (selected == null) {
//            return;
//        }
        
        boolean disposed = comboViewer.getControl().isDisposed();
        if (comboViewer == null || disposed) {
            listenService(false);
            return; // the view has shutdown!
        }
        
        IBookmark current = getSelected();
        // check combo
        if (current != selected) {
            try {
                //listenCombo(false);
                comboViewer.setSelection(new StructuredSelection(selected), true);
            } finally {
                //listenCombo(true);
            }
        }

    }
    
    /*
     * Get the Bookmark selected by the user
     * 
     * @return Ibookmark selected by the user
     */
    private IBookmark getSelected() {
        if (comboViewer.getSelection() instanceof IStructuredSelection) {
            IStructuredSelection selection = (IStructuredSelection) comboViewer.getSelection();
            return (IBookmark) selection.getFirstElement();
        }
        return null;
    }

    protected void listenService( boolean listen ){
        IBookmarkService bookmarkService = BookmarksPlugin.getBookmarkService();
        if (listen) {
            bookmarkService.addListener(serviceWatcher);
        } else {
            bookmarkService.removeListener(serviceWatcher);
        }
    }

    @Override
    public Composite getControl() {
        return page;
    }

    @Override
    public void setFocus() {
        if (page != null && !page.isDisposed()) {
            page.setFocus();
        }
    }
    
    @Override
    public void dispose() {
        if( page != null ){
            // remove any listeners            
        }
        if( strategy != null ){
            // remove any listeners
            strategy = null;
        }
        super.dispose();
    }

}
