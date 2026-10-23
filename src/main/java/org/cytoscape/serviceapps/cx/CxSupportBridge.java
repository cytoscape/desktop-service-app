package org.cytoscape.serviceapps.cx;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;

import org.cytoscape.io.read.CyNetworkReader;
import org.cytoscape.io.read.InputStreamTaskFactory;
import org.cytoscape.io.write.CyNetworkViewWriterFactory;
import org.cytoscape.io.write.CyWriter;
import org.cytoscape.model.CyNetwork;
import org.cytoscape.model.CyNetworkManager;
import org.cytoscape.view.model.CyNetworkView;
import org.cytoscape.view.model.CyNetworkViewManager;
import org.cytoscape.work.Task;
import org.cytoscape.work.TaskIterator;
import org.cytoscape.work.TaskMonitor;

/**
 * {@link Cx2Support} over Cytoscape's CX Support app, following cyndex-2.
 *
 * <p>CX Support registers its CX2 writer as a {@link CyNetworkViewWriterFactory} with {@code
 * id=cx2NetworkWriterFactory}, and its CX2 reader as an {@link InputStreamTaskFactory} with {@code
 * id=cytoscapeCx2NetworkReaderFactory}. The activator passes every such service to this bridge,
 * which keeps the two it needs.
 */
public class CxSupportBridge implements Cx2Support {

    public static final String WRITER_ID = "cx2NetworkWriterFactory";
    public static final String READER_ID = "cytoscapeCx2NetworkReaderFactory";

    private static final Logger LOGGER = LoggerFactory.getLogger(CxSupportBridge.class);

    private final CyNetworkManager networkManager;
    private final CyNetworkViewManager viewManager;
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile CyNetworkViewWriterFactory writer;
    private volatile InputStreamTaskFactory reader;

    public CxSupportBridge(CyNetworkManager networkManager, CyNetworkViewManager viewManager) {
        this.networkManager = networkManager;
        this.viewManager = viewManager;
    }

    @Override
    public boolean available() {
        return writer != null && reader != null;
    }

    @Override
    public void addAvailabilityListener(Runnable listener) {
        listeners.add(listener);
    }

    // --- OSGi service listener callbacks ---

    public void addWriterFactory(CyNetworkViewWriterFactory factory, Map<?, ?> props) {
        if (WRITER_ID.equals(String.valueOf(props.get("id")))) {
            writer = factory;
            LOGGER.info("CX Support CX2 writer found ({})", WRITER_ID);
            changed();
        }
    }

    public void removeWriterFactory(CyNetworkViewWriterFactory factory, Map<?, ?> props) {
        if (factory == writer) {
            writer = null;
            LOGGER.info("CX Support CX2 writer removed");
            changed();
        }
    }

    public void addReaderFactory(InputStreamTaskFactory factory, Map<?, ?> props) {
        if (READER_ID.equals(String.valueOf(props.get("id")))) {
            reader = factory;
            LOGGER.info("CX Support CX2 reader found ({})", READER_ID);
            changed();
        }
    }

    public void removeReaderFactory(InputStreamTaskFactory factory, Map<?, ?> props) {
        if (factory == reader) {
            reader = null;
            LOGGER.info("CX Support CX2 reader removed");
            changed();
        }
    }

    private void changed() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                LOGGER.warn("CX Support availability listener failed", e);
            }
        }
    }

    // --- Cx2Support ---

    @Override
    public JsonArray export(CyNetwork network, CyNetworkView view) {
        CyNetworkViewWriterFactory factory = writer;
        if (factory == null) {
            throw new Cx2Exception("Cytoscape's CX Support app isn't available to write CX2");
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            CyWriter cyWriter =
                    view == null
                            ? factory.createWriter(out, network)
                            : factory.createWriter(out, view);
            // Same settings as cyndex-2: IDs from the CX Element ID column, this network only.
            invoke(cyWriter, "setUseCxId", Boolean.TRUE);
            invoke(cyWriter, "setWriteSiblings", Boolean.FALSE);
            cyWriter.run(new QuietTaskMonitor());
            return JsonParser.parseString(out.toString(StandardCharsets.UTF_8)).getAsJsonArray();
        } catch (Cx2Exception e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Cx2Exception("interrupted while writing the network as CX2", e);
        } catch (Exception e) {
            throw new Cx2Exception("could not write the network as CX2: " + e.getMessage(), e);
        }
    }

    @Override
    public List<CyNetwork> importAndRegister(JsonArray cx2, String name) {
        InputStreamTaskFactory factory = reader;
        if (factory == null) {
            throw new Cx2Exception("Cytoscape's CX Support app isn't available to read CX2");
        }
        try {
            byte[] bytes = cx2.toString().getBytes(StandardCharsets.UTF_8);
            TaskIterator tasks = factory.createTaskIterator(new ByteArrayInputStream(bytes), name);
            Task first = tasks.next();
            if (!(first instanceof CyNetworkReader networkReader)) {
                throw new Cx2Exception("CX Support's CX2 reader is not a network reader");
            }
            invoke(first, "setCreateView", Boolean.TRUE);
            first.run(new QuietTaskMonitor());
            while (tasks.hasNext()) {
                tasks.next().run(new QuietTaskMonitor());
            }

            List<CyNetwork> networks = new ArrayList<>(Arrays.asList(networkReader.getNetworks()));
            for (CyNetwork network : networks) {
                networkManager.addNetwork(network);
                CyNetworkView[] built = new CyNetworkView[1];
                Runnable build = () -> built[0] = networkReader.buildCyNetworkView(network);
                if (SwingUtilities.isEventDispatchThread()) {
                    build.run();
                } else {
                    SwingUtilities.invokeAndWait(build);
                }
                if (built[0] != null && viewManager.getNetworkViews(network).isEmpty()) {
                    viewManager.addNetworkView(built[0]);
                }
            }
            return networks;
        } catch (Cx2Exception e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new Cx2Exception("interrupted while importing the CX2 network", e);
        } catch (Exception e) {
            throw new Cx2Exception("could not import the CX2 network: " + e.getMessage(), e);
        }
    }

    /** Calls an optional setter that CX Support's classes expose, if present. */
    private static void invoke(Object target, String setter, Boolean value) {
        try {
            Method method = target.getClass().getMethod(setter, Boolean.class);
            method.invoke(target, value);
        } catch (NoSuchMethodException e) {
            LOGGER.warn(
                    "CX Support's {} has no {}(Boolean); using its default",
                    target.getClass().getSimpleName(),
                    setter);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn(
                    "Could not call {} on CX Support's {}",
                    setter,
                    target.getClass().getSimpleName(),
                    e);
        }
    }

    /** A task monitor that ignores progress, for tasks run inside a run. */
    static final class QuietTaskMonitor implements TaskMonitor {
        @Override
        public void setTitle(String title) {}

        @Override
        public void setProgress(double progress) {}

        @Override
        public void setStatusMessage(String message) {}

        @Override
        public void showMessage(Level level, String message) {}
    }
}
