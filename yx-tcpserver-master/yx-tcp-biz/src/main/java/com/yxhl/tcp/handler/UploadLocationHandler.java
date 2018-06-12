package com.yxhl.tcp.handler;

import com.google.common.collect.Collections2;
import com.yxhl.domain.*;
import com.yxhl.enums.ResultCodeEnum;
import com.yxhl.persistence.mapper.BizOrderMapper;
import com.yxhl.persistence.mapper.DriverMapper;
import com.yxhl.persistence.mapper.TaskMapper;
import com.yxhl.persistence.mapper.dao.ConstantDao;
import com.yxhl.protobuf.*;
import com.yxhl.tcp.manager.BizOrderManager;
import com.yxhl.tcp.manager.TaskLocationManager;
import com.yxhl.tcp.manager.TaskManager;
import com.yxhl.util.CollectionUtil;
import com.yxhl.util.LogUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.SocketChannel;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.yxhl.enums.ResultCodeEnum.CHECK_NULL_ERROR;
import static com.yxhl.enums.ResultCodeEnum.REQUEST_ERROR;

/**
 * Created by alan on 16/4/20.
 * 处理地理位置
 */
@ChannelHandler.Sharable
@Service("uploadLocationHandler")
public class UploadLocationHandler extends BaseHandler {

    private static final Logger logger = LoggerFactory.getLogger(UploadLocationHandler.class);

    @Autowired
    private TaskLocationManager taskLocationManagerTcp;

    @Autowired
    private ConstantDao constantRedisDao;

    @Autowired
    private BizOrderMapper bizOrderMapper;

    @Autowired
    private TaskMapper taskMapper;

    @Autowired
    private BizOrderManager bizOrderManagerTcp;

    @Autowired
    private TaskManager taskManagerTcp;

    @Autowired
    private DriverMapper driverMapper;

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (filter(msg)) {
                //logger.info("start handling upload Location biz..." + msg.toString());

                TaskRequest taskRequest = ((TcpRequest) msg).getTask();
                checkNotNull(taskRequest, ResultCodeEnum.REQUEST_ERROR.getType());
                long driverId = taskRequest.getDriverId(); //以前为taskId上传,目前根据driverId上传(专车)
                long taskId = taskRequest.getTaskId(); //兼容以前逻辑

                // 校验
                checkArgument((driverId != 0 || taskId != 0)
                                && StringUtils.isNoneBlank(taskRequest.getLng(), taskRequest.getLat()),
                        ResultCodeEnum.CHECK_NULL_ERROR.getType());

                if (0 != driverId) { //driverId不为空,记录司机经纬度
                    DriverLocationDO driverLocationDO = new DriverLocationDO(taskRequest);
                    taskLocationManagerTcp.insertLocation(driverLocationDO);
                    if (0 != taskId) { //taskId不为空,并且记录任务经纬度
                        TaskLocationDO taskLocationDO = new TaskLocationDO(taskRequest);
                        taskLocationManagerTcp.insertLocation(taskLocationDO);
                    }
                } else { //默认场景
                    TaskLocationDO taskLocationDO = new TaskLocationDO(taskRequest);
                    taskLocationManagerTcp.insertLocation(taskLocationDO);
                }


                // 如果redis中存有任务,现在只有用户取消订单的时候加入进去,会让服务端发送一次任务信息
                if(taskId == 0l){
                    TaskDO tcpTask = constantRedisDao.queryTcpTask(ClientType.DRIVER.name()+driverId);
                    if(tcpTask !=null){
                        taskId = tcpTask.getId();
                        taskRequest=TaskRequest.newBuilder()
                                .setTaskTypeValue(tcpTask.getTaskType())
                                .setTaskId(tcpTask.getId()).build();
                        constantRedisDao.deleteTcpTask(ClientType.DRIVER.name()+driverId);
                    }
                }
                //如果连接时没有任务,只是上传位置,就直接返回成功
                if(taskId == 0l){
                    DriverDO driverDO=driverMapper.queryDriverById(driverId);
                    //如果司机id错误报错
                    checkNotNull(driverDO, REQUEST_ERROR.getType());

                    //根据手机号查司机的正在执行的任务237
                    List<TaskDO> list=taskMapper.queryTaskByMobile(driverDO.getMobile());
//                    checkArgument(CollectionUtil.isNotEmpty(list), ResultCodeEnum.TASK_NOT_ACTIVE.getType()); //司机没有任务

                   // checkArgument(list.size() >0, ResultCodeEnum.TASK_NOT_ACTIVE.getType());
                    if (list.size() > 0) {
                        //如果司机多个任务报错,现在没有多任务错误先显示不存在
                        checkArgument(list.size() == 1, ResultCodeEnum.TASK_NOT_EXIST.getType()); //如果司机正在执行的任务有多个
                        TaskDO taskDO=list.get(0);

                        //加入redis的样例
                        /*constantRedisDao.insertTcpTask(taskDO);
                        TaskDO aa=constantRedisDao.queryTcpTask(taskDO.getDriverMobile());*/

                        //重新构建taskRequest,赋值
                        taskRequest=TaskRequest.newBuilder().setDriverId(driverId)
                                .setTaskTypeValue(taskDO.getTaskType())
                                .setTaskId(taskDO.getId()).build();
                    } else {
                        //如果没有任务,查看一下情况,如果redis中
                        //TcpResponse.Builder tcpResponseBuilder2 = genNormalResp(msg);
                        TcpResponse.Builder tcpResponseBuilder2 = TcpResponse.newBuilder();
                        ctx.writeAndFlush(tcpResponseBuilder2.setIsSucc(false)
                                .setServiceType(ServiceType.DRIVER_QUERY_TASK)
                                .setResultCode(ResultCodeEnum.TASK_NOT_ACTIVE.getType())
                                .build());
                        return;
                    }

                }

                TaskResponse.Builder taskResponseBuilder = TaskResponse.newBuilder();

                // 处理对应逻辑,注意大巴和接驳车\快车是有差异的,需要分别处理
                taskManagerTcp.getTaskDetail(taskResponseBuilder, taskRequest);

                TcpResponse.Builder tcpResponseBuilder1 = genNormalResp(msg);
                tcpResponseBuilder1.setTaskResp(taskResponseBuilder)
                        .setServiceType(ServiceType.DRIVER_QUERY_TASK)
                        .setIsSucc(true);
                ctx.writeAndFlush(tcpResponseBuilder1.build());


                // 默认为成功,如果失败,则直接返回错误
//                TcpResponse.Builder tcpRespBuilder = genNormalResp(msg);
//                logger.info("response:" + tcpRespBuilder.build());
//                ctx.writeAndFlush(tcpRespBuilder.build());
            } else {
                logger.debug("skip driver upload location request,msg=" + msg.toString());
                ctx.fireChannelRead(msg);
            }
        } catch (Exception e) {
            logger.error("upload driver location error,msg=" + msg + ",error:", e);
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

    public boolean filter(Object msg) {
        if (!(msg instanceof TcpRequest)) {
            return false;
        }

        if (ServiceType.DRIVER_UPLOAD_LOC.equals(((TcpRequest) msg).getServiceType())) {
            return true;
        }

        return false;

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) throws Exception {
        ctx.flush();
    }
}
