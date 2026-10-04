package com.caroute.ui;

import com.caroute.domain.*;
import com.caroute.engine.RouteEngine;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class PanelPreviewTest extends RuneLiteUiTestSupport
{
    @Test public void sidebarRendersItsActualNextStopHeadlessly() throws Exception
    {
        List<CombatAchievement> tasks=new ArrayList<>();String[] names={"A clean opening","One careful kill","Shared progression","A second stop"};
        for(int i=0;i<4;i++)tasks.add(CombatAchievement.builder(i,names[i],i<3?"Example boss":"Following activity",AchievementTier.EASY)
            .description("Synthetic preview task; complete its static condition before returning to the route.").secondsPerKill(120).setupMinutes(6).requirementsKnown(true).timingKnown(true).build());
        PlayerProfile p=PlayerProfile.builder().loaded(true).catalogueComplete(true).currentPoints(8).thresholds(Map.of(AchievementTier.EASY,12)).build();
        RoutePlan plan=new RouteEngine().plan(tasks,p,RoutingPreferences.builder().target(AchievementTier.EASY).build());
        SwingUtilities.invokeAndWait(()-> {
            try {
                CaRoutePanel panel=new CaRoutePanel((id,action)->{},()->{},ids->{},target->{},(flag,pref)->{});
                panel.display(plan,Collections.singletonList("Synthetic preview; no live account data."));
                JTabbedPane tabs=find(panel,JTabbedPane.class);assertNotNull(tabs);tabs.setSelectedIndex(tabs.indexOfTab("Route"));
                panel.setSize(280,1000);layout(panel);
                BufferedImage image=new BufferedImage(280,1000,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();panel.printAll(graphics);graphics.dispose();
                Path path=Paths.get("build","ui-preview.png");Files.createDirectories(path.getParent());ImageIO.write(image,"png",path.toFile());
                assertSame(plan,panel.currentPlan());assertEquals(4,plan.projectedPoints());
            }catch(Exception ex){throw new RuntimeException(ex);}
        });
    }
    private static void layout(Container container){container.doLayout();for(Component component:container.getComponents())if(component instanceof Container)layout((Container)component);}
    private static <T extends Component> T find(Container parent,Class<T> type)
    {
        for(Component component:parent.getComponents())
        {
            if(type.isInstance(component))return type.cast(component);
            if(component instanceof Container){T child=find((Container)component,type);if(child!=null)return child;}
        }
        return null;
    }
}
