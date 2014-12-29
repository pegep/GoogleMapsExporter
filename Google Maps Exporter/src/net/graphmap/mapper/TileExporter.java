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
import org.apache.commons.io.FileUtils;
import org.gephi.io.exporter.spi.ByteExporter;
import org.gephi.preview.api.PreviewController;
import org.gephi.preview.api.PreviewModel;
import org.gephi.preview.api.PreviewProperties;
import org.gephi.preview.api.PreviewProperty;
import org.gephi.preview.api.ProcessingTarget;
import org.gephi.preview.api.RenderTarget;
import org.gephi.project.api.Workspace;
import org.gephi.utils.longtask.spi.LongTask;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;
import org.openide.util.Exceptions;
import org.openide.util.Lookup;
import processing.core.PGraphicsJava2D;
import processing.core.PImage;

/**
 *
 * @author pegerp
 */
public class TileExporter implements ByteExporter, LongTask {

    private boolean cancel = false;
    private Workspace workspace;
    private ProgressTicket progress;
    private OutputStream stream;
    private int width = 256;
    private int height = 256;
    private int step = 8; // render w*step x h*step sized image and then crop appropriate pieces instead of rendering each piece as a single w*h sized image
    private ProcessingTarget target;
    private int x = 0;
    private int y = 0;
    private int z = 0;
    private int levels = 3;
    private int verbose = 0;
    private Dimension customDimensions;
    private Point customTopLeftPosition;
    private Dimension dimensions;
    private String directory;
    private boolean exportJson = true;
    private long start;
    private long layerStart;

    @Override
    public boolean execute() {
        PreviewController controller = Lookup.getDefault().lookup(PreviewController.class);
        controller.getModel(workspace).getProperties().putValue(PreviewProperty.VISIBILITY_RATIO, 1.0);
        
        PreviewProperties props = controller.getModel(workspace).getProperties();
        Color oldColor = props.getColorValue(PreviewProperty.BACKGROUND_COLOR);
        
        props.putValue(PreviewProperty.BACKGROUND_COLOR, new Color(255, 255, 255, 0)); //White transparent
        
        props.putValue(PreviewProperty.MARGIN, 0f);
        
        props.putValue("width", width * step);
        props.putValue("height", height * step);
        
        PreviewModel model = controller.getModel(workspace);
        Method mSetDimensions = null;
        Method mSetTopLeftPosition = null;
        
        controller.refreshPreview();
        
        try {
            mSetDimensions = model.getClass().getMethod("setDimensions", Dimension.class);
            mSetTopLeftPosition = model.getClass().getMethod("setTopLeftPosition", Point.class);
        } catch (SecurityException e) {
            System.out.println("Security exception");
        } catch (NoSuchMethodException e) {
            System.out.println("No such method exception: " + e.getMessage());
        }
        
        int lastTicket = 0;
        for (int i = 0; i <= this.getLevels(); i++) {
            lastTicket += ((Double) Math.pow(2, 2 * i)).intValue();
        }
        
        Progress.start(progress, lastTicket);
        start = System.currentTimeMillis();
        layerStart = System.currentTimeMillis();
        Point topLeft = model.getTopLeftPosition();
        dimensions = model.getDimensions();
        
        do {
            if (cancel) {
                break;
            }
            
            props.putValue(PreviewProperty.NODE_LABEL_FONT, props.getFontValue(PreviewProperty.NODE_LABEL_FONT).deriveFont(Font.PLAIN, Math.max(1, 4 / (z + 1))));
            
            //controller.refreshPreview();

            int divisor = (int) Math.pow(2, z);
            float tileWidth = (float)  dimensions.width / divisor;
            float tileHeight = (float) dimensions.height / divisor;
            float dim = Math.max(tileWidth, tileHeight);
            // We may get imprecision here that affects the alignment of nodes. PreviewModel requires dimensions
            // in Dimension object that does not allow floating point precision. As the rendering window gets 
            // smaller in every zoom level we get rounding errors. This becomes an issue when graph's coordinates
            // are not on broad scale. Eg. rendering window that would require 2.75*2.75 wide window becomes
            // 2*2 size and thus becomes slightly misaligned. Using larger step variable remedies this a little.
            // However more memory is consumed. Other rendering logic (less memory intensive) or graph 
            // pre-scaling might be better approach.
            int dimStep = (int) (dim * step);

            try {
                mSetDimensions.invoke(model, new Dimension(dimStep, dimStep));
                mSetTopLeftPosition.invoke(model, new Point((int) (topLeft.x + (x * dim)), (int) (topLeft.y + (y * dim))));
                if (verbose > 0) {
                    System.out.println("Rendering:         " + this.getFilename("tile"));
                    System.out.println("Setting dimensions: " + model.getDimensions().width + ", " + model.getDimensions().width);
                    System.out.println("Setting tLP:       " + model.getTopLeftPosition().x + ", " + model.getTopLeftPosition().y);
                    System.out.println("Right edge:        " + (model.getTopLeftPosition().x + model.getDimensions().width));
                    System.out.println("DimStep:           " + dimStep);
                    System.out.println("Square:           [" + model.getTopLeftPosition().x + ", " + (model.getTopLeftPosition().x + model.getDimensions().width * step) + "], [" + model.getTopLeftPosition().y + ", " + (model.getTopLeftPosition().y + model.getDimensions().height * step) + "]");
                }
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
            }

            target = (ProcessingTarget) controller.getRenderTarget(RenderTarget.PROCESSING_TARGET, workspace);

            //OutputStream stream2 = null;
            try {
                Progress.setDisplayName(progress, "Rendering tile " + this.getFilename("tile", x, y));
                target.getGraphics().noSmooth();
                target.refresh();

                PGraphicsJava2D pg2 = (PGraphicsJava2D) target.getGraphics();
                /*
                // Save full size image from which the 256x256 pieces are cut
                BufferedImage imgOrig = new BufferedImage(width * step, height * step, BufferedImage.TYPE_INT_ARGB);
                imgOrig.setRGB(0, 0, width * step, height * step, pg2.pixels, 0, width * step);
                stream2 = new BufferedOutputStream(new FileOutputStream(new File(directory + File.separator + this.getFilename("tile", x, y) + "-orig.png")));
                ImageIO.write(imgOrig, "png", stream2);
                */
                for (int i = 0; i < step; i++) {
                    for (int j = 0; j < step; j++) {
                        // Comparing against equality (>=) may "crop" the tiles on borders. This is due to rounding errors
                        // explained near dimStep variable. We'll crop now to reduce amount of tiles produced. Using
                        // inequality (>) would render one extraneous tile over the borders.
                        if (x + i >= Math.pow(2, z)) {
                            continue;
                        }
                        if (y + j >= Math.pow(2, z)) {
                            continue;
                        }
                        if (verbose > 0) {
                            System.out.println("Cropping:          " + this.getFilename("tile", x + i, y + j));
                        }
                        Progress.setDisplayName(progress, "Rendering tile " + this.getFilename("tile", x + i, y + j));
                        PImage piece = pg2.get(width * i, height * j, width, height);
                        BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
                        img.setRGB(0, 0, width, height, piece.pixels, 0, width);
                        stream = new BufferedOutputStream(new FileOutputStream(new File(directory + File.separator + this.getFilename("tile", x + i, y + j) + ".png")));
                        ImageIO.write(img, "png", stream);
                        Progress.progress(progress);
                    }
                }
                if (verbose > 0) {
                    System.out.println();
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    stream.flush();
                    stream.close();
                    //stream2.flush();
                    //stream2.close();
                } catch (IOException ex) {
                    Exceptions.printStackTrace(ex);
                }
            }

            nextTile();
            
        } while (!this.isLast());
        
        System.out.println("Finished rendering in " + (int) (System.currentTimeMillis() - start) / 1000 + " seconds");

        //Fix bug caused by keeping width and height in the workspace preview properties.
        //When a .gephi file is loaded later with these properties PGraphics will be created instead of a PApplet
        props.removeSimpleValue("width");
        props.removeSimpleValue("height");
        props.removeSimpleValue(PreviewProperty.MARGIN);
        props.putValue(PreviewProperty.BACKGROUND_COLOR, oldColor);
        
        controller.refreshPreview();

        // Following steps set the observation window to be square. That is, if the x-axis is
        // wider than y-axis then y-axis will be made taller on maxy position - and vice versa.
        // This fixes alignment issues that sometimes make sense and sometimes not. This way it works.
        int minx = (int) model.getTopLeftPosition().getX();
        int miny = (int) model.getTopLeftPosition().getY();
        int maxx = (int) (model.getTopLeftPosition().getX() + model.getDimensions().getWidth());
        int maxy = (int) (model.getTopLeftPosition().getY() + model.getDimensions().getHeight());
        if (maxx - minx < maxy - miny) {
            maxx = maxx + (int) (model.getDimensions().getHeight() - model.getDimensions().getWidth());
        } else {
            maxy = maxy + (int) (model.getDimensions().getWidth() - model.getDimensions().getHeight());
        }
        
        if (isExportJson()) {
            StringBuilder bounds = new StringBuilder();
            bounds.append("var minx = ").append(model.getTopLeftPosition().getX()).append(";\n")
            .append("var miny = ").append(model.getTopLeftPosition().getY()).append(";\n")
            .append("var maxx = ").append(maxx).append(";\n")
            .append("var maxy = ").append(maxy).append(";\n");

            try {
                FileUtils.writeStringToFile(new File(directory + File.separator + "bounds.js"), bounds.toString());
            } catch (IOException ex) {
                Exceptions.printStackTrace(ex);
            }
        }

        Progress.finish(progress);

        return !cancel;
    }
    
    private void nextTile() {
        x = x + step;
        if (x >= (Math.pow(2, z)) && (y < Math.pow(2, z) - step)) { // x is at eol
            x = 0;
            y = y + step; // next row
        }
        if ((x >= Math.pow(2, z) && y >= Math.pow(2, z) - step)) { // x and y at eol
            z++;
            x = 0;
            y = 0;
            if (verbose > 0) {
                System.out.println("Next level: " + z);
            }
            System.out.println("Done rendering layer in " + (int) (System.currentTimeMillis() - layerStart) / 1000 + " seconds");
            layerStart = System.currentTimeMillis();
        }
        if (z == 0) {
            z++;
            x = 0;
            y = 0;
        }
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
    
    public boolean isExportJson() {
        return exportJson;
    }

    public void setExportJson(boolean exportJson) {
        this.exportJson = exportJson;
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
        this.progress = progressTicket;
    }

    public boolean isLast() {
        return z == levels + 1;
    }

    public String getFilename(String filename) {
        return filename + "-" + x + "-" + y + "-" + z;
    }
    
    public String getFilename(String filename, int x, int y) {
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
