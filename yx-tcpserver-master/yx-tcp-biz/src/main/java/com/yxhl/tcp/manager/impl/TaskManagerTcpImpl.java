package com.yxhl.tcp.manager.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Collections2;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import com.yxhl.amap.DistanceBO;
import com.yxhl.amap.DistanceResult;
import com.yxhl.constants.ConstantsKey;
import com.yxhl.constants.NonConfigYxConstants;
import com.yxhl.domain.*;
import com.yxhl.enums.*;
import com.yxhl.fields.StationField;
import com.yxhl.persistence.mapper.BizOrderMapper;
import com.yxhl.persistence.mapper.StationMapper;
import com.yxhl.persistence.mapper.TaskMapper;
import com.yxhl.persistence.mapper.dao.ConstantDao;
import com.yxhl.protobuf.*;
import com.yxhl.tcp.manager.TaskManager;
import com.yxhl.util.CollectionUtil;
import com.yxhl.util.HttpUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.http.message.BasicNameValuePair;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.*;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by alan on 16/4/18.
 */
@Service("taskManagerTcp")
public class TaskManagerTcpImpl implements TaskManager {
    @Autowired
    private TaskMapper taskMapper;
    @Autowired
    private BizOrderMapper bizOrderMapper;
    @Autowired
    private StationMapper stationMapper;
    @Autowired
    private ConstantDao constantRedisDao;

    @Transactional(rollbackFor = Exception.class,readOnly = true)
    @Override
    public void getTaskDetail(TaskResponse.Builder taskResponseBuilder, TaskRequest taskRequest) throws IOException {
        //查出任务信息
        TaskDO taskDO = taskMapper.findTaskById(taskRequest.getTaskId());
        //任务不存在
        checkNotNull(taskDO, ResultCodeEnum.TASK_NOT_EXIST.getType());
        //取车的位置,放入返回
        TaskLocationDO taskLocationDO = constantRedisDao.queryLatestLocation(taskDO.getId());
        if (null != taskLocationDO) { //根据任务信息上传的经纬度
            taskResponseBuilder.addLnglat(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_CAR))
                    .setLng(taskLocationDO.getLng())
                    .setLat(taskLocationDO.getLat())
                    .setHeading(taskLocationDO.getHeading()));
        } else { //根据司机信息上传经纬度修改
            if (taskRequest.getDriverId() != 0l) {
                DriverLocationDO driverLocationDO = constantRedisDao.queryLatestDriverLocation(taskRequest.getDriverId());
                if (driverLocationDO != null) {
                    taskResponseBuilder.addLnglat(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_CAR))
                            .setLng(driverLocationDO.getLng())
                            .setLat(driverLocationDO.getLat())
                            .setHeading(driverLocationDO.getHeading()));
                }
            }
        }
        //如果是接驳车任务
        if (taskRequest.getTaskTypeValue() == TaskType.FERRY_VALUE) {
            getCarDetail(taskResponseBuilder, taskDO);
            return;
        }
        //如果是定点定制bus任务
        if (taskRequest.getTaskTypeValue() == TaskType.DIY_BUS_VALUE || taskRequest.getTaskTypeValue() == TaskType.DIY_FIXED_BUS_VALUE) {
            getDiyBusDetailV2(taskResponseBuilder, taskDO, taskRequest);
            return;
        }
        //如果是快车任务
        if (TaskTypeEnum.isIn(taskRequest.getTaskType().getNumber(), TaskTypeEnum.SHARE, TaskTypeEnum.CHARTER)) {
            getQuickCarDetail(taskResponseBuilder, taskDO);
            return;
        }
        //如果是公务车/专车任务
        if(TaskTypeEnum.isIn(taskRequest.getTaskType().getNumber(), TaskTypeEnum.OFFICIAL_BUS_TIME, TaskTypeEnum.OFFICIAL_BUS_WAY,
                TaskTypeEnum.SPECIAL_BUS_TIME, TaskTypeEnum.SPECIAL_BUS_WAY)){
            getOfficialAndSpecialCarDetail(taskResponseBuilder, taskDO);
            return;
        }

    }

    /**
     * 暂未使用
     * @param taskResponseBuilder
     * @param taskDO
     * @throws IOException
     */
    @Transactional(rollbackFor = Exception.class,readOnly = true)
    @Override
    public void getTaskDetail2(TaskResponse.Builder taskResponseBuilder, TaskDO taskDO) throws IOException {
        //查出任务信息
        //TaskDO taskDO = taskMapper.findTaskById(taskRequest.getTaskId());
        //任务不存在
        checkNotNull(taskDO, ResultCodeEnum.TASK_NOT_EXIST.getType());
        checkArgument(taskDO.isActive(), ResultCodeEnum.TASK_NOT_ACTIVE.getType());
        //取车的位置,放入返回
        TaskLocationDO taskLocationDO = constantRedisDao.queryLatestLocation(taskDO.getId());
        if (null != taskLocationDO) {
            taskResponseBuilder.addLnglat(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_CAR))
                    .setLng(taskLocationDO.getLng())
                    .setLat(taskLocationDO.getLat())
                    .setHeading(taskLocationDO.getHeading()));
        } else { //根据司机信息上传经纬度修改
            if (taskDO != null && taskDO.getDriverId() != 0l) { //根据司机信息上传经纬度修改
                DriverLocationDO driverLocationDO = constantRedisDao.queryLatestDriverLocation(taskDO.getDriverId());
                if (driverLocationDO != null) {
                    taskResponseBuilder.addLnglat(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_CAR))
                            .setLng(driverLocationDO.getLng())
                            .setLat(driverLocationDO.getLat())
                            .setHeading(driverLocationDO.getHeading()));
                }
            }
        }
        //如果是接驳车任务
        if (taskDO.getTaskType() == TaskType.FERRY_VALUE) {
            getCarDetail(taskResponseBuilder, taskDO);
            return;
        }
        //todo 如果是定点定制bus任务
       /* if (taskRequest.getTaskTypeValue() == TaskType.DIY_BUS_VALUE || taskRequest.getTaskTypeValue() == TaskType.DIY_FIXED_BUS_VALUE) {
            getDiyBusDetailV2(taskResponseBuilder, taskDO, taskRequest);
            return;
        }*/
        //如果是快车任务
        if (TaskTypeEnum.isIn(taskDO.getTaskType() , TaskTypeEnum.SHARE, TaskTypeEnum.CHARTER)) {
            getQuickCarDetail(taskResponseBuilder, taskDO);
            return;
        }
        //如果是公务车/专车任务
        if(TaskTypeEnum.isIn(taskDO.getTaskType(), TaskTypeEnum.OFFICIAL_BUS_TIME, TaskTypeEnum.OFFICIAL_BUS_WAY,
                TaskTypeEnum.SPECIAL_BUS_TIME, TaskTypeEnum.SPECIAL_BUS_WAY)){
            getOfficialAndSpecialCarDetail(taskResponseBuilder, taskDO);
            return;
        }

    }





    /**
     * 接驳车任务详情
     *
     * @param taskResponseBuilder
     * @param taskDO
     */
    private void getCarDetail(TaskResponse.Builder taskResponseBuilder, TaskDO taskDO) {
        //返回任务类型
        taskResponseBuilder.setTaskTypeValue(taskDO.getTaskType());
        //已上车人数
        int people = 0;
        List<BizOrderDO> list = bizOrderMapper.queryByTaskAndRide(taskDO.getId(), new Integer[]{OrderRideStatusEnum.TAKEN.getStatus()});
        if (CollectionUtil.isNotEmpty(list)) {
            for (BizOrderDO bizOrderDO : list) {
                people += bizOrderDO.getAmount();
            }
        }
        //取到目的地信息
        StationDO stationDO = stationMapper.queryStationByName(taskDO.getEndStation(), VisibleTypeEnum.VISIBLE.getType());
        SimpleDateFormat sfm = new SimpleDateFormat(NonConfigYxConstants.DATE_NO_SS);
        //返回任务相关的信息
        if(stationDO!= null){
            taskResponseBuilder.addTaskFields(Task.newBuilder().setId(taskDO.getId())
                    .setGmtDepart(sfm.format(taskDO.getGmtDepart()))
                    .setPeople(taskDO.getPeople())
                    .setEndStation(taskDO.getEndStation())
                    .setInPeople(people)
                    .setElng(stationDO.getLongitude())
                    .setElat(stationDO.getLatitude()));

        }else {
            taskResponseBuilder.addTaskFields(Task.newBuilder().setId(taskDO.getId())
                    .setGmtDepart(sfm.format(taskDO.getGmtDepart()))
                    .setPeople(taskDO.getPeople())
                    .setEndStation(taskDO.getEndStation())
                    .setInPeople(people));

        }
        //根据任务查出来没有上车的乘客
        List<BizOrderDO> bizOrderDOList = bizOrderMapper.queryByTaskAndRide(taskDO.getId(), new Integer[]{OrderRideStatusEnum.UN_TAKEN.getStatus(),OrderRideStatusEnum.TAKEN.getStatus()});
        SimpleDateFormat sf = new SimpleDateFormat(NonConfigYxConstants.DATE_HM);
        //如有有未上车的乘客
        if (CollectionUtil.isNotEmpty(bizOrderDOList)) {
            String lng = "";
            String lat = "";
            //放入每个订单的相关信息
            for (BizOrderDO bizOrderDO : bizOrderDOList) {
                //取到订单经度和纬度
                lng = bizOrderDO.getAttribute(constantRedisDao.getConfigByKey(ConstantsKey.AttributeKeyConstants.LNG_LAT)).split(NonConfigYxConstants.SEP_COMMA)[0];
                lat = bizOrderDO.getAttribute(constantRedisDao.getConfigByKey(ConstantsKey.AttributeKeyConstants.LNG_LAT)).split(NonConfigYxConstants.SEP_COMMA)[1];
                taskResponseBuilder.addDriverOrdersFields(DriverOrders.newBuilder()
                        .setAmount(bizOrderDO.getAmount())
                        .setMobile(bizOrderDO.getMobile())
                        .setStart(bizOrderDO.getStart())
                        .setRideStatusValue(bizOrderDO.getRideStatus())
                        .setGmtDepartTime(sf.format(bizOrderDO.getGmtDepart()))
                        .setLng(lng)
                        .setLat(lat)
                        .setOrderId(bizOrderDO.getId()));
            }
        }
    }

    /**
     * 快车任务司机详情
     *
     * @param taskResponseBuilder
     * @param taskDO
     */
    private void getQuickCarDetail(TaskResponse.Builder taskResponseBuilder, TaskDO taskDO) {
        //返回任务类型
        taskResponseBuilder.setTaskTypeValue(taskDO.getTaskType());
        //取到任务下的订单,包括已上车的订单和未上车的
        List<BizOrderDO> list = bizOrderMapper.queryTaskDetail(taskDO.getId(), taskDO.getDriverId(),
                new Integer[] {
                        OrderPayStatusEnum.PAID.getStatus(),
                        OrderPayStatusEnum.CREATED.getStatus(),
                        OrderPayStatusEnum.OPS_SEND_BILL.getStatus()
                });
        checkArgument(CollectionUtil.isNotEmpty(list), ResultCodeEnum.TASK_NOT_ACTIVE.getType()); //未接到乘客,任务即将被取消
        //取出上车人数
        int people = 0;
        for (BizOrderDO bizOrderDO : list) {
            if (OrderRideStatusEnum.isIn(bizOrderDO.getRideStatus(), OrderRideStatusEnum.TAKEN,
                    OrderRideStatusEnum.PART_ARRIVED, OrderRideStatusEnum.ARRIVED)) {
                people += bizOrderDO.getAmount();
            }
        }
        final SimpleDateFormat sf = new SimpleDateFormat(NonConfigYxConstants.DATE_HM);
        Collection<DriverOrders> driverOrderses = Collections2.transform(list, new Function<BizOrderDO, DriverOrders>() {
            @Override
            public DriverOrders apply(BizOrderDO bizOrderDO) {
                DriverOrders.Builder orderBuider = DriverOrders.newBuilder();
                //放入乘客出发地和目的地经纬度
                String lnglat = bizOrderDO.getAttribute(constantRedisDao.getConfigByKey(ConstantsKey.AttributeKeyConstants.LNG_LAT));
                String elnglat = bizOrderDO.getAttribute(constantRedisDao.getConfigByKey(ConstantsKey.AttributeKeyConstants.ELNG_LAT));
                if (StringUtils.isNoneBlank(lnglat)) {
                    orderBuider.setLng(lnglat.split(",")[0])
                            .setLat(lnglat.split(",")[1]);
                }
                if (StringUtils.isNoneBlank(elnglat)) {
                    orderBuider.setElng(elnglat.split(",")[0])
                            .setElat(elnglat.split(",")[1]);
                }
                //放入乘客的相关信息
                orderBuider.setMobile(bizOrderDO.getMobile())
                        .setAmount(bizOrderDO.getAmount())
                        .setGmtDepartTime(sf.format(bizOrderDO.getGmtDepart()))
                        .setStart(bizOrderDO.getStart())
                        .setDestination(bizOrderDO.getDestination())
                        .setRideStatusValue(bizOrderDO.getRideStatus())
                        .setPayStatusValue(bizOrderDO.getPayStatus())
                        .setOrderId(bizOrderDO.getId());
                return orderBuider.build();
            }
        });
        //放入乘客信息
        taskResponseBuilder.addAllDriverOrdersFields(driverOrderses);
        //放入任务信息
        SimpleDateFormat sfm = new SimpleDateFormat(NonConfigYxConstants.DATE_NO_SS);
        taskResponseBuilder.addTaskFields(Task.newBuilder()
                .setGmtDepart(sfm.format(taskDO.getGmtDepart()))
                .setId(taskDO.getId())
                .setInPeople(people)
                .setVehicleSeats(taskDO.getVehicleSeats())
                .setTitle(taskDO.getTitle())
                .setStatus(taskDO.getStatus())
                .setPeople(taskDO.getPeople())
                .setDriverId(taskDO.getDriverId())
                .setTaskTypeValue(taskDO.getTaskType()));
    }

    /**
     * 司机任务定制bus详情
     *
     * @param taskResponseBuilder
     * @param taskDO
     */
    private void getDiyBusDetail(TaskResponse.Builder taskResponseBuilder, final TaskDO taskDO, TaskRequest taskRequest) {
        //返回任务类型
        taskResponseBuilder.setTaskTypeValue(taskDO.getTaskType());
        //如果是首次请求,放入途经站点的所有信息
        if (taskRequest.getIsFirst()) {
            //给据线路查出所有的站点
            List<StationField> stationFields = stationMapper.queryByLine(taskDO.getLineId());
            //校验站点
            checkArgument(CollectionUtil.isNotEmpty(stationFields), ResultCodeEnum.NO_STATION.getType());
            //放入站点信息
            Collection<StationEntry> stations = Collections2.transform(stationFields, new Function<StationField, StationEntry>() {
                @Override
                public StationEntry apply(StationField stationField) {
                    StationEntry.Builder stationBuilder = StationEntry.newBuilder();
                    //取到站点的信息
                    getStationMessage(stationBuilder, stationField, taskDO);
                    return stationBuilder.build();
                }
            });
            taskResponseBuilder.addAllStations(stations);
        }
        //放入任务信息
        taskResponseBuilder.addTaskFields(Task.newBuilder()
                .setId(taskDO.getId())
                .setTitle(taskDO.getTitle())
                .setStatus(taskDO.getStatus())
                .setPeople(taskDO.getPeople())
                .setDriverId(taskDO.getDriverId())
                .setTaskTypeValue(taskDO.getTaskType()));

    }

    /**
     * 后端计算到哪个站点
     *
     * @param taskResponseBuilder
     * @param taskDO
     * @param taskRequest
     */
    private void getDiyBusDetailV2(TaskResponse.Builder taskResponseBuilder, final TaskDO taskDO, TaskRequest taskRequest) throws IOException {
        //返回任务类型
        taskResponseBuilder.setTaskTypeValue(taskDO.getTaskType());
        //给据线路查出所有的站点
        List<StationField> stationFields = stationMapper.queryByLine(taskDO.getLineId());
        //校验站点
        checkArgument(CollectionUtil.isNotEmpty(stationFields), ResultCodeEnum.NO_STATION.getType());
        //放入站点信息
        Collection<StationEntry> stations = Collections2.transform(stationFields, new Function<StationField, StationEntry>() {
            @Override
            public StationEntry apply(StationField stationField) {
                StationEntry.Builder stationBuilder = StationEntry.newBuilder();
                //取到站点的信息
                getStationMessage(stationBuilder, stationField, taskDO);
                return stationBuilder.build();
            }
        });
        taskResponseBuilder.addAllStations(stations);
        //计算出最近的站点,给出序号
        taskResponseBuilder.setCurStationNo(getNearestStation(stationFields, taskRequest.getLng(), taskRequest.getLat()).getSequence());
        //放入任务信息
        SimpleDateFormat sfm = new SimpleDateFormat(NonConfigYxConstants.DATE_NO_SS);
        taskResponseBuilder.addTaskFields(Task.newBuilder()
                .setGmtDepart(sfm.format(taskDO.getGmtDepart()))
                .setId(taskDO.getId())
                .setTitle(taskDO.getTitle())
                .setStatus(taskDO.getStatus())
                .setPeople(taskDO.getPeople())
                .setDriverId(taskDO.getDriverId())
                .setTaskTypeValue(taskDO.getTaskType()));

    }

    /**
     * 公务车/专车任务信息
     * @param taskResponseBuilder
     * @param taskDO
     */
    private void getOfficialAndSpecialCarDetail(TaskResponse.Builder taskResponseBuilder, TaskDO taskDO) {
        //返回任务类型
        taskResponseBuilder.setTaskTypeValue(taskDO.getTaskType());
        //取到任务下的订单
        List<BizOrderDO> list = bizOrderMapper.queryTaskDetailForOfficialAndSpecial(taskDO.getId(), taskDO.getDriverId());
        checkArgument(CollectionUtil.isNotEmpty(list), ResultCodeEnum.TASK_NOT_ACTIVE.getType()); //订单不存在

        final SimpleDateFormat sf = new SimpleDateFormat(NonConfigYxConstants.DATE_HM);
        Collection<DriverOrders> driverOrderses = Collections2.transform(list, new Function<BizOrderDO, DriverOrders>() {
            @Override
            public DriverOrders apply(BizOrderDO bizOrderDO) {
                DriverOrders.Builder orderBuider = DriverOrders.newBuilder();
                //接驾中
                if (OrderRideStatusEnum.isIn(bizOrderDO.getRideStatus(), OrderRideStatusEnum.UN_TAKEN)){
                    //放入乘客出发地经纬度
                    String lnglat = bizOrderDO.getAttribute(constantRedisDao.getConfigByKey(ConstantsKey.AttributeKeyConstants.LNG_LAT));

                    if (StringUtils.isNoneBlank(lnglat)) {
                        orderBuider.setLng(lnglat.split(",")[0])
                                .setLat(lnglat.split(",")[1]);
                    }
                //行程中
                }else if(OrderRideStatusEnum.isIn(bizOrderDO.getRideStatus(), OrderRideStatusEnum.TAKEN)){
                    //如果是已发账单状态或预发账单状态,行驶时间和行驶里程不在变化(只有专车有发送账单)
                    if (OrderPayStatusEnum.isIn(bizOrderDO.getPayStatus(),OrderPayStatusEnum.OPS_SEND_BILL)
                            ||(null != bizOrderDO.getGmtEnd()
                               && OrderPayStatusEnum.isIn(bizOrderDO.getPayStatus(),OrderPayStatusEnum.CREATED)
                               && OrderBizTypeEnum.isIn(bizOrderDO.getBizType(), OrderBizTypeEnum.SPECIAL_BUS_TIME,OrderBizTypeEnum.SPECIAL_BUS_WAY))){
                            getRunInfoForEnd(orderBuider, bizOrderDO);
                    }else {
                        //公务车任务--单程租车/专车任务--单程租车,放入终点位置
                        if (OrderBizTypeEnum.isIn(bizOrderDO.getBizType(), OrderBizTypeEnum.OFFICIAL_BUS_WAY, OrderBizTypeEnum.SPECIAL_BUS_WAY)){
                            String elnglat = bizOrderDO.getAttribute(constantRedisDao.getConfigByKey(ConstantsKey.AttributeKeyConstants.ELNG_LAT));
                            if (StringUtils.isNoneBlank(elnglat)) {
                                orderBuider.setElng(elnglat.split(",")[0])
                                        .setElat(elnglat.split(",")[1]);
                            }
                        }//公务车任务--分时租车/专车任务--分时租车,放入行驶里程和用时
                        else if (OrderBizTypeEnum.isIn(bizOrderDO.getBizType(), OrderBizTypeEnum.OFFICIAL_BUS_TIME, OrderBizTypeEnum.SPECIAL_BUS_TIME)){
//                            Date gmtDepart= bizOrderDO.getGmtDepart();
//                            if (bizOrderDO.getBizType() == OrderBizType.SPECIAL_BUS_TIME_VALUE){
                            //公务车专车开始时间都用gmtStart 20161230 wangxl
                            Date gmtDepart= bizOrderDO.getGmtStart();
//                            }
                            Date nowTime = Calendar.getInstance().getTime();
                            if (gmtDepart != null && nowTime != null){
                                getRunInfo(gmtDepart,nowTime,orderBuider,bizOrderDO);
                            }
                        }
                    }

                }else if (OrderRideStatusEnum.isIn(bizOrderDO.getRideStatus(), OrderRideStatusEnum.PART_ARRIVED)){

                    getRunInfoForEnd(orderBuider, bizOrderDO);

                }
                //放入乘客的相关信息
                orderBuider.setMobile(bizOrderDO.getMobile())
                        .setGmtDepartTime(sf.format(bizOrderDO.getGmtDepart()))
                        .setStart(bizOrderDO.getStart() == null ? "" : bizOrderDO.getStart())
                        .setDestination(bizOrderDO.getDestination() == null ? "" : bizOrderDO.getDestination())
                        .setStartDetailAddr(bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.START_DETAIL_ADDRESS) == null ? "" : bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.START_DETAIL_ADDRESS))
                        .setEndDetailAddr(bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.END_DETAIL_ADDRESS) == null ? "" : bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.END_DETAIL_ADDRESS))
                        .setRideStatusValue(bizOrderDO.getRideStatus())
                        .setPayStatusValue(bizOrderDO.getPayStatus())
                        .setOrderId(bizOrderDO.getId())
                        .setUserId(""+bizOrderDO.getUserId());
                //如果是替他人叫车,手机号换成他人的手机号
                if("true".equals(bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.CALL_CAR_FOR_OTHER))){
                    orderBuider.setMobile(bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.OTHER_MOBILE));
                }
                return orderBuider.build();
            }
        });
        //放入乘客信息
        taskResponseBuilder.addAllDriverOrdersFields(driverOrderses);

        //放入任务信息
        taskResponseBuilder.addTaskFields(Task.newBuilder()
                .setId(taskDO.getId())
                .setTitle(taskDO.getTitle() == null ? "" : taskDO.getTitle())
                .setStatus(taskDO.getStatus())
                .setDriverId(taskDO.getDriverId())
                .setTaskTypeValue(taskDO.getTaskType())
                .putAllAttributes(taskDO.getAttributes()));
    }

    /**
     * 分时租车 行驶中和发送账单公用获取行驶分钟和行驶里程信息
     * @param gmtDepart
     * @param nowTime
     * @param orderBuider
     * @param bizOrderDO
     */
    private void getRunInfo(Date gmtDepart, Date nowTime, DriverOrders.Builder orderBuider,BizOrderDO bizOrderDO){
        long gmtDepartTime = gmtDepart.getTime();
        long gmtEndTime = nowTime.getTime();
        long duration = gmtEndTime -  gmtDepartTime;
        long totalSeconds = duration/1000;//秒数
        long secondsOfDay = 24 * 60 * 60;// 一天的秒数
        long secondsOfHour = 60 * 60; // 一小时的秒数
        long secondsOfMinute = 60; // 一分钟的秒数
        long days = totalSeconds / secondsOfDay;// 得到天数
        long hours = (totalSeconds % secondsOfDay) / secondsOfHour;// 余数中的小时个数
        long minutes = ((totalSeconds % secondsOfDay) % secondsOfHour) / secondsOfMinute; // 余数中的分钟数
        long seconds = totalSeconds % 60;// 余数中的秒数
        String time = "";
        if (days != 0l){
            time += days+" 天";
        }
        if (hours<10l){
            time +="0"+hours+":";
        }else{
            time +=hours+":";
        }

        if (minutes<10l){
            time += "0"+minutes+"";
        }else{
            time += minutes+"";
        }

        orderBuider.setRunTime(time);
        orderBuider.setRunDistance(new DecimalFormat("0.00")
                .format(constantRedisDao.queryTaskDistance(bizOrderDO.getTaskId())));
    }
    private void getRunInfoForEnd(DriverOrders.Builder orderBuider,BizOrderDO bizOrderDO){

        long duration = Long.valueOf(bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.RUN_MIN));//等到行驶分钟数

        long minsOfDay = 24 * 60 ;// 一天的分钟
        long minsOfHour = 60 ; // 一小时的分钟数
        long days = duration / minsOfDay;// 得到天数
        long hours = (duration % minsOfDay) / minsOfHour;// 余数中的小时个数
        long minutes = ((duration % minsOfDay) % minsOfHour); // 余数为分钟数
        String time = "";
        if (days != 0l){
            time += days+"天";
        }
        if (hours !=0l){
            time += hours+"小时";
        }
        if (minutes != 0l){
            time += minutes+"分钟";
        }
        orderBuider.setRunTime(time);

        orderBuider.setRunDistance(bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.RUN_KM));
    }

    /**
     * 通过redis优化司机过站问题
     */
//    public void getDiyBusDetailV3(TaskResponse.Builder taskResponseBuilder, final TaskDO taskDO, TaskRequest taskRequest) throws IOException {
//        //返回任务类型
//        taskResponseBuilder.setTaskTypeValue(taskDO.getTaskType());
//        //给据线路查出所有的站点
//        List<StationField> stationFields = stationMapper.queryByLine(taskDO.getLineId());
//        //校验站点
//        checkArgument(CollectionUtil.isNotEmpty(stationFields), ResultCodeEnum.NO_STATION.getType());
//        //放入站点信息
//        Collection<StationEntry> stations = Collections2.transform(stationFields, new Function<StationField, StationEntry>() {
//            @Override
//            public StationEntry apply(StationField stationField) {
//                StationEntry.Builder stationBuilder = StationEntry.newBuilder();
//                //取到站点的信息
//                getStationMessage(stationBuilder, stationField, taskDO);
//                return stationBuilder.build();
//            }
//        });
//        taskResponseBuilder.addAllStations(stations);
//        //取出当前站
//        Integer currentSequence = constantRedisDao.queryTaskSequence(taskDO.getId());
//        if (currentSequence.intValue() != NonConfigYxConstants.END_STATION_SEQ.intValue()) {
//            //取出当前站和下一站的站点列表
//            List<StationField> list = getList(stationFields, currentSequence);
//            //计算出最近的站点
//            if (!(getNearestStation(list, taskRequest.getLng(), taskRequest.getLat()).getSequence() == currentSequence)) {
//                currentSequence = list.get(1).getSequence();
//            }
//            //根据序号取站点
//
//            // StationField stationField = getStation(stationFields, currentSequence);
//            //计算原始站点和当前位置 和 当前站点和原始位置的距离
//            Integer distance = getDistance(stationFields.get(0).getLongitude(), stationFields.get(0).getLatitude(), taskRequest.getLng(), taskRequest.getLat(), list.get(0).getLongitude(), list.get(0).getLatitude());
//            //如果当前位置比当前站点远
//            if (distance > Integer.parseInt(constantRedisDao.getConfigByKey(ConstantsKey.diyBus.PASS_STATION_DISTANCE))) {
//                currentSequence = list.get(1).getSequence();
//            }
//        }
//        taskResponseBuilder.setCurStationNo(currentSequence);
//        //把当前站放入缓存
//        constantRedisDao.insertTaskSequence(taskDO.getId(), currentSequence);
//        //放入任务信息
//        taskResponseBuilder.addTaskFields(Task.newBuilder()
//                .setId(taskDO.getId())
//                .setTitle(taskDO.getTitle())
//                .setStatus(taskDO.getStatus())
//                .setPeople(taskDO.getPeople())
//                .setDriverId(taskDO.getDriverId())
//                .setTaskTypeValue(taskDO.getTaskType()));
//
//    }

    public void getStationMessage(StationEntry.Builder station, final StationField stationField, TaskDO taskDO) {
        //获取当前站的上下车的订单
        List<BizOrderDO> orderDOs = bizOrderMapper.queryBizOrderForUpDown(stationField.getStationName(), taskDO.getId(), taskDO.getDriverId(), OrderTypeEnum.MAIN.getType());
        Integer persionsU = 0;
        //取站点上车订单
        Optional<Collection<BizOrderDO>> orderOptionU = Optional.fromNullable(Collections2.filter(orderDOs, new Predicate<BizOrderDO>() {
            @Override
            public boolean apply(BizOrderDO input) {
                return StringUtils.equalsIgnoreCase(input.getStart(), stationField.getStationName());
            }
        }));
        //取上车人数
        if (orderOptionU.isPresent()) {
            Collection<BizOrderDO> listUp = orderOptionU.get();
            for (BizOrderDO bizOrderDO : listUp) {
                persionsU += bizOrderDO.getAmount();
            }
        }
        Integer persionsD = 0;
        //取站点下车 计算下车人数
        Optional<Collection<BizOrderDO>> orderOptionD = Optional.fromNullable(Collections2.filter(orderDOs, new Predicate<BizOrderDO>() {
            @Override
            public boolean apply(BizOrderDO input) {
                return StringUtils.equalsIgnoreCase(input.getDestination(), stationField.getStationName());
            }
        }));
        //如果有下车订单,计算下车人数
        if (orderOptionD.isPresent()) {
            Collection<BizOrderDO> listDown = orderOptionD.get();
            for (BizOrderDO bizOrderDO : listDown) {
                persionsD += bizOrderDO.getAmount();
            }
        }
        //存入站点信息
        station.setPersonUp(persionsU) //上车人数
                .setPersonsDown(persionsD)//下车人数
                .setLongitude(stationField.getLongitude())
                .setLatitude(stationField.getLatitude())
                .setStationName(stationField.getStationName())
                .setSequence(stationField.getSequence());//站点序号

    }

    //计算两个站点之间的距离
    private Integer getDistance(String elng, String elat, String lng1, String lat1, String lng2, String lat2) throws IOException {
        String locationStr = lng1 + "," + lat1 + "|" + lng2 + "," + lat2;
        //构造请求高德的数据
        String amapDistanceUrl = constantRedisDao.getConfigByKey(ConstantsKey.Amap.AMAP_WEBSERVER_URL)
                + constantRedisDao.getConfigByKey(ConstantsKey.Amap.AMAP_DISTANCE_URI);

        ImmutableList<BasicNameValuePair> pairs = ImmutableList.of(
                new BasicNameValuePair("output", "json"),
                new BasicNameValuePair("key", constantRedisDao.getConfigByKey(ConstantsKey.Amap.AMAP_SERVER_WEB_KEY)),
                new BasicNameValuePair("origins", locationStr),
                new BasicNameValuePair("destination", elng + "," + elat)
        );
        //请求并获取返回结果
        String response = HttpUtil.doRequest(amapDistanceUrl, HttpUtil.POST, pairs);
        checkNotNull(response, ResultCodeEnum.AMAP_RESPONSE_ERR.getType());
        // 转换为对象
        ObjectMapper mapper = new ObjectMapper();
        DistanceResult result = mapper.readValue(response, DistanceResult.class);
        checkNotNull(result, ResultCodeEnum.AMAP_RESPONSE_ERR.getType());
        checkArgument(StringUtils.equalsIgnoreCase(result.getStatus(), "1"), ResultCodeEnum.AMAP_RESPONSE_ERR.getType());
        List<DistanceBO> distanceBOList = new ArrayList<>(result.getDistances());
        return Integer.parseInt(distanceBOList.get(0).getDistance()) - Integer.parseInt(distanceBOList.get(1).getDistance());
    }

    private StationField getNearestStation(List<StationField> stationDOList, String lng, String lat) throws IOException {
        //拼接站点的经纬度
        Collection<String> locationInfo = Collections2.transform(stationDOList, new Function<StationField, String>() {
            @Override
            public String apply(StationField input) {
                return input.getLongitude() + "," + input.getLatitude();
            }
        });
        String locationStr = Joiner.on("|").join(locationInfo);
        //构造请求高德的数据
        String amapDistanceUrl = constantRedisDao.getConfigByKey(ConstantsKey.Amap.AMAP_WEBSERVER_URL)
                + constantRedisDao.getConfigByKey(ConstantsKey.Amap.AMAP_DISTANCE_URI);

        ImmutableList<BasicNameValuePair> pairs = ImmutableList.of(
                new BasicNameValuePair("output", "json"),
                new BasicNameValuePair("key", constantRedisDao.getConfigByKey(ConstantsKey.Amap.AMAP_SERVER_WEB_KEY)),
                new BasicNameValuePair("origins", locationStr),
                new BasicNameValuePair("destination", lng + "," + lat)
        );
        //请求并获取返回结果
        String response = HttpUtil.doRequest(amapDistanceUrl, HttpUtil.POST, pairs);
        checkNotNull(response, ResultCodeEnum.AMAP_RESPONSE_ERR.getType());
        // 转换为对象
        ObjectMapper mapper = new ObjectMapper();
        DistanceResult result = mapper.readValue(response, DistanceResult.class);
        checkNotNull(result, ResultCodeEnum.AMAP_RESPONSE_ERR.getType());
        checkArgument(StringUtils.equalsIgnoreCase(result.getStatus(), "1"), ResultCodeEnum.AMAP_RESPONSE_ERR.getType());
        // 排序,按照升序来
        List<DistanceBO> distanceBOList = new ArrayList<>(result.getDistances());
        Collections.sort(distanceBOList, new Comparator<DistanceBO>() {
            /**
             * 如果要按照升序排序，
             * 则o1 小于o2，返回-1（负数），相等返回0，01大于02返回1（正数）
             * 如果要按照降序排序
             * 则o1 小于o2，返回1（正数），相等返回0，01大于02返回-1（负数）
             * @param o1
             * @param o2
             * @return
             */
            @Override
            public int compare(DistanceBO o1, DistanceBO o2) {
                long o1Dis = NumberUtils.toLong(o1.getDistance());
                long o2Dis = NumberUtils.toLong(o2.getDistance());
                if (o1Dis < o2Dis)
                    return -1;
                if (o1Dis == o2Dis)
                    return 0;
                else return 1;
            }
        });
        //取最小的那个
        DistanceBO minDistance = Iterators.get(distanceBOList.iterator(), 0);
        // 获取对应的站点
        StationField stationDO = Iterators.get(stationDOList.iterator(), NumberUtils.toInt(minDistance.getOriginId()) - 1);
        return stationDO;
    }

    /**
     * 根据当前站去下一个站的list
     *
     * @param list
     * @param no
     * @return
     */
    private List<StationField> getList(List<StationField> list, final Integer no) {

        List<StationField> list1 = Lists.newArrayList();

        StationField curStation = Iterators.find(list.iterator(), new Predicate<StationField>() {
            @Override
            public boolean apply(StationField stationField) {
                return stationField.getSequence() == no;
            }
        });

        int stationAt = list.indexOf(curStation);

        list1.add(curStation);
        list1.add(list.get(stationAt + 1));

        return list1;
    }

    private StationField getStation(List<StationField> list, Integer no) {
        StationField stationField = new StationField();
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getSequence() == no) {
                stationField = list.get(i);
            }
        }
        return stationField;
    }
}
