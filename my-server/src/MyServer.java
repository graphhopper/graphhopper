import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.servlet.GuiceFilter;
import com.graphhopper.http.GHServer;
import com.graphhopper.util.CmdArgs;
import com.graphhopper.util.Helper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by robin on 25/09/15.
 */
public class MyServer {

    // execute via
    // java -jar target/your-project-0.1-jar-with-dependencies.jar osmreader.osm=berlin.pbf graph.location=/somewhere/graph
    public static void main(String[] args) throws Exception {
        CmdArgs tmpArgs = CmdArgs.readFromConfigAndMerge(CmdArgs.read(args), "config", "graphhopper.config");
        System.out.println(tmpArgs.toString());
        new MyServer(tmpArgs).start();
    }
    private final Logger logger = LoggerFactory.getLogger(getClass());
    private final CmdArgs cmdArgs;
    private GHServer server;

    public MyServer(CmdArgs args) {
        this.cmdArgs = args;
    }

    /**
     * Starts 'pure' GraphHopper server with the specified injector
     */
    public void start(Injector injector) throws Exception {
        server = new GHServer(cmdArgs);
        server.start(injector);
        logger.info("Memory utilization:" + Helper.getMemInfo() + ", " + cmdArgs.get("graph.flagEncoders", ""));
    }

    /**
     * Starts GraphHopper server with routing and custom module features.
     */
    public void start() throws Exception {
        Injector injector = Guice.createInjector(createModule());
        start(injector);
    }

    protected Module createModule() {
        return new AbstractModule() {
            @Override
            protected void configure() {
                binder().requireExplicitBindings();

                //install(new MyDefaultGuiceModule(cmdArgs));
                //install(new MyServletGuiceModule(cmdArgs));

                bind(GuiceFilter.class);
            }
        };
    }

    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}