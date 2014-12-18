/*
Copyright 2014
Authors : Pekka Maksimainen
Website : http://graphmap.net
 */
package net.graphmap.mapper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.gephi.desktop.io.export.spi.ExporterClassUI;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.utils.longtask.api.LongTaskErrorHandler;
import org.gephi.utils.longtask.api.LongTaskExecutor;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;
import uk.ac.ox.oii.jsonexporter.JSONExporter;

/**
 * @author Pekka Maksimainen
 */
@ServiceProvider(service = ExporterClassUI.class)
public class TileExporterUI implements ExporterClassUI {

    private final LongTaskErrorHandler errorHandler;
    private boolean cancelled = true;

    public TileExporterUI() {
        //Create a generic error handler called if the task raises an exception
        errorHandler = new LongTaskErrorHandler() {

            @Override
            public void fatalError(Throwable t) {
                cancelled = true;
                String message = t.getCause().getMessage();
                if (message == null || message.isEmpty()) {
                    message = t.getMessage();
                }
                NotifyDescriptor.Message msg = new NotifyDescriptor.Message(message, NotifyDescriptor.WARNING_MESSAGE);
                DialogDisplayer.getDefault().notify(msg);
            }
        };
    }

    @Override
    public String getName() {
        return "Google Maps Exporter";
    }

    @Override
    public boolean isEnable() {
        return true;
    }
    
    @Override
    public void action() {
        final TilePreviewExporter exporter = new TilePreviewExporter();
        final JSONExporter je = new JSONExporter();
        TileExporterPanel settingPanel = new TileExporterPanel();
        settingPanel.setup(exporter);
        final DialogDescriptor dd = new DialogDescriptor(settingPanel, "Google Maps Exporter");
        Object result = DialogDisplayer.getDefault().notify(dd);
        if (result == NotifyDescriptor.OK_OPTION) {
            settingPanel.unsetup(true);
            LongTaskExecutor executor = new LongTaskExecutor(true, "Google Maps Exporter");
            executor.setDefaultErrorHandler(errorHandler);
            
            final ExportController ec = Lookup.getDefault().lookup(ExportController.class);
            final String filePath = exporter.getDirectory();
            
            File outDir = new File(filePath);
            if (!outDir.exists()) {
                try {
                    outDir.mkdir();
                } catch (SecurityException se) {
                    return;
                }
            }

            executor.execute(exporter, new Runnable() {

                @Override
                public void run() {
                    try {
                        ec.exportFile(new File(filePath + File.separator + exporter.getFilename("tile") + ".png"), exporter);
                        
                        // Save template files
                        InputStream sourceHtml = null;
                        InputStream sourceMapJs = null;
                        InputStream sourceTaffyJs = null;
                        InputStream sourceUiJs = null;
                        InputStream sourceMarkerPng = null;
                        OutputStream destinationHtml = null;                        
                        OutputStream destinationMapJs = null;
                        OutputStream destinationTaffyJs = null;
                        OutputStream destinationUiJs = null;
                        OutputStream destinationMarkerPng = null;
                        try {
                            File outFileHtml = new File(filePath + File.separator + "index.html");
                            File outFileMapJs = new File(filePath + File.separator + "map.js");
                            File outFileTaffyJs = new File(filePath + File.separator + "taffy.js");
                            File outFileUiJs = new File(filePath + File.separator + "ui.js");
                            File outFileMarkerPng = new File(filePath + File.separator + "marker.png");

                            sourceHtml = getClass().getResourceAsStream("/templates/index.html");
                            sourceMapJs = getClass().getResourceAsStream("/templates/map.js");
                            sourceTaffyJs = getClass().getResourceAsStream("/templates/taffy.js");
                            sourceUiJs = getClass().getResourceAsStream("/templates/ui.js");
                            sourceMarkerPng = getClass().getResourceAsStream("/templates/marker.png");
                            destinationHtml = new FileOutputStream(outFileHtml);
                            destinationMapJs = new FileOutputStream(outFileMapJs);
                            destinationTaffyJs = new FileOutputStream(outFileTaffyJs);
                            destinationUiJs = new FileOutputStream(outFileUiJs);
                            destinationMarkerPng = new FileOutputStream(outFileMarkerPng);
                            byte[] buf = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = sourceHtml.read(buf)) > 0) {
                                destinationHtml.write(buf, 0, bytesRead);
                            }
                            while ((bytesRead = sourceMapJs.read(buf)) > 0) {
                                destinationMapJs.write(buf, 0, bytesRead);
                            }
                            while ((bytesRead = sourceTaffyJs.read(buf)) > 0) {
                                destinationTaffyJs.write(buf, 0, bytesRead);
                            }
                            while ((bytesRead = sourceUiJs.read(buf)) > 0) {
                                destinationUiJs.write(buf, 0, bytesRead);
                            }
                            while ((bytesRead = sourceMarkerPng.read(buf)) > 0) {
                                destinationMarkerPng.write(buf, 0, bytesRead);
                            }
                        } finally {
                            if (sourceHtml != null) {
                                sourceHtml.close();
                            }
                            if (destinationHtml != null) {
                                destinationHtml.close();
                            }
                            if (sourceMapJs != null) {
                                sourceMapJs.close();
                            }
                            if (destinationMapJs != null) {
                                destinationMapJs.close();
                            }
                            if (sourceTaffyJs != null) {
                                destinationTaffyJs.close();
                            }
                            if (destinationTaffyJs != null) {
                                destinationTaffyJs.close();
                            }
                            if (sourceUiJs != null) {
                                destinationUiJs.close();
                            }
                            if (destinationUiJs != null) {
                                destinationUiJs.close();
                            }
                            if (sourceMarkerPng != null) {
                                destinationMarkerPng.close();
                            }
                            if (destinationMarkerPng != null) {
                                destinationMarkerPng.close();
                            }
                        }
                        File graphFile = new File(filePath + File.separator + "graph.json");
                        ec.exportFile(graphFile, je);
                        FileInputStream graphInputStream = new FileInputStream(filePath + File.separator + "graph.json");
                        try {
                            String graphJSON = IOUtils.toString(graphInputStream);
                            StringBuilder sb = new StringBuilder(graphJSON);
                            sb.insert(0, "var graph = ");
                            FileUtils.writeStringToFile(graphFile, sb.toString());
                        } finally {
                            graphInputStream.close();
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        }
    }
}
