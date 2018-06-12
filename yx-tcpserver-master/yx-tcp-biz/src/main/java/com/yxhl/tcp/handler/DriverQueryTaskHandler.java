package com.yxhl.tcp.handler;

import com.yxhl.domain.ConfigDO;
import com.yxhl.enums.ResultCodeEnum;
import com.yxhl.persistence.mapper.dao.ConstantDao;
import com.yxhl.protobuf.*;
import com.yxhl.tcp.manager.TaskManager;
import com.yxhl.util.LogUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.yxhl.enums.ResultCodeEnum.CHECK_NULL_ERROR;

/**
 * Created by alan on 16/4/13.
 * 处理司机上送位置信息 及 查询任务的请求
 */
@ChannelHandler.Sharable
@Service("driverQueryTaskHandler")
public class DriverQueryTaskHandler extends BaseHandler {

    private final static Logger logger = LoggerFactory.getLogger(DriverQueryTaskHandler.class);

    @Autowired
    private TaskManager taskManagerTcp;

    @Autowired
    private ConstantDao constantRedisDao;

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (filter(msg)) {
                logger.info("start handling driverQueryTaskAndUploadLoc biz" + msg.toString());
                TaskRequest taskRequest = ((TcpRequest) msg).getTask();
                TaskResponse.Builder taskResponseBuilder = TaskResponse.newBuilder();

                // 参数校验
                checkNotNull(taskRequest.getTaskId(), CHECK_NULL_ERROR.getType());

                //打点
                LogUtil.setBizId(taskRequest.getDriverId() + "");

                // 处理对应逻辑,注意大巴和接驳车\快车是有差异的,需要分别处理
                taskManagerTcp.getTaskDetail(taskResponseBuilder, taskRequest);

                TcpResponse.Builder tcpResponseBuilder = genNormalResp(msg);
                tcpResponseBuilder.setTaskResp(taskResponseBuilder)
                        .setServiceType(ServiceType.DRIVER_QUERY_TASK)
                        .setIsSucc(true);

                logger.info("response:" + tcpResponseBuilder.build());
                ctx.writeAndFlush(tcpResponseBuilder.build());
            } else {
                logger.debug("skip DriverQueryTask handler,msg=" + msg.toString());
                ctx.fireChannelRead(msg);
            }
        } catch (Exception e) {
            logger.error("driver query task error,msg=" + msg + ",error:", e);

            ConfigDO configDO = constantRedisDao.queryConfigByKey(e.getMessage());
            if (null == configDO) {
                configDO = constantRedisDao.queryConfigByKey(ResultCodeEnum.UNKNOWN_ERROR.getType());
            }
            ctx.writeAndFlush(genErrResp(configDO, msg));
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("channel " + ctx.channel().remoteAddress() + " exception: " + cause.getMessage());
//        ctx.close();
    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }

    @Override
    public boolean filter(Object msg) {
        if (!(msg instanceof TcpRequest)) {
            return false;
        }

        if (ServiceType.DRIVER_QUERY_TASK.equals(((TcpRequest) msg).getServiceType())) {
            return true;
        }

        return false;
    }

}
