package com.yxhl.tcp.manager.impl;

import com.yxhl.amap.DistanceBO;
import com.yxhl.domain.DriverLocationDO;
import com.yxhl.domain.TaskLocationDO;
import com.yxhl.persistence.mapper.dao.ConstantDao;
import com.yxhl.tcp.manager.TaskLocationManager;
import com.yxhl.util.AmapUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Created by alan on 16/4/20.
 */
@Service("taskLocationManagerTcp")
public class TaskLocationManagerTcpImpl implements TaskLocationManager {


    @Autowired
    private ConstantDao constantRedisDao;
    @Autowired
    private AmapUtil amapServer;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void insertLocation(TaskLocationDO taskLocationDO) {

        try{
            //先取出之前的任务位置信息
            TaskLocationDO preTaskLocation = constantRedisDao.queryLatestLocation(taskLocationDO.getTaskId());
            if (preTaskLocation != null){
                String preLng = preTaskLocation.getLng();
                String preLat = preTaskLocation.getLat();

                String eLng = taskLocationDO.getLng();
                String eLat = taskLocationDO.getLat();
                //获取最新位置和上一次位置之间的距离
                DistanceBO distanceBO = amapServer.queryDistance(preLng, preLat, eLng, eLat);
                double distance = Math.round(Long.valueOf(distanceBO.getDistance())*0.001*10)/10.0;

                //从缓存中获取已累计的行程公里数
                double preDistance = constantRedisDao.queryTaskDistance(taskLocationDO.getTaskId());

                //将最新行加到累计行程中,保存到缓存
                constantRedisDao.insertTaskDistance(taskLocationDO.getTaskId(), distance+preDistance);

            }

            /**
             * 把司机上传的经纬度放入缓存中
             */
            constantRedisDao.insertTaskLocation(taskLocationDO);

        }catch (Exception e){
            e.printStackTrace();
        }

    }

    @Override
    public void insertLocation(DriverLocationDO driverLocationDO) {
        try{
            //先取出之前的任务位置信息
//            DriverLocationDO preDriverLocationDO = constantRedisDao.queryLatestDriverLocation(driverLocationDO.getDriverId());
//            if (preDriverLocationDO != null){
//                String preLng = preDriverLocationDO.getLng();
//                String preLat = preDriverLocationDO.getLat();
//
//                String eLng = driverLocationDO.getLng();
//                String eLat = driverLocationDO.getLat();
//                //获取最新位置和上一次位置之间的距离
//                DistanceBO distanceBO = amapServer.queryDistance(preLng, preLat, eLng, eLat);
//                double distance = Math.round(Long.valueOf(distanceBO.getDistance())*0.001*10)/10.0;
//
//                //从缓存中获取已累计的行程公里数
//                double preDistance = constantRedisDao.queryTaskDistance(taskLocationDO.getTaskId());
//                //将最新行加到累计行程中,保存到缓存
//                constantRedisDao.insertTaskDistance(taskLocationDO.getTaskId(), distance+preDistance);
//            }
            /**
             * 把司机上传的经纬度放入缓存中
             */
            constantRedisDao.insertDriverLocation(driverLocationDO);
        }catch (Exception e){
            e.printStackTrace();
        }
    }
}
