/* uDig - User Friendly Desktop Internet GIS client
 * http://udig.refractions.net
 * (C) 2004-2011, Refractions Research Inc.
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
package net.refractions.udig.tool.select.internal;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;

import net.refractions.udig.boundary.BoundaryListener;
import net.refractions.udig.boundary.IBoundaryService;
import net.refractions.udig.boundary.IBoundaryStrategy;
import net.refractions.udig.project.ILayer;
import net.refractions.udig.project.ui.commands.SelectionBoxCommand;
import net.refractions.udig.project.ui.render.displayAdapter.MapMouseEvent;
import net.refractions.udig.project.ui.render.displayAdapter.ViewportPane;
import net.refractions.udig.project.ui.tool.AbstractModalTool;
import net.refractions.udig.project.ui.tool.ModalTool;
import net.refractions.udig.project.ui.tool.options.ToolOptionContributionItem;
import net.refractions.udig.tool.select.SelectPlugin;
import net.refractions.udig.tool.select.commands.SetBoundaryLayerCommand;
import net.refractions.udig.ui.PlatformGIS;

import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.ComboViewer;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Envelope;

/**
 * Provides Boundary Navigation functionality for MapViewport, allows the selection and navigation
 * of background layers that are marked as boundary layers.
 * <p>
 * Each time you select a feature from a boundary layer the tool will move to the next available
 * boundary layer in the stack allowing you to make a selection.
 * </p>
 * 
 * @author leviputna
 * @since 1.2.3
 */
public class BoundaryLayerSelectionTool extends AbstractModalTool implements ModalTool {

    private SelectionBoxCommand shapeCommand;
    private boolean selecting;
    private Point start;

    private String CURSORPOINTID = "bondatySelectCursor";
    private String CURSORBOXID = "bondatyBoxSelectCursor";

    boolean showContextOnRightClick = false;

    /**
     * 
     */
    public BoundaryLayerSelectionTool() {
        super(MOUSE | MOTION);
    }

    /**
     * @see net.refractions.udig.project.ui.tool.AbstractTool#mousePressed(net.refractions.udig.project.render.displayAdapter.MapMouseEvent)
     */
    public void mousePressed( MapMouseEvent e ) {
        shapeCommand = new SelectionBoxCommand();

        if (e.button == MapMouseEvent.BUTTON3 && showContextOnRightClick) {
            ((ViewportPane) e.source).getMapEditor().openContextMenu();
            return;
        }

        if ((e.button == MapMouseEvent.BUTTON1) || (e.button == MapMouseEvent.BUTTON3)) {
            updateCursor(e);
            start = e.getPoint();

            if (e.isShiftDown()) {
                selecting = true;

                shapeCommand.setValid(true);
                shapeCommand.setShape(new Rectangle(start.x, start.y, 0, 0));
                context.sendASyncCommand(shapeCommand);

            } else {

                selecting = false;
                clickFeedback(e);

                Envelope bounds = getBounds(e);
                sendSelectionCommand(e, bounds);
            }
        }

    }

    /**
     * @see net.refractions.udig.project.ui.tool.AbstractTool#mouseReleased(net.refractions.udig.project.render.displayAdapter.MapMouseEvent)
     */
    public void mouseReleased( MapMouseEvent e ) {
        if (selecting) {
            Envelope bounds = getBounds(e);
            sendSelectionCommand(e, bounds);
        }
    }

    private Envelope getBounds( MapMouseEvent e ) {
        Point point = e.getPoint();
        if (start == null || start.equals(point)) {

            return getContext().getBoundingBox(point, 3);
        } else {
            Coordinate c1 = context.getMap().getViewportModel().pixelToWorld(start.x, start.y);
            Coordinate c2 = context.getMap().getViewportModel().pixelToWorld(point.x, point.y);

            return new Envelope(c1, c2);
        }
    }

    /**
     * @see net.refractions.udig.project.ui.tool.SimpleTool#onMouseDragged(net.refractions.udig.project.render.displayAdapter.MapMouseEvent)
     */
    public void mouseDragged( MapMouseEvent e ) {
        if (selecting) {
            Point end = e.getPoint();

            if (start == null)
                return;
            shapeCommand.setShape(new Rectangle(Math.min(start.x, end.x), Math.min(start.y, end.y),
                    Math.abs(start.x - end.x), Math.abs(start.y - end.y)));
            context.getViewportPane().repaint();

        }
    }

    /**
     * @param e
     * @param bounds
     */
    protected void sendSelectionCommand( MapMouseEvent e, Envelope bounds ) {

        SetBoundaryLayerCommand command = new SetBoundaryLayerCommand(e, bounds);

        getContext().sendASyncCommand(command);

        selecting = false;
        shapeCommand.setValid(false);
        getContext().getViewportPane().repaint();
    }

    /**
     * Provides user feedback when box select is disabled.
     * 
     * @param e
     */
    public void clickFeedback( MapMouseEvent e ) {
        Rectangle square = new Rectangle(e.x - 2, e.y - 2, 4, 4);

        shapeCommand.setValid(true);
        shapeCommand.setShape(square);
        context.sendASyncCommand(shapeCommand);
        context.getViewportPane().repaint();
    }

    private void updateCursor( MapMouseEvent e ) {

        if (e.isAltDown()) {
            setCursorID(CURSORBOXID);
        } else {
            setCursorID(CURSORPOINTID);
        }

    }

    /**
     * @see net.refractions.udig.project.ui.tool.Tool#dispose()
     */
    public void dispose() {
        super.dispose();
    }

    public static class OptionContribtionItem extends ToolOptionContributionItem {

        private ComboViewer comboViewer;

        private static String BOUNDARY_LAYER_ID = "net.refractions.udig.tool.default.BoundaryLayerService";

        /**
         * Listens to the user and changes the global IBoundaryService to the indicated strategy.
         */
        private ISelectionChangedListener comboListener = new ISelectionChangedListener(){
            @Override
            public void selectionChanged( SelectionChangedEvent event ) {
                IStructuredSelection selectedStrategy = (IStructuredSelection) event.getSelection();
                ILayer layer = (ILayer) selectedStrategy.getFirstElement();
                setActiveLayer(layer);
            }
        };

        /**
         * Watches for a change in boundary layer and sets the combo to the new layer
         */
        protected BoundaryListener watcher = new BoundaryListener(){
            public void handleEvent( BoundaryListener.Event event ) {
                PlatformGIS.asyncInDisplayThread(new Runnable(){

                    @Override
                    public void run() {
                        ILayer activeLayer = getBoundaryLayerStrategy().getActiveLayer();
                        List<ILayer> layers = getBoundaryLayerStrategy().getBoundaryLayers();
                        if( comboViewer == null || comboViewer.getControl() == null || comboViewer.getControl().isDisposed()){
                            return;
                        }
                        comboViewer.setInput(layers);
                        // check if the current layer still exists
                        if (layers.contains(activeLayer)) {
                            setSelected(activeLayer);
                        } else {
                            setSelected(null);
                        }
                    }
                }, true);
            }
        };

        @Override
        protected IPreferenceStore fillFields( Composite parent ) {

            Button nav = new Button(parent, SWT.CHECK);
            nav.setText("Navigate");
            addField(SelectionToolPreferencePage.NAVIGATE_SELECTION, nav);

//            Button zoom = new Button(parent, SWT.CHECK);
//            zoom.setText("Zoom to selection");
//            addField(SelectionToolPreferencePage.ZOOM_TO_SELECTION, zoom);
                        
            setCombo(parent);
            listenBoundaryLayer(true);

            // set the list of layers and the active layer
            List<ILayer> layers = getBoundaryLayerStrategy().getBoundaryLayers();
            ILayer activeLayer = getBoundaryLayerStrategy().getActiveLayer();
            comboViewer.setInput(layers);
            if (!layers.isEmpty()) {
                comboViewer.setInput(layers);
                if (activeLayer == null) {
                    activeLayer = layers.get(0);
                }
            }
            setSelected(activeLayer);
            listenCombo(true);

            return SelectPlugin.getDefault().getPreferenceStore();
        }

        private void setCombo( Composite parent ) {
            comboViewer = new ComboViewer(parent, SWT.READ_ONLY);
            comboViewer.setContentProvider(new ArrayContentProvider());

            comboViewer.setLabelProvider(new LabelProvider(){
                @Override
                public String getText( Object element ) {
                    if (element instanceof ILayer) {
                        ILayer layer = (ILayer) element;
                        return layer.getName();
                    }
                    return super.getText(element);
                }
            });

            comboViewer.setInput(getBoundaryLayers());
        }

        protected void listenCombo( boolean listen ) {
            if (comboViewer == null || comboViewer.getControl().isDisposed()) {
                return;
            }
            if (listen) {
                comboViewer.addSelectionChangedListener(comboListener);
            } else {
                comboViewer.removeSelectionChangedListener(comboListener);
            }
        }

        protected void listenBoundaryLayer( boolean listen ) {
            BoundaryLayerStrategy boundaryLayerStrategy = getBoundaryLayerStrategy();
            if (boundaryLayerStrategy == null) {
                return;
            }
            if (listen) {
                boundaryLayerStrategy.addListener(watcher);
            } else {
                boundaryLayerStrategy.removeListener(watcher);
            }
        }

        /*
         * This will update the combo viewer (carefully unhooking events while the viewer is
         * updated).
         * @param selected
         */
        private void setSelected( ILayer selected ) {

            boolean disposed = comboViewer.getControl().isDisposed();
            if (comboViewer == null || disposed) {
                listenBoundaryLayer(false);
                return; // the view has shutdown!
            }

            ILayer current = getSelected();
            // check combo
            if (current != selected) {
                try {
                    listenCombo(false);
                    comboViewer.setSelection(new StructuredSelection(selected), true);
                } finally {
                    listenCombo(true);
                }
            }

        }

        /*
         * Get the Boundary Layer currently selected in this tool
         * @return ILayer currently selected in this tool
         */
        private ILayer getSelected() {
            if (comboViewer.getSelection() instanceof IStructuredSelection) {
                IStructuredSelection selection = (IStructuredSelection) comboViewer.getSelection();
                return (ILayer) selection.getFirstElement();
            }
            return null;
        }

        /*
         * Sets the active layer in the boundary layer strategy
         */
        private void setActiveLayer( ILayer activeLayer ) {
            getBoundaryLayerStrategy().setActiveLayer(activeLayer);
        }

        /*
         * returns a BoundaryLayerStrategy object for quick access
         */
        private BoundaryLayerStrategy getBoundaryLayerStrategy() {
            IBoundaryService boundaryService = PlatformGIS.getBoundaryService();
            IBoundaryStrategy boundaryStrategy = boundaryService.findProxy(BOUNDARY_LAYER_ID)
                    .getStrategy();

            if (boundaryStrategy instanceof BoundaryLayerStrategy) {
                return (BoundaryLayerStrategy) boundaryStrategy;
            }
            return null;
        }

        /*
         * gets a list of boundary layers via the boundary strategy
         */
        private List<ILayer> getBoundaryLayers() {
            return getBoundaryLayerStrategy().getBoundaryLayers();
        }

    };

}