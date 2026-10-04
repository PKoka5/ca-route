package com.caroute.verification;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;
import static org.junit.Assert.*;

/** The Hub replaces build.gradle, so notices must be ordinary source resources. */
public class PackagedNoticesTest
{
    @Test public void licenseResourcesMatchTheirReviewedSources() throws Exception
    {
        matches("LICENSE", "/META-INF/licenses/CA_ROUTE_BSD_2_CLAUSE.txt");
        matches("licenses/CA_HELPER_BSD_2_CLAUSE.txt", "/META-INF/licenses/CA_HELPER_BSD_2_CLAUSE.txt");
        matches("licenses/RUNELITE_WORLD_MAP_BSD_2_CLAUSE.txt", "/META-INF/licenses/RUNELITE_WORLD_MAP_BSD_2_CLAUSE.txt");
        matches("THIRD_PARTY_NOTICES.md", "/META-INF/THIRD_PARTY_NOTICES.md");
    }
    private void matches(String source, String resource) throws Exception
    {
        try (InputStream stream = getClass().getResourceAsStream(resource))
        {
            assertNotNull("Missing bundled notice: " + resource, stream);
            assertArrayEquals(Files.readAllBytes(Path.of(source)), stream.readAllBytes());
        }
    }
}
