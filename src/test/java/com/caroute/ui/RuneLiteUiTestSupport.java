package com.caroute.ui;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import net.runelite.client.ui.laf.RuneLiteLAF;
import org.junit.BeforeClass;
import static org.junit.Assert.assertTrue;

/** Installs the client look and feel before any test constructs sidebar controls. */
abstract class RuneLiteUiTestSupport
{
    @BeforeClass public static void installRuneLiteLookAndFeel() throws Exception
    {
        SwingUtilities.invokeAndWait(() -> {
            if (!(UIManager.getLookAndFeel() instanceof RuneLiteLAF))
                assertTrue("RuneLite look and feel must initialize successfully", RuneLiteLAF.setup());
            assertTrue("UI assertions and previews use the actual RuneLite look and feel",
                UIManager.getLookAndFeel() instanceof RuneLiteLAF);
        });
    }
}
