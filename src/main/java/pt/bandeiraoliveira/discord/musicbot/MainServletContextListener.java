package pt.bandeiraoliveira.discord.musicbot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.security.auth.login.LoginException;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
/**
 *
 * @author Rodrigo
 */
public class MainServletContextListener implements ServletContextListener {

    
    private static final String PROPERTIES_FILENAME = "discord.properties";
    private static JDA jda;
    private static final Logger LOG = Logger.getLogger(MainServletContextListener.class.getName());

    @Override
    public void contextDestroyed(ServletContextEvent sce) {
	try {
	    jda.shutdown();
	    Thread.sleep(100);
	} catch (InterruptedException ex) {
	    LOG.log(Level.SEVERE, "Failed waiting for JDA to shutdown/n{0}\n", ex.getMessage());
	} finally {
	    jda.shutdownNow();
	}
	ServletContextListener.super.contextDestroyed(sce);
    }

    @Override
    public void contextInitialized(ServletContextEvent sce) {
        Properties properties = new Properties();
	try {
	    InputStream in = Search.class.getResourceAsStream("/" + PROPERTIES_FILENAME);
	    properties.load(in);
            String apiKey = properties.getProperty("discord.apikey");
	    jda = JDABuilder.createDefault(apiKey).addEventListeners(new MusicPlayerListenerAdapter()).build();
	} catch (IOException ex) {
	    LOG.log(Level.SEVERE, "There was an error reading " + PROPERTIES_FILENAME + "/n: {0}",ex.getMessage());
	} catch (LoginException ex) {
	    LOG.log(Level.SEVERE, "Failed to Build JDA/n: {0}", ex.getMessage());
	}
	ServletContextListener.super.contextInitialized(sce);
    }

}
