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
    private final ExporterTileSettings settings = new ExporterTileSettings();

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
        final TileExporter exporter = new TileExporter();
        final JSONExporter je = new JSONExporter();
        final TileExporterPanel settingPanel = new TileExporterPanel();
        settings.load(exporter);
        settingPanel.setup(exporter);
        final DialogDescriptor dd = new DialogDescriptor(settingPanel, "Google Maps Exporter");
        Object result = DialogDisplayer.getDefault().notify(dd);
        if (result == NotifyDescriptor.OK_OPTION) {
            settingPanel.unsetup(true);
            settings.save(exporter);
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
                        InputStream sourceHtml2 = null;
                        InputStream sourceMapJs = null;
                        InputStream sourceTaffyJs = null;
                        InputStream sourceUiJs = null;
                        InputStream sourceUi2Js = null;
                        InputStream sourceMarkerPng = null;
                        InputStream sourceDotObjectJs = null;
                        OutputStream destinationHtml = null;
                        OutputStream destinationHtml2 = null;
                        OutputStream destinationMapJs = null;
                        OutputStream destinationTaffyJs = null;
                        OutputStream destinationUiJs = null;
                        OutputStream destinationUi2Js = null;
                        OutputStream destinationMarkerPng = null;
                        OutputStream destinationDotObjectJs = null;
                        try {
                            File outFileHtml = new File(filePath + File.separator + "index.html");
                            File outFileHtml2 = new File(filePath + File.separator + "index2.html");
                            File outFileMapJs = new File(filePath + File.separator + "map.js");
                            File outFileTaffyJs = new File(filePath + File.separator + "taffy.js");
                            File outFileUiJs = new File(filePath + File.separator + "ui.js");
                            File outFileUi2Js = new File(filePath + File.separator + "ui2.js");
                            File outFileMarkerPng = new File(filePath + File.separator + "marker.png");
                            File outFileDotObjectJs = new File(filePath + File.separator + "dot-object.js");

                            sourceHtml = getClass().getResourceAsStream("/templates/index.html");
                            sourceHtml2 = getClass().getResourceAsStream("/templates/index2.html");
                            sourceMapJs = getClass().getResourceAsStream("/templates/map.js");
                            sourceTaffyJs = getClass().getResourceAsStream("/templates/taffy.js");
                            sourceUiJs = getClass().getResourceAsStream("/templates/ui.js");
                            sourceUi2Js = getClass().getResourceAsStream("/templates/ui2.js");
                            sourceMarkerPng = getClass().getResourceAsStream("/templates/marker.png");
                            sourceDotObjectJs = getClass().getResourceAsStream("/templates/dot-object.js");
                            destinationHtml = new FileOutputStream(outFileHtml);
                            destinationHtml2 = new FileOutputStream(outFileHtml2);
                            destinationMapJs = new FileOutputStream(outFileMapJs);
                            if (exporter.isExportJson()) {
                                destinationTaffyJs = new FileOutputStream(outFileTaffyJs);
                                destinationUiJs = new FileOutputStream(outFileUiJs);
                                destinationUi2Js = new FileOutputStream(outFileUi2Js);
                                destinationMarkerPng = new FileOutputStream(outFileMarkerPng);
                                destinationDotObjectJs = new FileOutputStream(outFileDotObjectJs);
                            }
                            byte[] buf = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = sourceHtml.read(buf)) > 0) {
                                destinationHtml.write(buf, 0, bytesRead);
                            }
                            while ((bytesRead = sourceHtml2.read(buf)) > 0) {
                                destinationHtml2.write(buf, 0, bytesRead);
                            }
                            while ((bytesRead = sourceMapJs.read(buf)) > 0) {
                                destinationMapJs.write(buf, 0, bytesRead);
                            }
                            if (exporter.isExportJson()) {
                                while ((bytesRead = sourceUiJs.read(buf)) > 0) {
                                    destinationUiJs.write(buf, 0, bytesRead);
                                }
                                while ((bytesRead = sourceUi2Js.read(buf)) > 0) {
                                    destinationUi2Js.write(buf, 0, bytesRead);
                                }
                                while ((bytesRead = sourceTaffyJs.read(buf)) > 0) {
                                    destinationTaffyJs.write(buf, 0, bytesRead);
                                }
                                while ((bytesRead = sourceMarkerPng.read(buf)) > 0) {
                                    destinationMarkerPng.write(buf, 0, bytesRead);
                                }
                                while ((bytesRead = sourceDotObjectJs.read(buf)) > 0) {
                                    destinationDotObjectJs.write(buf, 0, bytesRead);
                                }
                            }
                        } finally {
                            if (sourceHtml != null) {
                                sourceHtml.close();
                            }
                            if (sourceHtml2 != null) {
                                sourceHtml.close();
                            }
                            if (destinationHtml != null) {
                                destinationHtml.close();
                            }
                            if (destinationHtml2 != null) {
                                destinationHtml.close();
                            }
                            if (sourceMapJs != null) {
                                sourceMapJs.close();
                            }
                            if (sourceDotObjectJs != null) {
                                sourceDotObjectJs.close();
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
                            if (sourceUi2Js != null) {
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
                            if (destinationDotObjectJs != null) {
                                destinationDotObjectJs.close();
                            }
                        }

                        if (exporter.isExportJson()) {
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
                        }
                    } catch (IOException ex) {
                        ex.printStackTrace();
                    }
                }
            });
        } else {
            settingPanel.unsetup(false);
        }
    }
    
    private static class ExporterTileSettings {

        private int width = 256;
        private int height = 256;
        private int levels = 3;
        private boolean isExportJson = true;
        private String path = System.getProperty("user.home") + File.separator + "tiles";

        public void load(TileExporter exporter) {
            exporter.setHeight(height);
            exporter.setWidth(width);
            exporter.setLevels(levels);
            exporter.setExportJson(isExportJson);
            exporter.setDirectory(path);
        }

        public void save(TileExporter exporter) {
            this.height = exporter.getHeight();
            this.width = exporter.getWidth();
            this.levels = exporter.getLevels();
            this.isExportJson = exporter.isExportJson();
            this.path = exporter.getDirectory();
        }
    }
}
