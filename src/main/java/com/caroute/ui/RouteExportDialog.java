package com.caroute.ui;

import java.awt.Component;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.function.BooleanSupplier;
import javax.swing.*;
import net.runelite.client.util.Filepath;

/** Explicit user-initiated local save; never sends the route to another application/service. */
public final class RouteExportDialog
{
    private RouteExportDialog() {}
    public static void save(Component parent, String json, Executor worker, BooleanSupplier current)
    {
        if (!current.getAsBoolean()) return;
        List<Filepath> selected = new Filepath.Chooser().setIsSave().setAcceptsFiles()
            .setDialogTitle("Save Tasks Tracker route").addExtensionFilter("Route JSON", "json")
            .setDefaultExtension("json").setFileName("ca-route.json").showDialog(parent);
        if (selected == null || selected.isEmpty() || !current.getAsBoolean()) return;
        Filepath target = selected.get(0);
        if (target.exists() && JOptionPane.showConfirmDialog(parent, "Replace " + target.getFileName() + "?", "Replace route file", JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) return;
        if (!current.getAsBoolean()) return;
        try {
            worker.execute(() -> {
                if (!current.getAsBoolean()) return;
                try {
                    target.write(json);
                    SwingUtilities.invokeLater(() -> {
                        if (current.getAsBoolean()) JOptionPane.showMessageDialog(parent, "Route saved. Import this JSON manually in Tasks Tracker.");
                    });
                } catch (IOException ex) {
                    SwingUtilities.invokeLater(() -> {
                        if (current.getAsBoolean()) JOptionPane.showMessageDialog(parent, "Could not save the route: " + ex.getMessage(), "Export failed", JOptionPane.ERROR_MESSAGE);
                    });
                }
            });
        } catch (RejectedExecutionException ex) {
            if (current.getAsBoolean()) error(parent, "Could not save the route. The plugin is shutting down.");
        }
    }
    public static void error(Component parent, String message)
    { JOptionPane.showMessageDialog(parent, message, "Route export", JOptionPane.ERROR_MESSAGE); }
}
