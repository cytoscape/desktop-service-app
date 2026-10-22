package org.cytoscape.serviceapps.call;

import java.util.Set;

import org.junit.jupiter.api.Test;

import org.cytoscape.application.CyApplicationManager;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.serviceapps.testsupport.ContractNetwork;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** The uiInput rules, resolved before anything is sent. */
class UiTargetResolverTest {

    private final ContractNetwork active = new ContractNetwork("active");
    private final ContractNetwork other = new ContractNetwork("other");
    private final CyNetworkView otherSecondView = other.newView();
    private final CyNetworkManager networks = mock(CyNetworkManager.class);
    private final CyNetworkViewManager views = mock(CyNetworkViewManager.class);
    private final CyApplicationManager applications = mock(CyApplicationManager.class);
    private final UiTargetResolver resolver = new UiTargetResolver(networks, views, applications);

    {
        when(networks.getNetwork(active.network.getSUID())).thenReturn(active.network);
        when(networks.getNetwork(other.network.getSUID())).thenReturn(other.network);
        when(views.getNetworkViewSet())
                .thenReturn(Set.of(active.view, other.view, otherSecondView));
        when(applications.getCurrentNetwork()).thenReturn(active.network);
    }

    private String json(String body) {
        return "{" + body + "}";
    }

    private InvalidInputException rejected(String uiInput) {
        return assertThrows(InvalidInputException.class, () -> resolver.resolve(uiInput));
    }

    @Test
    void omittedMeansTheActiveNetworkAndEveryView() {
        for (String omitted : new String[] {null, "", "  ", "{}"}) {
            UiTarget target = resolver.resolve(omitted);
            assertSame(active.network, target.network(), String.valueOf(omitted));
            assertNull(target.view());
        }
    }

    @Test
    void networkIdAloneTargetsThatNetworkAndEveryView() {
        UiTarget target = resolver.resolve(json("\"networkId\":" + other.network.getSUID()));

        assertSame(other.network, target.network());
        assertNull(target.view());
    }

    @Test
    void networkViewIdAloneSetsTheNetworkToTheViewsNetwork() {
        UiTarget target = resolver.resolve(json("\"networkViewId\":" + otherSecondView.getSUID()));

        assertSame(other.network, target.network());
        assertSame(otherSecondView, target.view());
    }

    @Test
    void aViewOfTheGivenNetworkIsAccepted() {
        UiTarget target =
                resolver.resolve(
                        json(
                                "\"networkId\":"
                                        + other.network.getSUID()
                                        + ",\"networkViewId\":"
                                        + other.view.getSUID()));

        assertSame(other.view, target.view());
    }

    @Test
    void aViewOfAnotherNetworkIsRejected() {
        InvalidInputException e =
                rejected(
                        json(
                                "\"networkId\":"
                                        + active.network.getSUID()
                                        + ",\"networkViewId\":"
                                        + other.view.getSUID()));

        assertEquals(
                "networkViewId: view "
                        + other.view.getSUID()
                        + " is a view of network "
                        + other.network.getSUID()
                        + ", not of network "
                        + active.network.getSUID(),
                e.problems().get(0));
    }

    @Test
    void unknownIdsAreRejected() {
        assertEquals(
                "networkId: no network has SUID 999999",
                rejected(json("\"networkId\":999999")).problems().get(0));
        assertEquals(
                "networkViewId: no network view has SUID 999999",
                rejected(json("\"networkViewId\":999999")).problems().get(0));
    }

    @Test
    void idsAreWholeNumbersGivenAsNumbersOrText() {
        assertSame(
                other.network,
                resolver.resolve(json("\"networkId\":\"" + other.network.getSUID() + "\""))
                        .network());
        assertTrue(
                rejected(json("\"networkId\":\"abc\""))
                        .getMessage()
                        .contains("networkId: must be a SUID"));
        assertTrue(
                rejected(json("\"networkId\":[1]"))
                        .getMessage()
                        .contains("networkId: must be a SUID"));
    }

    @Test
    void unknownFieldsAreRejected() {
        assertEquals(
                "unknown field 'networkCollectionId'; uiInput takes only networkId and networkViewId",
                rejected(json("\"networkCollectionId\":1")).problems().get(0));
    }

    @Test
    void anObjectPassedInsteadOfAStringIsExplained() {
        IllegalArgumentException e =
                assertThrows(
                        IllegalArgumentException.class, () -> resolver.resolve("{networkId=52}"));

        assertTrue(
                e.getMessage().startsWith("uiInput must be passed as a JSON string"),
                e.getMessage());
    }

    @Test
    void textThatIsntJsonIsRejected() {
        IllegalArgumentException e =
                assertThrows(IllegalArgumentException.class, () -> resolver.resolve("[1,"));

        assertTrue(e.getMessage().startsWith("uiInput is not valid JSON"), e.getMessage());
    }
}
