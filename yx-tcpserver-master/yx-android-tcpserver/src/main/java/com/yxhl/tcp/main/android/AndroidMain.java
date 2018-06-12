package com.yxhl.tcp.main.android;

import com.google.common.base.Preconditions;
import com.yxhl.tcp.constants.TcpServerConstants;
import com.yxhl.tcp.server.YxBizServer;
import org.springframework.context.ApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by alan on 16/4/13.
 * 整个服务入口
 */
public class AndroidMain {

    /**
     * 服务启动参数
     */
    private final static String PARAM_START = "start";

    /**
     * 服务关闭参数
     */
    private final static String PARAM_STOP = "stop";

    /**
     * 服务重启参数
     */
    private final static String PARAM_RESTART = "restart";

    public static void main(String[] args) throws Exception {
        String param;

        // 用于判断是否有参数
        if (args.length == 0) {
            param = PARAM_START;
        } else {
            param = args[0];
        }

        AndroidMain myServer = new AndroidMain();

        // 获取spring上下文
        ApplicationContext appContext =
                new ClassPathXmlApplicationContext(new String[]{"classpath:spring-android.xml"});

        YxBizServer yxBizServer = (YxBizServer) appContext.getBean("yxBizServer");
        Preconditions.checkNotNull(yxBizServer, "netty business server error!!! yxBizServer is null");

        yxBizServer.setPort(TcpServerConstants.ANDORID_PORT);

        switch (param) {
            // 启动服务
            case PARAM_START:
                myServer.start(yxBizServer);
                break;

            // 停止服务
            case PARAM_STOP:
                myServer.stop(yxBizServer);
                break;

            // 重启服务
            case PARAM_RESTART:
                myServer.restart(yxBizServer);
                break;

            // 参数错误...
            default:
                return;
        }
    }

    /**
     * 初始化并启动服务
     *
     * @param yxBizServer
     */
    private void start(YxBizServer yxBizServer) throws Exception {
        yxBizServer.run();
    }

    /**
     * 停掉服务
     *
     * @param yxBizServer
     */
    private void stop(YxBizServer yxBizServer) {
        yxBizServer.stop();
    }

    /**
     * 重启服务
     *
     * @param yxBizServer
     */
    private void restart(YxBizServer yxBizServer) throws Exception {
        stop(yxBizServer);
        start(yxBizServer);
    }


}
