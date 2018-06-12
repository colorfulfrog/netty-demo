package com.yxhl.tcp.handler;

import com.yxhl.domain.TaskDO;
import com.yxhl.enums.TaskStatusEnum;
import com.yxhl.persistence.mapper.TaskMapper;
import com.yxhl.persistence.mapper.dao.ConstantDao;
import com.yxhl.protobuf.*;
import com.yxhl.tcp.manager.TaskManager;
import com.yxhl.tcp.server.YxBizServer;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static com.yxhl.constants.ConstantsKey.YXConstants.STOP_SERVICE_TIME_FORMAT;

/**
 * Created by alan on 16/4/26.
 * 心跳处理.注意这里不是TcpResponse 和 TcpRequest的类型,而是不需要客户端发送数据,返回channelInfo给客户端
 */
@ChannelHandler.Sharable
@Service("idleHandler")
public class IdleHandler extends BaseHandler {

    private final static Logger logger = LoggerFactory.getLogger(IdleHandler.class);

    @Autowired
    private ConstantDao constantRedisDao;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private TaskManager taskManagerTcp;

    //业务线程处理数据库业务
    private ExecutorService threadPool = Executors.newCachedThreadPool();


    //todo现在先对客户端心跳不进行处理
    /*@Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try{
            //如果是心跳就拦截
            TcpRequest request = (TcpRequest) msg;
            // 判断serviceTyp是否为空
            ServiceType serviceType = request.getServiceType();
            checkNotNull(serviceType, "SERVICE_TYPE_IS_NULL");
            // ping得话不予处理,如果不是继续分流业务
            if (ServiceType.PING.equals(request.getServiceType())) {
                logger.info("this is a ping request message,now do nothing!!!");
                //TODO 利用客户端的心跳来检测
                //如果是司机端的连接,就检测有没有新的任务
                //此处使用业务线程来做,防止通道阻塞
                ClientType clientType = request.getClientType();
                checkNotNull(clientType, "CLIENT_TYPE_IS_NULL");
                if (clientType == ClientType.DRIVER) {
                    threadPool.submit(new MyTask(ctx,msg));
                }
            }else {
                ctx.fireChannelRead(msg);
            }

        }catch (Exception e){
            logger.error("IdleHandler here get error , the cause:", e);
            ConfigDO configDO = constantRedisDao.queryConfigByKey(e.getMessage());
            if (null == configDO) {
                configDO = constantRedisDao.queryConfigByKey(ResultCodeEnum.BIND_TYPE_NULL.getType());
            }
            // 返回结果
            ctx.writeAndFlush(genErrResp(configDO, msg));
        }
    }*/

    @Override
    public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
        try {
            if (filter(evt)) {
                IdleStateEvent e = (IdleStateEvent) evt;
                Ping.Builder pingBuilder = Ping.newBuilder();

                TcpResponse.Builder responseBuilder = TcpResponse.newBuilder();

                pingBuilder.setChannelId(ctx.channel().id().toString())
                        .setCurTime(DateFormatUtils.format(Calendar.getInstance(),
                                constantRedisDao.getConfigByKey(STOP_SERVICE_TIME_FORMAT)))
                        .setLocalAddr(ctx.channel().localAddress().toString());

                // 读超时,超过秒数 直接关闭当前连接
                if (e.state() == IdleState.READER_IDLE) {
                    logger.info("close connection " + ctx.channel().remoteAddress() + " with 250 seconds,and now still have connections:" + YxBizServer.allChannels.size());
                    ctx.close();
                    return;
                }
                // 写超时
                else if (e.state() == IdleState.WRITER_IDLE) {
                    pingBuilder.setIdleType(IdleType.WRITER_IDLE);
                }
                // all超时
                else if (e.state() == IdleState.ALL_IDLE) {
                    pingBuilder.setIdleType(IdleType.ALL_IDLE);
                }
                // 如果发送失败,则关闭.
                ctx.writeAndFlush(responseBuilder.setIsSucc(true)
                        .setServiceType(ServiceType.PING)
                        .setPing(pingBuilder.build()).build())
                        .addListener(ChannelFutureListener.CLOSE_ON_FAILURE);
            } else {
                logger.debug("skip not idle info");
                super.userEventTriggered(ctx, evt);
            }
        } catch (Exception e) {
            logger.error("handling idle info error,", e);
            super.userEventTriggered(ctx, evt);
        }
    }

    @Override
    public boolean filter(Object msg) {
        return msg instanceof IdleStateEvent;
    }

    class MyTask implements Runnable{
        Object msg;
        ChannelHandlerContext ctx;

        public MyTask( ChannelHandlerContext ctx,Object msg) {
            this.msg = msg;
            this.ctx = ctx;
        }

        @Override
        public void run() {

            try {
                TcpRequest request = (TcpRequest) msg;
                TaskRequest taskRequest=  request.getTask();
                if (taskRequest != null && taskRequest.getDriverId()!= 0L){
                    //查询任务列表,查询任务状态是调度的和进行中的任务
                    List<TaskDO> list = taskMapper.queryTasks(taskRequest.getDriverId(), TaskStatusEnum.DISPATCH.getType(), TaskStatusEnum.TAKE_ORDER.getType());
                    if (list.size() > 0) {


                        TaskDO taskDO=list.get(0);



                        TaskResponse.Builder taskResponseBuilder = TaskResponse.newBuilder();

                   /*     // 参数校验
                        checkNotNull(taskRequest.getTaskId(), CHECK_NULL_ERROR.getType());

                        //打点
                        LogUtil.setBizId(taskRequest.getDriverId() + "");
*/
                        //设置任务类型
                        //taskResponseBuilder.setTaskType(TaskType.valueOf(taskDO.getTaskType()));
                        taskResponseBuilder.setTaskTypeValue(taskDO.getTaskType());
                        //放入一个任务

                        Task.Builder task= Task.newBuilder();
                        taskResponseBuilder.addTaskFields(task.setId(taskDO.getId())
                                .setDriverId(taskDO.getDriverId())
                                .setDriverMobile(taskDO.getDriverMobile())
                                .setDriverName(taskDO.getDriverName())
                                        //.setDescription(taskDO.getDescription())
                                .setTitle(taskDO.getTitle()).build());

                        // 处理对应逻辑,注意大巴和接驳车\快车是有差异的,需要分别处理
                        //taskManagerTcp.getTaskDetail(taskResponseBuilder, taskRequest);

                        TcpResponse.Builder tcpResponseBuilder = genNormalResp(msg);
                        tcpResponseBuilder.setTaskResp(taskResponseBuilder.build())
                                .setServiceType(ServiceType.DRIVER_QUERY_TASK)
                                .setIsSucc(true);

                        logger.info("response:" + tcpResponseBuilder.build());
                        ctx.writeAndFlush(tcpResponseBuilder.build());
                    }

                }else{
                    logger.info("driverId is null");

                }
            }catch (Exception e){
                e.printStackTrace();
            }
        }
    }

}
