package com.caroute;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

/** Development client entry point; not a logged-in automated gameplay test. */
public class CaRoutePluginTest
{
    public static void main(String[] args) throws Exception
    {
        ExternalPluginManager.loadBuiltin(CaRoutePlugin.class);
        RuneLite.main(args);
    }
}
