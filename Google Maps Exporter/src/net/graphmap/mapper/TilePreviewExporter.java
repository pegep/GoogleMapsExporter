/*
 Copyright 2014
 Authors : Pekka Maksimainen
 Website : http://graphmap.net
 */
package net.graphmap.mapper;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import javax.imageio.ImageIO;
import org.gephi.io.exporter.spi.ByteExporter;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.ProcessingTarget;
import org.gephi.preview.api.RenderTarget;
import org.gephi.project.api.Workspace;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.ProgressTicket;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import processing.core.PGraphicsJava2D;

/**
 *
 * @author pegerp
 */
public class TilePreviewExporter implements ByteExporter, LongTask {

//    private ProgressTicket progress;
    private boolean cancel = false;
    private Workspace workspace;
    private OutputStream stream;
    private int width = 256;
    private int height = 256;
    private boolean transparentBackground = true;
    private ProcessingTarget target;
    private int x = 0;
    private int y = 0;
    private int z = 0;
    private int levels = 3;
    private int verbose = 1;
    private Dimension customDimensions;
    private Point customTopLeftPosition;
    private Dimension d;
    private String directory;

    @Override
    public boolean execute() {
        //Progress.start(progress);
        PreviewController controller = Lookup.getDefault().lookup(PreviewController.class);
        controller.getModel(workspace).getProperties().putValue(PreviewProperty.VISIBILITY_RATIO, 1.0);
        
        PreviewProperties props = controller.getModel(workspace).getProperties();
        Color oldColor = props.getColorValue(PreviewProperty.BACKGROUND_COLOR);
        if (transparentBackground) {
            props.putValue(PreviewProperty.BACKGROUND_COLOR, new Color(255, 255, 255, 0));//White transparent
        }
        props.putValue(PreviewProperty.MARGIN, 0f);
        props.putValue(PreviewProperty.NODE_LABEL_FONT, props.getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(Font.PLAIN, Math.max(1, 4 / (z + 1))));
        props.putValue("width", width);
        props.putValue("height", height);

        PreviewModel model = controller.getModel(workspace);
        Method mSetDimensions = null;
        Method mSetTopLeftPosition = null;
        try {
            mSetDimensions = model.getClass().getMethod("setDimensions", Dimension.class);
            mSetTopLeftPosition = model.getClass().getMethod("setTopLeftPosition", Point.class);
        } catch (SecurityException e) {
            System.out.println("Security exception");
        } catch (NoSuchMethodException e) {
            System.out.println("No such method exception: " + e.getMessage());
        }
        
        do {
            controller.refreshPreview();            

            Point p = model.getTopLeftPosition();
            d = model.getDimensions();
            int divisor = (int) Math.pow(2, z);
            int tileWidth = d.width / divisor;
            int tileHeight = d.height / divisor;
            int dim = Math.max(tileWidth, tileHeight);

            try {
                mSetDimensions.invoke(model, new Dimension(dim, dim));
                mSetTopLeftPosition.invoke(model, new Point(p.x + (x * dim), p.y + (y * dim)));
            } catch (IllegalAccessException ex) {
                Exceptions.printStackTrace(ex);
            } catch (IllegalArgumentException ex) {
                Exceptions.printStackTrace(ex);
            } catch (InvocationTargetException ex) {
                Exceptions.printStackTrace(ex);
            }
            
            System.out.println("Rendering:         " + this.getFilename("tile"));
            if (this.verbose > 0) {
                System.out.println("Top left position: " + model.getTopLeftPosition().x + " " + model.getTopLeftPosition().y);
                System.out.println("Dimensions:        " + model.getDimensions().width + " " + model.getDimensions().height);
                System.out.println("Square:           [" + model.getTopLeftPosition().x + ", " + (model.getTopLeftPosition().x + model.getDimensions().width) + "], [" + model.getTopLeftPosition().y + ", " + (model.getTopLeftPosition().y + model.getDimensions().height) + "]");
                System.out.println("Tilewidth          " + tileWidth + " " + tileHeight);
                System.out.println();
            }

            target = (ProcessingTarget) controller.getRenderTarget(RenderTarget.PROCESSING_TARGET, workspace);
            if (target instanceof LongTask) {
                //((LongTask) target).setProgressTicket(progress);
            }

            try {
                target.getGraphics().noSmooth();
                target.refresh();

                PGraphicsJava2D pg2 = (PGraphicsJava2D) target.getGraphics();
                BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                img.setRGB(0, 0, width, height, pg2.pixels, 0, width);
                stream = new BufferedOutputStream(new FileOutputStream(new File(directory + File.separator + this.getFilename("tile") + ".png")));
                ImageIO.write(img, "png", stream);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            try {
                stream.flush();
                stream.close();
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
            
            x++;
            if (x == Math.pow(2, z) && y != Math.pow(2, z) - 1) { // x is at eol
                x = 0;
                y++; // next row
            }
            if ((x == Math.pow(2, z) && y == Math.pow(2, z) - 1)) { // x and y at eol
                z++;
                x = 0;
                y = 0;
            }
            if (z == 0) {
                z++;
                x = 0;
                y = 0;
            }
        } while (!this.isLast());
        //Fix bug caused by keeping width and height in the workspace preview properties.
        //When a .gephi file is loaded later with these properties PGraphics will be created instead of a PApplet
        props.removeSimpleValue("width");
        props.removeSimpleValue("height");
        props.removeSimpleValue(PreviewProperty.MARGIN);
        props.putValue(PreviewProperty.BACKGROUND_COLOR, oldColor);
        //Progress.finish(progress);

        return !cancel;
    }

    public int getHeight() {
        return height;
    }

    public void setHeight(int height) {
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public void setWidth(int width) {
        this.width = width;
    }

    public void setLevels(int levels) {
        this.levels = levels;
    }
    
    public int getLevels() {
        return levels;
    }
    
    public String getDirectory() {
        return directory;
    }

    public void setDirectory(String directory) {
        this.directory = directory;
    }

    public void setVerbosity(int verbose) {
        this.verbose = verbose;
    }

    public void setCustomDimensions(Dimension dimension) {
        this.customDimensions = dimension;
    }

    public void setCustomTopLeftPosition(Point p) {
        this.customTopLeftPosition = p;
    }

    public boolean isTransparentBackground() {
        return transparentBackground;
    }

    public void setTransparentBackground(boolean transparentBackground) {
        this.transparentBackground = transparentBackground;
    }

    @Override
    public boolean cancel() {
        cancel = true;
        if (target instanceof LongTask) {
            ((LongTask) target).cancel();
        }
        return true;
    }
    
    @Override
    public void setProgressTicket(ProgressTicket progressTicket) {
//        this.progress = progressTicket;
    }

    public boolean isLast() {
        return z == levels + 1;
    }

    public String getFilename(String filename) {
        return filename + "-" + x + "-" + y + "-" + z;
    }

    @Override
    public void setWorkspace(Workspace workspace) {
        this.workspace = workspace;
    }

    @Override
    public Workspace getWorkspace() {
        return workspace;
    }

    @Override
    public void setOutputStream(OutputStream stream) {
        this.stream = stream;
    }
}
