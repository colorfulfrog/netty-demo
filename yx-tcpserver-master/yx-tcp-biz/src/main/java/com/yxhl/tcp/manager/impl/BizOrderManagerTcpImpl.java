package com.yxhl.tcp.manager.impl;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterators;
import com.yxhl.amap.DistanceBO;
import com.yxhl.constants.ConstantsKey;
import com.yxhl.constants.NonConfigYxConstants;
import com.yxhl.domain.*;
import com.yxhl.enums.*;
import com.yxhl.persistence.mapper.BizOrderMapper;
import com.yxhl.persistence.mapper.DriverMapper;
import com.yxhl.persistence.mapper.JudgeMapper;
import com.yxhl.persistence.mapper.dao.ConstantDao;
import com.yxhl.protobuf.*;
import com.yxhl.tcp.manager.BizOrderManager;
import com.yxhl.util.AmapUtil;
import com.yxhl.util.NumberUtil;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.DecimalFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Created by alan on 16/4/18.
 * tcp实现
 */
@Service("bizOrderManagerTcp")
public class BizOrderManagerTcpImpl implements BizOrderManager {

    @Autowired
    private BizOrderMapper bizOrderMapper;

    @Autowired
    private ConstantDao constantRedisDao;

    @Autowired
    private DriverMapper driverMapper;

    @Autowired
    private AmapUtil amapServer;

    @Autowired
    private JudgeMapper judgeMapper;

    @Transactional(readOnly = true)
    @Override
    public void queryLocation(BizOrderResponse.Builder bizOrderResponseBuilder, BizOrderRequest bizOrderRequest) {

        // 查询当前订单
        BizOrderDO bizOrderDO = bizOrderMapper.queryBizOrderByOrderSerial(bizOrderRequest.getOrderSerial());
        checkNotNull(bizOrderDO, ResultCodeEnum.ORDER_NOT_EXIST.getType());
        // 快车(包车及拼车)
        if (bizOrderDO.getBizType() == OrderBizType.ORDER_BIZ_CHARTER_VALUE || bizOrderDO.getBizType() == OrderBizType.ORDER_BIZ_SHARE_VALUE) {
            handleCarLoc(bizOrderResponseBuilder, bizOrderDO);
            return;
        }

        // 对于巴士订单,需要判断其是否有接驳车
        if (bizOrderDO.getBizType() == OrderBizType.OBT_DIY_BUS_VALUE || bizOrderDO.getBizType() == OrderBizType.OBT_FT_BUS_VALUE) {
            handleBusLoc(bizOrderResponseBuilder, bizOrderDO);
            return;
        }

        //公务车/专车订单
        if(bizOrderDO.getBizType() == OrderBizType.OFFICIAL_BUS_TIME_VALUE || bizOrderDO.getBizType() == OrderBizType.OFFICIAL_BUS_WAY_VALUE||
                bizOrderDO.getBizType() == OrderBizType.SPECIAL_BUS_WAY_VALUE || bizOrderDO.getBizType() == OrderBizType.SPECIAL_BUS_TIME_VALUE){
            handleOfficialAndSpecialCarLoc(bizOrderResponseBuilder, bizOrderDO);
            return;
        }

    }

    @Transactional(readOnly = true)
    @Override
    public void queryLocation2(BizOrderResponse.Builder bizOrderResponseBuilder, BizOrderDO bizOrderDO) {

        // 查询当前订单
        //BizOrderDO bizOrderDO = bizOrderMapper.queryBizOrderByOrderSerial(bizOrderRequest.getOrderSerial());
        checkNotNull(bizOrderDO, ResultCodeEnum.ORDER_NOT_EXIST.getType());
        // 快车(包车及拼车)
        if (bizOrderDO.getBizType() == OrderBizType.ORDER_BIZ_CHARTER_VALUE || bizOrderDO.getBizType() == OrderBizType.ORDER_BIZ_SHARE_VALUE) {
            handleCarLoc(bizOrderResponseBuilder, bizOrderDO);
            return;
        }

        // 对于巴士订单,需要判断其是否有接驳车
        if (bizOrderDO.getBizType() == OrderBizType.OBT_DIY_BUS_VALUE || bizOrderDO.getBizType() == OrderBizType.OBT_FT_BUS_VALUE) {
            handleBusLoc(bizOrderResponseBuilder, bizOrderDO);
            return;
        }

        //公务车/专车订单
        if(bizOrderDO.getBizType() == OrderBizType.OFFICIAL_BUS_TIME_VALUE || bizOrderDO.getBizType() == OrderBizType.OFFICIAL_BUS_WAY_VALUE||
                bizOrderDO.getBizType() == OrderBizType.SPECIAL_BUS_WAY_VALUE || bizOrderDO.getBizType() == OrderBizType.SPECIAL_BUS_TIME_VALUE){
            handleOfficialAndSpecialCarLoc(bizOrderResponseBuilder, bizOrderDO);
            return;
        }

    }

    /**
     * 处理巴士相关的流程
     *
     * @param bizOrderResponseBuilder
     * @param bizOrderDO
     */
    private void handleBusLoc(BizOrderResponse.Builder bizOrderResponseBuilder, BizOrderDO bizOrderDO) {

        //判断订单是否有接驳车
        String needFerry = bizOrderDO.getAttributes().get(NonConfigYxConstants.AttributeKeyConstants.ferry);
        //如果有接驳车
        if (StringUtils.equalsIgnoreCase(NonConfigYxConstants.TRUE, needFerry)) {
            //根据主订单查出接驳车
            List<BizOrderDO> bizOrderDOList = bizOrderMapper.queryByOrderType(bizOrderDO.getOrderSerial(), OrderTypeEnum.FERRY.getType());
            //取到接驳车订单
            Optional<BizOrderDO> carBizOrderDO = Optional.of(Iterators.find(bizOrderDOList.iterator(), new Predicate<BizOrderDO>() {
                @Override
                public boolean apply(BizOrderDO bizOrderDO) {
                    return StringUtils.equals(bizOrderDO.getOrderType(), OrderTypeEnum.FERRY.getType());
                }
            }));
            //接驳车订单
            BizOrderDO bizOrderDO1 = carBizOrderDO.get();
            //如果接驳车未派车,或者派车了没有上车,返回当前位置
            if (null == bizOrderDO1.getTaskId() || OrderRideStatusEnum.isIn(bizOrderDO1.getRideStatus(), OrderRideStatusEnum.UN_SEND)) {
                String[] lnglatArray = handleLngLats(bizOrderDO1, ConstantsKey.AttributeKeyConstants.LNG_LAT);
                if (null != lnglatArray) {
                    bizOrderResponseBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_SELF))
                            .setLng(lnglatArray[0])
                            .setLat(lnglatArray[1]));
                }

            } else {
                // 查询任务对应车辆的最新位置
                TaskLocationDO locationDO = constantRedisDao.queryLatestLocation(bizOrderDO1.getTaskId());

                //放入接驳车位置
                if (null != locationDO) {
                    bizOrderResponseBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_CAR))
                            .setLng(locationDO.getLng())
                            .setLat(locationDO.getLat())
                            .setHeading(locationDO.getHeading()));
                } else {
                    if (bizOrderDO.getDriverId() != null) { //根据司机信息上传经纬度
                        DriverLocationDO driverLocationDO=constantRedisDao.queryLatestDriverLocation(bizOrderDO.getDriverId());
                        if (driverLocationDO != null) {
                            bizOrderResponseBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_CAR))
                                    .setLng(driverLocationDO.getLng())
                                    .setLat(driverLocationDO.getLat())
                                    .setHeading(driverLocationDO.getHeading()));
                        }
                    }
                }
            }
            //如果主订单已经派车,给出大巴的位置信息
            if (null != bizOrderDO.getTaskId()) {
                TaskLocationDO locationDO = constantRedisDao.queryLatestLocation(bizOrderDO.getTaskId());

                //放入大巴车位置
                if (null != locationDO) {
                    bizOrderResponseBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_BUS))
                            .setLng(locationDO.getLng())
                            .setLat(locationDO.getLat())
                            .setHeading(locationDO.getHeading()));
                }
            }
            //如果没有接驳车
        } else {
            //如果当前订单未分配
            if (null == bizOrderDO.getTaskId() || bizOrderDO.getRideStatus() == OrderRideStatusEnum.UN_SEND.getStatus()) {
                //只返回本订单的出发位置
                String[] lnglatArray = handleLngLats(bizOrderDO, ConstantsKey.AttributeKeyConstants.LNG_LAT);
                if (null != lnglatArray) {
                    bizOrderResponseBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_SELF))
                            .setLng(lnglatArray[0])
                            .setLat(lnglatArray[1]));
                    return;
                }
            } else {
                // 查询任务对应车辆的最新位置
                TaskLocationDO locationDO = constantRedisDao.queryLatestLocation(bizOrderDO.getTaskId());

                //如果订单为已支付并且是未上车,返回上车点
                if (OrderPayStatusEnum.isIn(bizOrderDO.getPayStatus(), OrderPayStatusEnum.PAID) &&
                        OrderRideStatusEnum.isIn(bizOrderDO.getRideStatus(), OrderRideStatusEnum.UN_TAKEN)) {

                    String[] lnglatArray = handleLngLats(bizOrderDO, ConstantsKey.AttributeKeyConstants.LNG_LAT);
                    if (null != lnglatArray) {
                        bizOrderResponseBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_SELF))
                                .setLng(lnglatArray[0])
                                .setLat(lnglatArray[1]));
                    }
                }
                //放入bus的位置信息
                if (null != locationDO) {
                    bizOrderResponseBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_BUS))
                            .setLng(locationDO.getLng())
                            .setLat(locationDO.getLat())
                            .setHeading(locationDO.getHeading()));
                }

            }
        }


    }

    /**
     * 处理快车的相关流程
     *
     * @param bizOrderRespBuilder
     * @param bizOrderDO
     */
    private void handleCarLoc(BizOrderResponse.Builder bizOrderRespBuilder, BizOrderDO bizOrderDO) {
        // 如果当前订单尚未分配任务,则直接返回它本身的地理位置
        if (bizOrderDO.getTaskId() == null
                || bizOrderDO.getRideStatus() == OrderRideStatusEnum.UN_SEND.getStatus()) {
            // 本订单出发位置
            String[] lnglatArray = handleLngLats(bizOrderDO, ConstantsKey.AttributeKeyConstants.LNG_LAT);
            if (null != lnglatArray) {
                bizOrderRespBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_SELF))
                        .setLng(lnglatArray[0])
                        .setLat(lnglatArray[1]));
            }
            // 本订单到达位置
            String[] elnglatArray = handleLngLats(bizOrderDO, ConstantsKey.AttributeKeyConstants.ELNG_LAT);
            {
                bizOrderRespBuilder.addElnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_SELF))
                        .setLng(elnglatArray[0])
                        .setLat(elnglatArray[1]));
            }
            return;
        }
        // 如果是已经分配任务,则查询出对应的任务关联的订单及车位置,组装回去
        else {
            // 任务关联的所有订单
            List<BizOrderDO> bizOrdersWithTask = bizOrderMapper.queryBizOrdersByTaskId(bizOrderDO.getTaskId());
            checkNotNull(bizOrdersWithTask, ResultCodeEnum.ORDER_NOT_EXIST.getType());
            // 查询任务对应车辆的最新位置
            TaskLocationDO locationDO = constantRedisDao.queryLatestLocation(bizOrderDO.getTaskId());


            // 查询司机信息
            DriverDO driverDO = driverMapper.queryDriverById(bizOrderDO.getDriverId());
            checkNotNull(driverDO, ResultCodeEnum.DRIVER_INFO_NOT_EXIST.getType());

            //设置司机评分
            bizOrderRespBuilder.setDriverJudgePoint(driverDO.getJudgePoint());

            // 如果订单未取消,则返回其对应的位置信息
            for (BizOrderDO order : bizOrdersWithTask) {
                if (OrderPayStatusEnum.isIn(order.getPayStatus(), OrderPayStatusEnum.CREATED, OrderPayStatusEnum.PAID)) {
                    // 本人订单所在位置\到达位置
                    if (StringUtils.equals(order.getOrderSerial(), bizOrderDO.getOrderSerial())) {
                        // 如果未发车 或者 未上车,都返回当前订单下单位置
                        if (OrderRideStatusEnum.isIn(order.getRideStatus(), OrderRideStatusEnum.UN_TAKEN, OrderRideStatusEnum.UN_SEND)) {
                            String[] lnglatArray = handleLngLats(order, ConstantsKey.AttributeKeyConstants.LNG_LAT);
                            if (null != lnglatArray) {
                                bizOrderRespBuilder.addLnglats(Location.newBuilder().setLocType(
                                        constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_SELF))
                                        .setLng(lnglatArray[0])
                                        .setLat(lnglatArray[1]));
                            }
                        }
                        // 如果未发车 或者 未上车 或者 未到达,都返回当前订单到达位置
                        if (OrderRideStatusEnum.isIn(order.getRideStatus(), OrderRideStatusEnum.UN_TAKEN,
                                OrderRideStatusEnum.UN_SEND, OrderRideStatusEnum.TAKEN)) {
                            String[] lnglatArray = handleLngLats(order, ConstantsKey.AttributeKeyConstants.ELNG_LAT);
                            if (null != lnglatArray) {
                                bizOrderRespBuilder.addElnglats(Location.newBuilder().setLocType(
                                        constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_SELF))
                                        .setLng(lnglatArray[0])
                                        .setLat(lnglatArray[1]));
                            }
                        }
                    }
                    // 同车其它订单所在位置\到达位置
                    else {
                        // 如果未发车 或者 未上车,都返回订单下单位置
                        if (OrderRideStatusEnum.isIn(order.getRideStatus(), OrderRideStatusEnum.UN_TAKEN, OrderRideStatusEnum.UN_SEND)) {
                            String[] lnglatArray = handleLngLats(order, ConstantsKey.AttributeKeyConstants.LNG_LAT);
                            if (null != lnglatArray) {
                                bizOrderRespBuilder.addLnglats(Location.newBuilder().setLocType(
                                        constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_ORDER))
                                        .setLng(lnglatArray[0])
                                        .setLat(lnglatArray[1]));
                            }
                        }

                        // 如果未发车 或者 未上车 或者 未到达,都返回订单到达位置
                        if (OrderRideStatusEnum.isIn(order.getRideStatus(), OrderRideStatusEnum.UN_TAKEN,
                                OrderRideStatusEnum.UN_SEND, OrderRideStatusEnum.TAKEN)) {
                            String[] lnglatArray = handleLngLats(order, ConstantsKey.AttributeKeyConstants.ELNG_LAT);
                            if (null != lnglatArray) {
                                bizOrderRespBuilder.addElnglats(Location.newBuilder().setLocType(
                                        constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_ORDER))
                                        .setLng(lnglatArray[0])
                                        .setLat(lnglatArray[1]));
                            }
                        }
                    }
                }
            }
            // 发送账单添加
            bizOrderRespBuilder.setRideStatus(OrderRideStatus.forNumber(bizOrderDO.getRideStatus()));//返回订单乘车状态
            bizOrderRespBuilder.setPayStatus(OrderPayStatus.forNumber(bizOrderDO.getPayStatus()));//返回订单支付状态
            bizOrderRespBuilder.setOrderBizType(OrderBizType.forNumber(bizOrderDO.getBizType()));  //返回订单类型
            // 将任务的位置信息加入 lnglat 对象中
            if (null != locationDO) {
                bizOrderRespBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_CAR))
                        .setLng(locationDO.getLng())
                        .setLat(locationDO.getLat())
                        .setHeading(locationDO.getHeading()));

            } else {
                if (bizOrderDO.getDriverId() != null) { //根据司机信息上传经纬度修改
                    DriverLocationDO driverLocationDO=constantRedisDao.queryLatestDriverLocation(bizOrderDO.getDriverId());
                    if (driverLocationDO != null) {
                        bizOrderRespBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_CAR))
                                .setLng(driverLocationDO.getLng())
                                .setLat(driverLocationDO.getLat())
                                .setHeading(driverLocationDO.getHeading()));
                    }
                }
            }

        }
    }
    /**
     * 处理公务车/专车的相关流程
     *
     * @param bizOrderRespBuilder
     * @param bizOrderDO
     */
    private void handleOfficialAndSpecialCarLoc(BizOrderResponse.Builder bizOrderRespBuilder, BizOrderDO bizOrderDO) {
        try{
             //没有任务未派车  等待接驾
             if(bizOrderDO.getTaskId() == null){
                 // 本订单出发位置
                 String[] lnglatArray = handleLngLats(bizOrderDO, ConstantsKey.AttributeKeyConstants.LNG_LAT);
                 if (null != lnglatArray) {
                     addLnglats(lnglatArray, bizOrderRespBuilder);
                 }
                 //单程租车
                 if(bizOrderDO.getBizType() == OrderBizType.OFFICIAL_BUS_WAY_VALUE||bizOrderDO.getBizType() == OrderBizType.SPECIAL_BUS_WAY_VALUE){
                     // 本订单到达位置
                     String[] elnglatArray = handleLngLats(bizOrderDO, ConstantsKey.AttributeKeyConstants.ELNG_LAT);
                     {
                         addElnglats(elnglatArray,bizOrderRespBuilder);
                     }
                     //公务车任务-分时租车/专车任务-分时租车
                 }

             }else {//已派车
                 // 查询任务对应车辆的最新位置
                 TaskLocationDO locationDO = constantRedisDao.queryLatestLocation(bizOrderDO.getTaskId());

                 // 查询司机信息
                 DriverDO driverDO = driverMapper.queryDriverById(bizOrderDO.getDriverId());
                 checkNotNull(driverDO, ResultCodeEnum.DRIVER_INFO_NOT_EXIST.getType());

                 //设置司机评分/姓名/手机号/车牌号
                 bizOrderRespBuilder.setDriverJudgePoint(driverDO.getJudgePoint()==null?0.0d:driverDO.getJudgePoint())
                                .setDriverName(driverDO.getRealname())
                                .setDriverTel(driverDO.getMobile())
                                .setVehicleNo(bizOrderDO.getVehicleNo() == null ? "" : bizOrderDO.getVehicleNo())
                                .setVehicleSeries(bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.VEHICLE_SERIES)
                                        == null ? "" : bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.VEHICLE_SERIES));
                 if (locationDO != null){
                     bizOrderRespBuilder.setHeading(locationDO.getHeading() == null ? "" : locationDO.getHeading());
                 }
                 //如果是已发账单状态或预发账单状态,行驶时间和行驶里程不在变化.(只有专车有已发账单)
                 if (OrderPayStatusEnum.isIn(bizOrderDO.getPayStatus(),OrderPayStatusEnum.OPS_SEND_BILL)
                         ||(null != bizOrderDO.getGmtEnd()
                            && OrderPayStatusEnum.isIn(bizOrderDO.getPayStatus(),OrderPayStatusEnum.CREATED)
                            && OrderBizTypeEnum.isIn(bizOrderDO.getBizType(), OrderBizTypeEnum.SPECIAL_BUS_TIME,OrderBizTypeEnum.SPECIAL_BUS_WAY))){
                     //行驶时间和行驶里程
                     this.getRunInfoForEnd(bizOrderRespBuilder,bizOrderDO);
                 }else{
                     //未上车 接驾中,返回车与乘客之间的距离和预计达到时间
                     if (OrderRideStatusEnum.isIn(bizOrderDO.getRideStatus(), OrderRideStatusEnum.UN_TAKEN)) {
                         // 本订单出发位置
                         String[] lnglatArray = handleLngLats(bizOrderDO, ConstantsKey.AttributeKeyConstants.LNG_LAT);
                         if (null != lnglatArray) {
                             addLnglats(lnglatArray, bizOrderRespBuilder);
                         }
                         //公务车任务-单程租车/专车任务-单程租车
                         if(bizOrderDO.getBizType() == OrderBizType.OFFICIAL_BUS_WAY_VALUE||bizOrderDO.getBizType() == OrderBizType.SPECIAL_BUS_WAY_VALUE){
                             // 本订单到达位置
                             String[] elnglatArray = handleLngLats(bizOrderDO, ConstantsKey.AttributeKeyConstants.ELNG_LAT);
                             {
                                 addElnglats(elnglatArray,bizOrderRespBuilder);
                             }
                             //公务车任务-分时租车/专车任务-分时租车
                         }
                         if (locationDO != null){
                             //获取车与乘客的距离和预计达到时间
                             DistanceBO distanceBO = amapServer.queryDistance(lnglatArray[0], lnglatArray[1], locationDO.getLng(), locationDO.getLat());
                             if (distanceBO != null){
                                 String distance = Math.round(Long.valueOf(distanceBO.getDistance())*0.001*10)/10.0+ "";
                                 String dutration = Math.round(Long.valueOf(distanceBO.getDuration())*0.01666667*10)/10+"";
                                 bizOrderRespBuilder.setDistance("距离"+distance+"公里,约"+dutration+"分钟到达");
                             }

                         }
                         //行程中 已上车
                     }else if (OrderRideStatusEnum.isIn(bizOrderDO.getRideStatus(), OrderRideStatusEnum.TAKEN)){
                         // 本订单出发位置
                         String[] lnglatArray = handleLngLats(bizOrderDO, ConstantsKey.AttributeKeyConstants.LNG_LAT);
                         if (null != lnglatArray) {
                             addLnglats(lnglatArray, bizOrderRespBuilder);
                         }
                         //公务车任务-单程租车/专车任务-单程租车
                         if(bizOrderDO.getBizType() == OrderBizType.OFFICIAL_BUS_WAY_VALUE||bizOrderDO.getBizType() == OrderBizType.SPECIAL_BUS_WAY_VALUE){
                             // 本订单到达位置
                             String[] elnglatArray = handleLngLats(bizOrderDO, ConstantsKey.AttributeKeyConstants.ELNG_LAT);
                             {
                                 addElnglats(elnglatArray,bizOrderRespBuilder);
                             }
                             //公务车任务-分时租车/专车任务-分时租车
                         }else if(bizOrderDO.getBizType() == OrderBizType.OFFICIAL_BUS_TIME_VALUE||bizOrderDO.getBizType() == OrderBizType.SPECIAL_BUS_TIME_VALUE){
                             //行驶时间和行驶里程
                             //公务车专车开始时间都用gmtStart 20161230 wangxl
//                             Date gmtDepart= bizOrderDO.getGmtDepart();
//                             if (bizOrderDO.getBizType() == OrderBizType.SPECIAL_BUS_TIME_VALUE){
                             Date  gmtDepart =  bizOrderDO.getGmtStart();
//                             }
                             Date nowTime = Calendar.getInstance().getTime();
                             if (gmtDepart != null && nowTime != null){
                                 this.getRunInfo(gmtDepart,nowTime,bizOrderRespBuilder,bizOrderDO);
                             }
                         }
                         //已到站 结束行程
                     }else if (OrderRideStatusEnum.isIn(bizOrderDO.getRideStatus(), OrderRideStatusEnum.ARRIVED)){
                         this.getRunInfoForEnd(bizOrderRespBuilder,bizOrderDO);
                     }
                 }

                 // 将车的位置放到返回信息中
                 if (null != locationDO) {
                     bizOrderRespBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_CAR))
                             .setLng(locationDO.getLng())
                             .setLat(locationDO.getLat())
                             .setHeading(locationDO.getHeading()));
                 } else {
                     if (bizOrderDO.getDriverId() != null) { //根据司机信息上传经纬度
                         DriverLocationDO driverLocationDO=constantRedisDao.queryLatestDriverLocation(bizOrderDO.getDriverId());
                         if (driverLocationDO != null) {
                             bizOrderRespBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_CAR))
                                     .setLng(driverLocationDO.getLng())
                                     .setLat(driverLocationDO.getLat())
                                     .setHeading(driverLocationDO.getHeading()));
                         }
                     }
                 }
             }
            //根据订单查出对应的评价
            JudgeDO judgeDO = judgeMapper.queryJudgeByOrder(bizOrderDO.getOrderSerial(), bizOrderDO.getMobile());
            // 加入司机信息,车辆信息,评价信息getProtoBizOrder
            bizOrderRespBuilder.addOrderFieldses(getProtoBizOrder(bizOrderDO,judgeDO));
            bizOrderRespBuilder.setRideStatus(OrderRideStatus.forNumber(bizOrderDO.getRideStatus()));//返回乘车状态
            bizOrderRespBuilder.setOrderBizType(OrderBizType.forNumber(bizOrderDO.getBizType()));
            // 发送账单添加
            bizOrderRespBuilder.setPayStatus(OrderPayStatus.forNumber(bizOrderDO.getPayStatus()));//返回订单支付状态
        }catch(Exception e) {
                ConfigDO configDO = constantRedisDao.queryConfigByKey(e.getMessage());
            bizOrderRespBuilder.setErrorMsg(configDO.getValue());
        }
    }

    /**
     * 分时租车 行驶中和发送账单公用获取行驶分钟和行驶里程信息
     * @param gmtDepart
     * @param gmtEnd
     * @param bizOrderRespBuilder
     * @param bizOrderDO
     */
    private void getRunInfo(Date gmtDepart,Date gmtEnd,BizOrderResponse.Builder bizOrderRespBuilder, BizOrderDO bizOrderDO){
        long gmtDepartTime = gmtDepart.getTime();
        long gmtEndTime = gmtEnd.getTime();
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
            time += days+"天";
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
        bizOrderRespBuilder.setRunTime(time);
        bizOrderRespBuilder.setRunDistance(new DecimalFormat("0.00")
                .format(constantRedisDao.queryTaskDistance(bizOrderDO.getTaskId())));
    }

    private void getRunInfoForEnd(BizOrderResponse.Builder bizOrderRespBuilder, BizOrderDO bizOrderDO){

        long duration = Long.valueOf(bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.RUN_MIN));//等到行驶分钟数

        long minsOfDay = 24 * 60 ;// 一天的分钟
        long minsOfHour = 60 ; // 一小时的分钟数
        long days = duration / minsOfDay;// 得到天数
        long hours = (duration % minsOfDay) / minsOfHour;// 余数中的小时个数
        long minutes = ((duration % minsOfDay) % minsOfHour); // 余数为分钟数
        String time = "";
        if (duration == 0l){
            time = "0分钟";
        }else{
            if (days != 0l){
                time += days+"天";
            }
            if (hours !=0l){
                time += hours+"小时";
            }
            if (minutes != 0l){
                time += minutes+"分钟";
            }
        }
        bizOrderRespBuilder.setRunTime(time);

        bizOrderRespBuilder.setRunDistance(bizOrderDO.getAttribute(ConstantsKey.AttributeKeyConstants.RUN_KM));
    }

  /*  private BizOrder.Builder getProtoBizOrder(BizOrderDO bizOrderDO) {
        BizOrder.Builder tmpDOBuilder = BizOrder.newBuilder();
//        ProtoUtil.copy2ProtoObj(tmpDOBuilder, BizOrder.getDescriptor(), bizOrderDO);
        //费用全都转换为以元为单位,而内部则全部采用分为单位
        tmpDOBuilder
//                .setRefundFee(NumberUtil.setScale(bizOrderDO.getRefundFee() / 100.00))
                .setOrderSerial(bizOrderDO.getOrderSerial())
                .setId(bizOrderDO.getId())
                .setTotalFee(NumberUtil.setScale(bizOrderDO.getTotalFee() / 100.00))
                .setDiscountFee(NumberUtil.setScale(bizOrderDO.getDiscountFee() / 100.00))
//                .setPrice(NumberUtil.setScale(bizOrderDO.getPrice() / 100.00))
                .setOrderTotalFee(NumberUtil.setScale(bizOrderDO.getTotalFee() / 100.00 + bizOrderDO.getDiscountFee() / 100.00));
        return tmpDOBuilder;
    }*/

    //*************
    /**
     * 根据主订单直接返回--V2(修改为protobuf模式)
     *
     * @param bizOrderDO
     * @return
     */
    private BizOrder.Builder getProtoBizOrder(BizOrderDO bizOrderDO) {
        BizOrder.Builder tmpDOBuilder = BizOrder.newBuilder();
//        ProtoUtil.copy2ProtoObj(tmpDOBuilder, BizOrder.getDescriptor(), bizOrderDO);
        //费用全都转换为以元为单位,而内部则全部采用分为单位
        tmpDOBuilder
//                .setRefundFee(NumberUtil.setScale(bizOrderDO.getRefundFee() / 100.00))
//                .setRefundFee(bizOrderDO.getRefundFee()==0l?0l:bizOrderDO.getRefundFee()/100.00)
                .setOrderSerial(bizOrderDO.getOrderSerial())
                .setId(bizOrderDO.getId())
                .setDriverId(bizOrderDO.getDriverId()==null ? 0l:bizOrderDO.getDriverId())
                .setDriverMobile(bizOrderDO.getDriverMobile()== null? "":bizOrderDO.getDriverMobile())
                .setDriverName(bizOrderDO.getDriverName()==null ? "":bizOrderDO.getDriverName())
//                .setTaskId(bizOrderDO.getTaskId())

                .setVehicleId(bizOrderDO.getVehicleId()==null ? 0l:bizOrderDO.getVehicleId())
                .setVehicleNo(bizOrderDO.getVehicleNo()==null ? "":bizOrderDO.getVehicleNo())
                .setTitle(bizOrderDO.getTitle()==null? "":bizOrderDO.getTitle())
                .setTotalFee(NumberUtil.setScale(bizOrderDO.getTotalFee() / 100.00))
                .setDiscountFee(NumberUtil.setScale(bizOrderDO.getDiscountFee() / 100.00))
//                .setPrice(NumberUtil.setScale(bizOrderDO.getPrice() / 100.00))
                .setOrderTotalFee(NumberUtil.setScale(bizOrderDO.getTotalFee() / 100.00 + bizOrderDO.getDiscountFee() / 100.00));
        return tmpDOBuilder;
    }

    /**
     * 根据主订单和JudgeDO返回--V2(修改为protobuf模式)
     *
     * @param bizOrderDO
     * @return
     */
    private BizOrder.Builder getProtoBizOrder(BizOrderDO bizOrderDO, JudgeDO judgeDO) {
        BizOrder.Builder tmpDOBuilder = this.getProtoBizOrder(bizOrderDO);
        if (null != judgeDO) {
            JudgeEntry.Builder judgeBuilder = JudgeEntry.newBuilder();
            judgeBuilder
                    .setJudgePoint(judgeDO.getJudgePoint()==null?0:judgeDO.getJudgePoint().intValue())
                    .setDescription(judgeDO.getDescription()==null ? "":judgeDO.getDescription());

            //ProtoUtil.copy2ProtoObj(judgeBuilder, JudgeEntry.getDescriptor(), judgeDO);
            //judgeBuilder.setJudgePoint(judgeDO.getJudgePoint().intValue());
            tmpDOBuilder.setJudge(judgeBuilder.build());
        }
        if (null != bizOrderDO.getTaskId()) {
            tmpDOBuilder.setTaskId(bizOrderDO.getTaskId());
        }
        return tmpDOBuilder;
    }
    //************

    private void addLnglats(String[] lnglatArray,BizOrderResponse.Builder bizOrderRespBuilder){
        bizOrderRespBuilder.addLnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_SELF))
                .setLng(lnglatArray[0])
                .setLat(lnglatArray[1]));
    }
    private void addElnglats(String[] elnglatArray,BizOrderResponse.Builder bizOrderRespBuilder){
        bizOrderRespBuilder.addElnglats(Location.newBuilder().setLocType(constantRedisDao.getConfigByKey(ConstantsKey.YXConstants.LOCATION_TYPE_ORDER))
                .setLng(elnglatArray[0])
                .setLat(elnglatArray[1]));
    }


    private String[] handleLngLats(BizOrderDO bizOrderDO, String key) {
        String lnglats = bizOrderDO.getAttribute(constantRedisDao.getConfigByKey(key));
        if (StringUtils.isNotBlank(lnglats)) {
            String[] lnglatArray = StringUtils.split(lnglats, NonConfigYxConstants.SEP_COMMA);
            if (null != lnglatArray && lnglatArray.length == 2)
                return lnglatArray;
        }

        return null;
    }
}
