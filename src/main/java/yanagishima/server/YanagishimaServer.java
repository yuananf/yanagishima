package yanagishima.server;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.GuiceFilter;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import me.geso.tinyorm.TinyORM;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.DefaultServlet;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import yanagishima.filter.YanagishimaFilter;
import yanagishima.module.*;

import javax.servlet.DispatcherType;
import java.io.*;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Properties;

public class YanagishimaServer {

	private static Logger LOGGER = LoggerFactory
			.getLogger(YanagishimaServer.class);

	public static void main(String[] args) throws Exception {
		
		Properties properties = loadProps(args, new OptionParser());
		int jettyPort = Integer.parseInt(Optional.ofNullable(properties.getProperty("jetty.port")).orElse("8080"));
		String webResourceDir = properties.getProperty("web.resource.dir", "web");

		PrestoServiceModule prestoServiceModule = new PrestoServiceModule(properties);
		PrestoServletModule prestoServletModule = new PrestoServletModule();
		HiveServiceModule hiveServiceModule = new HiveServiceModule(properties);
		HiveServletModule hiveServletModule = new HiveServletModule();
		DbModule dbModule = new DbModule();
		PoolModule poolModule = new PoolModule();
		ElasticsearchServiceModule elasticsearchServiceModule = new ElasticsearchServiceModule(properties);
		ElasticsearchServletModule elasticsearchServletModule = new ElasticsearchServletModule();
		@SuppressWarnings("unused")
		Injector injector = Guice.createInjector(prestoServiceModule,
				prestoServletModule, hiveServiceModule, hiveServletModule, dbModule, poolModule, elasticsearchServiceModule, elasticsearchServletModule);

		TinyORM tinyORM = injector.getInstance(TinyORM.class);
		try(Connection connection = tinyORM.getConnection()) {
			try(Statement statement = connection.createStatement()) {
				statement.executeUpdate("create table if not exists query (datasource text, engine text, query_id text, fetch_result_time_string text, query_string text, user text, status text, elapsed_time_millis integer, result_file_size integer, linenumber integer, primary key(datasource, engine, query_id))");
				statement.executeUpdate("create table if not exists publish (publish_id text, datasource text, engine text, query_id text, user text, primary key(publish_id))");
				statement.executeUpdate("create table if not exists bookmark (bookmark_id integer primary key autoincrement, datasource text, engine text, query text, title text, user text)");
				statement.executeUpdate("create table if not exists comment (datasource text, engine text, query_id text, content text, update_time_string text, user text, like_count integer, primary key(datasource, engine, query_id))");
				statement.executeUpdate("create table if not exists label (datasource text, engine text, query_id text, label_name text, primary key(datasource, engine, query_id))");
			}
		}

		Server server = new Server(jettyPort);
		server.setAttribute("org.eclipse.jetty.server.Request.maxFormContentSize", -1);

		ServletContextHandler servletContextHandler = new ServletContextHandler(
				server, "/", ServletContextHandler.SESSIONS);
		servletContextHandler.addFilter(new FilterHolder(new YanagishimaFilter(Boolean.parseBoolean(Optional.ofNullable(properties.getProperty("cors.enabled")).orElse("false")), properties.getProperty("audit.http.header.name"))), "/*", EnumSet.of(DispatcherType.REQUEST));
		servletContextHandler.addFilter(GuiceFilter.class, "/*",
				EnumSet.allOf(DispatcherType.class));

		servletContextHandler.addServlet(DefaultServlet.class, "/");
		
		servletContextHandler.setResourceBase(webResourceDir);

		LOGGER.info("Yanagishima Server started...");

		server.start();

		Runtime.getRuntime().addShutdownHook(new Thread() {

			public void run() {
				LOGGER.info("Shutting down Yanagishima Server...");
				try {
					server.stop();
					server.destroy();
				} catch (Exception e) {
					LOGGER.error(
							"Error while shutting down Yanagishima Server.", e);
				}
			}
		});
		LOGGER.info("Yanagishima Server running port " + jettyPort + ".");
	}

	public static Properties loadProps(String[] args, OptionParser parser) {
		OptionSpec<String> configDirectory = parser
				.acceptsAll(Arrays.asList("c", "conf"),
						"The conf directory for Yanagishima.")
				.withRequiredArg().describedAs("conf").ofType(String.class);

		OptionSet options = parser.parse(args);

		if (options.has(configDirectory)) {
			String path = options.valueOf(configDirectory);
			LOGGER.info("Loading yanagishima settings file from " + path);
			File dir = new File(path);
			if (!dir.exists()) {
				throw new RuntimeException("Conf directory " + path
						+ " doesn't exist.");
			} else if (!dir.isDirectory()) {
				throw new RuntimeException("Conf directory " + path
						+ " isn't a directory.");
			} else {
				Properties yanagishimaSettings = loadYanagishimaConfigurationFromDirectory(dir);
				return yanagishimaSettings;
			}
		} else {
			throw new RuntimeException("Conf parameter not set.");
		}

	}

	private static Properties loadYanagishimaConfigurationFromDirectory(File dir) {

		File yanagishimaPropertiesFile = new File(dir, "yanagishima.properties");

		if (yanagishimaPropertiesFile.exists()
				&& yanagishimaPropertiesFile.isFile()) {
			LOGGER.info("Loading yanagishima properties file");
			try (InputStream inputStream = new BufferedInputStream(
					new FileInputStream(yanagishimaPropertiesFile))) {
				Properties properties = new Properties();
				properties.load(inputStream);
				return properties;
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		} else {
			throw new RuntimeException("yanagishima.properties is not found.");
		}

	}

}
