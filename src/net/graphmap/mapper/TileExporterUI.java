/*
Copyright 2014
Authors : Pekka Maksimainen
Website : http://graphmap.net
 */
package net.graphmap.mapper;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import org.gephi.desktop.io.export.spi.ExporterClassUI;
import org.gephi.io.exporter.api.ExportController;
import org.gephi.utils.longtask.api.LongTaskErrorHandler;
import org.gephi.utils.longtask.api.LongTaskExecutor;
import org.gephi.utils.progress.Progress;
import org.gephi.utils.progress.ProgressTicket;
import org.gephi.utils.progress.ProgressTicketProvider;
import org.openide.DialogDescriptor;
import org.openide.DialogDisplayer;
import org.openide.NotifyDescriptor;
import org.openide.util.Lookup;
import org.openide.util.lookup.ServiceProvider;

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

            
//            final ExportController ec = Lookup.getDefault().lookup(ExportController.class);
//            final String filePath = exporter.getDirectory();
            //String[] tokens = filePath.split("\\.(?=[^\\.]+$)");
            //String fileName = FilenameUtils.getBaseName(filePath);
            //String fileName = tokens[0];
            //System.out.println("filename: " + fileName);

            //System.out.println("Running at " + filePath);
            //System.out.println("Using " + fileName + " for directory");

            executor.execute(exporter, new Runnable() {

                @Override
                public void run() {
                    try {
                        ProgressTicketProvider progressProvider = Lookup.getDefault().lookup(ProgressTicketProvider.class);
                        ProgressTicket progressTicket = progressProvider.createTicket("Google Maps Exporter", null);
                        
                        int lastTicket = 0;
                        for (int i = 0; i <= exporter.getLevels(); i++) {
                            lastTicket += ((Double) Math.pow(2, 2 * i)).intValue();
                        }
                        ec.exportFile(new File(filePath + File.separator + exporter.getFilename("tile") + ".png"), exporter);
                        Progress.start(progressTicket, lastTicket);
                        do {
                            ec.exportFile(new File(filePath + File.separator + exporter.getFilename("tile") + ".png"), exporter);
                            Progress.progress(progressTicket);
                        } while (!exporter.isLast());
                        Progress.finish(progressTicket);
                        
                        // Save HTML
                        InputStream sourceHtml = null;
                        InputStream sourceJs = null;
                        OutputStream destinationHtml = null;                        
                        OutputStream destinationJs = null;
                        try {
                            File outFileHtml = new File(filePath + File.separator + "index.html");
                            File outFileJs = new File(filePath + File.separator + "map.js");
                            sourceHtml = getClass().getResourceAsStream("/templates/index.html");
                            sourceJs = getClass().getResourceAsStream("/templates/map.js");
                            destinationHtml = new FileOutputStream(outFileHtml);
                            destinationJs = new FileOutputStream(outFileJs);
                            byte[] buf = new byte[1024];
                            int bytesRead;
                            while ((bytesRead = sourceHtml.read(buf)) > 0) {
                                destinationHtml.write(buf, 0, bytesRead);
                            }
                            while ((bytesRead = sourceJs.read(buf)) > 0) {
                                destinationJs.write(buf, 0, bytesRead);
                            }
                        } finally {
                            if (sourceHtml != null) {
                                sourceHtml.close();
                            }
                            if (destinationHtml != null) {
                                destinationHtml.close();
                            }
                            if (sourceJs != null) {
                                sourceJs.close();
                            }
                            if (destinationJs != null) {
                                destinationJs.close();
                            }
                        }
                        
                       
                        // Save JavaScript
                        
                    } catch (IOException ex) {
                        ex.printStackTrace();
                        return;
                    }
                }
            });
            
            //ec.exportFiles(new File(filePath), tpe);
        }
    }
}
