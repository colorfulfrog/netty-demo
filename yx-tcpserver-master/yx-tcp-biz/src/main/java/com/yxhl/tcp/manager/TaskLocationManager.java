package com.yxhl.tcp.manager;

import com.yxhl.domain.DriverLocationDO;
import com.yxhl.domain.TaskLocationDO;

/**
 * Created by alan on 16/4/20.
 */
public interface TaskLocationManager {

    /**
     * 根据任务号,记录司机位置
     * @param taskLocationDO
     */
    void insertLocation(TaskLocationDO taskLocationDO);

    /**
     * 根据司机ID,记录司机位置
     * @param driverLocationDO
     */
    void insertLocation(DriverLocationDO driverLocationDO);


}
