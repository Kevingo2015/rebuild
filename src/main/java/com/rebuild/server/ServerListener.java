/*
Copyright (c) REBUILD <https://getrebuild.com/> and its owners. All rights reserved.

rebuild is dual-licensed under commercial and open source licenses (GPLv3).
See LICENSE and COMMERCIAL in the project root for license information.
*/

package com.rebuild.server;

import cn.devezhao.commons.CalendarUtils;
import cn.devezhao.commons.SystemUtils;
import com.rebuild.server.helper.AesPreferencesConfigurer;
import com.rebuild.server.helper.ConfigurableItem;
import com.rebuild.server.helper.SysConfiguration;
import com.rebuild.server.helper.setup.InstallState;
import com.rebuild.server.helper.task.TaskExecutors;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.web.context.ContextCleanupListener;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import java.util.Date;

/**
 * 服务启动/停止监听
 * 
 * @author devezhao
 * @since 10/13/2018
 */
public class ServerListener extends ContextCleanupListener implements InstallState {

	private static final Log LOG = LogFactory.getLog(ServerListener.class);

	private static String CONTEXT_PATH = "";
	private static Date STARTUP_TIME = CalendarUtils.now();

	private static ServletContextEvent eventHold;

	@Override
	public void contextInitialized(ServletContextEvent event) {
	    if (event == null) {
            event = eventHold;
        }
	    if (event == null) {
            throw new IllegalStateException();
        }

		final long at = System.currentTimeMillis();
		LOG.info("Rebuild Booting (" + Application.VER + ") ...");

        CONTEXT_PATH = event.getServletContext().getContextPath();
        LOG.debug("Detecting Rebuild context-path '" + CONTEXT_PATH + "'");
        event.getServletContext().setAttribute("baseUrl", CONTEXT_PATH);

		try {
			AesPreferencesConfigurer.initApplicationProperties();

            if (!checkInstalled()) {
                eventHold = event;

				int serverPort = SystemUtils.getRuntimeInformation().getServletPort();
				if (serverPort < 1) serverPort = 18080;
                LOG.warn(Application.formatBootMsg("REBUILD IS WAITING FOR INSTALL ...", null, "Install URL : http://localhost:" + serverPort + "/"));
                return;
            }

            LOG.info("Initializing Spring context ...");
            ApplicationContext ctx = new ClassPathXmlApplicationContext(new String[] { "application-ctx.xml" });
            new Application(ctx).init(at);
			STARTUP_TIME = CalendarUtils.now();

			updateGlobalContextAttributes(event.getServletContext());

            eventHold = null;

		} catch (Throwable ex) {
            LOG.fatal(Application.formatBootMsg("REBUILD BOOTING FAILURE!!!"), ex);
		}
	}

	@Override
	public void contextDestroyed(ServletContextEvent event) {
		LOG.info("Rebuild shutdown ...");
		Application.getBean(TaskExecutors.class).shutdown();
        ((ClassPathXmlApplicationContext) Application.getApplicationContext()).close();
		super.contextDestroyed(event);
	}
	
	// --

    /**
     * 更新全局上下文属性
     *
     * @param context
     */
    public static void updateGlobalContextAttributes(ServletContext context) {
		context.setAttribute("env", Application.devMode() ? "dev" : "production");
        context.setAttribute("appName", SysConfiguration.get(ConfigurableItem.AppName));
        context.setAttribute("storageUrl", StringUtils.defaultIfEmpty(SysConfiguration.getStorageUrl(), StringUtils.EMPTY));
        context.setAttribute("fileSharable", SysConfiguration.getBool(ConfigurableItem.FileSharable));
        context.setAttribute("markWatermark", SysConfiguration.getBool(ConfigurableItem.MarkWatermark));
    }

	/**
	 * 获取 WEB 相对路径
     *
	 * @return
	 */
	public static String getContextPath() {
		return CONTEXT_PATH;
	}
	
	/**
	 * 获取启动时间
     *
	 * @return
	 */
	public static Date getStartupTime() {
		return STARTUP_TIME;
	}
}
